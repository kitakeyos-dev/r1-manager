package com.phicomm.r1manager.server.model.dto;

import com.google.gson.annotations.SerializedName;

public class CommonDto {
    public static class AppInfo {
        public String name;
        @SerializedName("package")
        public String pkg;
        public String version;
        public boolean isSystem;
        public boolean isRunning;
        public String icon; // Base64
    }

    public static class LogcatDto {
        public String logs;
        public int lines;
        public String error;
    }

    public static class ShellResult {
        public String stdout;
        public String stderr;
        public int exitCode;
        public boolean success;
    }

    public static class VolumeInfo {
        public StreamVolume music;
        public StreamVolume ring;
        public StreamVolume alarm;
        public StreamVolume notification;
    }

    public static class StreamVolume {
        public int current;
        public int max;
        public int percent;
    }
}
