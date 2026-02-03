package com.phicomm.r1manager.mcp.tools;

import android.content.Context;
import com.phicomm.r1manager.mcp.model.McpToolParameter;
import com.phicomm.r1manager.mcp.model.McpToolResponse;
import com.phicomm.r1manager.server.manager.MemoryManager;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * MemoryTool - Bộ nhớ AI toàn diện
 * Actions: read, save, note, clear
 */
public class MemoryTool extends BaseMcpTool {

    public MemoryTool(Context context) {
        super(context, "memory",
                "Bộ nhớ lưu thông tin người dùng và ngữ cảnh. " +
                "LUÔN gọi action=read ĐẦU TIÊN mỗi cuộc hội thoại để biết thông tin user. " +
                "Gọi action=save khi user chia sẻ thông tin cá nhân. " +
                "Gọi action=note để ghi nhớ điều quan trọng trong cuộc trò chuyện.");
    }

    @Override
    public String getDescription() {
        MemoryManager manager = MemoryManager.getInstance(context);
        String summary = manager.getSummary();

        return "Bộ nhớ AI - Lưu trữ thông tin và ngữ cảnh.\n" +
                "TRẠNG THÁI: " + summary + "\n\n" +
                "ACTIONS:\n" +
                "1. read - Đọc TẤT CẢ (LUÔN gọi đầu tiên!)\n" +
                "2. save - Lưu thông tin user (key + value)\n" +
                "3. note - Ghi chú quan trọng (chỉ cần value)\n" +
                "4. clear - Xóa toàn bộ\n\n" +
                "USER PROFILE KEYS:\n" +
                "user_name, user_nickname, user_age, user_birthday,\n" +
                "user_gender, user_job, user_location, user_hobbies,\n" +
                "user_music_taste, user_family, user_pets, user_goals\n\n" +
                "CONTEXT KEYS:\n" +
                "last_topic (chủ đề đang nói), user_mood (tâm trạng)";
    }

    @Override
    public List<McpToolParameter> getParameters() {
        return Arrays.asList(
                McpToolParameter.builder("action", "string")
                        .description(
                                "Hành động:\n" +
                                "- read: Đọc tất cả thông tin và ghi chú\n" +
                                "- save: Lưu thông tin (cần key + value)\n" +
                                "- note: Thêm ghi chú quan trọng (chỉ cần value)\n" +
                                "- clear: Xóa toàn bộ")
                        .required(true)
                        .build(),

                McpToolParameter.builder("key", "string")
                        .description(
                                "Key khi action=save. Các key có sẵn:\n" +
                                "- user_name: Tên\n" +
                                "- user_nickname: Biệt danh\n" +
                                "- user_age: Tuổi\n" +
                                "- user_birthday: Sinh nhật\n" +
                                "- user_gender: Giới tính\n" +
                                "- user_job: Nghề nghiệp\n" +
                                "- user_location: Địa điểm\n" +
                                "- user_hobbies: Sở thích\n" +
                                "- user_music_taste: Gu nhạc\n" +
                                "- user_family: Gia đình\n" +
                                "- user_pets: Thú cưng\n" +
                                "- user_goals: Mục tiêu\n" +
                                "- last_topic: Chủ đề đang nói\n" +
                                "- user_mood: Tâm trạng hiện tại\n" +
                                "- Hoặc tự tạo key mới")
                        .required(false)
                        .build(),

                McpToolParameter.builder("value", "string")
                        .description(
                                "Giá trị cần lưu.\n" +
                                "- Với action=save: giá trị cho key\n" +
                                "- Với action=note: nội dung ghi chú")
                        .required(false)
                        .build());
    }

    @Override
    protected McpToolResponse executeInternal(Map<String, Object> params) {
        String action = getStringParam(params, "action", "").toLowerCase();
        MemoryManager manager = MemoryManager.getInstance(context);

        try {
            switch (action) {
                case "read":
                    return handleRead(manager);

                case "save":
                    return handleSave(params, manager);

                case "note":
                    return handleNote(params, manager);

                case "clear":
                    return handleClear(manager);

                default:
                    return McpToolResponse.error(
                            "Action không hợp lệ: '" + action + "'. Dùng: read, save, note, clear");
            }
        } catch (Exception e) {
            return McpToolResponse.error("Lỗi bộ nhớ: " + e.getMessage());
        }
    }

    private McpToolResponse handleRead(MemoryManager manager) {
        String allData = manager.getAll();
        return McpToolResponse.success(allData);
    }

    private McpToolResponse handleSave(Map<String, Object> params, MemoryManager manager) {
        String key = getStringParam(params, "key", "");
        String value = getStringParam(params, "value", "");

        if (key.isEmpty()) {
            return McpToolResponse.error("Thiếu 'key'. VD: key=user_name");
        }
        if (value.isEmpty()) {
            return McpToolResponse.error("Thiếu 'value'. VD: value=Minh");
        }

        boolean success = manager.set(key, value);
        if (success) {
            return McpToolResponse.success("Đã lưu: " + key + " = " + value);
        }
        return McpToolResponse.error("Không thể lưu.");
    }

    private McpToolResponse handleNote(Map<String, Object> params, MemoryManager manager) {
        String value = getStringParam(params, "value", "");

        if (value.isEmpty()) {
            return McpToolResponse.error("Thiếu 'value' cho ghi chú.");
        }

        boolean success = manager.addContextNote(value);
        if (success) {
            return McpToolResponse.success("Đã ghi chú: " + value);
        }
        return McpToolResponse.error("Không thể ghi chú.");
    }

    private McpToolResponse handleClear(MemoryManager manager) {
        manager.clearAll();
        return McpToolResponse.success("Đã xóa toàn bộ bộ nhớ.");
    }
}
