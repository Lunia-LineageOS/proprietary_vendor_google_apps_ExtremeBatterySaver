package com.google.android.flipendo.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;

import androidx.core.app.NotificationCompat;

import com.google.android.flipendo.R;

public enum AppChannels {
    DISCOVERY(
            "EXTREME_BATTERY_SAVER_PROMO",
            NotificationStyle.SETTINGS,
            R.string.promo_notification_channel_title,
            NotificationManager.IMPORTANCE_LOW,
            NotificationSoundMode.DEFAULT),
    REPAUSE(
            "EXTREME_BATTERY_SAVER_RESUSPEND",
            NotificationStyle.EXTREME_BATTERY_SAVER,
            R.string.resuspend_notification_channel_title,
            NotificationManager.IMPORTANCE_HIGH,
            NotificationSoundMode.SILENT),
    STATUS(
            "EXTREME_BATTERY_SAVER_TURNED_ON",
            NotificationStyle.SETTINGS,
            R.string.turned_on_notification_channel_title,
            NotificationManager.IMPORTANCE_DEFAULT,
            NotificationSoundMode.DEFAULT),
    SYSTEM(
            "EXTREME_BATTERY_SAVER",
            NotificationStyle.SETTINGS,
            R.string.service_notification_channel_title,
            NotificationManager.IMPORTANCE_LOW,
            NotificationSoundMode.DEFAULT),
    ENABLE(
            "ENABLE",
            NotificationStyle.SETTINGS,
            R.string.enable_notification_channel_title,
            NotificationManager.IMPORTANCE_LOW,
            NotificationSoundMode.DEFAULT);

    private final String mId;
    private final int mImportance;
    private final NotificationSoundMode mSoundMode;
    private final NotificationStyle mStyle;
    private final int mTitle;

    AppChannels(
            String id,
            NotificationStyle style,
            int title,
            int importance,
            NotificationSoundMode soundMode) {
        mId = id;
        mStyle = style;
        mTitle = title;
        mImportance = importance;
        mSoundMode = soundMode;
    }

    public String createNotificationChannel(Context context) {
        NotificationChannel channel =
                new NotificationChannel(mId, context.getString(mTitle), mImportance);
        if (mSoundMode == NotificationSoundMode.SILENT) {
            channel.setSound(null, null);
        }
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        return mId;
    }

    public NotificationCompat.Builder createNotificationBuilder(Context context, String text) {
        Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, context.getString(getAppName()));
        return new NotificationCompat.Builder(context, createNotificationChannel(context))
                .setExtras(extras)
                .setSmallIcon(getIcon())
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setColor(context.getColor(R.color.google_blue300))
                .setLocalOnly(true);
    }

    public String getId() {
        return mId;
    }

    private int getAppName() {
        return mStyle.getAppName();
    }

    private int getIcon() {
        return mStyle.getIcon();
    }
}
