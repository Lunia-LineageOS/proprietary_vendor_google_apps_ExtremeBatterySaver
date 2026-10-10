package com.google.android.flipendo.notification;

import com.google.android.flipendo.R;

enum NotificationStyle {
    EXTREME_BATTERY_SAVER(R.string.app_label, R.drawable.ic_battery_saver),
    SETTINGS(R.string.settings_app_label, R.drawable.ic_settings);

    private final int mAppName;
    private final int mIcon;

    NotificationStyle(int appName, int icon) {
        mAppName = appName;
        mIcon = icon;
    }

    int getAppName() {
        return mAppName;
    }

    int getIcon() {
        return mIcon;
    }
}
