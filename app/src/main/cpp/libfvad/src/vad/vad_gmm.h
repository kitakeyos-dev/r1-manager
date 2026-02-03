/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 */

#ifndef VAD_GMM_H_
#define VAD_GMM_H_

#include <stdint.h>

// Calculate Gaussian probability
int32_t WebRtcVad_GaussianProbability(int16_t input,
                                       int16_t mean,
                                       int16_t std,
                                       int16_t weight);

#endif  // VAD_GMM_H_
