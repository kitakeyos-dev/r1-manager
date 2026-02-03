package com.phicomm.r1manager.server.voicebot;

import com.phicomm.r1manager.util.AppLog;

/**
 * Voice Activity Detection (VAD) using native libfvad (WebRTC VAD).
 *
 * Provides speech detection for client-side endpointing to prevent
 * Whisper hallucination by only sending complete sentences to server.
 *
 * Usage:
 *   VadDetector vad = new VadDetector();
 *   if (vad.initialize(VadDetector.MODE_VERY_AGGRESSIVE)) {
 *       boolean isSpeech = vad.isSpeech(pcmFrame);  // 30ms frame
 *       vad.release();
 *   }
 */
public class VadDetector {
    private static final String TAG = "VadDetector";

    // Load native library (same as opus, now includes VAD)
    static {
        System.loadLibrary("app");
    }

    // VAD aggressiveness modes
    public static final int MODE_QUALITY = 0;        // Less aggressive, fewer false negatives
    public static final int MODE_LOW_BITRATE = 1;    // Balanced
    public static final int MODE_AGGRESSIVE = 2;     // More aggressive
    public static final int MODE_VERY_AGGRESSIVE = 3; // Most aggressive, filters background noise

    // Audio configuration (must match AudioRecorder)
    public static final int SAMPLE_RATE = 16000;
    public static final int FRAME_SIZE_MS_10 = 10;
    public static final int FRAME_SIZE_MS_20 = 20;
    public static final int FRAME_SIZE_MS_30 = 30;

    // Frame sizes in samples
    public static final int FRAME_SAMPLES_10MS = 160;  // 10ms @ 16kHz
    public static final int FRAME_SAMPLES_20MS = 320;  // 20ms @ 16kHz
    public static final int FRAME_SAMPLES_30MS = 480;  // 30ms @ 16kHz

    // Frame sizes in bytes (16-bit PCM)
    public static final int FRAME_BYTES_10MS = 320;    // 160 samples * 2 bytes
    public static final int FRAME_BYTES_20MS = 640;    // 320 samples * 2 bytes
    public static final int FRAME_BYTES_30MS = 960;    // 480 samples * 2 bytes

    // Native handle
    private long nativeHandle = 0;
    private int mode;
    private volatile boolean initialized = false;

    // Native methods
    private native long nativeInitVad(int mode);
    private native int nativeIsSpeech(long handle, short[] audioFrame);
    private native int nativeIsSpeechBytes(long handle, byte[] audioData, int offset, int length);
    private native void nativeResetVad(long handle);
    private native void nativeFreeVad(long handle);
    private native int nativeGetFrameSize();
    private native int nativeGetFrameSizeBytes();

    public VadDetector() {
        this.mode = MODE_VERY_AGGRESSIVE;
    }

    public VadDetector(int mode) {
        this.mode = mode;
    }

    /**
     * Initialize the VAD detector.
     *
     * @param mode VAD aggressiveness mode (0-3)
     * @return true if initialization successful
     */
    public synchronized boolean initialize(int mode) {
        if (initialized) {
            AppLog.w(TAG, "VAD already initialized");
            return true;
        }

        this.mode = mode;
        nativeHandle = nativeInitVad(mode);

        if (nativeHandle == 0) {
            AppLog.e(TAG, "Failed to initialize native VAD");
            return false;
        }

        initialized = true;
        AppLog.i(TAG, "VAD initialized with mode " + mode);
        return true;
    }

    /**
     * Initialize with default mode (Very Aggressive).
     */
    public boolean initialize() {
        return initialize(MODE_VERY_AGGRESSIVE);
    }

    /**
     * Check if audio frame contains speech.
     *
     * @param audioFrame PCM samples (must be 160/320/480 samples for 10/20/30ms)
     * @return true if speech detected, false if silence
     * @throws IllegalStateException if not initialized
     * @throws IllegalArgumentException if invalid frame size
     */
    public boolean isSpeech(short[] audioFrame) {
        if (!initialized || nativeHandle == 0) {
            throw new IllegalStateException("VAD not initialized");
        }

        int length = audioFrame.length;
        if (length != FRAME_SAMPLES_10MS && length != FRAME_SAMPLES_20MS && length != FRAME_SAMPLES_30MS) {
            throw new IllegalArgumentException(
                    "Invalid frame size: " + length + ". Must be 160/320/480 samples.");
        }

        int result = nativeIsSpeech(nativeHandle, audioFrame);
        if (result < 0) {
            AppLog.e(TAG, "VAD processing error");
            return false;
        }

        return result == 1;
    }

    /**
     * Check if audio frame contains speech (byte array version).
     * More efficient for direct AudioRecord usage.
     *
     * @param audioData Raw PCM bytes (little-endian 16-bit)
     * @param offset Start offset in array
     * @param length Number of bytes (must be 320/640/960 for 10/20/30ms)
     * @return true if speech detected, false if silence
     */
    public boolean isSpeech(byte[] audioData, int offset, int length) {
        if (!initialized || nativeHandle == 0) {
            throw new IllegalStateException("VAD not initialized");
        }

        if (length != FRAME_BYTES_10MS && length != FRAME_BYTES_20MS && length != FRAME_BYTES_30MS) {
            throw new IllegalArgumentException(
                    "Invalid frame size: " + length + " bytes. Must be 320/640/960 bytes.");
        }

        int result = nativeIsSpeechBytes(nativeHandle, audioData, offset, length);
        if (result < 0) {
            AppLog.e(TAG, "VAD processing error");
            return false;
        }

        return result == 1;
    }

    /**
     * Reset VAD state. Call when starting a new utterance detection session.
     */
    public synchronized void reset() {
        if (initialized && nativeHandle != 0) {
            nativeResetVad(nativeHandle);
        }
    }

    /**
     * Release VAD resources. Must be called when done.
     */
    public synchronized void release() {
        if (nativeHandle != 0) {
            nativeFreeVad(nativeHandle);
            nativeHandle = 0;
        }
        initialized = false;
        AppLog.i(TAG, "VAD released");
    }

    /**
     * Check if VAD is initialized.
     */
    public boolean isInitialized() {
        return initialized && nativeHandle != 0;
    }

    /**
     * Get the expected frame size in samples for 30ms.
     */
    public int getFrameSize() {
        return FRAME_SAMPLES_30MS;
    }

    /**
     * Get the expected frame size in bytes for 30ms.
     */
    public int getFrameSizeBytes() {
        return FRAME_BYTES_30MS;
    }

    /**
     * Get current VAD mode.
     */
    public int getMode() {
        return mode;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (nativeHandle != 0) {
                AppLog.w(TAG, "VAD not released before finalize, releasing now");
                release();
            }
        } finally {
            super.finalize();
        }
    }
}
