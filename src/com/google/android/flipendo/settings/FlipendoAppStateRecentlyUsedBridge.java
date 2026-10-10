package com.google.android.flipendo.settings;

import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.settingslib.Utils;
import com.android.settingslib.applications.ApplicationsState;

import com.google.android.flipendo.R;
import com.google.android.flipendo.logging.WestworldLogger;
import com.google.android.flipendo.suspend.AllowedAppListManager;
import com.google.android.flipendo.suspend.AppListFetcher;
import com.google.android.flipendo.suspend.SuspensionManager;
import com.google.android.flipendo.utils.AllowedAppsUtils;
import com.google.android.flipendo.utils.BatteryUsageData;
import com.google.android.flipendo.utils.BatteryUsageUtils;
import com.google.common.collect.ImmutableList;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds launch counts, battery usage and the essential state of each app to the entries of the
 * essential apps list.
 */
class FlipendoAppStateRecentlyUsedBridge extends FlipendoAppStateBaseBridge {
    private static final String TAG = "EBSRecentlyUsedBridge";

    private static final int MATCH_DIRECT_BOOT_AWARENESS =
            PackageManager.MATCH_DIRECT_BOOT_AUTO
                    | PackageManager.MATCH_DIRECT_BOOT_AWARE
                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
    // Resuming an app sooner than this after it went to the background isn't a new launch.
    private static final Duration APP_LAUNCH_TIME_THRESHOLD = Duration.ofMillis(250);

    static final ApplicationsState.AppFilter FILTER_APP_LAUNCH_COUNTS =
            new ApplicationsState.AppFilter() {
                @Override
                public void init() {}

                @Override
                public boolean filterApp(ApplicationsState.AppEntry entry) {
                    RecentlyUsedState state = getRecentlyUsedState(entry);
                    return state != null && state.mLaunchCount >= 0;
                }
            };

    static final ApplicationsState.AppFilter FILTER_APP_BATTERY_USAGE =
            new ApplicationsState.AppFilter() {
                @Override
                public void init() {}

                @Override
                public boolean filterApp(ApplicationsState.AppEntry entry) {
                    RecentlyUsedState state = getRecentlyUsedState(entry);
                    return state != null && state.mBatteryUsage >= 0;
                }
            };

    static final Comparator<ApplicationsState.AppEntry> LAUNCH_COUNTS_COMPARATOR =
            (entry1, entry2) -> {
                RecentlyUsedState state1 = getRecentlyUsedState(entry1);
                RecentlyUsedState state2 = getRecentlyUsedState(entry2);
                if (state1 == null && state2 != null) {
                    return -1;
                }
                if (state1 != null && state2 == null) {
                    return 1;
                }
                if (state1 != null && state2 != null) {
                    if (state1.mLaunchCount < state2.mLaunchCount) {
                        return 1;
                    }
                    if (state1.mLaunchCount > state2.mLaunchCount) {
                        return -1;
                    }
                }
                return ApplicationsState.ALPHA_COMPARATOR.compare(entry1, entry2);
            };

    static final Comparator<ApplicationsState.AppEntry> BATTERY_USAGE_COMPARATOR =
            (entry1, entry2) -> {
                RecentlyUsedState state1 = getRecentlyUsedState(entry1);
                RecentlyUsedState state2 = getRecentlyUsedState(entry2);
                if (state1 == null && state2 != null) {
                    return -1;
                }
                if (state1 != null && state2 == null) {
                    return 1;
                }
                if (state1 != null && state2 != null) {
                    if (state1.mBatteryUsage < state2.mBatteryUsage) {
                        return 1;
                    }
                    if (state1.mBatteryUsage > state2.mBatteryUsage) {
                        return -1;
                    }
                }
                return ApplicationsState.ALPHA_COMPARATOR.compare(entry1, entry2);
            };

    private final Context mContext;
    private final ImmutableList<String> mExemptionPackages;
    @VisibleForTesting List<BatteryUsageData> mInjectBatteryUsageData = null;
    @VisibleForTesting Map<Integer, String[]> mInjectUnsuspendablePackages = null;
    private final boolean mIsWorkProfileTab;
    @VisibleForTesting boolean mRefreshSuspendState = true;
    private final SharedPreferences mSharedPreferences;
    private final Map<Integer, Set<String>> mUnsuspendable = new ArrayMap<>();
    @VisibleForTesting IUsageStatsManager mUsageStatsManager;
    @VisibleForTesting final List<Integer> mUserIds = new ArrayList<>();
    private final Map<Integer, Context> mUserToContextMap = new ArrayMap<>();
    private final SharedPreferences mWorkProfileSharedPreferences;
    private final String mWorkProfileSharedPreferencesKey;

    FlipendoAppStateRecentlyUsedBridge(
            Context context,
            ApplicationsState appState,
            FlipendoAppStateBaseBridge.Callback callback,
            boolean isWorkProfileTab) {
        super(appState, callback);
        mContext = context;
        mUsageStatsManager =
                IUsageStatsManager.Stub.asInterface(
                        ServiceManager.getService(Context.USAGE_STATS_SERVICE));
        mUserIds.add(context.getUserId());
        mSharedPreferences = AllowedAppListManager.getSharedPreferences(context);
        mExemptionPackages = AppListFetcher.getInstance(context).getRequiredAppsList();
        mWorkProfileSharedPreferences = SettingsController.getSharedPreferences(context);
        mWorkProfileSharedPreferencesKey = context.getString(R.string.work_profile_key);
        mIsWorkProfileTab = isWorkProfileTab;
        Log.d(TAG, "mIsWorkProfileTab = " + isWorkProfileTab);
        if (isWorkProfileTab) {
            int workProfileUserId = AllowedAppsUtils.getWorkProfileUserId(context);
            if (workProfileUserId != UserHandle.USER_NULL) {
                mUserIds.add(workProfileUserId);
            }
        }
    }

    @Override
    void pause() {
        super.pause();
        mUserToContextMap.clear();
    }

    @Override
    protected void loadAllExtraInfo() {
        if (mAppSession == null) {
            Log.d(TAG, "loadAllExtraInfo fail, mAppSession is null");
            return;
        }
        Log.d(TAG, "loadAllExtraInfo...");
        Map<String, RecentlyUsedState> usageStates = getUsageEvents(mUserIds, null);
        ArrayList<ApplicationsState.AppEntry> apps = mAppSession.getAllApps();
        mUnsuspendable.clear();
        for (Integer userId : mUserIds) {
            ArraySet<String> unsuspendable = new ArraySet<>();
            unsuspendable.addAll(getExemptionPackages(userId));
            unsuspendable.addAll(queryForUnsuspendablePackages(apps, userId));
            mUnsuspendable.put(userId, unsuspendable);
        }
        apps.stream()
                .filter(app -> !isSystemAppWithoutLauncherIntent(app.info))
                .forEach(app -> setAppEntryExtraInfo(app, usageStates));
    }

    @Override
    protected void updateExtraInfo(ApplicationsState.AppEntry app, String packageName, int uid) {
        Log.d(TAG, "updateExtraInfo...");
        setAppEntryExtraInfo(
                app,
                getUsageEvents(
                        new ArrayList<>(List.of(UserHandle.getUserId(app.info.uid))),
                        app.info.packageName));
    }

    private void setAppEntryExtraInfo(
            ApplicationsState.AppEntry app, Map<String, RecentlyUsedState> usageStates) {
        int userId = UserHandle.getUserId(app.info.uid);
        RecentlyUsedState state = usageStates.get(getKey(userId, app.info.packageName));
        if (state == null) {
            state = new RecentlyUsedState();
        }
        Set<String> unsuspendable = mUnsuspendable.get(userId);
        if (unsuspendable != null && unsuspendable.contains(app.info.packageName)) {
            state.mIsSystemApp = true;
            state.mIsChecked = true;
        }
        app.extraInfo = state;
    }

    /**
     * Collects the launch counts of the last day from UsageStats and the battery usage from
     * Settings, keyed by "userId|packageName".
     */
    @VisibleForTesting
    Map<String, RecentlyUsedState> getUsageEvents(List<Integer> userIds, String packageName) {
        long endTime = System.currentTimeMillis();
        long beginTime = endTime - DateUtils.DAY_IN_MILLIS;
        ArrayMap<String, RecentlyUsedState> usageStates = new ArrayMap<>();
        List<BatteryUsageData> batteryUsageData =
                mInjectBatteryUsageData != null
                        ? mInjectBatteryUsageData
                        : BatteryUsageUtils.getAllBatteryUsageData(mContext);
        Log.d(TAG, "getUsageEvents...UserIds size = " + userIds.size());
        for (int userId : userIds) {
            UsageEvents events;
            try {
                events =
                        packageName == null
                                ? mUsageStatsManager.queryEventsForUser(
                                        beginTime, endTime, userId, mContext.getPackageName())
                                : mUsageStatsManager.queryEventsForPackageForUser(
                                        beginTime,
                                        endTime,
                                        userId,
                                        packageName,
                                        mContext.getPackageName());
            } catch (RemoteException e) {
                Log.e(TAG, "Error fetching usage events: ", e);
                events = null;
            }
            if (events == null) {
                continue;
            }
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                String key = getKey(userId, getEffectivePackageName(event));
                RecentlyUsedState state = usageStates.get(key);
                if (state == null) {
                    state = new RecentlyUsedState();
                    usageStates.put(key, state);
                }
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (event.getTimeStamp() > state.mLastForeground) {
                        state.mLastForeground = event.getTimeStamp();
                    }
                    if (Instant.ofEpochMilli(state.mLastForeground)
                            .isAfter(
                                    Instant.ofEpochMilli(state.mLastBackground)
                                            .plus(APP_LAUNCH_TIME_THRESHOLD))) {
                        state.mLaunchCount++;
                    }
                }
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_STOPPED
                        && event.getTimeStamp() > state.mLastBackground) {
                    state.mLastBackground = event.getTimeStamp();
                }
            }
            if (batteryUsageData != null && !batteryUsageData.isEmpty()) {
                batteryUsageData.stream()
                        .filter(data -> data.mUserId == userId)
                        .forEach(data -> insertUsageData(usageStates, userId, data));
            }
        }
        Log.d(TAG, "UsageEvents size: " + usageStates.size());
        return usageStates;
    }

    private void insertUsageData(
            Map<String, RecentlyUsedState> usageStates, int userId, BatteryUsageData data) {
        String key = getKey(userId, data.mPackageName);
        RecentlyUsedState state = usageStates.get(key);
        if (state == null) {
            state = new RecentlyUsedState();
            usageStates.put(key, state);
        }
        state.mBatteryUsage = data.mUsagePercentage;
        state.mForegroundUsage = data.mForegroundUsageTimeInMs;
        state.mBackgroundUsage = data.mBackgroundUsageTimeInMs;
    }

    private String getKey(int userId, String packageName) {
        return userId + "|" + packageName;
    }

    private String getEffectivePackageName(UsageEvents.Event event) {
        if (event.getTaskRootPackageName() == null) {
            return event.getPackageName();
        }
        return event.getTaskRootPackageName();
    }

    private boolean isSystemAppWithoutLauncherIntent(ApplicationInfo info) {
        return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && getContextAsUser(UserHandle.getUserId(info.uid))
                        .getPackageManager()
                        .queryIntentActivities(
                                new Intent(Intent.ACTION_MAIN)
                                        .setPackage(info.packageName)
                                        .addCategory(Intent.CATEGORY_LAUNCHER),
                                PackageManager.ResolveInfoFlags.of(MATCH_DIRECT_BOOT_AWARENESS))
                        .isEmpty();
    }

    private List<String> getExemptionPackages(int userId) {
        if (userId == UserHandle.myUserId()) {
            return mExemptionPackages;
        }
        return ImmutableList.of();
    }

    private List<String> queryForUnsuspendablePackages(
            List<ApplicationsState.AppEntry> apps, int userId) {
        List<String> exemptionPackages = getExemptionPackages(userId);
        String[] packageNames =
                apps.stream()
                        .filter(app -> UserHandle.getUserId(app.info.uid) == userId)
                        .map(app -> app.info.packageName)
                        .filter(packageName -> !exemptionPackages.contains(packageName))
                        .toArray(String[]::new);
        List<String> unsuspendable = new ArrayList<>();
        Context userContext = AllowedAppsUtils.getContextAsUser(mContext, userId);
        String[] unsuspendablePackages =
                mInjectUnsuspendablePackages == null
                        ? null
                        : mInjectUnsuspendablePackages.get(userId);
        if (unsuspendablePackages == null) {
            unsuspendablePackages =
                    userContext.getPackageManager().getUnsuspendablePackages(packageNames);
        }
        Collections.addAll(unsuspendable, unsuspendablePackages);
        return unsuspendable;
    }

    Preference.OnPreferenceClickListener getOnPreferenceClickListener(
            ApplicationsState.AppEntry app, AllowedListAppPreference preference, int filterType) {
        if (app == null) {
            return null;
        }
        return clickedPreference -> updateStateAndSharedPreference(app, preference, filterType);
    }

    private boolean updateStateAndSharedPreference(
            ApplicationsState.AppEntry app, AllowedListAppPreference preference, int filterType) {
        RecentlyUsedState state = getRecentlyUsedState(app);
        if (state == null) {
            Log.e(TAG, "Click on an invalid column");
            return false;
        }
        boolean wasChecked = state.mIsChecked;
        state.mIsChecked = !wasChecked;
        if (state.mIsWorkProfile) {
            WestworldLogger.logWorkProfilePreferenceChange(wasChecked);
            mWorkProfileSharedPreferences
                    .edit()
                    .putBoolean(mWorkProfileSharedPreferencesKey, state.mIsChecked)
                    .apply();
        } else {
            WestworldLogger.logFlipendoEssentialAppsChange(
                    state.mIsChecked, app.info.packageName, mContext, filterType);
            mSharedPreferences.edit().putBoolean(getKey(app), state.mIsChecked).apply();
        }
        mCallback.onListItemClicked(app, state.mIsChecked);
        if (mRefreshSuspendState && !state.mIsWorkProfile) {
            if (state.mIsChecked) {
                SuspensionManager.getInstance(mContext).unsuspend(getKey(app));
            } else {
                SuspensionManager.getInstance(mContext).suspendWithoutAllowListCheck(getKey(app));
            }
        }
        return true;
    }

    private Context getContextAsUser(int userId) {
        Context context = mUserToContextMap.get(userId);
        if (context != null) {
            return context;
        }
        Context userContext = AllowedAppsUtils.getContextAsUser(mContext, userId);
        mUserToContextMap.put(userId, userContext);
        return userContext;
    }

    boolean isWorkProfile(ApplicationsState.AppEntry app) {
        RecentlyUsedState state = getRecentlyUsedState(app);
        return state != null && state.mIsWorkProfile;
    }

    Drawable getCheckedIcon(boolean isChecked) {
        return mContext.getDrawable(isChecked ? R.drawable.ic_cancel : R.drawable.ic_add);
    }

    boolean isSystemApp(ApplicationsState.AppEntry app) {
        RecentlyUsedState state = getRecentlyUsedState(app);
        return state != null && state.mIsSystemApp;
    }

    boolean isChecked(ApplicationsState.AppEntry app) {
        RecentlyUsedState state = getRecentlyUsedState(app);
        if (state == null) {
            return false;
        }
        if (state.mIsWorkProfile) {
            state.mIsChecked =
                    mWorkProfileSharedPreferences.getBoolean(
                            mWorkProfileSharedPreferencesKey, false);
        } else {
            state.mIsChecked =
                    state.mIsSystemApp || mSharedPreferences.getBoolean(getKey(app), false);
        }
        return state.mIsChecked;
    }

    static RecentlyUsedState getRecentlyUsedState(ApplicationsState.AppEntry app) {
        if (app == null) {
            return null;
        }
        return (RecentlyUsedState) app.extraInfo;
    }

    final String getBatteryUsage(ApplicationsState.AppEntry app) {
        RecentlyUsedState state = getRecentlyUsedState(app);
        return Utils.formatPercentage(state == null ? 0 : state.mBatteryUsage, true);
    }

    /** Returns the essential apps key, which is prefixed with the user id in the work tab. */
    private String getKey(ApplicationsState.AppEntry app) {
        if (mIsWorkProfileTab) {
            return getKey(UserHandle.getUserId(app.info.uid), app.info.packageName);
        }
        return app.info.packageName;
    }

    static final class RecentlyUsedState {
        boolean mIsChecked;
        boolean mIsSystemApp;
        boolean mIsWorkProfile;
        long mLastForeground = 0;
        long mLastBackground = 0;
        int mLaunchCount = 0;
        long mForegroundUsage = 0;
        long mBackgroundUsage = 0;
        double mBatteryUsage = 0;

        RecentlyUsedState() {}
    }
}
