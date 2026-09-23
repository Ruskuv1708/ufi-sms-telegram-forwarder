package com.ufi.voiceclient;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CallHistoryStore {
    static final String DIRECTION_INCOMING = "INCOMING";
    static final String DIRECTION_OUTGOING = "OUTGOING";
    static final String DIRECTION_UNKNOWN = "UNKNOWN";
    static final String OUTCOME_IN_PROGRESS = "In progress";
    static final String OUTCOME_CONNECTED = "Connected";
    static final String OUTCOME_COMPLETED = "Completed";
    static final String OUTCOME_MISSED = "Missed";
    static final String OUTCOME_DECLINED = "Declined";
    static final String OUTCOME_NOT_CONNECTED = "Not connected";
    static final String OUTCOME_FAILED = "Failed";
    static final String OUTCOME_INTERRUPTED = "Interrupted";

    private static final String PREFS = "ufi_call_history";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 100;

    static final class Entry {
        final String id;
        final String direction;
        final String number;
        final String outcome;
        final long startedAt;
        final long connectedAt;
        final long endedAt;

        Entry(String id, String direction, String number, String outcome,
                long startedAt, long connectedAt, long endedAt) {
            this.id = id;
            this.direction = direction;
            this.number = number;
            this.outcome = outcome;
            this.startedAt = startedAt;
            this.connectedAt = connectedAt;
            this.endedAt = endedAt;
        }
    }

    private CallHistoryStore() {
    }

    static synchronized List<Entry> entries(Context context) {
        return Collections.unmodifiableList(load(context));
    }

    static synchronized String begin(Context context, String direction, String number, long now) {
        ArrayList<Entry> entries = load(context);
        String id = Long.toString(now) + "-" + Integer.toHexString(
                (safe(direction) + safe(number)).hashCode());
        entries.add(0, new Entry(
                id,
                normalizeDirection(direction),
                safe(number),
                OUTCOME_IN_PROGRESS,
                now,
                0L,
                0L));
        save(context, entries);
        return id;
    }

    static synchronized void markConnected(Context context, String id, long now) {
        if (id == null || id.length() == 0) {
            return;
        }
        ArrayList<Entry> entries = load(context);
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (id.equals(entry.id)) {
                long connectedAt = entry.connectedAt > 0L ? entry.connectedAt : now;
                entries.set(index, new Entry(
                        entry.id,
                        entry.direction,
                        entry.number,
                        OUTCOME_CONNECTED,
                        entry.startedAt,
                        connectedAt,
                        entry.endedAt));
                save(context, entries);
                return;
            }
        }
    }

    static synchronized void finish(Context context, String id, String outcome, long now) {
        if (id == null || id.length() == 0) {
            return;
        }
        ArrayList<Entry> entries = load(context);
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (id.equals(entry.id)) {
                entries.set(index, new Entry(
                        entry.id,
                        entry.direction,
                        entry.number,
                        safe(outcome),
                        entry.startedAt,
                        entry.connectedAt,
                        now));
                save(context, entries);
                return;
            }
        }
    }

    static synchronized void record(Context context, String direction, String number,
            String outcome, long now) {
        ArrayList<Entry> entries = load(context);
        entries.add(0, new Entry(
                Long.toString(now) + "-" + Integer.toHexString(
                        (safe(direction) + safe(number) + safe(outcome)).hashCode()),
                normalizeDirection(direction),
                safe(number),
                safe(outcome),
                now,
                0L,
                now));
        save(context, entries);
    }

    static synchronized void markStaleInProgress(Context context, long now) {
        ArrayList<Entry> entries = load(context);
        boolean changed = false;
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (entry.endedAt == 0L
                    && (OUTCOME_IN_PROGRESS.equals(entry.outcome)
                            || OUTCOME_CONNECTED.equals(entry.outcome))) {
                entries.set(index, new Entry(
                        entry.id,
                        entry.direction,
                        entry.number,
                        OUTCOME_INTERRUPTED,
                        entry.startedAt,
                        entry.connectedAt,
                        now));
                changed = true;
            }
        }
        if (changed) {
            save(context, entries);
        }
    }

    private static String normalizeDirection(String direction) {
        if (DIRECTION_INCOMING.equals(direction) || DIRECTION_OUTGOING.equals(direction)) {
            return direction;
        }
        return DIRECTION_UNKNOWN;
    }

    private static ArrayList<Entry> load(Context context) {
        ArrayList<Entry> entries = new ArrayList<Entry>();
        String raw = prefs(context).getString(KEY_ENTRIES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                entries.add(new Entry(
                        item.optString("id", ""),
                        normalizeDirection(item.optString("direction", DIRECTION_UNKNOWN)),
                        item.optString("number", ""),
                        item.optString("outcome", OUTCOME_INTERRUPTED),
                        item.optLong("startedAt", 0L),
                        item.optLong("connectedAt", 0L),
                        item.optLong("endedAt", 0L)));
            }
        } catch (JSONException ignored) {
            // Corrupt local history should not break call monitoring.
        }
        return entries;
    }

    private static void save(Context context, ArrayList<Entry> entries) {
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        JSONArray array = new JSONArray();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            JSONObject item = new JSONObject();
            try {
                item.put("id", entry.id);
                item.put("direction", entry.direction);
                item.put("number", entry.number);
                item.put("outcome", entry.outcome);
                item.put("startedAt", entry.startedAt);
                item.put("connectedAt", entry.connectedAt);
                item.put("endedAt", entry.endedAt);
                array.put(item);
            } catch (JSONException ignored) {
                // Skip malformed rows rather than losing the whole list.
            }
        }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
