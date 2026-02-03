/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 */

#ifndef SIGNAL_PROCESSING_LIBRARY_H_
#define SIGNAL_PROCESSING_LIBRARY_H_

#include <stdint.h>
#include <stddef.h>

// State for 48kHz to 8kHz downsampling
typedef struct {
    int32_t S_48_48[16];
    int32_t S_48_32[8];
    int32_t S_32_16[8];
    int32_t S_16_8[8];
} WebRtcSpl_State48khzTo8khz;

// Initialize 48kHz to 8kHz state
static inline void WebRtcSpl_ResetResample48khzTo8khz(WebRtcSpl_State48khzTo8khz* state) {
    int i;
    for (i = 0; i < 16; i++) state->S_48_48[i] = 0;
    for (i = 0; i < 8; i++) state->S_48_32[i] = 0;
    for (i = 0; i < 8; i++) state->S_32_16[i] = 0;
    for (i = 0; i < 8; i++) state->S_16_8[i] = 0;
}

// Downsample 48kHz to 8kHz
void WebRtcSpl_Resample48khzTo8khz(const int16_t* input, int16_t* output,
                                    WebRtcSpl_State48khzTo8khz* state,
                                    int32_t* tmpmem);

// Get scaling square
int16_t WebRtcSpl_GetScalingSquare(const int16_t* in_vector,
                                    size_t in_vector_length,
                                    size_t times);

// Division operations
int32_t WebRtcSpl_DivW32W16(int32_t num, int16_t den);
int32_t WebRtcSpl_DivW32W16ResW16(int32_t num, int16_t den);

// Energy calculation
int32_t WebRtcSpl_Energy(const int16_t* vector, size_t vector_length, int* scale_factor);

// Norm operations
int16_t WebRtcSpl_NormW32(int32_t value);
int16_t WebRtcSpl_NormW16(int16_t value);

// Saturation
static inline int16_t WebRtcSpl_SatW32ToW16(int32_t value) {
    if (value > 32767) return 32767;
    if (value < -32768) return -32768;
    return (int16_t)value;
}

// Max/Min
static inline int16_t WebRtcSpl_MaxValueW16(const int16_t* vector, size_t length) {
    int16_t max_val = vector[0];
    size_t i;
    for (i = 1; i < length; i++) {
        if (vector[i] > max_val) max_val = vector[i];
    }
    return max_val;
}

static inline int16_t WebRtcSpl_MinValueW16(const int16_t* vector, size_t length) {
    int16_t min_val = vector[0];
    size_t i;
    for (i = 1; i < length; i++) {
        if (vector[i] < min_val) min_val = vector[i];
    }
    return min_val;
}

#endif  // SIGNAL_PROCESSING_LIBRARY_H_
