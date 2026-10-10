package com.google.android.flipendo.appactivity;

import android.app.ActivityManager;
import android.content.Context;
import android.os.UserHandle;
import android.util.Log;

import com.google.android.flipendo.collectors.ImmutableCollectors;
import com.google.android.flipendo.utils.AllowedAppsUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Finds the packages the user is currently interacting with. */
public final class AppActivityManager {
    private static final String TAG = "AppActivityManager";

    private static final int MAX_RUNNING_TASKS = 10;
    private static final String CHROME_PACKAGE_NAME = "com.android.chrome";
    private static final String CHROME_CUSTOM_TAB_ACTIVITY =
            "org.chromium.chrome.browser.customtabs.CustomTabActivity";
    private static final String GOOGLE_SEARCH_PACKAGE_NAME =
            "com.google.android.googlequicksearchbox";

    private static AppActivityManager sSingleton;

    private final ActivityManager mActivityManager;
    private final Context mContext;
    private Map<Integer, ImmutableSet<String>> mUserToForegroundPackages;
    private Map<Integer, ImmutableList<ActivityManager.RunningTaskInfo>> mUserToRunningTasks;

    public static synchronized AppActivityManager getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new AppActivityManager(context.getApplicationContext());
        }
        return sSingleton;
    }

    private AppActivityManager(Context context) {
        mActivityManager = context.getSystemService(ActivityManager.class);
        mContext = context;
    }

    public boolean isPackageInForeground(String packageName) {
        return getForegroundPackages().contains(packageName);
    }

    public ImmutableSet<String> getForegroundPackages() {
        return getForegroundPackages(UserHandle.myUserId());
    }

    /**
     * Returns the packages of the given user that have a foreground process and a visible activity,
     * including apps showing a Chrome Custom Tab.
     */
    public ImmutableSet<String> getForegroundPackages(int userId) {
        boolean isWorkProfileTabEnabled = AllowedAppsUtils.isWorkProfileTabEnable(mContext);
        // With the work profile tab, the maps are cached until resetForegroundPackagesMaps().
        if (mUserToForegroundPackages == null || !isWorkProfileTabEnabled) {
            mUserToForegroundPackages = loadForegroundPackages();
        }
        if (!mUserToForegroundPackages.containsKey(userId)) {
            Log.d(TAG, "No packages with process in foreground for user " + userId);
            return ImmutableSet.of();
        }
        ImmutableSet<String> foregroundPackages = mUserToForegroundPackages.get(userId);
        Log.d(TAG, "key = " + userId + ", foregroundPackages = " + foregroundPackages);
        if (mUserToRunningTasks == null || !isWorkProfileTabEnabled) {
            mUserToRunningTasks = loadRunningTasks();
        }
        ImmutableList<ActivityManager.RunningTaskInfo> runningTasks =
                mUserToRunningTasks.get(userId);
        Log.d(TAG, "key = " + userId + ", runningTasks = " + runningTasks);
        ImmutableSet<String> packagesWithCustomTabs =
                foregroundPackages.contains(CHROME_PACKAGE_NAME)
                        ? getPackagesWithOpenChromeCustomTabs(runningTasks)
                        : ImmutableSet.of();
        return ImmutableSet.copyOf(
                Sets.filter(
                        Sets.union(
                                Sets.intersection(
                                        foregroundPackages,
                                        getPackagesWithVisibleActivities(runningTasks)),
                                packagesWithCustomTabs),
                        packageName -> !GOOGLE_SEARCH_PACKAGE_NAME.equals(packageName)));
    }

    private Map<Integer, ImmutableSet<String>> loadForegroundPackages() {
        List<ActivityManager.RunningAppProcessInfo> processes =
                mActivityManager.getRunningAppProcesses();
        if (processes == null) {
            Log.w(TAG, "Couldn't get running processes");
            return ImmutableMap.of();
        }
        return processes.stream()
                .filter(AppActivityManager::isForegroundPackagesImportant)
                .collect(
                        Collectors.groupingBy(
                                process -> UserHandle.getUserId(process.uid),
                                Collectors.flatMapping(
                                        process -> Arrays.stream(process.pkgList),
                                        ImmutableCollectors.toImmutableSet())));
    }

    private Map<Integer, ImmutableList<ActivityManager.RunningTaskInfo>> loadRunningTasks() {
        List<ActivityManager.RunningTaskInfo> runningTasks =
                mActivityManager.getRunningTasks(MAX_RUNNING_TASKS);
        if (runningTasks == null || runningTasks.isEmpty()) {
            Log.w(TAG, "Couldn't get running tasks");
            return ImmutableMap.of();
        }
        return runningTasks.stream()
                .collect(
                        Collectors.groupingBy(
                                AppActivityManager::getUserId,
                                Collectors.mapping(
                                        task -> task, ImmutableCollectors.toImmutableList())));
    }

    private ImmutableSet<String> getPackagesWithVisibleActivities(
            List<ActivityManager.RunningTaskInfo> runningTasks) {
        if (runningTasks == null) {
            Log.d(TAG, "Couldn't get running tasks");
            return ImmutableSet.of();
        }
        return runningTasks.stream()
                .filter(task -> task.topActivity != null)
                .map(
                        task -> {
                            String packageName = task.topActivity.getPackageName();
                            Log.v(TAG, packageName + " has a topActivity in a recent task");
                            return packageName;
                        })
                .collect(ImmutableCollectors.toImmutableSet());
    }

    private ImmutableSet<String> getPackagesWithOpenChromeCustomTabs(
            List<ActivityManager.RunningTaskInfo> runningTasks) {
        if (runningTasks == null) {
            Log.d(TAG, "Couldn't get running tasks");
            return ImmutableSet.of();
        }
        return runningTasks.stream()
                .filter(task -> CHROME_CUSTOM_TAB_ACTIVITY.equals(task.topActivity.getClassName()))
                .filter(task -> task.baseActivity != null)
                .map(
                        task -> {
                            String packageName = task.baseActivity.getPackageName();
                            Log.v(
                                    TAG,
                                    packageName
                                            + " started a recent task with a Chrome Custom Tab"
                                            + " topActivity");
                            return packageName;
                        })
                .collect(ImmutableCollectors.toImmutableSet());
    }

    public void killBackgroundProcesses(String packageName) {
        mActivityManager.killBackgroundProcesses(packageName);
    }

    public void resetForegroundPackagesMaps() {
        mUserToForegroundPackages = null;
        mUserToRunningTasks = null;
    }

    /** Processes that are foreground only to serve another app don't count. */
    private static boolean isForegroundPackagesImportant(
            ActivityManager.RunningAppProcessInfo process) {
        return process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && process.importanceReasonCode
                        != ActivityManager.RunningAppProcessInfo.REASON_PROVIDER_IN_USE
                && process.importanceReasonCode
                        != ActivityManager.RunningAppProcessInfo.REASON_SERVICE_IN_USE;
    }

    private static int getUserId(ActivityManager.RunningTaskInfo task) {
        return task.userId;
    }
}
