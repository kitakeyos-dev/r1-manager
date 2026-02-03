/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 */

#include "signal_processing_library.h"

int32_t WebRtcSpl_DivW32W16(int32_t num, int16_t den) {
    if (den == 0) return 0;
    return num / den;
}

int32_t WebRtcSpl_DivW32W16ResW16(int32_t num, int16_t den) {
    if (den == 0) return 0;
    int32_t result = num / den;
    if (result > 32767) return 32767;
    if (result < -32768) return -32768;
    return result;
}
