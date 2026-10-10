package com.google.android.flipendo;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.BatterySaverPolicyConfig;
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;

import androidx.annotation.VisibleForTesting;

import com.google.android.flipendo.api_wrapper.DisplayManagerWrapper;
import com.google.android.flipendo.api_wrapper.DisplayManagerWrapperImpl;
import com.google.android.flipendo.bugreport.HistoricalLogUtil;

final class BatterySaverPolicyManager {
    private static final String TAG = "BatterySaverPolicyManager";

    private static final String SHARED_PREFS_NAME = "battery_saver_policy_preference";
    private static final String KEY_SOUND_TRIGGER_MODE = "sound_trigger_mode_key";
    private static final int INVALID_SOUND_TRIGGER_MODE = -1;
    private static final float WALLPAPER_DIM_AMOUNT = 0.4f;

    private static BatterySaverPolicyManager sSingleton;

    private final Context mContext;
    private DisplayManagerWrapper mDisplayManagerWrapper;
    private final PowerManager mPowerManager;
    private final WallpaperManager mWallpaperManager;

    static synchronized BatterySaverPolicyManager getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new BatterySaverPolicyManager(context.getApplicationContext());
        }
        return sSingleton;
    }

    @VisibleForTesting
    static void reset() {
        sSingleton = null;
    }

    private BatterySaverPolicyManager(Context context) {
        mContext = context;
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        mDisplayManagerWrapper =
                displayManager != null ? new DisplayManagerWrapperImpl(displayManager) : null;
        mPowerManager = context.getSystemService(PowerManager.class);
        mWallpaperManager = WallpaperManager.getInstance(context);
    }

    void overrideBatterySaverPolicyForEBS() {
        Log.d(TAG, "overrideBatterySaverPolicyForEBS");
        setWallpaperDimEnabled(true);
        setAllExternalDisplaysDisabled();
        BatterySaverPolicyConfig policy = mPowerManager.getFullPowerSavePolicy();
        int soundTriggerMode = policy.getSoundTriggerMode();
        if (soundTriggerMode != PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED) {
            saveSoundTriggerMode(soundTriggerMode);
        }
        updateFullPowerSavePolicy(policy, PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED);
        Log.v(
                TAG,
                String.format(
                        "disableSoundTrigger() from %d to %d",
                        soundTriggerMode, PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED));
    }

    void overrideBatterySaverPolicy() {
        Log.d(TAG, "overrideBatterySaverPolicy");
        setWallpaperDimEnabled(true);
    }

    void restoreBatterySaverPolicy() {
        Log.d(TAG, "restoreBatterySaverPolicy");
        setWallpaperDimEnabled(false);
        BatterySaverPolicyConfig policy = mPowerManager.getFullPowerSavePolicy();
        int soundTriggerMode = policy.getSoundTriggerMode();
        if (soundTriggerMode != PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED) {
            saveSoundTriggerMode(INVALID_SOUND_TRIGGER_MODE);
            Log.v(TAG, "sound trigger mode is already reset by others!");
            return;
        }
        int savedSoundTriggerMode =
                getSharedPreferences().getInt(KEY_SOUND_TRIGGER_MODE, INVALID_SOUND_TRIGGER_MODE);
        if (savedSoundTriggerMode == INVALID_SOUND_TRIGGER_MODE) {
            Log.v(TAG, "there is no valid sound trigger mode in the preference!");
            return;
        }
        saveSoundTriggerMode(INVALID_SOUND_TRIGGER_MODE);
        updateFullPowerSavePolicy(policy, savedSoundTriggerMode);
        Log.v(
                TAG,
                String.format(
                        "restoreSoundTrigger() from %d to %d",
                        soundTriggerMode, savedSoundTriggerMode));
    }

    SharedPreferences getSharedPreferences() {
        return mContext.createDeviceProtectedStorageContext()
                .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }

    void setAllExternalDisplaysDisabled() {
        if (mDisplayManagerWrapper == null) {
            return;
        }
        for (Display display : mDisplayManagerWrapper.getDisplays()) {
            if (display.getType() == Display.TYPE_EXTERNAL) {
                mDisplayManagerWrapper.disableConnectedDisplay(display.getDisplayId());
            }
        }
    }

    @VisibleForTesting
    void overrideDisplayManagerWrapper(DisplayManagerWrapper displayManagerWrapper) {
        mDisplayManagerWrapper = displayManagerWrapper;
    }

    private void updateFullPowerSavePolicy(BatterySaverPolicyConfig policy, int soundTriggerMode) {
        mPowerManager.setFullPowerSavePolicy(
                new BatterySaverPolicyConfig.Builder(policy)
                        .setSoundTriggerMode(soundTriggerMode)
                        .build());
    }

    private void saveSoundTriggerMode(int soundTriggerMode) {
        if (!getSharedPreferences()
                .edit()
                .putInt(KEY_SOUND_TRIGGER_MODE, soundTriggerMode)
                .commit()) {
            Log.wtf(TAG, String.format("failed to commit sound trigger mode:%d", soundTriggerMode));
        }
    }

    private void setWallpaperDimEnabled(boolean enabled) {
        if (mWallpaperManager != null) {
            mWallpaperManager.setWallpaperDimAmount(enabled ? WALLPAPER_DIM_AMOUNT : 0f);
            HistoricalLogUtil.logWallpaperDimEvent(mContext, enabled);
            Log.d(TAG, "setWallpaperDimEnabled: " + enabled);
        }
    }
}
