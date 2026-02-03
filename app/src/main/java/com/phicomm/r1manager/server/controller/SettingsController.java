package com.phicomm.r1manager.server.controller;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.WebServerService;
import com.phicomm.r1manager.config.AppConfig;
import com.phicomm.r1manager.server.annotation.*;
import com.phicomm.r1manager.server.model.ApiResponse;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private static final String TAG = "SettingsController";
    private final Context context;
    private final AppConfig config;
    private final int currentPort;

    public SettingsController(Context context, int currentPort) {
        this.context = context;
        this.config = AppConfig.getInstance(context);
        this.currentPort = currentPort;
    }

    @GetMapping("/port")
    public ApiResponse<Map<String, Object>> getPort() {
        Map<String, Object> data = new HashMap<>();
        data.put("port", config.getPort());
        data.put("currentPort", currentPort);
        return ApiResponse.success(data);
    }

    @PostMapping("/port")
    public ApiResponse<Map<String, Object>> setPort(@RequestBody PortRequest req) {
        final int newPort = req.port > 0 ? req.port : config.getDefaultPort();

        if (newPort < 1024 || newPort > 65535) {
            return ApiResponse.error("Port must be 1024-65535");
        }

        Map<String, Object> result = new HashMap<>();
        if (newPort == currentPort) {
            result.put("message", "Port unchanged");
            result.put("newPort", newPort);
            result.put("redirect", false);
            return ApiResponse.success(result);
        }

        config.setPort(newPort);
        result.put("message", "Port changed to " + newPort);
        result.put("oldPort", currentPort);
        result.put("newPort", newPort);
        result.put("redirect", true);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                restartService(newPort);
            }
        }, 1000);

        return ApiResponse.success(result);
    }

    private void restartService(int newPort) {
        try {
            AppLog.i(TAG, "Restarting service with port: " + newPort);
            Intent stopIntent = new Intent(context, WebServerService.class);
            context.stopService(stopIntent);

            Intent startIntent = new Intent(context, WebServerService.class);
            startIntent.putExtra("new_port", newPort);
            context.startService(startIntent);
        } catch (Exception e) {
            AppLog.e(TAG, "Error restarting", e);
        }
    }

    public static class PortRequest {
        public int port;
    }
}
