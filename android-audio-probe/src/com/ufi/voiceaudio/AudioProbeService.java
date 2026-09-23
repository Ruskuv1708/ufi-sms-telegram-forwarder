package com.ufi.voiceaudio;

import android.app.IntentService;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.Locale;

public final class AudioProbeService extends IntentService {
    private static final String TAG = "UfiVoiceAudio";

    public AudioProbeService() {
        super("UfiVoiceAudioProbe");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if ("com.ufi.voiceaudio.CAPTURE".equals(action)) {
            captureDownlink(Math.max(1, Math.min(10, intent.getIntExtra("seconds", 3))));
        } else if ("com.ufi.voiceaudio.TONE".equals(action)) {
            playUplinkTone();
        } else {
            probeAccess();
        }
    }

    private void probeAccess() {
        File control = new File("/dev/snd/controlC0");
        Log.i(TAG, "probe uid=" + Process.myUid()
                + " controlReadable=" + control.canRead()
                + " controlWritable=" + control.canWrite());
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(control);
            Log.i(TAG, "probe controlOpen=true");
        } catch (Exception error) {
            Log.e(TAG, "probe controlOpen=false error=" + error.getMessage());
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                    // Diagnostic cleanup only.
                }
            }
        }
    }

    private void captureDownlink(int seconds) {
        AudioRecord recorder = null;
        try {
            int minimum = AudioRecord.getMinBufferSize(
                    8000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(minimum, 4096);
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_DOWNLINK,
                    8000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    size);
            short[] samples = new short[size / 2];
            long squareSum = 0;
            long sampleCount = 0;
            int peak = 0;
            int nonZero = 0;
            long deadline = System.currentTimeMillis() + seconds * 1000L;
            recorder.startRecording();
            while (System.currentTimeMillis() < deadline) {
                int count = recorder.read(samples, 0, samples.length);
                if (count <= 0) {
                    continue;
                }
                for (int index = 0; index < count; index++) {
                    int value = samples[index];
                    int absolute = Math.abs(value);
                    peak = Math.max(peak, absolute);
                    if (value != 0) {
                        nonZero++;
                    }
                    squareSum += (long) value * value;
                }
                sampleCount += count;
            }
            recorder.stop();
            double rms = sampleCount == 0 ? 0.0
                    : Math.sqrt((double) squareSum / (double) sampleCount);
            Log.i(TAG, "capture seconds=" + seconds
                    + " samples=" + sampleCount
                    + " nonZero=" + nonZero
                    + " peak=" + peak
                    + " rms=" + String.format(Locale.US, "%.2f", rms));
        } catch (RuntimeException error) {
            Log.e(TAG, "capture failed=" + error.getClass().getSimpleName()
                    + ": " + error.getMessage(), error);
        } finally {
            if (recorder != null) {
                recorder.release();
            }
        }
    }

    private void playUplinkTone() {
        File tone = new File(getFilesDir(), "uplink-test.wav");
        try {
            writeTone(tone, 48000, 750, 1200);
            CommandResult enable = runCommand(
                    "/system/bin/tinymix",
                    "Incall_Music Audio Mixer MultiMedia2",
                    "1");
            CommandResult play = runCommand(
                    "/system/bin/tinyplay",
                    tone.getAbsolutePath(),
                    "-D", "0",
                    "-d", "1",
                    "-p", "240",
                    "-n", "4");
            Log.i(TAG, "tone enableExit=" + enable.exitCode
                    + " playExit=" + play.exitCode
                    + " playOutput=" + oneLine(play.output));
        } catch (Exception error) {
            Log.e(TAG, "tone failed=" + error.getClass().getSimpleName()
                    + ": " + error.getMessage(), error);
        } finally {
            try {
                runCommand(
                        "/system/bin/tinymix",
                        "Incall_Music Audio Mixer MultiMedia2",
                        "0");
            } catch (Exception ignored) {
                // Best-effort route cleanup.
            }
        }
    }

    private static String oneLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static CommandResult runCommand(String... command) throws Exception {
        java.lang.Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8"));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (output.length() < 2048) {
                output.append(line).append('\n');
            }
        }
        int exitCode = process.waitFor();
        return new CommandResult(exitCode, output.toString());
    }

    private static void writeTone(File output, int sampleRate, int frequency, int millis)
            throws Exception {
        int samples = sampleRate * millis / 1000;
        int dataBytes = samples * 2;
        FileOutputStream stream = new FileOutputStream(output);
        try {
            stream.write(new byte[] {'R', 'I', 'F', 'F'});
            writeInt(stream, 36 + dataBytes);
            stream.write(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
            writeInt(stream, 16);
            writeShort(stream, 1);
            writeShort(stream, 1);
            writeInt(stream, sampleRate);
            writeInt(stream, sampleRate * 2);
            writeShort(stream, 2);
            writeShort(stream, 16);
            stream.write(new byte[] {'d', 'a', 't', 'a'});
            writeInt(stream, dataBytes);
            for (int index = 0; index < samples; index++) {
                double angle = 2.0 * Math.PI * frequency * index / sampleRate;
                short value = (short) (Math.sin(angle) * 3500.0);
                writeShort(stream, value);
            }
        } finally {
            stream.close();
        }
    }

    private static void writeInt(FileOutputStream stream, int value) throws Exception {
        stream.write(value & 0xff);
        stream.write((value >> 8) & 0xff);
        stream.write((value >> 16) & 0xff);
        stream.write((value >> 24) & 0xff);
    }

    private static void writeShort(FileOutputStream stream, int value) throws Exception {
        stream.write(value & 0xff);
        stream.write((value >> 8) & 0xff);
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;

        CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
