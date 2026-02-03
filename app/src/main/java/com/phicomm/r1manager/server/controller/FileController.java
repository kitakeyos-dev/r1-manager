package com.phicomm.r1manager.server.controller;

import android.content.Context;
import java.io.File;
import com.phicomm.r1manager.server.annotation.*;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.manager.FileManager;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileManager fileManager;

    public FileController(Context context) {
        this.fileManager = new FileManager(context);
    }

    @GetMapping()
    public ApiResponse<Object> listFiles(@RequestParam(value = "path") String queryPath) {
        try {
            String dirPath = queryPath != null ? queryPath : fileManager.getDefaultPath();
            return ApiResponse.success(fileManager.listDirectory(dirPath));
        } catch (Exception e) {
            return ApiResponse.error("Failed to list: " + e.getMessage());
        }
    }

    @GetMapping("/info")
    public ApiResponse<Object> getFileInfo(@RequestParam(value = "path") String queryPath) {
        if (queryPath == null)
            return ApiResponse.error("path required");
        try {
            return ApiResponse.success(fileManager.getFileInfo(queryPath));
        } catch (Exception e) {
            return ApiResponse.error("Failed to get info: " + e.getMessage());
        }
    }

    @GetMapping("/download")
    public Response downloadFile(@RequestParam(value = "path") String queryPath) {
        if (queryPath == null)
            return NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "path required");
        try {
            FileInputStream fis = fileManager.readFile(queryPath);
            String filename = queryPath.substring(queryPath.lastIndexOf('/') + 1);
            String mimeType = getMimeType(filename);
            Response response = NanoHTTPD.newChunkedResponse(Response.Status.OK, mimeType, fis);
            response.addHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            return response;
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> uploadFile(IHTTPSession session,
            @RequestParam(value = "path") String queryPath) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String tempFilePath = files.get("file");
            String filename = session.getParms().get("filename");

            String fullPath = fileManager.saveUploadedFile(tempFilePath, queryPath, filename);

            Map<String, Object> result = new HashMap<>();
            result.put("path", fullPath);
            result.put("size", new File(fullPath).length());
            return ApiResponse.success("File uploaded", result);
        } catch (Exception e) {
            return ApiResponse.error("Upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/mkdir")
    public ApiResponse<String> mkdir(@RequestBody PathRequest req) {
        if (req.path == null || req.path.isEmpty())
            return ApiResponse.error("path required");
        try {
            if (fileManager.createDirectory(req.path)) {
                return ApiResponse.successMessage("Directory created: " + req.path);
            }
            return ApiResponse.error("Failed to create directory");
        } catch (Exception e) {
            return ApiResponse.error("Error: " + e.getMessage());
        }
    }

    @PostMapping("/rename")
    public ApiResponse<String> rename(@RequestBody RenameRequest req) {
        if (req.path == null || req.path.isEmpty())
            return ApiResponse.error("path required");
        if (req.newName == null || req.newName.isEmpty())
            return ApiResponse.error("newName required");
        try {
            if (fileManager.rename(req.path, req.newName)) {
                return ApiResponse.successMessage("Renamed successfully");
            }
            return ApiResponse.error("Failed to rename");
        } catch (Exception e) {
            return ApiResponse.error("Error: " + e.getMessage());
        }
    }

    @DeleteMapping()
    public ApiResponse<String> delete(@RequestParam(value = "path") String queryPath) {
        if (queryPath == null)
            return ApiResponse.error("path required");
        try {
            if (fileManager.delete(queryPath)) {
                return ApiResponse.successMessage("Deleted: " + queryPath);
            }
            return ApiResponse.error("Failed to delete");
        } catch (Exception e) {
            return ApiResponse.error("Error: " + e.getMessage());
        }
    }

    private String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".txt"))
            return "text/plain";
        if (lower.endsWith(".html") || lower.endsWith(".htm"))
            return "text/html";
        if (lower.endsWith(".css"))
            return "text/css";
        if (lower.endsWith(".js"))
            return "application/javascript";
        if (lower.endsWith(".json"))
            return "application/json";
        if (lower.endsWith(".xml"))
            return "application/xml";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".png"))
            return "image/png";
        if (lower.endsWith(".gif"))
            return "image/gif";
        if (lower.endsWith(".svg"))
            return "image/svg+xml";
        if (lower.endsWith(".mp3"))
            return "audio/mpeg";
        if (lower.endsWith(".mp4"))
            return "video/mp4";
        if (lower.endsWith(".pdf"))
            return "application/pdf";
        if (lower.endsWith(".zip"))
            return "application/zip";
        if (lower.endsWith(".apk"))
            return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }

    public static class PathRequest {
        public String path;
    }

    public static class RenameRequest {
        public String path;
        public String newName;
    }
}
