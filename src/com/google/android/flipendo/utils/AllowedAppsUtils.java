package com.google.android.flipendo.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

public abstract class AllowedAppsUtils {
    private static final String TAG = "AllowedAppsUtils";

    public static UserHandle getManagedProfile(Context context) {
        UserManager userManager = context.getSystemService(UserManager.class);
        for (UserHandle profile : userManager.getUserProfiles()) {
            if (profile.getIdentifier() != UserHandle.myUserId()
                    && userManager.getUserInfo(profile.getIdentifier()).isManagedProfile()) {
                Log.d(TAG, "profile = " + profile);
                return profile;
            }
        }
        return null;
    }

    public static boolean isManagedProfile(Context context) {
        return getManagedProfile(context) != null;
    }

    public static int getWorkProfileUserId(Context context) {
        UserHandle managedProfile = getManagedProfile(context);
        if (managedProfile != null) {
            return managedProfile.getIdentifier();
        }
        return UserHandle.USER_NULL;
    }

    public static Context getContextAsUser(Context context, int userId) {
        if (userId == UserHandle.myUserId()) {
            return context;
        }
        try {
            return context.createPackageContextAsUser(
                    "com.android.settings", 0, UserHandle.of(userId));
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Cannot get context for userId: " + userId, e);
            return context;
        }
    }

    public static boolean isWorkProfileTabEnable(Context context) {
        return DeviceConfigUtils.isWorkProfileTabEnabled() && isManagedProfile(context);
    }

    /** Strips the "userId|" prefix from a work profile app key. */
    public static String getPackageName(String key) {
        String[] parts = key.split("\\|");
        return parts.length != 2 ? key : parts[1];
    }
}
