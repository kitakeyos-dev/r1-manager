package com.phicomm.r1manager.server.controller;

import android.content.Context;
import com.google.gson.Gson;
import com.phicomm.r1manager.config.XiaozhiConfig;
import com.phicomm.r1manager.server.annotation.GetMapping;
import com.phicomm.r1manager.server.annotation.PostMapping;
import com.phicomm.r1manager.server.annotation.RequestBody;
import com.phicomm.r1manager.server.annotation.RestController;
import com.phicomm.r1manager.server.annotation.RequestMapping;
import com.phicomm.r1manager.server.manager.XiaozhiManager;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.model.xiaozhi.OtaResult;
import com.phicomm.r1manager.server.model.xiaozhi.XiaozhiBotProfile;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/xiaozhi")
public class XiaozhiController {

    private final Context context;
    private final XiaozhiManager xiaozhiManager;

    public XiaozhiController(Context context) {
        this.context = context;
        this.xiaozhiManager = new XiaozhiManager(context);
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, String>> startConversation() {
        if (xiaozhiManager.startConversation()) {
            Map<String, String> data = new HashMap<>();
            data.put("action", "started");
            return ApiResponse.success(data);
        } else {
            return ApiResponse.error("Failed to start: " + xiaozhiManager.getStatus());
        }
    }

    @PostMapping("/stop")
    public ApiResponse<String> stopConversation() {
        xiaozhiManager.stopConversation();
        return ApiResponse.successMessage("Conversation stopped");
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, String>> getStatus() {
        Map<String, String> data = new HashMap<>();
        data.put("state", xiaozhiManager.getStatus());
        return ApiResponse.success(data);
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, String>> getConfig() {
        XiaozhiConfig config = XiaozhiConfig.getInstance(context);
        Map<String, String> data = new HashMap<>();
        data.put("ws_url", config.getWebSocketUrl());
        data.put("qta_url", config.getQtaUrl());
        data.put("device_id", config.getDeviceId());
        data.put("uuid", config.getUuid());
        data.put("transport_type", config.getTransportType());
        data.put("voice_bot_enabled", String.valueOf(config.isVoiceBotEnabled()));
        return ApiResponse.success(data);
    }

    @PostMapping("/config")
    public ApiResponse<String> updateConfig(@RequestBody Map<String, String> body) {
        xiaozhiManager.updateConfig(body);
        return ApiResponse.successMessage("Config updated");
    }

    @PostMapping("/check-otp")
    public ApiResponse<OtaResult> checkOtp(@RequestBody Map<String, String> body) {
        String deviceId = body != null ? body.get("device_id") : null;
        String uuid = body != null ? body.get("uuid") : null;
        String qtaUrl = body != null ? body.get("qta_url") : null;

        OtaResult result = xiaozhiManager.checkOta(deviceId, uuid, qtaUrl);
        if (result != null && (result.mqttConfig != null || result.code == 0)) {
            return ApiResponse.success(result);
        }

        String errorMsg = (result != null && result.message != null) ? result.message : "Request Failed";
        return ApiResponse.error(errorMsg);
    }

    @GetMapping("/bots")
    public ApiResponse<Map<String, Object>> getBots() {
        Map<String, Object> data = new HashMap<>();
        data.put("active_id", xiaozhiManager.getActiveBotId());
        data.put("profiles", xiaozhiManager.getBotProfiles());
        return ApiResponse.success(data);
    }

    @PostMapping("/bots")
    public ApiResponse<String> addOrUpdateBot(@RequestBody Map<String, Object> body) {
        XiaozhiBotProfile profile = new Gson().fromJson(new Gson().toJson(body), XiaozhiBotProfile.class);
        xiaozhiManager.addOrUpdateBot(profile);
        return ApiResponse.successMessage("Bot saved");
    }

    @PostMapping("/bots/delete")
    public ApiResponse<String> deleteBot(@RequestBody Map<String, String> body) {
        String botId = body != null ? body.get("id") : null;
        if (botId != null) {
            xiaozhiManager.deleteBot(botId);
            return ApiResponse.successMessage("Bot deleted");
        }
        return ApiResponse.error("Bot ID required");
    }

    @PostMapping("/bots/active")
    public ApiResponse<String> switchBot(@RequestBody Map<String, String> body) {
        String botId = body != null ? body.get("id") : null;
        if (botId != null) {
            xiaozhiManager.switchActiveBot(botId);
            return ApiResponse.successMessage("Bot switched");
        }
        return ApiResponse.error("Bot ID required");
    }
}
