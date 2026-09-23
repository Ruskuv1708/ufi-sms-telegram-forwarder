package com.ufi.voicegateway;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

final class FifoPlayer {
    private static final int SAMPLE_RATE = 48000;
    private final Context context;
    private File fifo;
    private FileOutputStream stream;
    private java.lang.Process player;

    FifoPlayer(Context context) {
        this.context = context;
    }

    void start() throws Exception {
        fifo = new File(context.getFilesDir(), "uplink-audio.fifo");
        if (fifo.exists() && !fifo.delete()) {
            throw new IllegalStateException("cannot replace stale audio pipe");
        }
        makeFifo(fifo.getAbsolutePath());
        setMixer(true);
        try {
            player = new ProcessBuilder(
                    "/system/bin/tinyplay",
                    fifo.getAbsolutePath(),
                    "-D", "0",
                    "-d", "1",
                    "-p", "240",
                    "-n", "4")
                    .redirectErrorStream(true)
                    .start();
            drainInBackground(player);
            stream = new FileOutputStream(fifo);
            writeWaveHeader(stream, SAMPLE_RATE, 0x7ffff000);
            stream.flush();
        } catch (Exception error) {
            close();
            throw error;
        }
    }

    void write(byte[] data, int offset, int length) throws Exception {
        if (stream == null) {
            throw new IllegalStateException("uplink player is not started");
        }
        stream.write(data, offset, length);
    }

    void close() {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
                // Best-effort shutdown.
            }
            stream = null;
        }
        if (player != null) {
            player.destroy();
            player = null;
        }
        try {
            setMixer(false);
        } catch (Exception ignored) {
            // Best-effort route cleanup.
        }
        if (fifo != null && fifo.exists()) {
            fifo.delete();
        }
    }

    private static void makeFifo(String path) throws Exception {
        java.lang.Process process = new ProcessBuilder(
                "/system/bin/busybox", "mkfifo", "-m", "600", path)
                .redirectErrorStream(true)
                .start();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8"));
        while (reader.readLine() != null) {
            // Drain diagnostics so the child process cannot block.
        }
        int result = process.waitFor();
        if (result != 0) {
            throw new IllegalStateException("mkfifo exited with " + result);
        }
    }

    private static void setMixer(boolean enabled) throws Exception {
        java.lang.Process process = new ProcessBuilder(
                "/system/bin/tinymix",
                "Incall_Music Audio Mixer MultiMedia2",
                enabled ? "1" : "0")
                .redirectErrorStream(true)
                .start();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8"));
        while (reader.readLine() != null) {
            // Drain command output to prevent a blocked child process.
        }
        int result = process.waitFor();
        if (result != 0) {
            throw new IllegalStateException("tinymix exited with " + result);
        }
    }

    private static void drainInBackground(final java.lang.Process process) {
        Thread drain = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), "UTF-8"));
                    while (reader.readLine() != null) {
                        // Audio process diagnostics are intentionally discarded.
                    }
                } catch (Exception ignored) {
                    // The stream normally closes when the audio client disconnects.
                }
            }
        }, "UfiVoiceTinyplayOutput");
        drain.setDaemon(true);
        drain.start();
    }

    private static void writeWaveHeader(FileOutputStream output, int sampleRate, int dataBytes)
            throws Exception {
        output.write(new byte[] {'R', 'I', 'F', 'F'});
        writeInt(output, 36 + dataBytes);
        output.write(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        writeInt(output, 16);
        writeShort(output, 1);
        writeShort(output, 1);
        writeInt(output, sampleRate);
        writeInt(output, sampleRate * 2);
        writeShort(output, 2);
        writeShort(output, 16);
        output.write(new byte[] {'d', 'a', 't', 'a'});
        writeInt(output, dataBytes);
    }

    private static void writeInt(FileOutputStream output, int value) throws Exception {
        output.write(value & 0xff);
        output.write((value >> 8) & 0xff);
        output.write((value >> 16) & 0xff);
        output.write((value >> 24) & 0xff);
    }

    private static void writeShort(FileOutputStream output, int value) throws Exception {
        output.write(value & 0xff);
        output.write((value >> 8) & 0xff);
    }
}
