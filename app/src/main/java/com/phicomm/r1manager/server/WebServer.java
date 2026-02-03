package com.phicomm.r1manager.server;

import android.content.Context;
import android.content.res.AssetManager;
import com.phicomm.r1manager.util.AppLog;

import com.phicomm.r1manager.server.controller.AppsController;
import com.phicomm.r1manager.server.controller.FileController;
import com.phicomm.r1manager.server.controller.MusicLedController;
import com.phicomm.r1manager.server.controller.SettingsController;
import com.phicomm.r1manager.server.controller.SystemController;
import com.phicomm.r1manager.server.controller.VolumeController;
import com.phicomm.r1manager.server.controller.WifiController;
import com.phicomm.r1manager.server.controller.HardwareController;
import com.phicomm.r1manager.server.controller.ZingMp3Controller;
import com.phicomm.r1manager.server.controller.PlaylistController;
import com.phicomm.r1manager.server.controller.WolController;
import com.phicomm.r1manager.server.controller.XiaozhiController;
import com.phicomm.r1manager.server.controller.LogController;
import com.phicomm.r1manager.server.controller.MemoryController;
import com.phicomm.r1manager.server.router.Router;
import com.phicomm.r1manager.server.manager.MusicServiceManager;

import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

/**
 * WebServer - NanoHTTPD server with clean routing
 * Delegates API requests to controllers
 */
public class WebServer extends NanoHTTPD {

    private static final String TAG = "WebServer";
    private static final String MIME_JSON = "application/json";
    private static final String MIME_HTML = "text/html";
    private static final String MIME_CSS = "text/css";
    private static final String MIME_JS = "application/javascript";

    private Context context;
    private AssetManager assetManager;

    private final Router router;

    public WebServer(int port, Context context) {
        super(port);
        this.context = context;
        this.assetManager = context.getAssets();

        // Initialize music services
        MusicServiceManager musicManager = MusicServiceManager.getInstance();
        musicManager.initialize(context);

        // Initialize and register controllers with Router
        this.router = new Router();
        router.registerController(new AppsController(context));
        router.registerController(new SystemController(context));
        router.registerController(new VolumeController(context));
        router.registerController(new WifiController(context));
        router.registerController(new SettingsController(context, port));
        router.registerController(new FileController(context));
        router.registerController(new HardwareController(context));

        // Register music-related controllers
        router.registerController(new MusicLedController(context));
        router.registerController(new ZingMp3Controller());
        router.registerController(new PlaylistController());
        router.registerController(new WolController(context));
        router.registerController(new XiaozhiController(context));
        router.registerController(new LogController(context));
        router.registerController(new MemoryController(context));

        AppLog.i(TAG, "WebServer initialized with music features");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        // AppLog.d(TAG, method + " " + uri);

        try {
            // Handle CORS preflight
            if (Method.OPTIONS.equals(method)) {
                Response response = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
                addCorsHeaders(response);
                return response;
            }

            // Static files
            if (!uri.startsWith("/api/")) {
                return serveStaticFile(uri);
            }

            // Route to controllers via Router
            Response response = router.handle(session);

            if (response != null) {
                addCorsHeaders(response);
                return response;
            }

            // 404 Not Found
            return jsonError(Response.Status.NOT_FOUND, "Not found: " + uri);

        } catch (Exception e) {
            AppLog.e(TAG, "Error handling request: " + uri, e);
            return jsonError(Response.Status.INTERNAL_ERROR, e.getMessage());
        }
    }

    // ==================== Static Files ====================

    private Response serveStaticFile(String uri) {
        if (uri.equals("/") || uri.isEmpty()) {
            uri = "/index.html";
        }

        String assetPath = "web" + uri;

        try {
            InputStream is = assetManager.open(assetPath);
            String mimeType = getMimeType(uri);
            return newChunkedResponse(Response.Status.OK, mimeType, is);
        } catch (IOException e) {
            // Try index.html for SPA
            try {
                InputStream is = assetManager.open("web/index.html");
                return newChunkedResponse(Response.Status.OK, MIME_HTML, is);
            } catch (IOException e2) {
                return jsonError(Response.Status.NOT_FOUND, "File not found: " + uri);
            }
        }
    }

    private String getMimeType(String uri) {
        if (uri.endsWith(".html"))
            return MIME_HTML;
        if (uri.endsWith(".css"))
            return MIME_CSS;
        if (uri.endsWith(".js"))
            return MIME_JS;
        if (uri.endsWith(".json"))
            return MIME_JSON;
        if (uri.endsWith(".png"))
            return "image/png";
        if (uri.endsWith(".jpg") || uri.endsWith(".jpeg"))
            return "image/jpeg";
        if (uri.endsWith(".svg"))
            return "image/svg+xml";
        if (uri.endsWith(".ico"))
            return "image/x-icon";
        if (uri.endsWith(".woff2"))
            return "font/woff2";
        if (uri.endsWith(".woff"))
            return "font/woff";
        return "application/octet-stream";
    }

    // ==================== Helpers ====================

    private Response jsonError(Response.Status status, String message) {
        String json = "{\"status\":\"error\",\"message\":\"" +
                (message != null ? message.replace("\"", "'") : "Unknown error") + "\"}";
        Response response = newFixedLengthResponse(status, MIME_JSON, json);
        addCorsHeaders(response);
        return response;
    }

    private void addCorsHeaders(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
    }
}
