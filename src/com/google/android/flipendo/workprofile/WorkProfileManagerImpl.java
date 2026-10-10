package com.google.android.flipendo.workprofile;

import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.google.android.flipendo.InternalStateController;
import com.google.android.flipendo.notification.TurnWorkProfileBackOnNotification;
import com.google.android.flipendo.utils.AllowedAppsUtils;

import java.util.Optional;

public final class WorkProfileManagerImpl implements WorkProfileManager {
    private static final String TAG = "WorkProfileManagerImpl";

    private static WorkProfileManager sInstance;

    private final IActivityTaskManager mActivityTaskManager;
    private final Context mContext;
    private final InternalStateController mInternalStateController;
    private final UserManager mUserManager;

    public static synchronized WorkProfileManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new WorkProfileManagerImpl(context);
        }
        return sInstance;
    }

    private WorkProfileManagerImpl(Context context) {
        mContext = context.getApplicationContext();
        mActivityTaskManager = ActivityTaskManager.getService();
        mInternalStateController = InternalStateController.getInstance(mContext);
        mUserManager = context.getSystemService(UserManager.class);
    }

    @Override
    public void restoreWorkProfile() {
        Log.i(TAG, "Trying to turn work profile back on.");
        turnOnWorkProfile(false);
        mInternalStateController.setTurnedOffWorkProfile(false);
    }

    /**
     * Turns the work profile back on. When not triggered by the user, this only succeeds if no
     * credential is required, and otherwise posts a notification asking the user to do it.
     */
    @Override
    public void turnOnWorkProfile(boolean fromUser) {
        Optional<UserHandle> workProfile = getWorkProfile();
        if (!workProfile.isPresent()) {
            Log.e(TAG, "Could not find work profile to enable.");
            return;
        }
        Log.i(TAG, "Turning on work profile");
        int flags = fromUser ? 0 : UserManager.QUIET_MODE_DISABLE_ONLY_IF_CREDENTIAL_NOT_REQUIRED;
        if (mContext.getSystemService(UserManager.class)
                .requestQuietModeEnabled(false, workProfile.get(), flags)) {
            return;
        }
        Log.w(TAG, "Unable to turn work profile back on ourselves.");
        if (!fromUser) {
            TurnWorkProfileBackOnNotification.show(mContext);
        }
    }

    @Override
    public void turnOffWorkProfile() {
        Optional<UserHandle> workProfile = getWorkProfile();
        if (!workProfile.isPresent()) {
            Log.i(TAG, "No work profile to disable.");
            return;
        }
        if (mUserManager.isQuietModeEnabled(workProfile.get())) {
            Log.i(TAG, "Work profile is already off");
            return;
        }
        try {
            Log.i(TAG, "Turning off work profile");
            mUserManager.requestQuietModeEnabled(true, workProfile.get());
            mInternalStateController.setTurnedOffWorkProfile(true);
        } catch (Exception e) {
            Log.e(TAG, "Error turning off work profile", e);
        }
    }

    @Override
    public boolean isWorkProfilePresent() {
        return getWorkProfile().isPresent();
    }

    @Override
    public boolean isWorkProfilePresentAndOn() {
        Optional<UserHandle> workProfile = getWorkProfile();
        return workProfile.isPresent() && !mUserManager.isQuietModeEnabled(workProfile.get());
    }

    @Override
    public boolean isWorkAppInForeground() {
        Optional<UserHandle> workProfile = getWorkProfile();
        if (!workProfile.isPresent()) {
            return false;
        }
        try {
            return mActivityTaskManager.getLastResumedActivityUserId()
                    == workProfile.get().getIdentifier();
        } catch (RemoteException e) {
            return false;
        }
    }

    private Optional<UserHandle> getWorkProfile() {
        if (mContext.getSystemService(DevicePolicyManager.class)
                .isOrganizationOwnedDeviceWithManagedProfile()) {
            Log.i(TAG, "COPE device");
            return Optional.empty();
        }
        UserHandle managedProfile = AllowedAppsUtils.getManagedProfile(mContext);
        if (managedProfile == null) {
            Log.i(TAG, "no work profile");
            return Optional.empty();
        }
        return Optional.of(managedProfile);
    }
}
