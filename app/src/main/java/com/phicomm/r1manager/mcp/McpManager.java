package com.phicomm.r1manager.mcp;

import android.content.Context;
import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.mcp.client.McpClient;
import com.phicomm.r1manager.mcp.client.McpConnectionManager;
import com.phicomm.r1manager.mcp.registry.McpToolRegistry;
import com.phicomm.r1manager.mcp.tools.MusicControlTool;
import com.phicomm.r1manager.mcp.tools.IpAddressTool;
import com.phicomm.r1manager.mcp.tools.MemoryTool;
import com.phicomm.r1manager.mcp.tools.WolTool;

/**
 * Manager for MCP client lifecycle and tool registration
 */
public class McpManager {
    private static final String TAG = "McpManager";

    private final Context context;
    private McpConnectionManager connectionManager;
    private McpToolRegistry toolRegistry;

    public McpManager(Context context) {
        this.context = context;
    }

    /**
     * Initialize and connect to MCP server
     * 
     * @param serverUrl MCP WebSocket URL
     * @param authToken Bearer token for authentication
     */
    public void initialize(String serverUrl, String authToken) {
        AppLog.i(TAG, "Initializing MCP Manager with URL: " + serverUrl);

        // Create tool registry
        toolRegistry = McpToolRegistry.getInstance();
        registerBuiltInTools();

        // Create connection manager with fresh initialization logic
        AppLog.d(TAG, "Creating McpConnectionManager with URL and Token...");
        connectionManager = new McpConnectionManager(context, serverUrl, authToken, toolRegistry);

        // Connect
        AppLog.i(TAG, "Starting MCP connection process...");
        connectionManager.connect();
    }

    /**
     * Register all built-in tools
     */
    private void registerBuiltInTools() {
        try {
            toolRegistry.register(new MusicControlTool(context));
            toolRegistry.register(new IpAddressTool(context));
            toolRegistry.register(new MemoryTool(context));
            toolRegistry.register(new WolTool(context));

            AppLog.i(TAG, "Registered " + toolRegistry.getToolCount() + " built-in tools");
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to register tools", e);
        }
    }

    /**
     * Check if connected to MCP server
     */
    public boolean isConnected() {
        return connectionManager != null && connectionManager.isConnected();
    }

    /**
     * Disconnect and cleanup
     */
    public void shutdown() {
        AppLog.i(TAG, "Shutting down MCP Manager");

        if (connectionManager != null) {
            connectionManager.disconnect();
        }

        if (toolRegistry != null) {
            toolRegistry.clear();
        }
    }

    /**
     * Get tool registry for custom tool registration
     */
    public McpToolRegistry getToolRegistry() {
        return toolRegistry;
    }
}
