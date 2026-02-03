/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 */

#include "vad_sp.h"
#include "vad_core.h"

// Noise estimation using minimum statistics
void WebRtcVad_FindMinimum(VadInstT* inst, int16_t* features) {
    int i, j;
    int16_t pos;
    int16_t tmp16;
    int16_t* index_ptr;
    int16_t* value_ptr;

    for (i = 0; i < kNumChannels; i++) {
        index_ptr = &inst->index_vector[i * 16];
        value_ptr = &inst->low_value_vector[i * 16];

        // Find minimum over sliding window
        int16_t min_val = features[i];
        int16_t min_pos = 0;

        // Update running minimum
        for (j = 0; j < 16; j++) {
            if (value_ptr[j] < min_val) {
                min_val = value_ptr[j];
                min_pos = (int16_t)j;
            }
        }

        // Shift window and insert new value
        for (j = 15; j > 0; j--) {
            value_ptr[j] = value_ptr[j - 1];
            index_ptr[j] = index_ptr[j - 1];
        }
        value_ptr[0] = features[i];
        index_ptr[0] = 0;

        // Update mean value for noise estimation
        if (min_val < inst->mean_value[i]) {
            // Slowly adapt noise floor down
            inst->mean_value[i] = (int16_t)(((int32_t)inst->mean_value[i] * 31 + min_val) >> 5);
        } else {
            // Slowly adapt noise floor up
            inst->mean_value[i] = (int16_t)(((int32_t)inst->mean_value[i] * 63 + min_val) >> 6);
        }

        // Update noise model
        int16_t noise_update = (int16_t)(((int32_t)(features[i] - inst->noise_means[i * 2]) * 655) >> 15);
        inst->noise_means[i * 2] += noise_update;
        inst->noise_means[i * 2 + 1] += noise_update >> 1;
    }

    inst->frame_counter++;
}
