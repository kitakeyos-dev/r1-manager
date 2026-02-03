/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 */

#include "signal_processing_library.h"
#include <string.h>

// Simple 48kHz to 8kHz resampling (6x decimation)
void WebRtcSpl_Resample48khzTo8khz(const int16_t* input, int16_t* output,
                                    WebRtcSpl_State48khzTo8khz* state,
                                    int32_t* tmpmem) {
    // Simplified: average every 6 samples
    size_t in_idx = 0;
    size_t out_idx = 0;

    while (in_idx + 5 < 480) {  // Assuming 10ms frame at 48kHz
        int32_t sum = 0;
        for (int i = 0; i < 6; i++) {
            sum += input[in_idx + i];
        }
        sum = (sum + 3) / 6;  // Average with rounding

        // Apply low-pass filter from state
        sum = (sum + state->S_16_8[0]) >> 1;
        state->S_16_8[0] = (int32_t)input[in_idx + 5];

        // Saturate
        if (sum > 32767) sum = 32767;
        if (sum < -32768) sum = -32768;

        output[out_idx++] = (int16_t)sum;
        in_idx += 6;
    }
}
