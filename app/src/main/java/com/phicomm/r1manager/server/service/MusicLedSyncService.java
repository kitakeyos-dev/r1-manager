package com.phicomm.r1manager.server.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import com.phicomm.r1manager.util.AppLog;

import com.phicomm.r1manager.server.client.HardwareClient;
import com.phicomm.r1manager.server.manager.MusicServiceManager;

/**
 * Service to synchronize LED effects with music playback
 */
public class MusicLedSyncService extends Service implements AudioVisualizerService.AudioDataListener {

    private static final String TAG = "MusicLedSyncService";
    private static final String PREFS_NAME = "MusicLedSyncPrefs";
    private static final int LED_UPDATE_INTERVAL = 50; // 20 FPS for stability

    private AudioVisualizerService visualizerService;
    private com.phicomm.r1manager.server.manager.LedManager ledManager;
    private Handler handler = new Handler();

    private boolean isEnabled = false;
    private LedMode currentMode = LedMode.SPECTRUM;
    private float sensitivity = 0.7f;
    private int brightness = 80;

    // Animation state
    private float hue = 0; // For rainbow effect
    private long lastUpdateTime = 0;
    private boolean lastBeatState = false;
    private float meteorPos = 0;

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public MusicLedSyncService getService() {
            return MusicLedSyncService.this;
        }
    }

    /**
     * LED effect modes
     */
    public enum LedMode {
        SPECTRUM, // VU Meter style
        PULSE, // Brightness pulses with beat
        WAVE, // Color wave based on amplitude
        RAINBOW, // Rainbow cycle, speed based on tempo
        PARTY, // Combined effects
        METEOR, // Trailing chase on Ring
        VORTEX, // Rotating internal pattern
        SPIRAL // Continuous 39-LED spiral
    }

    /**
     * LED sync settings
     */
    public static class LedSyncSettings {
        public LedMode mode;
        public float sensitivity;
        public int brightness;
        public boolean enabled;

        public LedSyncSettings() {
            this.mode = LedMode.SPECTRUM;
            this.sensitivity = 0.7f;
            this.brightness = 80;
            this.enabled = false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.d(TAG, "MusicLedSyncService created");

        ledManager = com.phicomm.r1manager.server.manager.LedManager.getInstance();
        loadSettings();

        // Register with manager
        MusicServiceManager.registerMusicLedSyncService(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLog.d(TAG, "MusicLedSyncService started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppLog.d(TAG, "MusicLedSyncService destroyed");

        disable();
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Set the visualizer service
     */
    public void setVisualizerService(AudioVisualizerService service) {
        this.visualizerService = service;
        if (isEnabled) {
            AppLog.d(TAG, "Auto-starting LED sync from settings");
            visualizerService.addListener(this);
            visualizerService.enable();
        }
    }

    /**
     * Enable LED sync
     */
    public boolean enable() {
        if (isEnabled) {
            AppLog.d(TAG, "LED sync already enabled");
            return true;
        }

        if (visualizerService == null) {
            AppLog.e(TAG, "Visualizer service not set");
            return false;
        }

        visualizerService.addListener(this);
        if (!visualizerService.enable()) {
            AppLog.e(TAG, "Failed to enable visualizer");
            return false;
        }
        isEnabled = true;

        AppLog.d(TAG, "LED sync enabled with mode: " + currentMode);
        saveSettings();

        return true;
    }

    /**
     * Disable LED sync
     */
    public boolean disable() {
        if (!isEnabled) {
            return true;
        }

        if (visualizerService != null) {
            visualizerService.removeListener(this);
            visualizerService.disable();
        }

        isEnabled = false;

        // Turn off LED
        ledManager.turnOffAll();

        AppLog.d(TAG, "LED sync disabled");
        saveSettings();

        return true;
    }

    /**
     * Set LED mode
     */
    public void setMode(LedMode mode) {
        this.currentMode = mode;
        AppLog.d(TAG, "LED mode set to: " + mode);
        saveSettings();
    }

    /**
     * Set sensitivity (0.0 - 1.0)
     */
    public void setSensitivity(float sensitivity) {
        this.sensitivity = Math.max(0.0f, Math.min(1.0f, sensitivity));
        AppLog.d(TAG, "Sensitivity set to: " + this.sensitivity);
        saveSettings();
    }

    /**
     * Set brightness (0 - 100)
     */
    public void setBrightness(int brightness) {
        this.brightness = Math.max(0, Math.min(100, brightness));
        AppLog.d(TAG, "Brightness set to: " + this.brightness);
        saveSettings();
    }

    /**
     * Get current settings
     */
    public LedSyncSettings getSettings() {
        LedSyncSettings settings = new LedSyncSettings();
        settings.mode = currentMode;
        settings.sensitivity = sensitivity;
        settings.brightness = brightness;
        settings.enabled = isEnabled;
        return settings;
    }

    /**
     * Get available modes
     */
    public static LedMode[] getAvailableModes() {
        return LedMode.values();
    }

    /**
     * Audio data callback from visualizer
     */
    @Override
    public void onAudioData(AudioVisualizerService.AudioData data) {
        if (!isEnabled || !ledManager.isAnythingActive()) {
            return;
        }

        // Throttle updates to ~30 FPS
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < LED_UPDATE_INTERVAL) {
            return;
        }
        lastUpdateTime = currentTime;

        // Apply sensitivity (Boost 1x to 20x)
        float boost = 1.0f + (sensitivity * 19.0f);
        float adjustedAmplitude = Math.min(1.0f, data.amplitude * boost);
        float adjustedBass = Math.min(1.0f, data.bass * boost);
        float adjustedMid = Math.min(1.0f, data.mid * boost);
        float adjustedTreble = Math.min(1.0f, data.treble * boost);

        // Log occasionally
        if (System.currentTimeMillis() % 1000 < 50) {
            AppLog.d(TAG, "Processing LED: Mode=" + currentMode + ", Amp=" + adjustedAmplitude);
        }

        // Process based on mode
        switch (currentMode) {
            case SPECTRUM:
                updateSpectrumMode(adjustedBass, adjustedMid, adjustedTreble);
                break;
            case PULSE:
                updatePulseMode(data.beatDetected, adjustedAmplitude);
                break;
            case WAVE:
                updateWaveMode(adjustedAmplitude);
                break;
            case RAINBOW:
                updateRainbowMode(adjustedAmplitude);
                break;
            case PARTY:
                updatePartyMode(data.beatDetected, adjustedBass, adjustedMid, adjustedTreble);
                break;
            case METEOR:
                updateMeteorMode(adjustedAmplitude);
                break;
            case VORTEX:
                updateVortexMode(data.beatDetected, adjustedAmplitude);
                break;
            case SPIRAL:
                updateSpiralMode(adjustedAmplitude);
                break;
        }
    }

    /**
     * SPECTRUM mode: VU Meter (Internal) + Frequency Color (Ring)
     */
    private void updateSpectrumMode(float bass, float mid, float treble) {
        float total = (bass + mid + treble) / 3.0f;
        int ledCount = (int) (total * 15);
        if (ledCount < 0)
            ledCount = 0;

        long maskInt = (1L << ledCount) - 1;
        int r = (int) (bass * 255);
        int g = (int) (mid * 255);
        int b = (int) (treble * 255);
        String color = String.format("%02x%02x%02x", r, g, b);

        ledManager.setInternalLed(Long.toHexString(maskInt), color, "VU_INT");

        // Ring: Middle glow
        int ringBri = (int) (total * 255 * brightness / 100);
        ledManager.setRingLed("7ffff8000", String.format("%02x", ringBri), "VU_RING");
    }

    /**
     * PULSE mode: Brightness pulses with beat
     */
    private void updatePulseMode(boolean beatDetected, float amplitude) {
        int bri = (int) (amplitude * 255 * brightness / 100);
        if (beatDetected) {
            bri = (int) (255 * brightness / 100);
            // Flash both
            ledManager.setInternalLed("7fff", "ffffff", "PULSE_BEAT_INT");
            ledManager.setRingLed("7ffff8000", "ff", "PULSE_BEAT_RING");
        } else {
            // Fade out
            String color = String.format("%02x%02x%02x", bri, bri, bri);
            ledManager.setInternalLed("7fff", color, "PULSE_FADE_INT");
            ledManager.setRingLed("7ffff8000", String.format("%02x", bri / 2), "PULSE_FADE_RING");
        }
        lastBeatState = beatDetected;
    }

    /**
     * SPIRAL mode: Continuous rotation through all 39 LEDs
     */
    private float spiralPos = 0;

    private void updateSpiralMode(float amplitude) {
        // Move position based on volume
        spiralPos = (spiralPos + 1.0f + amplitude * 3.0f) % 39;

        long maskInt = 0;
        long maskRing = 0;

        // Light up 8 LEDs in a row across the 39-LED sequence
        for (int i = 0; i < 8; i++) {
            int pos = ((int) spiralPos + i) % 39;
            if (pos < 15) {
                maskInt |= (1L << pos);
            } else {
                maskRing |= (1L << (pos - 15 + 15)); // Still needs 15-bit shift for hardware
            }
        }

        hue = (hue + 2) % 360;
        int[] rgb = hsvToRgb(hue, 1.0f, 1.0f);
        String color = String.format("%02x%02x%02x", rgb[0], rgb[1], rgb[2]);

        if (maskInt > 0)
            ledManager.setInternalLed(Long.toHexString(maskInt), color, "SPIRAL_INT");
        if (maskRing > 0)
            ledManager.setRingLed(Long.toHexString(maskRing), "ff", "SPIRAL_RING");
    }

    /**
     * WAVE mode: Smooth Circular chase on Ring LED + Rainbow Wave on Internal
     */
    private int ringStep = 0;

    private void updateWaveMode(float amplitude) {
        hue = (hue + 5 + amplitude * 10) % 360;
        int[] rgb = hsvToRgb(hue, 1.0f, (float) brightness / 100);
        String color = String.format("%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
        ledManager.setInternalLed("7fff", color, "WAVE_INT");

        // Smoother Ring chase: 8 LEDs rotating
        ringStep = (ringStep + 1) % 24;
        long ringMask = 0;
        for (int i = 0; i < 8; i++) {
            int pos = (ringStep + i) % 24;
            ringMask |= (1L << (pos + 15));
        }
        ledManager.setRingLed(Long.toHexString(ringMask), String.format("%02x", (int) (amplitude * 255)), "WAVE_RING");
    }

    /**
     * Smoother METEOR mode (Anti-flicker)
     */
    private void updateMeteorMode(float amplitude) {
        // Slower base speed, capped max speed to prevent flickering
        meteorPos = (meteorPos + 0.3f + Math.min(amplitude, 0.8f) * 1.5f) % 24;
        int head = (int) meteorPos;
        long ringMask = 0;

        for (int i = 0; i < 5; i++) {
            int pos = (head - i + 24) % 24;
            ringMask |= (1L << (pos + 15));
        }

        ledManager.setRingLed(Long.toHexString(ringMask), "ff", "METEOR_RING");

        // Background internal glow
        int[] rgb = hsvToRgb(hue, 0.5f, amplitude * 0.3f);
        ledManager.setInternalLed("7fff", String.format("%02x%02x%02x", rgb[0], rgb[1], rgb[2]), "METEOR_INT");
        hue = (hue + 1) % 360;
    }

    /**
     * VORTEX mode: Rotating internal pattern + Beat flash ring
     */
    private int vortexStep = 0;

    private void updateVortexMode(boolean beatDetected, float amplitude) {
        if (beatDetected) {
            vortexStep = (vortexStep + 3) % 15;
            ledManager.setRingLed("7ffff8000", "ff", "VORTEX_BEAT_RING");
        } else {
            vortexStep = (vortexStep + 1) % 15;
            ledManager.setRingLed("7ffff8000", "22", "VORTEX_IDLE_RING");
        }

        // Rotate 3-LED block internally
        long maskInt = 0;
        for (int i = 0; i < 3; i++) {
            maskInt |= (1L << ((vortexStep + i) % 15));
        }

        hue = (hue + 10) % 360;
        int[] rgb = hsvToRgb(hue, 1.0f, amplitude);
        ledManager.setInternalLed(Long.toHexString(maskInt), String.format("%02x%02x%02x", rgb[0], rgb[1], rgb[2]),
                "VORTEX_INT");
    }

    /**
     * RAINBOW mode: Rainbow cycle on Internal, Glow on Ring
     */
    private void updateRainbowMode(float amplitude) {
        hue = (hue + 5 + amplitude * 10) % 360;
        int[] rgb = hsvToRgb(hue, 1.0f, (float) brightness / 100);
        String color = String.format("%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
        ledManager.setInternalLed("7fff", color, "RAINBOW_INT");

        // Ring glow
        int ringBri = (int) (amplitude * 255 * brightness / 100);
        ledManager.setRingLed("7ffff8000", String.format("%02x", ringBri), "RAINBOW_RING");
    }

    /**
     * PARTY mode: Fast Ring Rotation + Strobe effect on beat
     */
    private void updatePartyMode(boolean beatDetected, float bass, float mid, float treble) {
        if (beatDetected) {
            // Strobe
            ledManager.setInternalLed("7fff", "ffffff", "PARTY_STROBE");
            ledManager.setRingLed("7ffff8000", "ff", "PARTY_STROBE_RING");
        } else {
            // Random colors internal
            int r = (int) (bass * 255);
            int g = (int) (mid * 255);
            int b = (int) (treble * 255);
            ledManager.setInternalLed("7fff", String.format("%02x%02x%02x", r, g, b), "PARTY_INT");

            // Fast rotation ring
            ringStep = (ringStep + 2) % 24;
            long mask = (0xF0L << (ringStep % 20 + 15)); // 4 LED block
            ledManager.setRingLed(Long.toHexString(mask), "ff", "PARTY_RING");
        }
    }

    /**
     * Convert HSV to RGB
     */
    private int[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60) % 2 - 1));
        float m = v - c;

        float r, g, b;
        if (h < 60) {
            r = c;
            g = x;
            b = 0;
        } else if (h < 120) {
            r = x;
            g = c;
            b = 0;
        } else if (h < 180) {
            r = 0;
            g = c;
            b = x;
        } else if (h < 240) {
            r = 0;
            g = x;
            b = c;
        } else if (h < 300) {
            r = x;
            g = 0;
            b = c;
        } else {
            r = c;
            g = 0;
            b = x;
        }

        return new int[] {
                (int) ((r + m) * 255),
                (int) ((g + m) * 255),
                (int) ((b + m) * 255)
        };
    }

    /**
     * Load settings from SharedPreferences
     */
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String modeStr = prefs.getString("mode", LedMode.SPECTRUM.name());
        try {
            currentMode = LedMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            currentMode = LedMode.SPECTRUM;
        }

        sensitivity = prefs.getFloat("sensitivity", 0.7f);
        brightness = prefs.getInt("brightness", 80);
        isEnabled = prefs.getBoolean("enabled", false);

        AppLog.d(TAG, "Settings loaded: mode=" + currentMode + ", sensitivity=" + sensitivity +
                ", brightness=" + brightness + ", enabled=" + isEnabled);
    }

    /**
     * Save settings to SharedPreferences
     */
    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("mode", currentMode.name());
        editor.putFloat("sensitivity", sensitivity);
        editor.putInt("brightness", brightness);
        editor.putBoolean("enabled", isEnabled);

        editor.apply();
    }
}
