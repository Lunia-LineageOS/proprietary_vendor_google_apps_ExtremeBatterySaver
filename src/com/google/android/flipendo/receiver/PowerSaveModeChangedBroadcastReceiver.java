package com.google.android.flipendo.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.UserManager;
import android.util.Log;

import com.android.settingslib.fuelgauge.BatteryUtils;

import com.google.android.flipendo.BatterySaverManager;
import com.google.android.flipendo.bugreport.HistoricalLogUtil;
import com.google.android.flipendo.settings.SettingsInjector;

public final class PowerSaveModeChangedBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "PowerSaveModeChangedBR";

    @Override
    public void onReceive(Context context, Intent intent) {
        AsyncTask.execute(() -> HistoricalLogUtil.logIntent(context, intent));
        if (!PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction())) {
            Log.e(
                    TAG,
                    "Unknown action for PowerSaveModeChangedBroadcastReceiver: "
                            + intent.getAction());
            return;
        }
        Log.v(TAG, "Received: " + intent);
        SettingsInjector.tryToUpdateSettingsInjection(context);
        if (BatteryUtils.isAdditionalProfile(context)) {
            Log.v(TAG, "Triggered inside an additional profile, no-op ... ");
            return;
        }
        Log.v(
                TAG,
                "Device is in direct boot mode: "
                        + !context.getSystemService(UserManager.class).isUserUnlocked());
        new ReceiverHandoverDelegate(
                        BatterySaverManager.getInstance(context).get().updateState(TAG))
                .onReceive(this, context);
    }
}
