package com.google.android.flipendo.receiver.testing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.android.flipendo.suspend.AllowedAppListManager;
import com.google.android.flipendo.suspend.SuspensionManager;

/** Adds or removes an app from the essential apps list on userdebug builds. */
public final class ChangeAllowedAppListBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "ChangeAllowedBR";

    @VisibleForTesting static final String ACTION_ALLOW = "com.google.android.flipendo.ALLOW";
    @VisibleForTesting static final String EXTRA_ALLOWED = "allowed";
    @VisibleForTesting static final String EXTRA_PACKAGE_NAME = "package_name";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Build.IS_ENG && !Build.IS_USERDEBUG) {
            Log.w(TAG, "Works on the userdebug builds only");
            return;
        }
        if (!intent.getAction().equals(ACTION_ALLOW)) {
            Log.w(TAG, "Received unexpected broadcast");
            return;
        }
        if (intent.getStringExtra(EXTRA_PACKAGE_NAME) == null) {
            Log.w(TAG, "No package specified.");
            return;
        }
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        boolean allowed = intent.getBooleanExtra(EXTRA_ALLOWED, true);
        AllowedAppListManager.getInstance(context).setAllowedApp(packageName, allowed);
        if (allowed) {
            SuspensionManager.getInstance(context).unsuspend(packageName);
        }
    }
}
