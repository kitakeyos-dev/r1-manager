package com.phicomm.r1manager.server.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.audiofx.Visualizer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import com.phicomm.r1manager.util.AppLog;

import java.util.ArrayList;
import java.util.List;

import com.phicomm.r1manager.server.manager.LedManager;
import com.phicomm.r1manager.server.manager.MusicServiceManager;

/**
 * Service to analyze audio output and provide visualization data for LED sync
 */
public class AudioVisualizerService extends Service implements LedManager.LedActivitySource {

    private static final String TAG = "AudioVisualizerService";
    private static final int CAPTURE_RATE_MILLIS = 50; // 20 FPS
    private static final int FFT_SIZE = 128; // Small size for performance

    private Visualizer visualizer;
    private boolean isEnabled = false;
    private boolean isAudioPlaying = false;
    private Handler handler = new Handler();

    private List<AudioDataListener> listeners = new ArrayList<>();

    // Beat detection
    private float lastBass = 0;
    private long lastBeatTime = 0;
    private static final float BEAT_THRESHOLD = 0.3f;
    private static final long BEAT_MIN_INTERVAL = 200; // ms

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public AudioVisualizerService getService() {
            return AudioVisualizerService.this;
        }
    }

    /**
     * Audio data container
     */
    public static class AudioData {
        public float amplitude; // 0.0 - 1.0
        public float bass; // Low frequency (20-250 Hz)
        public float mid; // Mid frequency (250-4000 Hz)
        public float treble; // High frequency (4000-20000 Hz)
        public boolean beatDetected; // True when beat is detected
        public long timestamp;

        public AudioData() {
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Listener interface for audio data updates
     */
    public interface AudioDataListener {
        void onAudioData(AudioData data);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.d(TAG, "AudioVisualizerService created");

        // Register with manager
        MusicServiceManager.registerAudioVisualizerService(this);
        LedManager.getInstance().registerActivitySource(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLog.d(TAG, "AudioVisualizerService started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppLog.d(TAG, "AudioVisualizerService destroyed");
        LedManager.getInstance().unregisterActivitySource(this);
        disable();
    }

    /**
     * Enable audio visualization
     */
    public boolean enable() {
        if (isEnabled) {
            AppLog.d(TAG, "Visualizer already enabled");
            return true;
        }

        try {
            // Create visualizer on audio session 0 (mix output)
            visualizer = new Visualizer(0);
            visualizer.setCaptureSize(FFT_SIZE);
            visualizer.setDataCaptureListener(
                    new Visualizer.OnDataCaptureListener() {
                        @Override
                        public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                            // Not used
                        }

                        @Override
                        public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                            // AppLog.v(TAG, "FFT Capture: " + fft.length + " bytes");
                            processAudioData(fft, samplingRate);
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    false,
                    true // Enable FFT
            );

            visualizer.setEnabled(true);
            isEnabled = true;

            AppLog.d(TAG, "Visualizer enabled successfully");
            return true;

        } catch (Exception e) {
            AppLog.e(TAG, "Error enabling visualizer", e);
            return false;
        }
    }

    /**
     * Disable audio visualization
     */
    public boolean disable() {
        if (!isEnabled) {
            return true;
        }

        try {
            if (visualizer != null) {
                visualizer.setEnabled(false);
                visualizer.release();
                visualizer = null;
            }

            isEnabled = false;
            AppLog.d(TAG, "Visualizer disabled");
            return true;

        } catch (Exception e) {
            AppLog.e(TAG, "Error disabling visualizer", e);
            return false;
        }
    }

    /**
     * Check if visualizer is enabled
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    public boolean isAudioPlaying() {
        return isAudioPlaying;
    }

    /**
     * Add audio data listener
     */
    public void addListener(AudioDataListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove audio data listener
     */
    public void removeListener(AudioDataListener listener) {
        listeners.remove(listener);
    }

    /**
     * Process FFT data into frequency bands
     */
    /**
     * Process FFT data into frequency bands
     */
    private void processAudioData(byte[] fft, int samplingRate) {
        if (fft == null || fft.length == 0) {
            // AppLog.w(TAG, "Empty FFT data");
            return;
        }

        AudioData data = new AudioData();

        // Calculate energy in bands
        float[] magn = new float[fft.length / 2];
        float totalEnergy = 0;

        for (int i = 0; i < fft.length / 2; i++) {
            byte real = fft[2 * i];
            byte imag = fft[2 * i + 1];
            magn[i] = (float) Math.hypot(real, imag);
            totalEnergy += magn[i];
        }

        // Calculate average amplitude (0-1)
        data.amplitude = Math.min(1.0f, totalEnergy / (fft.length * 32.0f));

        // Define frequency bands (approximate for 44.1kHz / 1024 FFT)
        int bassEnd = fft.length / 20; // ~1100Hz
        int midEnd = fft.length / 5; // ~4400Hz

        // Calculate band energies
        float bassEnergy = 0;
        for (int i = 0; i < bassEnd; i++)
            bassEnergy += magn[i];

        float midEnergy = 0;
        for (int i = bassEnd; i < midEnd; i++)
            midEnergy += magn[i];

        float trebleEnergy = 0;
        for (int i = midEnd; i < magn.length; i++)
            trebleEnergy += magn[i];

        // Normalize (heuristic scaling)
        data.bass = Math.min(1.0f, bassEnergy / (bassEnd * 32.0f) * 3.0f);
        data.mid = Math.min(1.0f, midEnergy / ((midEnd - bassEnd) * 32.0f) * 3.0f);
        data.treble = Math.min(1.0f, trebleEnergy / ((magn.length - midEnd) * 32.0f) * 3.0f);

        // Beat detection (simple energy threshold)
        data.beatDetected = data.bass > 0.6f;

        // Log audio stats every ~100 frames to avoid spamming
        if (System.currentTimeMillis() % 1000 < 50) {
            AppLog.d(TAG, String.format("Audio Stats - Amp: %.2f, Bass: %.2f, Mid: %.2f, Treble: %.2f",
                    data.amplitude, data.bass, data.mid, data.treble));
        }

        notifyListeners(data);
    }

    /**
     * Notify all listeners with audio data
     */
    private void notifyListeners(final AudioData data) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (AudioDataListener listener : listeners) {
                    try {
                        listener.onAudioData(data);
                    } catch (Exception e) {
                        AppLog.e(TAG, "Error notifying listener", e);
                    }
                }
            }
        });
    }

    @Override
    public boolean isLedActivityActive() {
        return isAudioPlaying;
    }

    /**
     * Broadcast receiver for audio playing state
     */
    private final BroadcastReceiver audioStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.phicomm.r1manager.BLUETOOTH_AUDIO_PLAYING_STATE_CHANGED".equals(intent.getAction())) {
                boolean playing = intent.getBooleanExtra("playing", false);
                isAudioPlaying = playing;
                AppLog.d(TAG, "Audio playing state changed: " + playing);

                // Reset beat detection when audio stops
                if (!playing) {
                    lastBass = 0;
                    lastBeatTime = 0;

                    // Notify LED manager
                    com.phicomm.r1manager.server.manager.LedManager.getInstance().checkAndGatedStop();
                }
            }
        }
    };
}
