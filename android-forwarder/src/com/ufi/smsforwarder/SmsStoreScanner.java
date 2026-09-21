package com.ufi.smsforwarder;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

final class SmsStoreScanner {
    private static final Uri INBOX = Uri.parse("content://sms/inbox");
    private static final long OVERLAP_MS = 60L * 1000L;
    private static final int MAX_ROWS = 200;

    private SmsStoreScanner() {}

    static int scan(Context context, QueueDb queue) {
        long lastScan = AppConfig.getLastScan(context);
        if (lastScan <= 0L) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long since = Math.max(0L, lastScan - OVERLAP_MS);
        Cursor cursor = context.getContentResolver().query(
                INBOX,
                new String[]{"_id", "address", "date", "body"},
                "date>=?",
                new String[]{Long.toString(since)},
                "date ASC"
        );
        if (cursor == null) {
            throw new IllegalStateException("SMS provider returned no cursor");
        }
        int inserted = 0;
        int visited = 0;
        try {
            while (cursor.moveToNext() && visited < MAX_ROWS) {
                visited++;
                String sender = cursor.getString(1);
                long receivedAt = cursor.getLong(2);
                String body = cursor.getString(3);
                if (queue.enqueue(sender, receivedAt, body)) {
                    inserted++;
                }
            }
        } finally {
            cursor.close();
        }
        AppConfig.setLastScan(context, now);
        return inserted;
    }
}
