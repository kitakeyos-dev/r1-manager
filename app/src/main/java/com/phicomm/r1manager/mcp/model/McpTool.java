package com.phicomm.r1manager.mcp.model;

import java.util.List;
import java.util.Map;

/**
 * Interface for MCP Tool definition.
 * Each tool represents a capability that can be called by AI assistant.
 */
public interface McpTool {
    /**
     * Get unique tool name (e.g., "play_music", "get_system_info")
     */
    String getName();

    /**
     * Get human-readable description for AI
     */
    String getDescription();

    /**
     * Get list of parameters this tool accepts
     */
    List<McpToolParameter> getParameters();

    /**
     * Execute the tool with given parameters
     * 
     * @param params Parameter map from AI request
     * @return Tool execution result
     */
    McpToolResponse execute(Map<String, Object> params);

    /**
     * Validate parameters before execution
     * 
     * @param params Parameter map to validate
     * @throws IllegalArgumentException if validation fails
     */
    default void validateParameters(Map<String, Object> params) {
        for (McpToolParameter param : getParameters()) {
            if (param.isRequired() && !params.containsKey(param.getName())) {
                throw new IllegalArgumentException("Missing required parameter: " + param.getName());
            }
        }
    }
}
