package com.phicomm.r1manager.server.controller;

import com.phicomm.r1manager.server.annotation.GetMapping;
import com.phicomm.r1manager.server.annotation.PostMapping;
import com.phicomm.r1manager.server.annotation.RequestBody;
import com.phicomm.r1manager.server.annotation.RequestMapping;
import com.phicomm.r1manager.server.annotation.RequestParam;
import com.phicomm.r1manager.server.annotation.RestController;
import com.phicomm.r1manager.server.model.ApiResponse;
import com.phicomm.r1manager.server.model.Song;
import com.phicomm.r1manager.server.service.ExoPlayerService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/playlist")
public class PlaylistController {

    private final ExoPlayerService playerService;

    public PlaylistController() {
        this.playerService = ExoPlayerService.getInstance();
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getPlaylist() {
        Map<String, Object> data = new HashMap<>();
        data.put("playlist", playerService.getPlaylist());
        data.put("currentSong", playerService.getCurrentSong());
        data.put("isPlaying", playerService.isPlaying());
        data.put("isPaused", playerService.isPaused());
        data.put("playerState", playerService.getPlayerState().toString());
        data.put("mode", playerService.getPlaybackMode());
        data.put("position", playerService.getCurrentPosition());
        data.put("duration", playerService.getDuration());
        data.put("volume", playerService.getVolume());
        data.put("speed", playerService.getPlaybackSpeed());
        return ApiResponse.success(data);
    }

    @PostMapping("/add")
    public ApiResponse<String> addToQueue(@RequestBody Song song) {
        if (song.getStreamUrl() == null || song.getStreamUrl().isEmpty()) {
            return ApiResponse.error("Stream URL required");
        }
        boolean added = playerService.addToQueue(song);
        if (added) {
            return ApiResponse.successMessage("Đã thêm vào danh sách phát: " + song.getTitle());
        } else {
            return ApiResponse.error("Bài hát đã có trong danh sách");
        }
    }

    @PostMapping("/add-next")
    public ApiResponse<String> addToQueueNext(@RequestBody Song song) {
        if (song.getStreamUrl() == null || song.getStreamUrl().isEmpty()) {
            return ApiResponse.error("Stream URL required");
        }
        boolean added = playerService.addToQueueNext(song);
        if (added) {
            return ApiResponse.successMessage("Đã thêm vào vị trí tiếp theo: " + song.getTitle());
        } else {
            return ApiResponse.error("Bài hát đã có trong danh sách");
        }
    }

    @PostMapping("/reorder")
    public ApiResponse<String> reorderQueue(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("fromIndex") || !body.containsKey("toIndex")) {
            return ApiResponse.error("fromIndex and toIndex required");
        }
        try {
            int fromIndex = (int) Double.parseDouble(body.get("fromIndex").toString());
            int toIndex = (int) Double.parseDouble(body.get("toIndex").toString());
            boolean success = playerService.moveInQueue(fromIndex, toIndex);
            if (success) {
                return ApiResponse.successMessage("Đã sắp xếp lại queue");
            } else {
                return ApiResponse.error("Invalid indices");
            }
        } catch (Exception e) {
            return ApiResponse.error("Invalid parameters");
        }
    }

    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> getHistory() {
        Map<String, Object> data = new HashMap<>();
        data.put("history", playerService.getPlayHistory());
        return ApiResponse.success(data);
    }

    @PostMapping("/volume")
    public ApiResponse<String> setVolume(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("volume")) {
            return ApiResponse.error("Volume required");
        }
        try {
            int volume = (int) Double.parseDouble(body.get("volume").toString());
            playerService.setVolume(volume);
            return ApiResponse.successMessage("Volume set to: " + volume);
        } catch (Exception e) {
            return ApiResponse.error("Invalid volume value");
        }
    }

    @PostMapping("/speed")
    public ApiResponse<String> setSpeed(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("speed")) {
            return ApiResponse.error("Speed required");
        }
        try {
            float speed = Float.parseFloat(body.get("speed").toString());
            playerService.setPlaybackSpeed(speed);
            return ApiResponse.successMessage("Speed set to: " + speed + "x");
        } catch (Exception e) {
            return ApiResponse.error("Invalid speed value");
        }
    }

    @PostMapping("/remove")
    public ApiResponse<String> removeIndex(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("index")) {
            return ApiResponse.error("Index required");
        }
        try {
            int index = (int) Double.parseDouble(body.get("index").toString());
            playerService.removeFromQueue(index);
            return ApiResponse.successMessage("Removed index: " + index);
        } catch (Exception e) {
            return ApiResponse.error("Invalid index");
        }
    }

    @PostMapping("/play-index")
    public ApiResponse<String> playIndex(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("index")) {
            return ApiResponse.error("Index required");
        }
        try {
            int index = (int) Double.parseDouble(body.get("index").toString());
            playerService.playIndex(index);
            return ApiResponse.successMessage("Playing index: " + index);
        } catch (Exception e) {
            return ApiResponse.error("Invalid index");
        }
    }

    @PostMapping("/clear")
    public ApiResponse<String> clearQueue() {
        playerService.clearQueue();
        return ApiResponse.successMessage("Queue cleared");
    }

    @PostMapping("/control")
    public ApiResponse<String> control(@RequestBody Map<String, Object> body) {
        String action = (String) body.get("action");
        if (action == null)
            return ApiResponse.error("Action required");

        switch (action) {
            case "next":
                playerService.playNext();
                break;
            case "prev":
                playerService.playPrevious();
                break;
            case "pause":
                playerService.pause();
                break;
            case "resume":
                playerService.resume();
                break;
            case "seek":
                if (body.containsKey("value")) {
                    try {
                        int msec = Integer.parseInt(body.get("value").toString());
                        playerService.seekTo(msec);
                    } catch (NumberFormatException e) {
                        return ApiResponse.error("Invalid seek value");
                    }
                }
                break;
            case "mode":
                if (body.containsKey("value")) {
                    try {
                        int mode = Integer.parseInt(body.get("value").toString());
                        playerService.setPlaybackMode(mode);
                    } catch (NumberFormatException e) {
                        return ApiResponse.error("Invalid mode value");
                    }
                }
                break;
            default:
                return ApiResponse.error("Unknown action: " + action);
        }
        return ApiResponse.successMessage("Action executed: " + action);
    }
}
