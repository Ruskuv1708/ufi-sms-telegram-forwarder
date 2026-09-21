package com.ufi.smsforwarder;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

final class TelegramClient {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String CURL = "/system/bin/curl";
    private final Context context;

    TelegramClient(Context context) {
        this.context = context.getApplicationContext();
    }

    void send(AppConfig.Snapshot config, String text) throws Exception {
        if (!config.isConfigured()) {
            throw new IllegalStateException("Telegram is not configured");
        }

        File caFile = ensureCaFile();
        File requestFile = File.createTempFile("telegram-", ".form", context.getCacheDir());
        try {
            writeFile(requestFile, form(config.chatId, trimForTelegram(text)).getBytes(UTF_8));

            List<String> command = new ArrayList<String>();
            command.add(CURL);
            command.add("--config");
            command.add("-");
            command.add("--silent");
            command.add("--show-error");
            command.add("--connect-timeout");
            command.add("20");
            command.add("--max-time");
            command.add("35");
            command.add("--request");
            command.add("POST");
            command.add("--header");
            command.add("Content-Type: application/x-www-form-urlencoded; charset=UTF-8");
            command.add("--data-binary");
            command.add("@" + requestFile.getAbsolutePath());
            command.add("--write-out");
            command.add("\\n%{http_code}");

            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            OutputStream curlConfig = process.getOutputStream();
            try {
                String endpoint = "https://api.telegram.org/bot" + config.token + "/sendMessage";
                String privateConfig = "url = \"" + endpoint + "\"\n"
                        + "cacert = \"" + caFile.getAbsolutePath() + "\"\n"
                        + "user-agent = \"UFI-SMS-Forwarder/1.0\"\n";
                curlConfig.write(privateConfig.getBytes(UTF_8));
            } finally {
                curlConfig.close();
            }

            String output = readLimited(process.getInputStream(), 65536);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("curl exited with code " + exitCode);
            }

            int split = output.lastIndexOf('\n');
            if (split < 0) {
                throw new IOException("Telegram returned an invalid response");
            }
            String response = output.substring(0, split).trim();
            String statusText = output.substring(split + 1).trim();
            int status;
            try {
                status = Integer.parseInt(statusText);
            } catch (NumberFormatException error) {
                throw new IOException("Telegram returned an invalid HTTP status");
            }
            if (status < 200 || status >= 300) {
                throw new IOException("Telegram HTTP " + status);
            }
            JSONObject json = new JSONObject(response);
            if (!json.optBoolean("ok", false)) {
                throw new IOException("Telegram rejected the request");
            }
        } finally {
            if (!requestFile.delete()) {
                requestFile.deleteOnExit();
            }
        }
    }

    private File ensureCaFile() throws IOException {
        File destination = new File(context.getFilesDir(), "godaddy-root-g2.pem");
        if (destination.isFile() && destination.length() > 0L) {
            return destination;
        }
        InputStream input = context.getAssets().open("godaddy-root-g2.pem");
        try {
            FileOutputStream output = new FileOutputStream(destination, false);
            try {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
        return destination;
    }

    private static void writeFile(File file, byte[] bytes) throws IOException {
        FileOutputStream output = new FileOutputStream(file, false);
        try {
            output.write(bytes);
            output.getFD().sync();
        } finally {
            output.close();
        }
    }

    private static String form(String chatId, String text) throws Exception {
        return "chat_id=" + encode(chatId)
                + "&text=" + encode(text)
                + "&protect_content=true"
                + "&link_preview_options=" + encode("{\"is_disabled\":true}");
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String trimForTelegram(String text) {
        String safe = text == null ? "" : text;
        if (safe.length() <= 4000) {
            return safe;
        }
        int end = 3990;
        if (end > 0 && Character.isHighSurrogate(safe.charAt(end - 1))) {
            end--;
        }
        return safe.substring(0, end) + "\n…[truncated]";
    }

    private static String readLimited(InputStream input, int limit) throws IOException {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(chunk)) != -1) {
                int remaining = limit - total;
                if (remaining <= 0) {
                    break;
                }
                int accepted = Math.min(read, remaining);
                buffer.write(chunk, 0, accepted);
                total += accepted;
            }
            return new String(buffer.toByteArray(), UTF_8);
        } finally {
            input.close();
        }
    }
}
