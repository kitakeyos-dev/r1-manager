package com.phicomm.r1manager.server.manager;

import android.content.Context;
import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.config.AppConfig;
import com.phicomm.r1manager.server.model.WolDevice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

/**
 * WolManager - Wake-on-LAN Business Logic
 * Provides WOL packet sending and device management
 */
public class WolManager {
    private static final String TAG = "WolManager";
    private static WolManager instance;
    private final AppConfig config;

    private WolManager(Context context) {
        this.config = AppConfig.getInstance(context);
    }

    public static synchronized WolManager getInstance(Context context) {
        if (instance == null) {
            instance = new WolManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Send Wake-on-LAN magic packet to the specified MAC address
     * 
     * @param macAddress MAC address in format XX:XX:XX:XX:XX:XX or
     *                   XX-XX-XX-XX-XX-XX
     * @throws IllegalArgumentException if MAC format is invalid
     * @throws Exception                on network errors
     */
    public void sendMagicPacket(String macAddress) throws Exception {
        byte[] macBytes = getMacBytes(macAddress);
        byte[] magicPacket = new byte[6 + 16 * macBytes.length];

        // First 6 bytes: 0xFF
        for (int i = 0; i < 6; i++) {
            magicPacket[i] = (byte) 0xff;
        }

        // Repeat MAC address 16 times
        for (int i = 6; i < magicPacket.length; i += macBytes.length) {
            System.arraycopy(macBytes, 0, magicPacket, i, macBytes.length);
        }

        // Broadcast on port 9
        InetAddress broadcast = InetAddress.getByName("255.255.255.255");
        DatagramPacket packet = new DatagramPacket(magicPacket, magicPacket.length, broadcast, 9);

        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        socket.send(packet);
        socket.close();

        AppLog.i(TAG, "Wake-on-LAN magic packet sent to " + macAddress);
    }

    /**
     * Convert MAC address string to bytes
     */
    private byte[] getMacBytes(String macStr) throws IllegalArgumentException {
        byte[] bytes = new byte[6];
        String[] hex = macStr.split("(\\:|\\-)");

        if (hex.length != 6) {
            throw new IllegalArgumentException("Invalid MAC address format: " + macStr);
        }

        try {
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) Integer.parseInt(hex[i], 16);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex digit in MAC address: " + macStr);
        }

        return bytes;
    }

    /**
     * Validate MAC address format
     */
    public boolean isValidMac(String mac) {
        if (mac == null || mac.trim().isEmpty())
            return false;
        return mac.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    }

    // --- Device CRUD ---

    public List<WolDevice> getDevices() {
        return config.getWolDevices();
    }

    public WolDevice getDeviceByMac(String mac) {
        for (WolDevice device : getDevices()) {
            if (device.getMac().equalsIgnoreCase(mac)) {
                return device;
            }
        }
        return null;
    }

    public WolDevice getDeviceByName(String name) {
        for (WolDevice device : getDevices()) {
            if (device.getName() != null && device.getName().equalsIgnoreCase(name)) {
                return device;
            }
        }
        return null;
    }

    public boolean saveDevice(WolDevice device) {
        if (device.getMac() == null || device.getMac().trim().isEmpty()) {
            return false;
        }

        List<WolDevice> devices = config.getWolDevices();
        // Remove existing if same MAC to update
        devices.remove(device);
        devices.add(0, device);

        // Keep only top 10
        if (devices.size() > 10) {
            devices = devices.subList(0, 10);
        }

        config.saveWolDevices(devices);
        return true;
    }

    public boolean removeDevice(String mac) {
        List<WolDevice> devices = config.getWolDevices();
        String macToMatch = mac.trim();

        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getMac().equalsIgnoreCase(macToMatch)) {
                devices.remove(i);
                config.saveWolDevices(devices);
                return true;
            }
        }
        return false;
    }
}
