/**
 * VAD JNI Interface for Android
 *
 * Provides native Voice Activity Detection using libfvad (WebRTC VAD).
 * Used for client-side endpointing to prevent Whisper hallucination.
 *
 * Copyright (c) 2024 PhicommR1 Project
 */

#include <jni.h>
#include <android/log.h>
#include <cstdlib>
#include <cstring>

extern "C" {
#include "fvad.h"
}

#define LOG_TAG "VadJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// VAD configuration constants
static constexpr int VAD_SAMPLE_RATE = 16000;
static constexpr int VAD_FRAME_MS_30 = 30;
static constexpr int VAD_FRAME_SAMPLES_30 = (VAD_SAMPLE_RATE * VAD_FRAME_MS_30) / 1000; // 480 samples
static constexpr int VAD_MODE_VERY_AGGRESSIVE = 3;

// VAD handle structure for multi-instance support
struct VadHandle {
    Fvad* vad;
    int sample_rate;
    int mode;
    bool initialized;
};

extern "C" {

/**
 * Initialize VAD instance
 * @param mode VAD aggressiveness mode (0-3, where 3 is most aggressive)
 * @return Native handle (pointer as long), or 0 on failure
 */
JNIEXPORT jlong JNICALL
Java_com_phicomm_r1manager_server_voicebot_VadDetector_nativeInitVad(
        JNIEnv* env,
        jobject thiz,
        jint mode) {

    VadHandle* handle = static_cast<VadHandle*>(malloc(sizeof(VadHandle)));
    if (!handle) {
        LOGE("Failed to allocate VadHandle");
        return 0;
    }

    handle->vad = fvad_new();
    if (!handle->vad) {
        LOGE("Failed to create VAD instance");
        free(handle);
        return 0;
    }

    // Set sample rate (16kHz)
    if (fvad_set_sample_rate(handle->vad, VAD_SAMPLE_RATE) < 0) {
        LOGE("Failed to set VAD sample rate to %d", VAD_SAMPLE_RATE);
        fvad_free(handle->vad);
        free(handle);
        return 0;
    }

    // Validate and set mode (0-3)
    int vadMode = (mode >= 0 && mode <= 3) ? mode : VAD_MODE_VERY_AGGRESSIVE;
    if (fvad_set_mode(handle->vad, vadMode) < 0) {
        LOGE("Failed to set VAD mode to %d", vadMode);
        fvad_free(handle->vad);
        free(handle);
        return 0;
    }

    handle->sample_rate = VAD_SAMPLE_RATE;
    handle->mode = vadMode;
    handle->initialized = true;

    LOGI("VAD initialized: rate=%dHz, mode=%d, frame=%d samples",
         VAD_SAMPLE_RATE, vadMode, VAD_FRAME_SAMPLES_30);

    return reinterpret_cast<jlong>(handle);
}

/**
 * Process audio frame and detect speech (using short[] array)
 * @param nativeHandle Handle returned from nativeInitVad
 * @param audioFrame 480 samples (30ms at 16kHz) of 16-bit PCM audio
 * @return 1 if speech detected, 0 if silence, -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_phicomm_r1manager_server_voicebot_VadDetector_nativeIsSpeech(
        JNIEnv* env,
        jobject thiz,
        jlong nativeHandle,
        jshortArray audioFrame) {

    if (nativeHandle == 0) {
        LOGE("Invalid native handle (null)");
        return -1;
    }

    VadHandle* handle = reinterpret_cast<VadHandle*>(nativeHandle);
    if (!handle->initialized || !handle->vad) {
        LOGE("VAD not properly initialized");
        return -1;
    }

    // Validate frame length (must be 10, 20, or 30 ms)
    jsize frameLength = env->GetArrayLength(audioFrame);
    if (frameLength != 160 && frameLength != 320 && frameLength != 480) {
        LOGE("Invalid frame length: expected 160/320/480, got %d", frameLength);
        return -1;
    }

    // Get audio data (critical section - no copy mode for performance)
    jshort* samples = env->GetShortArrayElements(audioFrame, nullptr);
    if (!samples) {
        LOGE("Failed to get audio frame elements");
        return -1;
    }

    // Process frame through VAD
    int result = fvad_process(handle->vad, samples, frameLength);

    // Release array (JNI_ABORT = don't copy back, read-only operation)
    env->ReleaseShortArrayElements(audioFrame, samples, JNI_ABORT);

    if (result < 0) {
        LOGE("VAD processing error");
        return -1;
    }

    return result; // 1 = speech, 0 = silence
}

/**
 * Process audio frame from byte[] array (more efficient for AudioRecord)
 * @param nativeHandle Handle returned from nativeInitVad
 * @param audioData Raw PCM bytes (little-endian 16-bit)
 * @param offset Start offset in array
 * @param length Number of bytes (must be 320/640/960 for 10/20/30ms)
 * @return 1 if speech detected, 0 if silence, -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_phicomm_r1manager_server_voicebot_VadDetector_nativeIsSpeechBytes(
        JNIEnv* env,
        jobject thiz,
        jlong nativeHandle,
        jbyteArray audioData,
        jint offset,
        jint length) {

    if (nativeHandle == 0) {
        LOGE("Invalid native handle (null)");
        return -1;
    }

    VadHandle* handle = reinterpret_cast<VadHandle*>(nativeHandle);
    if (!handle->initialized || !handle->vad) {
        LOGE("VAD not properly initialized");
        return -1;
    }

    // Validate byte length (samples * 2 bytes per sample)
    int numSamples = length / 2;
    if (numSamples != 160 && numSamples != 320 && numSamples != 480) {
        LOGE("Invalid byte length: %d (samples=%d, expected 160/320/480)", length, numSamples);
        return -1;
    }

    // Get byte array
    jbyte* bytes = env->GetByteArrayElements(audioData, nullptr);
    if (!bytes) {
        LOGE("Failed to get audio data elements");
        return -1;
    }

    // Cast to int16_t* (assumes little-endian, which is standard on Android)
    const int16_t* samples = reinterpret_cast<const int16_t*>(bytes + offset);

    // Process frame through VAD
    int result = fvad_process(handle->vad, samples, numSamples);

    // Release array
    env->ReleaseByteArrayElements(audioData, bytes, JNI_ABORT);

    if (result < 0) {
        LOGE("VAD processing error");
        return -1;
    }

    return result;
}

/**
 * Reset VAD state (call when starting new utterance detection)
 * @param nativeHandle Handle returned from nativeInitVad
 */
JNIEXPORT void JNICALL
Java_com_phicomm_r1manager_server_voicebot_VadDetector_nativeResetVad(
        JNIEnv* env,
        jobject thiz,
        jlong nativeHandle) {

    if (nativeHandle == 0) {
        return;
    }

    VadHandle* handle = reinterpret_cast<VadHandle*>(nativeHandle);
    if (handle->vad) {
        fvad_reset(handle->vad);
        // Restore settings after reset
        fvad_set_sample_rate(handle->vad, handle->sample_rate);
        fvad_set_mode(handle->vad, handle->mode);
        LOGD("VAD state reset");
    }
}

/**
 * Free VAD resources
 * @param nativeHandle Handle returned from nativeInitVad
 */
JNIEXPORT void JNICALL
Java_com_phicomm_r1manager_server_voicebot_VadDetector_nativeFreeVad(
        JNIEnv* env,
        jobject thiz,
        jlong nativeHandle) {

    if (nativeHandle == 0) {
        LOGD("nativeFreeVad called with null handle (already freed?)");
        return;
    }

    VadHandle* handle = reinterpret_cast<VadHandle*>(nativeHandle);

    if (handle->vad) {
        fvad_free(handle->vad);
        handle->vad = nullptr;
    }

    handle->initialized = false;
    free(handle);

    LOGI("VAD resources freed");
}

/**
 * Get expected frame size in samples for 30ms
 * @return Number of samples per frame (480 for 30ms @ 16kHz)
 */
JNIEXPORT jint JNICALL
Java_com_phicomm_r1manager_server_voicebot_VadDetector_nativeGetFrameSize(
        JNIEnv* env,
        jobject thiz) {
    return VAD_FRAME_SAMPLES_30;
}

/**
 * Get expected frame size in bytes for 30ms
 * @return Number of bytes per frame (960 for 30ms @ 16kHz, 16-bit)
 */
JNIEXPORT jint JNICALL
Java_com_phicomm_r1manager_server_voicebot_VadDetector_nativeGetFrameSizeBytes(
        JNIEnv* env,
        jobject thiz) {
    return VAD_FRAME_SAMPLES_30 * 2;  // 16-bit = 2 bytes per sample
}

} // extern "C"
