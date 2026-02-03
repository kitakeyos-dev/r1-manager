package com.phicomm.r1manager.server.manager;

import android.content.Context;
import com.phicomm.r1manager.util.AppLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import ai.kitt.snowboy.SnowboyDetect;

public class SnowboyHotwordDetector {
    private static final String TAG = "SnowboyHotwordDetector";

    // Assets from r1-helper
    private static final String COMMON_RES = "snowboy/common.res";
    private static final String MODEL_FILE = "snowboy/alexa.umdl"; // Using alexa model as default

    // Sensitivity
    private static final String SENSITIVITY = "0.8";
    private static final float AUDIO_GAIN = 1.0f;

    private SnowboyDetect detector;
    private final Context context;

    static {
        try {
            System.loadLibrary("snowboy-detect-android");
        } catch (UnsatisfiedLinkError e) {
            AppLog.e(TAG, "Failed to load snowboy-detect-android: " + e.getMessage());
        }
    }

    public SnowboyHotwordDetector(Context context) {
        this.context = context;
        initialize();
    }

    private void initialize() {
        try {
            String commonResPath = copyAssetToStorage(COMMON_RES, "common.res");
            String modelPath = copyAssetToStorage(MODEL_FILE, "alexa.umdl");

            if (new File(commonResPath).exists() && new File(modelPath).exists()) {
                detector = new SnowboyDetect(commonResPath, modelPath);
                detector.setSensitivity(SENSITIVITY);
                detector.setAudioGain(AUDIO_GAIN);
                detector.applyFrontend(true);
                AppLog.i(TAG, "Snowboy initialized successfully. Sensitivity=" + SENSITIVITY);
                AppLog.i(TAG, "SampleRate=" + detector.sampleRate() + " Channels=" + detector.numChannels());
            } else {
                AppLog.e(TAG, "Model files missing in storage. common=" + commonResPath + " model=" + modelPath);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to initialize Snowboy", e);
        }
    }

    private String copyAssetToStorage(String assetPath, String outFilename) throws IOException {
        File outFile = new File(context.getFilesDir(), outFilename);
        if (!outFile.exists()) {
            try (InputStream in = context.getAssets().open(assetPath);
                    FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                AppLog.i(TAG, "Copied asset " + assetPath + " to " + outFile.getAbsolutePath());
            } catch (IOException e) {
                AppLog.e(TAG, "Asset " + assetPath + " not found in APK. " + e.getMessage());
                throw e;
            }
        }
        return outFile.getAbsolutePath();
    }

    public boolean detect(byte[] pcmData) {
        if (detector == null)
            return false;

        // Convert byte[] to short[]
        short[] audioData = new short[pcmData.length / 2];
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(audioData);

        // runDetection is lowercase in r1-helper wrapper
        int result = detector.runDetection(audioData, audioData.length);
        if (result >= 1) {
            AppLog.i(TAG, "Hotword Detected! result=" + result);
            return true;
        } else if (result == -1) {
            AppLog.e(TAG, "Snowboy detection error");
        }
        return false;
    }

    public void reset() {
        if (detector != null) {
            detector.reset();
        }
    }

    public void release() {
        if (detector != null) {
            detector.delete();
            detector = null;
        }
    }
}
