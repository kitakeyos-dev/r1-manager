package com.phicomm.r1manager.server.model.dto;

import java.util.List;

public class NetworkDto {
    public static class WifiInfo {
        public boolean enabled;
        public String ssid;
        public String bssid;
        public String ipAddress;
        public String macAddress;
        public int linkSpeed;
        public int rssi;
        public int signalLevel;
        public int networkId;
    }

    public static class WifiScanResult {
        public String ssid;
        public String bssid;
        public int level;
        public int signalLevel;
        public int frequency;
        public String capabilities;
        public boolean secured;
    }

    public static class WifiScanResponse {
        public List<WifiScanResult> networks;
    }

    public static class WifiApInfo {
        public boolean enabled;
        public String ssid;
        public String password;
        public boolean secured;
        public List<ApClient> clients;
        public int clientCount;
    }

    public static class ApClient {
        public String ip;
        public String mac;
    }

    public static class BluetoothInfo {
        public boolean supported;
        public boolean enabled;
        public String name;
        public String address;
        public int state;
        public int scanMode;
        public boolean discovering;
        public List<BluetoothDeviceDto> pairedDevices;
        public int pairedCount;
    }

    public static class BluetoothDeviceDto {
        public String name;
        public String address;
        public int type;
        public int bondState;
    }
}
