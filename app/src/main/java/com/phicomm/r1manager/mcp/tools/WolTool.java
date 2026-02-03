package com.phicomm.r1manager.mcp.tools;

import android.content.Context;
import com.phicomm.r1manager.mcp.model.McpToolParameter;
import com.phicomm.r1manager.mcp.model.McpToolResponse;
import com.phicomm.r1manager.server.manager.WolManager;
import com.phicomm.r1manager.server.model.WolDevice;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * WolTool - Wake-on-LAN MCP Tool
 * Allows AI to wake computers on the local network
 */
public class WolTool extends BaseMcpTool {

    public WolTool(Context context) {
        super(context, "wake_on_lan",
                "Bật máy tính từ xa bằng Wake-on-LAN (gửi Magic Packet qua mạng LAN). " +
                "SỬ DỤNG KHI người dùng nói: 'bật máy tính', 'mở PC', 'wake máy', 'khởi động máy [tên]', " +
                "'bật máy phòng khách', 'wake PC gaming'. " +
                "Ví dụ: 'Bật máy tính của anh lên' → dùng action=list xem có máy nào, rồi action=wake.");
    }

    @Override
    public String getDescription() {
        WolManager manager = WolManager.getInstance(context);
        List<WolDevice> devices = manager.getDevices();

        StringBuilder desc = new StringBuilder();
        desc.append("Công cụ Wake-on-LAN - Gửi Magic Packet để bật máy tính từ xa.\n\n");
        desc.append("CÁC ACTIONS:\n");
        desc.append("- wake: Gửi magic packet để bật máy (cần: mac hoặc device_name)\n");
        desc.append("- list: Liệt kê các thiết bị đã lưu\n\n");

        if (!devices.isEmpty()) {
            desc.append("THIẾT BỊ ĐÃ LƯU (").append(devices.size()).append("):\n");
            for (WolDevice device : devices) {
                desc.append("- ").append(device.getName())
                        .append(" (").append(device.getMac()).append(")\n");
            }
        } else {
            desc.append("THIẾT BỊ: Chưa có thiết bị nào được lưu.\n");
        }

        return desc.toString();
    }

    @Override
    public List<McpToolParameter> getParameters() {
        return Arrays.asList(
                McpToolParameter.builder("action", "string")
                        .description(
                                "Hành động cần thực hiện:\n" +
                                        "- wake: Gửi magic packet bật máy (cần mac hoặc device_name)\n" +
                                        "- list: Xem danh sách thiết bị đã lưu")
                        .required(true)
                        .build(),

                McpToolParameter.builder("mac", "string")
                        .description(
                                "Địa chỉ MAC của máy cần bật.\n" +
                                        "Format: XX:XX:XX:XX:XX:XX hoặc XX-XX-XX-XX-XX-XX")
                        .required(false)
                        .build(),

                McpToolParameter.builder("device_name", "string")
                        .description(
                                "Tên thiết bị đã lưu.\n" +
                                        "Sử dụng thay cho MAC nếu thiết bị đã được lưu trước đó.")
                        .required(false)
                        .build());
    }

    @Override
    protected McpToolResponse executeInternal(Map<String, Object> params) {
        String action = getStringParam(params, "action", "").toLowerCase();
        WolManager manager = WolManager.getInstance(context);

        try {
            switch (action) {
                case "wake":
                    return handleWake(params, manager);

                case "list":
                    return handleList(manager);

                default:
                    return McpToolResponse.error(
                            "[ERROR] Hành động không hợp lệ: '" + action + "'\n" +
                                    "Các action hợp lệ: wake, list");
            }
        } catch (Exception e) {
            return McpToolResponse.error("[ERROR] Lỗi WOL: " + e.getMessage());
        }
    }

    private McpToolResponse handleWake(Map<String, Object> params, WolManager manager) {
        String mac = getStringParam(params, "mac", "");
        String deviceName = getStringParam(params, "device_name", "");

        // Resolve MAC from device name if not provided
        if (mac.isEmpty() && !deviceName.isEmpty()) {
            WolDevice device = manager.getDeviceByName(deviceName);
            if (device != null) {
                mac = device.getMac();
            } else {
                return McpToolResponse.error(
                        "[ERROR] Không tìm thấy thiết bị: '" + deviceName + "'\n" +
                                "Sử dụng action=list để xem danh sách thiết bị.");
            }
        }

        if (mac.isEmpty()) {
            return McpToolResponse.error(
                    "[ERROR] Cần cung cấp 'mac' hoặc 'device_name' để bật máy.");
        }

        // Validate MAC format
        if (!manager.isValidMac(mac)) {
            return McpToolResponse.error(
                    "[ERROR] Định dạng MAC không hợp lệ: '" + mac + "'\n" +
                            "Format đúng: XX:XX:XX:XX:XX:XX hoặc XX-XX-XX-XX-XX-XX");
        }

        try {
            manager.sendMagicPacket(mac);

            // Get device name for display
            WolDevice device = manager.getDeviceByMac(mac);
            String displayName = (device != null && device.getName() != null)
                    ? device.getName() + " (" + mac + ")"
                    : mac;

            return McpToolResponse.success(
                    "[SUCCESS] Đã gửi Magic Packet!\n\n" +
                            "Thiết bị: " + displayName + "\n" +
                            "Giao thức: UDP Broadcast (Port 9)\n\n" +
                            "LƯU Ý: Máy tính cần:\n" +
                            "- Bật tính năng WOL trong BIOS/UEFI\n" +
                            "- Bật WOL trong driver card mạng\n" +
                            "- Kết nối mạng LAN (có dây hoặc WiFi hỗ trợ WOL)");

        } catch (IllegalArgumentException e) {
            return McpToolResponse.error("[ERROR] " + e.getMessage());
        } catch (Exception e) {
            return McpToolResponse.error(
                    "[ERROR] Không thể gửi Magic Packet: " + e.getMessage());
        }
    }

    private McpToolResponse handleList(WolManager manager) {
        List<WolDevice> devices = manager.getDevices();

        if (devices.isEmpty()) {
            return McpToolResponse.success(
                    "[LIST] Chưa có thiết bị WOL nào được lưu.\n\n" +
                            "Bạn có thể trực tiếp gửi wake với action=wake và mac=XX:XX:XX:XX:XX:XX");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[LIST] Danh sách thiết bị WOL (").append(devices.size()).append("):\n\n");

        for (int i = 0; i < devices.size(); i++) {
            WolDevice device = devices.get(i);
            sb.append((i + 1)).append(". ").append(device.getName()).append("\n");
            sb.append("   MAC: ").append(device.getMac()).append("\n");
            sb.append("\n");
        }

        sb.append("Gợi ý: Dùng action=wake với device_name để bật máy.");

        return McpToolResponse.success(sb.toString());
    }
}
