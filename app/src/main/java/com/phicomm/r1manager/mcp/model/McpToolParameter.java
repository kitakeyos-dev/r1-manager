package com.phicomm.r1manager.mcp.model;

/**
 * Tool parameter schema definition
 */
public class McpToolParameter {
    private final String name;
    private final String type;
    private final String description;
    private final boolean required;
    private final Object defaultValue;

    private McpToolParameter(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.description = builder.description;
        this.required = builder.required;
        this.defaultValue = builder.defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public static Builder builder(String name, String type) {
        return new Builder(name, type);
    }

    public static class Builder {
        private final String name;
        private final String type;
        private String description = "";
        private boolean required = false;
        private Object defaultValue = null;

        public Builder(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public McpToolParameter build() {
            return new McpToolParameter(this);
        }
    }
}
