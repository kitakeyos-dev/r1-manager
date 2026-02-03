package com.phicomm.r1manager.server.controller;

import android.content.Context;
import com.phicomm.r1manager.server.annotation.*;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.service.MusicLedSyncService;
import com.phicomm.r1manager.server.service.MusicLedSyncService.LedMode;
import com.phicomm.r1manager.server.service.MusicLedSyncService.LedSyncSettings;
import com.phicomm.r1manager.server.manager.MusicServiceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/music-led")
public class MusicLedController {

    private final Context context;

    public MusicLedController(Context context) {
        this.context = context;
    }

    private MusicLedSyncService getService() {
        return MusicServiceManager.getInstance().getMusicLedSyncService();
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        MusicLedSyncService service = getService();
        if (service == null) {
            Map<String, Object> status = new HashMap<>();
            status.put("enabled", false);
            status.put("mode", "SPECTRUM");
            status.put("sensitivity", 0.7f);
            status.put("brightness", 80);
            return ApiResponse.success(status);
        }

        LedSyncSettings settings = service.getSettings();

        Map<String, Object> status = new HashMap<>();
        status.put("enabled", settings.enabled);
        status.put("mode", settings.mode.name());
        status.put("sensitivity", settings.sensitivity);
        status.put("brightness", settings.brightness);

        return ApiResponse.success(status);
    }

    @PostMapping("/enable")
    public ApiResponse<String> enable(@RequestBody EnableRequest req) {
        MusicLedSyncService service = getService();
        if (service == null) {
            return ApiResponse.error("Music LED service not available - Check if service started");
        }

        boolean success;
        if (req.enabled) {
            success = service.enable();
        } else {
            success = service.disable();
        }

        if (success) {
            return ApiResponse.successMessage("Music LED sync " + (req.enabled ? "enabled" : "disabled"));
        } else {
            return ApiResponse.error("Failed to " + (req.enabled ? "enable" : "disable") + " LED sync");
        }
    }

    @GetMapping("/modes")
    public ApiResponse<List<Map<String, String>>> getModes() {
        List<Map<String, String>> modes = new ArrayList<>();
        for (LedMode mode : MusicLedSyncService.getAvailableModes()) {
            Map<String, String> modeInfo = new HashMap<>();
            modeInfo.put("id", mode.name());
            modeInfo.put("name", getModeDisplayName(mode));
            modeInfo.put("description", getModeDescription(mode));
            modes.add(modeInfo);
        }
        return ApiResponse.success(modes);
    }

    @PostMapping("/mode")
    public ApiResponse<String> setMode(@RequestBody ModeRequest req) {
        MusicLedSyncService service = getService();
        if (service == null) {
            return ApiResponse.error("Music LED service not available");
        }

        try {
            LedMode mode = LedMode.valueOf(req.mode.toUpperCase());
            service.setMode(mode);
            return ApiResponse.successMessage("Mode set to " + mode.name());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("Invalid mode: " + req.mode);
        }
    }

    @PostMapping("/settings")
    public ApiResponse<String> updateSettings(@RequestBody SettingsRequest req) {
        MusicLedSyncService service = getService();
        if (service == null) {
            return ApiResponse.error("Music LED service not available");
        }

        if (req.sensitivity != null) {
            service.setSensitivity(req.sensitivity);
        }
        if (req.brightness != null) {
            service.setBrightness(req.brightness);
        }
        return ApiResponse.successMessage("Settings updated");
    }

    // Helper methods
    private String getModeDisplayName(LedMode mode) {
        switch (mode) {
            case SPECTRUM:
                return "Spectrum";
            case PULSE:
                return "Pulse";
            case WAVE:
                return "Wave";
            case RAINBOW:
                return "Rainbow";
            case PARTY:
                return "Party";
            default:
                return mode.name();
        }
    }

    private String getModeDescription(LedMode mode) {
        switch (mode) {
            case SPECTRUM:
                return "Color changes based on frequency (bass=red, mid=green, treble=blue)";
            case PULSE:
                return "Brightness pulses with beat";
            case WAVE:
                return "Color wave based on amplitude";
            case RAINBOW:
                return "Rainbow cycle, speed based on tempo";
            case PARTY:
                return "Combined effects for maximum energy";
            default:
                return "";
        }
    }

    // DTOs
    public static class EnableRequest {
        public boolean enabled;
    }

    public static class ModeRequest {
        public String mode;
    }

    public static class SettingsRequest {
        public Float sensitivity;
        public Integer brightness;
    }
}
