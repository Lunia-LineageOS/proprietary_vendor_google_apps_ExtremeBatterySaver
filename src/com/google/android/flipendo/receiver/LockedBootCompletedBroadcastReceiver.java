package com.google.android.flipendo.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.settingslib.fuelgauge.BatteryUtils;

import com.google.android.flipendo.BatterySaverManager;
import com.google.android.flipendo.InternalStateController;
import com.google.android.flipendo.settings.SettingsController;
import com.google.android.flipendo.settings.SettingsInjector;

public final class LockedBootCompletedBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "LockedBootComplete";

    @VisibleForTesting ReceiverHandoverDelegate mReceiverHandoverDelegate;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive starting ... ");
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.w(
                    TAG,
                    "Unknown action for LockedBootCompletedBroadcastReceiver: "
                            + intent.getAction());
            return;
        }
        Log.v(TAG, "Received: " + intent);
        SettingsInjector.tryToUpdateSettingsInjection(context);
        BatterySaverManager.getInstance(context)
                .ifPresent(BatterySaverManager::removeAskSavingModeIfNeeded);
        if (BatteryUtils.isAdditionalProfile(context)) {
            Log.v(TAG, "Triggered inside an additional profile, no-op ... ");
            return;
        }
        InternalStateController.getInstance(context).clearAllResuspensionStorage();
        SettingsController.getInstance(context).clearTerminatorModeStorage();
        mReceiverHandoverDelegate =
                new ReceiverHandoverDelegate(
                        BatterySaverManager.getInstance(context).get().updateState(TAG));
        mReceiverHandoverDelegate.onReceive(this, context);
    }
}
