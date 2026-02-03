package com.phicomm.r1manager.server.voicebot;

import com.phicomm.r1manager.util.AppLog;

public class OpusDecoder {
    private static final String TAG = "OpusDecoder";

    static {
        System.loadLibrary("opus");
        System.loadLibrary("app");
    }

    private long nativeDecoderHandle = 0;
    private int frameSize;
    private int channels;

    public OpusDecoder(int sampleRate, int channels, int frameSizeMs) {
        this.channels = channels;
        this.frameSize = (sampleRate * frameSizeMs) / 1000;

        nativeDecoderHandle = nativeInitDecoder(sampleRate, channels);
        if (nativeDecoderHandle == 0) {
            throw new IllegalStateException("Failed to initialize Opus decoder");
        }
    }

    public byte[] decode(byte[] opusData) {
        int maxPcmSize = frameSize * channels * 2; // 16-bit PCM
        byte[] pcmBuffer = new byte[maxPcmSize];

        int decodedBytes = nativeDecodeBytes(
                nativeDecoderHandle,
                opusData,
                opusData.length,
                pcmBuffer,
                maxPcmSize);

        if (decodedBytes > 0) {
            if (decodedBytes < pcmBuffer.length) {
                byte[] result = new byte[decodedBytes];
                System.arraycopy(pcmBuffer, 0, result, 0, decodedBytes);
                return result;
            } else {
                return pcmBuffer;
            }
        } else {
            AppLog.e(TAG, "Failed to decode frame");
            return null;
        }
    }

    public void release() {
        if (nativeDecoderHandle != 0) {
            nativeReleaseDecoder(nativeDecoderHandle);
            nativeDecoderHandle = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            release();
        } finally {
            super.finalize();
        }
    }

    private native long nativeInitDecoder(int sampleRate, int channels);

    private native int nativeDecodeBytes(
            long decoderHandle,
            byte[] inputBuffer,
            int inputSize,
            byte[] outputBuffer,
            int maxOutputSize);

    private native void nativeReleaseDecoder(long decoderHandle);
}
