package com.ufi.voiceclient;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQUEST_MICROPHONE = 81;
    private static final int REQUEST_NOTIFICATIONS = 82;
    private static final int TAB_CALLS = 0;
    private static final int TAB_KEYPAD = 1;
    private static final int TAB_MESSAGES = 2;

    private static final int COLOR_BACKGROUND = 0xfff7f9fe;
    private static final int COLOR_SURFACE = 0xffffffff;
    private static final int COLOR_TEXT = 0xff101b35;
    private static final int COLOR_MUTED = 0xff627087;
    private static final int COLOR_BLUE = 0xff0b67d1;
    private static final int COLOR_BLUE_SOFT = 0xffe7f0ff;
    private static final int COLOR_GREEN = 0xff159455;
    private static final int COLOR_GREEN_SOFT = 0xffe5f6ec;
    private static final int COLOR_RED = 0xffd92d20;
    private static final int COLOR_DIVIDER = 0xffe3e8f1;

    private FrameLayout contentHost;
    private View callsScreen;
    private View keypadScreen;
    private FrameLayout messagesScreen;
    private LinearLayout callsContainer;
    private LinearLayout callBanner;
    private TextView callStateView;
    private TextView callNumberView;
    private TextView callDetailView;
    private TextView statusText;
    private View statusDot;
    private EditText keypadNumber;
    private ImageButton keypadCallButton;
    private Button answerButton;
    private Button hangupButton;
    private Button audioButton;
    private final LinearLayout[] navItems = new LinearLayout[3];
    private final ImageView[] navIcons = new ImageView[3];
    private final TextView[] navLabels = new TextView[3];
    private TextView messageBadge;

    private boolean receiverRegistered;
    private boolean pendingAnswer;
    private boolean pendingAudioStart;
    private boolean incomingIntentHandled;
    private boolean composingNewMessage;
    private boolean lastCallReady;
    private boolean lastSpeaker = true;
    private int selectedTab = TAB_CALLS;
    private int lastModeRecoveries;
    private int lastSmsUnread;
    private long lastSmsRevision = -1L;
    private String pendingDialNumber = "";
    private String lastState = "CONNECTING";
    private String lastAudio = "OFF";
    private String lastNetwork = "";
    private String lastSmsDetail = "Syncing messages";
    private String openConversationAddress = "";
    private String historySignature;

    private EditText messageAddressInput;
    private EditText messageBodyInput;

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
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(buildInterface());
        startMonitor();
        requestNotificationPermission();
        handleNavigationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNavigationIntent(intent);
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
        refreshCalls();
        if (selectedTab == TAB_MESSAGES) {
            renderMessages();
        }
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
    public void onBackPressed() {
        if (selectedTab == TAB_MESSAGES
                && (openConversationAddress.length() > 0 || composingNewMessage)) {
            openConversationAddress = "";
            composingNewMessage = false;
            hideKeyboard();
            renderMessages();
            return;
        }
        if (selectedTab != TAB_CALLS) {
            selectTab(TAB_CALLS);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_MICROPHONE) {
            return;
        }
        boolean granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            pendingAnswer = false;
            pendingAudioStart = false;
            pendingDialNumber = "";
            showMessage("Microphone permission is needed for call audio");
            return;
        }
        if (pendingAnswer) {
            pendingAnswer = false;
            pendingDialNumber = "";
            pendingAudioStart = false;
            sendServiceAction(VoiceMonitorService.ACTION_ANSWER);
        } else if (pendingDialNumber.length() > 0) {
            String number = pendingDialNumber;
            pendingDialNumber = "";
            pendingAudioStart = false;
            sendDialAction(number);
        } else if (pendingAudioStart) {
            pendingAudioStart = false;
            sendServiceAction(VoiceMonitorService.ACTION_AUDIO_START);
        }
    }

    private View buildInterface() {
        boolean landscape = isLandscape();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(landscape ? 68 : 82)));

        callBanner = buildCallBanner();
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bannerParams.setMargins(dp(landscape ? 20 : 28), 0,
                dp(landscape ? 20 : 28), dp(landscape ? 6 : 12));
        root.addView(callBanner, bannerParams);

        contentHost = new FrameLayout(this);
        callsScreen = buildCallsScreen();
        keypadScreen = buildKeypadScreen();
        messagesScreen = new FrameLayout(this);
        contentHost.addView(callsScreen, matchFrame());
        contentHost.addView(keypadScreen, matchFrame());
        contentHost.addView(messagesScreen, matchFrame());
        root.addView(contentHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(landscape ? 82 : 112)));
        selectTab(TAB_CALLS);
        return root;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(28), dp(10), dp(20), dp(6));

        TextView title = text("Phone", isLandscape() ? 27 : 31, COLOR_TEXT, true);
        bar.addView(title);
        bar.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(14), dp(9), dp(14), dp(9));
        status.setBackground(rounded(COLOR_GREEN_SOFT, 24));
        statusDot = new View(this);
        statusDot.setBackground(rounded(COLOR_GREEN, 20));
        status.addView(statusDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        statusText = text("Connecting", 14, COLOR_MUTED, false);
        LinearLayout.LayoutParams statusTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusTextParams.setMargins(dp(8), 0, 0, 0);
        status.addView(statusText, statusTextParams);
        bar.addView(status);

        ImageButton settings = iconButton(
                R.drawable.ic_settings, COLOR_TEXT, Color.TRANSPARENT, 44);
        settings.setContentDescription("Connection settings");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showConnectionDialog();
            }
        });
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        settingsParams.setMargins(dp(10), 0, 0, 0);
        bar.addView(settings, settingsParams);
        return bar;
    }

    private LinearLayout buildCallBanner() {
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(20), dp(16), dp(20), dp(16));
        banner.setBackground(rounded(COLOR_SURFACE, 22));
        banner.setElevation(dp(2));
        banner.setVisibility(View.GONE);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        callStateView = text("Incoming call", 19, COLOR_TEXT, true);
        heading.addView(callStateView);
        heading.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1f));
        callNumberView = text("", 17, COLOR_BLUE, true);
        heading.addView(callNumberView);
        banner.addView(heading);

        callDetailView = text("", 13, COLOR_MUTED, false);
        callDetailView.setPadding(0, dp(4), 0, dp(12));
        banner.addView(callDetailView);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        answerButton = actionButton("Answer", COLOR_GREEN);
        answerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                answerCall();
            }
        });
        actions.addView(answerButton, weightedActionParams());

        Space gapOne = new Space(this);
        actions.addView(gapOne, new LinearLayout.LayoutParams(dp(10), 1));
        hangupButton = actionButton("Hang up", COLOR_RED);
        hangupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendServiceAction(VoiceMonitorService.ACTION_HANGUP);
            }
        });
        actions.addView(hangupButton, weightedActionParams());

        Space gapTwo = new Space(this);
        actions.addView(gapTwo, new LinearLayout.LayoutParams(dp(10), 1));
        audioButton = actionButton("Audio", COLOR_BLUE);
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
        actions.addView(audioButton, weightedActionParams());
        banner.addView(actions);
        return banner;
    }

    private View buildCallsScreen() {
        FrameLayout screen = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(18), dp(28), dp(104));
        ScrollView.LayoutParams contentParams = new ScrollView.LayoutParams(
                isLandscape() ? contentWidth(760) : ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT);
        contentParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        scroll.addView(content, contentParams);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Recent calls", 27, COLOR_TEXT, true);
        heading.addView(title);
        heading.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView seeAll = text("Last 100", 14, COLOR_BLUE, true);
        heading.addView(seeAll);
        content.addView(heading);

        callsContainer = new LinearLayout(this);
        callsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams callsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        callsParams.setMargins(0, dp(16), 0, 0);
        content.addView(callsContainer, callsParams);
        screen.addView(scroll, matchFrame());

        ImageButton dialpad = iconButton(R.drawable.ic_dialpad, Color.WHITE, COLOR_BLUE, 68);
        dialpad.setContentDescription("Open keypad");
        dialpad.setElevation(dp(6));
        dialpad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectTab(TAB_KEYPAD);
            }
        });
        FrameLayout.LayoutParams dialpadParams = new FrameLayout.LayoutParams(dp(68), dp(68));
        dialpadParams.gravity = Gravity.BOTTOM | Gravity.END;
        dialpadParams.setMargins(0, 0, dp(28), dp(24));
        screen.addView(dialpad, dialpadParams);
        return screen;
    }

    private View buildKeypadScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        boolean landscape = isLandscape();
        root.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setGravity(landscape ? Gravity.CENTER : Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(landscape ? 28 : 40), dp(landscape ? 6 : 18),
                dp(landscape ? 28 : 40), dp(landscape ? 8 : 24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout introduction = new LinearLayout(this);
        introduction.setOrientation(LinearLayout.VERTICAL);
        introduction.setGravity(landscape ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
        TextView title = text("Keypad", 27, COLOR_TEXT, true);
        title.setGravity(Gravity.START);
        introduction.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout numberRow = new LinearLayout(this);
        numberRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams numberRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(landscape ? 68 : 76));
        numberRowParams.setMargins(0, dp(landscape ? 10 : 18), 0, dp(landscape ? 8 : 12));
        introduction.addView(numberRow, numberRowParams);
        keypadNumber = new EditText(this);
        keypadNumber.setSingleLine(true);
        keypadNumber.setGravity(Gravity.CENTER);
        keypadNumber.setTextSize(27);
        keypadNumber.setTextColor(COLOR_TEXT);
        keypadNumber.setHintTextColor(COLOR_MUTED);
        keypadNumber.setHint("Enter number");
        keypadNumber.setInputType(InputType.TYPE_CLASS_PHONE);
        keypadNumber.setShowSoftInputOnFocus(false);
        keypadNumber.setBackgroundColor(Color.TRANSPARENT);
        numberRow.addView(keypadNumber, new LinearLayout.LayoutParams(0, dp(70), 1f));
        ImageButton backspace = iconButton(
                R.drawable.ic_backspace, COLOR_MUTED, Color.TRANSPARENT, 48);
        backspace.setContentDescription("Delete digit");
        backspace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int end = keypadNumber.getSelectionEnd();
                if (end < 0) {
                    end = keypadNumber.length();
                }
                if (end > 0) {
                    keypadNumber.getText().delete(end - 1, end);
                }
            }
        });
        backspace.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                keypadNumber.setText("");
                return true;
            }
        });
        numberRow.addView(backspace, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView note = text(
                "Emergency numbers and service codes are intentionally blocked",
                12, COLOR_MUTED, false);
        note.setGravity(landscape ? Gravity.START : Gravity.CENTER);
        introduction.addView(note);

        if (landscape) {
            LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            introParams.setMargins(0, 0, dp(28), 0);
            root.addView(introduction, introParams);
        } else {
            root.addView(introduction, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        keypad.setGravity(Gravity.CENTER_HORIZONTAL);

        String[][] keys = {
                {"1", "2\nABC", "3\nDEF"},
                {"4\nGHI", "5\nJKL", "6\nMNO"},
                {"7\nPQRS", "8\nTUV", "9\nWXYZ"},
                {"*", "0\n+", "#"}
        };
        for (int rowIndex = 0; rowIndex < keys.length; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            for (int column = 0; column < keys[rowIndex].length; column++) {
                final String key = keys[rowIndex][column].substring(0, 1);
                TextView button = text(keys[rowIndex][column], 23, COLOR_TEXT, false);
                button.setGravity(Gravity.CENTER);
                button.setLineSpacing(0, 0.82f);
                button.setBackground(rippleRounded(0xffeef3fb, 48));
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        appendDialCharacter(key);
                    }
                });
                if ("0".equals(key)) {
                    button.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View view) {
                            appendDialCharacter("+");
                            return true;
                        }
                    });
                }
                int keySize = landscape ? 62 : 82;
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                        dp(keySize), dp(keySize));
                keyParams.setMargins(dp(landscape ? 7 : 12), dp(landscape ? 2 : 5),
                        dp(landscape ? 7 : 12), dp(landscape ? 2 : 5));
                row.addView(button, keyParams);
            }
            keypad.addView(row);
        }

        keypadCallButton = iconButton(R.drawable.ic_phone, Color.WHITE, COLOR_BLUE, 76);
        keypadCallButton.setContentDescription("Call number");
        keypadCallButton.setElevation(dp(5));
        keypadCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialNumber(keypadNumber.getText().toString());
            }
        });
        int callSize = landscape ? 62 : 76;
        LinearLayout.LayoutParams callParams = new LinearLayout.LayoutParams(
                dp(callSize), dp(callSize));
        callParams.setMargins(0, dp(landscape ? 5 : 16), 0, dp(landscape ? 2 : 8));
        keypad.addView(keypadCallButton, callParams);
        if (landscape) {
            root.addView(keypad, new LinearLayout.LayoutParams(
                    dp(250), LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            root.addView(keypad, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        return scroll;
    }

    private View buildBottomNavigation() {
        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(20), dp(isLandscape() ? 4 : 8), dp(20),
                dp(isLandscape() ? 8 : 26));
        navigation.setBackgroundColor(COLOR_SURFACE);
        navigation.setElevation(dp(8));
        addNavItem(navigation, TAB_CALLS, R.drawable.ic_phone, "Calls");
        addNavItem(navigation, TAB_KEYPAD, R.drawable.ic_dialpad, "Keypad");
        addNavItem(navigation, TAB_MESSAGES, R.drawable.ic_message, "Messages");
        return navigation;
    }

    private void addNavItem(LinearLayout navigation, final int tab, int icon, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(8), dp(5), dp(8), dp(4));
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setColorFilter(COLOR_MUTED);
        item.addView(image, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setGravity(Gravity.CENTER);
        TextView text = text(label, 12, COLOR_MUTED, false);
        labelRow.addView(text);
        if (tab == TAB_MESSAGES) {
            messageBadge = text("0", 10, Color.WHITE, true);
            messageBadge.setGravity(Gravity.CENTER);
            messageBadge.setBackground(rounded(COLOR_RED, 20));
            messageBadge.setVisibility(View.GONE);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            badgeParams.setMargins(dp(5), 0, 0, 0);
            labelRow.addView(messageBadge, badgeParams);
        }
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(3), 0, 0);
        item.addView(labelRow, labelParams);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectTab(tab);
            }
        });
        navItems[tab] = item;
        navIcons[tab] = image;
        navLabels[tab] = text;
        navigation.addView(item, new LinearLayout.LayoutParams(
                0, dp(isLandscape() ? 66 : 70), 1f));
    }

    private void selectTab(int tab) {
        selectedTab = tab;
        callsScreen.setVisibility(tab == TAB_CALLS ? View.VISIBLE : View.GONE);
        keypadScreen.setVisibility(tab == TAB_KEYPAD ? View.VISIBLE : View.GONE);
        messagesScreen.setVisibility(tab == TAB_MESSAGES ? View.VISIBLE : View.GONE);
        for (int index = 0; index < navItems.length; index++) {
            boolean selected = index == tab;
            navItems[index].setBackground(selected ? rounded(COLOR_BLUE_SOFT, 24) : null);
            navIcons[index].setColorFilter(selected ? COLOR_BLUE : COLOR_MUTED);
            navLabels[index].setTextColor(selected ? COLOR_BLUE : COLOR_MUTED);
            navLabels[index].setTypeface(Typeface.DEFAULT,
                    selected ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (tab == TAB_CALLS) {
            refreshCalls();
        } else if (tab == TAB_KEYPAD) {
            hideKeyboard();
            keypadNumber.clearFocus();
        } else {
            hideKeyboard();
            renderMessages();
        }
    }

    private void refreshCalls() {
        if (callsContainer == null) {
            return;
        }
        List<CallHistoryStore.Entry> entries = CallHistoryStore.entries(this);
        StringBuilder signature = new StringBuilder();
        for (CallHistoryStore.Entry entry : entries) {
            signature.append(entry.id).append('|').append(entry.outcome).append('|')
                    .append(entry.connectedAt).append('|').append(entry.endedAt).append(';');
        }
        String next = signature.toString();
        if (next.equals(historySignature)) {
            return;
        }
        historySignature = next;
        callsContainer.removeAllViews();
        if (entries.isEmpty()) {
            TextView empty = text("No calls yet", 15, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(70), 0, dp(20));
            callsContainer.addView(empty);
            return;
        }
        DateFormat format = new SimpleDateFormat("MMM d\nHH:mm", Locale.US);
        for (CallHistoryStore.Entry entry : entries) {
            callsContainer.addView(buildCallRow(entry, format));
            callsContainer.addView(divider());
        }
    }

    private View buildCallRow(final CallHistoryStore.Entry entry, DateFormat format) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.addView(avatarIcon(entry.number, R.drawable.ic_phone),
                new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        String number = entry.number.length() == 0 ? "Unknown number" : entry.number;
        middle.addView(text(number, 17, COLOR_TEXT, true));
        String direction;
        if (CallHistoryStore.DIRECTION_INCOMING.equals(entry.direction)) {
            direction = "Incoming";
        } else if (CallHistoryStore.DIRECTION_OUTGOING.equals(entry.direction)) {
            direction = "Outgoing";
        } else {
            direction = "Call";
        }
        String detail = direction + " · " + entry.outcome;
        if (entry.connectedAt > 0L && entry.endedAt > entry.connectedAt) {
            detail += " · " + formatDuration(entry.endedAt - entry.connectedAt);
        }
        middle.addView(text(detail, 13,
                CallHistoryStore.OUTCOME_MISSED.equals(entry.outcome) ? COLOR_RED : COLOR_MUTED,
                false));
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        middleParams.setMargins(dp(14), 0, dp(10), 0);
        row.addView(middle, middleParams);

        TextView time = text(format.format(new Date(entry.startedAt)), 12, COLOR_MUTED, false);
        time.setGravity(Gravity.END);
        row.addView(time);

        ImageButton call = iconButton(R.drawable.ic_phone, COLOR_MUTED, Color.TRANSPARENT, 46);
        call.setContentDescription("Call " + number);
        call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialNumber(entry.number);
            }
        });
        LinearLayout.LayoutParams callParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        callParams.setMargins(dp(8), 0, 0, 0);
        row.addView(call, callParams);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                keypadNumber.setText(entry.number);
                keypadNumber.setSelection(entry.number.length());
                selectTab(TAB_KEYPAD);
            }
        });
        return row;
    }

    private void renderMessages() {
        if (messagesScreen == null) {
            return;
        }
        messagesScreen.removeAllViews();
        if (openConversationAddress.length() > 0 || composingNewMessage) {
            messagesScreen.addView(buildMessageThread(), matchFrame());
        } else {
            messagesScreen.addView(buildConversationList(), matchFrame());
        }
    }

    private View buildConversationList() {
        FrameLayout screen = new FrameLayout(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), 0);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("Messages", 27, COLOR_TEXT, true));
        header.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1f));
        Button compose = outlineButton("New");
        compose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                composingNewMessage = true;
                openConversationAddress = "";
                renderMessages();
            }
        });
        header.addView(compose, new LinearLayout.LayoutParams(dp(92), dp(44)));
        root.addView(header);

        if (!"Messages synced directly with modem".equals(lastSmsDetail)) {
            TextView sync = text(lastSmsDetail, 13, COLOR_MUTED, false);
            sync.setPadding(0, dp(4), 0, dp(10));
            root.addView(sync);
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        List<Conversation> conversations = conversations(SmsStore.messages(this));
        if (conversations.isEmpty()) {
            TextView empty = text("No SMS messages on the modem yet", 15, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(70), 0, 0);
            list.addView(empty);
        } else {
            for (final Conversation conversation : conversations) {
                list.addView(buildConversationRow(conversation));
                list.addView(divider());
            }
        }
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                isLandscape() ? contentWidth(900) : FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        rootParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        screen.addView(root, rootParams);
        return screen;
    }

    private View buildConversationRow(final Conversation conversation) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(13));

        row.addView(avatarIcon(conversation.address, R.drawable.ic_message),
                new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.addView(text(displayAddress(conversation.address), 17, COLOR_TEXT,
                conversation.unread > 0));
        String preview = conversation.latest.body.replace('\n', ' ').replace('\r', ' ');
        if (preview.length() > 68) {
            preview = preview.substring(0, 67) + "…";
        }
        middle.addView(text(preview, 13, COLOR_MUTED, false));
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        middleParams.setMargins(dp(14), 0, dp(10), 0);
        row.addView(middle, middleParams);

        LinearLayout trailing = new LinearLayout(this);
        trailing.setOrientation(LinearLayout.VERTICAL);
        trailing.setGravity(Gravity.END);
        trailing.addView(text(relativeTime(conversation.latest.date), 12, COLOR_MUTED, false));
        if (conversation.unread > 0) {
            TextView unread = text(Integer.toString(conversation.unread), 11, Color.WHITE, true);
            unread.setGravity(Gravity.CENTER);
            unread.setBackground(rounded(COLOR_BLUE, 22));
            LinearLayout.LayoutParams unreadParams = new LinearLayout.LayoutParams(dp(24), dp(24));
            unreadParams.setMargins(0, dp(5), 0, 0);
            trailing.addView(unread, unreadParams);
        }
        row.addView(trailing);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openConversation(conversation.address);
            }
        });
        return row;
    }

    private View buildMessageThread() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(10), dp(22), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = iconButton(R.drawable.ic_back, COLOR_TEXT, Color.TRANSPARENT, 46);
        back.setContentDescription("Back to messages");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openConversationAddress = "";
                composingNewMessage = false;
                hideKeyboard();
                renderMessages();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));

        if (composingNewMessage) {
            messageAddressInput = new EditText(this);
            messageAddressInput.setHint("Recipient number");
            messageAddressInput.setTextSize(18);
            messageAddressInput.setSingleLine(true);
            messageAddressInput.setInputType(InputType.TYPE_CLASS_PHONE);
            messageAddressInput.setTextColor(COLOR_TEXT);
            messageAddressInput.setHintTextColor(COLOR_MUTED);
            messageAddressInput.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(54), 1f);
            addressParams.setMargins(dp(6), 0, dp(8), 0);
            header.addView(messageAddressInput, addressParams);
        } else {
            TextView address = text(displayAddress(openConversationAddress), 19, COLOR_TEXT, true);
            LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            addressParams.setMargins(dp(6), 0, dp(8), 0);
            header.addView(address, addressParams);
        }

        ImageButton call = iconButton(R.drawable.ic_phone, COLOR_BLUE, Color.TRANSPARENT, 46);
        String possibleNumber = composingNewMessage ? "" : openConversationAddress;
        boolean callable = DialNumber.normalize(possibleNumber).length() > 0;
        call.setEnabled(callable);
        call.setAlpha(callable ? 1f : 0.3f);
        call.setContentDescription("Call message sender");
        call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialNumber(openConversationAddress);
            }
        });
        header.addView(call, new LinearLayout.LayoutParams(dp(46), dp(46)));
        root.addView(header);
        root.addView(divider());

        final ScrollView threadScroll = new ScrollView(this);
        final LinearLayout messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(4), dp(14), dp(4), dp(14));
        if (!composingNewMessage) {
            List<SmsMessage> all = SmsStore.messages(this);
            boolean found = false;
            for (int index = all.size() - 1; index >= 0; index--) {
                SmsMessage message = all.get(index);
                if (openConversationAddress.equals(message.address)) {
                    messages.addView(buildMessageBubble(message));
                    found = true;
                }
            }
            if (!found) {
                TextView empty = text("Start the conversation below", 14, COLOR_MUTED, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(60), 0, 0);
                messages.addView(empty);
            }
        } else {
            TextView empty = text("Send a direct SMS through the modem", 14, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, 0);
            messages.addView(empty);
        }
        threadScroll.addView(messages, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(threadScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);
        messageBodyInput = new EditText(this);
        messageBodyInput.setHint("Text message");
        messageBodyInput.setTextSize(16);
        messageBodyInput.setTextColor(COLOR_TEXT);
        messageBodyInput.setHintTextColor(COLOR_MUTED);
        messageBodyInput.setMinHeight(dp(52));
        messageBodyInput.setMaxLines(4);
        messageBodyInput.setPadding(dp(18), dp(12), dp(18), dp(12));
        messageBodyInput.setBackground(rippleRounded(0xffeef3f9, 26));
        composer.addView(messageBodyInput, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton send = iconButton(R.drawable.ic_send, Color.WHITE, COLOR_BLUE, 54);
        send.setContentDescription("Send SMS");
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendSms();
            }
        });
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        sendParams.setMargins(dp(10), 0, 0, 0);
        composer.addView(send, sendParams);
        root.addView(composer);

        threadScroll.post(new Runnable() {
            @Override
            public void run() {
                threadScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
        if (composingNewMessage && messageAddressInput != null) {
            messageAddressInput.requestFocus();
            showKeyboard(messageAddressInput);
        }
        return root;
    }

    private View buildMessageBubble(SmsMessage message) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setGravity(message.incoming() ? Gravity.START : Gravity.END);
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(16), dp(11), dp(16), dp(9));
        bubble.setBackground(rounded(
                message.incoming() ? 0xffedf1f6 : 0xffdceaff, 20));
        TextView body = text(message.body, 16, COLOR_TEXT, false);
        body.setMaxWidth(dp(520));
        body.setTextIsSelectable(true);
        bubble.addView(body);
        TextView time = text(relativeTime(message.date), 11, COLOR_MUTED, false);
        time.setGravity(Gravity.END);
        time.setPadding(0, dp(4), 0, 0);
        bubble.addView(time);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bubbleParams.setMargins(0, dp(4), 0, dp(4));
        wrapper.addView(bubble, bubbleParams);
        return wrapper;
    }

    private void applyStatus(Intent intent) {
        lastState = safe(intent.getStringExtra(VoiceMonitorService.EXTRA_STATE));
        lastAudio = safe(intent.getStringExtra(VoiceMonitorService.EXTRA_AUDIO));
        lastNetwork = safe(intent.getStringExtra(VoiceMonitorService.EXTRA_NETWORK));
        String caller = safe(intent.getStringExtra(VoiceMonitorService.EXTRA_CALLER));
        String detail = safe(intent.getStringExtra(VoiceMonitorService.EXTRA_DETAIL));
        lastSpeaker = intent.getBooleanExtra(VoiceMonitorService.EXTRA_SPEAKER, true);
        lastCallReady = intent.getBooleanExtra(VoiceMonitorService.EXTRA_CALL_READY, false);
        lastModeRecoveries = intent.getIntExtra(
                VoiceMonitorService.EXTRA_MODE_RECOVERIES, 0);
        int smsUnread = intent.getIntExtra(VoiceMonitorService.EXTRA_SMS_UNREAD, 0);
        long smsRevision = intent.getLongExtra(VoiceMonitorService.EXTRA_SMS_REVISION, 0L);
        String smsDetail = safe(intent.getStringExtra(VoiceMonitorService.EXTRA_SMS_DETAIL));

        updateStatusPill();
        updateCallBanner(caller, detail);
        boolean canDial = "IDLE".equals(lastState) && lastCallReady;
        keypadCallButton.setEnabled(canDial);
        keypadCallButton.setAlpha(canDial ? 1f : 0.35f);
        refreshCalls();

        lastSmsUnread = smsUnread;
        if (smsUnread > 0) {
            messageBadge.setText(smsUnread > 99 ? "99+" : Integer.toString(smsUnread));
            messageBadge.setVisibility(View.VISIBLE);
        } else {
            messageBadge.setVisibility(View.GONE);
        }
        boolean messageUiChanged = smsRevision != lastSmsRevision
                || !smsDetail.equals(lastSmsDetail);
        lastSmsRevision = smsRevision;
        lastSmsDetail = smsDetail;
        if (selectedTab == TAB_MESSAGES && messageUiChanged) {
            renderMessages();
        }
    }

    private void updateStatusPill() {
        int dot;
        int background;
        String value;
        if ("OFFLINE".equals(lastState) || "NOT_PAIRED".equals(lastState)) {
            dot = COLOR_RED;
            background = 0xffffe9e7;
            value = "Modem offline";
        } else if (!lastCallReady) {
            dot = 0xffe58b00;
            background = 0xfffff1d8;
            value = "Correcting call mode";
        } else {
            dot = COLOR_GREEN;
            background = COLOR_GREEN_SOFT;
            value = "Modem · " + (lastNetwork.length() == 0 ? "Connected" : lastNetwork)
                    + " · Ready";
            if (lastModeRecoveries > 0) {
                value += " · recovered " + lastModeRecoveries + "×";
            }
        }
        statusDot.setBackground(rounded(dot, 20));
        statusText.setText(value);
        ((View) statusText.getParent()).setBackground(rounded(background, 24));
    }

    private void updateCallBanner(String caller, String detail) {
        boolean visible = "RINGING".equals(lastState) || "DIALING".equals(lastState)
                || "ACTIVE".equals(lastState);
        callBanner.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            return;
        }
        if ("RINGING".equals(lastState)) {
            callStateView.setText("Incoming call");
        } else if ("DIALING".equals(lastState)) {
            callStateView.setText("Calling");
        } else {
            callStateView.setText("Call in progress");
        }
        callNumberView.setText(caller.length() == 0 ? "Unknown number" : caller);
        String audio = "CONNECTED".equals(lastAudio) ? " · tablet audio connected" : "";
        callDetailView.setText(detail + audio);
        answerButton.setVisibility("RINGING".equals(lastState) ? View.VISIBLE : View.GONE);
        hangupButton.setVisibility(View.VISIBLE);
        audioButton.setVisibility("ACTIVE".equals(lastState) ? View.VISIBLE : View.GONE);
        audioButton.setText("CONNECTED".equals(lastAudio) || "CONNECTING".equals(lastAudio)
                ? "Disconnect audio" : "Connect audio");
    }

    private void openConversation(String address) {
        openConversationAddress = address;
        composingNewMessage = false;
        Intent intent = new Intent(this, VoiceMonitorService.class);
        intent.setAction(VoiceMonitorService.ACTION_SMS_READ);
        intent.putExtra(VoiceMonitorService.EXTRA_SMS_ADDRESS, address);
        startService(intent);
        renderMessages();
    }

    private void sendSms() {
        String rawAddress = composingNewMessage && messageAddressInput != null
                ? messageAddressInput.getText().toString() : openConversationAddress;
        String address = SmsAddress.normalize(rawAddress);
        String body = messageBodyInput == null ? "" : messageBodyInput.getText().toString();
        if (address.length() == 0) {
            showMessage("Enter a valid recipient number");
            return;
        }
        if (body.trim().length() == 0) {
            showMessage("Write a message first");
            return;
        }
        if (body.length() > 2000) {
            showMessage("Message is too long");
            return;
        }
        Intent intent = new Intent(this, VoiceMonitorService.class);
        intent.setAction(VoiceMonitorService.ACTION_SMS_SEND);
        intent.putExtra(VoiceMonitorService.EXTRA_SMS_ADDRESS, address);
        intent.putExtra(VoiceMonitorService.EXTRA_SMS_BODY, body);
        startService(intent);
        openConversationAddress = address;
        composingNewMessage = false;
        messageBodyInput.setText("");
        hideKeyboard();
        renderMessages();
    }

    private void answerCall() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAnswer = true;
            pendingAudioStart = false;
            pendingDialNumber = "";
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }
        sendServiceAction(VoiceMonitorService.ACTION_ANSWER);
    }

    private void dialNumber(String rawNumber) {
        String number = DialNumber.normalize(rawNumber);
        if (number.length() == 0) {
            showMessage("Enter a normal phone number with 6 to 20 digits");
            return;
        }
        if (!"IDLE".equals(lastState) || !lastCallReady) {
            showMessage("The modem is not ready for a new call");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAnswer = false;
            pendingAudioStart = false;
            pendingDialNumber = number;
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }
        sendDialAction(number);
    }

    private void startAudioManually() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAudioStart = true;
            pendingAnswer = false;
            pendingDialNumber = "";
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }
        sendServiceAction(VoiceMonitorService.ACTION_AUDIO_START);
    }

    private void showConnectionDialog() {
        String readiness = lastCallReady ? "Ready for calls" : "Call mode is being corrected";
        String recoveries = lastModeRecoveries == 0
                ? "No LTE-only resets detected"
                : "LTE-only mode recovered " + lastModeRecoveries
                        + (lastModeRecoveries == 1 ? " time" : " times");
        String message = "Mobile network · "
                + (lastNetwork.length() == 0 ? "Unknown" : lastNetwork)
                + "\n" + readiness
                + "\n" + recoveries
                + "\n\nSMS is read and sent directly through the modem's local network."
                + " Message content is not sent to Telegram by this app.";
        new AlertDialog.Builder(this)
                .setTitle("Modem connection")
                .setMessage(message)
                .setNeutralButton(lastSpeaker ? "Use earpiece" : "Use speaker",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                setSpeaker(!lastSpeaker);
                            }
                        })
                .setPositiveButton("Close", null)
                .show();
    }

    private void setSpeaker(boolean enabled) {
        Intent intent = new Intent(this, VoiceMonitorService.class);
        intent.setAction(VoiceMonitorService.ACTION_SPEAKER);
        intent.putExtra(VoiceMonitorService.EXTRA_SPEAKER, enabled);
        startService(intent);
    }

    private void handleNavigationIntent(Intent intent) {
        if (intent == null || messagesScreen == null) {
            return;
        }
        String requestedTab = intent.getStringExtra(VoiceMonitorService.EXTRA_OPEN_TAB);
        String address = intent.getStringExtra(VoiceMonitorService.EXTRA_SMS_ADDRESS);
        if ("messages".equals(requestedTab)) {
            selectTab(TAB_MESSAGES);
            if (address != null && address.length() > 0) {
                openConversation(address);
            }
            intent.removeExtra(VoiceMonitorService.EXTRA_OPEN_TAB);
            intent.removeExtra(VoiceMonitorService.EXTRA_SMS_ADDRESS);
        }
    }

    private void appendDialCharacter(String value) {
        int start = keypadNumber.getSelectionStart();
        if (start < 0 || start > keypadNumber.length()) {
            start = keypadNumber.length();
        }
        keypadNumber.getText().insert(start, value);
    }

    private List<Conversation> conversations(List<SmsMessage> messages) {
        LinkedHashMap<String, Conversation> grouped = new LinkedHashMap<String, Conversation>();
        for (SmsMessage message : messages) {
            String address = message.address;
            Conversation conversation = grouped.get(address);
            if (conversation == null) {
                conversation = new Conversation(address, message);
                grouped.put(address, conversation);
            }
            conversation.messages.add(message);
            if (message.incoming() && !message.read) {
                conversation.unread++;
            }
        }
        return new ArrayList<Conversation>(grouped.values());
    }

    private static final class Conversation {
        final String address;
        final SmsMessage latest;
        final ArrayList<SmsMessage> messages = new ArrayList<SmsMessage>();
        int unread;

        Conversation(String address, SmsMessage latest) {
            this.address = address;
            this.latest = latest;
        }
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

    private void sendDialAction(String number) {
        Intent intent = new Intent(this, VoiceMonitorService.class);
        intent.setAction(VoiceMonitorService.ACTION_DIAL);
        intent.putExtra(VoiceMonitorService.EXTRA_DIAL_NUMBER, number);
        startService(intent);
    }

    private void sendServiceAction(String action) {
        Intent intent = new Intent(this, VoiceMonitorService.class);
        intent.setAction(action);
        startService(intent);
    }

    private Button actionButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rippleRounded(color, 16));
        return button;
    }

    private Button outlineButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(COLOR_BLUE);
        button.setTextSize(14);
        button.setAllCaps(false);
        GradientDrawable shape = rounded(COLOR_BLUE_SOFT, 18);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x220b67d1), shape, null));
        return button;
    }

    private ImageButton iconButton(int drawable, int tint, int fill, int size) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setColorFilter(tint);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(Math.max(8, size / 4)), dp(Math.max(8, size / 4)),
                dp(Math.max(8, size / 4)), dp(Math.max(8, size / 4)));
        button.setBackground(rippleRounded(fill, size / 2));
        return button;
    }

    private View avatarIcon(String value, int drawable) {
        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(rounded(avatarColor(value), 48));
        ImageView icon = new ImageView(this);
        icon.setImageResource(drawable);
        icon.setColorFilter(COLOR_BLUE);
        icon.setPadding(dp(13), dp(13), dp(13), dp(13));
        avatar.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return avatar;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DIVIDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return divider;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private Drawable rippleRounded(int fill, float radiusDp) {
        return new RippleDrawable(
                ColorStateList.valueOf(0x220b67d1),
                rounded(fill, radiusDp),
                null);
    }

    private LinearLayout.LayoutParams weightedActionParams() {
        return new LinearLayout.LayoutParams(0, dp(44), 1f);
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private String relativeTime(long timestamp) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(timestamp);
        Calendar now = Calendar.getInstance();
        if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
            return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(timestamp));
        }
        return new SimpleDateFormat("MMM d", Locale.US).format(new Date(timestamp));
    }

    private static String formatDuration(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainder + "s";
        }
        return remainder + "s";
    }

    private static String displayAddress(String address) {
        return address == null || address.length() == 0 ? "Unknown sender" : address;
    }

    private static int avatarColor(String value) {
        int hash = value == null ? 0 : value.hashCode() & 0x7fffffff;
        int[] colors = {0xffd9e9ff, 0xffe5ddff, 0xffd9f2e4, 0xffffe0e6, 0xffffebc8};
        return colors[hash % colors.length];
    }

    private void showKeyboard(final View view) {
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager manager = (InputMethodManager) getSystemService(
                        INPUT_METHOD_SERVICE);
                if (manager != null) {
                    manager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 150L);
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return;
        }
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
        focused.clearFocus();
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int contentWidth(int maximumDp) {
        int available = getResources().getDisplayMetrics().widthPixels - dp(56);
        return Math.max(dp(280), Math.min(available, dp(maximumDp)));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
