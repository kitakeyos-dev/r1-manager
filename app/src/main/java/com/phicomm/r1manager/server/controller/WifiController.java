package com.phicomm.r1manager.server.controller;

import android.content.Context;
import com.phicomm.r1manager.server.annotation.*;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.manager.NetworkManager;

@RestController
@RequestMapping("/api/wifi")
public class WifiController {

    private final NetworkManager networkManager;

    public WifiController(Context context) {
        this.networkManager = new NetworkManager(context);
    }

    @GetMapping()
    public ApiResponse<Object> getWifiInfo() {
        return ApiResponse.success(networkManager.getWifiInfo());
    }

    @GetMapping("/scan")
    public ApiResponse<Object> scanWifi() {
        return ApiResponse.success(networkManager.scanWifiNetworks());
    }

    public ApiResponse<String> setWifiEnabled(@RequestBody EnableRequest req) {
        boolean success = networkManager.setWifiEnabled(req.enabled);
        if (success) {
            return ApiResponse.successMessage("WiFi " + (req.enabled ? "enabled" : "disabled"));
        } else {
            return ApiResponse.error("Failed");
        }
    }

    @PostMapping("/connect")
    public ApiResponse<String> connect(@RequestBody ConnectRequest req) {
        if (req.ssid == null || req.ssid.isEmpty())
            return ApiResponse.error("SSID required");
        boolean success = networkManager.connectToWifi(req.ssid, req.password,
                req.useStaticIp, req.ipAddress, req.gateway, req.netmask, req.dns1, req.dns2);
        if (success) {
            return ApiResponse.successMessage("Connecting to " + req.ssid);
        } else {
            return ApiResponse.error("Failed to connect");
        }
    }

    public ApiResponse<String> disconnect() {
        boolean success = networkManager.disconnectWifi();
        if (success) {
            return ApiResponse.successMessage("Disconnected");
        } else {
            return ApiResponse.error("Failed");
        }
    }

    public ApiResponse<String> forget(@RequestBody ForgetRequest req) {
        boolean success = networkManager.forgetWifi(req.networkId);
        if (success) {
            return ApiResponse.successMessage("Network removed");
        } else {
            return ApiResponse.error("Failed");
        }
    }

    public static class EnableRequest {
        public boolean enabled;
    }

    public static class ConnectRequest {
        public String ssid;
        public String password;
        // Static IP Options
        public boolean useStaticIp;
        public String ipAddress;
        public String gateway;
        public String netmask;
        public String dns1;
        public String dns2;
    }

    public static class ForgetRequest {
        public int networkId;
    }

}
