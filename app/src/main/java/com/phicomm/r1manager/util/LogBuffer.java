package com.phicomm.r1manager.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Circular buffer to store app logs for Web UI display
 */
public class LogBuffer {
    private static final int MAX_LOGS = 1000;
    private static LogBuffer instance;

    private final List<LogEntry> logs = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private LogBuffer() {
    }

    public static synchronized LogBuffer getInstance() {
        if (instance == null) {
            instance = new LogBuffer();
        }
        return instance;
    }

    public synchronized void log(String level, String tag, String message) {
        String timestamp = timeFormat.format(new Date());
        LogEntry entry = new LogEntry(timestamp, level, tag, message);

        logs.add(entry);

        // Keep only last MAX_LOGS entries
        if (logs.size() > MAX_LOGS) {
            logs.remove(0);
        }
    }

    public synchronized List<LogEntry> getLogs() {
        return new ArrayList<>(logs);
    }

    public synchronized List<LogEntry> getLogs(int count) {
        int start = Math.max(0, logs.size() - count);
        return new ArrayList<>(logs.subList(start, logs.size()));
    }

    public synchronized void clear() {
        logs.clear();
    }

    public static class LogEntry {
        public final String timestamp;
        public final String level;
        public final String tag;
        public final String message;

        public LogEntry(String timestamp, String level, String tag, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }
    }
}
