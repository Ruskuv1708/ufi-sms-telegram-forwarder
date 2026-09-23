package com.ufi.voiceclient;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class AudioBridge {
    interface Listener {
        void onAudioReady();
        void onAudioError(String message);
    }

    private final String host;
    private final String token;
    private final Listener listener;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean failureReported = new AtomicBoolean(false);
    private final AtomicInteger readyStreams = new AtomicInteger(0);
    private volatile GatewayClient.Session downlinkSession;
    private volatile GatewayClient.Session uplinkSession;
    private volatile AudioTrack audioTrack;
    private volatile AudioRecord audioRecord;
    private volatile AcousticEchoCanceler echoCanceler;

    AudioBridge(String host, String token, Listener listener) {
        this.host = host;
        this.token = token;
        this.listener = listener;
    }

    void start() {
        Thread downlink = new Thread(new Runnable() {
            @Override
            public void run() {
                runDownlink();
            }
        }, "UfiCallDownlink");
        Thread uplink = new Thread(new Runnable() {
            @Override
            public void run() {
                runUplink();
            }
        }, "UfiCallUplink");
        downlink.start();
        uplink.start();
    }

    void stop() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        GatewayClient.Session down = downlinkSession;
        GatewayClient.Session up = uplinkSession;
        if (down != null) {
            down.close();
        }
        if (up != null) {
            up.close();
        }
        AudioRecord record = audioRecord;
        if (record != null) {
            try {
                record.stop();
            } catch (RuntimeException ignored) {
                // The socket may close while AudioRecord is stopping.
            }
        }
        AudioTrack track = audioTrack;
        if (track != null) {
            try {
                track.stop();
            } catch (RuntimeException ignored) {
                // The socket may close while AudioTrack is stopping.
            }
        }
    }

    private void runDownlink() {
        AudioTrack track = null;
        GatewayClient.Session session = null;
        try {
            session = GatewayClient.open(host, token, GatewayClient.DOWNLINK_PORT);
            downlinkSession = session;
            String response = GatewayClient.readLine(session.input, 256);
            if (!response.startsWith("OK ")) {
                throw new IOException(response);
            }
            session.socket.setSoTimeout(0);
            int minimum = AudioTrack.getMinBufferSize(
                    8000,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) {
                throw new IOException("Tablet cannot play 8 kHz call audio");
            }
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(8000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            track = new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(Math.max(4096, minimum * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                throw new IOException("Tablet call speaker could not initialize");
            }
            audioTrack = track;
            track.play();
            streamReady();
            byte[] buffer = new byte[2048];
            while (!stopping.get()) {
                int count = session.input.read(buffer);
                if (count < 0) {
                    break;
                }
                int offset = 0;
                while (offset < count && !stopping.get()) {
                    int written = track.write(buffer, offset, count - offset);
                    if (written < 0) {
                        throw new IOException("Tablet call speaker stopped");
                    }
                    offset += written;
                }
            }
        } catch (Exception error) {
            reportFailure(error);
        } finally {
            if (track != null) {
                try {
                    track.stop();
                } catch (RuntimeException ignored) {
                    // Best-effort shutdown.
                }
                track.release();
            }
            if (session != null) {
                session.close();
            }
            audioTrack = null;
            downlinkSession = null;
        }
    }

    private void runUplink() {
        AudioRecord record = null;
        AcousticEchoCanceler echo = null;
        GatewayClient.Session session = null;
        try {
            session = GatewayClient.open(host, token, GatewayClient.UPLINK_PORT);
            uplinkSession = session;
            String response = GatewayClient.readLine(session.input, 256);
            if (!response.startsWith("OK ")) {
                throw new IOException(response);
            }
            session.socket.setSoTimeout(0);
            int minimum = AudioRecord.getMinBufferSize(
                    48000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) {
                throw new IOException("Tablet cannot record 48 kHz call audio");
            }
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(48000)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();
            record = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(Math.max(7680, minimum * 2))
                    .build();
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IOException("Tablet microphone could not initialize");
            }
            audioRecord = record;
            if (AcousticEchoCanceler.isAvailable()) {
                echo = AcousticEchoCanceler.create(record.getAudioSessionId());
                if (echo != null) {
                    echo.setEnabled(true);
                    echoCanceler = echo;
                }
            }
            record.startRecording();
            streamReady();
            byte[] buffer = new byte[3840];
            while (!stopping.get()) {
                int count = record.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                if (count < 0) {
                    throw new IOException("Tablet microphone stopped");
                }
                if (count > 0) {
                    session.output.write(buffer, 0, count);
                }
            }
        } catch (Exception error) {
            reportFailure(error);
        } finally {
            if (echo != null) {
                echo.release();
            }
            if (record != null) {
                try {
                    record.stop();
                } catch (RuntimeException ignored) {
                    // Best-effort shutdown.
                }
                record.release();
            }
            if (session != null) {
                session.close();
            }
            echoCanceler = null;
            audioRecord = null;
            uplinkSession = null;
        }
    }

    private void streamReady() {
        if (readyStreams.incrementAndGet() == 2 && !stopping.get()) {
            listener.onAudioReady();
        }
    }

    private void reportFailure(Exception error) {
        if (stopping.get() || !failureReported.compareAndSet(false, true)) {
            return;
        }
        String message = error.getMessage();
        if (message == null || message.length() == 0) {
            message = error.getClass().getSimpleName();
        }
        listener.onAudioError(message);
        stop();
    }
}
