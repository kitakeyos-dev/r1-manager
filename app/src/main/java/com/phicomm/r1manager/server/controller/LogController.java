package com.phicomm.r1manager.server.controller;

import android.content.Context;
import com.phicomm.r1manager.server.annotation.*;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.util.LogBuffer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    public LogController(Context context) {
    }

    @GetMapping("/get")
    public Object getLogs(
            @RequestParam(value = "count", defaultValue = "500") int count) {
        try {
            List<LogBuffer.LogEntry> logs = LogBuffer.getInstance().getLogs(count);

            java.util.List<Map<String, String>> logMaps = new java.util.ArrayList<>();
            for (LogBuffer.LogEntry log : logs) {
                Map<String, String> m = new HashMap<>();
                m.put("time", log.timestamp);
                m.put("level", log.level);
                m.put("tag", log.tag);
                m.put("message", log.message);
                logMaps.add(m);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("logs", logMaps);
            data.put("total", logMaps.size());

            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.error("Failed to fetch logs: " + e.getMessage());
        }
    }

    @PostMapping("/clear")
    public Object clearLogs() {
        try {
            LogBuffer.getInstance().clear();
            return ApiResponse.successMessage("Logs cleared");
        } catch (Exception e) {
            return ApiResponse.error("Failed to clear logs: " + e.getMessage());
        }
    }
}
