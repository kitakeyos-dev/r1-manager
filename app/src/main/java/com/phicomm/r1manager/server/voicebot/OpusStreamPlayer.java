package com.phicomm.r1manager.server.voicebot;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.util.ThreadManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class OpusStreamPlayer {
    private static final String TAG = "OpusStreamPlayer";

    private final int sampleRate;
    private final int channels;
    private AudioTrack audioTrack;
    private boolean isPlaying = false;
    private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean shouldRun = new AtomicBoolean(false);

    public OpusStreamPlayer(int sampleRate, int channels, int frameSizeMs) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        // frameSizeMs unused in AudioTrack config but kept for signature consistency

        int channelConfig = (channels == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = minBufferSize * 2;

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build();

        audioTrack = new AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioAttributes.CONTENT_TYPE_SPEECH);
    }

    public void start() {
        if (isPlaying)
            return;

        shouldRun.set(true);
        if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            audioTrack.play();
            isPlaying = true;
        } else {
            AppLog.e(TAG, "AudioTrack not initialized");
            return;
        }

        ThreadManager.getInstance().executeAudio(() -> {
            while (shouldRun.get()) {
                try {
                    byte[] data = audioQueue.take();
                    if (data.length == 0)
                        continue; // End of stream or poison pill

                    int written = 0;
                    while (written < data.length && shouldRun.get()) {
                        int result = audioTrack.write(data, written, data.length - written);
                        if (result < 0) {
                            AppLog.e(TAG, "AudioTrack write error: " + result);
                            break;
                        }
                        written += result;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void play(byte[] pcmData) {
        if (pcmData != null && pcmData.length > 0) {
            audioQueue.offer(pcmData);
        }
    }

    public void waitForPlaybackCompletion() {
        while (!audioQueue.isEmpty() && shouldRun.get()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        shouldRun.set(false);
        isPlaying = false;

        // Wake up the thread if it's blocked on queue
        audioQueue.offer(new byte[0]);

        if (audioTrack != null) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Error stopping AudioTrack", e);
            }
        }
        audioQueue.clear();
    }

    public void release() {
        stop();
        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }
    }
}
