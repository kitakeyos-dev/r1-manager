package com.phicomm.r1manager.mcp.client;

import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.mcp.model.McpMessage;
import com.phicomm.r1manager.mcp.model.McpTool;
import com.phicomm.r1manager.mcp.model.McpToolResponse;
import com.phicomm.r1manager.mcp.registry.McpToolRegistry;
import com.phicomm.r1manager.util.LogBuffer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import com.phicomm.r1manager.mcp.tools.BaseMcpTool;
import com.phicomm.r1manager.server.manager.MemoryManager;
import java.util.List;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket client for MCP protocol communication
 */
public class McpClient extends WebSocketClient {
    private static final String TAG = "McpClient";

    private final String authToken;
    private McpToolRegistry toolRegistry;
    private McpConnectionManager connectionManager;
    private final java.util.concurrent.ExecutorService executorService;

    public McpClient(String serverUri, String authToken) {
        super(buildUriWithToken(serverUri, authToken));
        this.authToken = authToken;
        this.executorService = java.util.concurrent.Executors.newFixedThreadPool(3);
    }

    private static URI buildUriWithToken(String serverUri, String token) {
        try {
            // Append token as query parameter if provided
            if (token != null && !token.isEmpty()) {
                String separator = serverUri.contains("?") ? "&" : "?";
                return URI.create(serverUri + separator + "token=" + token);
            }
            return URI.create(serverUri);
        } catch (Exception e) {
            return URI.create(serverUri);
        }
    }

    public void setToolRegistry(McpToolRegistry registry) {
        this.toolRegistry = registry;
    }

    public void setConnectionManager(McpConnectionManager manager) {
        this.connectionManager = manager;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        AppLog.i(TAG, "Connected to MCP server");

        if (connectionManager != null) {
            connectionManager.onConnected();
        }

        // Register all tools on connect
        registerTools();
    }

    @Override
    public void onMessage(String message) {
        AppLog.d(TAG, "Received: " + message);

        try {
            JSONObject json = new JSONObject(message);
            handleMcpMessage(json);
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to handle message", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        AppLog.w(TAG, "Disconnected: " + reason + " (code: " + code + ")");

        if (connectionManager != null) {
            connectionManager.onDisconnected(reason);
        }
    }

    @Override
    public void onError(Exception ex) {
        AppLog.e(TAG, "WebSocket error: " + ex.getMessage());

        if (connectionManager != null) {
            connectionManager.onError(ex);
        }
    }

    private void registerTools() {
        if (toolRegistry == null) {
            AppLog.w(TAG, "No tool registry set, skipping tool registration");
            return;
        }

        try {
            JSONObject message = McpMessage.buildReportTools(toolRegistry.getAllTools());
            send(message.toString());
            int toolCount = toolRegistry.getAllTools().size();
            AppLog.i(TAG, "Registered " + toolCount + " tools");

            StringBuilder toolList = new StringBuilder();
            java.util.List<McpTool> tools = toolRegistry.getAllTools();
            for (int i = 0; i < tools.size(); i++) {
                toolList.append(tools.get(i).getName());
                if (i < tools.size() - 1) {
                    toolList.append(", ");
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to register tools", e);
        }
    }

    private void handleMcpMessage(JSONObject json) {
        String type = json.optString("type");
        String method = json.optString("method");
        Object id = json.opt("id");

        if ("call_tool".equals(type) || "call_tool".equals(method) || "tools/call".equals(method)) {
            handleCallTool(json);
        } else if ("initialize".equals(method)) {
            handleInitialize(id);
        } else if ("ping".equals(method)) {
            handlePing(id);
        } else if ("tools/list".equals(method)) {
            handleToolsList(id);
        } else if ("resources/list".equals(method)) {
            handleResourcesList(id);
        } else if ("resources/read".equals(method)) {
            handleResourcesRead(json);
        } else if ("prompts/list".equals(method)) {
            handlePromptsList(id);
        } else if ("prompts/get".equals(method)) {
            handlePromptsGet(json);
        } else if ("notifications/initialized".equals(method)) {
            AppLog.i(TAG, "Broker sent initialized notification");
        } else if (!type.isEmpty() || !method.isEmpty()) {
            AppLog.d(TAG, "Unhandled message: " + (type.isEmpty() ? method : type));
        }
    }

    private void handleInitialize(Object requestId) {
        // 1. Send response
        JSONObject response = McpMessage.buildInitializeResponse(requestId);
        send(response.toString());
        AppLog.i(TAG, "Sent initialize response for id " + requestId);

        // 2. Send initialized notification (required by broker)
        JSONObject notification = McpMessage.buildInitializedNotification();
        send(notification.toString());
        AppLog.i(TAG, "Sent notifications/initialized notification");
    }

    private void handlePing(Object requestId) {
        JSONObject response = McpMessage.buildSuccessResponse(requestId);
        send(response.toString());
        AppLog.d(TAG, "Sent ping response for id " + requestId);
    }

    private void handleToolsList(Object requestId) {
        if (toolRegistry == null) {
            AppLog.w(TAG, "No tool registry, sending empty tools list");
            JSONObject response = McpMessage.buildToolsListResponse(new java.util.ArrayList<>(), requestId);
            send(response.toString());
            return;
        }

        JSONObject response = McpMessage.buildToolsListResponse(toolRegistry.getAllTools(), requestId);
        send(response.toString());
        AppLog.i(TAG, "Sent tools/list response with " + toolRegistry.getToolCount() + " tools");
    }

    private void handleResourcesList(Object requestId) {
        JSONArray resources = new JSONArray();
        try {
            JSONObject res = new JSONObject();
            res.put("uri", "memory://context");
            res.put("name", "Bộ nhớ AI (Sở thích & Bối cảnh)");
            res.put("description", "Chứa các thông tin quan trọng AI đã ghi nhớ về người dùng và thiết bị.");
            res.put("mimeType", "text/plain");
            resources.put(res);
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to build resources list", e);
        }

        JSONObject response = McpMessage.buildResourcesListResponse(requestId, resources);
        send(response.toString());
        AppLog.i(TAG, "Sent resources/list response");
    }

    private void handleResourcesRead(JSONObject json) {
        Object requestId = json.opt("id");
        JSONObject params = json.optJSONObject("params");
        String uri = params != null ? params.optString("uri") : "";

        if ("memory://context".equals(uri)) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        MemoryManager mm = MemoryManager.getInstance(connectionManager.getContext());
                        String allData = mm.getAll();

                        JSONObject response = McpMessage.buildResourceReadResponse(requestId, uri, allData);
                        send(response.toString());
                        AppLog.i(TAG, "Sent resources/read response for internal memory");
                    } catch (Exception e) {
                        AppLog.e(TAG, "Failed to read memory resource", e);
                    }
                }
            });
        } else {
            // Placeholder for other URIs
            AppLog.w(TAG, "Unknown resource requested: " + uri);
        }
    }

    private void handlePromptsList(Object requestId) {
        JSONArray prompts = new JSONArray();
        try {
            JSONObject prompt = new JSONObject();
            prompt.put("name", "memory_context");
            prompt.put("description", "Bối cảnh bộ nhớ AI - Tự động inject vào mỗi conversation");
            prompts.put(prompt);
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to build prompts list", e);
        }

        JSONObject response = McpMessage.buildPromptsListResponse(requestId, prompts);
        send(response.toString());
        AppLog.i(TAG, "Sent prompts/list response");
    }

    private void handlePromptsGet(JSONObject json) {
        Object requestId = json.opt("id");
        JSONObject params = json.optJSONObject("params");
        String name = params != null ? params.optString("name") : "";

        if ("memory_context".equals(name)) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        MemoryManager mm = MemoryManager.getInstance(connectionManager.getContext());
                        String allData = mm.getAll();

                        JSONArray messages = new JSONArray();
                        JSONObject systemMessage = new JSONObject();
                        systemMessage.put("role", "user");

                        StringBuilder content = new StringBuilder();
                        content.append("# BỘ NHỚ CỦA TÔI\n\n");
                        content.append(allData);

                        systemMessage.put("content",
                                new JSONObject().put("type", "text").put("text", content.toString()));
                        messages.put(systemMessage);

                        JSONObject response = McpMessage.buildPromptGetResponse(requestId, messages);
                        send(response.toString());
                        AppLog.i(TAG, "Sent prompts/get response with memory context");
                    } catch (Exception e) {
                        AppLog.e(TAG, "Failed to create prompt", e);
                    }
                }
            });
        } else {
            AppLog.w(TAG, "Unknown prompt requested: " + name);
        }
    }

    private void handleCallTool(JSONObject json) {
        if (toolRegistry == null) {
            AppLog.e(TAG, "Cannot execute tool: no registry");
            return;
        }

        McpMessage.CallToolRequest request = McpMessage.parseCallTool(json);

        // Execute tool using thread pool
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    McpTool tool = toolRegistry.getTool(request.toolName);
                    if (tool == null) {
                        sendError(request.callId, "Tool not found: " + request.toolName);
                        return;
                    }

                    Map<String, Object> params = BaseMcpTool.jsonToMap(request.parameters);
                    McpToolResponse response = tool.execute(params);
                    sendResponse(request.callId, response);

                    if (response.isSuccess()) {
                        AppLog.i(TAG, "Tool succeeded: " + request.toolName);
                    } else {
                        AppLog.w(TAG, "Tool failed: " + request.toolName + " - " + response.getError());
                    }

                } catch (Exception e) {
                    AppLog.e(TAG, "Tool execution failed", e);
                    sendError(request.callId, "Execution failed: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void close() {
        super.close();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void sendResponse(Object callId, McpToolResponse response) {
        try {
            JSONObject message = McpMessage.buildToolResponse(callId, response);
            send(message.toString());
            AppLog.d(TAG, "Sent response for call " + callId);
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to send response", e);
        }
    }

    private void sendError(Object callId, String errorMessage) {
        sendResponse(callId, McpToolResponse.error(errorMessage));
    }
}
