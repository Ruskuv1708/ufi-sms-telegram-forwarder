package com.ufi.voicegateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (GatewayConfig.enabled(context)) {
            context.startService(new Intent(context, VoiceGatewayService.class));
        }
    }
}
