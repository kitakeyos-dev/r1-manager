package com.phicomm.r1manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.phicomm.r1manager.util.AppLog;

import com.phicomm.r1manager.server.service.AudioVisualizerService;
import com.phicomm.r1manager.server.service.XiaozhiService;
import com.phicomm.r1manager.server.service.MusicLedSyncService;

/**
 * BootReceiver - Automatically starts service on various system events
 * Handles: Boot completed, Package replaced/updated
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action == null)
            return;

        AppLog.i(TAG, "Received action: " + action);

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
                AppLog.i(TAG, "Boot completed, starting service");
                startService(context);
                break;

            case Intent.ACTION_MY_PACKAGE_REPLACED:
                AppLog.i(TAG, "Package updated, restarting service");
                startService(context);
                break;

            case "android.intent.action.QUICKBOOT_POWERON":
                // Some devices use this for fast boot
                AppLog.i(TAG, "Quick boot, starting service");
                startService(context);
                break;
        }
    }

    private void startService(Context context) {
        try {
            // Start WebServerService
            Intent webServerIntent = new Intent(context, WebServerService.class);
            context.startService(webServerIntent);
            AppLog.i(TAG, "WebServerService start requested");

            // Start XiaozhiService
            Intent xiaozhiIntent = new Intent(context, XiaozhiService.class);
            context.startService(xiaozhiIntent);
            AppLog.i(TAG, "XiaozhiService start requested");

            // Start AudioVisualizerService
            Intent audioVisualizerIntent = new Intent(context, AudioVisualizerService.class);
            context.startService(audioVisualizerIntent);
            AppLog.i(TAG, "AudioVisualizerService start requested");

            // Start MusicLedSyncService
            Intent musicLedSyncIntent = new Intent(context, MusicLedSyncService.class);
            context.startService(musicLedSyncIntent);
            AppLog.i(TAG, "MusicLedSyncService start requested");

        } catch (Exception e) {
            AppLog.e(TAG, "Failed to start services", e);
        }
    }
}
