package com.google.android.flipendo.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.settingslib.fuelgauge.BatteryUtils;

import com.google.android.flipendo.BatterySaverManager;
import com.google.android.flipendo.InternalStateController;
import com.google.android.flipendo.bugreport.HistoricalLogUtil;
import com.google.android.flipendo.logging.WestworldLogger;
import com.google.android.flipendo.settings.SettingsController;
import com.google.android.flipendo.settings.SettingsInjector;
import com.google.android.flipendo.suspend.AllowedAppListManager;

public final class BootCompletedBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedBR";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.w(TAG, "Unknown action for BootCompletedBroadcastReceiver: " + intent.getAction());
            return;
        }
        Log.v(TAG, "Received " + Intent.ACTION_BOOT_COMPLETED);
        HistoricalLogUtil.logIntent(context, intent);
        SettingsInjector.tryToUpdateSettingsInjection(context);
        BatterySaverManager.getInstance(context)
                .ifPresent(BatterySaverManager::removeAskSavingModeIfNeeded);
        if (BatteryUtils.isAdditionalProfile(context)) {
            Log.v(TAG, "Triggered inside an additional profile, no-op ... ");
            return;
        }
        AllowedAppListManager.getInstance(context).scheduleCleaning();
        HistoricalLogUtil.moveDirectBootLog(context);
        WestworldLogger.storeFlipendoSavingsMode(
                context, SettingsController.getInstance(context).getSavedSavingsMode());
        WestworldLogger.logFlipendoEnabledAtom(
                InternalStateController.getInstance(context).isFlipendoEnabled(),
                WestworldLogger.FlipendoEnabledType.BOOT_COMPLETED);
        new ReceiverHandoverDelegate(
                        BatterySaverManager.getInstance(context).get().updateState(TAG, true))
                .onReceive(this, context);
    }
}
