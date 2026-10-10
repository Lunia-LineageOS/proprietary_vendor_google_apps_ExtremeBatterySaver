package com.google.android.flipendo.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResourcesManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;

import com.google.android.flipendo.R;
import com.google.android.flipendo.receiver.TurnOnWorkProfileBroadcastReceiver;

public abstract class TurnWorkProfileBackOnNotification {
    public static final int NOTIFICATION_ID = 789;

    private static final String WORK_PROFILE_BACK_ON_NOTIFICATION_TITLE =
            "Flipendo.WORK_PROFILE_BACK_ON_NOTIFICATION_TITLE";
    private static final String WORK_PROFILE_BACK_ON_NOTIFICATION_TEXT =
            "Flipendo.WORK_PROFILE_BACK_ON_NOTIFICATION_TEXT";

    public static void show(Context context) {
        DevicePolicyResourcesManager dpmResources =
                context.getSystemService(DevicePolicyManager.class).getResources();
        String text =
                dpmResources.getString(
                        WORK_PROFILE_BACK_ON_NOTIFICATION_TEXT,
                        () -> context.getString(R.string.work_profile_back_on_notification_text));
        NotificationCompat.Builder builder =
                AppChannels.STATUS.createNotificationBuilder(context, text);
        String title =
                dpmResources.getString(
                        WORK_PROFILE_BACK_ON_NOTIFICATION_TITLE,
                        () -> context.getString(R.string.work_profile_back_on_notification_title));
        Notification notification =
                builder.setContentTitle(title)
                        .setContentIntent(
                                TurnOnWorkProfileBroadcastReceiver.getPendingIntent(context))
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(true)
                        .build();
        context.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }

    public static void hide(Context context) {
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }
}
