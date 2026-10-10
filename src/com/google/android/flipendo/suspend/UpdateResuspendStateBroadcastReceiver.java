package com.google.android.flipendo.suspend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.flipendo.InternalStateController;

public final class UpdateResuspendStateBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "UpdateStateBR";

    static final String ACTION_UPDATE = "com.google.android.flipendo.suspend.UPDATE";
    static final String ACTION_CLEANUP = "com.google.android.flipendo.suspend.CLEANUP";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_UPDATE.equals(intent.getAction())) {
            updateState(context, intent);
        } else if (ACTION_CLEANUP.equals(intent.getAction())) {
            cleanUpMissedResuspensionTriggers(context);
        } else {
            Log.w(TAG, "Unknown action: " + intent.getAction());
        }
    }

    private void updateState(Context context, Intent intent) {
        String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            Log.w(TAG, "No package name specified");
            return;
        }
        Log.i(TAG, "updating state for ".concat(packageName));
        SuspensionManager.getInstance(context).updateResuspendState(packageName);
    }

    private void cleanUpMissedResuspensionTriggers(Context context) {
        if (InternalStateController.getInstance(context).isFlipendoEnabled()) {
            Log.i(TAG, "cleaning up");
            SuspensionManager.getInstance(context).cleanUpMissedResuspensionTriggers();
        } else {
            Log.w(TAG, "not cleaning up ");
        }
    }
}
