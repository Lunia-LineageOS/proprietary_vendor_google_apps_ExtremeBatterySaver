package com.google.android.flipendo.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Recreates the notification channels so their names follow the new locale. */
public final class ChannelsLocaleReceiver extends BroadcastReceiver {
    private static final String TAG = "ChannelsLocaleReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
            Log.w(TAG, "Unknown action for ChannelsLocaleReceiver: " + intent.getAction());
            return;
        }
        Log.d(TAG, "Updating notification channels...");
        for (AppChannels channel : AppChannels.values()) {
            channel.createNotificationChannel(context);
        }
    }
}
