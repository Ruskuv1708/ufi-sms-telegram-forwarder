package com.ufi.voicegateway;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public final class GatewayControlService extends IntentService {
    private static final String TAG = "UfiVoiceGateway";
    static final String ACTION_CONFIGURE = "com.ufi.voicegateway.CONFIGURE";
    static final String ACTION_STATUS = "com.ufi.voicegateway.STATUS";

    public GatewayControlService() {
        super("UfiVoiceGatewayControl");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_CONFIGURE.equals(action)) {
            String token = intent.getStringExtra("token");
            boolean enabled = intent.getBooleanExtra("enabled", true);
            if (!GatewayConfig.save(this, token, enabled)) {
                Log.e(TAG, "configuration rejected");
                return;
            }
            stopService(new Intent(this, VoiceGatewayService.class));
            if (enabled) {
                startService(new Intent(this, VoiceGatewayService.class));
            }
            Log.i(TAG, "configuration saved; enabled=" + enabled);
        } else if (ACTION_STATUS.equals(action)) {
            Log.i(TAG, "configured=" + GatewayConfig.validToken(GatewayConfig.token(this))
                    + " enabled=" + GatewayConfig.enabled(this)
                    + " running=" + VoiceGatewayService.isRunning());
        } else {
            Log.e(TAG, "unsupported control action");
        }
    }
}
