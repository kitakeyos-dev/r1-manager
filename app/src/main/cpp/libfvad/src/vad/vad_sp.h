/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 */

#ifndef VAD_SP_H_
#define VAD_SP_H_

#include "vad_core.h"

// Find minimum values for noise estimation
void WebRtcVad_FindMinimum(VadInstT* inst, int16_t* features);

#endif  // VAD_SP_H_
