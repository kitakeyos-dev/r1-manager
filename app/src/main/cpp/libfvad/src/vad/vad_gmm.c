/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 */

#include "vad_gmm.h"

// Gaussian probability calculation
// Uses fixed-point arithmetic for efficiency
int32_t WebRtcVad_GaussianProbability(int16_t input,
                                       int16_t mean,
                                       int16_t std,
                                       int16_t weight) {
    int32_t diff;
    int32_t tmp32;
    int32_t inv_std;
    int32_t prob;

    // Calculate difference from mean
    diff = (int32_t)input - (int32_t)mean;

    // Avoid division by zero
    if (std < 1) std = 1;

    // Calculate inverse of standard deviation (Q12)
    inv_std = (4096 * 128) / std;

    // Normalize difference by standard deviation
    tmp32 = (diff * inv_std) >> 12;

    // Calculate squared normalized difference
    int32_t squared = (tmp32 * tmp32) >> 8;

    // Gaussian exponent approximation: exp(-x^2/2)
    // Using piecewise linear approximation
    if (squared > 1024) {
        prob = 0;  // Very unlikely
    } else if (squared > 512) {
        prob = (1024 - squared) >> 2;
    } else if (squared > 256) {
        prob = 128 + ((512 - squared) >> 1);
    } else {
        prob = 256 - (squared >> 1);
    }

    // Apply weight
    prob = (prob * weight) >> 7;

    return prob;
}
