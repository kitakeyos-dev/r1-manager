package com.phicomm.r1manager.server.controller;

import android.content.Context;
import com.phicomm.r1manager.server.annotation.*;
import com.phicomm.r1manager.server.client.HardwareClient;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.service.ExoPlayerService;

import org.json.JSONObject;

@RestController
@RequestMapping("/api/hardware")
public class HardwareController {

    private final Context context;

    public HardwareController(Context context) {
        this.context = context;
    }

    @PostMapping("/light/brightness")
    public ApiResponse<String> setBrightness(@RequestBody ValueRequest req) {
        HardwareClient.getInstance().sendLightCommand(65, req.value);
        return ApiResponse.successMessage("Brightness set");
    }

    @PostMapping("/light/speed")
    public ApiResponse<String> setSpeed(@RequestBody ValueRequest req) {
        HardwareClient.getInstance().sendLightCommand(66, req.value);
        return ApiResponse.successMessage("Speed set");
    }

    @PostMapping("/light/effect")
    public ApiResponse<String> setEffect(@RequestBody ModeRequest req) {
        HardwareClient.getInstance().sendLightCommand(68, req.mode);
        return ApiResponse.successMessage("Effect set");
    }

    @PostMapping("/language")
    public ApiResponse<String> setLanguage(@RequestBody LangRequest req) {
        HardwareClient.getInstance().setSystemLanguage(req.lang);
        return ApiResponse.successMessage("Language set");
    }

    @PostMapping("/light")
    public ApiResponse<String> toggleLight(@RequestBody StateRequest req) {
        com.phicomm.r1manager.server.manager.LedManager manager = com.phicomm.r1manager.server.manager.LedManager
                .getInstance();
        if ("on".equalsIgnoreCase(req.state)) {
            manager.setRingLed("7fffff8000", "ff", "开灯");
            return ApiResponse.successMessage("Light turned on");
        } else {
            manager.setRingLed("7fffff8000", "00", "关灯");
            return ApiResponse.successMessage("Light turned off");
        }
    }

    @PostMapping("/reboot")
    public ApiResponse<String> reboot() {
        HardwareClient.getInstance().reboot();
        return ApiResponse.successMessage("Reboot command sent");
    }

    @PostMapping("/tts")
    public ApiResponse<String> sendTTS(@RequestBody TextRequest req) {
        HardwareClient.getInstance().sendTTS(req.text);
        return ApiResponse.successMessage("TTS command sent");
    }

    @PostMapping("/volume")
    public ApiResponse<String> setVolume(@RequestBody LevelRequest req) {
        HardwareClient.getInstance().setVolume(req.level);
        return ApiResponse.successMessage("Volume command sent");
    }

    @PostMapping("/led/internal")
    public ApiResponse<String> setInternalLed(@RequestBody ColorRequest req) {
        String color = req.color.replace("#", "");
        com.phicomm.r1manager.server.manager.LedManager.getInstance().setInternalLed("7fff", color, "InternalLED");
        return ApiResponse.successMessage("Internal LED set");
    }

    @PostMapping("/led/ring")
    public ApiResponse<String> setRingLed(@RequestBody BrightnessRequest req) {
        String hex = String.format("%02x", req.brightness & 0xFF);
        com.phicomm.r1manager.server.manager.LedManager.getInstance().setRingLed("7fffff8000", hex, "RingLED");
        return ApiResponse.successMessage("Ring LED set");
    }

    @PostMapping("/led/custom")
    public ApiResponse<String> setCustomLed(@RequestBody CustomLedRequest req) {
        String color = req.color.replace("#", "");
        String mask = req.mask != null ? req.mask : "7fff";
        com.phicomm.r1manager.server.manager.LedManager.getInstance().setInternalLed(mask, color, "CustomLED");
        return ApiResponse.successMessage("Custom LED set with mask: " + mask);
    }

    @PostMapping("/music/request")
    public ApiResponse<String> musicRequest(@RequestBody SongRequest req) {
        HardwareClient.getInstance().sendMusicRequest(req.song);
        return ApiResponse.successMessage("Music command sent");
    }

    @PostMapping("/bluetooth/set")
    public ApiResponse<String> setBluetooth(@RequestBody EnableRequest req) {
        HardwareClient.getInstance().setBluetooth(req.enable);
        return ApiResponse.successMessage("Bluetooth command sent");
    }

    @PostMapping("/service/reboot")
    public ApiResponse<String> rebootService(@RequestBody ServiceRequest req) {
        HardwareClient.getInstance().rebootService(req.service);
        return ApiResponse.successMessage("Service reboot command sent");
    }

    @GetMapping("/info")
    public ApiResponse<Object> getInfo() {
        HardwareClient.getInstance().getInfo();
        JSONObject info = HardwareClient.getInstance().getLastDeviceInfo();
        if (info != null) {
            return ApiResponse.success(info.toString());
        }
        return ApiResponse.successMessage("No info yet, refreshing...");
    }

    @PostMapping("/play")
    public ApiResponse<String> playAudio(@RequestBody PathRequest req) {
        if (req.path == null || req.path.isEmpty()) {
            return ApiResponse.error("Path required");
        }

        try {
            ExoPlayerService.getInstance().play(req.path);
            return ApiResponse.successMessage("Playing: " + req.path);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("Failed to play: " + e.getMessage());
        }
    }

    // --- DTOs ---
    public static class PathRequest {
        public String path;
    }

    public static class ValueRequest {
        public int value;
    }

    public static class ModeRequest {
        public int mode;
    }

    public static class LangRequest {
        public String lang;
    }

    public static class StateRequest {
        public String state;
    }

    public static class TextRequest {
        public String text;
    }

    public static class LevelRequest {
        public int level;
    }

    public static class BrightnessRequest {
        public int brightness;
    }

    public static class ColorRequest {
        public String color;
    }

    public static class CustomLedRequest {
        public String mask;
        public String color;
    }

    public static class SongRequest {
        public String song;
    }

    public static class EnableRequest {
        public boolean enable;
    }

    public static class ServiceRequest {
        public String service;
    }
}
