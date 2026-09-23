package com.ufi.smsforwarder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

final class Scheduler {
    private static final long FIRST_RETRY_MS = 60L * 1000L;
    private static final long RETRY_INTERVAL_MS = 5L * 60L * 1000L;

    private Scheduler() {}

    static void ensureScheduled(Context context, boolean force) {
        if (!AppConfig.isEnabled(context)) {
            return;
        }
        if (!force && AppConfig.isAlarmSet(context)) {
            return;
        }
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, RetryReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        alarms.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + FIRST_RETRY_MS,
                RETRY_INTERVAL_MS,
                pending
        );
        AppConfig.setAlarmSet(context, true);
    }

    static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, RetryReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        alarms.cancel(pending);
        pending.cancel();
        AppConfig.setAlarmSet(context, false);
    }
}
