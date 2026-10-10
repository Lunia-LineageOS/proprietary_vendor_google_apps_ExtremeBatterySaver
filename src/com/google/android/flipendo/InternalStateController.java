package com.google.android.flipendo;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.android.flipendo.utils.ThreadUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

public final class InternalStateController {
    private static final String TAG = "InternalStateController";

    static final String INTERNAL_STATE_FILE_NAME = "internal_state";
    static final String RESUSPEND_STATE_FILE_NAME = "resuspend_state";
    static final String RESUSPEND_TIMESTAMPS_FILE_NAME = "resuspend_timestamps";

    // Settings.Secure key read by the backup transport; it has no public constant.
    private static final String BACKUP_SKIP_USER_FACING_DATA = "backup_skip_user_facing_data";

    private static InternalStateController sSingleton;

    private final Context mContext;
    private final SharedPreferences mInternalStateSharedPreferences;
    private final String mKeyPrefixResuspendWarningPosted;
    private final String mKeyPromoShownCount;
    private final String mKeyState;
    private final String mKeyWorkProfile;
    private final String mKeyWorkProfilePauseTimestamp;
    private final String mKeyWorkProfileWarningPosted;
    private final SharedPreferences mResuspendStateSharedPreferences;
    private final SharedPreferences mResuspendTimestampsSharedPreferences;

    public static synchronized InternalStateController getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new InternalStateController(context.getApplicationContext());
        }
        return sSingleton;
    }

    private InternalStateController(Context context) {
        mContext = context.getApplicationContext();
        mInternalStateSharedPreferences =
                context.createDeviceProtectedStorageContext()
                        .getSharedPreferences(INTERNAL_STATE_FILE_NAME, Context.MODE_PRIVATE);
        mResuspendTimestampsSharedPreferences =
                context.createDeviceProtectedStorageContext()
                        .getSharedPreferences(RESUSPEND_TIMESTAMPS_FILE_NAME, Context.MODE_PRIVATE);
        mResuspendStateSharedPreferences =
                context.createDeviceProtectedStorageContext()
                        .getSharedPreferences(RESUSPEND_STATE_FILE_NAME, Context.MODE_PRIVATE);
        mKeyState = context.getString(R.string.shared_pref_internal_state_state_key);
        mKeyPromoShownCount =
                context.getString(R.string.shared_pref_internal_state_promo_count_key);
        mKeyWorkProfile = context.getString(R.string.shared_pref_lever_work_profile_key);
        mKeyPrefixResuspendWarningPosted =
                context.getString(
                        R.string.shared_pref_resuspend_state_resuspend_warning_posted_key_prefix);
        mKeyWorkProfilePauseTimestamp =
                context.getString(
                        R.string.shared_pref_resuspend_state_work_profile_pause_timestamp_key);
        mKeyWorkProfileWarningPosted =
                context.getString(
                        R.string.shared_pref_resuspend_state_work_profile_warning_posted_key);
    }

    public void setState(InternalState state) {
        if (ThreadUtils.isMainThread()) {
            Log.w(TAG, "setState called from main thread");
        }
        SharedPreferences.Editor editor = mInternalStateSharedPreferences.edit();
        editor.putInt(mKeyState, state.getNumber());
        Preconditions.checkArgument(
                editor.commit(), "Failed to write our internal state %s", state.name());
        if (!Settings.Secure.putInt(
                mContext.getContentResolver(),
                BACKUP_SKIP_USER_FACING_DATA,
                state == InternalState.ENABLED ? 1 : 0)) {
            Log.e(TAG, "Failed to write backup secure setting");
        }
    }

    @VisibleForTesting
    public void clearForTest() {
        mInternalStateSharedPreferences.edit().clear().commit();
        mResuspendStateSharedPreferences.edit().clear().commit();
        mResuspendTimestampsSharedPreferences.edit().clear().commit();
    }

    public boolean isFlipendoEnabled() {
        InternalState state = getState();
        switch (state) {
            case UNKNOWN:
            case DISABLED:
                return false;
            case ENABLED:
                return true;
            default:
                throw new AssertionError("Unexpected state: " + state);
        }
    }

    public boolean shouldTurnWorkProfileBackOn() {
        boolean turnedOff = mInternalStateSharedPreferences.getBoolean(mKeyWorkProfile, false);
        Log.i(TAG, "shouldTurnWorkProfileBackOn? " + turnedOff);
        return turnedOff;
    }

    public void setTurnedOffWorkProfile(boolean turnedOff) {
        Log.i(TAG, "Recording disabled work profile " + turnedOff);
        mInternalStateSharedPreferences.edit().putBoolean(mKeyWorkProfile, turnedOff).apply();
    }

    public InternalState getState() {
        return InternalState.forNumber(
                mInternalStateSharedPreferences.getInt(
                        mKeyState, InternalState.UNKNOWN.getNumber()));
    }

    @SuppressWarnings("unchecked")
    public ImmutableMap<String, Long> getAllResuspendTimestamps() {
        return ImmutableMap.copyOf(
                (Map<String, Long>) mResuspendTimestampsSharedPreferences.getAll());
    }

    public Optional<Long> getResuspendTimestamp(String packageName) {
        if (!mResuspendTimestampsSharedPreferences.contains(packageName)) {
            return Optional.empty();
        }
        return Optional.of(mResuspendTimestampsSharedPreferences.getLong(packageName, 0L));
    }

    public void setResuspendTimestamp(String packageName, long timestamp) {
        mResuspendTimestampsSharedPreferences.edit().putLong(packageName, timestamp).apply();
    }

    public boolean hasResuspendWarningNotificationPosted(String packageName) {
        return mResuspendStateSharedPreferences.getBoolean(
                getResuspendWarningKey(packageName), false);
    }

    public void setResuspendWarningNotificationPosted(String packageName) {
        mResuspendStateSharedPreferences
                .edit()
                .putBoolean(getResuspendWarningKey(packageName), true)
                .apply();
    }

    private void clearResuspendWarningNotificationPosted(String packageName) {
        mResuspendStateSharedPreferences.edit().remove(getResuspendWarningKey(packageName)).apply();
    }

    public void clearResuspendState(String packageName) {
        removeResuspendTimestamp(packageName);
        clearResuspendWarningNotificationPosted(packageName);
    }

    public void clearWorkProfileState() {
        clearWorkProfilePauseTimestamp();
        clearWorkProfileWarningPosted();
    }

    public Optional<Long> getWorkProfilePauseTimestamp() {
        if (!mResuspendStateSharedPreferences.contains(mKeyWorkProfilePauseTimestamp)) {
            return Optional.empty();
        }
        return Optional.of(
                mResuspendStateSharedPreferences.getLong(mKeyWorkProfilePauseTimestamp, 0L));
    }

    public void setWorkProfileWarningPosted() {
        mResuspendStateSharedPreferences
                .edit()
                .putBoolean(mKeyWorkProfileWarningPosted, true)
                .apply();
    }

    public boolean hasWorkProfileWarningPosted() {
        return mResuspendStateSharedPreferences.getBoolean(mKeyWorkProfileWarningPosted, false);
    }

    public void clearAllResuspensionStorage() {
        Log.i(TAG, "Clearing all resuspension storage");
        mResuspendStateSharedPreferences.edit().clear().apply();
        mResuspendTimestampsSharedPreferences.edit().clear().apply();
    }

    private void removeResuspendTimestamp(String packageName) {
        mResuspendTimestampsSharedPreferences.edit().remove(packageName).apply();
    }

    private String getResuspendWarningKey(String packageName) {
        return mKeyPrefixResuspendWarningPosted + packageName;
    }

    private void clearWorkProfilePauseTimestamp() {
        mResuspendStateSharedPreferences.edit().remove(mKeyWorkProfilePauseTimestamp).apply();
    }

    private void clearWorkProfileWarningPosted() {
        mResuspendStateSharedPreferences.edit().remove(mKeyWorkProfileWarningPosted).apply();
    }
}
