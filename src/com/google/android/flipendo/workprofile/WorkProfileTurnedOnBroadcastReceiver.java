package com.google.android.flipendo.workprofile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.flipendo.suspend.SuspensionManager;

public final class WorkProfileTurnedOnBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "WorkProfileBR";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive");
        SuspensionManager.getInstance(context).onWorkProfileTurnedOn();
    }
}
