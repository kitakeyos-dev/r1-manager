/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VAD_CORE_H_
#define VAD_CORE_H_

#include "../signal_processing/signal_processing_library.h"

enum { kNumChannels = 6 };  // Number of frequency bands
enum { kNumGaussians = 2 }; // Number of Gaussian models per band
enum { kTableSize = kNumChannels * kNumGaussians };
enum { kMinEnergy = 10 };   // Minimum energy

// Spectrum Weighting
static const int16_t kSpectrumWeight[kNumChannels] = { 6, 8, 10, 12, 14, 16 };
static const int16_t kNoiseUpdateConst = 655;     // Q15
static const int16_t kSpeechUpdateConst = 6554;   // Q15
static const int16_t kBackEta = 154;              // Q8

typedef struct VadInstT_ {
    int vad;
    int32_t downsampling_filter_states[4];
    WebRtcSpl_State48khzTo8khz state_48_to_8;
    int16_t noise_means[kTableSize];
    int16_t speech_means[kTableSize];
    int16_t noise_stds[kTableSize];
    int16_t speech_stds[kTableSize];
    int32_t frame_counter;
    int16_t over_hang;
    int16_t num_of_speech;
    int16_t index_vector[16 * kNumChannels];
    int16_t low_value_vector[16 * kNumChannels];
    int16_t mean_value[kNumChannels];
    int16_t upper_state[5];
    int16_t lower_state[5];
    int16_t hp_filter_state[4];
    int16_t over_hang_max_1[3];
    int16_t over_hang_max_2[3];
    int16_t individual[3];
    int16_t total[3];
    int init_flag;
} VadInstT;

// Initialize the VAD
int WebRtcVad_InitCore(VadInstT* inst);

// Set the VAD mode
int WebRtcVad_set_mode_core(VadInstT* inst, int mode);

// Calculate VAD decision
int WebRtcVad_CalcVad(VadInstT* inst, int fs, const int16_t* audio_frame, size_t frame_length);

#endif  // VAD_CORE_H_
