package com.ufi.voicegateway;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VoiceGatewayService extends Service {
    private static final String TAG = "UfiVoiceGateway";
    private static final String BIND_ADDRESS = "192.168.100.1";
    private static final int CONTROL_PORT = 8765;
    private static final int DOWNLINK_PORT = 8766;
    private static final int UPLINK_PORT = 8767;
    private static final long DIALING_WINDOW_MS = 30000L;
    private static final String DIRECTION_INCOMING = "INCOMING";
    private static final String DIRECTION_OUTGOING = "OUTGOING";
    private static final String DIRECTION_UNKNOWN = "UNKNOWN";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static volatile boolean running;
    private volatile boolean stopping;
    private volatile int callState = TelephonyManager.CALL_STATE_IDLE;
    private volatile String callNumber = "";
    private volatile String callDirection = "";
    private volatile long dialRequestedAt;
    private volatile Socket downlinkClient;
    private volatile Socket uplinkClient;
    private final AtomicBoolean downlinkBusy = new AtomicBoolean(false);
    private final AtomicBoolean uplinkBusy = new AtomicBoolean(false);
    private TelephonyManager telephony;
    private PhoneStateListener phoneListener;
    private ServerSocket controlServer;
    private ServerSocket downlinkServer;
    private ServerSocket uplinkServer;

    static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!GatewayConfig.enabled(this)) {
            stopSelf();
            return;
        }
        running = true;
        stopping = false;
        telephony = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        callState = telephony.getCallState();
        phoneListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String number) {
                callState = state;
                if (state == TelephonyManager.CALL_STATE_RINGING) {
                    callNumber = number == null ? "" : number;
                    callDirection = DIRECTION_INCOMING;
                    dialRequestedAt = 0L;
                } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    if (callDirection.length() == 0) {
                        callDirection = DIRECTION_UNKNOWN;
                    }
                } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                    callNumber = "";
                    callDirection = "";
                    dialRequestedAt = 0L;
                    closeAudioClients();
                }
            }
        };
        telephony.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
        startServer("Control", CONTROL_PORT, new SocketHandler() {
            @Override
            public void handle(Socket socket) throws Exception {
                handleControl(socket);
            }
        });
        startServer("Downlink", DOWNLINK_PORT, new SocketHandler() {
            @Override
            public void handle(Socket socket) throws Exception {
                handleDownlink(socket);
            }
        });
        startServer("Uplink", UPLINK_PORT, new SocketHandler() {
            @Override
            public void handle(Socket socket) throws Exception {
                handleUplink(socket);
            }
        });
        startNetworkModeGuard();
        Log.i(TAG, "LAN gateway starting on " + BIND_ADDRESS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopping = true;
        running = false;
        closeServer(controlServer);
        closeServer(downlinkServer);
        closeServer(uplinkServer);
        closeAudioClients();
        if (telephony != null && phoneListener != null) {
            telephony.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
        }
        super.onDestroy();
    }

    private void startServer(final String name, final int port, final SocketHandler handler) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopping) {
                    ServerSocket server = null;
                    try {
                        server = new ServerSocket();
                        server.setReuseAddress(true);
                        server.bind(new InetSocketAddress(
                                InetAddress.getByName(BIND_ADDRESS), port));
                        assignServer(port, server);
                        Log.i(TAG, name + " server ready on port " + port);
                        while (!stopping) {
                            final Socket socket = server.accept();
                            if (!isLocalClient(socket)) {
                                closeSocket(socket);
                                continue;
                            }
                            Thread client = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        handler.handle(socket);
                                    } catch (Exception error) {
                                        Log.w(TAG, "client session ended: "
                                                + error.getClass().getSimpleName());
                                    } finally {
                                        closeSocket(socket);
                                    }
                                }
                            }, "UfiVoice" + name + "Client");
                            client.setDaemon(true);
                            client.start();
                        }
                    } catch (Exception error) {
                        if (!stopping) {
                            Log.w(TAG, name + " bind retry: "
                                    + error.getClass().getSimpleName());
                            sleepQuietly(3000);
                        }
                    } finally {
                        closeServer(server);
                    }
                }
            }
        }, "UfiVoice" + name + "Server");
        thread.setDaemon(true);
        thread.start();
    }

    private void assignServer(int port, ServerSocket server) {
        if (port == CONTROL_PORT) {
            controlServer = server;
        } else if (port == DOWNLINK_PORT) {
            downlinkServer = server;
        } else if (port == UPLINK_PORT) {
            uplinkServer = server;
        }
    }

    private void handleControl(Socket socket) throws Exception {
        socket.setSoTimeout(5000);
        InputStream input = socket.getInputStream();
        OutputStream output = socket.getOutputStream();
        if (!authenticate(input)) {
            writeLine(output, "ERR AUTH");
            return;
        }
        String rawCommand = readLine(input, 16384).trim();
        String command = rawCommand.toUpperCase(Locale.US);
        if ("PING".equals(command)) {
            writeLine(output, "OK PONG");
        } else if ("STATUS".equals(command)) {
            writeLine(output, "OK " + statusJson());
        } else if ("SMS_LIST".equals(command)) {
            handleSmsList(output);
        } else if (command.startsWith("SMS_SEND ")) {
            handleSmsSend(rawCommand, output);
        } else if (command.startsWith("SMS_READ ")) {
            handleSmsRead(rawCommand, output);
        } else if (command.startsWith("DIAL")) {
            handleDial(rawCommand, output);
        } else if ("ANSWER".equals(command)) {
            if (callState != TelephonyManager.CALL_STATE_RINGING) {
                writeLine(output, "ERR NOT_RINGING");
            } else {
                writeLine(output, CallController.answer() ? "OK" : "ERR ANSWER_FAILED");
            }
        } else if ("HANGUP".equals(command)) {
            writeLine(output, CallController.hangup() ? "OK" : "ERR HANGUP_FAILED");
        } else {
            writeLine(output, "ERR COMMAND");
        }
    }

    private void handleSmsList(OutputStream output) throws IOException {
        try {
            writeLine(output, "OK " + SmsController.listJson(this, 250));
        } catch (SecurityException error) {
            writeLine(output, "ERR SMS_PERMISSION");
        } catch (Exception error) {
            Log.w(TAG, "SMS list failed: " + error.getClass().getSimpleName());
            writeLine(output, "ERR SMS_READ_FAILED");
        }
    }

    private void handleSmsSend(String rawCommand, OutputStream output) throws IOException {
        String payload = rawCommand.substring("SMS_SEND ".length()).trim();
        int separator = payload.indexOf(' ');
        if (separator <= 0 || separator >= payload.length() - 1) {
            writeLine(output, "ERR BAD_SMS");
            return;
        }
        try {
            String message = SmsController.sendEncoded(
                    this,
                    payload.substring(0, separator),
                    payload.substring(separator + 1));
            writeLine(output, "OK " + message);
        } catch (IllegalArgumentException error) {
            writeLine(output, "ERR BAD_SMS");
        } catch (SecurityException error) {
            writeLine(output, "ERR SMS_PERMISSION");
        } catch (Exception error) {
            Log.w(TAG, "SMS send failed: " + error.getClass().getSimpleName());
            writeLine(output, "ERR SMS_SEND_FAILED");
        }
    }

    private void handleSmsRead(String rawCommand, OutputStream output) throws IOException {
        String encodedAddress = rawCommand.substring("SMS_READ ".length()).trim();
        try {
            int updated = SmsController.markReadEncoded(this, encodedAddress);
            writeLine(output, "OK " + updated);
        } catch (IllegalArgumentException error) {
            writeLine(output, "ERR BAD_SMS");
        } catch (SecurityException error) {
            writeLine(output, "ERR SMS_PERMISSION");
        } catch (Exception error) {
            Log.w(TAG, "SMS read update failed: " + error.getClass().getSimpleName());
            writeLine(output, "ERR SMS_READ_FAILED");
        }
    }

    private void handleDial(String rawCommand, OutputStream output) throws IOException {
        if (callState != TelephonyManager.CALL_STATE_IDLE) {
            writeLine(output, "ERR CALL_IN_PROGRESS");
            return;
        }
        String number = "";
        if (rawCommand.length() > 4) {
            number = normalizeDialNumber(rawCommand.substring(4));
        }
        if (number.length() == 0) {
            writeLine(output, "ERR BAD_NUMBER");
            return;
        }
        if (isBlockedDialNumber(number)) {
            writeLine(output, "ERR NUMBER_BLOCKED");
            return;
        }
        try {
            callNumber = number;
            callDirection = DIRECTION_OUTGOING;
            dialRequestedAt = System.currentTimeMillis();
            writeLine(output, CallController.dial(this, number) ? "OK" : "ERR DIAL_FAILED");
        } catch (Exception error) {
            callNumber = "";
            callDirection = "";
            dialRequestedAt = 0L;
            writeLine(output, "ERR DIAL_FAILED");
        }
    }

    private void handleDownlink(Socket socket) throws Exception {
        if (!downlinkBusy.compareAndSet(false, true)) {
            writeLine(socket.getOutputStream(), "ERR BUSY");
            return;
        }
        AudioRecord recorder = null;
        PowerManager.WakeLock wakeLock = null;
        downlinkClient = socket;
        try {
            socket.setSoTimeout(5000);
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            if (!authenticate(input)) {
                writeLine(output, "ERR AUTH");
                return;
            }
            if (callState != TelephonyManager.CALL_STATE_OFFHOOK) {
                writeLine(output, "ERR NO_CALL");
                return;
            }
            int minimum = AudioRecord.getMinBufferSize(
                    8000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(2048, minimum * 2);
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_DOWNLINK,
                    8000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    size * 2);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                writeLine(output, "ERR AUDIO_INIT");
                return;
            }
            wakeLock = acquireWakeLock("Downlink");
            writeLine(output, "OK 8000 1 S16LE");
            socket.setSoTimeout(0);
            byte[] buffer = new byte[size];
            recorder.startRecording();
            while (!stopping && callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                int count = recorder.read(buffer, 0, buffer.length);
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
        } finally {
            if (recorder != null) {
                try {
                    recorder.stop();
                } catch (Exception ignored) {
                    // It may already have stopped with the call.
                }
                recorder.release();
            }
            releaseWakeLock(wakeLock);
            downlinkClient = null;
            downlinkBusy.set(false);
        }
    }

    private void handleUplink(Socket socket) throws Exception {
        if (!uplinkBusy.compareAndSet(false, true)) {
            writeLine(socket.getOutputStream(), "ERR BUSY");
            return;
        }
        FifoPlayer player = null;
        PowerManager.WakeLock wakeLock = null;
        uplinkClient = socket;
        try {
            socket.setSoTimeout(5000);
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            if (!authenticate(input)) {
                writeLine(output, "ERR AUTH");
                return;
            }
            if (callState != TelephonyManager.CALL_STATE_OFFHOOK) {
                writeLine(output, "ERR NO_CALL");
                return;
            }
            player = new FifoPlayer(this);
            player.start();
            wakeLock = acquireWakeLock("Uplink");
            writeLine(output, "OK 48000 1 S16LE");
            socket.setSoTimeout(0);
            byte[] buffer = new byte[3840];
            while (!stopping && callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                int count = input.read(buffer);
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    player.write(buffer, 0, count);
                }
            }
        } finally {
            if (player != null) {
                player.close();
            }
            releaseWakeLock(wakeLock);
            uplinkClient = null;
            uplinkBusy.set(false);
        }
    }

    private boolean authenticate(InputStream input) throws Exception {
        String line = readLine(input, 160);
        if (!line.startsWith("TOKEN ")) {
            return false;
        }
        byte[] supplied = line.substring(6).trim().getBytes(UTF8);
        byte[] expected = GatewayConfig.token(this).getBytes(UTF8);
        return MessageDigest.isEqual(supplied, expected);
    }

    private String statusJson() {
        String state;
        String number = callNumber;
        String direction = callDirection;
        if (callState == TelephonyManager.CALL_STATE_RINGING) {
            state = "RINGING";
            direction = DIRECTION_INCOMING;
        } else if (callState == TelephonyManager.CALL_STATE_OFFHOOK) {
            state = "ACTIVE";
        } else {
            long requestedAt = dialRequestedAt;
            if (DIRECTION_OUTGOING.equals(direction)
                    && requestedAt > 0L
                    && System.currentTimeMillis() - requestedAt < DIALING_WINDOW_MS) {
                state = "DIALING";
            } else {
                state = "IDLE";
                number = "";
                direction = "";
                callNumber = "";
                callDirection = "";
                dialRequestedAt = 0L;
            }
        }
        int preferredMode = Settings.Global.getInt(
                getContentResolver(), "preferred_network_mode", -1);
        int modeRecoveries = Settings.Global.getInt(
                getContentResolver(), "ufi_voice_network_mode_recoveries", 0);
        long lastModeRecovery = Settings.Global.getLong(
                getContentResolver(), "ufi_voice_network_mode_last_recovery", 0L);
        long lastModeApplied = Settings.Global.getLong(
                getContentResolver(), "ufi_voice_network_mode_last_applied", 0L);
        return "{\"state\":\"" + state
                + "\",\"network\":\"" + networkName(telephony.getNetworkType())
                + "\",\"caller\":\"" + jsonEscape(number)
                + "\",\"direction\":\"" + jsonEscape(direction)
                + "\",\"downlinkBusy\":" + downlinkBusy.get()
                + ",\"uplinkBusy\":" + uplinkBusy.get()
                + ",\"callReady\":" + (preferredMode == 9)
                + ",\"preferredNetworkMode\":" + preferredMode
                + ",\"modeRecoveries\":" + modeRecoveries
                + ",\"lastModeRecoveryAt\":" + lastModeRecovery
                + ",\"lastModeAppliedAt\":" + lastModeApplied
                + "}";
    }

    private static String normalizeDialNumber(String rawNumber) {
        StringBuilder normalized = new StringBuilder();
        String value = rawNumber == null ? "" : rawNumber.trim();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= '0' && character <= '9') {
                normalized.append(character);
            } else if (character == '+' && normalized.length() == 0) {
                normalized.append(character);
            } else if (character == ' ' || character == '-' || character == '('
                    || character == ')' || character == '.') {
                continue;
            } else {
                return "";
            }
        }
        int digitCount = normalized.length();
        if (digitCount > 0 && normalized.charAt(0) == '+') {
            digitCount--;
        }
        if (digitCount < 6 || digitCount > 20) {
            return "";
        }
        return normalized.toString();
    }

    @SuppressWarnings("deprecation")
    private static boolean isBlockedDialNumber(String number) {
        return PhoneNumberUtils.isEmergencyNumber(number);
    }

    private static String networkName(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "HSPA+";
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return "HSPA";
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return "HSUPA";
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE";
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS";
            default:
                return "UNKNOWN";
        }
    }

    private void startNetworkModeGuard() {
        try {
            Intent apply = new Intent("com.ufi.networkguard.APPLY");
            apply.setClassName(
                    "com.ufi.networkguard",
                    "com.ufi.networkguard.NetworkGuardService");
            startService(apply);
        } catch (Exception error) {
            Log.w(TAG, "network guard unavailable: "
                    + error.getClass().getSimpleName());
        }
    }

    private PowerManager.WakeLock acquireWakeLock(String suffix) {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = manager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "UfiVoiceGateway:" + suffix);
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
        return wakeLock;
    }

    private static void releaseWakeLock(PowerManager.WakeLock wakeLock) {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void closeAudioClients() {
        closeSocket(downlinkClient);
        closeSocket(uplinkClient);
    }

    private static boolean isLocalClient(Socket socket) {
        byte[] address = socket.getInetAddress().getAddress();
        return address.length == 4
                && (address[0] & 0xff) == 192
                && (address[1] & 0xff) == 168
                && (address[2] & 0xff) == 100;
    }

    private static String readLine(InputStream input, int maximum) throws IOException {
        byte[] buffer = new byte[maximum];
        int length = 0;
        while (length < buffer.length) {
            int value = input.read();
            if (value < 0) {
                break;
            }
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                buffer[length++] = (byte) value;
            }
        }
        if (length == buffer.length) {
            throw new IOException("line too long");
        }
        return new String(buffer, 0, length, UTF8);
    }

    private static void writeLine(OutputStream output, String line) throws IOException {
        output.write((line + "\n").getBytes(UTF8));
        output.flush();
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeSocket(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    private static void closeServer(ServerSocket server) {
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    private interface SocketHandler {
        void handle(Socket socket) throws Exception;
    }
}
