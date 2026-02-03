package com.phicomm.r1manager.server.controller;

import android.content.Context;
import com.phicomm.r1manager.server.annotation.*;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.manager.SystemManager;

@RestController
@RequestMapping("/api/volume")
public class VolumeController {

    private final SystemManager systemManager;

    public VolumeController(Context context) {
        this.systemManager = new SystemManager(context);
    }

    @GetMapping()
    public ApiResponse<Object> getVolume() {
        return ApiResponse.success(systemManager.getVolumeInfo());
    }

    @PostMapping()
    public ApiResponse<Object> setVolume(@RequestBody VolumeRequest req) {
        String type = req.type != null ? req.type : "music";
        boolean success = systemManager.setVolume(type, req.volume);
        if (success) {
            return ApiResponse.success(systemManager.getVolumeInfo());
        } else {
            return ApiResponse.error("Failed to set volume");
        }
    }

    public static class VolumeRequest {
        public String type;
        public int volume;
    }
}
