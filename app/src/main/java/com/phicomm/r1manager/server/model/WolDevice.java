package com.phicomm.r1manager.server.model;

public class WolDevice {
    private String name;
    private String mac;

    public WolDevice() {
    }

    public WolDevice(String name, String mac) {
        this.name = name;
        this.mac = mac;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        WolDevice that = (WolDevice) o;
        return mac != null ? mac.equalsIgnoreCase(that.mac) : that.mac == null;
    }

    @Override
    public int hashCode() {
        return mac != null ? mac.toLowerCase().hashCode() : 0;
    }
}
