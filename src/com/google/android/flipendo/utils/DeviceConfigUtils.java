package com.google.android.flipendo.utils;

import android.content.Context;
import android.provider.DeviceConfig;
import android.util.Log;

import com.google.android.flipendo.R;
import com.google.android.flipendo.settings.SettingsController;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public abstract class DeviceConfigUtils {
    private static final String TAG = "DeviceConfigUtils";

    public static final String FLAG_ESSENTIAL_APPS = "essential_apps";
    public static final String FLAG_FLIPENDO_ENABLED = "flipendo_enabled_launch";
    public static final String FLAG_GRAYSCALE_ENABLED = "grayscale_enabled_launch";
    public static final String FLAG_IS_ASK_FEATURE_ENABLED = "is_ask_feature_enabled_launch";
    static final String FLAG_LEVER_HOTSPOT_ENABLED = "lever_hotspot_enabled_launch";
    public static final String FLAG_LEVER_WORK_PROFILE_ENABLED =
            "lever_work_profile_enabled_launch";
    public static final String FLAG_PROMO_SHOWN_LIMIT = "promo_shown_limit";
    public static final String FLAG_RESUSPEND_DELAY_MINUTES = "resuspend_delay_minutes";
    static final String FLAG_WORK_PROFILE_TAB_ENABLED = "work_profile_tab_enabled";
    public static final String NAMESPACE = "flipendo";

    private static final String FLAG_DEFAULT_SAVINGS_MODE = "default_savings_mode_launch";
    private static final String FLAG_LEVER_BLE_SCANNING_ENABLED =
            "lever_ble_scanning_enabled_launch";
    private static final String FLAG_LEVER_WIFI_SCANNING_ENABLED =
            "lever_wifi_scanning_enabled_launch";
    private static final String FLAG_LEVER_SCREEN_OFF_TIMEOUT_ENABLED =
            "lever_screen_off_timeout_enabled_launch";
    private static final String FLAG_SCREEN_OFF_TIMEOUT = "screen_off_timeout";
    private static final String FLAG_PACKAGE_NAMES_ALLOWED_TO_USE_API =
            "package_names_allowed_to_use_api";

    private static final String SAVINGS_MODE_ASK = "ask";
    private static final String SAVINGS_MODE_MILD = "mild";
    private static final String SAVINGS_MODE_AGGRESSIVE = "aggressive";

    public static boolean getFlipendoEnabled(Context context) {
        return DeviceConfig.getBoolean(
                NAMESPACE,
                FLAG_FLIPENDO_ENABLED,
                context.getResources().getBoolean(R.bool.default_flipendo_enabled_flag));
    }

    public static SettingsController.SavingsMode getDefaultSavingsMode() {
        String mode =
                DeviceConfig.getString(NAMESPACE, FLAG_DEFAULT_SAVINGS_MODE, SAVINGS_MODE_MILD);
        switch (mode.toLowerCase(Locale.ROOT)) {
            case SAVINGS_MODE_ASK:
                return SettingsController.SavingsMode.ASK;
            case SAVINGS_MODE_MILD:
                return SettingsController.SavingsMode.MILD;
            case SAVINGS_MODE_AGGRESSIVE:
                return SettingsController.SavingsMode.AGGRESSIVE;
            default:
                Log.e(TAG, "Incorrect default savings mode flag ".concat(mode));
                Log.d(TAG, "Defaulting to mild");
                return SettingsController.SavingsMode.MILD;
        }
    }

    public static boolean getIsAskEveryTimeFeatureEnabled() {
        return DeviceConfig.getBoolean(NAMESPACE, FLAG_IS_ASK_FEATURE_ENABLED, true);
    }

    public static boolean getIsWorkProfileLeverEnabled() {
        return DeviceConfig.getBoolean(NAMESPACE, FLAG_LEVER_WORK_PROFILE_ENABLED, true);
    }

    public static boolean getIsGrayscaleEnabled() {
        return DeviceConfig.getBoolean(NAMESPACE, FLAG_GRAYSCALE_ENABLED, true);
    }

    public static List<String> getAdditionalEssentialApps() {
        String essentialApps = DeviceConfig.getString(NAMESPACE, FLAG_ESSENTIAL_APPS, "");
        if (Strings.isNullOrEmpty(essentialApps)) {
            return ImmutableList.of();
        }
        return Arrays.asList(essentialApps.split(","));
    }

    public static int getResuspendDelayMinutes() {
        return DeviceConfig.getInt(NAMESPACE, FLAG_RESUSPEND_DELAY_MINUTES, 5);
    }

    public static boolean getDisableBleScanningLeverEnabled() {
        return DeviceConfig.getBoolean(NAMESPACE, FLAG_LEVER_BLE_SCANNING_ENABLED, true);
    }

    public static boolean getIsHotspotLeverEnabled() {
        return DeviceConfig.getBoolean(NAMESPACE, FLAG_LEVER_HOTSPOT_ENABLED, true);
    }

    public static boolean getDisableWifiScanningLeverEnabled() {
        return DeviceConfig.getBoolean(NAMESPACE, FLAG_LEVER_WIFI_SCANNING_ENABLED, true);
    }

    public static boolean getReduceScreenOffTimeoutEnabled() {
        return DeviceConfig.getBoolean(NAMESPACE, FLAG_LEVER_SCREEN_OFF_TIMEOUT_ENABLED, true);
    }

    public static String getScreenOffTimeoutMillis() {
        return DeviceConfig.getString(NAMESPACE, FLAG_SCREEN_OFF_TIMEOUT, "30000");
    }

    public static ImmutableSet<String> getAppsAllowedToUseApi() {
        String packageNames =
                DeviceConfig.getString(NAMESPACE, FLAG_PACKAGE_NAMES_ALLOWED_TO_USE_API, "");
        if (Strings.isNullOrEmpty(packageNames)) {
            return ImmutableSet.of();
        }
        return ImmutableSet.copyOf(packageNames.split(","));
    }

    public static boolean isWorkProfileTabEnabled() {
        return DeviceConfig.getBoolean(NAMESPACE, FLAG_WORK_PROFILE_TAB_ENABLED, false);
    }
}
