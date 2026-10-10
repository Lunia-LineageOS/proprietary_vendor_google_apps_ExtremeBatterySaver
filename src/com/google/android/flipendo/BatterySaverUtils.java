package com.google.android.flipendo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

abstract class BatterySaverUtils {
    private static final String TAG = "BatterySaverUtils";

    static final String ACTION_SAVER_STATE_UPDATE =
            "com.google.android.flipendo.ACTION_SAVER_STATE_UPDATE";
    static final String KEY_EXTREME_SAVE_SATE = "extreme_save_state";
    static final String KEY_POWER_SAVE_STATE = "power_save_state";
    static final String PERMISSION_SAVER_STATE_UPDATE =
            "com.google.android.flipendo.SAVER_STATE_UPDATE";

    private static final String SHARED_PREFS_NAME = "saver_states_preferences";
    private static final String TURBO_PACKAGE_NAME = "com.google.android.apps.turbo";

    /** Notifies Device Health Services when the (extreme) battery saver state changes. */
    static void sendUpdateIntent(Context context, boolean powerSaveMode, boolean extremeSaveMode) {
        SharedPreferences prefs = getSharedPreferences(context);
        boolean lastPowerSaveMode = prefs.getBoolean(KEY_POWER_SAVE_STATE, false);
        boolean lastExtremeSaveMode = prefs.getBoolean(KEY_EXTREME_SAVE_SATE, false);
        if (powerSaveMode == lastPowerSaveMode && extremeSaveMode == lastExtremeSaveMode) {
            return;
        }
        updateSharedPreferences(context, powerSaveMode, extremeSaveMode);
        context.sendBroadcast(
                new Intent()
                        .setAction(ACTION_SAVER_STATE_UPDATE)
                        .setPackage(TURBO_PACKAGE_NAME)
                        .putExtra(KEY_POWER_SAVE_STATE, powerSaveMode ? 1 : 0)
                        .putExtra(KEY_EXTREME_SAVE_SATE, extremeSaveMode ? 1 : 0),
                PERMISSION_SAVER_STATE_UPDATE);
        Log.d(TAG, "sendUpdateIntent: " + powerSaveMode + "/" + extremeSaveMode);
    }

    static void updateSharedPreferences(
            Context context, boolean powerSaveMode, boolean extremeSaveMode) {
        getSharedPreferences(context)
                .edit()
                .putBoolean(KEY_POWER_SAVE_STATE, powerSaveMode)
                .putBoolean(KEY_EXTREME_SAVE_SATE, extremeSaveMode)
                .commit();
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }
}
