package com.phicomm.r1manager.server.manager;

import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.server.client.HardwareClient;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized manager for R1 LED hardware control.
 * Handles shell commands, state gating, and visual feedback orchestration.
 */
public class LedManager {
    private static final String TAG = "LedManager";
    private static LedManager instance;
    private final HardwareClient hardwareClient;
    private final List<LedActivitySource> activitySources = new ArrayList<>();

    /**
     * Interface for components that can signal if they are using LEDs.
     */
    public interface LedActivitySource {
        boolean isLedActivityActive();
    }

    private LedManager() {
        this.hardwareClient = HardwareClient.getInstance();
    }

    public static synchronized LedManager getInstance() {
        if (instance == null) {
            instance = new LedManager();
        }
        return instance;
    }

    /**
     * Register a source that can provide activity state.
     */
    public void registerActivitySource(LedActivitySource source) {
        if (!activitySources.contains(source)) {
            activitySources.add(source);
        }
    }

    /**
     * Unregister an activity source.
     */
    public void unregisterActivitySource(LedActivitySource source) {
        activitySources.remove(source);
    }

    /**
     * Set internal LEDs using hex mask and color.
     */
    public void setInternalLed(String maskHex, String colorHex, String tag) {
        hardwareClient.sendShell("lights_test set " + maskHex + " " + colorHex, tag != null ? tag : "LED_INT");
    }

    /**
     * Set ring LEDs using hex mask and brightness (00-ff).
     */
    public void setRingLed(String maskHex, String brightnessHex, String tag) {
        hardwareClient.sendShell("lights_test set " + maskHex + " " + brightnessHex, tag != null ? tag : "LED_RING");
    }

    /**
     * Turn off all LEDs immediately.
     */
    public void turnOffAll() {
        AppLog.i(TAG, "Turning off all LEDs");
        setInternalLed("7fff", "000000", "OFF_INT");
        setRingLed("7fffff8000", "00", "OFF_RING");
    }

    /**
     * Check if any registered source is currently active.
     */
    public boolean isAnythingActive() {
        for (LedActivitySource source : activitySources) {
            if (source.isLedActivityActive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Trigger a check and turn off LEDs if nothing is active.
     */
    public void checkAndGatedStop() {
        if (!isAnythingActive()) {
            AppLog.d(TAG, "Gated stop triggered: All services idle");
            turnOffAll();
        }
    }

    /**
     * Show listening indicator - green ring.
     * Called when the device is actively listening for speech.
     */
    public void showListening() {
        AppLog.d(TAG, "LED: Listening mode (green ring)");
        setRingLed("7fffff8000", "80", "LISTENING");
    }

    /**
     * Show speaking indicator - bright ring.
     * Called when the device is speaking (TTS playing).
     */
    public void showSpeaking() {
        AppLog.d(TAG, "LED: Speaking mode (bright ring)");
        setRingLed("7fffff8000", "ff", "SPEAKING");
    }
}
