package com.ufi.voiceclient;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_MICROPHONE = 81;
    private static final int REQUEST_NOTIFICATIONS = 82;

    private TextView stateView;
    private TextView callerView;
    private TextView detailView;
    private TextView networkView;
    private TextView readinessView;
    private EditText numberInput;
    private Button dialButton;
    private Button answerButton;
    private Button hangupButton;
    private Button audioButton;
    private Switch speakerSwitch;
    private LinearLayout historyContainer;
    private boolean receiverRegistered;
    private boolean pendingAnswer;
    private String pendingDialNumber = "";
    private boolean incomingIntentHandled;
    private String lastState = "CONNECTING";
    private String lastAudio = "OFF";
    private String historySignature;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && VoiceMonitorService.ACTION_STATUS.equals(intent.getAction())) {
                applyStatus(intent);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        setContentView(buildInterface());
        startMonitor();
        requestNotificationPermission();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(VoiceMonitorService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHistory();
        if (answerWhenOpened() && !incomingIntentHandled) {
            incomingIntentHandled = true;
            answerCall();
        }
    }

    protected boolean answerWhenOpened() {
        return false;
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MICROPHONE) {
            boolean granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingAnswer) {
                pendingAnswer = false;
                pendingDialNumber = "";
                sendServiceAction(VoiceMonitorService.ACTION_ANSWER);
            } else if (granted && pendingDialNumber.length() > 0) {
                String number = pendingDialNumber;
                pendingDialNumber = "";
                sendDialAction(number);
            } else {
                pendingAnswer = false;
                pendingDialNumber = "";
                detailView.setText("Microphone permission is needed for call audio.");
            }
        }
    }

    private View buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 247, 251));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(24), dp(28), dp(24));
        root.setBackgroundColor(Color.rgb(244, 247, 251));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("UFI Call Client");
        title.setTextSize(27);
        title.setTextColor(Color.rgb(13, 71, 161));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("Calls through your modem's SIM");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(80, 92, 110));
        subtitle.setPadding(0, dp(4), 0, dp(24));
        root.addView(subtitle);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(22), dp(24), dp(22));
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(Color.WHITE);
        cardBackground.setCornerRadius(dp(18));
        card.setBackground(cardBackground);
        root.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        stateView = new TextView(this);
        stateView.setText("Connecting…");
        stateView.setTextSize(25);
        stateView.setTextColor(Color.rgb(27, 38, 54));
        stateView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(stateView);

        callerView = new TextView(this);
        callerView.setText("");
        callerView.setTextSize(20);
        callerView.setTextColor(Color.rgb(21, 101, 192));
        callerView.setPadding(0, dp(10), 0, 0);
        callerView.setVisibility(View.GONE);
        card.addView(callerView);

        networkView = new TextView(this);
        networkView.setText("Network: —");
        networkView.setTextSize(14);
        networkView.setTextColor(Color.rgb(89, 99, 115));
        networkView.setPadding(0, dp(14), 0, 0);
        card.addView(networkView);

        readinessView = new TextView(this);
        readinessView.setText("Call readiness: checking…");
        readinessView.setTextSize(14);
        readinessView.setTextColor(Color.rgb(89, 99, 115));
        readinessView.setPadding(0, dp(6), 0, 0);
        card.addView(readinessView);

        detailView = new TextView(this);
        detailView.setText("Starting the secure modem connection");
        detailView.setTextSize(14);
        detailView.setTextColor(Color.rgb(89, 99, 115));
        detailView.setPadding(0, dp(6), 0, 0);
        card.addView(detailView);

        TextView dialLabel = new TextView(this);
        dialLabel.setText("Make a call");
        dialLabel.setTextSize(18);
        dialLabel.setTextColor(Color.rgb(27, 38, 54));
        dialLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dialLabel.setPadding(0, dp(26), 0, dp(8));
        root.addView(dialLabel);

        LinearLayout dialRow = new LinearLayout(this);
        dialRow.setOrientation(LinearLayout.HORIZONTAL);
        dialRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(dialRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        numberInput = new EditText(this);
        numberInput.setHint("Phone number");
        numberInput.setSingleLine(true);
        numberInput.setTextSize(18);
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        dialRow.addView(numberInput, new LinearLayout.LayoutParams(0, dp(54), 1f));

        Space dialGap = new Space(this);
        dialRow.addView(dialGap, new LinearLayout.LayoutParams(dp(12), 1));

        dialButton = makeButton("Call", Color.rgb(21, 101, 192));
        dialButton.setEnabled(false);
        dialButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialNumber();
            }
        });
        dialRow.addView(dialButton, new LinearLayout.LayoutParams(dp(112), dp(54)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_HORIZONTAL);
        buttons.setPadding(0, dp(28), 0, 0);
        root.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        answerButton = makeButton("Answer", Color.rgb(25, 135, 84));
        answerButton.setEnabled(false);
        answerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                answerCall();
            }
        });
        buttons.addView(answerButton, weightedButtonParams());

        Space gap = new Space(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(dp(14), 1));

        hangupButton = makeButton("Hang up", Color.rgb(198, 40, 40));
        hangupButton.setEnabled(false);
        hangupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendServiceAction(VoiceMonitorService.ACTION_HANGUP);
            }
        });
        buttons.addView(hangupButton, weightedButtonParams());

        audioButton = makeButton("Connect audio", Color.rgb(21, 101, 192));
        audioButton.setEnabled(false);
        audioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ("CONNECTED".equals(lastAudio) || "CONNECTING".equals(lastAudio)) {
                    sendServiceAction(VoiceMonitorService.ACTION_AUDIO_STOP);
                } else {
                    startAudioManually();
                }
            }
        });
        LinearLayout.LayoutParams audioParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        audioParams.setMargins(0, dp(18), 0, 0);
        root.addView(audioButton, audioParams);

        speakerSwitch = new Switch(this);
        speakerSwitch.setText("Use tablet speaker");
        speakerSwitch.setTextSize(16);
        speakerSwitch.setTextColor(Color.rgb(45, 55, 70));
        speakerSwitch.setChecked(true);
        speakerSwitch.setPadding(0, dp(18), 0, 0);
        speakerSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Intent intent = new Intent(MainActivity.this, VoiceMonitorService.class);
                intent.setAction(VoiceMonitorService.ACTION_SPEAKER);
                intent.putExtra(VoiceMonitorService.EXTRA_SPEAKER, checked);
                startService(intent);
            }
        });
        root.addView(speakerSwitch);

        TextView historyTitle = new TextView(this);
        historyTitle.setText("Call history");
        historyTitle.setTextSize(20);
        historyTitle.setTextColor(Color.rgb(27, 38, 54));
        historyTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        historyTitle.setPadding(0, dp(28), 0, dp(10));
        root.addView(historyTitle);

        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(historyContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setText("Keep the connection notification enabled so incoming calls can ring. "
                + "Headphones reduce echo during speaker calls. Emergency and service-code "
                + "dialing is intentionally blocked.");
        note.setTextSize(13);
        note.setTextColor(Color.rgb(105, 113, 126));
        note.setPadding(0, dp(22), 0, 0);
        root.addView(note);
        return scroll;
    }

    private Button makeButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setAllCaps(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(14));
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        return new LinearLayout.LayoutParams(0, dp(54), 1f);
    }

    private void applyStatus(Intent intent) {
        lastState = intent.getStringExtra(VoiceMonitorService.EXTRA_STATE);
        lastAudio = intent.getStringExtra(VoiceMonitorService.EXTRA_AUDIO);
        String network = safe(intent.getStringExtra(VoiceMonitorService.EXTRA_NETWORK));
        String caller = safe(intent.getStringExtra(VoiceMonitorService.EXTRA_CALLER));
        String direction = safe(intent.getStringExtra(VoiceMonitorService.EXTRA_DIRECTION));
        String detail = safe(intent.getStringExtra(VoiceMonitorService.EXTRA_DETAIL));
        boolean speaker = intent.getBooleanExtra(VoiceMonitorService.EXTRA_SPEAKER, true);
        boolean callReady = intent.getBooleanExtra(
                VoiceMonitorService.EXTRA_CALL_READY, false);
        int preferredMode = intent.getIntExtra(
                VoiceMonitorService.EXTRA_PREFERRED_MODE, -1);
        int recoveries = intent.getIntExtra(
                VoiceMonitorService.EXTRA_MODE_RECOVERIES, 0);

        String displayState;
        if ("RINGING".equals(lastState)) {
            displayState = "Incoming call";
        } else if ("DIALING".equals(lastState)) {
            displayState = "Calling";
        } else if ("ACTIVE".equals(lastState)) {
            displayState = "Call in progress";
        } else if ("IDLE".equals(lastState)) {
            displayState = "Ready for calls";
        } else if ("NOT_PAIRED".equals(lastState)) {
            displayState = "Setup required";
        } else if ("OFFLINE".equals(lastState)) {
            displayState = "Modem offline";
        } else {
            displayState = "Connecting…";
        }
        stateView.setText(displayState);
        if (caller.length() > 0 && ("RINGING".equals(lastState)
                || "DIALING".equals(lastState) || "ACTIVE".equals(lastState))) {
            String prefix = CallHistoryStore.DIRECTION_OUTGOING.equals(direction)
                    ? "To: " : "From: ";
            callerView.setText(prefix + caller);
            callerView.setVisibility(View.VISIBLE);
        } else {
            callerView.setVisibility(View.GONE);
        }
        networkView.setText("Network: " + (network.length() == 0 ? "—" : network));
        if (preferredMode < 0) {
            readinessView.setText("Call readiness: unavailable");
            readinessView.setTextColor(Color.rgb(89, 99, 115));
        } else if (callReady) {
            String recovered = recoveries == 0
                    ? ""
                    : " · LTE-only recovered " + recoveries
                            + (recoveries == 1 ? " time" : " times");
            readinessView.setText("Call readiness: Ready (LTE/3G automatic)" + recovered);
            readinessView.setTextColor(Color.rgb(25, 135, 84));
        } else {
            readinessView.setText("Call readiness: LTE-only — calls may be busy");
            readinessView.setTextColor(Color.rgb(198, 40, 40));
        }
        String audioDetail = "CONNECTED".equals(lastAudio) ? " · audio connected" : "";
        detailView.setText(detail + audioDetail);
        answerButton.setEnabled("RINGING".equals(lastState));
        hangupButton.setEnabled("RINGING".equals(lastState)
                || "DIALING".equals(lastState) || "ACTIVE".equals(lastState));
        audioButton.setEnabled("ACTIVE".equals(lastState));
        boolean canDial = "IDLE".equals(lastState) && callReady;
        dialButton.setEnabled(canDial);
        numberInput.setEnabled(canDial);
        audioButton.setText(
                "CONNECTED".equals(lastAudio) || "CONNECTING".equals(lastAudio)
                        ? "Disconnect audio" : "Connect audio");
        if (speakerSwitch.isChecked() != speaker) {
            speakerSwitch.setChecked(speaker);
        }
        refreshHistory();
    }

    private void answerCall() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAnswer = true;
            pendingDialNumber = "";
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }
        sendServiceAction(VoiceMonitorService.ACTION_ANSWER);
    }

    private void dialNumber() {
        String number = DialNumber.normalize(numberInput.getText().toString());
        if (number.length() == 0) {
            detailView.setText("Enter a normal phone number with 6 to 20 digits.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAnswer = false;
            pendingDialNumber = number;
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }
        sendDialAction(number);
    }

    private void sendDialAction(String number) {
        Intent intent = new Intent(this, VoiceMonitorService.class);
        intent.setAction(VoiceMonitorService.ACTION_DIAL);
        intent.putExtra(VoiceMonitorService.EXTRA_DIAL_NUMBER, number);
        startService(intent);
    }

    private void refreshHistory() {
        if (historyContainer == null) {
            return;
        }
        List<CallHistoryStore.Entry> entries = CallHistoryStore.entries(this);
        StringBuilder signature = new StringBuilder();
        int visibleCount = entries.size();
        for (int index = 0; index < visibleCount; index++) {
            CallHistoryStore.Entry entry = entries.get(index);
            signature.append(entry.id).append('|')
                    .append(entry.outcome).append('|')
                    .append(entry.connectedAt).append('|')
                    .append(entry.endedAt).append(';');
        }
        String nextSignature = signature.toString();
        if (nextSignature.equals(historySignature)) {
            return;
        }
        historySignature = nextSignature;
        historyContainer.removeAllViews();
        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No calls yet");
            empty.setTextSize(14);
            empty.setTextColor(Color.rgb(105, 113, 126));
            empty.setPadding(0, dp(4), 0, dp(8));
            historyContainer.addView(empty);
            return;
        }
        DateFormat dateFormat = DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT);
        for (int index = 0; index < visibleCount; index++) {
            final CallHistoryStore.Entry entry = entries.get(index);
            String directionLabel;
            if (CallHistoryStore.DIRECTION_OUTGOING.equals(entry.direction)) {
                directionLabel = "Outgoing";
            } else if (CallHistoryStore.DIRECTION_INCOMING.equals(entry.direction)) {
                directionLabel = "Incoming";
            } else {
                directionLabel = "Call";
            }
            String number = entry.number.length() == 0 ? "Unknown number" : entry.number;
            StringBuilder detail = new StringBuilder();
            detail.append(directionLabel).append(" · ").append(number)
                    .append('\n')
                    .append(dateFormat.format(new Date(entry.startedAt)))
                    .append(" · ").append(entry.outcome);
            if (entry.connectedAt > 0L && entry.endedAt > entry.connectedAt) {
                detail.append(" · ").append(formatDuration(entry.endedAt - entry.connectedAt));
            }

            TextView row = new TextView(this);
            row.setText(detail.toString());
            row.setTextSize(15);
            row.setTextColor(Color.rgb(45, 55, 70));
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.WHITE);
            background.setCornerRadius(dp(12));
            row.setBackground(background);
            if (entry.number.length() > 0) {
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        numberInput.setText(entry.number);
                        numberInput.setSelection(entry.number.length());
                    }
                });
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, dp(8));
            historyContainer.addView(row, rowParams);
        }
    }

    private static String formatDuration(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m " + remainder + "s";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainder + "s";
        }
        return remainder + "s";
    }

    private void startAudioManually() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }
        sendServiceAction(VoiceMonitorService.ACTION_AUDIO_START);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    private void startMonitor() {
        Intent intent = new Intent(this, VoiceMonitorService.class);
        intent.setAction(VoiceMonitorService.ACTION_START);
        startForegroundService(intent);
    }

    private void sendServiceAction(String action) {
        Intent intent = new Intent(this, VoiceMonitorService.class);
        intent.setAction(action);
        startService(intent);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
