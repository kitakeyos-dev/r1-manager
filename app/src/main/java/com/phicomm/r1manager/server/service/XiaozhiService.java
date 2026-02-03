package com.phicomm.r1manager.server.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import com.phicomm.r1manager.util.AppLog;

import com.phicomm.r1manager.server.manager.XiaozhiAudioEngine;
import com.phicomm.r1manager.server.manager.MusicServiceManager;
import com.phicomm.r1manager.mcp.McpManager;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.phicomm.r1manager.server.manager.LedManager;
import com.phicomm.r1manager.server.voicebot.protocol.WebsocketProtocol;
import com.phicomm.r1manager.server.model.xiaozhi.XiaozhiBotProfile;
import com.phicomm.r1manager.config.XiaozhiConfig;

public class XiaozhiService extends Service
        implements WebsocketProtocol.ProtocolListener, LedManager.LedActivitySource {
    private static final String TAG = "XiaozhiService";

    public enum State {
        IDLE,
        CONNECTING,
        CONNECTED,
        LISTENING,
        SPEAKING,
        ERROR
    }

    private WebsocketProtocol protocol;
    private XiaozhiAudioEngine audioEngine;

    // Background execution
    private ExecutorService networkExecutor; // For connections
    private ExecutorService backgroundExecutor; // For audio/logic tasks
    private Handler mainHandler;

    private McpManager mcpManager;

    private volatile State currentState = State.IDLE;
    private volatile String lastError = null;
    private volatile boolean pendingStartConversation = false;

    public String getStatus() {
        if (lastError != null)
            return "Error: " + lastError;
        switch (currentState) {
            case CONNECTING:
                return "Connecting...";
            case CONNECTED:
                return "Connected (Idle)";
            case LISTENING:
                return "Listening";
            case SPEAKING:
                return "Speaking";
            case ERROR:
                return "Error (Check Logs)";
            default:
                return "Disconnected";
        }
    }

    public State getState() {
        return currentState;
    }

    private void setState(State state) {
        if (this.currentState != state) {
            AppLog.i(TAG, "State transition: " + currentState + " -> " + state);
            this.currentState = state;

            // Update LED based on state
            LedManager ledManager = LedManager.getInstance();
            if (state == State.LISTENING) {
                ledManager.showListening();
            } else if (state == State.SPEAKING) {
                ledManager.showSpeaking();
            } else {
                // Notify LED manager if transition suggests idling
                ledManager.checkAndGatedStop();
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.i(TAG, "XiaozhiService Created");
        MusicServiceManager.registerXiaozhiService(this);

        networkExecutor = Executors.newSingleThreadExecutor();
        backgroundExecutor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(getMainLooper());
        audioEngine = new XiaozhiAudioEngine(this);

        initializeProtocol();
        startWakeDetection();
        initializeMcp();

        // Register with LED manager
        LedManager.getInstance().registerActivitySource(this);
    }

    private void startWakeDetection() {
        if (audioEngine != null) {
            audioEngine.startWakeDetection(() -> {
                AppLog.i(TAG, "Wake word detected! Starting conversation...");
                // Visual feedback: Light up the ring Blue
                LedManager.getInstance().setRingLed("7fffff8000", "ff", "WAKE_LIGHT");
                mainHandler.post(this::startConversation);
            });
        }
    }

    private void initializeProtocol() {
        if (currentState == State.CONNECTING)
            return;

        // Cleanup existing protocol
        if (protocol != null) {
            protocol.close();
            protocol = null;
        }

        XiaozhiConfig config = XiaozhiConfig.getInstance(this);
        XiaozhiBotProfile activeProfile = config.getActiveProfile();

        if (activeProfile == null) {
            AppLog.e(TAG, "No active bot profile found!");
            setState(State.ERROR);
            lastError = "No Bot Configured";
            return;
        }

        String deviceId = getDeviceIdForProfile(activeProfile, config);
        String uuid = activeProfile.uuid;
        String wsUrl = activeProfile.wsUrl;

        final WebsocketProtocol currentProtocol = new WebsocketProtocol(deviceId, uuid, wsUrl, "test-token");
        this.protocol = currentProtocol;

        currentProtocol.setListener(new WebsocketProtocol.ProtocolListener() {
            @Override
            public void onAudioData(byte[] data) {
                if (protocol != currentProtocol)
                    return;
                XiaozhiService.this.onAudioData(data);
            }

            @Override
            public void onJsonMessage(org.json.JSONObject json) {
                if (protocol != currentProtocol)
                    return;
                XiaozhiService.this.onJsonMessage(json);
            }

            @Override
            public void onConnected() {
                if (protocol != currentProtocol)
                    return;
                XiaozhiService.this.onConnected();
            }

            @Override
            public void onDisconnected(String reason) {
                if (protocol != currentProtocol) {
                    AppLog.w(TAG, "Ignored disconnect from OLD protocol instance");
                    return;
                }
                XiaozhiService.this.onDisconnected(reason);
            }

            @Override
            public void onError(String message) {
                if (protocol != currentProtocol)
                    return;
                XiaozhiService.this.onError(message);
            }
        });

        setState(State.CONNECTING);
        lastError = null;

        networkExecutor.execute(() -> {
            boolean connected = false;
            try {
                connected = currentProtocol.start();
            } catch (Exception e) {
                AppLog.e(TAG, "Protocol start exception", e);
                lastError = "Connection Error: " + e.getMessage();
            }

            if (connected) {
                if (protocol == currentProtocol) {
                    setState(State.CONNECTED);
                    if (pendingStartConversation) {
                        AppLog.i(TAG, "Executing pending conversation start...");
                        mainHandler.post(XiaozhiService.this::startConversation);
                    }
                }
            } else {
                if (protocol == currentProtocol) {
                    pendingStartConversation = false;
                    if (lastError == null)
                        lastError = "Handshake Failed";
                    setState(State.ERROR);
                }
            }
        });
    }

    public void reloadConfig() {
        AppLog.i(TAG, "Reloading Configuration...");
        stopConversation("Configuration Reload");

        // Shutdown old MCP connection if exists
        if (mcpManager != null) {
            mcpManager.shutdown();
            mcpManager = null;
        }

        initializeProtocol();
        initializeMcp(); // Reinitialize MCP for new profile
    }

    public synchronized boolean startConversation() {
        if (!isReadyForConversation()) {
            AppLog.i(TAG, "Requesting conversation while disconnected. Pending...");
            pendingStartConversation = true;
            initializeProtocol();
            return false;
        }

        pendingStartConversation = false;
        keepListening = true;

        try {
            audioEngine.startRecording(sentenceListener);
            protocol.sendStartListening();
            setState(State.LISTENING);
            return true;
        } catch (Exception e) {
            AppLog.e(TAG, "Start conversation failed", e);
            lastError = "Audio Error: " + e.getMessage();
            setState(State.ERROR);
            return false;
        }
    }

    public void stopConversation(String reason) {
        keepListening = false;
        if (!isInConversation()) {
            return;
        }

        if (protocol != null) {
            protocol.sendStopListening();
        }
        audioEngine.stopRecording();
        setState(State.CONNECTED);
        startWakeDetection();
    }

    public void stopConversation() {
        stopConversation("Manual");
    }

    @Override
    public void onAudioData(byte[] data) {
        // Received audio from server to play
        audioEngine.playAudio(data);
        if (currentState != State.SPEAKING) {
            setState(State.SPEAKING);
        }
    }

    private boolean keepListening = true;

    @Override
    public void onJsonMessage(JSONObject json) {
        String type = json.optString("type");
        if ("tts".equals(type) && "stop".equals(json.optString("state"))) {
            // TTS Finished
            if (currentState == State.SPEAKING) {
                // Run in background to avoid blocking WebSocket thread or connection management
                backgroundExecutor.execute(() -> {
                    audioEngine.waitForPlaybackCompletion();
                    if (keepListening) {
                        AppLog.i(TAG, "Auto-restarting listening...");
                        try {
                            if (protocol != null && protocol.isOpened()) {
                                protocol.sendStartListening();
                            }
                            audioEngine.startRecording(sentenceListener);
                            setState(State.LISTENING);
                        } catch (Exception e) {
                            AppLog.e(TAG, "Failed to auto-restart conversation", e);
                            setState(State.CONNECTED);
                        }
                    } else {
                        setState(State.CONNECTED);
                        startWakeDetection();
                    }
                });
            }
        } else if ("listen".equals(type) && "stop".equals(json.optString("state"))) {
            // Server commanded stop listening
            stopConversation("Server Stop");
        }
    }

    @Override
    public void onConnected() {
        AppLog.i(TAG, "Socket Connected Callback");
    }

    @Override
    public void onDisconnected(String reason) {
        pendingStartConversation = false;
        stopConversation("Disconnected");
        lastError = reason;
        setState(State.IDLE);
    }

    @Override
    public void onError(String message) {
        pendingStartConversation = false;
        lastError = message;
        setState(State.ERROR);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LedManager.getInstance().unregisterActivitySource(this);

        if (mcpManager != null) {
            mcpManager.shutdown();
        }

        if (protocol != null)
            protocol.close();
        if (audioEngine != null)
            audioEngine.release();
        if (networkExecutor != null)
            networkExecutor.shutdownNow();
        if (backgroundExecutor != null)
            backgroundExecutor.shutdownNow();
    }

    @Override
    public boolean isLedActivityActive() {
        return currentState == State.LISTENING ||
                currentState == State.SPEAKING ||
                currentState == State.CONNECTING ||
                pendingStartConversation;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Helper methods

    private String getDeviceIdForProfile(XiaozhiBotProfile profile, XiaozhiConfig config) {
        return "default".equals(profile.id) ? config.getDeviceId() : profile.customMac;
    }

    private boolean isReadyForConversation() {
        return currentState == State.CONNECTED || currentState == State.SPEAKING;
    }

    private boolean isInConversation() {
        return currentState == State.LISTENING || currentState == State.SPEAKING;
    }

    private void handleOutgoingAudio(byte[] encodedData) {
        if (currentState == State.LISTENING && protocol != null && protocol.isOpened()) {
            protocol.sendAudio(encodedData);
        }
    }

    /**
     * SentenceListener for VAD-based audio recording.
     */
    private final XiaozhiAudioEngine.SentenceListener sentenceListener =
            new XiaozhiAudioEngine.SentenceListener() {
        @Override
        public void onAudioFrame(byte[] encodedFrame) {
            handleOutgoingAudio(encodedFrame);
        }

        @Override
        public void onSpeechStart() {
            // Could update UI or LED here
        }

        @Override
        public void onSentenceComplete() {
            // All audio already sent via onAudioFrame()
        }
    };

    private void initializeMcp() {
        AppLog.d(TAG, "Processing initializeMcp...");
        XiaozhiConfig config = XiaozhiConfig.getInstance(this);
        XiaozhiBotProfile activeProfile = config.getActiveProfile();

        if (activeProfile == null) {
            AppLog.w(TAG, "No active profile, MCP not initialized");
            return;
        }

        // Check if this profile has MCP configured
        if (activeProfile.mcpUrl == null || activeProfile.mcpUrl.isEmpty()) {
            AppLog.i(TAG, "MCP not configured for profile: " + activeProfile.name);
            return;
        }

        if (activeProfile.mcpToken == null || activeProfile.mcpToken.isEmpty()) {
            AppLog.w(TAG, "MCP token missing for profile: " + activeProfile.name);
            return;
        }

        try {
            mcpManager = new McpManager(this);
            mcpManager.initialize(activeProfile.mcpUrl, activeProfile.mcpToken);
            AppLog.i(TAG, "MCP initialized for " + activeProfile.name + ": " + activeProfile.mcpUrl);
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to initialize MCP for profile: " + activeProfile.name, e);
        }
    }
}
