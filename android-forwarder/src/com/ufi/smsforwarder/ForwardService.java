package com.ufi.smsforwarder;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ForwardService extends IntentService {
    private static final String TAG = "UfiSmsForwarder";
    private static final String ACTION_ENQUEUE = "com.ufi.smsforwarder.action.ENQUEUE";
    private static final String ACTION_DRAIN = "com.ufi.smsforwarder.action.DRAIN";
    private static final String ACTION_TEST = "com.ufi.smsforwarder.action.TEST";
    private static final String EXTRA_SENDER = "sender";
    private static final String EXTRA_RECEIVED_AT = "received_at";
    private static final String EXTRA_BODY = "body";
    private static final String EXTRA_TEST_TEXT = "test_text";
    private static final int MAX_PER_RUN = 50;

    public ForwardService() {
        super("UfiSmsForwardService");
    }

    static void enqueue(Context context, String sender, long receivedAt, String body) {
        Intent intent = new Intent(context, ForwardService.class);
        intent.setAction(ACTION_ENQUEUE);
        intent.putExtra(EXTRA_SENDER, sender);
        intent.putExtra(EXTRA_RECEIVED_AT, receivedAt);
        intent.putExtra(EXTRA_BODY, body);
        context.startService(intent);
    }

    static void requestDrain(Context context) {
        Intent intent = new Intent(context, ForwardService.class);
        intent.setAction(ACTION_DRAIN);
        context.startService(intent);
    }

    static void requestTest(Context context, String text) {
        Intent intent = new Intent(context, ForwardService.class);
        intent.setAction(ACTION_TEST);
        intent.putExtra(EXTRA_TEST_TEXT, text);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock lock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "UfiSmsForwarder"
        );
        lock.acquire(120000L);
        QueueDb queue = null;
        try {
            Scheduler.ensureScheduled(this, false);
            queue = new QueueDb(this);
            String action = intent == null ? ACTION_DRAIN : intent.getAction();
            if (ACTION_ENQUEUE.equals(action)) {
                queue.enqueue(
                        intent.getStringExtra(EXTRA_SENDER),
                        intent.getLongExtra(EXTRA_RECEIVED_AT, System.currentTimeMillis()),
                        intent.getStringExtra(EXTRA_BODY)
                );
            }

            AppConfig.Snapshot config = AppConfig.load(this);
            if (!config.isConfigured()) {
                return;
            }

            try {
                SmsStoreScanner.scan(this, queue);
            } catch (Exception error) {
                AppConfig.recordError(this, safeError(error, config.token));
                Log.w(TAG, "SMS provider scan failed: " + error.getClass().getSimpleName());
            }

            TelegramClient telegram = new TelegramClient(this);
            if (ACTION_TEST.equals(action)) {
                String message = intent.getStringExtra(EXTRA_TEST_TEXT);
                try {
                    telegram.send(config, message);
                    AppConfig.recordSuccess(this);
                    Log.i(TAG, "Telegram test delivered");
                } catch (Exception error) {
                    AppConfig.recordError(this, safeError(error, config.token));
                    Log.w(TAG, "Telegram test failed: " + error.getClass().getSimpleName());
                }
            }

            drain(queue, telegram, config);
        } finally {
            if (queue != null) {
                queue.close();
            }
            if (lock.isHeld()) {
                lock.release();
            }
        }
    }

    private void drain(QueueDb queue, TelegramClient telegram, AppConfig.Snapshot config) {
        int delivered = 0;
        while (delivered < MAX_PER_RUN) {
            QueuedSms message = queue.next();
            if (message == null) {
                return;
            }
            try {
                telegram.send(config, formatMessage(message));
                queue.delete(message.id);
                AppConfig.recordSuccess(this);
                delivered++;
                Log.i(TAG, "Queued SMS delivered");
            } catch (Exception error) {
                String safe = safeError(error, config.token);
                queue.markFailed(message.id, safe);
                AppConfig.recordError(this, safe);
                Log.w(TAG, "Queued SMS delivery failed: " + error.getClass().getSimpleName());
                return;
            }
        }
        if (queue.count() > 0L) {
            requestDrain(this);
        }
    }

    private static String formatMessage(QueuedSms message) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US);
        return "📩 SMS\nFrom: " + message.sender
                + "\nTime: " + format.format(new Date(message.receivedAt))
                + "\n\n" + message.body;
    }

    private static String safeError(Exception error, String token) {
        String type = error.getClass().getSimpleName();
        String detail = error.getMessage();
        if (detail == null || detail.length() == 0) {
            return type;
        }
        if (token != null && token.length() > 0) {
            detail = detail.replace(token, "[redacted]");
        }
        detail = detail.replace('\n', ' ').replace('\r', ' ');
        if (detail.length() > 160) {
            detail = detail.substring(0, 160);
        }
        return type + ": " + detail;
    }
}
