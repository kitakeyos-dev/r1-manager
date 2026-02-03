package com.phicomm.r1manager.mcp.tools;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import com.phicomm.r1manager.mcp.model.McpToolParameter;
import com.phicomm.r1manager.mcp.model.McpToolResponse;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool for querying system information
 */
public class IpAddressTool extends BaseMcpTool {

    public IpAddressTool(Context context) {
        super(context, "get_ip_address",
                "Lấy địa chỉ IP và thông tin mạng WiFi của loa R1. " +
                "SỬ DỤNG KHI người dùng hỏi: 'IP của loa là gì', 'loa đang kết nối WiFi nào', " +
                "'địa chỉ mạng của tôi', 'thông tin kết nối'. " +
                "Trả về: IP nội bộ, tên WiFi (SSID), cường độ tín hiệu.");
    }

    @Override
    public List<McpToolParameter> getParameters() {
        return new ArrayList<>(); // No parameters needed
    }

    @Override
    protected McpToolResponse executeInternal(Map<String, Object> params) {
        try {
            JSONObject info = new JSONObject();

            // WiFi & IP Info
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);

            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    info.put("ssid", wifiInfo.getSSID());

                    int ipAddress = wifiInfo.getIpAddress();
                    String ipString = String.format("%d.%d.%d.%d",
                            (ipAddress & 0xff),
                            (ipAddress >> 8 & 0xff),
                            (ipAddress >> 16 & 0xff),
                            (ipAddress >> 24 & 0xff));

                    info.put("local_ip", ipString);
                    info.put("signal_strength", wifiInfo.getRssi());
                    info.put("link_speed", wifiInfo.getLinkSpeed() + " Mbps");
                } else {
                    return McpToolResponse.error("Không tìm thấy thông tin WiFi.");
                }
            } else {
                return McpToolResponse.error("WiFi Manager không khả dụng.");
            }

            // Return plain text for better voice feedback
            String result = "Thiết bị đang kết nối WiFi '" + info.optString("ssid") + "' "
                    + "với địa chỉ IP là " + info.optString("local_ip") + ".";

            return McpToolResponse.success(result);

        } catch (Exception e) {
            return McpToolResponse.error("Không thể lấy địa chỉ IP: " + e.getMessage());
        }
    }
}
