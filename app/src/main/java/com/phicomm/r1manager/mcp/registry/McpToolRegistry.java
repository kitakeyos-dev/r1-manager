package com.phicomm.r1manager.mcp.registry;

import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.mcp.model.McpTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for MCP tools
 */
public class McpToolRegistry {
    private static final String TAG = "McpToolRegistry";
    private static McpToolRegistry instance;

    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();

    private McpToolRegistry() {
    }

    public static synchronized McpToolRegistry getInstance() {
        if (instance == null) {
            instance = new McpToolRegistry();
        }
        return instance;
    }

    /**
     * Register a new tool
     * 
     * @throws IllegalArgumentException if tool with same name already exists
     */
    public void register(McpTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool cannot be null");
        }

        String name = tool.getName();
        if (tools.containsKey(name)) {
            AppLog.w(TAG, "Replacing existing tool: " + name);
        }

        tools.put(name, tool);
        AppLog.i(TAG, "Registered tool: " + name);
    }

    /**
     * Unregister a tool by name
     */
    public void unregister(String toolName) {
        if (tools.remove(toolName) != null) {
            AppLog.i(TAG, "Unregistered tool: " + toolName);
        }
    }

    /**
     * Get tool by name
     * 
     * @return Tool instance or null if not found
     */
    public McpTool getTool(String toolName) {
        return tools.get(toolName);
    }

    /**
     * Get all registered tools
     */
    public List<McpTool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * Check if tool is registered
     */
    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }

    /**
     * Get count of registered tools
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * Clear all registered tools
     */
    public void clear() {
        tools.clear();
        AppLog.i(TAG, "Cleared all tools");
    }
}
