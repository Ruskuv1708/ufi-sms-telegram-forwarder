package com.ufi.voiceclient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class GatewayClient {
    static final int CONTROL_PORT = 8765;
    static final int DOWNLINK_PORT = 8766;
    static final int UPLINK_PORT = 8767;

    static final class Session {
        final Socket socket;
        final InputStream input;
        final OutputStream output;

        Session(Socket socket) throws IOException {
            this.socket = socket;
            input = socket.getInputStream();
            output = socket.getOutputStream();
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best-effort shutdown.
            }
        }
    }

    static final class Status {
        final String state;
        final String network;
        final String caller;
        final String direction;
        final boolean callReady;
        final int preferredNetworkMode;
        final int modeRecoveries;

        Status(String state, String network, String caller, String direction, boolean callReady,
                int preferredNetworkMode, int modeRecoveries) {
            this.state = state;
            this.network = network;
            this.caller = caller;
            this.direction = direction;
            this.callReady = callReady;
            this.preferredNetworkMode = preferredNetworkMode;
            this.modeRecoveries = modeRecoveries;
        }
    }

    private GatewayClient() {
    }

    static String control(String host, String token, String command) throws Exception {
        Session session = open(host, token, CONTROL_PORT);
        try {
            session.output.write((command + "\n").getBytes(StandardCharsets.US_ASCII));
            session.output.flush();
            String response = readLine(session.input, 8192);
            if ("OK".equals(response)) {
                return "";
            }
            if (response.startsWith("OK ")) {
                return response.substring(3);
            }
            throw new IOException(response.length() == 0 ? "Gateway closed the connection" : response);
        } finally {
            session.close();
        }
    }

    static Status status(String host, String token) throws Exception {
        JSONObject json = new JSONObject(control(host, token, "STATUS"));
        return new Status(
                json.optString("state", "UNKNOWN"),
                json.optString("network", "UNKNOWN"),
                json.optString("caller", ""),
                json.optString("direction", ""),
                json.optBoolean("callReady", true),
                json.optInt("preferredNetworkMode", 9),
                json.optInt("modeRecoveries", 0));
    }

    static Session open(String host, String token, int port) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 4000);
        socket.setSoTimeout(5000);
        socket.setTcpNoDelay(true);
        Session session = new Session(socket);
        session.output.write(("TOKEN " + token + "\n").getBytes(StandardCharsets.US_ASCII));
        session.output.flush();
        return session;
    }

    static String readLine(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (buffer.size() < maximum) {
            int value = input.read();
            if (value < 0 || value == '\n') {
                break;
            }
            if (value != '\r') {
                buffer.write(value);
            }
        }
        if (buffer.size() >= maximum) {
            throw new IOException("Gateway response was too long");
        }
        return buffer.toString("UTF-8");
    }
}
