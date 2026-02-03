package com.phicomm.r1manager.server.controller;

import android.content.Context;
import com.phicomm.r1manager.server.annotation.*;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.manager.AppManager;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/apps")
public class AppsController {

    private final AppManager appManager;

    public AppsController(Context context) {
        this.appManager = new AppManager(context);
    }

    @GetMapping()
    public ApiResponse<Object> listApps() {
        try {
            return ApiResponse.success(appManager.getApps());
        } catch (Exception e) {
            return ApiResponse.error("Failed to list apps: " + e.getMessage());
        }
    }

    @GetMapping("/export")
    public Response exportApk(@RequestParam("package") String pkg) throws Exception {
        if (pkg == null || pkg.isEmpty()) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Package required");
        }

        File apkFile = appManager.getApkFile(pkg);
        if (apkFile == null || !apkFile.exists()) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "APK not found");
        }

        FileInputStream fis = new FileInputStream(apkFile);
        Response response = NanoHTTPD.newChunkedResponse(Response.Status.OK, "application/vnd.android.package-archive",
                fis);
        response.addHeader("Content-Disposition", "attachment; filename=\"" + pkg + ".apk\"");
        return response;
    }

    @PostMapping("/launch")
    public ApiResponse<String> launchApp(@RequestBody PackageRequest req) {
        try {
            if (appManager.launchApp(req.pkg))
                return ApiResponse.successMessage("App launched");
            return ApiResponse.error("Failed to launch app");
        } catch (Exception e) {
            return ApiResponse.error("Error: " + e.getMessage());
        }
    }

    @PostMapping("/stop")
    public ApiResponse<String> stopApp(@RequestBody PackageRequest req) {
        try {
            if (appManager.stopApp(req.pkg))
                return ApiResponse.successMessage("App stopped");
            return ApiResponse.error("Failed to stop app");
        } catch (Exception e) {
            return ApiResponse.error("Error: " + e.getMessage());
        }
    }

    @PostMapping("/uninstall")
    public ApiResponse<String> uninstallApp(@RequestBody PackageRequest req) {
        try {
            if (appManager.uninstallApp(req.pkg))
                return ApiResponse.successMessage("App uninstalled");
            return ApiResponse.error("Failed to uninstall app");
        } catch (Exception e) {
            return ApiResponse.error("Error: " + e.getMessage());
        }
    }

    @PostMapping("/install")
    public ApiResponse<String> installApp(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String tempPath = null;
            if (files.containsKey("file"))
                tempPath = files.get("file");
            else {
                for (String key : files.keySet()) {
                    if (!"postData".equals(key)) {
                        tempPath = files.get(key);
                        break;
                    }
                }
            }

            if (tempPath != null && appManager.installApp(tempPath)) {
                return ApiResponse.successMessage("App installed");
            }
            return ApiResponse.error("Failed to install app");
        } catch (Exception e) {
            return ApiResponse.error("Upload failed: " + e.getMessage());
        }
    }

    public static class PackageRequest {
        @com.google.gson.annotations.SerializedName("package")
        public String pkg;
    }
}
