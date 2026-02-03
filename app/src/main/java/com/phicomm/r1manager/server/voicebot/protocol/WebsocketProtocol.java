package com.phicomm.r1manager.server.voicebot.protocol;

import com.phicomm.r1manager.util.AppLog;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WebsocketProtocol {
    private static final String TAG = "WebsocketProtocol";
    private static final int OPUS_FRAME_DURATION_MS = 60;

    private final String url;
    private final String accessToken;
    private final String deviceId;
    private final String uuid;
    private WebSocketClient webSocketClient;
    private boolean isOpened = false;
    private CountDownLatch helloLatch;
    private String sessionId;

    public interface ProtocolListener {
        void onAudioData(byte[] data);

        void onJsonMessage(JSONObject json);

        void onConnected();

        void onDisconnected(String reason);

        void onError(String message);
    }

    private ProtocolListener listener;

    public WebsocketProtocol(String deviceId, String uuid, String url, String accessToken) {
        this.deviceId = deviceId;
        this.uuid = uuid;
        this.url = url;
        this.accessToken = accessToken;
    }

    public void setListener(ProtocolListener listener) {
        this.listener = listener;
    }

    public boolean start() {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Protocol-Version", "1");
            headers.put("Device-Id", deviceId);
            headers.put("Client-Id", uuid);

            helloLatch = new CountDownLatch(1);

            webSocketClient = new WebSocketClient(new URI(url), headers) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    AppLog.i(TAG, "WebSocket onOpen: " + handshakedata.getHttpStatusMessage());
                    isOpened = true;
                    if (listener != null)
                        listener.onConnected();

                    try {
                        JSONObject helloMessage = new JSONObject();
                        helloMessage.put("type", "hello");
                        helloMessage.put("version", 1);
                        helloMessage.put("transport", "websocket");

                        // Features object (required by protocol)
                        JSONObject features = new JSONObject();
                        features.put("mcp", true);
                        helloMessage.put("features", features);

                        JSONObject audioParams = new JSONObject();
                        audioParams.put("format", "opus");
                        audioParams.put("sample_rate", 16000);
                        audioParams.put("channels", 1);
                        audioParams.put("frame_duration", OPUS_FRAME_DURATION_MS);

                        helloMessage.put("audio_params", audioParams);

                        AppLog.i(TAG, "Sending hello: " + helloMessage.toString());
                        send(helloMessage.toString());
                    } catch (Exception e) {
                        AppLog.e(TAG, "Error sending hello", e);
                    }
                }

                @Override
                public void onMessage(String message) {
                    AppLog.i(TAG, "Received message: " + message);
                    try {
                        JSONObject json = new JSONObject(message);
                        String type = json.optString("type");
                        if ("hello".equals(type)) {
                            parseServerHello(json);
                        } else {
                            if (listener != null)
                                listener.onJsonMessage(json);
                        }
                    } catch (Exception e) {
                        AppLog.e(TAG, "Error parsing JSON message: " + message, e);
                    }
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    // AppLog.v(TAG, "Received audio binary: " + bytes.remaining());
                    if (listener != null) {
                        byte[] data = new byte[bytes.remaining()];
                        bytes.get(data);
                        listener.onAudioData(data);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    AppLog.w(TAG, "WebSocket closed. Code: " + code + ", Reason: " + reason + ", Remote: " + remote);
                    isOpened = false;
                    if (listener != null)
                        listener.onDisconnected(reason != null && !reason.isEmpty() ? reason : "Code " + code);

                    if (helloLatch != null)
                        helloLatch.countDown();
                }

                @Override
                public void onError(Exception ex) {
                    AppLog.e(TAG, "WebSocket error", ex);
                    isOpened = false;
                    if (listener != null)
                        listener.onError(ex.toString());
                    if (helloLatch != null)
                        helloLatch.countDown();
                }
            };
            webSocketClient.setConnectionLostTimeout(60); // Standard heartbeat
            boolean connected = webSocketClient.connectBlocking(60, TimeUnit.SECONDS);

            if (!connected) {
                AppLog.e(TAG, "Connection Failed");
                close();
                return false;
            }

            // Wait for handshake with timeout
            boolean success = helloLatch.await(10, TimeUnit.SECONDS);
            if (!success) {
                AppLog.e(TAG, "Server Handshake Timeout");
                close();
                return false;
            }
            return true;

        } catch (Exception e) {
            AppLog.e(TAG, "Start error", e);
            return false;
        }
    }

    private void parseServerHello(JSONObject root) {
        String transport = root.optString("transport");
        if (!"websocket".equals(transport)) {
            AppLog.e(TAG, "Unsupported transport: " + transport);
            return;
        }
        sessionId = root.optString("session_id");
        AppLog.i(TAG, "Session ID: " + sessionId);
        if (helloLatch != null)
            helloLatch.countDown();
    }

    public void sendAudio(byte[] data) {
        if (webSocketClient != null && isOpened) {
            webSocketClient.send(data);
        } else {
            AppLog.w(TAG, "Cannot send audio: socket not open");
        }
    }

    public void sendText(String text) {
        if (webSocketClient != null && isOpened) {
            webSocketClient.send(text);
        }
    }

    public void sendStartListening() {
        try {
            JSONObject json = new JSONObject();
            json.put("session_id", sessionId);
            json.put("type", "listen");
            json.put("state", "start");
            json.put("mode", "auto");
            sendText(json.toString());
        } catch (Exception e) {
            AppLog.e(TAG, "Error sending start listening", e);
        }
    }

    public void sendStopListening() {
        try {
            JSONObject json = new JSONObject();
            json.put("session_id", sessionId);
            json.put("type", "listen");
            json.put("state", "stop");
            sendText(json.toString());
        } catch (Exception e) {
            AppLog.e(TAG, "Error sending stop listening", e);
        }
    }

    public void close() {
        if (webSocketClient != null) {
            webSocketClient.close();
            webSocketClient = null;
        }
        isOpened = false;
    }

    public boolean isOpened() {
        return isOpened;
    }
}
