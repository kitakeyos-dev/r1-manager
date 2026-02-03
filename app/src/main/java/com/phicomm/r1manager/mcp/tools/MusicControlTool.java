package com.phicomm.r1manager.mcp.tools;

import android.content.Context;
import com.phicomm.r1manager.mcp.model.McpToolParameter;
import com.phicomm.r1manager.mcp.model.McpToolResponse;
import com.phicomm.r1manager.server.manager.ZingMp3Manager;
import com.phicomm.r1manager.server.service.ExoPlayerService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool for controlling music playback via ZingMp3 and ExoPlayer
 */
public class MusicControlTool extends BaseMcpTool {

    public MusicControlTool(Context context) {
        super(context, "music_playback_control",
                "Điều khiển phát nhạc trên loa Phicomm R1 với nguồn nhạc từ ZingMp3. " +
                "SỬ DỤNG KHI người dùng nói: 'phát nhạc', 'mở bài', 'nghe nhạc', 'bật nhạc [tên bài/ca sĩ]', " +
                "'dừng nhạc', 'tạm dừng', 'tiếp tục', 'bài tiếp', 'bài trước', 'tăng/giảm âm lượng', " +
                "'thêm bài vào playlist', 'xem playlist', 'đang phát gì'. " +
                "QUAN TRỌNG: Khi user nói 'tăng/giảm âm lượng' mà KHÔNG nói số cụ thể, " +
                "PHẢI gọi action=status TRƯỚC để biết âm lượng hiện tại, rồi tính toán mức mới (+/-20). " +
                "Ví dụ: 'Phát nhạc Sơn Tùng' → action=search_play, keyword='Sơn Tùng'. " +
                "'Thêm bài ABC vào playlist' → action=queue, keyword='ABC'. " +
                "'Xem playlist' → action=playlist.");
    }

    @Override
    public List<McpToolParameter> getParameters() {
        return Arrays.asList(
                McpToolParameter.builder("action", "string")
                        .description(
                                "Hành động cần thực hiện. BẮT BUỘC chọn 1 trong: " +
                                "status (lấy trạng thái + âm lượng hiện tại), " +
                                "search_play (tìm và phát bài mới), " +
                                "queue (thêm bài vào playlist), " +
                                "playlist (xem danh sách phát), " +
                                "remove (xóa bài khỏi playlist theo index), " +
                                "clear_playlist (xóa toàn bộ playlist), " +
                                "play (tiếp tục phát), pause (tạm dừng), " +
                                "next (bài tiếp), prev (bài trước), stop (ngừng hẳn), " +
                                "volume (chỉnh âm lượng)")
                        .required(true)
                        .build(),
                McpToolParameter.builder("keyword", "string")
                        .description("Tên bài hát, ca sĩ hoặc thể loại nhạc. DÙNG khi action=search_play hoặc action=queue")
                        .required(false)
                        .build(),
                McpToolParameter.builder("volume", "integer")
                        .description("Mức âm lượng mục tiêu từ 0 đến 100. CHỈ DÙNG khi action=volume")
                        .required(false)
                        .build(),
                McpToolParameter.builder("index", "integer")
                        .description("Vị trí bài hát trong playlist (bắt đầu từ 0). CHỈ DÙNG khi action=remove")
                        .required(false)
                        .build());
    }

    @Override
    protected McpToolResponse executeInternal(Map<String, Object> params) {
        String action = getStringParam(params, "action", "");
        ExoPlayerService playerService = ExoPlayerService.getInstance();
        ZingMp3Manager zingManager = ZingMp3Manager.getInstance();

        try {
            switch (action) {
                case "status":
                    return handleStatus(playerService);

                case "search_play":
                    String keyword = getStringParam(params, "keyword", "");
                    if (keyword.isEmpty()) {
                        return McpToolResponse.error("Thiếu từ khóa tìm kiếm (keyword).");
                    }
                    return handleSearchAndPlay(zingManager, keyword);

                case "queue":
                    String queueKeyword = getStringParam(params, "keyword", "");
                    if (queueKeyword.isEmpty()) {
                        return McpToolResponse.error("Thiếu từ khóa tìm kiếm (keyword).");
                    }
                    return handleQueue(zingManager, queueKeyword);

                case "playlist":
                    return handlePlaylist(playerService);

                case "remove":
                    int removeIndex = getIntParam(params, "index", -1);
                    if (removeIndex < 0) {
                        return McpToolResponse.error("Thiếu index bài hát cần xóa.");
                    }
                    return handleRemove(playerService, removeIndex);

                case "clear_playlist":
                    playerService.clearQueue();
                    return McpToolResponse.success("Đã xóa toàn bộ playlist.");

                case "play":
                case "resume":
                    playerService.resume();
                    return McpToolResponse.success("Đã tiếp tục phát nhạc.");

                case "pause":
                    playerService.pause();
                    return McpToolResponse.success("Đã tạm dừng phát nhạc.");

                case "stop":
                    playerService.stop();
                    return McpToolResponse.success("Đã dừng phát nhạc.");

                case "next":
                    playerService.playNext();
                    return McpToolResponse.success("Đã chuyển sang bài kế tiếp.");

                case "prev":
                    playerService.playPrevious();
                    return McpToolResponse.success("Đã quay lại bài trước.");

                case "volume":
                    int volume = getIntParam(params, "volume", -1);
                    if (volume < 0 || volume > 100) {
                        return McpToolResponse.error("Âm lượng phải từ 0 đến 100.");
                    }
                    playerService.setVolume(volume);
                    return McpToolResponse.success("Đã chỉnh âm lượng lên " + volume + "%.");

                default:
                    return McpToolResponse.error("Hành động không hợp lệ: " + action);
            }
        } catch (Exception e) {
            return McpToolResponse.error("Lỗi điều khiển nhạc: " + e.getMessage());
        }
    }

    private McpToolResponse handleStatus(ExoPlayerService playerService) {
        int volume = playerService.getVolume();
        boolean isPlaying = playerService.isPlaying();
        com.phicomm.r1manager.server.model.Song currentSong = playerService.getCurrentSong();

        StringBuilder status = new StringBuilder();
        status.append("Âm lượng hiện tại: ").append(volume).append("%");
        status.append(", Trạng thái: ").append(isPlaying ? "đang phát" : "tạm dừng");
        if (currentSong != null) {
            String title = currentSong.getTitle();
            String artist = currentSong.getArtist();
            if (title != null && !title.isEmpty()) {
                status.append(", Bài hát: ").append(title);
                if (artist != null && !artist.isEmpty()) {
                    status.append(" - ").append(artist);
                }
            }
        }

        return McpToolResponse.success(status.toString());
    }

    private McpToolResponse handlePlaylist(ExoPlayerService playerService) {
        List<com.phicomm.r1manager.server.model.Song> playlist = playerService.getPlaylist();
        if (playlist == null || playlist.isEmpty()) {
            return McpToolResponse.success("Playlist trống.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Playlist (").append(playlist.size()).append(" bài):\n");
        for (int i = 0; i < playlist.size(); i++) {
            com.phicomm.r1manager.server.model.Song song = playlist.get(i);
            sb.append(i).append(". ").append(song.getTitle());
            if (song.getArtist() != null && !song.getArtist().isEmpty()) {
                sb.append(" - ").append(song.getArtist());
            }
            sb.append("\n");
        }
        return McpToolResponse.success(sb.toString());
    }

    private McpToolResponse handleQueue(ZingMp3Manager zingManager, String keyword) throws Exception {
        JSONObject searchResult = zingManager.search(keyword);
        if (searchResult == null || !searchResult.has("data")) {
            return McpToolResponse.error("Không tìm thấy kết quả cho: " + keyword);
        }

        JSONObject data = searchResult.optJSONObject("data");
        JSONArray items = data != null ? data.optJSONArray("items") : null;
        if (items == null || items.length() == 0) {
            return McpToolResponse.error("Không tìm thấy bài hát nào.");
        }

        // Find first song
        JSONObject bestMatch = findFirstSong(items);
        if (bestMatch == null) {
            return McpToolResponse.error("Không tìm thấy bài hát phù hợp.");
        }

        String id = bestMatch.optString("id");
        String title = bestMatch.optString("title");
        String artist = extractArtists(bestMatch);
        String thumb = bestMatch.optString("thumb");

        // Use "add" mode to queue instead of play
        String resultMessage = zingManager.playSong(id, title, artist, thumb, "add");
        return McpToolResponse.success(resultMessage);
    }

    private McpToolResponse handleRemove(ExoPlayerService playerService, int index) {
        List<com.phicomm.r1manager.server.model.Song> playlist = playerService.getPlaylist();
        if (playlist == null || index < 0 || index >= playlist.size()) {
            return McpToolResponse.error("Index không hợp lệ. Playlist có " +
                    (playlist != null ? playlist.size() : 0) + " bài.");
        }

        String removedTitle = playlist.get(index).getTitle();
        playerService.removeFromQueue(index);
        return McpToolResponse.success("Đã xóa bài \"" + removedTitle + "\" khỏi playlist.");
    }

    private McpToolResponse handleSearchAndPlay(ZingMp3Manager zingManager, String keyword) throws Exception {
        JSONObject searchResult = zingManager.search(keyword);
        if (searchResult == null || !searchResult.has("data")) {
            return McpToolResponse.error("Không tìm thấy kết quả từ ZingMp3 cho từ khóa: " + keyword);
        }

        JSONObject data = searchResult.optJSONObject("data");
        if (data == null || !data.has("items")) {
            return McpToolResponse.error("Dữ liệu tìm kiếm không đúng định dạng.");
        }

        JSONArray items = data.optJSONArray("items");
        if (items == null || items.length() == 0) {
            return McpToolResponse.error("Không tìm thấy bài hát nào.");
        }

        // Logic matched with zing.js: Iterate items -> find suggestions -> filter type
        // 1
        JSONObject bestMatch = null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject section = items.optJSONObject(i);
            if (section != null && section.has("suggestions")) {
                JSONArray suggestions = section.optJSONArray("suggestions");
                if (suggestions != null) {
                    for (int j = 0; j < suggestions.length(); j++) {
                        JSONObject suggestion = suggestions.optJSONObject(j);
                        if (suggestion != null && suggestion.optInt("type") == 1) { // type 1 = song
                            bestMatch = suggestion;
                            break;
                        }
                    }
                }
            }
            if (bestMatch != null)
                break;
        }

        if (bestMatch == null) {
            return McpToolResponse.error("Không tìm thấy bài hát phù hợp (chỉ thấy playlist hoặc từ khóa gợi ý).");
        }

        String id = bestMatch.optString("id");
        String title = bestMatch.optString("title");

        // Handle artists array
        StringBuilder artistsNames = new StringBuilder();
        JSONArray artists = bestMatch.optJSONArray("artists");
        if (artists != null && artists.length() > 0) {
            for (int k = 0; k < artists.length(); k++) {
                JSONObject artist = artists.optJSONObject(k);
                if (artist != null) {
                    if (artistsNames.length() > 0)
                        artistsNames.append(", ");
                    artistsNames.append(artist.optString("name"));
                }
            }
        } else {
            artistsNames.append("Unknown");
        }

        String thumb = bestMatch.optString("thumb");

        String resultMessage = zingManager.playSong(id, title, artistsNames.toString(), thumb, "standard");
        return McpToolResponse.success(resultMessage);
    }

    /**
     * Find first song from search results
     */
    private JSONObject findFirstSong(JSONArray items) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject section = items.optJSONObject(i);
            if (section != null && section.has("suggestions")) {
                JSONArray suggestions = section.optJSONArray("suggestions");
                if (suggestions != null) {
                    for (int j = 0; j < suggestions.length(); j++) {
                        JSONObject suggestion = suggestions.optJSONObject(j);
                        if (suggestion != null && suggestion.optInt("type") == 1) {
                            return suggestion;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extract artist names from song object
     */
    private String extractArtists(JSONObject song) {
        StringBuilder artistsNames = new StringBuilder();
        JSONArray artists = song.optJSONArray("artists");
        if (artists != null && artists.length() > 0) {
            for (int k = 0; k < artists.length(); k++) {
                JSONObject artist = artists.optJSONObject(k);
                if (artist != null) {
                    if (artistsNames.length() > 0)
                        artistsNames.append(", ");
                    artistsNames.append(artist.optString("name"));
                }
            }
        } else {
            artistsNames.append("Unknown");
        }
        return artistsNames.toString();
    }
}
