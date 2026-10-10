package com.google.android.flipendo.receiver.testing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.android.flipendo.suspend.SuspensionManager;

import java.util.Locale;

/** Pauses or unpauses an app on userdebug builds. */
public final class ChangeAppPauseBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "ChangeAppPauseBR";

    @VisibleForTesting static final String ACTION_PAUSE = "com.google.android.flipendo.PAUSE";
    @VisibleForTesting static final String ACTION_UNPAUSE = "com.google.android.flipendo.UNPAUSE";
    @VisibleForTesting static final String EXTRA_PACKAGE_NAME = "package_name";
    @VisibleForTesting static final String EXTRA_START_TIMER = "start_timer";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Build.IS_ENG && !Build.IS_USERDEBUG) {
            Log.w(TAG, "Works on the userdebug builds only");
            return;
        }
        if (!ACTION_PAUSE.equals(intent.getAction())
                && !ACTION_UNPAUSE.equals(intent.getAction())) {
            throw new IllegalArgumentException("Unknown action " + intent.getAction());
        }
        if (intent.getStringExtra(EXTRA_PACKAGE_NAME) == null) {
            throw new IllegalArgumentException("No package specified");
        }
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (ACTION_PAUSE.equals(intent.getAction())) {
            SuspensionManager.getInstance(context).suspend(packageName);
            return;
        }
        SuspensionManager.getInstance(context).unsuspend(packageName);
        if (intent.getStringExtra(EXTRA_START_TIMER) != null
                && intent.getStringExtra(EXTRA_START_TIMER)
                        .toLowerCase(Locale.ROOT)
                        .equals("true")) {
            SuspensionManager.getInstance(context).queueResuspend(packageName);
        }
    }
}
