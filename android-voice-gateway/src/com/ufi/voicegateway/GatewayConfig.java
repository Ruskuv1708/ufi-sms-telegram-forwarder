package com.ufi.voicegateway;

import android.content.Context;
import android.content.SharedPreferences;

final class GatewayConfig {
    private static final String PREFERENCES = "gateway";
    private static final String TOKEN = "token";
    private static final String ENABLED = "enabled";

    private GatewayConfig() {
    }

    static String token(Context context) {
        return preferences(context).getString(TOKEN, "");
    }

    static boolean enabled(Context context) {
        return preferences(context).getBoolean(ENABLED, false) && validToken(token(context));
    }

    static boolean save(Context context, String token, boolean enabled) {
        if (!validToken(token)) {
            return false;
        }
        return preferences(context).edit()
                .putString(TOKEN, token)
                .putBoolean(ENABLED, enabled)
                .commit();
    }

    static boolean validToken(String token) {
        return token != null && token.matches("[0-9a-f]{64}");
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
