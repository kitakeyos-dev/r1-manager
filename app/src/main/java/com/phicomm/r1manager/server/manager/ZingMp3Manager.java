package com.phicomm.r1manager.server.manager;

import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.App;
import com.phicomm.r1manager.config.AppConfig;
import com.phicomm.r1manager.server.client.ZingMp3Client;
import com.phicomm.r1manager.server.model.Song;
import com.phicomm.r1manager.server.service.ExoPlayerService;
import org.json.JSONObject;
import java.net.URLEncoder;

/**
 * ZingMp3Manager - Orchestrates music search and playback logic.
 * Thins out ZingMp3Controller by handling business orchestration.
 */
public class ZingMp3Manager {
    private static final String TAG = "ZingMp3Manager";
    private static ZingMp3Manager instance;

    private final ZingMp3Client zingClient;
    private final ExoPlayerService playerService;

    private ZingMp3Manager() {
        this.zingClient = ZingMp3Client.getInstance();
        this.playerService = ExoPlayerService.getInstance();
    }

    public static synchronized ZingMp3Manager getInstance() {
        if (instance == null) {
            instance = new ZingMp3Manager();
        }
        return instance;
    }

    public JSONObject search(String keyword) throws Exception {
        return zingClient.search(keyword);
    }

    public String playSong(String id, String title, String artist, String thumbnail, String mode) throws Exception {
        JSONObject response = zingClient.getFullInfo(id);
        if (response == null) {
            throw new Exception("No response from Zing API");
        }

        if (response.has("err") && response.optInt("err", 0) != 0) {
            throw new Exception("API error: " + response.optString("msg", "Unknown"));
        }

        // Fallback for missing metadata
        String finalTitle = (title != null && !title.isEmpty() && !"Unknown".equals(title)) ? title : "Unknown";
        String finalArtist = (artist != null && !artist.isEmpty() && !"Unknown".equals(artist)) ? artist : "Unknown";
        String finalThumbnail = (thumbnail != null) ? thumbnail : "";

        if ("Unknown".equals(finalTitle)) {
            JSONObject songData = response.has("data") ? response.optJSONObject("data") : response;
            if (songData != null) {
                finalTitle = songData.optString("title", finalTitle);
                finalThumbnail = songData.optString("thumbnailM", songData.optString("thumb", finalThumbnail));
            }
        }

        String streamUrl = extractStreamUrl(response);
        if (streamUrl == null) {
            throw new Exception("No valid stream found (VIP/Empty)");
        }

        if (!streamUrl.startsWith("http")) {
            streamUrl = "https:" + streamUrl;
        }

        // Proxy Mode logic
        int localPort = AppConfig.getInstance(App.getInstance()).getPort();
        String finalUrl = String.format("http://127.0.0.1:%d/api/zing/proxy?url=%s",
                localPort, URLEncoder.encode(streamUrl, "UTF-8"));

        Song song = new Song();
        song.setId(id);
        song.setTitle(finalTitle);
        song.setArtist(finalArtist);
        song.setThumbnail(finalThumbnail);
        song.setStreamUrl(finalUrl);

        if ("add".equalsIgnoreCase(mode)) {
            playerService.addToQueue(song);
            return "Đã thêm vào danh sách phát: " + finalTitle;
        } else {
            playerService.clearQueue();
            playerService.addToQueue(song);
            playerService.playIndex(0);
            return "Đang phát (Single): " + finalTitle;
        }
    }

    private String extractStreamUrl(JSONObject response) {
        JSONObject streaming = null;
        if (response.has("128") || response.has("320")) {
            streaming = response;
        } else if (response.has("data")) {
            Object data = response.opt("data");
            if (data instanceof JSONObject) {
                JSONObject dataObj = (JSONObject) data;
                if (dataObj.has("128") || dataObj.has("320"))
                    streaming = dataObj;
                else if (dataObj.has("streaming"))
                    streaming = dataObj.optJSONObject("streaming");
            }
        } else if (response.has("streaming")) {
            streaming = response.optJSONObject("streaming");
        }

        if (streaming == null)
            return null;
        String streamUrl = streaming.has("128") && !streaming.isNull("128") ? streaming.optString("128")
                : (streaming.has("320") && !streaming.isNull("320") ? streaming.optString("320") : null);

        if ("VIP".equals(streamUrl))
            return null;
        return (streamUrl != null && !streamUrl.isEmpty()) ? streamUrl : null;
    }
}
