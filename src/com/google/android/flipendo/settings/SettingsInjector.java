package com.google.android.flipendo.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import com.google.android.flipendo.InternalStateController;
import com.google.android.flipendo.appactivity.AppActivityManager;
import com.google.android.flipendo.utils.DeviceConfigUtils;

/**
 * Enables or disables the Extreme Battery Saver settings activity, which Settings discovers through
 * its intent action, according to the feature flag.
 */
public abstract class SettingsInjector {
    private static final String TAG = "SettingsInjector";

    private static final String SETTINGS_PACKAGE_NAME = "com.android.settings";

    public static final Class<?> SETTINGS_ACTIVITY_CLASS = AllowedAppListSelectionActivity.class;

    public static void tryToUpdateSettingsInjection(Context context) {
        AppActivityManager appActivityManager = AppActivityManager.getInstance(context);
        boolean flipendoEnabled = DeviceConfigUtils.getFlipendoEnabled(context);
        Log.v(TAG, "Feature flag is set to " + flipendoEnabled);
        if (isUpdateNotNeeded(context, flipendoEnabled)) {
            Log.v(TAG, "Settings injection is up to date");
            return;
        }
        if (InternalStateController.getInstance(context).isFlipendoEnabled()) {
            Log.w(TAG, "Settings shouldn't be injected while Flipendo is running");
            return;
        }
        if (appActivityManager.isPackageInForeground(SETTINGS_PACKAGE_NAME)) {
            Log.v(TAG, "Settings is in foreground won't change settings injection");
            return;
        }
        updateSettingsComponentState(
                context,
                flipendoEnabled
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        Log.v(TAG, "Settings injection updated, killing settings");
        appActivityManager.killBackgroundProcesses(SETTINGS_PACKAGE_NAME);
    }

    public static boolean isSettingsComponentEnabled(Context context) {
        PackageManager packageManager = context.getPackageManager();
        ComponentName component = new ComponentName(context, SETTINGS_ACTIVITY_CLASS);
        boolean isExplicitlyEnabled =
                packageManager.getComponentEnabledSetting(component)
                        == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        boolean hasDefaultState =
                packageManager.getComponentEnabledSetting(component)
                        == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        Log.v(
                TAG,
                "Component "
                        + component.toShortString()
                        + ": is explicitly enabled = "
                        + isExplicitlyEnabled
                        + ", has default state = "
                        + hasDefaultState);
        return isExplicitlyEnabled || hasDefaultState;
    }

    private static boolean isUpdateNotNeeded(Context context, boolean flipendoEnabled) {
        return flipendoEnabled == isSettingsComponentEnabled(context);
    }

    private static void updateSettingsComponentState(Context context, int state) {
        context.getPackageManager()
                .setComponentEnabledSetting(
                        new ComponentName(context, SETTINGS_ACTIVITY_CLASS),
                        state,
                        PackageManager.DONT_KILL_APP);
    }
}
