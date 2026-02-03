package com.phicomm.r1manager.server.manager;

import android.content.Context;
import com.phicomm.r1manager.util.AppLog;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * MemoryManager - Quản lý bộ nhớ AI toàn diện
 * Lưu trữ: thông tin user + context + facts + notes
 */
public class MemoryManager {
    private static final String TAG = "MemoryManager";
    private static final String FILE_NAME = "ai_memory.json";
    private static final int MAX_CONTEXT_ITEMS = 20; // Giới hạn context để không quá dài

    private static MemoryManager instance;
    private final File memoryFile;
    private final SimpleDateFormat dateFormat;

    // ===== USER PROFILE KEYS =====
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_USER_NICKNAME = "user_nickname";
    public static final String KEY_USER_AGE = "user_age";
    public static final String KEY_USER_BIRTHDAY = "user_birthday";
    public static final String KEY_USER_GENDER = "user_gender";
    public static final String KEY_USER_JOB = "user_job";
    public static final String KEY_USER_LOCATION = "user_location";
    public static final String KEY_USER_HOBBIES = "user_hobbies";
    public static final String KEY_USER_MUSIC_TASTE = "user_music_taste";
    public static final String KEY_USER_FAMILY = "user_family";
    public static final String KEY_USER_PETS = "user_pets";
    public static final String KEY_USER_GOALS = "user_goals";

    // ===== CONTEXT KEYS =====
    public static final String KEY_LAST_TOPIC = "last_topic";
    public static final String KEY_MOOD = "user_mood";
    public static final String KEY_CONTEXT_NOTES = "context_notes"; // JSON Array

    private MemoryManager(Context context) {
        this.memoryFile = new File(context.getFilesDir(), FILE_NAME);
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    }

    public static synchronized MemoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new MemoryManager(context.getApplicationContext());
        }
        return instance;
    }

    // ==================== BASIC KEY-VALUE ====================

    /**
     * Lưu hoặc cập nhật một key
     */
    public synchronized boolean set(String key, String value) {
        try {
            JSONObject data = loadAll();
            data.put(key, value);
            data.put("_last_updated", System.currentTimeMillis());
            saveAll(data);
            AppLog.d(TAG, "Memory set: " + key);
            return true;
        } catch (Exception e) {
            AppLog.e(TAG, "Set memory failed", e);
            return false;
        }
    }

    /**
     * Lấy giá trị của một key
     */
    public synchronized String get(String key) {
        try {
            JSONObject data = loadAll();
            return data.optString(key, null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Xóa một key
     */
    public synchronized boolean remove(String key) {
        try {
            JSONObject data = loadAll();
            if (data.has(key)) {
                data.remove(key);
                saveAll(data);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== CONTEXT NOTES (Array-based) ====================

    /**
     * Thêm một ghi chú context mới (tự động giới hạn số lượng)
     */
    public synchronized boolean addContextNote(String note) {
        try {
            JSONObject data = loadAll();
            JSONArray notes = data.optJSONArray(KEY_CONTEXT_NOTES);
            if (notes == null) {
                notes = new JSONArray();
            }

            // Tạo entry mới với timestamp
            JSONObject entry = new JSONObject();
            entry.put("note", note);
            entry.put("time", dateFormat.format(new Date()));

            notes.put(entry);

            // Giới hạn số lượng
            while (notes.length() > MAX_CONTEXT_ITEMS) {
                notes.remove(0);
            }

            data.put(KEY_CONTEXT_NOTES, notes);
            saveAll(data);
            return true;
        } catch (Exception e) {
            AppLog.e(TAG, "Add context note failed", e);
            return false;
        }
    }

    /**
     * Lấy tất cả context notes
     */
    public synchronized List<String> getContextNotes() {
        List<String> result = new ArrayList<>();
        try {
            JSONObject data = loadAll();
            JSONArray notes = data.optJSONArray(KEY_CONTEXT_NOTES);
            if (notes != null) {
                for (int i = 0; i < notes.length(); i++) {
                    JSONObject entry = notes.optJSONObject(i);
                    if (entry != null) {
                        String note = entry.optString("note", "");
                        String time = entry.optString("time", "");
                        result.add("[" + time + "] " + note);
                    }
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Get context notes failed", e);
        }
        return result;
    }

    // ==================== GET ALL - Format cho AI ====================

    /**
     * Lấy TẤT CẢ thông tin - format đẹp cho AI đọc
     */
    public synchronized String getAll() {
        try {
            JSONObject data = loadAll();
            if (data.length() == 0) {
                return "Chưa có thông tin nào được lưu.";
            }

            StringBuilder sb = new StringBuilder();

            // 1. User Profile
            sb.append("=== THÔNG TIN NGƯỜI DÙNG ===\n");
            boolean hasProfile = false;
            hasProfile |= appendIfExists(sb, data, KEY_USER_NAME, "Tên");
            hasProfile |= appendIfExists(sb, data, KEY_USER_NICKNAME, "Biệt danh");
            hasProfile |= appendIfExists(sb, data, KEY_USER_AGE, "Tuổi");
            hasProfile |= appendIfExists(sb, data, KEY_USER_BIRTHDAY, "Sinh nhật");
            hasProfile |= appendIfExists(sb, data, KEY_USER_GENDER, "Giới tính");
            hasProfile |= appendIfExists(sb, data, KEY_USER_JOB, "Nghề nghiệp");
            hasProfile |= appendIfExists(sb, data, KEY_USER_LOCATION, "Địa điểm");
            hasProfile |= appendIfExists(sb, data, KEY_USER_HOBBIES, "Sở thích");
            hasProfile |= appendIfExists(sb, data, KEY_USER_MUSIC_TASTE, "Gu nhạc");
            hasProfile |= appendIfExists(sb, data, KEY_USER_FAMILY, "Gia đình");
            hasProfile |= appendIfExists(sb, data, KEY_USER_PETS, "Thú cưng");
            hasProfile |= appendIfExists(sb, data, KEY_USER_GOALS, "Mục tiêu");

            if (!hasProfile) {
                sb.append("(Chưa có thông tin)\n");
            }

            // 2. Current Context
            sb.append("\n=== NGỮ CẢNH HIỆN TẠI ===\n");
            boolean hasContext = false;
            hasContext |= appendIfExists(sb, data, KEY_LAST_TOPIC, "Chủ đề gần nhất");
            hasContext |= appendIfExists(sb, data, KEY_MOOD, "Tâm trạng");

            if (!hasContext) {
                sb.append("(Chưa có)\n");
            }

            // 3. Context Notes
            List<String> notes = getContextNotes();
            if (!notes.isEmpty()) {
                sb.append("\n=== GHI CHÚ QUAN TRỌNG ===\n");
                for (String note : notes) {
                    sb.append("• ").append(note).append("\n");
                }
            }

            // 4. Custom keys
            StringBuilder customSb = new StringBuilder();
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!isReservedKey(key) && !key.startsWith("_")) {
                    String value = data.optString(key, "");
                    if (!value.isEmpty() && !value.startsWith("[")) { // Skip JSON arrays
                        customSb.append("• ").append(key).append(": ").append(value).append("\n");
                    }
                }
            }

            if (customSb.length() > 0) {
                sb.append("\n=== THÔNG TIN KHÁC ===\n");
                sb.append(customSb);
            }

            return sb.toString().trim();
        } catch (Exception e) {
            AppLog.e(TAG, "Get all failed", e);
            return "Lỗi đọc bộ nhớ.";
        }
    }

    /**
     * Lấy tóm tắt ngắn (cho tool description)
     */
    public synchronized String getSummary() {
        try {
            JSONObject data = loadAll();
            String name = data.optString(KEY_USER_NAME, "");
            int count = countUserKeys(data);
            int contextCount = getContextNotes().size();

            if (count == 0 && contextCount == 0) {
                return "Trống";
            }

            StringBuilder sb = new StringBuilder();
            if (!name.isEmpty()) {
                sb.append("User: ").append(name);
            }
            if (count > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(count).append(" thông tin");
            }
            if (contextCount > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(contextCount).append(" ghi chú");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Lỗi";
        }
    }

    /**
     * Kiểm tra có data không
     */
    public synchronized boolean hasData() {
        try {
            JSONObject data = loadAll();
            return data.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Xóa tất cả
     */
    public synchronized void clearAll() {
        if (memoryFile.exists()) {
            memoryFile.delete();
        }
        AppLog.d(TAG, "All memories cleared");
    }

    /**
     * Xóa chỉ context notes (giữ lại user profile)
     */
    public synchronized void clearContextNotes() {
        try {
            JSONObject data = loadAll();
            data.remove(KEY_CONTEXT_NOTES);
            data.remove(KEY_LAST_TOPIC);
            data.remove(KEY_MOOD);
            saveAll(data);
        } catch (Exception e) {
            AppLog.e(TAG, "Clear context failed", e);
        }
    }

    /**
     * Lấy tất cả keys đang lưu
     */
    public synchronized List<String> getAllKeys() {
        List<String> keys = new ArrayList<>();
        try {
            JSONObject data = loadAll();
            Iterator<String> iterator = data.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (!key.startsWith("_") && !key.equals(KEY_CONTEXT_NOTES)) {
                    keys.add(key);
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Get all keys failed", e);
        }
        return keys;
    }

    // ==================== PRIVATE HELPERS ====================

    private boolean appendIfExists(StringBuilder sb, JSONObject data, String key, String label) {
        String value = data.optString(key, "");
        if (!value.isEmpty()) {
            sb.append("• ").append(label).append(": ").append(value).append("\n");
            return true;
        }
        return false;
    }

    private boolean isReservedKey(String key) {
        return key.equals(KEY_USER_NAME) || key.equals(KEY_USER_NICKNAME) ||
               key.equals(KEY_USER_AGE) || key.equals(KEY_USER_BIRTHDAY) ||
               key.equals(KEY_USER_GENDER) || key.equals(KEY_USER_JOB) ||
               key.equals(KEY_USER_LOCATION) || key.equals(KEY_USER_HOBBIES) ||
               key.equals(KEY_USER_MUSIC_TASTE) || key.equals(KEY_USER_FAMILY) ||
               key.equals(KEY_USER_PETS) || key.equals(KEY_USER_GOALS) ||
               key.equals(KEY_LAST_TOPIC) || key.equals(KEY_MOOD) ||
               key.equals(KEY_CONTEXT_NOTES);
    }

    private int countUserKeys(JSONObject data) {
        int count = 0;
        String[] userKeys = {
            KEY_USER_NAME, KEY_USER_NICKNAME, KEY_USER_AGE, KEY_USER_BIRTHDAY,
            KEY_USER_GENDER, KEY_USER_JOB, KEY_USER_LOCATION, KEY_USER_HOBBIES,
            KEY_USER_MUSIC_TASTE, KEY_USER_FAMILY, KEY_USER_PETS, KEY_USER_GOALS
        };
        for (String key : userKeys) {
            if (data.has(key) && !data.optString(key, "").isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private JSONObject loadAll() throws Exception {
        if (!memoryFile.exists()) {
            return new JSONObject();
        }
        FileInputStream fis = new FileInputStream(memoryFile);
        byte[] data = new byte[(int) memoryFile.length()];
        fis.read(data);
        fis.close();
        return new JSONObject(new String(data, StandardCharsets.UTF_8));
    }

    private void saveAll(JSONObject data) throws Exception {
        FileOutputStream fos = new FileOutputStream(memoryFile);
        fos.write(data.toString(2).getBytes(StandardCharsets.UTF_8));
        fos.close();
    }
}
