/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 */

#include "signal_processing_library.h"

int32_t WebRtcSpl_Energy(const int16_t* vector, size_t vector_length, int* scale_factor) {
    int32_t energy = 0;
    size_t i;
    int scaling = 0;

    // Calculate raw energy
    for (i = 0; i < vector_length; i++) {
        int32_t tmp = (int32_t)vector[i] * (int32_t)vector[i];
        energy += tmp >> 8;  // Scale down to prevent overflow
        scaling = 8;
    }

    if (scale_factor) *scale_factor = scaling;
    return energy;
}

int16_t WebRtcSpl_GetScalingSquare(const int16_t* in_vector,
                                    size_t in_vector_length,
                                    size_t times) {
    int16_t max_val = 0;
    size_t i;

    for (i = 0; i < in_vector_length; i++) {
        int16_t abs_val = in_vector[i] < 0 ? -in_vector[i] : in_vector[i];
        if (abs_val > max_val) max_val = abs_val;
    }

    // Calculate required scaling
    int16_t scale = 0;
    while (max_val > 16383 && scale < 15) {
        max_val >>= 1;
        scale++;
    }

    return scale;
}

int16_t WebRtcSpl_NormW32(int32_t value) {
    int16_t zeros = 0;

    if (value == 0) return 31;
    if (value < 0) value = ~value;

    while ((value & 0x40000000) == 0) {
        value <<= 1;
        zeros++;
    }

    return zeros;
}

int16_t WebRtcSpl_NormW16(int16_t value) {
    int16_t zeros = 0;

    if (value == 0) return 15;
    if (value < 0) value = ~value;

    while ((value & 0x4000) == 0) {
        value <<= 1;
        zeros++;
    }

    return zeros;
}
