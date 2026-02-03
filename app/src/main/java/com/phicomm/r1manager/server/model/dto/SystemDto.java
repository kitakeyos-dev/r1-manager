package com.phicomm.r1manager.server.model.dto;

public class SystemDto {
    public static class SystemInfo {
        public RamInfo ram;
        public StorageInfo storage;
        public CpuInfo cpu;
        public DeviceInfo device;
    }

    public static class RamInfo {
        public long total;
        public long available;
        public long used;
        public long usedPercent;
    }

    public static class StorageInfo {
        public long total;
        public long available;
        public long used;
        public long usedPercent;
    }

    public static class CpuInfo {
        public long usage;
        public int cores;
    }

    public static class DeviceInfo {
        public String model;
        public String manufacturer;
        public String androidVersion;
        public int sdkLevel;
    }
}
