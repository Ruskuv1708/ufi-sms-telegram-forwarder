package com.ufi.voiceclient;

import android.content.Context;
import android.content.SharedPreferences;

final class ClientConfig {
    static final String DEFAULT_HOST = "192.168.100.1";
    private static final String PREFERENCES = "voice_client";
    private static final String HOST = "host";
    private static final String TOKEN = "token";
    private static final String ENABLED = "enabled";

    private ClientConfig() {
    }

    static String host(Context context) {
        return preferences(context).getString(HOST, DEFAULT_HOST);
    }

    static String token(Context context) {
        return preferences(context).getString(TOKEN, "");
    }

    static boolean enabled(Context context) {
        return preferences(context).getBoolean(ENABLED, false) && validToken(token(context));
    }

    static boolean save(Context context, String host, String token, boolean enabled) {
        if (!validHost(host) || !validToken(token)) {
            return false;
        }
        return preferences(context).edit()
                .putString(HOST, host)
                .putString(TOKEN, token)
                .putBoolean(ENABLED, enabled)
                .commit();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static boolean validHost(String host) {
        return host != null && host.matches("192\\.168\\.100\\.[0-9]{1,3}");
    }

    static boolean validToken(String token) {
        return token != null && token.matches("[0-9a-f]{64}");
    }
}
