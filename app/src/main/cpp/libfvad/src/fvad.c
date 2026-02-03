/*
 * Copyright (c) 2016 Daniel Pirch
 *
 * Use of this source code is governed by a BSD-style license
 * that can be found in the LICENSE file in the root of the source
 * tree. An additional intellectual property rights grant can be found
 * in the file PATENTS.  All contributing project authors may
 * be found in the AUTHORS file in the root of the source tree.
 */

#include <stdlib.h>
#include <string.h>

#include "fvad.h"
#include "vad/vad_core.h"

// internal state of a VAD instance
struct Fvad {
    VadInstT core;
    int sample_rate;
};

Fvad *fvad_new(void)
{
    Fvad *inst = malloc(sizeof *inst);
    if (inst) {
        fvad_reset(inst);
    }
    return inst;
}

void fvad_free(Fvad *inst)
{
    free(inst);
}

void fvad_reset(Fvad *inst)
{
    // Initialize the core VAD component
    WebRtcVad_InitCore(&inst->core);
    inst->sample_rate = 8000;
}

int fvad_set_mode(Fvad *inst, int mode)
{
    return WebRtcVad_set_mode_core(&inst->core, mode);
}

int fvad_set_sample_rate(Fvad *inst, int sample_rate)
{
    switch (sample_rate) {
        case  8000:
        case 16000:
        case 32000:
        case 48000:
            inst->sample_rate = sample_rate;
            return 0;
        default:
            return -1;
    }
}

static int valid_frame_length(size_t length, int sample_rate)
{
    // 10, 20, or 30 ms frame length
    if (length == (size_t)(sample_rate / 100) ||
        length == (size_t)(sample_rate / 50)  ||
        length == (size_t)(sample_rate * 3 / 100))
        return 1;
    else
        return 0;
}

int fvad_process(Fvad *inst, const int16_t *frame, size_t length)
{
    if (!valid_frame_length(length, inst->sample_rate))
        return -1;

    return WebRtcVad_CalcVad(&inst->core, inst->sample_rate, frame, length);
}
