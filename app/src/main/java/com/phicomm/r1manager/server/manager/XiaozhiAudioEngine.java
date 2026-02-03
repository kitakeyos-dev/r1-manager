package com.phicomm.r1manager.server.manager;

import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.server.voicebot.AudioRecorder;
import com.phicomm.r1manager.server.voicebot.OpusDecoder;
import com.phicomm.r1manager.server.voicebot.OpusEncoder;
import com.phicomm.r1manager.server.voicebot.OpusStreamPlayer;

/**
 * Manages Audio recording, encoding, decoding, and playback for Xiaozhi Service.
 *
 * Uses VAD-based endpointing to detect complete sentences (speech -> 800ms silence)
 * before sending to server. This prevents Whisper hallucination from background noise.
 *
 * Audio Flow:
 *   Mic -> AudioRecorder -> VAD State Machine -> onSentenceFinished()
 *                                             -> OpusEncoder -> Server
 */
public class XiaozhiAudioEngine {
    private static final String TAG = "XiaozhiAudioEngine";

    // Audio Constants
    private static final int RECORD_SAMPLE_RATE = 16000;
    private static final int RECORD_CHANNELS = 1;

    // VAD uses 30ms frames (libfvad requirement: 10, 20, or 30ms only)
    private static final int VAD_FRAME_SIZE_MS = 30;
    private static final int VAD_FRAME_BYTES = (RECORD_SAMPLE_RATE * VAD_FRAME_SIZE_MS / 1000) * RECORD_CHANNELS * 2; // 960 bytes

    // Opus encoder uses 60ms frames (xiaozhi.me server requirement)
    private static final int OPUS_FRAME_SIZE_MS = 60;
    private static final int OPUS_FRAME_BYTES = (RECORD_SAMPLE_RATE * OPUS_FRAME_SIZE_MS / 1000) * RECORD_CHANNELS * 2; // 1920 bytes

    private static final int PLAY_SAMPLE_RATE = 24000;
    private static final int PLAY_CHANNELS = 1;
    private static final int PLAY_FRAME_SIZE_MS = 60;

    private AudioRecorder recorder;
    private OpusEncoder encoder;
    private OpusDecoder decoder;
    private OpusStreamPlayer player;

    private SnowboyHotwordDetector hotwordDetector;
    private Runnable wakeCallback;
    private SentenceListener sentenceListener;
    private volatile boolean isWakeDetectionMode = false;
    private android.content.Context context;

    // Buffer to accumulate 2 VAD frames (30ms each) into 1 Opus frame (60ms)
    private byte[] frameAccumulator = new byte[OPUS_FRAME_BYTES];
    private int accumulatorOffset = 0;

    /**
     * Callback for VAD-based sentence detection.
     * Used for client-side endpointing to prevent Whisper hallucination.
     *
     * Audio frames are sent DURING speech (not after), matching server expectations.
     */
    public interface SentenceListener {
        /**
         * Called for each encoded audio frame during speech.
         * Frames are sent immediately as they are recorded.
         * @param encodedFrame Single Opus-encoded frame
         */
        void onAudioFrame(byte[] encodedFrame);

        /**
         * Called when a complete sentence is finished (speech followed by 800ms silence).
         * All frames have already been sent via onAudioFrame.
         * This is a notification only - no audio data.
         */
        void onSentenceComplete();

        /**
         * Called when speech starts.
         */
        void onSpeechStart();
    }

    public XiaozhiAudioEngine(android.content.Context context) {
        this.context = context;
        hotwordDetector = new SnowboyHotwordDetector(context);
    }

    /**
     * Start recording with VAD-based endpointing.
     * Complete sentences are detected and sent to prevent Whisper hallucination.
     *
     * @param listener Listener for sentence events
     */
    public synchronized void startRecording(SentenceListener listener) {
        this.sentenceListener = listener;
        this.isWakeDetectionMode = false;

        startRecorderInternal();
        AppLog.i(TAG, "Recording started (VAD Endpointing Mode)");
    }

    /**
     * Start recording in wake word detection mode.
     * Raw audio is sent to hotword detector, no VAD processing.
     *
     * @param onWake Callback when wake word detected
     */
    public synchronized void startWakeDetection(Runnable onWake) {
        this.wakeCallback = onWake;
        this.isWakeDetectionMode = true;

        startRecorderInternal();
        AppLog.i(TAG, "Recording started (Wake Detection Mode)");
    }

    private void startRecorderInternal() {
        if (!com.phicomm.r1manager.config.XiaozhiConfig.getInstance(context).isVoiceBotEnabled()) {
            AppLog.w(TAG, "Voice Bot is DISABLED. Recording skipped.");
            return;
        }

        // Opus encoder uses 60ms frames (server requirement)
        if (encoder == null) {
            encoder = new OpusEncoder(RECORD_SAMPLE_RATE, RECORD_CHANNELS, OPUS_FRAME_SIZE_MS);
        }

        // Stop existing recorder when switching modes to ensure VAD is properly initialized
        if (recorder != null && recorder.isRecording()) {
            AppLog.d(TAG, "Stopping recorder to switch modes...");
            recorder.stopRecording();
        }

        // Create new recorder if needed
        if (recorder == null) {
            recorder = new AudioRecorder(RECORD_SAMPLE_RATE, RECORD_CHANNELS, VAD_FRAME_SIZE_MS);
        }

        // Reset frame accumulator
        accumulatorOffset = 0;

        // 1. Raw Listener -> Wake Word Detection (parallel path)
        recorder.setRawListener(pcmData -> {
            if (isWakeDetectionMode) {
                if (hotwordDetector != null && hotwordDetector.detect(pcmData)) {
                    if (wakeCallback != null) {
                        try {
                            wakeCallback.run();
                        } catch (Exception e) {
                            AppLog.e(TAG, "Wake callback error", e);
                        }
                    }
                }
            }
        });

        // 2. VAD Sentence Listener -> Buffer 2x30ms frames, encode as 60ms, send
        if (!isWakeDetectionMode && sentenceListener != null) {
            AppLog.i(TAG, "Setting VAD sentence listener for conversation mode");
            recorder.setVadSentenceListener(new AudioRecorder.VadSentenceListener() {
                @Override
                public void onAudioFrame(byte[] pcmFrame) {
                    // Accumulate 30ms VAD frames into 60ms Opus frames
                    if (encoder != null && sentenceListener != null) {
                        // Copy frame to accumulator
                        System.arraycopy(pcmFrame, 0, frameAccumulator, accumulatorOffset, VAD_FRAME_BYTES);
                        accumulatorOffset += VAD_FRAME_BYTES;

                        // When we have 60ms worth of audio, encode and send
                        if (accumulatorOffset >= OPUS_FRAME_BYTES) {
                            byte[] encoded = encoder.encode(frameAccumulator);
                            if (encoded != null) {
                                sentenceListener.onAudioFrame(encoded);
                            }
                            accumulatorOffset = 0;
                        }
                    }
                }

                @Override
                public void onSpeechStart() {
                    // Reset accumulator on speech start
                    accumulatorOffset = 0;
                    if (sentenceListener != null) {
                        sentenceListener.onSpeechStart();
                    }
                }

                @Override
                public void onSentenceComplete() {
                    // Flush any remaining audio in accumulator
                    if (accumulatorOffset > 0 && encoder != null && sentenceListener != null) {
                        // Pad with silence if needed
                        java.util.Arrays.fill(frameAccumulator, accumulatorOffset, OPUS_FRAME_BYTES, (byte) 0);
                        byte[] encoded = encoder.encode(frameAccumulator);
                        if (encoded != null) {
                            sentenceListener.onAudioFrame(encoded);
                        }
                        accumulatorOffset = 0;
                    }
                    if (sentenceListener != null) {
                        sentenceListener.onSentenceComplete();
                    }
                }
            });
        } else {
            // Wake mode - clear VAD listener
            recorder.setVadSentenceListener(null);
        }

        recorder.startRecording();
    }

    public synchronized void stopRecording() {
        if (recorder != null) {
            recorder.stopRecording();
            AppLog.i(TAG, "Recording stopped");
        }
    }

    public synchronized void playAudio(byte[] opusData) {
        ensurePlayerInitialized();
        if (decoder != null && player != null) {
            byte[] pcm = decoder.decode(opusData);
            if (pcm != null) {
                player.play(pcm);
            }
        }
    }

    public synchronized void waitForPlaybackCompletion() {
        if (player != null) {
            player.waitForPlaybackCompletion();
        }
    }

    private void ensurePlayerInitialized() {
        if (decoder == null) {
            decoder = new OpusDecoder(PLAY_SAMPLE_RATE, PLAY_CHANNELS, PLAY_FRAME_SIZE_MS);
        }
        if (player == null) {
            player = new OpusStreamPlayer(PLAY_SAMPLE_RATE, PLAY_CHANNELS, PLAY_FRAME_SIZE_MS);
            player.start();
        }
    }

    public synchronized void release() {
        if (recorder != null) {
            recorder.stopRecording();
            recorder = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        if (encoder != null) {
            encoder.release();
            encoder = null;
        }
        if (decoder != null) {
            decoder.release();
            decoder = null;
        }
        AppLog.i(TAG, "Audio engine released");
    }

    /**
     * Check if currently recording.
     */
    public boolean isRecording() {
        return recorder != null && recorder.isRecording();
    }

    /**
     * Get current VAD state (for debugging).
     */
    public String getVadState() {
        return recorder != null ? recorder.getVadState() : "NOT_INITIALIZED";
    }
}
