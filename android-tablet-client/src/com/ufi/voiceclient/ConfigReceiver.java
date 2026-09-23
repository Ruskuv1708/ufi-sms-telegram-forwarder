package com.ufi.voiceclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class ConfigReceiver extends BroadcastReceiver {
    private static final String TAG = "UfiCallClient";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"com.ufi.voiceclient.CONFIGURE".equals(intent.getAction())) {
            return;
        }
        String host = intent.getStringExtra("host");
        String token = intent.getStringExtra("token");
        boolean enabled = intent.getBooleanExtra("enabled", true);
        if (host == null) {
            host = ClientConfig.DEFAULT_HOST;
        }
        if (!ClientConfig.save(context, host, token, enabled)) {
            Log.e(TAG, "pairing configuration rejected");
            return;
        }
        Log.i(TAG, "pairing configuration saved");
    }
}
