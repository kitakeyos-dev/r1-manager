package com.phicomm.r1manager.server.voicebot;

import com.phicomm.r1manager.util.AppLog;

public class OpusEncoder {
    private static final String TAG = "OpusEncoder";

    static {
        System.loadLibrary("opus");
        System.loadLibrary("app");
    }

    private long nativeEncoderHandle = 0;
    private int frameSize;
    private int channels;

    public OpusEncoder(int sampleRate, int channels, int frameSizeMs) {
        this.channels = channels;
        this.frameSize = (sampleRate * frameSizeMs) / 1000;

        // OPUS_APPLICATION_VOIP = 2048
        nativeEncoderHandle = nativeInitEncoder(sampleRate, channels, 2048);
        if (nativeEncoderHandle == 0) {
            throw new IllegalStateException("Failed to initialize Opus encoder");
        }
    }

    public byte[] encode(byte[] pcmData) {
        int expectedBytes = frameSize * channels * 2; // 16-bit PCM
        if (pcmData.length != expectedBytes) {
            AppLog.e(TAG, "Input buffer size must be " + expectedBytes + " bytes (got " + pcmData.length + ")");
            return null;
        }

        byte[] outputBuffer = new byte[expectedBytes]; // Allocate sufficient buffer
        int encodedBytes = nativeEncodeBytes(
                nativeEncoderHandle,
                pcmData,
                pcmData.length,
                outputBuffer,
                outputBuffer.length);

        if (encodedBytes > 0) {
            byte[] result = new byte[encodedBytes];
            System.arraycopy(outputBuffer, 0, result, 0, encodedBytes);
            return result;
        } else {
            AppLog.e(TAG, "Failed to encode frame");
            return null;
        }
    }

    public void release() {
        if (nativeEncoderHandle != 0) {
            nativeReleaseEncoder(nativeEncoderHandle);
            nativeEncoderHandle = 0;
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

    private native long nativeInitEncoder(int sampleRate, int channels, int application);

    private native int nativeEncodeBytes(
            long encoderHandle,
            byte[] inputBuffer,
            int inputSize,
            byte[] outputBuffer,
            int maxOutputSize);

    private native void nativeReleaseEncoder(long encoderHandle);
}
