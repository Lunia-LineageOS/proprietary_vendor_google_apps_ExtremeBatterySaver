package com.google.android.flipendo.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.flipendo.BatterySaverManager;
import com.google.android.flipendo.R;
import com.google.android.flipendo.backup.BackupManagerWrapper;
import com.google.android.flipendo.utils.DeviceConfigUtils;

/** Stores the user's Extreme Battery Saver preferences. */
public final class SettingsController {
    private static final String TAG = "SettingsController";

    private static final String SHARED_PREFS_NAME = "settings_preferences";

    private static SettingsController sSingleton;

    private final String mAggressiveKey;
    private final String mAskKey;
    private final Context mContext;
    private final String mMildKey;
    private final SharedPreferences mSharedPreferences;
    private final String mTerminatorEnabledKey;
    private final String mTerminatorRequestKey;
    private final String mWorkProfileKey;

    public static synchronized SettingsController getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new SettingsController(context.getApplicationContext());
        }
        return sSingleton;
    }

    private SettingsController(Context context) {
        mContext = context.getApplicationContext();
        mAggressiveKey = SavingsMode.AGGRESSIVE.getKeyString(context);
        mMildKey = SavingsMode.MILD.getKeyString(context);
        mAskKey = SavingsMode.ASK.getKeyString(context);
        mWorkProfileKey = context.getString(R.string.work_profile_key);
        mTerminatorRequestKey = context.getString(R.string.terminator_request_key);
        mTerminatorEnabledKey = context.getString(R.string.terminator_enabled_key);
        mSharedPreferences = getSharedPreferences(context);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(
                (prefs, key) -> BackupManagerWrapper.getInstance(mContext).dataChanged());
    }

    public SavingsMode getSavedSavingsMode() {
        if (!isSavingsModeSetByTheUser()) {
            return DeviceConfigUtils.getDefaultSavingsMode();
        }
        if (mSharedPreferences.getBoolean(mAskKey, false)) {
            if (!DeviceConfigUtils.getIsAskEveryTimeFeatureEnabled()) {
                Log.w(TAG, "User selected `ask`, but feature is disabled");
                return SavingsMode.MILD;
            }
            return SavingsMode.ASK;
        }
        if (mSharedPreferences.getBoolean(mAggressiveKey, false)) {
            return SavingsMode.AGGRESSIVE;
        }
        if (!mSharedPreferences.getBoolean(mMildKey, false)) {
            Log.w(TAG, "At least one key is set, but none are true. Assuming mild.");
        }
        return SavingsMode.MILD;
    }

    public boolean isSavingsModeSetByTheUser() {
        return mSharedPreferences.contains(mAggressiveKey)
                || mSharedPreferences.contains(mMildKey)
                || mSharedPreferences.contains(mAskKey);
    }

    public boolean shouldIgnoreWorkProfile() {
        return mSharedPreferences.getBoolean(mWorkProfileKey, false);
    }

    public boolean shouldExecuteTerminator() {
        return mSharedPreferences.getBoolean(mTerminatorRequestKey, false);
    }

    public void setEnableTerminatorRequest(boolean request) {
        mSharedPreferences.edit().putBoolean(mTerminatorRequestKey, request).apply();
    }

    public boolean isTerminatorModeEnabled() {
        return mSharedPreferences.getBoolean(mTerminatorEnabledKey, false);
    }

    public void setTerminatorModeEnabled(boolean enabled) {
        mSharedPreferences.edit().putBoolean(mTerminatorEnabledKey, enabled).apply();
    }

    public void clearTerminatorModeStorage() {
        mSharedPreferences
                .edit()
                .remove(mTerminatorRequestKey)
                .remove(mTerminatorEnabledKey)
                .apply();
    }

    public void setSavingsMode(SavingsMode savingsMode) {
        mSharedPreferences
                .edit()
                .putBoolean(
                        SavingsMode.MILD.getKeyString(mContext),
                        savingsMode.equals(SavingsMode.MILD))
                .putBoolean(
                        SavingsMode.AGGRESSIVE.getKeyString(mContext),
                        savingsMode.equals(SavingsMode.AGGRESSIVE))
                .putBoolean(
                        SavingsMode.ASK.getKeyString(mContext), savingsMode.equals(SavingsMode.ASK))
                .apply();
        // Stock immediately migrates "ask" back to "mild".
        if (savingsMode.equals(SavingsMode.ASK)) {
            BatterySaverManager.getInstance(mContext)
                    .ifPresent(BatterySaverManager::removeAskSavingModeIfNeeded);
        }
    }

    String getModeTitle() {
        return getSavedSavingsMode().getModeTitle(mContext);
    }

    public enum SavingsMode {
        MILD(R.string.savings_mode_mild_key, R.string.savings_mode_mild_title),
        AGGRESSIVE(R.string.savings_mode_aggressive_key, R.string.savings_mode_aggressive_title),
        ASK(R.string.savings_mode_ask_key, R.string.savings_mode_ask_title);

        private final int mKey;
        private final int mTitleId;

        SavingsMode(int key, int titleId) {
            mKey = key;
            mTitleId = titleId;
        }

        public String getKeyString(Context context) {
            return context.getString(mKey);
        }

        public String getModeTitle(Context context) {
            return context.getString(mTitleId);
        }
    }

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }
}
