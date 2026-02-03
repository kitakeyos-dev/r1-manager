package com.phicomm.r1manager.mcp.model;

/**
 * Response wrapper for MCP tool execution
 */
public class McpToolResponse {
    private final boolean success;
    private final Object result;
    private final String error;

    private McpToolResponse(boolean success, Object result, String error) {
        this.success = success;
        this.result = result;
        this.error = error;
    }

    public static McpToolResponse success(Object result) {
        return new McpToolResponse(true, result, null);
    }

    public static McpToolResponse success(String message) {
        return new McpToolResponse(true, message, null);
    }

    public static McpToolResponse error(String errorMessage) {
        return new McpToolResponse(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        if (success) {
            return "Success: " + (result != null ? result.toString() : "OK");
        } else {
            return "Error: " + error;
        }
    }
}
