package com.ufi.voicegateway;

import android.content.Context;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Base64;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

final class SmsController {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_BODY_LENGTH = 2000;
    private static final long ICC_SYNC_INTERVAL_MS = 60L * 1000L;
    private static volatile long lastIccSync;

    private SmsController() {
    }

    static String listJson(Context context, int requestedLimit) {
        SmsDb database = new SmsDb(context);
        try {
            importIccMessages(database);
            List<SmsDb.Row> rows = database.latest(requestedLimit);
            StringBuilder json = new StringBuilder();
            json.append("{\"messages\":[");
            for (int index = 0; index < rows.size(); index++) {
                if (index > 0) {
                    json.append(',');
                }
                SmsDb.Row row = rows.get(index);
                json.append(messageJson(
                        row.id,
                        row.address,
                        row.body,
                        row.date,
                        row.type,
                        row.read));
            }
            json.append("],\"count\":").append(rows.size()).append('}');
            return json.toString();
        } finally {
            database.close();
        }
    }

    static String sendEncoded(Context context, String encodedAddress, String encodedBody) {
        String address = normalizeRecipient(decode(encodedAddress));
        String body = decode(encodedBody);
        if (address.length() == 0 || body.trim().length() == 0
                || body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("invalid SMS");
        }

        SmsManager manager = SmsManager.getDefault();
        ArrayList<String> parts = manager.divideMessage(body);
        if (parts.size() > 1) {
            manager.sendMultipartTextMessage(address, null, parts, null, null);
        } else {
            manager.sendTextMessage(address, null, body, null, null);
        }

        long now = System.currentTimeMillis();
        SmsDb database = new SmsDb(context);
        long id;
        try {
            id = database.insertSent(address, now, body);
        } finally {
            database.close();
        }
        return messageJson(id, address, body, now, SmsDb.TYPE_SENT, true);
    }

    static int markReadEncoded(Context context, String encodedAddress) {
        String address = decode(encodedAddress).trim();
        if (address.length() == 0 || address.length() > 80) {
            throw new IllegalArgumentException("invalid address");
        }
        SmsDb database = new SmsDb(context);
        try {
            return database.markRead(address);
        } finally {
            database.close();
        }
    }

    private static void importIccMessages(SmsDb database) {
        long now = System.currentTimeMillis();
        if (now - lastIccSync < ICC_SYNC_INTERVAL_MS) {
            return;
        }
        lastIccSync = now;
        try {
            Method method = SmsManager.class.getMethod("getAllMessagesFromIcc");
            Object result = method.invoke(SmsManager.getDefault());
            if (!(result instanceof List<?>)) {
                return;
            }
            for (Object item : (List<?>) result) {
                if (!(item instanceof SmsMessage)) {
                    continue;
                }
                SmsMessage message = (SmsMessage) item;
                int status = message.getStatusOnIcc();
                int type = status == SmsManager.STATUS_ON_ICC_SENT
                        || status == SmsManager.STATUS_ON_ICC_UNSENT
                        ? SmsDb.TYPE_SENT : SmsDb.TYPE_INBOX;
                String address = message.getOriginatingAddress();
                if (address == null || address.length() == 0) {
                    address = message.getDisplayOriginatingAddress();
                }
                if (address == null || address.length() == 0) {
                    continue;
                }
                String body = message.getMessageBody();
                if (body == null) {
                    body = message.getDisplayMessageBody();
                }
                boolean read = status != SmsManager.STATUS_ON_ICC_UNREAD;
                database.insertFromIcc(
                        address,
                        message.getTimestampMillis(),
                        body,
                        type,
                        read);
            }
        } catch (Exception ignored) {
            // Some firmware builds omit the hidden ICC-list API. Broadcast
            // capture still stores every new incoming message reliably.
        }
    }

    private static String decode(String encoded) {
        if (encoded == null || encoded.length() == 0 || encoded.length() > 12000) {
            throw new IllegalArgumentException("invalid encoded value");
        }
        try {
            byte[] bytes = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP);
            return new String(bytes, UTF8);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("invalid base64");
        }
    }

    private static String normalizeRecipient(String rawAddress) {
        String value = rawAddress == null ? "" : rawAddress.trim();
        StringBuilder normalized = new StringBuilder();
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
        int digits = normalized.length();
        if (digits > 0 && normalized.charAt(0) == '+') {
            digits--;
        }
        return digits >= 3 && digits <= 20 ? normalized.toString() : "";
    }

    private static String messageJson(long id, String address, String body, long date,
            int type, boolean read) {
        return "{\"id\":" + id
                + ",\"address\":\"" + jsonEscape(address)
                + "\",\"body\":\"" + jsonEscape(body)
                + "\",\"date\":" + date
                + ",\"type\":" + type
                + ",\"read\":" + read
                + "}";
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        String hex = Integer.toHexString(character);
                        escaped.append("\\u");
                        for (int padding = hex.length(); padding < 4; padding++) {
                            escaped.append('0');
                        }
                        escaped.append(hex);
                    } else {
                        escaped.append(character);
                    }
                    break;
            }
        }
        return escaped.toString();
    }
}
