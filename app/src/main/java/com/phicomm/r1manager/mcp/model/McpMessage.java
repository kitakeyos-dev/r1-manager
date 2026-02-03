package com.phicomm.r1manager.mcp.model;

import com.phicomm.r1manager.util.AppLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * MCP Protocol message types and builders
 */
public class McpMessage {
    private static final String TAG = "McpMessage";

    /**
     * Build "report_tools" message to send tool definitions to server
     */
    public static JSONObject buildReportTools(List<McpTool> tools) {
        JSONObject message = new JSONObject();
        try {
            message.put("type", "report_tools");

            JSONArray toolsArray = new JSONArray();
            for (McpTool tool : tools) {
                JSONObject toolDef = new JSONObject();
                toolDef.put("name", tool.getName());
                toolDef.put("description", tool.getDescription());

                JSONObject parameters = new JSONObject();
                for (McpToolParameter param : tool.getParameters()) {
                    JSONObject paramDef = new JSONObject();
                    paramDef.put("type", param.getType());
                    paramDef.put("description", param.getDescription());
                    if (param.isRequired()) {
                        paramDef.put("required", true);
                    }
                    if (param.getDefaultValue() != null) {
                        paramDef.put("default", param.getDefaultValue());
                    }
                    parameters.put(param.getName(), paramDef);
                }
                toolDef.put("parameters", parameters);

                toolsArray.put(toolDef);
            }
            message.put("tools", toolsArray);
        } catch (JSONException e) {
            AppLog.e(TAG, "Failed to build report_tools message", e);
        }

        return message;
    }

    /**
     * Build "tool_response" message for standard MCP tools/call
     */
    public static JSONObject buildToolResponse(Object callId, McpToolResponse response) {
        JSONObject message = new JSONObject();
        try {
            message.put("jsonrpc", "2.0");
            message.put("id", callId);

            JSONObject result = new JSONObject();
            JSONArray content = new JSONArray();
            JSONObject contentItem = new JSONObject();

            contentItem.put("type", "text");
            if (response.isSuccess()) {
                Object res = response.getResult();
                contentItem.put("text", res != null ? res.toString() : "Success");
                result.put("isError", false);
            } else {
                contentItem.put("text", "Error: " + response.getError());
                result.put("isError", true);
            }

            content.put(contentItem);
            result.put("content", content);
            message.put("result", result);
        } catch (JSONException e) {
            AppLog.e(TAG, "Failed to build tool_response message", e);
        }

        return message;
    }

    /**
     * Build "notifications/initialized" message
     */
    public static JSONObject buildInitializedNotification() {
        JSONObject message = new JSONObject();
        try {
            message.put("jsonrpc", "2.0");
            message.put("method", "notifications/initialized");
        } catch (JSONException e) {
            AppLog.e(TAG, "Failed to build initialized notification", e);
        }
        return message;
    }

    /**
     * Build response for "tools/list" request (Pull model)
     */
    public static JSONObject buildToolsListResponse(List<McpTool> tools, Object requestId) {
        JSONObject response = new JSONObject();
        try {
            response.put("jsonrpc", "2.0");
            response.put("id", requestId);

            JSONObject result = new JSONObject();
            JSONArray toolsArray = new JSONArray();
            for (McpTool tool : tools) {
                JSONObject toolDef = new JSONObject();
                toolDef.put("name", tool.getName());
                toolDef.put("description", tool.getDescription());

                JSONObject inputSchema = new JSONObject();
                inputSchema.put("type", "object");
                JSONObject properties = new JSONObject();
                JSONArray requiredArray = new JSONArray();

                for (McpToolParameter param : tool.getParameters()) {
                    JSONObject paramDef = new JSONObject();
                    paramDef.put("type", param.getType());
                    paramDef.put("description", param.getDescription());
                    if (param.getDefaultValue() != null) {
                        paramDef.put("default", param.getDefaultValue());
                    }
                    properties.put(param.getName(), paramDef);
                    if (param.isRequired()) {
                        requiredArray.put(param.getName());
                    }
                }
                inputSchema.put("properties", properties);
                if (requiredArray.length() > 0) {
                    inputSchema.put("required", requiredArray);
                }
                toolDef.put("inputSchema", inputSchema);
                toolsArray.put(toolDef);
            }
            result.put("tools", toolsArray);
            response.put("result", result);
        } catch (JSONException e) {
            AppLog.e(TAG, "Failed to build tools/list response", e);
        }
        return response;
    }

    /**
     * Parse incoming "call_tool" or "tools/call" message
     */
    public static CallToolRequest parseCallTool(JSONObject message) {
        String method = message.optString("method");
        Object callId = message.opt("id");
        String toolName;
        JSONObject params;

        if ("tools/call".equals(method)) {
            // Standard MCP format: params: { name: "tool", arguments: {...} }
            JSONObject methodParams = message.optJSONObject("params");
            toolName = methodParams != null ? methodParams.optString("name") : "";
            params = methodParams != null ? methodParams.optJSONObject("arguments") : new JSONObject();
        } else {
            // Legacy/Lite format: name: "tool", parameters: {...}
            toolName = message.optString("name");
            params = message.optJSONObject("parameters");
        }

        return new CallToolRequest(callId, toolName, params);
    }

    /**
     * Build "initialize" response for MCP handshake
     */
    public static JSONObject buildInitializeResponse(Object requestId) {
        JSONObject response = new JSONObject();
        try {
            response.put("jsonrpc", "2.0");
            response.put("id", requestId);

            JSONObject result = new JSONObject();
            result.put("protocolVersion", "2024-11-05");

            JSONObject capabilities = new JSONObject();

            // Tools capability
            JSONObject toolsCap = new JSONObject();
            toolsCap.put("listChanged", true);
            capabilities.put("tools", toolsCap);

            // Resources capability
            JSONObject resourcesCap = new JSONObject();
            resourcesCap.put("subscribe", false);
            resourcesCap.put("listChanged", true);
            capabilities.put("resources", resourcesCap);

            // Prompts capability (NEW - for auto context injection)
            JSONObject promptsCap = new JSONObject();
            promptsCap.put("listChanged", true);
            capabilities.put("prompts", promptsCap);

            result.put("capabilities", capabilities);

            JSONObject serverInfo = new JSONObject();
            serverInfo.put("name", "Xiaozhi-R1-Manager");
            serverInfo.put("version", "1.2.0");
            result.put("serverInfo", serverInfo);

            response.put("result", result);
        } catch (JSONException e) {
            AppLog.e(TAG, "Failed to build initialize response", e);
        }
        return response;
    }

    /**
     * Build response for "prompts/list" request
     */
    public static JSONObject buildPromptsListResponse(Object requestId, JSONArray promptsArray) {
        JSONObject response = new JSONObject();
        try {
            response.put("jsonrpc", "2.0");
            response.put("id", requestId);

            JSONObject result = new JSONObject();
            result.put("prompts", promptsArray);
            response.put("result", result);
        } catch (JSONException e) {
            AppLog.e(TAG, "Failed to build prompts/list response", e);
        }
        return response;
    }

    /**
     * Build response for "prompts/get" request
     */
    public static JSONObject buildPromptGetResponse(Object requestId, JSONArray messages) {
        JSONObject response = new JSONObject();
        try {
            response.put("jsonrpc", "2.0");
            response.put("id", requestId);

            JSONObject result = new JSONObject();
            result.put("messages", messages);
            response.put("result", result);
        } catch (JSONException e) {
            AppLog.e(TAG, "Failed to build prompts/get response", e);
        }
        return response;
    }

    /**
     * Build response for "resources/list" request
     */
    public static JSONObject buildResourcesListResponse(Object requestId, JSONArray resourcesArray) {
        JSONObject response = new JSONObject();
        try {
            response.put("jsonrpc", "2.0");
            response.put("id", requestId);

            JSONObject result = new JSONObject();
            result.put("resources", resourcesArray);
            response.put("result", result);
        } catch (JSONException e) {
            AppLog.e(TAG, "Failed to build resources/list response", e);
        }
        return response;
    }

    /**
     * Build response for "resources/read" request
     */
    public static JSONObject buildResourceReadResponse(Object requestId, String uri, String text) {
        JSONObject response = new JSONObject();
        try {
            response.put("jsonrpc", "2.0");
            response.put("id", requestId);

            JSONObject result = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            content.put("uri", uri);
            content.put("text", text);
            content.put("mimeType", "text/plain");
            contents.put(content);

            result.put("contents", contents);
            response.put("result", result);
        } catch (JSONException e) {
            AppLog.e(TAG, "Failed to build resources/read response", e);
        }
        return response;
    }

    /**
     * Build standard JSON-RPC success response (for ping)
     */
    public static JSONObject buildSuccessResponse(Object requestId) {
        JSONObject response = new JSONObject();
        try {
            response.put("jsonrpc", "2.0");
            response.put("id", requestId);
            response.put("result", new JSONObject());
        } catch (JSONException e) {
            AppLog.e(TAG, "Failed to build success response", e);
        }
        return response;
    }

    public static class CallToolRequest {
        public final Object callId;
        public final String toolName;
        public final JSONObject parameters;

        public CallToolRequest(Object callId, String toolName, JSONObject parameters) {
            this.callId = callId;
            this.toolName = toolName;
            this.parameters = parameters != null ? parameters : new JSONObject();
        }
    }
}
