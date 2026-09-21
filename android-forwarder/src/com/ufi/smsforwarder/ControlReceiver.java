package com.ufi.smsforwarder;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.regex.Pattern;

public final class ControlReceiver extends BroadcastReceiver {
    private static final String ACTION_CONFIGURE = "com.ufi.smsforwarder.CONFIGURE";
    private static final String ACTION_STATUS = "com.ufi.smsforwarder.STATUS";
    private static final String ACTION_TEST = "com.ufi.smsforwarder.TEST";
    private static final String ACTION_DRAIN = "com.ufi.smsforwarder.DRAIN";
    private static final Pattern TOKEN = Pattern.compile("[0-9]+:[A-Za-z0-9_-]+");

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_CONFIGURE.equals(action)) {
            String token = intent.getStringExtra("token");
            String chatId = intent.getStringExtra("chat_id");
            if (token == null || !TOKEN.matcher(token).matches()
                    || chatId == null || chatId.trim().length() == 0) {
                finish(Activity.RESULT_CANCELED, "invalid configuration");
                return;
            }
            if (!AppConfig.save(context, token, chatId.trim())) {
                finish(Activity.RESULT_CANCELED, "configuration could not be saved");
                return;
            }
            Scheduler.ensureScheduled(context, true);
            ForwardService.requestTest(
                    context,
                    "✅ UFI modem SMS forwarder is running autonomously."
            );
            ForwardService.requestDrain(context);
            finish(Activity.RESULT_OK, "configured; Telegram test queued");
            return;
        }

        if (ACTION_STATUS.equals(action)) {
            QueueDb queue = new QueueDb(context);
            long count;
            try {
                count = queue.count();
            } finally {
                queue.close();
            }
            AppConfig.Snapshot config = AppConfig.load(context);
            String error = AppConfig.getLastError(context);
            String status = "configured=" + config.isConfigured()
                    + "; queued=" + count
                    + "; last_success_ms=" + AppConfig.getLastSuccess(context)
                    + "; last_error=" + (error.length() == 0 ? "none" : error);
            finish(Activity.RESULT_OK, status);
            return;
        }

        if (ACTION_TEST.equals(action)) {
            if (!AppConfig.load(context).isConfigured()) {
                finish(Activity.RESULT_CANCELED, "not configured");
                return;
            }
            ForwardService.enqueue(
                    context,
                    "UFI self-test",
                    System.currentTimeMillis(),
                    "The modem SMS queue and Telegram delivery pipeline are working."
            );
            finish(Activity.RESULT_OK, "SMS pipeline test queued");
            return;
        }

        if (ACTION_DRAIN.equals(action)) {
            ForwardService.requestDrain(context);
            finish(Activity.RESULT_OK, "queue drain requested");
            return;
        }

        finish(Activity.RESULT_CANCELED, "unknown action");
    }

    private void finish(int code, String data) {
        setResultCode(code);
        setResultData(data);
    }
}
