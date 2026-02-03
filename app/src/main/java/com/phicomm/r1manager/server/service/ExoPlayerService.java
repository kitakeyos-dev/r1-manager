package com.phicomm.r1manager.server.service;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.phicomm.r1manager.util.AppLog;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import com.phicomm.r1manager.server.manager.LedManager;
import com.phicomm.r1manager.server.manager.MusicServiceManager;

/**
 * Singleton service using ExoPlayer for advanced music playback
 * Thread-safe wrapper for UI-thread bound ExoPlayer
 */
public class ExoPlayerService implements LedManager.LedActivitySource {
    private static final String TAG = "ExoPlayerService";
    private static ExoPlayerService instance;
    private SimpleExoPlayer exoPlayer;
    private Context context;
    private Handler mainHandler;

    // Playlist State
    private List<com.phicomm.r1manager.server.model.Song> playlist = new ArrayList<>();
    private int currentSongIndex = -1;
    private int playbackMode = 0; // 0: Sequential, 1: Repeat One, 2: Repeat All, 3: Shuffle

    // Play History
    private LinkedList<com.phicomm.r1manager.server.model.Song> playHistory = new LinkedList<>();
    private static final int MAX_HISTORY = 50;

    // Shuffle History
    private LinkedList<Integer> shuffleHistory = new LinkedList<>();
    private static final int SHUFFLE_HISTORY_SIZE = 5;

    // Volume and Speed
    private int volume = 80; // 0-100
    private float playbackSpeed = 1.0f; // 0.5x - 2.0x

    // Cached Player State (for thread-safe access)
    private volatile boolean isPaused = false;
    private volatile boolean isPlaying = false;
    private volatile int currentPlaybackState = Player.STATE_IDLE;
    private volatile long cachedDuration = 0;
    private volatile long cachedPosition = 0;
    private volatile long lastPositionUpdate = 0;

    public static final int MODE_SEQUENCE = 0;
    public static final int MODE_REPEAT_ONE = 1;
    public static final int MODE_REPEAT_ALL = 2;
    public static final int MODE_SHUFFLE = 3;

    // Player States for API
    public enum PlayerState {
        IDLE, PREPARING, PLAYING, PAUSED, STOPPED
    }

    // Data source factory
    private DataSource.Factory dataSourceFactory;
    private ConcatenatingMediaSource concatenatingSource;

    private ExoPlayerService() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized ExoPlayerService getInstance() {
        if (instance == null) {
            instance = new ExoPlayerService();
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer == null) {
                    initializePlayer();
                    // Register with LED manager
                    LedManager.getInstance().registerActivitySource(ExoPlayerService.this);
                }
            }
        });
    }

    private void initializePlayer() {
        if (context == null) {
            AppLog.e(TAG, "Context is null, cannot initialize player");
            return;
        }

        try {
            exoPlayer = new SimpleExoPlayer.Builder(context).build();
            dataSourceFactory = new DefaultDataSourceFactory(context, Util.getUserAgent(context, "R1Manager"));
            concatenatingSource = new ConcatenatingMediaSource();

            // Set initial volume and speed
            exoPlayer.setVolume(volume / 100.0f);
            exoPlayer.setPlaybackParameters(new PlaybackParameters(playbackSpeed));

            // Add listener for events
            exoPlayer.addListener(new Player.EventListener() {
                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    currentPlaybackState = playbackState;

                    switch (playbackState) {
                        case Player.STATE_READY:
                            AppLog.d(TAG, "Player is ready");
                            cachedDuration = exoPlayer.getDuration();
                            break;
                        case Player.STATE_ENDED:
                            AppLog.d(TAG, "Playback ended");
                            cachedPosition = 0;
                            handlePlaybackComplete();
                            break;
                    }

                    // Track playing/pause state
                    boolean wasPlaying = isPlaying;
                    if (playbackState == Player.STATE_READY && playWhenReady) {
                        isPlaying = true;
                        isPaused = false;
                    } else if (playbackState == Player.STATE_READY && !playWhenReady) {
                        isPlaying = false;
                        isPaused = true;
                    } else {
                        isPlaying = false;
                    }

                    // Notify LED manager if stopped
                    if (wasPlaying && !isPlaying) {
                        com.phicomm.r1manager.server.manager.LedManager.getInstance().checkAndGatedStop();
                    }
                }

                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    AppLog.e(TAG, "Player error: " + error.getMessage(), error);
                    playNext();
                }

                @Override
                public void onPositionDiscontinuity(int reason) {
                    // Update position when seek or auto transition happens
                    if (exoPlayer != null) {
                        cachedPosition = exoPlayer.getCurrentPosition();
                        // Also update current index if it changed automatically (e.g. playlist
                        // transition)
                        int windowIndex = exoPlayer.getCurrentWindowIndex();
                        if (windowIndex != currentSongIndex && windowIndex >= 0 && windowIndex < playlist.size()) {
                            currentSongIndex = windowIndex;
                            addToHistory(getCurrentSong());
                        }
                    }
                }
            });

            AppLog.d(TAG, "ExoPlayer initialized");
        } catch (Exception e) {
            AppLog.e(TAG, "Error initializing player", e);
        }
    }

    private void handlePlaybackComplete() {
        isPaused = false;
        if (playbackMode == MODE_REPEAT_ONE) {
            // Replay current
            seekTo(0);
            resume();
        } else {
            playNext();
        }
    }

    // --- Playlist Management (Thread-safe) ---

    public synchronized boolean addToQueue(final com.phicomm.r1manager.server.model.Song song) {
        if (song == null)
            return false;

        if (song.getId() != null) {
            for (com.phicomm.r1manager.server.model.Song s : playlist) {
                if (song.getId().equals(s.getId()))
                    return false;
            }
        }

        playlist.add(song);

        // Dynamic Update: Add to end
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (concatenatingSource != null && dataSourceFactory != null) {
                    MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(Uri.parse(song.getStreamUrl()));
                    concatenatingSource.addMediaSource(mediaSource);

                    // If player was idle/ended and this is the first item, prepare or play
                    if (playlist.size() == 1 && exoPlayer != null) {
                        currentSongIndex = 0;
                        exoPlayer.prepare(concatenatingSource, false, false);
                    }
                }
            }
        });
        return true;
    }

    public synchronized boolean addToQueueNext(final com.phicomm.r1manager.server.model.Song song) {
        if (song == null)
            return false;

        if (song.getId() != null) {
            for (com.phicomm.r1manager.server.model.Song s : playlist) {
                if (song.getId().equals(s.getId()))
                    return false;
            }
        }

        final int insertPosition = currentSongIndex + 1;
        // Clamp insert position
        int safePos = insertPosition;
        if (safePos > playlist.size())
            safePos = playlist.size();

        playlist.add(safePos, song);

        final int finalPos = safePos;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (concatenatingSource != null && dataSourceFactory != null) {
                    MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(Uri.parse(song.getStreamUrl()));
                    concatenatingSource.addMediaSource(finalPos, mediaSource);
                }
            }
        });
        return true;
    }

    public synchronized boolean moveInQueue(final int fromIndex, final int toIndex) {
        if (fromIndex < 0 || fromIndex >= playlist.size() || toIndex < 0 || toIndex >= playlist.size()) {
            return false;
        }
        com.phicomm.r1manager.server.model.Song song = playlist.remove(fromIndex);
        playlist.add(toIndex, song);

        if (fromIndex == currentSongIndex) {
            currentSongIndex = toIndex;
        } else if (fromIndex < currentSongIndex && toIndex >= currentSongIndex) {
            currentSongIndex--;
        } else if (fromIndex > currentSongIndex && toIndex <= currentSongIndex) {
            currentSongIndex++;
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (concatenatingSource != null) {
                    concatenatingSource.moveMediaSource(fromIndex, toIndex);
                }
            }
        });
        return true;
    }

    public synchronized void clearQueue() {
        playlist.clear();
        currentSongIndex = -1;
        stop();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (concatenatingSource != null)
                    concatenatingSource.clear();
            }
        });
    }

    public synchronized void removeFromQueue(final int index) {
        if (index >= 0 && index < playlist.size()) {
            playlist.remove(index);

            if (index < currentSongIndex) {
                currentSongIndex--;
            } else if (index == currentSongIndex) {
                if (playlist.isEmpty()) {
                    currentSongIndex = -1;
                    stop();
                } else {
                    if (currentSongIndex >= playlist.size())
                        currentSongIndex = 0;
                    playIndex(currentSongIndex);
                }
            }

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (concatenatingSource != null) {
                        concatenatingSource.removeMediaSource(index);
                    }
                }
            });
        }
    }

    // Removed updateMediaSource() as it causes re-buffering

    // --- Getters (Safe for API threads) ---

    public synchronized List<com.phicomm.r1manager.server.model.Song> getPlaylist() {
        return new ArrayList<>(playlist);
    }

    public synchronized com.phicomm.r1manager.server.model.Song getCurrentSong() {
        if (currentSongIndex >= 0 && currentSongIndex < playlist.size()) {
            return playlist.get(currentSongIndex);
        }
        return null;
    }

    public synchronized List<com.phicomm.r1manager.server.model.Song> getPlayHistory() {
        return new ArrayList<>(playHistory);
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public PlayerState getPlayerState() {
        if (currentPlaybackState == Player.STATE_BUFFERING)
            return PlayerState.PREPARING;
        if (isPaused)
            return PlayerState.PAUSED;
        if (isPlaying)
            return PlayerState.PLAYING;
        if (currentPlaybackState == Player.STATE_ENDED)
            return PlayerState.STOPPED;
        return PlayerState.IDLE;
    }

    public int getCurrentPosition() {
        // Return cached position + estimated delta if playing
        // Or simply post a request to update cache (too slow for RT)
        // For now, let's rely on regular updates or return last known
        // Important: DO NOT call exoPlayer directly here!
        return (int) cachedPosition;
    }

    public int getDuration() {
        return (int) cachedDuration;
    }

    public int getVolume() {
        return volume;
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public int getPlaybackMode() {
        return playbackMode;
    }

    // --- Setters (Dispatched to Main Thread) ---

    public void setVolume(final int newVolume) {
        int v = newVolume;
        if (v < 0)
            v = 0;
        if (v > 100)
            v = 100;
        this.volume = v;

        final float vol = v / 100.0f;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null)
                    exoPlayer.setVolume(vol);
            }
        });
    }

    public void setPlaybackSpeed(final float newSpeed) {
        float s = newSpeed;
        if (s < 0.5f)
            s = 0.5f;
        if (s > 2.0f)
            s = 2.0f;
        this.playbackSpeed = s;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null)
                    exoPlayer.setPlaybackParameters(new PlaybackParameters(newSpeed));
            }
        });
    }

    public void setPlaybackMode(final int mode) {
        this.playbackMode = mode;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null) {
                    if (mode == MODE_REPEAT_ONE)
                        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
                    else if (mode == MODE_REPEAT_ALL)
                        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
                    else
                        exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
                }
            }
        });
    }

    // --- Controls (Dispatched to Main Thread) ---

    public void playIndex(final int index) {
        if (index < 0 || index >= playlist.size())
            return;

        currentSongIndex = index;
        final com.phicomm.r1manager.server.model.Song song = playlist.get(index);
        addToHistory(song);

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null) {
                    try {
                        // Ensure player is prepared if IDLE or STOPPED
                        int state = exoPlayer.getPlaybackState();
                        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                            if (concatenatingSource != null) {
                                exoPlayer.prepare(concatenatingSource, false, false);
                            }
                        }

                        // Check bounds before seeking
                        if (concatenatingSource != null && index < concatenatingSource.getSize()) {
                            exoPlayer.seekTo(index, 0);
                            exoPlayer.setPlayWhenReady(true);
                        } else {
                            AppLog.e(TAG, "Invalid seek index: " + index + ", source size: " +
                                    (concatenatingSource != null ? concatenatingSource.getSize() : "null"));
                        }
                    } catch (Exception e) {
                        AppLog.e(TAG, "Error in playIndex", e);
                        // Prevent stuck state
                        playNext();
                    }
                }
            }
        });
    }

    public void playNext() {
        if (playlist.isEmpty())
            return;

        int nextIndex = currentSongIndex;
        if (playbackMode == MODE_SHUFFLE) {
            nextIndex = getSmartShuffleIndex();
        } else {
            // FIX: If only 1 song, just replay it regardless of mode (UX fix)
            if (playlist.size() == 1) {
                nextIndex = 0;
            } else {
                nextIndex++;
                if (nextIndex >= playlist.size()) {
                    if (playbackMode == MODE_REPEAT_ALL)
                        nextIndex = 0;
                    else {
                        stop();
                        return;
                    }
                }
            }
        }
        playIndex(nextIndex);
    }

    public void playPrevious() {
        if (playlist.isEmpty())
            return;

        int prevIndex = currentSongIndex;
        if (playbackMode == MODE_SHUFFLE) {
            prevIndex = getSmartShuffleIndex();
        } else {
            prevIndex--;
            if (prevIndex < 0)
                prevIndex = playlist.size() - 1;
        }
        playIndex(prevIndex);
    }

    public void pause() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null)
                    exoPlayer.setPlayWhenReady(false);
            }
        });
        isPaused = true;
        isPlaying = false;
    }

    public void resume() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null) {
                    // Check IDLE state same as playIndex
                    int state = exoPlayer.getPlaybackState();
                    if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                        if (concatenatingSource != null) {
                            exoPlayer.prepare(concatenatingSource, false, false);
                            if (currentSongIndex >= 0)
                                exoPlayer.seekTo(currentSongIndex, 0);
                        }
                    }
                    exoPlayer.setPlayWhenReady(true);
                }
            }
        });
        isPaused = false;
        isPlaying = true;
    }

    public void stop() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null)
                    exoPlayer.stop();
            }
        });
        isPaused = false;
        isPlaying = false;
    }

    public void seekTo(final int msec) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null)
                    exoPlayer.seekTo(msec);
            }
        });
    }

    public void play(String path) {
        com.phicomm.r1manager.server.model.Song s = new com.phicomm.r1manager.server.model.Song();
        s.setTitle("Direct Play");
        s.setStreamUrl(path);

        clearQueue();
        addToQueue(s);
        playIndex(0);
    }

    public synchronized void playSong(com.phicomm.r1manager.server.model.Song song) {
        if (song == null)
            return;

        // Check if exists
        int existingIndex = -1;
        if (song.getId() != null) {
            for (int i = 0; i < playlist.size(); i++) {
                if (song.getId().equals(playlist.get(i).getId())) {
                    existingIndex = i;
                    break;
                }
            }
        }

        if (existingIndex != -1) {
            playIndex(existingIndex);
        } else {
            addToQueue(song);
            playIndex(playlist.size() - 1);
        }
    }

    public void release() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null) {
                    exoPlayer.release();
                    exoPlayer = null;
                }
            }
        });
    }

    // Helpers

    private int getSmartShuffleIndex() {
        if (playlist.size() <= 1)
            return 0;
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            int idx = random.nextInt(playlist.size());
            if (!shuffleHistory.contains(idx)) {
                shuffleHistory.add(idx);
                if (shuffleHistory.size() > SHUFFLE_HISTORY_SIZE)
                    shuffleHistory.removeFirst();
                return idx;
            }
        }
        int idx = random.nextInt(playlist.size());
        shuffleHistory.add(idx);
        return idx;
    }

    private void addToHistory(com.phicomm.r1manager.server.model.Song song) {
        if (song == null)
            return;
        playHistory.addFirst(song);
        if (playHistory.size() > MAX_HISTORY)
            playHistory.removeLast();
    }

    @Override
    public boolean isLedActivityActive() {
        MusicLedSyncService service = MusicServiceManager.getInstance().getMusicLedSyncService();
        return isPlaying && service != null && service.isEnabled();
    }

    // Polling for UI updates (Optional: could add a ticker to update
    // cachedPosition)
    // For now, client side can interpolate, or we update on events.
}
