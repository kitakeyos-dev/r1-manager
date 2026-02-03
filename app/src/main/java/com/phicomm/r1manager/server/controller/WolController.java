package com.phicomm.r1manager.server.controller;

import android.content.Context;
import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.server.annotation.*;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.model.WolDevice;
import com.phicomm.r1manager.server.manager.WolManager;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wol")
public class WolController {
    private static final String TAG = "WolController";
    private final WolManager wolManager;

    public WolController(Context context) {
        this.wolManager = WolManager.getInstance(context);
    }

    @GetMapping("/list")
    public ApiResponse<List<WolDevice>> listDevices() {
        return ApiResponse.success(wolManager.getDevices());
    }

    @PostMapping("/save")
    public ApiResponse<String> saveDevice(@RequestBody WolDevice device) {
        if (device.getMac() == null || device.getMac().trim().isEmpty()) {
            return ApiResponse.error("MAC address is required");
        }

        if (wolManager.saveDevice(device)) {
            return ApiResponse.successMessage("Đã lưu thiết bị: " + device.getName());
        }
        return ApiResponse.error("Không thể lưu thiết bị");
    }

    @PostMapping("/remove")
    public ApiResponse<String> removeDevice(@RequestBody Map<String, String> body) {
        String mac = body.get("mac");
        if (mac == null || mac.trim().isEmpty()) {
            return ApiResponse.error("MAC address is required");
        }

        if (wolManager.removeDevice(mac)) {
            return ApiResponse.successMessage("Đã xóa thiết bị");
        }
        return ApiResponse.error("Không tìm thấy thiết bị");
    }

    @PostMapping("/wake")
    public ApiResponse<String> wake(@RequestBody Map<String, String> body) {
        String mac = body.get("mac");
        if (mac == null || mac.trim().isEmpty()) {
            return ApiResponse.error("MAC Address required");
        }

        if (!wolManager.isValidMac(mac)) {
            return ApiResponse.error("Invalid MAC Address format");
        }

        try {
            wolManager.sendMagicPacket(mac.trim());
            return ApiResponse.successMessage("Magic Packet sent to " + mac);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("Invalid MAC Address format");
        } catch (Exception e) {
            AppLog.e(TAG, "Error sending WOL packet", e);
            return ApiResponse.error("Failed to send packet: " + e.getMessage());
        }
    }
}
