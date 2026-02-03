package com.phicomm.r1manager.config;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.phicomm.r1manager.server.model.WolDevice;
import java.util.ArrayList;
import java.util.List;

/**
 * AppConfig - Centralized configuration and settings manager
 * Wraps SharedPreferences for easy access to app settings
 */
public class AppConfig {

    private static final String PREFS_NAME = "r1_manager_prefs";
    private static final String KEY_PORT = "web_server_port";
    private static final String KEY_WOL_DEVICES = "wol_devices";

    private static final int DEFAULT_PORT = 8188;

    private SharedPreferences prefs;
    private static AppConfig instance;

    private AppConfig(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AppConfig getInstance(Context context) {
        if (instance == null) {
            instance = new AppConfig(context);
        }
        return instance;
    }

    // ==================== Port Settings ====================

    public int getPort() {
        return prefs.getInt(KEY_PORT, DEFAULT_PORT);
    }

    public void setPort(int port) {
        prefs.edit().putInt(KEY_PORT, port).apply();
    }

    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    // ==================== WoL Settings ====================

    public List<WolDevice> getWolDevices() {
        String json = getString(KEY_WOL_DEVICES, "[]");
        try {
            return new Gson().fromJson(json, new TypeToken<List<WolDevice>>() {
            }.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveWolDevices(List<WolDevice> devices) {
        String json = new Gson().toJson(devices);
        setString(KEY_WOL_DEVICES, json);
    }

    // ==================== Generic Methods ====================

    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    public void setString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    public void setInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }
}
