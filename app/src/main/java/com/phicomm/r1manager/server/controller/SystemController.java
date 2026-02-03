package com.phicomm.r1manager.server.controller;

import android.content.Context;
import com.phicomm.r1manager.server.annotation.*;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.manager.SystemManager;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemManager systemManager;

    public SystemController(Context context) {
        this.systemManager = new SystemManager(context);
    }

    @GetMapping("/info")
    public ApiResponse<Object> getInfo() {
        return ApiResponse.success(systemManager.getSystemInfo());
    }

    @GetMapping("/logcat")
    public ApiResponse<Object> getLogcat(
            @RequestParam(value = "lines", defaultValue = "100") int lines,
            @RequestParam(value = "filter") String filter) {
        return ApiResponse.success(systemManager.getLogcat(lines, filter));
    }

    public ApiResponse<String> clearLogcat() {
        boolean success = systemManager.clearLogcat();
        if (success) {
            return ApiResponse.successMessage("Logcat cleared");
        } else {
            return ApiResponse.error("Failed to clear");
        }
    }

    @PostMapping("/shell")
    public ApiResponse<Object> executeShell(@RequestBody ShellRequest req) {
        if (req.command == null || req.command.isEmpty()) {
            return ApiResponse.error("No command provided");
        }
        return ApiResponse.success(systemManager.executeShell(req.command));
    }

    public static class ShellRequest {
        public String command;
    }
}
