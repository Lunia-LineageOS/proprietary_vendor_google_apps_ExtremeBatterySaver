package com.google.android.flipendo.workprofile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.flipendo.InternalState;
import com.google.android.flipendo.InternalStateController;
import com.google.android.flipendo.suspend.SuspensionManager;

public final class UpdateWorkProfileStateBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "WorkProfileBR";

    private static final String ACTION_UPDATE = "com.google.android.flipendo.workprofile.UPDATE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_UPDATE.equals(intent.getAction())) {
            update(context);
            return;
        }
        Log.w(TAG, "Unknown action: " + intent.getAction());
    }

    private void update(Context context) {
        if (InternalStateController.getInstance(context).getState().equals(InternalState.ENABLED)) {
            Log.i(TAG, "updating work profile state");
            SuspensionManager.getInstance(context).updateWorkProfileState();
        } else {
            Log.i(TAG, "not updating work profile state");
        }
    }
}
