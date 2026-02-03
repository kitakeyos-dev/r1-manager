package com.phicomm.r1manager.server.controller;

import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.server.annotation.GetMapping;
import com.phicomm.r1manager.server.annotation.PostMapping;
import com.phicomm.r1manager.server.annotation.RequestBody;
import com.phicomm.r1manager.server.annotation.RequestMapping;
import com.phicomm.r1manager.server.annotation.RequestParam;
import com.phicomm.r1manager.server.annotation.RestController;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.manager.ZingMp3Manager;
import com.phicomm.r1manager.server.client.ZingMp3Client;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import fi.iki.elonen.NanoHTTPD;

@RestController
@RequestMapping("/api/zing")
public class ZingMp3Controller {

    private static final String TAG = "ZingMp3Controller";
    private final ZingMp3Manager zingManager;
    private final ZingMp3Client zingClient; // Keep for proxy connection

    public ZingMp3Controller() {
        this.zingManager = ZingMp3Manager.getInstance();
        this.zingClient = ZingMp3Client.getInstance();
    }

    @GetMapping("/search")
    public ApiResponse<Object> search(@RequestParam("q") String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return ApiResponse.error("Keyword required");
        }
        try {
            JSONObject result = zingManager.search(keyword);
            return ApiResponse.success(result.toString());
        } catch (Exception e) {
            AppLog.e(TAG, "Search failed", e);
            return ApiResponse.error("Search failed: " + e.getMessage());
        }
    }

    @PostMapping("/play")
    public ApiResponse<String> play(@RequestBody PlayRequest req) {
        if (req.id == null || req.id.isEmpty()) {
            return ApiResponse.error("Song ID required");
        }
        try {
            String message = zingManager.playSong(req.id, req.title, req.artist, req.thumbnail, req.mode);
            return ApiResponse.successMessage(message);
        } catch (Exception e) {
            AppLog.e(TAG, "Play error", e);
            return ApiResponse.error("Play failed: " + e.getMessage());
        }
    }

    @GetMapping("/proxy")
    public NanoHTTPD.Response streamProxy(@RequestParam("url") String targetUrl) {
        if (targetUrl == null || targetUrl.isEmpty()) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain",
                    "URL required");
        }

        try {
            AppLog.d(TAG, "Proxying stream: " + targetUrl);
            String currentUrl = targetUrl;
            HttpURLConnection conn = null;
            int responseCode = 0;

            for (int i = 0; i < 5; i++) {
                conn = zingClient.getStreamConnection(currentUrl);
                conn.setRequestMethod("GET");
                String cookies = zingClient.getCookies();
                if (cookies != null && !cookies.isEmpty()) {
                    conn.setRequestProperty("Cookie", cookies);
                }
                conn.connect();
                responseCode = conn.getResponseCode();

                if (responseCode == 301 || responseCode == 302 || responseCode == 307) {
                    String cleanRedirect = conn.getHeaderField("Location");
                    if (cleanRedirect != null) {
                        currentUrl = cleanRedirect;
                        conn.disconnect();
                        continue;
                    }
                }
                break;
            }

            if (responseCode >= 400) {
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain",
                        "Upstream error: " + responseCode);
            }

            InputStream inputStream = conn.getInputStream();
            String mimeType = conn.getContentType();
            if (mimeType == null)
                mimeType = "audio/mpeg";

            return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, mimeType, inputStream);

        } catch (Exception e) {
            AppLog.e(TAG, "Proxy error", e);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain",
                    "Proxy error: " + e.getMessage());
        }
    }

    public static class PlayRequest {
        public String id;
        public String mode;
        public String title;
        public String artist;
        public String thumbnail;
    }
}
