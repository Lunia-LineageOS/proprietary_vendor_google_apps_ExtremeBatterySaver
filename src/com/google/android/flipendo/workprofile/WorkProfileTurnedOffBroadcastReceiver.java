package com.google.android.flipendo.workprofile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.flipendo.suspend.SuspensionManager;

public final class WorkProfileTurnedOffBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "WorkProfileOffBR";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive");
        SuspensionManager.getInstance(context).onWorkProfileTurnedOff();
    }
}
