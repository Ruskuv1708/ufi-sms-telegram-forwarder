package com.ufi.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public final class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "UfiSmsForwarder";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent == null ? null : intent.getExtras();
        Object[] pdus = extras == null ? null : (Object[]) extras.get("pdus");
        if (pdus == null || pdus.length == 0) {
            Log.w(TAG, "SMS broadcast had no PDU data");
            ForwardService.requestDrain(context);
            return;
        }

        String sender = null;
        long receivedAt = System.currentTimeMillis();
        StringBuilder body = new StringBuilder();
        for (Object item : pdus) {
            if (!(item instanceof byte[])) {
                continue;
            }
            SmsMessage part = SmsMessage.createFromPdu((byte[]) item);
            if (part == null) {
                continue;
            }
            if (sender == null) {
                sender = part.getOriginatingAddress();
            }
            String partBody = part.getMessageBody();
            if (partBody != null) {
                body.append(partBody);
            }
        }
        if (sender == null && body.length() == 0) {
            ForwardService.requestDrain(context);
            return;
        }
        ForwardService.enqueue(context, sender, receivedAt, body.toString());
    }
}
