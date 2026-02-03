package com.phicomm.r1manager.config;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.phicomm.r1manager.server.model.xiaozhi.MqttConfig;
import com.google.gson.reflect.TypeToken;

import com.phicomm.r1manager.server.model.xiaozhi.XiaozhiBotProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class XiaozhiConfig {
    private static final String PREFS_NAME = "xiaozhi_config";
    private static final String KEY_WS_URL = "ws_url";
    private static final String KEY_QTA_URL = "qta_url";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_MQTT_CONFIG = "mqtt_config";
    private static final String KEY_TRANSPORT_TYPE = "transport_type"; // "websocket" or "mqtt"
    private static final String KEY_BOT_PROFILES = "bot_profiles";
    private static final String KEY_ACTIVE_BOT_ID = "active_bot_id";
    private static final String KEY_VOICE_BOT_ENABLED = "voice_bot_enabled";

    // Defaults
    private static final String DEFAULT_WS_URL = "wss://api.tenclass.net/xiaozhi/v1/";
    private static final String DEFAULT_QTA_URL = "https://api.tenclass.net/xiaozhi/ota/";

    private SharedPreferences prefs;
    private Context context;
    private static XiaozhiConfig instance;

    private XiaozhiConfig(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized XiaozhiConfig getInstance(Context context) {
        if (instance == null) {
            instance = new XiaozhiConfig(context);
        }
        return instance;
    }

    public String getWebSocketUrl() {
        return prefs.getString(KEY_WS_URL, DEFAULT_WS_URL);
    }

    public void setWebSocketUrl(String url) {
        prefs.edit().putString(KEY_WS_URL, url).apply();
    }

    public String getQtaUrl() {
        return prefs.getString(KEY_QTA_URL, DEFAULT_QTA_URL);
    }

    public void setQtaUrl(String url) {
        prefs.edit().putString(KEY_QTA_URL, url).apply();
    }

    // Generate random UUID if not exists
    public String getUuid() {
        String uuid = prefs.getString(KEY_UUID, null);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_UUID, uuid).apply();
        }
        return uuid;
    }

    // Get MAC Address or generate one if failing
    public String getDeviceId() {
        String deviceId = prefs.getString(KEY_DEVICE_ID, null);
        if (deviceId == null || deviceId.equals("unknown_device")) {
            deviceId = getRealMacAddress();
            if (deviceId == null) {
                deviceId = generateMacAddress();
            }
            setDeviceId(deviceId);
        }
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
    }

    private String getRealMacAddress() {
        try {
            android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) context
                    .getSystemService(Context.WIFI_SERVICE);
            android.net.wifi.WifiInfo info = wifi.getConnectionInfo();
            String mac = info.getMacAddress();
            if (mac != null && !mac.equals("02:00:00:00:00:00")) {
                return mac;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Fail
    }

    private String generateMacAddress() {
        java.util.Random r = new java.util.Random();
        byte[] mac = new byte[6];
        r.nextBytes(mac);
        mac[0] = (byte) (mac[0] & (byte) 254);
        StringBuilder sb = new StringBuilder(18);
        for (byte b : mac) {
            if (sb.length() > 0)
                sb.append(":");
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public MqttConfig getMqttConfig() {
        String json = prefs.getString(KEY_MQTT_CONFIG, null);
        if (json != null) {
            return new Gson().fromJson(json, MqttConfig.class);
        }
        return null;
    }

    public void setMqttConfig(MqttConfig config) {
        String json = new Gson().toJson(config);
        prefs.edit().putString(KEY_MQTT_CONFIG, json).apply();
    }

    public String getTransportType() {
        return prefs.getString(KEY_TRANSPORT_TYPE, "websocket");
    }

    public void setTransportType(String type) {
        prefs.edit().putString(KEY_TRANSPORT_TYPE, type).apply();
    }

    // --- Multi-Bot Profile Management ---

    public List<XiaozhiBotProfile> getBotProfiles() {
        String json = prefs.getString(KEY_BOT_PROFILES, null);
        List<XiaozhiBotProfile> profiles;
        if (json == null) {
            profiles = new ArrayList<>();
        } else {
            profiles = new Gson().fromJson(json, new TypeToken<List<XiaozhiBotProfile>>() {
            }.getType());
        }

        // Migration: If empty, create default profile from legacy settings
        if (profiles.isEmpty()) {
            XiaozhiBotProfile defaultProfile = new XiaozhiBotProfile();
            defaultProfile.id = "default";
            defaultProfile.name = "Default Bot";
            defaultProfile.wsUrl = getWebSocketUrl();
            defaultProfile.qtaUrl = getQtaUrl();
            defaultProfile.macType = "REAL"; // Default to real MAC for existing users
            defaultProfile.customMac = getDeviceId(); // Store current device ID as fallback
            defaultProfile.uuid = getUuid();
            profiles.add(defaultProfile);
            setBotProfiles(profiles);
            if (getActiveBotId() == null) {
                setActiveBotId("default");
            }
        }
        return profiles;
    }

    public void setBotProfiles(List<XiaozhiBotProfile> profiles) {
        String json = new Gson().toJson(profiles);
        prefs.edit().putString(KEY_BOT_PROFILES, json).commit();
    }

    public String getActiveBotId() {
        return prefs.getString(KEY_ACTIVE_BOT_ID, null);
    }

    public void setActiveBotId(String botId) {
        prefs.edit().putString(KEY_ACTIVE_BOT_ID, botId).commit();
    }

    public boolean isVoiceBotEnabled() {
        return prefs.getBoolean(KEY_VOICE_BOT_ENABLED, true);
    }

    public void setVoiceBotEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOICE_BOT_ENABLED, enabled).commit();
    }

    public XiaozhiBotProfile getActiveProfile() {
        String activeId = getActiveBotId();
        List<XiaozhiBotProfile> profiles = getBotProfiles();
        if (activeId == null && !profiles.isEmpty()) {
            activeId = profiles.get(0).id;
            setActiveBotId(activeId);
        }
        for (XiaozhiBotProfile profile : profiles) {
            if (profile.id.equals(activeId)) {
                return profile;
            }
        }
        return profiles.isEmpty() ? null : profiles.get(0);
    }

    // MCP Configuration
    public boolean isMcpEnabled() {
        return prefs.getBoolean("mcp_enabled", false);
    }

    public void setMcpEnabled(boolean enabled) {
        prefs.edit().putBoolean("mcp_enabled", enabled).commit();
    }

    public String getMcpServerUrl() {
        return prefs.getString("mcp_server_url", "wss://api.xiaozhi.me/mcp/v1");
    }

    public void setMcpServerUrl(String url) {
        prefs.edit().putString("mcp_server_url", url).commit();
    }

    public String getMcpAuthToken() {
        return prefs.getString("mcp_auth_token", "");
    }

    public void setMcpAuthToken(String token) {
        prefs.edit().putString("mcp_auth_token", token).commit();
    }
}
