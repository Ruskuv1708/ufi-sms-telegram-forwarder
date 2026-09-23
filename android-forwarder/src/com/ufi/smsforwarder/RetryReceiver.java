package com.ufi.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RetryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AppConfig.isEnabled(context)) {
            return;
        }
        ForwardService.requestDrain(context);
    }
}
