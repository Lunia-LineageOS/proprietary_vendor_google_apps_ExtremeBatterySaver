package com.google.android.flipendo.suspend;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.ArraySet;

import com.google.android.flipendo.utils.AllowedAppsUtils;
import com.google.android.flipendo.utils.DeviceConfigUtils;

import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Stores the essential apps chosen by the user, keyed by package name ("userId|packageName" for the
 * work profile).
 */
public final class AllowedAppListManager {
    private static final String SHARED_PREFS_NAME = "allowed_app_list_preferences";
    private static final int CLEANING_JOB_ID = 5000;

    private static AllowedAppListManager sInstance;

    private final Context mContext;
    private final JobScheduler mJobScheduler;
    private final PackageManager mPackageManager;
    private final SharedPreferences mSharedPreferences;

    public static synchronized AllowedAppListManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AllowedAppListManager(context);
        }
        return sInstance;
    }

    private AllowedAppListManager(Context context) {
        mContext = context.getApplicationContext();
        mJobScheduler = context.getSystemService(JobScheduler.class);
        mPackageManager = context.getPackageManager();
        mSharedPreferences = getSharedPreferences(context);
    }

    public boolean isPackageAllowed(String key) {
        return mSharedPreferences.getBoolean(key, false);
    }

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void scheduleCleaning() {
        mJobScheduler.schedule(
                new JobInfo.Builder(
                                CLEANING_JOB_ID,
                                new ComponentName(mContext, AllowedAppListCleaningJobService.class))
                        .setPeriodic(Duration.ofDays(1).toMillis())
                        .setRequiresCharging(true)
                        .build());
    }

    void removePackagesNotPresentOnDevice() {
        ArraySet<String> packagesToRemove = new ArraySet<>();
        ArraySet<String> installedApps =
                getInstalledAppsAsUser(mPackageManager, UserHandle.USER_SYSTEM);
        if (DeviceConfigUtils.isWorkProfileTabEnabled()) {
            int workProfileUserId = AllowedAppsUtils.getWorkProfileUserId(mContext);
            if (workProfileUserId != UserHandle.USER_NULL) {
                installedApps.addAll(
                        getInstalledAppsAsUser(
                                AllowedAppsUtils.getContextAsUser(mContext, workProfileUserId)
                                        .getPackageManager(),
                                workProfileUserId));
            }
        }
        for (String key : mSharedPreferences.getAll().keySet()) {
            if (!installedApps.contains(key)) {
                packagesToRemove.add(key);
            }
        }
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        for (String key : packagesToRemove) {
            editor.remove(key);
        }
        editor.apply();
    }

    public void setAllowedApp(String key, boolean allowed) {
        mSharedPreferences.edit().putBoolean(key, allowed).apply();
    }

    private static ArraySet<String> getInstalledAppsAsUser(
            PackageManager packageManager, int userId) {
        return packageManager.getInstalledApplications(0).stream()
                .map(info -> getKey(userId, info.packageName))
                .collect(Collectors.toCollection(ArraySet::new));
    }

    private static String getKey(int userId, String packageName) {
        if (userId == UserHandle.USER_SYSTEM) {
            return packageName;
        }
        return userId + "|" + packageName;
    }
}
