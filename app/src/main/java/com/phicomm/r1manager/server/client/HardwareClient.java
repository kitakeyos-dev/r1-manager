package com.phicomm.r1manager.server.client;

import com.phicomm.r1manager.util.AppLog;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import java.net.URI;

/**
 * HardwareClient - WebSocket client for hardware control
 * Connects to ws://127.0.0.1:8080
 */
public class HardwareClient extends WebSocketClient {

    private static final String TAG = "HardwareClient";
    private static final String SERVER_URI = "ws://127.0.0.1:8080";
    private static HardwareClient instance;

    public interface MessageListener {
        void onMessage(String message);
    }

    private MessageListener listener;
    private final Map<String, BlockingQueue<JSONObject>> pendingRequests = new ConcurrentHashMap<>();

    public static synchronized HardwareClient getInstance() {
        if (instance == null) {
            instance = new HardwareClient(null); // Listener can be set later
            instance.connect();
        }
        return instance;
    }

    public static synchronized void init() {
        getInstance();
    }

    public HardwareClient(MessageListener listener) {
        super(URI.create(SERVER_URI));
        this.listener = listener;
    }

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        AppLog.i(TAG, "Connected to hardware server");
    }

    private JSONObject lastDeviceInfo = null;

    @Override
    public void onMessage(String message) {
        AppLog.d(TAG, "Received: " + message);
        try {
            JSONObject json = new JSONObject(message);

            // Handle get_info response
            if (json.has("type") && "get_info".equals(json.getString("type"))) {
                if (json.has("data")) {
                    String dataStr = json.getString("data");
                    // The data field is a JSON string, parse it
                    lastDeviceInfo = new JSONObject(dataStr);
                    AppLog.i(TAG, "Updated device info: " + lastDeviceInfo.toString());
                }
            }

            // Check for correlation ID (type_id)
            if (json.has("type_id")) {
                String typeId = json.getString("type_id");
                if (pendingRequests.containsKey(typeId)) {
                    pendingRequests.get(typeId).offer(json);
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error parsing message", e);
        }

        if (listener != null) {
            listener.onMessage(message);
        }
    }

    public JSONObject getLastDeviceInfo() {
        return lastDeviceInfo;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        AppLog.i(TAG, "Connection closed: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        AppLog.e(TAG, "WebSocket error", ex);
    }

    // ==================== Reconnect Logic ====================

    /**
     * Ensures the WebSocket is connected before sending data.
     * Uses synchronous/blocking reconnect if the connection is closed.
     */
    private boolean ensureConnection() {
        if (isOpen()) {
            return true;
        }

        AppLog.i(TAG, "Connection is closed. Attempting lazy reconnect...");
        try {
            // If already connecting, wait a bit
            if ("CONNECTING".equals(getReadyState().name())) {
                int attempts = 0;
                while ("CONNECTING".equals(getReadyState().name()) && attempts < 20) {
                    Thread.sleep(100);
                    attempts++;
                }
                return isOpen();
            }

            // Blocking reconnect
            return this.reconnectBlocking();
        } catch (InterruptedException e) {
            AppLog.e(TAG, "Lazy reconnect interrupted", e);
            return false;
        } catch (Exception e) {
            AppLog.e(TAG, "Lazy reconnect failed", e);
            return false;
        }
    }

    // ==================== Public Methods ====================

    /**
     * Sends a generic JSON request and waits for a response with a matching
     * type_id.
     */
    public JSONObject sendJson(JSONObject json, long timeoutMs) {
        if (!ensureConnection()) {
            AppLog.e(TAG, "Failed to connect to hardware service.");
            return null;
        }

        String typeId;
        try {
            if (json.has("type_id")) {
                typeId = json.getString("type_id");
            } else {
                typeId = java.util.UUID.randomUUID().toString();
                json.put("type_id", typeId);
            }

            BlockingQueue<JSONObject> queue = new LinkedBlockingQueue<>();
            pendingRequests.put(typeId, queue);

            send(json.toString());

            // Wait for response
            JSONObject response = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            pendingRequests.remove(typeId);
            return response;

        } catch (Exception e) {
            AppLog.e(TAG, "Error sending JSON", e);
            return null;
        }
    }

    public JSONObject sendShellCommand(String command) {
        return sendShellCommand(command, 5000);
    }

    public JSONObject sendShellCommand(String command, long timeoutMs) {
        String uuid = java.util.UUID.randomUUID().toString();
        return sendShellCommand(command, uuid, timeoutMs);
    }

    public JSONObject sendShellCommand(String command, String typeId) {
        return sendShellCommand(command, typeId, 5000);
    }

    public JSONObject sendShellCommand(String command, String typeId, long timeoutMs) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "shell");
            json.put("shell", command);
            json.put("type_id", typeId);
            return sendJson(json, timeoutMs);
        } catch (Exception e) {
            AppLog.e(TAG, "Error constructing shell command json", e);
            return null;
        }
    }

    // ==================== New Control Methods ====================

    public void reboot() {
        // "stop adbd && start adbd && adb reboot" seems robust based on r1_control.js
        sendShellCommand("stop adbd && start adbd && adb reboot");
    }

    public void sendTTS(String text) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "send_message");
            json.put("what", 65536);
            json.put("obj", text);
            json.put("type_id", "TTS");

            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error sending TTS", e);
        }
    }

    public void setVolume(int volume) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "set_vol");
            json.put("vol", volume);
            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error setting volume", e);
        }
    }

    public void setSystemLanguage(String lang) {
        // lang should be "zh" or "en"
        String command;
        if ("en".equals(lang)) {
            command = "setprop persist.sys.language en && setprop persist.sys.country US";
        } else {
            // Default to zh
            command = "setprop persist.sys.language zh && setprop persist.sys.country CN";
        }
        sendShellCommand(command);
    }

    // --- Music & Entertainment ---
    public void sendMusicRequest(String songName) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "send_message");
            json.put("what", 65536);
            json.put("arg1", 0);
            json.put("arg2", 1);
            json.put("obj", "web_播放" + songName);
            json.put("type_id", "点播音乐");
            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error sending music request", e);
        }
    }

    public void sendRadioRequest(String radioName) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "send_message");
            json.put("what", 65536);
            json.put("arg1", 0);
            json.put("arg2", 1);
            json.put("obj", "web_收听" + radioName);
            json.put("type_id", "点播广播");
            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error sending radio request", e);
        }
    }

    public void sendPlaylistRequest(String url) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "send_message");
            json.put("what", 65536);
            json.put("arg1", 0);
            json.put("arg2", 9);
            json.put("obj", url);
            json.put("type_id", "点播歌单");
            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error sending playlist request", e);
        }
    }

    // --- Bluetooth & Connectivity ---
    public void setBluetooth(boolean enable) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "send_message");
            json.put("what", 64);
            json.put("arg1", enable ? 1 : 2); // 1: Open, 2: Close
            json.put("arg2", -1);
            json.put("type_id", enable ? "打开蓝牙" : "关闭蓝牙");
            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error setting bluetooth", e);
        }
    }

    public void setDlna(boolean enable) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "Set_DLNA_Open");
            json.put("open", enable);
            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error setting DLNA", e);
        }
    }

    public void setAirPlay(boolean enable) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "Set_AirPlay_Open");
            json.put("open", enable);
            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error setting AirPlay", e);
        }
    }

    // --- Voice & Assistant ---
    public void setDeviceName(String name) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "set_dev_name");
            json.put("dev_name", name);
            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error setting device name", e);
        }
    }

    public void setWakeWord(String word) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "send_message");
            json.put("what", 65536);
            json.put("arg1", 0);
            json.put("arg2", 3);
            json.put("obj", word);
            json.put("type_id", "修改小讯唤醒词");
            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error setting wake word", e);
        }
    }

    public void setXiaoAiWake(boolean enable) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "set_wakeup_xiaoai");
            obj.put("enable", enable);

            JSONObject json = new JSONObject();
            json.put("type", "send_message");
            json.put("what", 65536);
            json.put("arg1", 0);
            json.put("arg2", 10);
            json.put("obj", obj);
            json.put("type_id", "打开小爱唤醒");
            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error setting XiaoAi wake", e);
        }
    }

    // --- System ---
    public void rebootService(String serviceType) {
        // serviceType: reboot_adbd, reboot_echo, reboot_xiaoaiservice
        try {
            JSONObject json = new JSONObject();
            json.put("type", serviceType); // These are sent as simple {type: "itemType"} in buttons
            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error rebooting service " + serviceType, e);
        }
    }

    public void autoRestart(boolean enable) {
        // This is complex in r1_control, it seems to open a dialog.
        // Skipping complex logic for now, or just setting a property if known.
        // "设置自动重启" itemType: "Auto_restart_device". It opens a UI.
        // We might need to investigate what that UI does.
        // For now, let's implement the generic shell command execution.
    }

    public void sendLightCommand(int what, int value) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "send_message");
            json.put("what", 4); // Light Category
            json.put("arg1", what);

            // Logic based on observed args in r1_control.min.js:
            // 65 = Brightness (arg2=String)
            // 66 = Speed (arg2=String)
            // 68 = Effect (arg2=Int)

            String typeIdPrefix = "light";

            if (what == 65) {
                json.put("arg2", String.valueOf(value));
                json.put("type_id", "设置氛围灯亮度");
            } else if (what == 66) {
                json.put("arg2", String.valueOf(value));
                json.put("type_id", "设置颜色渐变速度");
            } else if (what == 68) {
                json.put("arg2", value);
                json.put("type_id", "切换七彩氛围效果");
            } else {
                json.put("arg2", value);
                json.put("type_id", "light_cmd");
            }

            // Note: type_id is strictly for client tracking in many cases, but using
            // official strings to be safe.

            sendJson(json, 2000);
        } catch (Exception e) {
            AppLog.e(TAG, "Error sending light command", e);
        }
    }

    public void getInfo() {
        if (ensureConnection()) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "get_info");
                send(json.toString());
            } catch (Exception e) {
                AppLog.e(TAG, "Error sending get_info", e);
            }
        }
    }

    public void sendShell(String command, String typeId) {
        if (ensureConnection()) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "shell");
                json.put("shell", command);
                json.put("type_id", typeId != null ? typeId : "shell_cmd");
                send(json.toString());
            } catch (Exception e) {
                AppLog.e(TAG, "Error sending shell", e);
            }
        }
    }
}
