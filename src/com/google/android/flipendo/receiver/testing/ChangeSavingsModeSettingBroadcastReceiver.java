package com.google.android.flipendo.receiver.testing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.android.flipendo.BatterySaverManager;
import com.google.android.flipendo.receiver.ReceiverHandoverDelegate;
import com.google.android.flipendo.settings.SettingsController;

/** Changes the Extreme Battery Saver mode on userdebug builds. */
public final class ChangeSavingsModeSettingBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "TestingModeBR";

    @VisibleForTesting static final String ACTION_SET_MODE = "com.google.android.flipendo.SET_MODE";
    @VisibleForTesting static final String EXTRA_MODE = "mode";
    @VisibleForTesting static final String EXTRA_MODE_AGGRESSIVE = "aggressive";
    @VisibleForTesting static final String EXTRA_MODE_ASK = "ask";
    @VisibleForTesting static final String EXTRA_MODE_MILD = "mild";

    @VisibleForTesting ReceiverHandoverDelegate mReceiverHandoverDelegate;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Build.IS_ENG && !Build.IS_USERDEBUG) {
            Log.w(TAG, "Works on the userdebug builds only");
            return;
        }
        if (!intent.getAction().equals(ACTION_SET_MODE)) {
            Log.w(TAG, "Unexpected SET_MODE broadcast: " + intent.getAction());
            return;
        }
        if (!BatterySaverManager.getInstance(context).isPresent()) {
            Log.w(TAG, "Works on main profile context only");
            return;
        }
        String mode = intent.getStringExtra(EXTRA_MODE);
        if (mode == null) {
            Log.w(TAG, "No savings mode specified");
            return;
        }
        switch (mode) {
            case EXTRA_MODE_ASK:
                Log.v(TAG, "Setting ask mode");
                SettingsController.getInstance(context)
                        .setSavingsMode(SettingsController.SavingsMode.ASK);
                break;
            case EXTRA_MODE_MILD:
                Log.v(TAG, "Setting mild mode");
                SettingsController.getInstance(context)
                        .setSavingsMode(SettingsController.SavingsMode.MILD);
                break;
            case EXTRA_MODE_AGGRESSIVE:
                Log.v(TAG, "Setting extreme mode");
                SettingsController.getInstance(context)
                        .setSavingsMode(SettingsController.SavingsMode.AGGRESSIVE);
                break;
            default:
                Log.w(TAG, "Unknown power savings mode");
                break;
        }
        mReceiverHandoverDelegate =
                new ReceiverHandoverDelegate(
                        BatterySaverManager.getInstance(context).get().updateState(TAG));
        mReceiverHandoverDelegate.onReceive(this, context);
    }
}
