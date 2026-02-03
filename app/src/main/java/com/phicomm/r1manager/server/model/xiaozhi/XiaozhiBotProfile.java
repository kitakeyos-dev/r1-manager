package com.phicomm.r1manager.server.model.xiaozhi;

public class XiaozhiBotProfile {
    public String id;
    public String name;
    public String wsUrl;
    public String qtaUrl;
    public String macType; // "REAL" or "FAKE"
    public String customMac;
    public String uuid;

    // MCP Configuration (per-profile)
    public String mcpUrl; // MCP WebSocket endpoint for this profile
    public String mcpToken; // MCP authentication token for this profile

    public XiaozhiBotProfile() {
    }

    public XiaozhiBotProfile(String id, String name, String wsUrl, String qtaUrl, String macType, String customMac,
            String uuid) {
        this.id = id;
        this.name = name;
        this.wsUrl = wsUrl;
        this.qtaUrl = qtaUrl;
        this.macType = macType;
        this.customMac = customMac;
        this.uuid = uuid;
    }
}
