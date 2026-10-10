package com.google.android.flipendo.suspend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.google.pixel.vendor.PixelAtoms;
import android.util.Log;

import com.google.android.flipendo.logging.WestworldLogger;

public final class KeepUnpausedBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "KeepUnpausedBR";

    @Override
    public void onReceive(Context context, Intent intent) {
        String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            Log.w(TAG, "No package name specified");
            return;
        }
        Log.i(TAG, "Keeping unpaused: ".concat(packageName));
        if (packageName.equals(SuspensionManager.WORK_PROFILE)) {
            SuspensionManager.getInstance(context).keepWorkProfileUnpaused();
            WestworldLogger.logFlipendoUIElementAction(
                    PixelAtoms.FlipendoUIElementAction.UIElement
                            .NOTIFICATION_WORK_PROFILE_SUSPENSION,
                    PixelAtoms.FlipendoUIElementAction.Action.KEEP_UNPAUSED_CLICKED);
        } else {
            SuspensionManager.getInstance(context).keepAppUnpaused(packageName);
        }
    }
}
