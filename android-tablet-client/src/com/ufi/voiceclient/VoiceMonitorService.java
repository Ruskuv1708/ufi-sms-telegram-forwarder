package com.ufi.voiceclient;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class VoiceMonitorService extends Service {
    static final String ACTION_START = "com.ufi.voiceclient.START";
    static final String ACTION_DIAL = "com.ufi.voiceclient.DIAL";
    static final String ACTION_ANSWER = "com.ufi.voiceclient.ANSWER";
    static final String ACTION_HANGUP = "com.ufi.voiceclient.HANGUP";
    static final String ACTION_AUDIO_START = "com.ufi.voiceclient.AUDIO_START";
    static final String ACTION_AUDIO_STOP = "com.ufi.voiceclient.AUDIO_STOP";
    static final String ACTION_SPEAKER = "com.ufi.voiceclient.SPEAKER";
    static final String ACTION_SMS_SEND = "com.ufi.voiceclient.SMS_SEND";
    static final String ACTION_SMS_READ = "com.ufi.voiceclient.SMS_READ";
    static final String ACTION_STATUS = "com.ufi.voiceclient.STATUS";
    static final String EXTRA_STATE = "state";
    static final String EXTRA_NETWORK = "network";
    static final String EXTRA_CALLER = "caller";
    static final String EXTRA_DIRECTION = "direction";
    static final String EXTRA_DIAL_NUMBER = "dial_number";
    static final String EXTRA_DETAIL = "detail";
    static final String EXTRA_AUDIO = "audio";
    static final String EXTRA_SPEAKER = "speaker";
    static final String EXTRA_CALL_READY = "call_ready";
    static final String EXTRA_PREFERRED_MODE = "preferred_mode";
    static final String EXTRA_MODE_RECOVERIES = "mode_recoveries";
    static final String EXTRA_SMS_ADDRESS = "sms_address";
    static final String EXTRA_SMS_BODY = "sms_body";
    static final String EXTRA_SMS_UNREAD = "sms_unread";
    static final String EXTRA_SMS_REVISION = "sms_revision";
    static final String EXTRA_SMS_DETAIL = "sms_detail";
    static final String EXTRA_OPEN_TAB = "open_tab";

    private static final String TAG = "UfiCallClient";
    private static final String CHANNEL_MONITOR = "ufi_voice_monitor";
    private static final String CHANNEL_CALLS = "ufi_voice_calls";
    private static final String CHANNEL_GUARD = "ufi_voice_guard";
    private static final String CHANNEL_MESSAGES = "ufi_sms_messages";
    private static final int NOTIFICATION_MONITOR = 4101;
    private static final int NOTIFICATION_CALL = 4102;
    private static final int NOTIFICATION_GUARD = 4103;
    private static final int NOTIFICATION_MESSAGE = 4104;
    private static final String SMS_NOTIFICATION_PREFS = "ufi_sms_notifications";
    private static final String SMS_LAST_INCOMING_DATE = "last_incoming_date";

    private ScheduledExecutorService executor;
    private NotificationManager notifications;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private AudioBridge audioBridge;
    private AudioManager audioManager;
    private volatile boolean stopping;
    private volatile boolean speaker = true;
    private volatile boolean autoStartAudio;
    private volatile String state = "CONNECTING";
    private volatile String network = "";
    private volatile String caller = "";
    private volatile String direction = "";
    private volatile String detail = "Connecting to the modem";
    private volatile String audioState = "OFF";
    private volatile boolean callReady = true;
    private volatile int preferredNetworkMode = -1;
    private volatile int modeRecoveries = -1;
    private volatile String pendingOutgoingNumber = "";
    private volatile long pendingDialAt;
    private volatile String activeHistoryId = "";
    private volatile String activeHistoryDirection = "";
    private volatile String activeHistoryState = "";
    private volatile boolean hangupRequested;
    private volatile int smsUnread;
    private volatile long smsRevision;
    private volatile String smsDetail = "Syncing messages";

    @Override
    public void onCreate() {
        super.onCreate();
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        CallHistoryStore.markStaleInProgress(this, System.currentTimeMillis());
        createNotificationChannels();
        startForeground(
                NOTIFICATION_MONITOR,
                buildMonitorNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        acquireLocks();
        executor = Executors.newScheduledThreadPool(3);
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                pollGateway();
            }
        }, 0, 1, TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                pollSms();
            }
        }, 1, 5, TimeUnit.SECONDS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_DIAL.equals(action)) {
            String number = intent == null ? "" : intent.getStringExtra(EXTRA_DIAL_NUMBER);
            runDial(number);
        } else if (ACTION_ANSWER.equals(action)) {
            Log.i(TAG, "answer requested");
            autoStartAudio = true;
            runControl("ANSWER");
        } else if (ACTION_HANGUP.equals(action)) {
            Log.i(TAG, "hangup requested");
            autoStartAudio = false;
            hangupRequested = true;
            runControl("HANGUP");
        } else if (ACTION_AUDIO_START.equals(action)) {
            Log.i(TAG, "audio requested");
            autoStartAudio = true;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    startAudio();
                }
            });
        } else if (ACTION_AUDIO_STOP.equals(action)) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    stopAudio();
                }
            });
        } else if (ACTION_SPEAKER.equals(action)) {
            speaker = intent.getBooleanExtra(EXTRA_SPEAKER, true);
            applyAudioRoute();
            broadcastStatus();
        } else if (ACTION_SMS_SEND.equals(action)) {
            String address = intent == null ? "" : intent.getStringExtra(EXTRA_SMS_ADDRESS);
            String body = intent == null ? "" : intent.getStringExtra(EXTRA_SMS_BODY);
            runSmsSend(address, body);
        } else if (ACTION_SMS_READ.equals(action)) {
            String address = intent == null ? "" : intent.getStringExtra(EXTRA_SMS_ADDRESS);
            runSmsMarkRead(address);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopping = true;
        if (executor != null) {
            executor.shutdownNow();
        }
        stopAudio();
        releaseLocks();
        super.onDestroy();
    }

    private void pollGateway() {
        if (stopping) {
            return;
        }
        if (!ClientConfig.enabled(this)) {
            updateStatus("NOT_PAIRED", "", "", "Run tablet setup from the computer");
            return;
        }
        try {
            GatewayClient.Status status = GatewayClient.status(
                    ClientConfig.host(this), ClientConfig.token(this));
            String previous = state;
            String nextState = status.state;
            String nextDirection = resolveDirection(
                    status.direction, status.caller, nextState);
            String nextCaller = status.caller;
            long now = System.currentTimeMillis();
            if ("IDLE".equals(nextState)
                    && pendingDialAt > 0L
                    && now - pendingDialAt < 30000L) {
                nextState = "DIALING";
                nextDirection = CallHistoryStore.DIRECTION_OUTGOING;
                nextCaller = pendingOutgoingNumber;
            } else if (nextCaller.length() == 0
                    && pendingOutgoingNumber.length() > 0
                    && ("DIALING".equals(nextState) || "ACTIVE".equals(nextState))) {
                nextCaller = pendingOutgoingNumber;
            }
            state = nextState;
            network = status.network;
            caller = nextCaller;
            direction = nextDirection;
            boolean previousCallReady = callReady;
            int previousRecoveries = modeRecoveries;
            callReady = status.callReady;
            preferredNetworkMode = status.preferredNetworkMode;
            modeRecoveries = status.modeRecoveries;
            if ("DIALING".equals(state) && caller.length() > 0) {
                detail = "Dialing " + caller;
            } else {
                detail = callReady
                        ? "Connected to modem"
                        : "LTE-only mode detected; guard is correcting it";
            }
            if (!callReady && previousCallReady) {
                showModeWarning(false);
            } else if (callReady && previousRecoveries >= 0
                    && modeRecoveries > previousRecoveries) {
                showModeWarning(true);
            }
            if ("RINGING".equals(state)) {
                if (!"RINGING".equals(previous)) {
                    showIncomingCall();
                }
            } else {
                notifications.cancel(NOTIFICATION_CALL);
            }
            updateHistory(previous, state, direction, caller, now);
            if ("ACTIVE".equals(state) && autoStartAudio && audioBridge == null) {
                autoStartAudio = false;
                startAudio();
            }
            if ("IDLE".equals(state)) {
                autoStartAudio = false;
            }
            if (!"ACTIVE".equals(state) && audioBridge != null) {
                stopAudio();
            }
            notifyAndBroadcast();
        } catch (Exception error) {
            String message = error.getMessage();
            if (message == null || message.length() == 0) {
                message = "Modem is unreachable";
            }
            updateStatus("OFFLINE", "", "", message);
        }
    }

    private void pollSms() {
        if (stopping || !ClientConfig.enabled(this)) {
            return;
        }
        try {
            String raw = GatewayClient.smsList(
                    ClientConfig.host(this), ClientConfig.token(this));
            boolean changed = SmsStore.replace(this, raw);
            smsUnread = SmsStore.unreadCount(this);
            smsRevision = SmsStore.revision(this);
            smsDetail = "Messages synced directly with modem";
            SmsMessage newest = SmsStore.newestIncoming(this);
            maybeShowSmsNotification(newest);
            if (changed) {
                Log.i(TAG, "SMS cache updated");
            }
            broadcastStatus();
        } catch (Exception error) {
            smsDetail = "Messages: " + friendlyError(error);
            Log.w(TAG, "SMS poll failed: " + error.getClass().getSimpleName());
            broadcastStatus();
        }
    }

    private void runSmsSend(String rawAddress, final String body) {
        final String address = SmsAddress.normalize(rawAddress);
        if (address.length() == 0 || body == null || body.trim().length() == 0
                || body.length() > 2000) {
            smsDetail = "Enter a valid recipient and message";
            broadcastStatus();
            return;
        }
        smsDetail = "Sending message";
        broadcastStatus();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SmsMessage sent = GatewayClient.smsSend(
                            ClientConfig.host(VoiceMonitorService.this),
                            ClientConfig.token(VoiceMonitorService.this),
                            address,
                            body);
                    SmsStore.addSent(VoiceMonitorService.this, sent);
                    smsUnread = SmsStore.unreadCount(VoiceMonitorService.this);
                    smsRevision = SmsStore.revision(VoiceMonitorService.this);
                    smsDetail = "Message sent";
                    broadcastStatus();
                    pollSms();
                } catch (Exception error) {
                    smsDetail = "Message not sent: " + friendlyError(error);
                    Log.w(TAG, "SMS send failed: " + error.getClass().getSimpleName());
                    broadcastStatus();
                }
            }
        });
    }

    private void runSmsMarkRead(String address) {
        final String safeAddress = address == null ? "" : address.trim();
        if (safeAddress.length() == 0 || safeAddress.length() > 80) {
            return;
        }
        SmsStore.markRead(this, safeAddress);
        smsUnread = SmsStore.unreadCount(this);
        smsRevision = SmsStore.revision(this);
        broadcastStatus();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    GatewayClient.smsMarkRead(
                            ClientConfig.host(VoiceMonitorService.this),
                            ClientConfig.token(VoiceMonitorService.this),
                            safeAddress);
                    pollSms();
                } catch (Exception error) {
                    smsDetail = "Could not mark message read: " + friendlyError(error);
                    Log.w(TAG, "SMS read update failed: "
                            + error.getClass().getSimpleName());
                    broadcastStatus();
                }
            }
        });
    }

    private synchronized void runDial(final String rawNumber) {
        final String number = DialNumber.normalize(rawNumber);
        if (number.length() == 0) {
            detail = "Enter a normal phone number first";
            notifyAndBroadcast();
            return;
        }
        if (!"IDLE".equals(state)) {
            detail = "Finish the current call before dialing";
            notifyAndBroadcast();
            return;
        }
        long now = System.currentTimeMillis();
        autoStartAudio = true;
        pendingOutgoingNumber = number;
        pendingDialAt = now;
        caller = number;
        direction = CallHistoryStore.DIRECTION_OUTGOING;
        state = "DIALING";
        detail = "Dialing " + number;
        activeHistoryId = CallHistoryStore.begin(
                this, CallHistoryStore.DIRECTION_OUTGOING, number, now);
        activeHistoryDirection = CallHistoryStore.DIRECTION_OUTGOING;
        activeHistoryState = "DIALING";
        notifyAndBroadcast();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    GatewayClient.control(
                            ClientConfig.host(VoiceMonitorService.this),
                            ClientConfig.token(VoiceMonitorService.this),
                            "DIAL " + number);
                    detail = "Dialing " + number;
                } catch (Exception error) {
                    finishFailedDial(error);
                }
                notifyAndBroadcast();
            }
        });
    }

    private synchronized void finishFailedDial(Exception error) {
        autoStartAudio = false;
        pendingOutgoingNumber = "";
        pendingDialAt = 0L;
        state = "IDLE";
        detail = friendlyError(error);
        if (activeHistoryId.length() > 0) {
            CallHistoryStore.finish(
                    this,
                    activeHistoryId,
                    CallHistoryStore.OUTCOME_FAILED,
                    System.currentTimeMillis());
            clearHistorySession();
        }
        Log.w(TAG, "dial command failed: " + detail);
    }

    private void runControl(final String command) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    GatewayClient.control(
                            ClientConfig.host(VoiceMonitorService.this),
                            ClientConfig.token(VoiceMonitorService.this),
                            command);
                    detail = "ANSWER".equals(command) ? "Answering call" : "Ending call";
                } catch (Exception error) {
                    // An overlapping Answer tap can race the first successful
                    // one and receive NOT_RINGING after the call is active.
                    // Keep the pending audio request; the status poll decides.
                    if (!"ANSWER".equals(command)) {
                        autoStartAudio = false;
                    }
                    detail = friendlyError(error);
                    Log.w(TAG, command.toLowerCase() + " command failed: " + detail);
                }
                notifyAndBroadcast();
            }
        });
    }

    private String resolveDirection(String reportedDirection, String number,
            String nextState) {
        if (CallHistoryStore.DIRECTION_INCOMING.equals(reportedDirection)
                || CallHistoryStore.DIRECTION_OUTGOING.equals(reportedDirection)) {
            return reportedDirection;
        }
        if ("RINGING".equals(nextState)) {
            return CallHistoryStore.DIRECTION_INCOMING;
        }
        if (pendingOutgoingNumber.length() > 0
                || CallHistoryStore.DIRECTION_OUTGOING.equals(activeHistoryDirection)) {
            return CallHistoryStore.DIRECTION_OUTGOING;
        }
        if ("ACTIVE".equals(nextState) && activeHistoryDirection.length() > 0) {
            return activeHistoryDirection;
        }
        return number.length() == 0 ? "" : CallHistoryStore.DIRECTION_UNKNOWN;
    }

    private synchronized void updateHistory(String previousState, String currentState,
            String currentDirection, String currentNumber, long now) {
        if ("RINGING".equals(currentState)) {
            beginHistoryIfNeeded(
                    CallHistoryStore.DIRECTION_INCOMING,
                    currentNumber,
                    now,
                    currentState);
        } else if ("DIALING".equals(currentState)) {
            long startedAt = pendingDialAt > 0L ? pendingDialAt : now;
            beginHistoryIfNeeded(
                    CallHistoryStore.DIRECTION_OUTGOING,
                    currentNumber,
                    startedAt,
                    currentState);
        } else if ("ACTIVE".equals(currentState)) {
            String historyDirection = currentDirection.length() == 0
                    ? CallHistoryStore.DIRECTION_UNKNOWN : currentDirection;
            beginHistoryIfNeeded(historyDirection, currentNumber, now, currentState);
            CallHistoryStore.markConnected(this, activeHistoryId, now);
            activeHistoryState = "ACTIVE";
        } else if ("IDLE".equals(currentState)
                && !"IDLE".equals(previousState)) {
            finishHistoryForIdle(now);
        }
    }

    private void beginHistoryIfNeeded(String historyDirection, String number,
            long startedAt, String currentState) {
        if (activeHistoryId.length() == 0) {
            activeHistoryId = CallHistoryStore.begin(
                    this, historyDirection, number, startedAt);
            activeHistoryDirection = historyDirection;
        }
        activeHistoryState = currentState;
    }

    private void finishHistoryForIdle(long now) {
        if (activeHistoryId.length() > 0) {
            String outcome;
            if (CallHistoryStore.DIRECTION_INCOMING.equals(activeHistoryDirection)
                    && "RINGING".equals(activeHistoryState)) {
                outcome = hangupRequested
                        ? CallHistoryStore.OUTCOME_DECLINED
                        : CallHistoryStore.OUTCOME_MISSED;
            } else if (CallHistoryStore.DIRECTION_OUTGOING.equals(activeHistoryDirection)
                    && !"ACTIVE".equals(activeHistoryState)) {
                outcome = CallHistoryStore.OUTCOME_NOT_CONNECTED;
            } else {
                outcome = CallHistoryStore.OUTCOME_COMPLETED;
            }
            CallHistoryStore.finish(this, activeHistoryId, outcome, now);
        }
        clearHistorySession();
        pendingOutgoingNumber = "";
        pendingDialAt = 0L;
        hangupRequested = false;
    }

    private void clearHistorySession() {
        activeHistoryId = "";
        activeHistoryDirection = "";
        activeHistoryState = "";
    }

    private synchronized void startAudio() {
        if (audioBridge != null || !"ACTIVE".equals(state)) {
            Log.i(TAG, "audio deferred; call state=" + state);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioState = "PERMISSION_REQUIRED";
            detail = "Microphone permission is required";
            notifyAndBroadcast();
            return;
        }
        try {
            startForeground(
                    NOTIFICATION_MONITOR,
                    buildMonitorNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            applyAudioRoute();
            audioState = "CONNECTING";
            Log.i(TAG, "opening two-way audio");
            audioBridge = new AudioBridge(
                    ClientConfig.host(this),
                    ClientConfig.token(this),
                    new AudioBridge.Listener() {
                        @Override
                        public void onAudioReady() {
                            audioState = "CONNECTED";
                            detail = "Two-way audio connected";
                            Log.i(TAG, "two-way audio connected");
                            notifyAndBroadcast();
                        }

                        @Override
                        public void onAudioError(String message) {
                            audioState = "ERROR";
                            detail = "Audio: " + message;
                            Log.w(TAG, "audio failed: " + message);
                            synchronized (VoiceMonitorService.this) {
                                audioBridge = null;
                            }
                            restoreMonitorForeground();
                            notifyAndBroadcast();
                        }
                    });
            audioBridge.start();
            notifyAndBroadcast();
        } catch (RuntimeException error) {
            audioBridge = null;
            audioState = "ERROR";
            detail = "Android did not allow microphone access";
            Log.w(TAG, "microphone foreground start rejected: "
                    + error.getClass().getSimpleName());
            restoreMonitorForeground();
            notifyAndBroadcast();
        }
    }

    private synchronized void stopAudio() {
        AudioBridge bridge = audioBridge;
        audioBridge = null;
        if (bridge != null) {
            bridge.stop();
        }
        audioState = "OFF";
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        restoreMonitorForeground();
        notifyAndBroadcast();
    }

    private void restoreMonitorForeground() {
        try {
            startForeground(
                    NOTIFICATION_MONITOR,
                    buildMonitorNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } catch (RuntimeException error) {
            Log.w(TAG, "could not update foreground service type");
        }
    }

    @SuppressWarnings("deprecation")
    private void applyAudioRoute() {
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(speaker);
        }
    }

    private void updateStatus(String newState, String newNetwork, String newCaller,
            String newDetail) {
        state = newState;
        network = newNetwork;
        caller = newCaller;
        direction = "";
        detail = newDetail;
        if ("OFFLINE".equals(newState) || "NOT_PAIRED".equals(newState)) {
            callReady = false;
            preferredNetworkMode = -1;
        }
        notifyAndBroadcast();
    }

    private void notifyAndBroadcast() {
        if (notifications != null) {
            notifications.notify(NOTIFICATION_MONITOR, buildMonitorNotification());
        }
        broadcastStatus();
    }

    private void broadcastStatus() {
        Intent status = new Intent(ACTION_STATUS);
        status.setPackage(getPackageName());
        status.putExtra(EXTRA_STATE, state);
        status.putExtra(EXTRA_NETWORK, network);
        status.putExtra(EXTRA_CALLER, caller);
        status.putExtra(EXTRA_DIRECTION, direction);
        status.putExtra(EXTRA_DETAIL, detail);
        status.putExtra(EXTRA_AUDIO, audioState);
        status.putExtra(EXTRA_SPEAKER, speaker);
        status.putExtra(EXTRA_CALL_READY, callReady);
        status.putExtra(EXTRA_PREFERRED_MODE, preferredNetworkMode);
        status.putExtra(EXTRA_MODE_RECOVERIES, Math.max(0, modeRecoveries));
        status.putExtra(EXTRA_SMS_UNREAD, smsUnread);
        status.putExtra(EXTRA_SMS_REVISION, smsRevision);
        status.putExtra(EXTRA_SMS_DETAIL, smsDetail);
        sendBroadcast(status);
    }

    private Notification buildMonitorNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(
                this, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title;
        if (!callReady && !"OFFLINE".equals(state) && !"NOT_PAIRED".equals(state)) {
            title = "Calls may be busy — LTE-only mode";
        } else if ("DIALING".equals(state)) {
            title = "Dialing through modem";
        } else if ("ACTIVE".equals(state)) {
            title = "Modem call active";
        } else if ("RINGING".equals(state)) {
            title = "Incoming modem call";
        } else if ("OFFLINE".equals(state)) {
            title = "Modem call client offline";
        } else {
            title = "Listening for modem calls";
        }
        String text = network.length() == 0 ? detail : detail + " · " + network;
        return new Notification.Builder(this, CHANNEL_MONITOR)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build();
    }

    private void showIncomingCall() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(
                this, 4, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent answerIntent = new Intent(this, AnswerCallActivity.class);
        answerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent answer = PendingIntent.getActivity(
                this, 2, answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent hangupIntent = new Intent(this, VoiceMonitorService.class);
        hangupIntent.setAction(ACTION_HANGUP);
        PendingIntent hangup = PendingIntent.getService(
                this, 3, hangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String displayCaller = caller.length() == 0 ? "Unknown caller" : caller;
        Notification call = new Notification.Builder(this, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentTitle("Incoming modem call")
                .setContentText(displayCaller)
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_phone, "Answer", answer).build())
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_phone, "Decline", hangup).build())
                .build();
        notifications.notify(NOTIFICATION_CALL, call);
    }

    private void showModeWarning(boolean recovered) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(
                this, 5, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = recovered
                ? "Modem call mode recovered"
                : "Modem switched to LTE-only mode";
        String message = recovered
                ? "Automatic LTE/3G mode was restored. Recovery count: " + modeRecoveries
                : "Incoming calls may be busy while the guard corrects it.";
        Notification warning = new Notification.Builder(this, CHANNEL_GUARD)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_ERROR)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build();
        notifications.notify(NOTIFICATION_GUARD, warning);
    }

    private void maybeShowSmsNotification(SmsMessage message) {
        if (message == null) {
            return;
        }
        long previous = getSharedPreferences(SMS_NOTIFICATION_PREFS, MODE_PRIVATE)
                .getLong(SMS_LAST_INCOMING_DATE, 0L);
        if (previous == 0L) {
            getSharedPreferences(SMS_NOTIFICATION_PREFS, MODE_PRIVATE)
                    .edit().putLong(SMS_LAST_INCOMING_DATE, message.date).apply();
            return;
        }
        if (message.date <= previous) {
            return;
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openIntent.putExtra(EXTRA_OPEN_TAB, "messages");
        openIntent.putExtra(EXTRA_SMS_ADDRESS, message.address);
        PendingIntent open = PendingIntent.getActivity(
                this,
                100 + Math.abs(message.address.hashCode() % 10000),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String sender = message.address.length() == 0 ? "Unknown sender" : message.address;
        Notification notification = new Notification.Builder(this, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_message)
                .setContentTitle("New SMS from " + sender)
                .setContentText(message.body)
                .setStyle(new Notification.BigTextStyle().bigText(message.body))
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build();
        notifications.notify(NOTIFICATION_MESSAGE, notification);
        getSharedPreferences(SMS_NOTIFICATION_PREFS, MODE_PRIVATE)
                .edit().putLong(SMS_LAST_INCOMING_DATE, message.date).apply();
    }

    private void createNotificationChannels() {
        NotificationChannel monitor = new NotificationChannel(
                CHANNEL_MONITOR, "Modem call connection", NotificationManager.IMPORTANCE_LOW);
        monitor.setDescription("Keeps the tablet connected to the UFI modem");
        monitor.setSound(null, null);
        notifications.createNotificationChannel(monitor);

        NotificationChannel calls = new NotificationChannel(
                CHANNEL_CALLS, "Incoming modem calls", NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription("Rings for calls received by the UFI modem");
        calls.enableVibration(true);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build();
        calls.setSound(Settings.System.DEFAULT_RINGTONE_URI, attributes);
        notifications.createNotificationChannel(calls);

        NotificationChannel guard = new NotificationChannel(
                CHANNEL_GUARD, "Modem call readiness", NotificationManager.IMPORTANCE_DEFAULT);
        guard.setDescription("Warns if the modem returns to LTE-only mode");
        guard.setSound(null, null);
        guard.enableVibration(true);
        notifications.createNotificationChannel(guard);

        NotificationChannel messages = new NotificationChannel(
                CHANNEL_MESSAGES, "Modem SMS messages", NotificationManager.IMPORTANCE_HIGH);
        messages.setDescription("Messages received by the UFI modem");
        messages.enableVibration(true);
        notifications.createNotificationChannel(messages);
    }

    @SuppressWarnings("deprecation")
    private void acquireLocks() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UfiCallClient:monitor");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(
                Context.WIFI_SERVICE);
        if (wifi != null) {
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "UfiCallClient:wifi");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
    }

    private void releaseLocks() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
    }

    private static String friendlyError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.length() == 0) {
            return "The modem command failed";
        }
        if (message.startsWith("ERR ")) {
            return message.substring(4).replace('_', ' ');
        }
        return message;
    }
}
