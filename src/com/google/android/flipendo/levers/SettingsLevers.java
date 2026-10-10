package com.google.android.flipendo.levers;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.google.android.flipendo.utils.DeviceConfigUtils;
import com.google.common.collect.ImmutableList;

public final class SettingsLevers {
    private static final String SHARED_PREFS_NAME = "original_levers_preferences";

    private static SettingsLevers sSingleton;

    private final ImmutableList<SettingsLever> mLevers;

    public static synchronized SettingsLevers getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new SettingsLevers(context.getApplicationContext());
        }
        return sSingleton;
    }

    private SettingsLevers(Context context) {
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        mLevers =
                ImmutableList.of(
                        new SettingsLever(
                                context,
                                Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE,
                                SettingsLever.booleanSupplierToPredicate(
                                        DeviceConfigUtils::getDisableBleScanningLeverEnabled),
                                () -> "0",
                                SettingsLever.Namespace.GLOBAL,
                                SettingsLever.OriginalLocation.FLIPENDO_SETTINGS,
                                sharedPreferences),
                        new SettingsLever(
                                context,
                                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE,
                                SettingsLever.booleanSupplierToPredicate(
                                        DeviceConfigUtils::getDisableWifiScanningLeverEnabled),
                                () -> "0",
                                SettingsLever.Namespace.GLOBAL,
                                SettingsLever.OriginalLocation.FLIPENDO_SETTINGS,
                                sharedPreferences),
                        new SettingsLever(
                                context,
                                Settings.System.SCREEN_OFF_TIMEOUT,
                                SettingsLever.booleanSupplierToPredicate(
                                                DeviceConfigUtils::getReduceScreenOffTimeoutEnabled)
                                        .and(
                                                SettingsLever.checkGreaterThanPredicate(
                                                        DeviceConfigUtils
                                                                ::getScreenOffTimeoutMillis)),
                                DeviceConfigUtils::getScreenOffTimeoutMillis,
                                SettingsLever.Namespace.SYSTEM,
                                SettingsLever.OriginalLocation.DEVICE_SETTINGS,
                                sharedPreferences));
    }

    public void enableLowPower() {
        for (SettingsLever lever : mLevers) {
            lever.enableLowPower();
        }
    }

    public void disableLowPower() {
        for (SettingsLever lever : mLevers) {
            lever.disableLowPower();
        }
    }

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }
}
