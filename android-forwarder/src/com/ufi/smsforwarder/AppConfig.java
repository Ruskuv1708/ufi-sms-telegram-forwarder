package com.ufi.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

final class AppConfig {
    private static final String PREFS = "ufi_sms_forwarder";
    private static final String KEY_TOKEN = "bot_token";
    private static final String KEY_CHAT_ID = "chat_id";
    private static final String KEY_LAST_SCAN = "last_scan_ms";
    private static final String KEY_LAST_SUCCESS = "last_success_ms";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_ALARM_SET = "alarm_set";
    private static final String KEY_ENABLED = "enabled";

    static final class Snapshot {
        final String token;
        final String chatId;

        Snapshot(String token, String chatId) {
            this.token = token;
            this.chatId = chatId;
        }

        boolean isConfigured() {
            return token != null && token.length() > 0 && chatId != null && chatId.length() > 0;
        }
    }

    private AppConfig() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Snapshot load(Context context) {
        SharedPreferences preferences = prefs(context);
        return new Snapshot(
                preferences.getString(KEY_TOKEN, ""),
                preferences.getString(KEY_CHAT_ID, "")
        );
    }

    static boolean save(Context context, String token, String chatId) {
        long now = System.currentTimeMillis();
        return prefs(context).edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_CHAT_ID, chatId)
                .putBoolean(KEY_ENABLED, true)
                .putLong(KEY_LAST_SCAN, now)
                .remove(KEY_LAST_ERROR)
                .commit();
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    static boolean setEnabled(Context context, boolean enabled) {
        return prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    static long getLastScan(Context context) {
        return prefs(context).getLong(KEY_LAST_SCAN, 0L);
    }

    static void setLastScan(Context context, long value) {
        prefs(context).edit().putLong(KEY_LAST_SCAN, value).apply();
    }

    static long getLastSuccess(Context context) {
        return prefs(context).getLong(KEY_LAST_SUCCESS, 0L);
    }

    static String getLastError(Context context) {
        return prefs(context).getString(KEY_LAST_ERROR, "");
    }

    static void recordSuccess(Context context) {
        prefs(context).edit()
                .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
                .remove(KEY_LAST_ERROR)
                .apply();
    }

    static void recordError(Context context, String error) {
        String safe = error == null ? "unknown error" : error;
        if (safe.length() > 240) {
            safe = safe.substring(0, 240);
        }
        prefs(context).edit().putString(KEY_LAST_ERROR, safe).apply();
    }

    static boolean isAlarmSet(Context context) {
        return prefs(context).getBoolean(KEY_ALARM_SET, false);
    }

    static void setAlarmSet(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_ALARM_SET, value).apply();
    }
}
