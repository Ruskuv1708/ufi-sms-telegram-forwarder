package com.ufi.voicegateway;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public final class PhoneBridgeService extends Service {
    private static final String TAG = "UfiVoiceGateway";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !"com.ufi.voicegateway.NETWORK_MODE".equals(intent.getAction())) {
            Log.e(TAG, "phoneBridge unsupported action");
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        int current = Settings.Global.getInt(
                getContentResolver(), "preferred_network_mode", -1);
        if (!intent.hasExtra("mode")) {
            Log.i(TAG, "phoneBridge networkMode current=" + current);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        int requested = intent.getIntExtra("mode", -1);
        if (requested < 0 || requested > 22) {
            Log.e(TAG, "phoneBridge networkMode rejected=" + requested);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        try {
            Class<?> factoryClass = Class.forName(
                    "com.android.internal.telephony.PhoneFactory");
            Method getDefaultPhone = factoryClass.getMethod("getDefaultPhone");
            Object phone = getDefaultPhone.invoke(null);
            Method setMode = phone.getClass().getMethod(
                    "setPreferredNetworkType", int.class, Message.class);
            setMode.invoke(phone, requested, null);
            boolean stored = Settings.Global.putInt(
                    getContentResolver(), "preferred_network_mode", requested);
            Log.i(TAG, "phoneBridge networkMode previous=" + current
                    + " requested=" + requested
                    + " stored=" + stored
                    + " phoneClass=" + phone.getClass().getName());
        } catch (Exception error) {
            Throwable cause = error;
            while (cause instanceof InvocationTargetException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            Log.e(TAG, "phoneBridge networkMode failed="
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }
}
