package com.google.android.flipendo.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.flipendo.workprofile.WorkProfileManagerImpl;

public final class TurnOnWorkProfileBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "WorkProfileBR";

    private static final String ACTION_WORK_PROFILE_ON =
            "com.google.android.flipendo.receiver.WORK_PROF_ON";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_WORK_PROFILE_ON.equals(intent.getAction())) {
            Log.w(TAG, "Unknown action: " + intent.getAction());
            return;
        }
        WorkProfileManagerImpl.getInstance(context).turnOnWorkProfile(true);
    }

    public static PendingIntent getPendingIntent(Context context) {
        return PendingIntent.getBroadcast(
                context.getApplicationContext(),
                ACTION_WORK_PROFILE_ON.hashCode(),
                new Intent(ACTION_WORK_PROFILE_ON)
                        .setClass(context, TurnOnWorkProfileBroadcastReceiver.class)
                        .setPackage(context.getPackageName())
                        .setFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
