package com.ufi.voiceclient;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SmsStore {
    private static final String PREFS = "ufi_sms_cache";
    private static final String KEY_RAW = "messages";
    private static final String KEY_REVISION = "revision";
    private static final String EMPTY = "{\"messages\":[],\"count\":0}";

    private SmsStore() {
    }

    static synchronized boolean replace(Context context, String raw) throws JSONException {
        SmsMessage.parseEnvelope(raw);
        SharedPreferences preferences = prefs(context);
        String previous = preferences.getString(KEY_RAW, EMPTY);
        if (raw.equals(previous)) {
            return false;
        }
        long revision = preferences.getLong(KEY_REVISION, 0L) + 1L;
        return preferences.edit()
                .putString(KEY_RAW, raw)
                .putLong(KEY_REVISION, revision)
                .commit();
    }

    static synchronized List<SmsMessage> messages(Context context) {
        String raw = prefs(context).getString(KEY_RAW, EMPTY);
        try {
            return Collections.unmodifiableList(SmsMessage.parseEnvelope(raw));
        } catch (JSONException ignored) {
            return Collections.emptyList();
        }
    }

    static synchronized long revision(Context context) {
        return prefs(context).getLong(KEY_REVISION, 0L);
    }

    static synchronized int unreadCount(Context context) {
        int count = 0;
        List<SmsMessage> messages = messages(context);
        for (SmsMessage message : messages) {
            if (message.incoming() && !message.read) {
                count++;
            }
        }
        return count;
    }

    static synchronized SmsMessage newestIncoming(Context context) {
        List<SmsMessage> messages = messages(context);
        for (SmsMessage message : messages) {
            if (message.incoming()) {
                return message;
            }
        }
        return null;
    }

    static synchronized void markRead(Context context, String address) {
        ArrayList<SmsMessage> updated = new ArrayList<SmsMessage>();
        boolean changed = false;
        for (SmsMessage message : messages(context)) {
            if (message.incoming() && message.address.equals(address) && !message.read) {
                updated.add(message.asRead());
                changed = true;
            } else {
                updated.add(message);
            }
        }
        if (changed) {
            save(context, updated);
        }
    }

    static synchronized void addSent(Context context, SmsMessage sent) {
        ArrayList<SmsMessage> updated = new ArrayList<SmsMessage>();
        updated.add(sent);
        for (SmsMessage message : messages(context)) {
            if (message.id != sent.id) {
                updated.add(message);
            }
            if (updated.size() >= 250) {
                break;
            }
        }
        save(context, updated);
    }

    private static void save(Context context, List<SmsMessage> messages) {
        JSONArray array = new JSONArray();
        for (SmsMessage message : messages) {
            try {
                array.put(message.toJson());
            } catch (JSONException ignored) {
                // Keep the rest of the valid cache.
            }
        }
        JSONObject envelope = new JSONObject();
        try {
            envelope.put("messages", array);
            envelope.put("count", array.length());
        } catch (JSONException ignored) {
            return;
        }
        SharedPreferences preferences = prefs(context);
        preferences.edit()
                .putString(KEY_RAW, envelope.toString())
                .putLong(KEY_REVISION, preferences.getLong(KEY_REVISION, 0L) + 1L)
                .commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
