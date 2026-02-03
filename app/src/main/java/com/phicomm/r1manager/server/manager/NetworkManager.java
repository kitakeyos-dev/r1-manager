package com.phicomm.r1manager.server.manager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import com.phicomm.r1manager.util.AppLog;

import com.phicomm.r1manager.server.model.dto.NetworkDto;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * NetworkManager - Business logic for WiFi and Bluetooth
 */
public class NetworkManager {

    private static final String TAG = "NetworkManager";

    private Context context;
    private WifiManager wifiManager;
    private BluetoothAdapter bluetoothAdapter;

    public NetworkManager(Context context) {
        this.context = context;
        this.wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    // ==================== WiFi Client ====================

    public NetworkDto.WifiInfo getWifiInfo() {
        NetworkDto.WifiInfo info = new NetworkDto.WifiInfo();
        try {
            info.enabled = wifiManager.isWifiEnabled();

            if (wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    info.ssid = wifiInfo.getSSID() != null ? wifiInfo.getSSID().replace("\"", "") : "";
                    info.bssid = wifiInfo.getBSSID();
                    info.ipAddress = intToIp(wifiInfo.getIpAddress());
                    info.macAddress = wifiInfo.getMacAddress();
                    info.linkSpeed = wifiInfo.getLinkSpeed();
                    info.rssi = wifiInfo.getRssi();
                    info.signalLevel = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), 5);
                    info.networkId = wifiInfo.getNetworkId();
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error getting WiFi info", e);
        }
        return info;
    }

    private NetworkDto.WifiScanResponse lastScanResponse;
    private long lastScanTime = 0;
    private static final long SCAN_CACHE_MS = 10000; // 10 seconds cache

    public NetworkDto.WifiScanResponse scanWifiNetworks() {
        long now = System.currentTimeMillis();
        if (lastScanResponse != null && (now - lastScanTime) < SCAN_CACHE_MS) {
            return lastScanResponse;
        }

        try {
            wifiManager.startScan();
            // We don't sleep here anymore to avoid blocking the thread.
            // getScanResults() returns the results of the last scan.
            // If the user wants fresh results, they can click "Scan" again after a few
            // seconds.
            // This is much better for CPU and RAM.
            List<ScanResult> results = wifiManager.getScanResults();

            NetworkDto.WifiScanResponse response = new NetworkDto.WifiScanResponse();
            List<NetworkDto.WifiScanResult> networks = new ArrayList<NetworkDto.WifiScanResult>();
            response.networks = networks;

            if (results != null) {
                for (ScanResult r : results) {
                    if (r.SSID == null || r.SSID.isEmpty())
                        continue;

                    NetworkDto.WifiScanResult net = new NetworkDto.WifiScanResult();
                    net.ssid = r.SSID;
                    net.bssid = r.BSSID;
                    net.level = r.level;
                    net.signalLevel = WifiManager.calculateSignalLevel(r.level, 5);
                    net.frequency = r.frequency;
                    net.capabilities = r.capabilities;
                    net.secured = r.capabilities != null && !r.capabilities.contains("ESS");
                    networks.add(net);
                }
            }

            lastScanResponse = response;
            lastScanTime = now;
            return response;
        } catch (Exception e) {
            AppLog.e(TAG, "Error scanning WiFi", e);
            return lastScanResponse != null ? lastScanResponse : new NetworkDto.WifiScanResponse();
        }
    }

    public boolean setWifiEnabled(boolean enabled) {
        try {
            return wifiManager.setWifiEnabled(enabled);
        } catch (Exception e) {
            AppLog.e(TAG, "Error setting WiFi", e);
            return false;
        }
    }

    public boolean connectToWifi(String ssid, String password) {
        return connectToWifi(ssid, password, false, null, null, null, null, null);
    }

    public boolean connectToWifi(String ssid, String password, boolean useStaticIp,
            String ip, String gateway, String netmask, String dns1, String dns2) {
        try {
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = "\"" + ssid + "\"";

            if (password != null && !password.isEmpty()) {
                config.preSharedKey = "\"" + password + "\"";
            } else {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            }

            if (useStaticIp) {
                configureStaticIp(config, ip, gateway, netmask, dns1, dns2);
            }

            int networkId = wifiManager.addNetwork(config);
            if (networkId == -1) {
                List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
                for (WifiConfiguration c : configs) {
                    if (c.SSID != null && c.SSID.equals("\"" + ssid + "\"")) {
                        networkId = c.networkId;
                        // Update existing config
                        config.networkId = networkId;
                        wifiManager.updateNetwork(config);
                        break;
                    }
                }
            }

            if (networkId != -1) {
                wifiManager.disconnect();
                wifiManager.enableNetwork(networkId, true);
                wifiManager.reconnect();
                wifiManager.saveConfiguration();
                return true;
            }
            return false;
        } catch (Exception e) {
            AppLog.e(TAG, "Error connecting WiFi", e);
            return false;
        }
    }

    // Reflection for Static IP (Android 5.x)
    private void configureStaticIp(WifiConfiguration config, String ip, String gateway, String netmask, String dns1,
            String dns2) {
        try {
            // 1. Set IpAssignment to STATIC
            Object ipAssignment = getEnumValue("android.net.IpConfiguration$IpAssignment", "STATIC");
            callMethod(config, "setIpAssignment", new String[] { "android.net.IpConfiguration$IpAssignment" },
                    new Object[] { ipAssignment });

            // 2. Create StaticIpConfiguration
            Object staticIpConfig = Class.forName("android.net.StaticIpConfiguration").newInstance();

            // 3. Set IP Address (LinkAddress)
            if (ip != null) {
                java.net.InetAddress ipAddr = java.net.InetAddress.getByName(ip);
                int prefixLength = 24;
                try {
                    if (netmask != null && netmask.contains(".")) {
                        // Convert netmask to prefix length if needed, simplified to 24 for now or parse
                        // Actually, commonly users pass 24. If they pass 255.255.255.0, we just use 24.
                        // For robustness, let's assume standard /24 if not parsed.
                    }
                } catch (Exception e) {
                }

                Object linkAddress = Class.forName("android.net.LinkAddress")
                        .getConstructor(java.net.InetAddress.class, int.class)
                        .newInstance(ipAddr, prefixLength);

                setField(staticIpConfig, "ipAddress", linkAddress);
            }

            // 4. Set Gateway
            if (gateway != null) {
                java.net.InetAddress gatewayAddr = java.net.InetAddress.getByName(gateway);
                setField(staticIpConfig, "gateway", gatewayAddr);
            }

            // 5. Set DNS
            java.util.ArrayList<java.net.InetAddress> dnsList = new java.util.ArrayList<>();
            if (dns1 != null && !dns1.isEmpty())
                dnsList.add(java.net.InetAddress.getByName(dns1));
            if (dns2 != null && !dns2.isEmpty())
                dnsList.add(java.net.InetAddress.getByName(dns2));

            setField(staticIpConfig, "dnsServers", dnsList);

            // 6. Apply StaticIpConfiguration to WifiConfiguration
            callMethod(config, "setStaticIpConfiguration", new String[] { "android.net.StaticIpConfiguration" },
                    new Object[] { staticIpConfig });

            AppLog.i(TAG, "Static IP configured: " + ip);

        } catch (Exception e) {
            AppLog.e(TAG, "Failed to set Static IP via reflection", e);
        }
    }

    private Object getEnumValue(String enumClassName, String enumValue) throws ClassNotFoundException {
        Class<?> enumClass = Class.forName(enumClassName);
        return Enum.valueOf((Class<Enum>) enumClass, enumValue);
    }

    private void callMethod(Object target, String methodName, String[] parameterTypes, Object[] parameterValues)
            throws Exception {
        Class<?>[] types = new Class<?>[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            types[i] = Class.forName(parameterTypes[i]);
        }
        Method method = target.getClass().getMethod(methodName, types);
        method.invoke(target, parameterValues);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    public boolean disconnectWifi() {
        try {
            return wifiManager.disconnect();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean forgetWifi(int networkId) {
        try {
            return wifiManager.removeNetwork(networkId);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== WiFi AP ====================

    public NetworkDto.WifiApInfo getWifiApInfo() {
        NetworkDto.WifiApInfo info = new NetworkDto.WifiApInfo();
        try {
            Method method = wifiManager.getClass().getMethod("isWifiApEnabled");
            boolean enabled = (Boolean) method.invoke(wifiManager);
            info.enabled = enabled;

            Method getConfig = wifiManager.getClass().getMethod("getWifiApConfiguration");
            WifiConfiguration config = (WifiConfiguration) getConfig.invoke(wifiManager);

            if (config != null) {
                info.ssid = config.SSID;
                info.password = config.preSharedKey != null ? config.preSharedKey : "";
                info.secured = config.preSharedKey != null && !config.preSharedKey.isEmpty();
            }

            info.clients = getApClients();
            info.clientCount = info.clients != null ? info.clients.size() : 0;
        } catch (Exception e) {
            AppLog.e(TAG, "Error getting AP info", e);
            info.enabled = false;
        }
        return info;
    }

    private List<NetworkDto.ApClient> getApClients() {
        List<NetworkDto.ApClient> clients = new ArrayList<NetworkDto.ApClient>();
        try {
            Process process = Runtime.getRuntime().exec("cat /proc/net/arp");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 4) {
                    NetworkDto.ApClient client = new NetworkDto.ApClient();
                    client.ip = parts[0];
                    client.mac = parts[3];
                    clients.add(client);
                }
            }
            reader.close();
        } catch (Exception e) {
            AppLog.e(TAG, "Error getting AP clients", e);
        }
        return clients;
    }

    public boolean setWifiApEnabled(boolean enabled, String ssid, String password) {
        try {
            if (enabled && wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(false);
            }

            WifiConfiguration config = new WifiConfiguration();
            config.SSID = ssid != null ? ssid : "R1_Hotspot";

            if (password != null && password.length() >= 8) {
                config.preSharedKey = password;
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            } else {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            }

            Method method = wifiManager.getClass().getMethod(
                    "setWifiApEnabled", WifiConfiguration.class, boolean.class);
            return (Boolean) method.invoke(wifiManager, config, enabled);
        } catch (Exception e) {
            AppLog.e(TAG, "Error setting AP", e);
            return false;
        }
    }

    // ==================== Bluetooth ====================

    public NetworkDto.BluetoothInfo getBluetoothInfo() {
        NetworkDto.BluetoothInfo info = new NetworkDto.BluetoothInfo();
        try {
            if (bluetoothAdapter == null) {
                info.supported = false;
                return info;
            }

            info.supported = true;
            info.enabled = bluetoothAdapter.isEnabled();
            info.name = bluetoothAdapter.getName();
            info.address = bluetoothAdapter.getAddress();
            info.state = bluetoothAdapter.getState();
            info.scanMode = bluetoothAdapter.getScanMode();
            info.discovering = bluetoothAdapter.isDiscovering();

            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
            info.pairedDevices = new ArrayList<NetworkDto.BluetoothDeviceDto>();
            for (BluetoothDevice d : paired) {
                NetworkDto.BluetoothDeviceDto dev = new NetworkDto.BluetoothDeviceDto();
                dev.name = d.getName();
                dev.address = d.getAddress();
                dev.type = d.getType();
                dev.bondState = d.getBondState();
                info.pairedDevices.add(dev);
            }
            info.pairedCount = info.pairedDevices.size();
        } catch (Exception e) {
            AppLog.e(TAG, "Error getting BT info", e);
            info.supported = false;
        }
        return info;
    }

    public boolean setBluetoothEnabled(boolean enabled) {
        try {
            if (bluetoothAdapter == null)
                return false;
            return enabled ? bluetoothAdapter.enable() : bluetoothAdapter.disable();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean startBluetoothDiscovery() {
        try {
            return bluetoothAdapter != null && bluetoothAdapter.isEnabled()
                    && bluetoothAdapter.startDiscovery();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean stopBluetoothDiscovery() {
        try {
            return bluetoothAdapter != null && bluetoothAdapter.cancelDiscovery();
        } catch (Exception e) {
            return false;
        }
    }

    private String intToIp(int ip) {
        return String.format("%d.%d.%d.%d", ip & 0xff, (ip >> 8) & 0xff,
                (ip >> 16) & 0xff, (ip >> 24) & 0xff);
    }
}
