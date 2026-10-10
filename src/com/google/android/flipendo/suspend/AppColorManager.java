package com.google.android.flipendo.suspend;

import android.content.Context;
import android.hardware.display.ColorDisplayManager;
import android.os.Handler;
import android.util.Log;

import com.google.android.flipendo.utils.DeviceConfigUtils;
import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Set;

/** Turns apps grayscale while they are about to be paused again. */
public final class AppColorManager {
    private static final String TAG = "AppColorManager";

    private static final int SATURATION_LEVEL_GRAYSCALE = 0;
    private static final int SATURATION_LEVEL_DEFAULT = 100;
    private static final int UNSET_GRAYSCALE_FOR_SUSPEND_DELAY_MS = 2000;

    private static AppColorManager sSingleton;

    private ColorDisplayManager mColorDisplayManager;
    private final Set<String> mGrayscaleApps = new HashSet<>();
    private final Handler mHandler;

    public static synchronized AppColorManager getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new AppColorManager(context.getApplicationContext());
        }
        return sSingleton;
    }

    private AppColorManager(Context context) {
        try {
            mColorDisplayManager = context.getSystemService(ColorDisplayManager.class);
            if (mColorDisplayManager == null) {
                Log.w(TAG, "ColorDisplayManager does not exist");
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not get ColorDisplayManager", e);
        }
        mHandler = new Handler(context.getMainLooper());
    }

    public void setAppGrayscale(String packageName) {
        if (mColorDisplayManager == null) {
            Log.i(TAG, "Not setting grayscale; ColorDisplayManager does not exist");
            return;
        }
        if (!DeviceConfigUtils.getIsGrayscaleEnabled()) {
            Log.i(TAG, "Not setting grayscale on app; grayscale mode disabled");
            return;
        }
        Log.v(TAG, "Setting grayscale for app " + packageName);
        mColorDisplayManager.setAppSaturationLevel(packageName, SATURATION_LEVEL_GRAYSCALE);
        mGrayscaleApps.add(packageName);
    }

    void unsetAppGrayscale(String packageName) {
        unsetAppGrayscale(packageName, 0);
    }

    /** Delays restoring the colors so the app doesn't flash in color right before pausing. */
    void unsetAppGrayscaleForSuspend(String packageName) {
        unsetAppGrayscale(packageName, UNSET_GRAYSCALE_FOR_SUSPEND_DELAY_MS);
    }

    private void unsetAppGrayscale(String packageName, int delayMs) {
        if (mColorDisplayManager == null) {
            Log.i(TAG, "Not setting grayscale; ColorDisplayManager does not exist");
            return;
        }
        Log.i(TAG, "Clearing grayscale for app " + packageName);
        mGrayscaleApps.remove(packageName);
        mHandler.postDelayed(
                () -> {
                    if (mGrayscaleApps.contains(packageName)) {
                        Log.i(
                                TAG,
                                "Grayscale re-set for app "
                                        + packageName
                                        + " - not clearing grayscale");
                        return;
                    }
                    Log.i(TAG, "Clearing grayscale for app " + packageName + " - in handler");
                    mColorDisplayManager.setAppSaturationLevel(
                            packageName, SATURATION_LEVEL_DEFAULT);
                },
                delayMs);
    }

    public void unsetAllAppGrayscale() {
        for (String packageName : ImmutableSet.copyOf(mGrayscaleApps)) {
            unsetAppGrayscale(packageName);
        }
    }

    public boolean isAppGrayscale(String packageName) {
        return mGrayscaleApps.contains(packageName);
    }
}
