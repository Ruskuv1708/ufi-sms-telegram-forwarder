package com.ufi.voiceclient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class SmsMessage {
    static final int TYPE_INBOX = 1;
    static final int TYPE_SENT = 2;

    final long id;
    final String address;
    final String body;
    final long date;
    final int type;
    final boolean read;

    SmsMessage(long id, String address, String body, long date, int type, boolean read) {
        this.id = id;
        this.address = safe(address);
        this.body = safe(body);
        this.date = date;
        this.type = type;
        this.read = read;
    }

    boolean incoming() {
        return type == TYPE_INBOX;
    }

    static List<SmsMessage> parseEnvelope(String raw) throws JSONException {
        ArrayList<SmsMessage> messages = new ArrayList<SmsMessage>();
        JSONArray array = new JSONObject(raw).optJSONArray("messages");
        if (array == null) {
            return messages;
        }
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item != null) {
                messages.add(fromJson(item));
            }
        }
        return messages;
    }

    static SmsMessage parseOne(String raw) throws JSONException {
        return fromJson(new JSONObject(raw));
    }

    JSONObject toJson() throws JSONException {
        JSONObject item = new JSONObject();
        item.put("id", id);
        item.put("address", address);
        item.put("body", body);
        item.put("date", date);
        item.put("type", type);
        item.put("read", read);
        return item;
    }

    SmsMessage asRead() {
        return new SmsMessage(id, address, body, date, type, true);
    }

    private static SmsMessage fromJson(JSONObject item) {
        return new SmsMessage(
                item.optLong("id", 0L),
                item.optString("address", ""),
                item.optString("body", ""),
                item.optLong("date", 0L),
                item.optInt("type", 0),
                item.optBoolean("read", true));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
