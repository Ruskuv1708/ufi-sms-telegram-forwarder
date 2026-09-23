package com.ufi.networkguard;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class NetworkGuardService extends Service {
    private static final String TAG = "UfiNetworkGuard";
    private static final int AUTOMATIC_LTE_GSM_WCDMA = 9;
    private static final String KEY_RECOVERIES = "ufi_voice_network_mode_recoveries";
    private static final String KEY_LAST_RECOVERY = "ufi_voice_network_mode_last_recovery";
    private static final String KEY_LAST_APPLIED = "ufi_voice_network_mode_last_applied";
    private static final long CHECK_INTERVAL_MS = 15000L;
    private static final long PERIODIC_REASSERT_MS = 6L * 60L * 60L * 1000L;
    private static final long[] BOOT_REASSERT_DELAYS_MS = {
            0L,
            30000L,
            120000L,
            300000L
    };
    private final Handler handler = new Handler();
    private boolean stopped;
    private long serviceStartedAt;
    private long lastAppliedAt;
    private int nextBootReassert;
    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            if (stopped) {
                return;
            }
            runGuardCheck();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        stopped = false;
        serviceStartedAt = SystemClock.elapsedRealtime();
        lastAppliedAt = 0L;
        nextBootReassert = 0;
        handler.post(check);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(check);
        handler.post(check);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopped = true;
        handler.removeCallbacks(check);
        super.onDestroy();
    }

    private void runGuardCheck() {
        long now = SystemClock.elapsedRealtime();
        boolean scheduledBootReassert = nextBootReassert < BOOT_REASSERT_DELAYS_MS.length
                && now - serviceStartedAt >= BOOT_REASSERT_DELAYS_MS[nextBootReassert];
        boolean periodicReassert = nextBootReassert >= BOOT_REASSERT_DELAYS_MS.length
                && (lastAppliedAt == 0L || now - lastAppliedAt >= PERIODIC_REASSERT_MS);
        if (enforceAutomaticMode(scheduledBootReassert || periodicReassert)) {
            lastAppliedAt = now;
            if (scheduledBootReassert) {
                nextBootReassert++;
            }
        }
    }

    private boolean enforceAutomaticMode(boolean force) {
        int current = Settings.Global.getInt(
                getContentResolver(), "preferred_network_mode", -1);
        if (!force && current == AUTOMATIC_LTE_GSM_WCDMA) {
            return false;
        }
        try {
            Class<?> factoryClass = Class.forName(
                    "com.android.internal.telephony.PhoneFactory");
            Method getDefaultPhone = factoryClass.getMethod("getDefaultPhone");
            Object phone = getDefaultPhone.invoke(null);
            Method setMode = phone.getClass().getMethod(
                    "setPreferredNetworkType", int.class, Message.class);
            setMode.invoke(phone, AUTOMATIC_LTE_GSM_WCDMA, null);
            Settings.Global.putInt(
                    getContentResolver(),
                    "preferred_network_mode",
                    AUTOMATIC_LTE_GSM_WCDMA);
            long now = System.currentTimeMillis();
            Settings.Global.putLong(getContentResolver(), KEY_LAST_APPLIED, now);
            if (current != AUTOMATIC_LTE_GSM_WCDMA) {
                int recoveries = Settings.Global.getInt(
                        getContentResolver(), KEY_RECOVERIES, 0);
                Settings.Global.putInt(
                        getContentResolver(), KEY_RECOVERIES, recoveries + 1);
                Settings.Global.putLong(getContentResolver(), KEY_LAST_RECOVERY, now);
                Log.w(TAG, "recovered from unsafe network mode " + current);
            }
            Log.i(TAG, "restored automatic LTE/GSM/WCDMA mode");
            return true;
        } catch (Exception error) {
            Throwable cause = error;
            while (cause instanceof InvocationTargetException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            Log.e(TAG, "cannot restore network mode: "
                    + cause.getClass().getSimpleName());
            return false;
        }
    }
}
