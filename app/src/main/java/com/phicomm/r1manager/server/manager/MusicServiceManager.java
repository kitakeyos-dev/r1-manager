package com.phicomm.r1manager.server.manager;

import android.content.Context;
import com.phicomm.r1manager.server.service.AudioVisualizerService;
import com.phicomm.r1manager.server.service.MusicLedSyncService;
import com.phicomm.r1manager.server.manager.LedManager;
import com.phicomm.r1manager.server.service.XiaozhiService;

/**
 * Singleton manager for music-related services
 * Note: Services are managed by Android system, this class only holds
 * references
 */
public class MusicServiceManager {

    private static MusicServiceManager instance;

    // Static references set by services themselves when they start
    private static AudioVisualizerService audioVisualizerServiceInstance;
    private static MusicLedSyncService musicLedSyncServiceInstance;
    private static XiaozhiService xiaozhiServiceInstance;
    private static LedManager ledManager;

    private MusicServiceManager() {
    }

    public static synchronized MusicServiceManager getInstance() {
        if (instance == null) {
            instance = new MusicServiceManager();
        }
        return instance;
    }

    /**
     * Initialize - just returns dummy instances for controllers
     * Real services are started by Android system via AndroidManifest
     */
    public void initialize(Context context) {
        // Services will register themselves when they start
        // This method is kept for compatibility but does nothing
    }

    // Static methods for services to register themselves
    public static void registerAudioVisualizerService(AudioVisualizerService service) {
        audioVisualizerServiceInstance = service;
        // Connect to music led sync service if available
        if (musicLedSyncServiceInstance != null) {
            musicLedSyncServiceInstance.setVisualizerService(service);
        }
    }

    public static void registerMusicLedSyncService(MusicLedSyncService service) {
        musicLedSyncServiceInstance = service;
        // Connect to visualizer if available
        if (audioVisualizerServiceInstance != null) {
            service.setVisualizerService(audioVisualizerServiceInstance);
        }
    }

    public static void registerXiaozhiService(XiaozhiService service) {
        xiaozhiServiceInstance = service;
    }

    public LedManager getLedManager() {
        if (ledManager == null) {
            ledManager = LedManager.getInstance();
        }
        return ledManager;
    }

    public AudioVisualizerService getAudioVisualizerService() {
        return audioVisualizerServiceInstance;
    }

    public MusicLedSyncService getMusicLedSyncService() {
        return musicLedSyncServiceInstance;
    }

    public static XiaozhiService getXiaozhiService() {
        return xiaozhiServiceInstance;
    }

    public void shutdown() {
        // Services are managed by Android system
        // Just clear references
        audioVisualizerServiceInstance = null;
        musicLedSyncServiceInstance = null;
        xiaozhiServiceInstance = null;
    }
}
