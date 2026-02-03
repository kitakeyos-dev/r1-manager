package com.phicomm.r1manager.mcp.client;

import android.os.Handler;
import android.os.Looper;
import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.util.ThreadManager;
import com.phicomm.r1manager.mcp.registry.McpToolRegistry;
import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages MCP connection lifecycle with automatic reconnection
 */
public class McpConnectionManager {
    private static final String TAG = "McpConnectionManager";

    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final int BASE_RECONNECT_DELAY_MS = 1000;
    private static final int MAX_RECONNECT_DELAY_MS = 60000;

    private final String serverUrl;
    private final String authToken;
    private final McpToolRegistry toolRegistry;
    private final Context context;
    private McpClient client;
    private final Handler handler;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);

    public McpConnectionManager(Context context, String serverUrl, String authToken, McpToolRegistry toolRegistry) {
        this.context = context;
        this.serverUrl = serverUrl;
        this.authToken = authToken;
        this.toolRegistry = toolRegistry;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void connect() {
        shouldReconnect.set(true);
        reconnectAttempts.set(0);
        attemptConnection();
    }

    public void disconnect() {
        shouldReconnect.set(false);
        isConnected.set(false);

        if (client != null) {
            client.close();
            client = null;
        }
    }

    public boolean isConnected() {
        return isConnected.get();
    }

    public Context getContext() {
        return context;
    }

    void onConnected() {
        isConnected.set(true);
        reconnectAttempts.set(0);
        AppLog.i(TAG, "Connection established");
    }

    void onDisconnected(String reason) {
        isConnected.set(false);
        AppLog.w(TAG, "Disconnected: " + reason);

        if (shouldReconnect.get()) {
            scheduleReconnect();
        }
    }

    void onError(Exception ex) {
        AppLog.e(TAG, "Connection error", ex);
    }

    private void attemptConnection() {
        if (isConnected.get()) {
            return;
        }

        AppLog.i(TAG, "Attempting connection to: " + serverUrl);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Always create a fresh client instance as they are not reusable
                    if (client != null) {
                        try {
                            client.close();
                        } catch (Exception ignored) {
                        }
                    }

                    client = new McpClient(serverUrl, authToken);
                    client.setToolRegistry(toolRegistry);
                    client.setConnectionManager(McpConnectionManager.this);

                    boolean success = client.connectBlocking();
                    if (!success) {
                        AppLog.w(TAG, "Connection failed (timeout or other error)");
                        if (shouldReconnect.get()) {
                            scheduleReconnect();
                        }
                    }
                } catch (Exception e) {
                    AppLog.e(TAG, "Connection attempt failed with exception", e);
                    if (shouldReconnect.get()) {
                        scheduleReconnect();
                    }
                }
            }
        }).start();
    }

    private void scheduleReconnect() {
        int attempts = reconnectAttempts.incrementAndGet();

        if (attempts > MAX_RECONNECT_ATTEMPTS) {
            AppLog.e(TAG, "Max reconnect attempts reached, giving up");
            shouldReconnect.set(false);
            return;
        }

        int delay = calculateBackoffDelay(attempts);
        AppLog.i(TAG, "Scheduling reconnect attempt " + attempts + " in " + delay + "ms");

        handler.postDelayed(this::attemptConnection, delay);
    }

    private int calculateBackoffDelay(int attempts) {
        int delay = BASE_RECONNECT_DELAY_MS * (int) Math.pow(2, attempts - 1);
        return Math.min(delay, MAX_RECONNECT_DELAY_MS);
    }
}
