package com.phicomm.r1manager.util;

import android.util.Log;

/**
 * Application-wide logging utility that mirrors android.util.Log
 * but also stores logs in LogBuffer for Web UI display
 */
public class AppLog {

    // Verbose (lowest priority)
    public static int v(String tag, String msg) {
        LogBuffer.getInstance().log("VERBOSE", tag, msg);
        return Log.v(tag, msg);
    }

    public static int v(String tag, String msg, Throwable tr) {
        LogBuffer.getInstance().log("VERBOSE", tag, msg + "\n" + Log.getStackTraceString(tr));
        return Log.v(tag, msg, tr);
    }

    // Debug
    public static int d(String tag, String msg) {
        LogBuffer.getInstance().log("DEBUG", tag, msg);
        return Log.d(tag, msg);
    }

    public static int d(String tag, String msg, Throwable tr) {
        LogBuffer.getInstance().log("DEBUG", tag, msg + "\n" + Log.getStackTraceString(tr));
        return Log.d(tag, msg, tr);
    }

    // Info
    public static int i(String tag, String msg) {
        LogBuffer.getInstance().log("INFO", tag, msg);
        return Log.i(tag, msg);
    }

    public static int i(String tag, String msg, Throwable tr) {
        LogBuffer.getInstance().log("INFO", tag, msg + "\n" + Log.getStackTraceString(tr));
        return Log.i(tag, msg, tr);
    }

    // Warning
    public static int w(String tag, String msg) {
        LogBuffer.getInstance().log("WARN", tag, msg);
        return Log.w(tag, msg);
    }

    public static int w(String tag, String msg, Throwable tr) {
        LogBuffer.getInstance().log("WARN", tag, msg + "\n" + Log.getStackTraceString(tr));
        return Log.w(tag, msg, tr);
    }

    public static int w(String tag, Throwable tr) {
        LogBuffer.getInstance().log("WARN", tag, Log.getStackTraceString(tr));
        return Log.w(tag, tr);
    }

    // Error (highest priority)
    public static int e(String tag, String msg) {
        LogBuffer.getInstance().log("ERROR", tag, msg);
        return Log.e(tag, msg);
    }

    public static int e(String tag, String msg, Throwable tr) {
        LogBuffer.getInstance().log("ERROR", tag, msg + "\n" + Log.getStackTraceString(tr));
        return Log.e(tag, msg, tr);
    }
}
