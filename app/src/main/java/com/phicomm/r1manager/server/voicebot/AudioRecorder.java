package com.phicomm.r1manager.server.voicebot;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.util.ThreadManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Audio Recorder with Hybrid VAD for client-side endpointing.
 *
 * Uses libfvad (WebRTC) as primary VAD for accurate speech detection.
 * Automatically falls back to amplitude-based VAD for 4-mic array devices
 * that output low-level audio (detected by monitoring max RMS over ~3 seconds).
 *
 * State Machine:
 *   LISTENING -> (speech detected) -> SPEAKING -> (silence detected) -> SILENCE_CHECK
 *                                                                      |
 *                                     (speech resumes) <---------------+
 *                                                                      |
 *                                     (800ms silence) -> ENDPOINTING -> callback
 */
public class AudioRecorder {
    private static final String TAG = "AudioRecorder";

    private final int sampleRate;
    private final int channels;
    private final int frameBytes;
    private final int bufferSize;
    private final int channelConfig;

    private AudioRecord audioRecord;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);

    // ========== VAD Configuration ==========
    private static final int VAD_FRAME_MS = 30;
    private static final int VAD_FRAME_BYTES = 960;    // 30ms @ 16kHz, 16-bit mono

    // Endpointing configuration
    private static final int SILENCE_THRESHOLD_MS = 800;  // 800ms silence = sentence end
    private static final int SILENCE_FRAMES_THRESHOLD = SILENCE_THRESHOLD_MS / VAD_FRAME_MS; // ~27 frames

    // Pre-roll buffer (captures audio before speech for context)
    private static final int PRE_ROLL_MS = 300;
    private static final int PRE_ROLL_FRAMES = PRE_ROLL_MS / VAD_FRAME_MS; // 10 frames

    // Minimum speech duration to avoid noise triggers
    private static final int MIN_SPEECH_MS = 100;
    private static final int MIN_SPEECH_FRAMES = MIN_SPEECH_MS / VAD_FRAME_MS; // ~3 frames

    // ========== Hybrid VAD: libfvad + Amplitude fallback ==========
    // libfvad is more accurate but requires normal audio levels (RMS 500+)
    // Amplitude-based is less accurate but works with low-level 4-mic arrays
    private static final int RMS_SPEECH_THRESHOLD = 20;     // For amplitude fallback
    private static final int RMS_LOW_LEVEL_DETECT = 100;    // If max RMS < this, switch to amplitude mode
    private static final int LOW_LEVEL_DETECT_FRAMES = 100; // Check after this many frames (~3 seconds)
    private boolean useAmplitudeMode = false;
    private int maxRmsObserved = 0;
    private int frameCountForDetection = 0;

    // VAD components
    private VadDetector vadDetector;

    // VAD state machine states
    private enum VadState {
        LISTENING,      // Waiting for speech
        SPEAKING,       // Recording speech
        SILENCE_CHECK   // Checking if speech ended
    }

    private VadState vadState = VadState.LISTENING;
    private int silenceFrameCount = 0;
    private int speechFrameCount = 0;

    // Pre-roll circular buffer
    private byte[][] preRollBuffer;
    private int preRollIndex = 0;

    /**
     * Callback for raw audio data (used for wake word detection).
     */
    public interface AudioDataListener {
        void onAudioData(byte[] data);
    }

    /**
     * Callback for VAD-based speech detection with real-time streaming.
     * Audio frames are sent DURING speech (not after) to match server expectations.
     */
    public interface VadSentenceListener {
        /**
         * Called for each audio frame DURING speech.
         * Frames are sent immediately as they are recorded.
         * @param pcmFrame Raw PCM audio frame (30ms, 960 bytes)
         */
        void onAudioFrame(byte[] pcmFrame);

        /**
         * Called when speech starts.
         */
        void onSpeechStart();

        /**
         * Called when sentence is complete (silence detected after speech).
         * All audio has already been sent via onAudioFrame().
         */
        void onSentenceComplete();
    }

    private volatile AudioDataListener rawListener; // For Wake Word (Always Raw)
    private volatile VadSentenceListener vadSentenceListener; // For VAD endpointing

    public AudioRecorder(int sampleRate, int channels, int frameSizeMs) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.channelConfig = (channels == 1) ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;

        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        this.bufferSize = minBufferSize * 2;

        // Use VAD frame size for optimal processing
        this.frameBytes = VAD_FRAME_BYTES;

        // Initialize pre-roll buffer
        this.preRollBuffer = new byte[PRE_ROLL_FRAMES][VAD_FRAME_BYTES];
    }

    /**
     * Set listener for raw audio (Wake Word detection).
     * Raw audio is dispatched in parallel with VAD processing.
     */
    public void setRawListener(AudioDataListener listener) {
        this.rawListener = listener;
    }

    /**
     * Set VAD sentence listener for client-side endpointing.
     * Audio will be accumulated and only sent when a complete
     * sentence is detected (speech followed by 800ms silence).
     *
     * @param listener Listener for sentence events
     */
    public void setVadSentenceListener(VadSentenceListener listener) {
        this.vadSentenceListener = listener;
    }

    @SuppressLint("MissingPermission")
    public boolean startRecording() {
        if (isRecording.get()) {
            return true;
        }

        // Initialize libfvad
        if (vadDetector == null) {
            vadDetector = new VadDetector();
            if (!vadDetector.initialize(VadDetector.MODE_AGGRESSIVE)) {
                AppLog.w(TAG, "libfvad init failed, using amplitude-only mode");
                useAmplitudeMode = true;
            }
        }

        try {
            // Use VOICE_COMMUNICATION for built-in AEC on devices without separate AEC support
            // This source has echo cancellation built into the audio path on most devices
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                AppLog.e(TAG, "AudioRecord initialization failed");
                return false;
            }

            resetVadState();
            audioRecord.startRecording();
            isRecording.set(true);

            ThreadManager.getInstance().executeAudio(this::recordingLoop);
            return true;

        } catch (Exception e) {
            AppLog.e(TAG, "Start recording error", e);
            stopRecording();
            return false;
        }
    }

    /**
     * Main recording loop with VAD processing.
     */
    private void recordingLoop() {
        byte[] frameBuffer = new byte[VAD_FRAME_BYTES];

        while (isRecording.get() && audioRecord != null) {
            int read = audioRecord.read(frameBuffer, 0, VAD_FRAME_BYTES);

            if (read == VAD_FRAME_BYTES) {
                // 1. Dispatch RAW Audio to Wake Word Engine (parallel path)
                if (rawListener != null) {
                    byte[] rawCopy = new byte[read];
                    System.arraycopy(frameBuffer, 0, rawCopy, 0, read);
                    rawListener.onAudioData(rawCopy);
                }

                // 2. Process through VAD state machine
                if (vadSentenceListener != null) {
                    processVadFrame(frameBuffer);
                }
            } else if (read < 0) {
                AppLog.e(TAG, "AudioRecord read error: " + read);
                break;
            }
        }
    }

    /**
     * Calculate RMS (Root Mean Square) audio level from PCM data.
     */
    private int calculateRmsLevel(byte[] pcmData) {
        long sum = 0;
        int sampleCount = pcmData.length / 2;

        for (int i = 0; i < pcmData.length - 1; i += 2) {
            short sample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
            sum += (long) sample * sample;
        }

        return (int) Math.sqrt(sum / sampleCount);
    }

    /**
     * Process a single VAD frame through the state machine.
     * Uses libfvad primarily, with automatic fallback to amplitude-based for low-level devices.
     */
    private void processVadFrame(byte[] frame) {
        int rmsLevel = calculateRmsLevel(frame);
        boolean isSpeech;

        // Track max RMS to detect low-level audio devices
        if (rmsLevel > maxRmsObserved) {
            maxRmsObserved = rmsLevel;
        }
        frameCountForDetection++;

        // Use both methods and compare
        boolean libfvadSpeech = vadDetector != null && vadDetector.isSpeech(frame, 0, VAD_FRAME_BYTES);
        boolean amplitudeSpeech = rmsLevel > RMS_SPEECH_THRESHOLD;

        // Auto-detect low-level audio: if amplitude detects speech but libfvad doesn't
        if (!useAmplitudeMode && amplitudeSpeech && !libfvadSpeech) {
            useAmplitudeMode = true;
            AppLog.i(TAG, "Low-level audio detected (rms=" + rmsLevel + "), switching to amplitude mode");
        }

        // Choose result based on mode
        isSpeech = useAmplitudeMode ? amplitudeSpeech : libfvadSpeech;

        switch (vadState) {
            case LISTENING:
                handleListeningState(frame, isSpeech);
                break;

            case SPEAKING:
                handleSpeakingState(frame, isSpeech);
                break;

            case SILENCE_CHECK:
                handleSilenceCheckState(frame, isSpeech);
                break;
        }
    }

    /**
     * LISTENING state: Wait for speech, store audio in pre-roll buffer.
     */
    private void handleListeningState(byte[] frame, boolean isSpeech) {
        // Store frame in pre-roll buffer (circular)
        System.arraycopy(frame, 0, preRollBuffer[preRollIndex], 0, VAD_FRAME_BYTES);
        preRollIndex = (preRollIndex + 1) % PRE_ROLL_FRAMES;

        if (isSpeech) {
            speechFrameCount++;

            // Require minimum consecutive speech frames to avoid false triggers
            if (speechFrameCount >= MIN_SPEECH_FRAMES) {
                vadState = VadState.SPEAKING;
                silenceFrameCount = 0;

                if (vadSentenceListener != null) {
                    vadSentenceListener.onSpeechStart();
                    sendPreRollFrames();

                    byte[] frameCopy = new byte[VAD_FRAME_BYTES];
                    System.arraycopy(frame, 0, frameCopy, 0, VAD_FRAME_BYTES);
                    vadSentenceListener.onAudioFrame(frameCopy);
                }
            }
        } else {
            speechFrameCount = 0;
        }
    }

    /**
     * SPEAKING state: Send audio frames in real-time, watch for silence.
     */
    private void handleSpeakingState(byte[] frame, boolean isSpeech) {
        // Send audio frame immediately
        if (vadSentenceListener != null) {
            byte[] frameCopy = new byte[VAD_FRAME_BYTES];
            System.arraycopy(frame, 0, frameCopy, 0, VAD_FRAME_BYTES);
            vadSentenceListener.onAudioFrame(frameCopy);
        }

        if (isSpeech) {
            // Reset silence counter, stay in SPEAKING
            silenceFrameCount = 0;
        } else {
            // Transition to SILENCE_CHECK
            vadState = VadState.SILENCE_CHECK;
            silenceFrameCount = 1;
        }
    }

    /**
     * SILENCE_CHECK state: Check if silence persists long enough for endpointing.
     * Continue sending frames during silence check for natural trailing audio.
     */
    private void handleSilenceCheckState(byte[] frame, boolean isSpeech) {
        // Continue sending frames during silence check (captures trailing audio)
        if (vadSentenceListener != null) {
            byte[] frameCopy = new byte[VAD_FRAME_BYTES];
            System.arraycopy(frame, 0, frameCopy, 0, VAD_FRAME_BYTES);
            vadSentenceListener.onAudioFrame(frameCopy);
        }

        if (isSpeech) {
            // Speech resumed, back to SPEAKING
            vadState = VadState.SPEAKING;
            silenceFrameCount = 0;
        } else {
            silenceFrameCount++;

            // Check if silence threshold exceeded (800ms)
            if (silenceFrameCount >= SILENCE_FRAMES_THRESHOLD) {
                if (vadSentenceListener != null) {
                    vadSentenceListener.onSentenceComplete();
                }
                resetVadState();
            }
        }
    }

    /**
     * Send pre-roll buffer frames to listener.
     * Preserves audio just before speech was detected.
     */
    private void sendPreRollFrames() {
        if (vadSentenceListener == null) return;

        // Send frames in chronological order (oldest first)
        for (int i = 0; i < PRE_ROLL_FRAMES; i++) {
            int idx = (preRollIndex + i) % PRE_ROLL_FRAMES;
            byte[] frameCopy = new byte[VAD_FRAME_BYTES];
            System.arraycopy(preRollBuffer[idx], 0, frameCopy, 0, VAD_FRAME_BYTES);
            vadSentenceListener.onAudioFrame(frameCopy);
        }
    }

    /**
     * Reset VAD state machine to initial state.
     */
    private void resetVadState() {
        vadState = VadState.LISTENING;
        silenceFrameCount = 0;
        speechFrameCount = 0;
        preRollIndex = 0;
        // Keep useAmplitudeMode and maxRmsObserved - persist across resets

        for (byte[] frame : preRollBuffer) {
            java.util.Arrays.fill(frame, (byte) 0);
        }

        if (vadDetector != null) {
            vadDetector.reset();
        }
    }

    public void stopRecording() {
        isRecording.set(false);

        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                AppLog.e(TAG, "Error stopping AudioRecord", e);
            }
            audioRecord = null;
        }

        if (vadDetector != null) {
            vadDetector.release();
            vadDetector = null;
        }

        // Reset detection state for next session
        useAmplitudeMode = false;
        maxRmsObserved = 0;
        frameCountForDetection = 0;
    }

    /**
     * Check if currently recording.
     */
    public boolean isRecording() {
        return isRecording.get();
    }

    /**
     * Get current VAD state (for debugging).
     */
    public String getVadState() {
        return vadState.name();
    }
}
