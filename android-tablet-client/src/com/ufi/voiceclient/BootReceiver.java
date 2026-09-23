package com.ufi.voiceclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ClientConfig.enabled(context)) {
            return;
        }
        Intent service = new Intent(context, VoiceMonitorService.class);
        service.setAction(VoiceMonitorService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (RuntimeException ignored) {
            // Some Android 15 builds do not allow an FGS directly from the
            // package-replaced broadcast. Opening the app starts it normally.
        }
    }
}
