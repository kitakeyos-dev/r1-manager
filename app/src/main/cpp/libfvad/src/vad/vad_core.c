/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "vad_core.h"
#include "vad_filterbank.h"
#include "vad_gmm.h"
#include "vad_sp.h"
#include <string.h>

// Gaussian probabilities table
static const int16_t kNoiseDataWeights[kTableSize] = {
    34, 62, 72, 66, 53, 25, 94, 66, 56, 62, 75, 103
};

static const int16_t kSpeechDataWeights[kTableSize] = {
    48, 82, 45, 87, 50, 47, 80, 46, 83, 41, 78, 81
};

static const int16_t kNoiseDataMeans[kTableSize] = {
    6738, 4892, 7065, 6715, 6771, 3369, 7646, 3863, 7820, 7266, 5020, 4362
};

static const int16_t kSpeechDataMeans[kTableSize] = {
    8306, 10085, 10078, 11823, 11843, 6309, 9473, 9571, 10879, 7581, 8180, 7483
};

static const int16_t kNoiseDataStds[kTableSize] = {
    378, 1064, 493, 582, 688, 593, 474, 697, 475, 277, 1198, 1106
};

static const int16_t kSpeechDataStds[kTableSize] = {
    555, 505, 567, 524, 585, 1231, 509, 828, 492, 1540, 1079, 850
};

// Mode-dependent parameters
static const int16_t kOverHangMax1[3] = { 8, 6, 4 };
static const int16_t kOverHangMax2[3] = { 14, 9, 5 };
static const int16_t kLocalThreshold[3] = { 24, 21, 24 };
static const int16_t kGlobalThreshold[3] = { 57, 48, 57 };

int WebRtcVad_InitCore(VadInstT* inst) {
    int i;

    // Initialize filter states
    memset(inst->downsampling_filter_states, 0, sizeof(inst->downsampling_filter_states));
    memset(&inst->state_48_to_8, 0, sizeof(inst->state_48_to_8));

    // Initialize noise/speech estimates
    for (i = 0; i < kTableSize; i++) {
        inst->noise_means[i] = kNoiseDataMeans[i];
        inst->speech_means[i] = kSpeechDataMeans[i];
        inst->noise_stds[i] = kNoiseDataStds[i];
        inst->speech_stds[i] = kSpeechDataStds[i];
    }

    // Initialize index vectors for quantile estimation
    for (i = 0; i < 16 * kNumChannels; i++) {
        inst->low_value_vector[i] = 10000;
        inst->index_vector[i] = 0;
    }

    for (i = 0; i < kNumChannels; i++) {
        inst->mean_value[i] = 1600;
    }

    // Initialize state
    memset(inst->upper_state, 0, sizeof(inst->upper_state));
    memset(inst->lower_state, 0, sizeof(inst->lower_state));
    memset(inst->hp_filter_state, 0, sizeof(inst->hp_filter_state));

    // Default mode is 0 (quality mode)
    inst->over_hang_max_1[0] = kOverHangMax1[0];
    inst->over_hang_max_2[0] = kOverHangMax2[0];
    inst->individual[0] = kLocalThreshold[0];
    inst->total[0] = kGlobalThreshold[0];

    inst->over_hang_max_1[1] = kOverHangMax1[1];
    inst->over_hang_max_2[1] = kOverHangMax2[1];
    inst->individual[1] = kLocalThreshold[1];
    inst->total[1] = kGlobalThreshold[1];

    inst->over_hang_max_1[2] = kOverHangMax1[2];
    inst->over_hang_max_2[2] = kOverHangMax2[2];
    inst->individual[2] = kLocalThreshold[2];
    inst->total[2] = kGlobalThreshold[2];

    inst->over_hang = 0;
    inst->num_of_speech = 0;
    inst->frame_counter = 0;
    inst->vad = 0;
    inst->init_flag = 42;  // Magic number indicating initialization

    return 0;
}

int WebRtcVad_set_mode_core(VadInstT* inst, int mode) {
    switch (mode) {
        case 0:  // Quality mode
            inst->over_hang_max_1[0] = kOverHangMax1[0];
            inst->over_hang_max_2[0] = kOverHangMax2[0];
            inst->individual[0] = kLocalThreshold[0];
            inst->total[0] = kGlobalThreshold[0];
            break;
        case 1:  // Low bitrate mode
            inst->over_hang_max_1[0] = kOverHangMax1[1];
            inst->over_hang_max_2[0] = kOverHangMax2[1];
            inst->individual[0] = kLocalThreshold[1];
            inst->total[0] = kGlobalThreshold[1];
            break;
        case 2:  // Aggressive mode
            inst->over_hang_max_1[0] = kOverHangMax1[2];
            inst->over_hang_max_2[0] = kOverHangMax2[2];
            inst->individual[0] = kLocalThreshold[2];
            inst->total[0] = kGlobalThreshold[2];
            break;
        case 3:  // Very aggressive mode
            inst->over_hang_max_1[0] = 2;
            inst->over_hang_max_2[0] = 2;
            inst->individual[0] = 28;
            inst->total[0] = 66;
            break;
        default:
            return -1;
    }
    return 0;
}

int WebRtcVad_CalcVad(VadInstT* inst, int fs, const int16_t* audio_frame, size_t frame_length) {
    int16_t vad = 0;
    size_t i;

    if (inst->init_flag != 42) {
        return -1;
    }

    // Calculate features and VAD decision
    int16_t features[kNumChannels];
    int16_t total_power;

    // Calculate filterbank features
    total_power = WebRtcVad_CalculateFeatures(inst, audio_frame, frame_length, features);

    if (total_power < kMinEnergy) {
        // Very low energy, likely silence
        inst->over_hang = 0;
        inst->vad = 0;
        return 0;
    }

    // Calculate log-likelihood ratio for each channel
    int32_t h0 = 0;  // Noise hypothesis
    int32_t h1 = 0;  // Speech hypothesis

    for (i = 0; i < kNumChannels; i++) {
        int16_t feature_val = features[i];

        // Noise model
        int32_t noise_prob = WebRtcVad_GaussianProbability(
            feature_val, inst->noise_means[i * 2], inst->noise_stds[i * 2], kNoiseDataWeights[i * 2]);
        noise_prob += WebRtcVad_GaussianProbability(
            feature_val, inst->noise_means[i * 2 + 1], inst->noise_stds[i * 2 + 1], kNoiseDataWeights[i * 2 + 1]);

        // Speech model
        int32_t speech_prob = WebRtcVad_GaussianProbability(
            feature_val, inst->speech_means[i * 2], inst->speech_stds[i * 2], kSpeechDataWeights[i * 2]);
        speech_prob += WebRtcVad_GaussianProbability(
            feature_val, inst->speech_means[i * 2 + 1], inst->speech_stds[i * 2 + 1], kSpeechDataWeights[i * 2 + 1]);

        h0 += noise_prob;
        h1 += speech_prob;
    }

    // Decision logic with hysteresis
    int32_t log_likelihood_ratio = h1 - h0;

    if (log_likelihood_ratio >= inst->total[0]) {
        vad = 1;
        inst->over_hang = inst->over_hang_max_1[0];
    } else if (inst->over_hang > 0) {
        vad = 1;
        inst->over_hang--;
    } else {
        vad = 0;
    }

    // Update model parameters (slowly adapt to new noise/speech)
    WebRtcVad_FindMinimum(inst, features);

    inst->vad = vad;
    return vad;
}
