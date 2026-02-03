package com.phicomm.r1manager.server.manager;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.phicomm.r1manager.util.AppLog;

import com.phicomm.r1manager.server.model.dto.CommonDto;
import com.phicomm.r1manager.server.client.HardwareClient;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.os.Environment;

/**
 * AppManager - App management (Read Only)
 */
public class AppManager {

    private static final String TAG = "AppManager";

    private Context context;
    private PackageManager pm;
    private ActivityManager am;

    public AppManager(Context context) {
        this.context = context;
        this.pm = context.getPackageManager();
        this.am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }

    private List<CommonDto.AppInfo> cachedApps;
    private long lastAppsTime = 0;
    private static final long APPS_CACHE_MS = 20000; // 20 seconds cache

    /**
     * Get all installed apps with running status
     */
    @SuppressWarnings("deprecation")
    public List<CommonDto.AppInfo> getApps() {
        long now = System.currentTimeMillis();
        if (cachedApps != null && (now - lastAppsTime) < APPS_CACHE_MS) {
            return cachedApps;
        }

        List<CommonDto.AppInfo> apps = new ArrayList<CommonDto.AppInfo>();
        try {
            Set<String> runningPackages = new HashSet<String>();
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo proc : processes) {
                    runningPackages.add(proc.processName);
                }
            }

            List<PackageInfo> packages = pm.getInstalledPackages(0);
            for (PackageInfo pkg : packages) {
                boolean isSystem = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean isRunning = runningPackages.contains(pkg.packageName);

                CommonDto.AppInfo app = new CommonDto.AppInfo();
                app.name = pkg.applicationInfo.loadLabel(pm).toString();
                app.pkg = pkg.packageName;
                app.version = pkg.versionName != null ? pkg.versionName : "";
                app.isSystem = isSystem;
                app.isRunning = isRunning;
                apps.add(app);
            }

            cachedApps = apps;
            lastAppsTime = now;
        } catch (Exception e) {
            AppLog.e(TAG, "Error getting apps", e);
            if (cachedApps != null)
                return cachedApps;
        }
        return apps;
    }

    /**
     * Get APK file for a given package name
     */
    public java.io.File getApkFile(String packageName) {
        try {
            PackageInfo pkg = pm.getPackageInfo(packageName, 0);
            if (pkg != null && pkg.applicationInfo != null) {
                String sourceDir = pkg.applicationInfo.sourceDir;
                if (sourceDir != null) {
                    return new java.io.File(sourceDir);
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error finding APK for " + packageName, e);
        }
        return null;
    }

    /**
     * Launch an app
     */
    public boolean launchApp(String packageName) {
        try {
            // Try via launch intent first (standard way)
            android.content.Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } else {
                // Try via shell (monkey or am start) if no launch intent
                String cmd = "monkey -p " + packageName + " -c android.intent.category.LAUNCHER 1";
                HardwareClient client = HardwareClient.getInstance();
                if (client != null) {
                    client.sendShellCommand(cmd);
                    return true;
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error launching app " + packageName, e);
        }
        return false;
    }

    /**
     * Stop an app (Prevent system apps and self)
     */
    public boolean stopApp(String packageName) {
        if (isSystemApp(packageName) || context.getPackageName().equals(packageName))
            return false;
        try {
            String cmd = "am force-stop " + packageName;
            HardwareClient client = HardwareClient.getInstance();
            if (client != null) {
                client.sendShellCommand(cmd);
                return true;
            } else {
                // Fallback to Runtime.exec (might fail if not system)
                Runtime.getRuntime().exec(new String[] { "sh", "-c", cmd });
                return true; // Assume sent
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error stopping app " + packageName, e);
        }
        return false;
    }

    /**
     * Uninstall an app (Prevent system apps and self)
     */
    public boolean uninstallApp(String packageName) {
        if (isSystemApp(packageName) || context.getPackageName().equals(packageName))
            return false;
        try {
            String cmd = "pm uninstall " + packageName;
            HardwareClient client = HardwareClient.getInstance();
            if (client != null) {
                client.sendShellCommand(cmd);
                return true;
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error uninstalling app " + packageName, e);
        }
        return false;
    }

    private boolean isSystemApp(String packageName) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Install an APK from path
     */
    public boolean installApp(String path) {
        // NanoHTTPD temp files are in private cache, which 'system' user might not face
        // access to.
        // We copy to /sdcard/r1helper_temp.apk to ensure access.
        String tempSdPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/r1helper_temp.apk";
        try {
            copyFile(new java.io.File(path), new java.io.File(tempSdPath));

            // Allow time for flush?
            Thread.sleep(500);

            String cmd = "pm install -r " + tempSdPath;
            HardwareClient client = HardwareClient.getInstance();
            if (client != null) {
                // Wait up to 120 seconds for installation
                JSONObject response = client.sendShellCommand(cmd, 120000);

                // Cleanup
                new java.io.File(tempSdPath).delete();

                // Check if successful if possible, but for now return true if command sent
                if (response != null && response.has("data")) {
                    String output = response.getString("data");
                    if (output.contains("Success"))
                        return true;
                }
                return false; // Actually check response content
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error installing app from " + path, e);
        } finally {
            // Cleanup just in case
            new java.io.File(tempSdPath).delete();
        }
        return false;
    }

    private void copyFile(java.io.File src, java.io.File dst) throws java.io.IOException {
        java.io.FileInputStream inStream = new java.io.FileInputStream(src);
        java.io.FileOutputStream outStream = new java.io.FileOutputStream(dst);
        try {
            java.nio.channels.FileChannel inChannel = inStream.getChannel();
            java.nio.channels.FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            inStream.close();
            outStream.close();
        }
    }
}
