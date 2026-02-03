/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 */

#include "vad_filterbank.h"
#include "vad_core.h"
#include "../signal_processing/signal_processing_library.h"
#include <string.h>

// First-order high-pass filter coefficients
static const int16_t kHpZeroCoefs[3] = { 6631, -13262, 6631 };
static const int16_t kHpPoleCoefs[3] = { 16384, -32312, 15983 };

// Offset to add for log calculation
static const int32_t kLogConst = 1500;

// All-pass filter coefficients for downsampling
static const int16_t kAllPassCoefsQ15[2] = { 20972, 5571 };

// Calculate log base 2 approximation
static int16_t Log2Q8(int32_t x) {
    int16_t log2 = 0;
    int16_t shift = 0;

    if (x <= 0) return 0;

    // Find position of most significant bit
    while (x > 32767) {
        x >>= 1;
        shift++;
    }
    while (x < 16384 && shift > 0) {
        x <<= 1;
        shift--;
    }

    log2 = shift << 8;

    // Linear approximation for fractional part
    if (x > 16384) {
        log2 += (int16_t)(((x - 16384) * 256) / 16384);
    }

    return log2;
}

// Downsample from higher sample rate to 8kHz
static void Downsample(const int16_t* in, size_t in_length,
                       int16_t* out, int32_t* filter_state) {
    size_t i;
    int32_t tmp32;
    int16_t tmp16;

    for (i = 0; i < in_length / 2; i++) {
        // Simple 2x downsampling with low-pass filter
        tmp32 = (int32_t)in[i * 2] + (int32_t)in[i * 2 + 1];
        tmp32 = (tmp32 + 1) >> 1;  // Average

        // Apply simple IIR low-pass filter
        tmp32 = (tmp32 + filter_state[0]) >> 1;
        filter_state[0] = in[i * 2 + 1];

        // Saturate to 16-bit
        if (tmp32 > 32767) tmp32 = 32767;
        if (tmp32 < -32768) tmp32 = -32768;

        out[i] = (int16_t)tmp32;
    }
}

// Apply high-pass filter to remove DC offset
static void HighPassFilter(const int16_t* in, size_t length,
                           int16_t* out, int16_t* filter_state) {
    size_t i;
    int32_t tmp32;

    for (i = 0; i < length; i++) {
        // Simple first-order high-pass: y[n] = x[n] - x[n-1] + alpha * y[n-1]
        tmp32 = (int32_t)in[i] - (int32_t)filter_state[0];
        tmp32 += ((int32_t)filter_state[1] * 31000) >> 15;

        // Saturate
        if (tmp32 > 32767) tmp32 = 32767;
        if (tmp32 < -32768) tmp32 = -32768;

        filter_state[0] = in[i];
        filter_state[1] = (int16_t)tmp32;
        out[i] = (int16_t)tmp32;
    }
}

// Calculate energy in frequency band
static int32_t CalculateEnergy(const int16_t* data, size_t length) {
    size_t i;
    int32_t energy = 0;

    for (i = 0; i < length; i++) {
        energy += ((int32_t)data[i] * (int32_t)data[i]) >> 8;
    }

    return energy;
}

int16_t WebRtcVad_CalculateFeatures(VadInstT* inst,
                                    const int16_t* audio_frame,
                                    size_t frame_length,
                                    int16_t* features) {
    int16_t temp_buffer[240];
    int16_t hp_buffer[240];
    size_t data_length;
    int32_t total_energy = 0;
    int i;

    // Determine input sample rate and downsample to 8kHz if needed
    if (frame_length == 480) {
        // 16kHz, 30ms frame -> downsample to 8kHz
        Downsample(audio_frame, frame_length, temp_buffer, inst->downsampling_filter_states);
        data_length = 240;
    } else if (frame_length == 320) {
        // 16kHz, 20ms frame
        Downsample(audio_frame, frame_length, temp_buffer, inst->downsampling_filter_states);
        data_length = 160;
    } else if (frame_length == 160) {
        // 16kHz, 10ms frame or 8kHz, 20ms frame
        Downsample(audio_frame, frame_length, temp_buffer, inst->downsampling_filter_states);
        data_length = 80;
    } else if (frame_length == 240) {
        // 8kHz, 30ms frame
        memcpy(temp_buffer, audio_frame, frame_length * sizeof(int16_t));
        data_length = 240;
    } else if (frame_length == 80) {
        // 8kHz, 10ms frame
        memcpy(temp_buffer, audio_frame, frame_length * sizeof(int16_t));
        data_length = 80;
    } else {
        memcpy(temp_buffer, audio_frame, frame_length * sizeof(int16_t));
        data_length = frame_length;
    }

    // Apply high-pass filter
    HighPassFilter(temp_buffer, data_length, hp_buffer, inst->hp_filter_state);

    // Calculate energy in different frequency bands
    // Simplified: we split the signal into 6 bands using simple energy measurement
    size_t band_size = data_length / kNumChannels;
    if (band_size < 1) band_size = 1;

    for (i = 0; i < kNumChannels; i++) {
        size_t start = i * band_size;
        size_t len = (i == kNumChannels - 1) ? (data_length - start) : band_size;

        if (start >= data_length) {
            features[i] = 0;
            continue;
        }

        int32_t energy = CalculateEnergy(&hp_buffer[start], len);
        total_energy += energy;

        // Convert to log domain
        features[i] = Log2Q8(energy + kLogConst);
    }

    // Return total power indication
    return (int16_t)(total_energy >> 8);
}
