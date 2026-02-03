package com.phicomm.r1manager.server.manager;

import android.app.ActivityManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Environment;
import android.os.StatFs;
import com.phicomm.r1manager.util.AppLog;

import com.phicomm.r1manager.server.model.dto.CommonDto;
import com.phicomm.r1manager.server.model.dto.SystemDto;
import com.phicomm.r1manager.server.client.HardwareClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * SystemManager - Business logic for system info and controls
 */
public class SystemManager {

    private static final String TAG = "SystemManager";

    private Context context;
    private AudioManager audioManager;

    public SystemManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    // ==================== System Info ====================

    private SystemDto.RamInfo cachedRam;
    private SystemDto.StorageInfo cachedStorage;
    private SystemDto.CpuInfo cachedCpu;
    private SystemDto.DeviceInfo cachedDevice;

    private long lastRamTime = 0;
    private long lastStorageTime = 0;
    private long lastCpuTime = 0;
    private static final long CACHE_MS = 2000; // 2 seconds cache for stats

    public SystemDto.SystemInfo getSystemInfo() {
        SystemDto.SystemInfo info = new SystemDto.SystemInfo();
        try {
            info.ram = getRamInfo();
            info.storage = getStorageInfo();
            info.cpu = getCpuInfo();
            info.device = getDeviceInfo();
        } catch (Exception e) {
            AppLog.e(TAG, "Error getting system info", e);
        }
        return info;
    }

    private SystemDto.RamInfo getRamInfo() throws Exception {
        long now = System.currentTimeMillis();
        if (cachedRam != null && (now - lastRamTime) < CACHE_MS) {
            return cachedRam;
        }

        SystemDto.RamInfo ram = new SystemDto.RamInfo();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memInfo);

        long totalMem = memInfo.totalMem;
        long availMem = memInfo.availMem;
        long usedMem = totalMem - availMem;

        ram.total = totalMem;
        ram.available = availMem;
        ram.used = usedMem;
        ram.usedPercent = Math.round((usedMem * 100.0) / totalMem);

        cachedRam = ram;
        lastRamTime = now;
        return ram;
    }

    private SystemDto.StorageInfo getStorageInfo() throws Exception {
        long now = System.currentTimeMillis();
        if (cachedStorage != null && (now - lastStorageTime) < CACHE_MS * 5) { // 10s for storage
            return cachedStorage;
        }

        SystemDto.StorageInfo storage = new SystemDto.StorageInfo();
        java.io.File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());

        long blockSize = stat.getBlockSize();
        long total = (long) stat.getBlockCount() * blockSize;
        long available = (long) stat.getAvailableBlocks() * blockSize;
        long used = total - available;

        storage.total = total;
        storage.available = available;
        storage.used = used;
        storage.usedPercent = Math.round((used * 100.0) / total);

        cachedStorage = storage;
        lastStorageTime = now;
        return storage;
    }

    private SystemDto.CpuInfo getCpuInfo() throws Exception {
        long now = System.currentTimeMillis();
        if (cachedCpu != null && (now - lastCpuTime) < CACHE_MS) {
            return cachedCpu;
        }

        SystemDto.CpuInfo cpu = new SystemDto.CpuInfo();
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();
            reader.close();

            String[] toks = load.split(" +");
            long idle = Long.parseLong(toks[4]);
            long total = 0;
            for (int i = 1; i < toks.length; i++) {
                total += Long.parseLong(toks[i]);
            }
            if (total > 0) {
                cpu.usage = 100 - ((idle * 100) / total);
            } else {
                cpu.usage = 0;
            }
        } catch (Exception e) {
            cpu.usage = 0;
        }
        cpu.cores = Runtime.getRuntime().availableProcessors();

        cachedCpu = cpu;
        lastCpuTime = now;
        return cpu;
    }

    private SystemDto.DeviceInfo getDeviceInfo() throws Exception {
        if (cachedDevice != null) {
            return cachedDevice;
        }

        SystemDto.DeviceInfo device = new SystemDto.DeviceInfo();
        device.model = android.os.Build.MODEL;
        device.manufacturer = android.os.Build.MANUFACTURER;
        device.androidVersion = android.os.Build.VERSION.RELEASE;
        device.sdkLevel = android.os.Build.VERSION.SDK_INT;

        cachedDevice = device;
        return device;
    }

    // ==================== Logcat ====================

    public CommonDto.LogcatDto getLogcat(int lines, String filter) {
        CommonDto.LogcatDto result = new CommonDto.LogcatDto();
        try {
            List<String> command = new ArrayList<>();
            command.add("logcat");
            command.add("-d");
            command.add("-t");
            command.add(String.valueOf(lines));

            if (filter != null && !filter.isEmpty()) {
                command.add("-s");
                command.add(filter);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();
            process.waitFor();

            result.logs = output.toString();
            result.lines = lines;
        } catch (Exception e) {
            AppLog.e(TAG, "Error reading logcat", e);
            result.logs = "";
            result.error = e.getMessage();
        }
        return result;
    }

    public boolean clearLogcat() {
        try {
            Process process = Runtime.getRuntime().exec("logcat -c");
            return process.waitFor() == 0;
        } catch (Exception e) {
            AppLog.e(TAG, "Error clearing logcat", e);
            return false;
        }
    }

    // ==================== Shell ====================

    public CommonDto.ShellResult executeShell(String command) {
        CommonDto.ShellResult result = new CommonDto.ShellResult();

        // Try HardwareClient first for System Privileges
        try {
            HardwareClient client = HardwareClient.getInstance();
            if (client != null) {
                // HardwareClient has its own blocking reconnect logic in sendJson
                AppLog.i(TAG, "Executing via HardwareClient: " + command);
                JSONObject wsResponse = client.sendShellCommand(command, 10000); // 10s timeout for complex commands

                if (wsResponse != null) {
                    String stdout = "";
                    String stderr = "";
                    int exitCode = 0;

                    // Parse hardware service response format
                    // Standard hardware service response usually has data as string
                    if (wsResponse.has("data")) {
                        Object dataObj = wsResponse.get("data");
                        if (dataObj instanceof String) {
                            stdout = (String) dataObj;
                        } else {
                            stdout = dataObj.toString();
                        }
                    }

                    if (wsResponse.has("error")) {
                        stderr = wsResponse.getString("error");
                    }

                    if (wsResponse.has("code")) {
                        int code = wsResponse.getInt("code");
                        exitCode = (code == 200) ? 0 : code;
                    }

                    result.stdout = stdout;
                    result.stderr = stderr;
                    result.exitCode = exitCode;
                    result.success = (exitCode == 0);
                    return result;
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "HardwareClient execution failed, falling back to Runtime", e);
        }

        try {
            AppLog.i(TAG, "Executing via Runtime: " + command);
            Process process = Runtime.getRuntime().exec(new String[] { "sh", "-c", command });

            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            String line;

            while ((line = stdoutReader.readLine()) != null)
                stdout.append(line).append("\n");
            while ((line = stderrReader.readLine()) != null)
                stderr.append(line).append("\n");

            stdoutReader.close();
            stderrReader.close();

            int exitCode = process.waitFor();

            result.stdout = stdout.toString();
            result.stderr = stderr.toString();
            result.exitCode = exitCode;
            result.success = (exitCode == 0);
        } catch (Exception e) {
            AppLog.e(TAG, "Error executing command", e);
            result.stdout = "";
            result.stderr = e.getMessage();
            result.exitCode = -1;
            result.success = false;
        }
        return result;
    }

    // ==================== Volume ====================

    public CommonDto.VolumeInfo getVolumeInfo() {
        CommonDto.VolumeInfo info = new CommonDto.VolumeInfo();
        try {
            info.music = getStreamVolume(AudioManager.STREAM_MUSIC);
            info.ring = getStreamVolume(AudioManager.STREAM_RING);
            info.alarm = getStreamVolume(AudioManager.STREAM_ALARM);
            info.notification = getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        } catch (Exception e) {
            AppLog.e(TAG, "Error getting volume", e);
        }
        return info;
    }

    private CommonDto.StreamVolume getStreamVolume(int stream) throws Exception {
        CommonDto.StreamVolume vol = new CommonDto.StreamVolume();
        int current = audioManager.getStreamVolume(stream);
        int max = audioManager.getStreamMaxVolume(stream);
        vol.current = current;
        vol.max = max;
        vol.percent = Math.round((current * 100.0f) / max);
        return vol;
    }

    public boolean setVolume(String streamType, int percent) {
        try {
            int stream;
            switch (streamType.toLowerCase()) {
                case "music":
                    stream = AudioManager.STREAM_MUSIC;
                    break;
                case "ring":
                    stream = AudioManager.STREAM_RING;
                    break;
                case "alarm":
                    stream = AudioManager.STREAM_ALARM;
                    break;
                case "notification":
                    stream = AudioManager.STREAM_NOTIFICATION;
                    break;
                default:
                    stream = AudioManager.STREAM_MUSIC;
            }

            int max = audioManager.getStreamMaxVolume(stream);
            int volume = Math.round((percent * max) / 100.0f);
            audioManager.setStreamVolume(stream, volume, 0);
            return true;
        } catch (Exception e) {
            AppLog.e(TAG, "Error setting volume", e);
            return false;
        }
    }
}
