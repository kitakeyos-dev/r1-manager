/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 */

#ifndef VAD_FILTERBANK_H_
#define VAD_FILTERBANK_H_

#include "vad_core.h"

int16_t WebRtcVad_CalculateFeatures(VadInstT* inst,
                                    const int16_t* audio_frame,
                                    size_t frame_length,
                                    int16_t* features);

#endif  // VAD_FILTERBANK_H_
