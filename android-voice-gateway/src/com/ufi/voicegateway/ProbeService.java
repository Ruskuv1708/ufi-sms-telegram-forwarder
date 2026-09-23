package com.ufi.voicegateway;

import android.Manifest;
import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.Process;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.reflect.Method;

public final class ProbeService extends IntentService {
    private static final String TAG = "UfiVoiceGateway";

    public ProbeService() {
        super("UfiVoiceGatewayProbe");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null && "com.ufi.voicegateway.CAPTURE".equals(intent.getAction())) {
            captureSource(
                    intent.getStringExtra("source"),
                    Math.max(1, Math.min(10, intent.getIntExtra("seconds", 3))));
            return;
        }
        if (intent != null && "com.ufi.voicegateway.NETWORK_MODE".equals(intent.getAction())) {
            inspectOrSetNetworkMode(intent);
            return;
        }
        if (intent != null && "com.ufi.voicegateway.CALL_CONTROL".equals(intent.getAction())) {
            controlCall(intent.getStringExtra("operation"));
            return;
        }

        Log.i(TAG, "probe.begin uid=" + Process.myUid() + " pid=" + Process.myPid());
        logPermission(Manifest.permission.READ_PHONE_STATE);
        logPermission(Manifest.permission.CALL_PHONE);
        logPermission(Manifest.permission.MODIFY_PHONE_STATE);
        logPermission(Manifest.permission.RECORD_AUDIO);
        logPermission(Manifest.permission.CAPTURE_AUDIO_OUTPUT);
        logPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS);

        TelephonyManager telephony =
                (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        Log.i(TAG, "telephony callState=" + telephony.getCallState()
                + " networkType=" + telephony.getNetworkType());

        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        Log.i(TAG, "audio mode=" + audio.getMode()
                + " speaker=" + audio.isSpeakerphoneOn()
                + " bluetoothSco=" + audio.isBluetoothScoOn());

        probeSource("MIC", MediaRecorder.AudioSource.MIC);
        probeSource("VOICE_UPLINK", MediaRecorder.AudioSource.VOICE_UPLINK);
        probeSource("VOICE_DOWNLINK", MediaRecorder.AudioSource.VOICE_DOWNLINK);
        probeSource("VOICE_CALL", MediaRecorder.AudioSource.VOICE_CALL);
        probeSource("VOICE_COMMUNICATION", MediaRecorder.AudioSource.VOICE_COMMUNICATION);
        Log.i(TAG, "probe.end");
    }

    private void controlCall(String operation) {
        if (!"answer".equals(operation) && !"hangup".equals(operation)) {
            Log.e(TAG, "callControl rejected=" + operation);
            return;
        }
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getService = serviceManagerClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "phone");
            Class<?> stubClass = Class.forName(
                    "com.android.internal.telephony.ITelephony$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            Object phone = asInterface.invoke(null, binder);
            String methodName = "answer".equals(operation) ? "answerRingingCall" : "endCall";
            Method method = phone.getClass().getMethod(methodName);
            Object result = method.invoke(phone);
            Log.i(TAG, "callControl operation=" + operation + " result=" + result);
        } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            Log.e(TAG, "callControl operation=" + operation + " failed="
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
        }
    }

    private void inspectOrSetNetworkMode(Intent intent) {
        int current = Settings.Global.getInt(
                getContentResolver(), "preferred_network_mode", -1);
        if (!intent.hasExtra("mode")) {
            Log.i(TAG, "networkMode current=" + current);
            return;
        }

        int requested = intent.getIntExtra("mode", -1);
        if (requested < 0 || requested > 22) {
            Log.e(TAG, "networkMode rejected=" + requested);
            return;
        }

        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getService = serviceManagerClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "phone");
            Class<?> stubClass = Class.forName(
                    "com.android.internal.telephony.ITelephony$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            Object phone = asInterface.invoke(null, binder);
            Method setMode = phone.getClass().getMethod("setPreferredNetworkType", int.class);
            Object result = setMode.invoke(phone, requested);
            boolean stored = Settings.Global.putInt(
                    getContentResolver(), "preferred_network_mode", requested);
            Log.i(TAG, "networkMode previous=" + current
                    + " requested=" + requested
                    + " binderResult=" + result
                    + " stored=" + stored);
        } catch (Exception error) {
            Log.e(TAG, "networkMode failed=" + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        }
    }

    private void captureSource(String requestedSource, int seconds) {
        String name = requestedSource == null ? "VOICE_CALL" : requestedSource;
        int source = MediaRecorder.AudioSource.VOICE_CALL;
        if ("VOICE_UPLINK".equals(name)) {
            source = MediaRecorder.AudioSource.VOICE_UPLINK;
        } else if ("VOICE_DOWNLINK".equals(name)) {
            source = MediaRecorder.AudioSource.VOICE_DOWNLINK;
        } else {
            name = "VOICE_CALL";
        }

        AudioRecord recorder = null;
        try {
            int minimum = AudioRecord.getMinBufferSize(
                    8000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(minimum, 4096);
            recorder = new AudioRecord(
                    source,
                    8000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    size);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "capture source=" + name + " could not initialize");
                return;
            }

            short[] samples = new short[size / 2];
            long squareSum = 0;
            long sampleCount = 0;
            int peak = 0;
            int nonZero = 0;
            long deadline = System.currentTimeMillis() + seconds * 1000L;
            recorder.startRecording();
            while (System.currentTimeMillis() < deadline) {
                int count = recorder.read(samples, 0, samples.length);
                if (count <= 0) {
                    continue;
                }
                for (int index = 0; index < count; index++) {
                    int value = samples[index];
                    int absolute = Math.abs(value);
                    peak = Math.max(peak, absolute);
                    if (value != 0) {
                        nonZero++;
                    }
                    squareSum += (long) value * value;
                }
                sampleCount += count;
            }
            recorder.stop();
            double rms = sampleCount == 0 ? 0.0
                    : Math.sqrt((double) squareSum / (double) sampleCount);
            Log.i(TAG, "capture source=" + name
                    + " seconds=" + seconds
                    + " samples=" + sampleCount
                    + " nonZero=" + nonZero
                    + " peak=" + peak
                    + " rms=" + String.format(java.util.Locale.US, "%.2f", rms));
        } catch (RuntimeException error) {
            Log.e(TAG, "capture source=" + name + " failed="
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (recorder != null) {
                recorder.release();
            }
        }
    }

    private void logPermission(String permission) {
        boolean granted = checkCallingOrSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
        Log.i(TAG, "permission " + permission + "=" + granted);
    }

    private void probeSource(String name, int source) {
        AudioRecord recorder = null;
        try {
            int minimum = AudioRecord.getMinBufferSize(
                    8000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(minimum, 4096);
            recorder = new AudioRecord(
                    source,
                    8000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    size);
            Log.i(TAG, "source " + name + " initialized="
                    + (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                    + " minBuffer=" + minimum + " session=" + recorder.getAudioSessionId());
        } catch (RuntimeException error) {
            Log.e(TAG, "source " + name + " failed=" + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        } finally {
            if (recorder != null) {
                recorder.release();
            }
        }
    }
}
