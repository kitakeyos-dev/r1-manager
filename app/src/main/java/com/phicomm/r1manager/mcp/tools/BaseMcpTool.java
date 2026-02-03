package com.phicomm.r1manager.mcp.tools;

import android.content.Context;
import com.phicomm.r1manager.mcp.model.McpTool;
import com.phicomm.r1manager.mcp.model.McpToolParameter;
import com.phicomm.r1manager.mcp.model.McpToolResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for MCP tools with common functionality
 */
public abstract class BaseMcpTool implements McpTool {
    protected final Context context;
    private final String name;
    private final String description;

    protected BaseMcpTool(Context context, String name, String description) {
        this.context = context;
        this.name = name;
        this.description = description;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<McpToolParameter> getParameters() {
        return new ArrayList<>(); // Override if tool has parameters
    }

    @Override
    public McpToolResponse execute(Map<String, Object> params) {
        try {
            validateParameters(params);
            return executeInternal(params);
        } catch (IllegalArgumentException e) {
            return McpToolResponse.error("Invalid parameters: " + e.getMessage());
        } catch (Exception e) {
            return McpToolResponse.error("Execution failed: " + e.getMessage());
        }
    }

    /**
     * Internal execution logic to be implemented by subclasses
     */
    protected abstract McpToolResponse executeInternal(Map<String, Object> params);

    /**
     * Helper to get parameter value with type casting
     */
    protected String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    protected int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    protected boolean getBoolParam(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    /**
     * Convert JSON object to Map for easier parameter handling
     */
    public static Map<String, Object> jsonToMap(org.json.JSONObject json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null) {
            return map;
        }

        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, json.opt(key));
        }
        return map;
    }
}
