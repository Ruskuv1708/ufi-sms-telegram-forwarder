package com.ufi.voicegateway;

import android.os.IBinder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class CallController {
    private CallController() {
    }

    static boolean answer() throws Exception {
        invoke("answerRingingCall");
        return true;
    }

    static boolean hangup() throws Exception {
        Object result = invoke("endCall");
        return !(result instanceof Boolean) || ((Boolean) result).booleanValue();
    }

    private static Object invoke(String methodName) throws Exception {
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getService = serviceManagerClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "phone");
            if (binder == null) {
                throw new IllegalStateException("phone service is unavailable");
            }
            Class<?> stubClass = Class.forName(
                    "com.android.internal.telephony.ITelephony$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            Object phone = asInterface.invoke(null, binder);
            Method method = phone.getClass().getMethod(methodName);
            return method.invoke(phone);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw error;
        }
    }
}
