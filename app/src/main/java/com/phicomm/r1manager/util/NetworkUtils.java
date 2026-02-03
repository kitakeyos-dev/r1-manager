package com.phicomm.r1manager.util;

import com.phicomm.r1manager.util.AppLog;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * NetworkUtils - Network utility functions
 * Provides IP address detection
 */
public class NetworkUtils {

    private static final String TAG = "NetworkUtils";

    /**
     * Get local IP address
     * Prioritizes WiFi interfaces (wlan)
     * 
     * @return IP address string or null if not found
     */
    public static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

            // First try to find WiFi interface
            for (NetworkInterface intf : interfaces) {
                if (!intf.isUp() || intf.isLoopback())
                    continue;

                String name = intf.getName().toLowerCase();
                if (name.contains("wlan") || name.contains("eth")) {
                    String ip = getIpFromInterface(intf);
                    if (ip != null) {
                        return ip;
                    }
                }
            }

            // Fallback: try any interface
            for (NetworkInterface intf : interfaces) {
                if (!intf.isUp() || intf.isLoopback())
                    continue;

                String ip = getIpFromInterface(intf);
                if (ip != null) {
                    return ip;
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error getting IP address", e);
        }

        return null;
    }

    /**
     * Get IPv4 address from network interface
     */
    private static String getIpFromInterface(NetworkInterface intf) {
        List<InetAddress> addrs = Collections.list(intf.getInetAddresses());

        for (InetAddress addr : addrs) {
            if (addr.isLoopbackAddress())
                continue;

            String hostAddr = addr.getHostAddress();
            // Only IPv4
            if (hostAddr != null && !hostAddr.contains(":")) {
                return hostAddr;
            }
        }

        return null;
    }
}
