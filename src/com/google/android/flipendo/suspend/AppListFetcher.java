package com.google.android.flipendo.suspend;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.settingslib.fuelgauge.BatteryUtils;

import com.google.android.flipendo.collectors.ImmutableCollectors;
import com.google.android.flipendo.utils.AllowedAppsUtils;
import com.google.android.flipendo.utils.DeviceConfigUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Computes which packages Extreme Battery Saver should pause. */
public final class AppListFetcher {
    private static final String TAG = "AppListFetcher";

    private static final int MATCH_DIRECT_BOOT_AWARENESS =
            PackageManager.MATCH_DIRECT_BOOT_AUTO
                    | PackageManager.MATCH_DIRECT_BOOT_AWARE
                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;

    private static final String[] PACKAGES_TO_SKIP = {
        "com.google.android.apps.betterbug",
        "com.google.android.GoogleCamera",
        "com.google.android.deskclock",
        "com.google.android.apps.messaging",
        "com.google.android.dialer",
        "com.android.vending",
        "com.google.android.apps.safetyhub",
        "com.android.settings",
        "com.google.android.euicc",
        "com.google.android.flipendo",
        "com.google.android.apps.wellbeing",
    };

    private static final String[] TERMINATOR_MODE_PACKAGES_TO_SKIP = {
        "com.google.android.flipendo",
        "com.google.android.dialer",
        "com.android.settings",
        "com.google.android.apps.turbo",
    };

    private static AppListFetcher sInstance;

    private final AllowedAppListManager mAllowedAppListManager;
    private final Context mContext;
    private final PackageManager mPackageManager;
    private final ImmutableList<String> mPackagesToSkip;
    private final ImmutableList<String> mTerminatorModePackagesToSkip;

    public static synchronized AppListFetcher getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AppListFetcher(context);
        }
        return sInstance;
    }

    private AppListFetcher(Context context) {
        mContext = context.getApplicationContext();
        mPackageManager = context.getPackageManager();
        mAllowedAppListManager = AllowedAppListManager.getInstance(context);
        mPackagesToSkip =
                ImmutableList.<String>builder()
                        .add(PACKAGES_TO_SKIP)
                        .add(getDefaultLauncherPackageName(mPackageManager))
                        .addAll(DeviceConfigUtils.getAdditionalEssentialApps())
                        .build();
        mTerminatorModePackagesToSkip =
                ImmutableList.<String>builder()
                        .add(TERMINATOR_MODE_PACKAGES_TO_SKIP)
                        .add(getDefaultLauncherPackageName(mPackageManager))
                        .build();
    }

    private List<ApplicationInfo> getInstalledApplications(Context context) {
        return context.getPackageManager().getInstalledApplications(MATCH_DIRECT_BOOT_AWARENESS);
    }

    ImmutableSet<String> getAllPackagesToSuspend() {
        ArraySet<String> a11yPackageNames = BatteryUtils.getA11yPackageNames(mContext);
        Log.d(TAG, "accessibilityPackages: " + a11yPackageNames);
        return getInstalledApplications(mContext).stream()
                .filter(info -> !isSystemAppWithoutLauncherIntent(info, mPackageManager))
                .map(info -> info.packageName)
                .filter(this::shouldSuspendPackage)
                .filter(packageName -> !a11yPackageNames.contains(packageName))
                .collect(ImmutableCollectors.toImmutableSet());
    }

    ImmutableSet<String> getTerminatorModePackagesToSuspend() {
        ArraySet<String> a11yPackageNames = BatteryUtils.getA11yPackageNames(mContext);
        return getInstalledApplications(mContext).stream()
                .filter(info -> !isSystemAppWithoutLauncherIntent(info, mPackageManager))
                .map(info -> info.packageName)
                .filter(packageName -> !mTerminatorModePackagesToSkip.contains(packageName))
                .filter(packageName -> !a11yPackageNames.contains(packageName))
                .collect(ImmutableCollectors.toImmutableSet());
    }

    ArraySet<String> getAllPackagesToSuspendForWork() {
        int workProfileUserId = AllowedAppsUtils.getWorkProfileUserId(mContext);
        if (workProfileUserId == UserHandle.USER_NULL) {
            return new ArraySet<>();
        }
        Context workContext = AllowedAppsUtils.getContextAsUser(mContext, workProfileUserId);
        PackageManager workPackageManager = workContext.getPackageManager();
        return getInstalledApplications(workContext).stream()
                .filter(info -> !isSystemAppWithoutLauncherIntent(info, workPackageManager))
                .map(info -> getKey(workProfileUserId, info.packageName))
                .filter(this::shouldSuspendPackage)
                .map(AllowedAppsUtils::getPackageName)
                .collect(Collectors.toCollection(ArraySet::new));
    }

    public ImmutableList<String> getRequiredAppsList() {
        return mPackagesToSkip;
    }

    boolean shouldSuspendPackage(String key) {
        return !mPackagesToSkip.contains(key) && !mAllowedAppListManager.isPackageAllowed(key);
    }

    public String[] identifyUnsuspendablePackages(String[] packageNames) {
        Log.d(
                TAG,
                String.format(
                        Locale.ROOT,
                        "Calling PackageManager#getUnsuspendablePackages with %d apps",
                        packageNames.length));
        long startTime = SystemClock.elapsedRealtime();
        String[] unsuspendablePackages = mPackageManager.getUnsuspendablePackages(packageNames);
        Log.d(
                TAG,
                String.format(
                        Locale.ROOT,
                        "Completed getUnsuspendablePackages in %d ms",
                        SystemClock.elapsedRealtime() - startTime));
        return unsuspendablePackages;
    }

    private static String getKey(int userId, String packageName) {
        return userId + "|" + packageName;
    }

    private static String getDefaultLauncherPackageName(PackageManager packageManager) {
        ResolveInfo resolveInfo =
                packageManager.resolveActivity(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                        PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null) {
            return "";
        }
        ActivityInfo activityInfo = resolveInfo.activityInfo;
        return activityInfo == null ? "" : activityInfo.packageName;
    }

    private static boolean isSystemAppWithoutLauncherIntent(
            ApplicationInfo info, PackageManager packageManager) {
        return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && packageManager
                        .queryIntentActivities(
                                new Intent(Intent.ACTION_MAIN)
                                        .setPackage(info.packageName)
                                        .addCategory(Intent.CATEGORY_LAUNCHER),
                                MATCH_DIRECT_BOOT_AWARENESS)
                        .isEmpty();
    }

    @VisibleForTesting
    static void resetForTest() {
        sInstance = null;
    }
}
