package com.ufi.networkguard;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class NetworkGuardService extends Service {
    private static final String TAG = "UfiNetworkGuard";
    private static final int AUTOMATIC_LTE_GSM_WCDMA = 9;
    private static final long CHECK_INTERVAL_MS = 15000L;
    private final Handler handler = new Handler();
    private boolean stopped;
    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            if (stopped) {
                return;
            }
            enforceAutomaticMode();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        stopped = false;
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

    private void enforceAutomaticMode() {
        int current = Settings.Global.getInt(
                getContentResolver(), "preferred_network_mode", -1);
        if (current == AUTOMATIC_LTE_GSM_WCDMA) {
            return;
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
            Log.i(TAG, "restored automatic LTE/GSM/WCDMA mode");
        } catch (Exception error) {
            Throwable cause = error;
            while (cause instanceof InvocationTargetException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            Log.e(TAG, "cannot restore network mode: "
                    + cause.getClass().getSimpleName());
        }
    }
}
