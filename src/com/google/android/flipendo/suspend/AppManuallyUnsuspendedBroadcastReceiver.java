package com.google.android.flipendo.suspend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.google.pixel.vendor.PixelAtoms;
import android.util.Log;

import com.google.android.flipendo.logging.WestworldLogger;

public final class AppManuallyUnsuspendedBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "AppManuallyUnsuspended";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_PACKAGE_UNSUSPENDED_MANUALLY.equals(intent.getAction())) {
            Log.w(TAG, "Unknown action: " + intent.getAction());
            return;
        }
        String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            Log.w(TAG, "No package name specified");
            return;
        }
        WestworldLogger.logFlipendoAppUnsuspended(
                packageName, context, PixelAtoms.FlipendoAppUnsuspended.Type.UNSUSPENDED);
        Log.i(TAG, "Manually unsuspended ".concat(packageName));
        SuspensionManager.getInstance(context).queueResuspend(packageName);
    }
}
