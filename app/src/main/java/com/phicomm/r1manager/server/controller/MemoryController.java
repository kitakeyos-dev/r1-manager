package com.phicomm.r1manager.server.controller;

import android.content.Context;
import com.phicomm.r1manager.server.annotation.*;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.manager.MemoryManager;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final Context context;
    private final MemoryManager memoryManager;

    public MemoryController(Context context) {
        this.context = context;
        this.memoryManager = MemoryManager.getInstance(context);
    }

    /**
     * GET /api/memory - Lấy tất cả thông tin đã lưu
     */
    @GetMapping()
    public ApiResponse<Map<String, Object>> getAllMemories() {
        try {
            String allData = memoryManager.getAll();
            List<String> keys = memoryManager.getAllKeys();
            List<String> notes = memoryManager.getContextNotes();

            Map<String, Object> data = new HashMap<>();
            data.put("formatted", allData);
            data.put("keys", keys);
            data.put("notes", notes);
            data.put("summary", memoryManager.getSummary());
            data.put("hasData", memoryManager.hasData());

            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.error("Failed to fetch memories: " + e.getMessage());
        }
    }

    /**
     * GET /api/memory/get?key=xxx - Lấy giá trị một key
     */
    @GetMapping("/get")
    public ApiResponse<Object> getMemoryByKey(@RequestParam("key") String key) {
        try {
            String value = memoryManager.get(key);

            if (value == null) {
                return ApiResponse.error("Key not found: " + key);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("key", key);
            data.put("value", value);

            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.error("Failed to fetch memory: " + e.getMessage());
        }
    }

    /**
     * POST /api/memory/set - Lưu key-value
     * Body: { "key": "...", "value": "..." }
     */
    @PostMapping("/set")
    public ApiResponse<Object> setMemory(@RequestBody String body) {
        try {
            JSONObject json = new JSONObject(body);

            String key = json.optString("key", "");
            String value = json.optString("value", "");

            if (key.isEmpty()) {
                return ApiResponse.error("Key is required");
            }
            if (value.isEmpty()) {
                return ApiResponse.error("Value is required");
            }

            boolean success = memoryManager.set(key, value);

            if (success) {
                Map<String, Object> data = new HashMap<>();
                data.put("key", key);
                data.put("value", value);
                data.put("message", "Saved successfully");
                return ApiResponse.success(data);
            } else {
                return ApiResponse.error("Failed to save memory");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to save memory: " + e.getMessage());
        }
    }

    /**
     * POST /api/memory/note - Thêm ghi chú context
     * Body: { "note": "..." }
     */
    @PostMapping("/note")
    public ApiResponse<Object> addNote(@RequestBody String body) {
        try {
            JSONObject json = new JSONObject(body);
            String note = json.optString("note", "");

            if (note.isEmpty()) {
                return ApiResponse.error("Note is required");
            }

            boolean success = memoryManager.addContextNote(note);

            if (success) {
                Map<String, Object> data = new HashMap<>();
                data.put("note", note);
                data.put("message", "Note added successfully");
                return ApiResponse.success(data);
            } else {
                return ApiResponse.error("Failed to add note");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to add note: " + e.getMessage());
        }
    }

    /**
     * POST /api/memory/delete?key=xxx - Xóa một key
     */
    @PostMapping("/delete")
    public ApiResponse<String> deleteMemory(@RequestParam("key") String key) {
        try {
            boolean success = memoryManager.remove(key);

            if (success) {
                return ApiResponse.successMessage("Key deleted: " + key);
            } else {
                return ApiResponse.error("Key not found: " + key);
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to delete memory: " + e.getMessage());
        }
    }

    /**
     * POST /api/memory/clear - Xóa tất cả
     */
    @PostMapping("/clear")
    public ApiResponse<String> clearAllMemories() {
        try {
            memoryManager.clearAll();
            return ApiResponse.successMessage("All memories cleared");
        } catch (Exception e) {
            return ApiResponse.error("Failed to clear memories: " + e.getMessage());
        }
    }

    /**
     * POST /api/memory/clear-notes - Xóa chỉ context notes
     */
    @PostMapping("/clear-notes")
    public ApiResponse<String> clearContextNotes() {
        try {
            memoryManager.clearContextNotes();
            return ApiResponse.successMessage("Context notes cleared");
        } catch (Exception e) {
            return ApiResponse.error("Failed to clear notes: " + e.getMessage());
        }
    }

    /**
     * GET /api/memory/keys - Liệt kê tất cả keys
     */
    @GetMapping("/keys")
    public ApiResponse<List<String>> getAllKeys() {
        try {
            List<String> keys = memoryManager.getAllKeys();
            return ApiResponse.success(keys);
        } catch (Exception e) {
            return ApiResponse.error("Failed to get keys: " + e.getMessage());
        }
    }

    /**
     * GET /api/memory/notes - Lấy tất cả context notes
     */
    @GetMapping("/notes")
    public ApiResponse<List<String>> getContextNotes() {
        try {
            List<String> notes = memoryManager.getContextNotes();
            return ApiResponse.success(notes);
        } catch (Exception e) {
            return ApiResponse.error("Failed to get notes: " + e.getMessage());
        }
    }

    /**
     * GET /api/memory/summary - Lấy tóm tắt
     */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> getSummary() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("summary", memoryManager.getSummary());
            data.put("hasData", memoryManager.hasData());
            data.put("keyCount", memoryManager.getAllKeys().size());
            data.put("noteCount", memoryManager.getContextNotes().size());

            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.error("Failed to get summary: " + e.getMessage());
        }
    }
}
