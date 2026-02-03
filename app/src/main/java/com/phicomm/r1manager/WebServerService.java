package com.phicomm.r1manager;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import com.phicomm.r1manager.util.AppLog;

import com.phicomm.r1manager.config.AppConfig;
import com.phicomm.r1manager.server.WebServer;

import java.io.IOException;

/**
 * WebServerService - Background service running the web server
 * Features:
 * - Foreground service (prevent system kill)
 * - START_STICKY (auto-restart by system)
 * - AlarmManager fallback (restart if service dies)
 * - Watchdog thread (monitor server health)
 */
public class WebServerService extends Service {

    private static final String TAG = "WebServerService";
    private static final int NOTIFICATION_ID = 1;
    private static final int RESTART_DELAY_MS = 5000;
    private static final int WATCHDOG_INTERVAL_MS = 30000;

    private WebServer webServer;
    private AppConfig config;
    private int currentPort;
    private Handler watchdogHandler;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        config = AppConfig.getInstance(this);
        watchdogHandler = new Handler(Looper.getMainLooper());

        // Setup global crash handler
        setupCrashHandler();

        // Init hardware client
        com.phicomm.r1manager.server.client.HardwareClient.init();

        AppLog.i(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLog.i(TAG, "Service started");

        // Cancel any pending restart alarms
        cancelRestartAlarm();

        // Get saved port
        currentPort = config.getPort();

        // Check for new port from intent
        if (intent != null && intent.hasExtra("new_port")) {
            int newPort = intent.getIntExtra("new_port", currentPort);
            if (newPort != currentPort) {
                currentPort = newPort;
                config.setPort(newPort);
                stopWebServer();
            }
        }

        startForegroundService();
        startWebServer();
        startWatchdog();

        isRunning = true;

        // START_STICKY: system will restart service if killed
        return START_STICKY;
    }

    /**
     * Setup global uncaught exception handler
     */
    private void setupCrashHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                AppLog.e(TAG, "Uncaught exception, scheduling restart", throwable);
                scheduleRestart();

                // Call default handler
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }
        });
    }

    /**
     * Schedule service restart using AlarmManager
     */
    private void scheduleRestart() {
        AppLog.i(TAG, "Scheduling restart in " + RESTART_DELAY_MS + "ms");

        Intent restartIntent = new Intent(this, WebServerService.class);
        restartIntent.setAction("RESTART");

        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, restartIntent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
                    pendingIntent);
        }
    }

    /**
     * Cancel any pending restart alarms
     */
    private void cancelRestartAlarm() {
        Intent restartIntent = new Intent(this, WebServerService.class);
        restartIntent.setAction("RESTART");

        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, restartIntent, PendingIntent.FLAG_NO_CREATE);

        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }
            pendingIntent.cancel();
        }
    }

    /**
     * Watchdog thread to monitor server health
     */
    private Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                // Check if server is alive
                if (webServer == null || !webServer.isAlive()) {
                    AppLog.w(TAG, "Watchdog: Server not alive, restarting...");
                    startWebServer();
                } else {
                    AppLog.d(TAG, "Watchdog: Server healthy on port " + currentPort);
                }

                // Schedule next check
                watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS);
            }
        }
    };

    private void startWatchdog() {
        watchdogHandler.removeCallbacks(watchdogRunnable);
        watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS);
        AppLog.i(TAG, "Watchdog started, interval: " + WATCHDOG_INTERVAL_MS + "ms");
    }

    private void stopWatchdog() {
        watchdogHandler.removeCallbacks(watchdogRunnable);
        AppLog.i(TAG, "Watchdog stopped");
    }

    @SuppressWarnings("deprecation")
    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("R1 Manager")
                .setContentText("Web server running on port " + currentPort)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void startWebServer() {
        if (webServer != null && webServer.isAlive()) {
            AppLog.d(TAG, "Web server already running");
            return;
        }

        try {
            webServer = new WebServer(currentPort, this);
            webServer.start();
            AppLog.i(TAG, "Web server started on port " + currentPort);
        } catch (IOException e) {
            AppLog.e(TAG, "Failed to start web server", e);
            // Retry after delay
            watchdogHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isRunning)
                        startWebServer();
                }
            }, RESTART_DELAY_MS);
        }
    }

    private void stopWebServer() {
        if (webServer != null) {
            webServer.stop();
            webServer = null;
            AppLog.i(TAG, "Web server stopped");
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // App swiped from recents, schedule restart
        AppLog.w(TAG, "Task removed, scheduling restart");
        scheduleRestart();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        stopWatchdog();
        stopWebServer();

        // Schedule restart if destroyed unexpectedly
        scheduleRestart();

        AppLog.i(TAG, "Service destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Static helpers
    public static int getSavedPort(Context context) {
        return AppConfig.getInstance(context).getPort();
    }

    public static void savePort(Context context, int port) {
        AppConfig.getInstance(context).setPort(port);
    }

    /**
     * Ensure service is running
     */
    public static void ensureRunning(Context context) {
        Intent intent = new Intent(context, WebServerService.class);
        context.startService(intent);
    }
}
