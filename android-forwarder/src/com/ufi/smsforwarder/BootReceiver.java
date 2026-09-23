package com.ufi.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AppConfig.isEnabled(context)) {
            return;
        }
        Scheduler.ensureScheduled(context, true);
        ForwardService.requestDrain(context);
    }
}
