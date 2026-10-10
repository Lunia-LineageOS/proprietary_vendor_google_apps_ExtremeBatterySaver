package com.google.android.flipendo.suspend;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.SuspendDialogInfo;
import android.hardware.google.pixel.vendor.PixelAtoms;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.android.flipendo.InternalStateController;
import com.google.android.flipendo.R;
import com.google.android.flipendo.appactivity.AppActivityManager;
import com.google.android.flipendo.bugreport.HistoricalLogUtil;
import com.google.android.flipendo.clock.Clock;
import com.google.android.flipendo.logging.WestworldLogger;
import com.google.android.flipendo.notification.TurnWorkProfileBackOnNotification;
import com.google.android.flipendo.settings.SettingsController;
import com.google.android.flipendo.utils.AllowedAppsUtils;
import com.google.android.flipendo.utils.DeviceConfigUtils;
import com.google.android.flipendo.utils.LogUtils;
import com.google.android.flipendo.workprofile.UpdateWorkProfileStateBroadcastReceiver;
import com.google.android.flipendo.workprofile.WorkProfileManager;
import com.google.android.flipendo.workprofile.WorkProfileManagerImpl;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Pauses (suspends) apps and the work profile, and re-pauses temporarily unpaused apps. */
public final class SuspensionManager {
    private static final String TAG = "SuspensionManager";

    /** Pseudo package name used to track the work profile pause state. */
    static final String WORK_PROFILE = "WORK_PROFILE";

    private static final String ACTION_UPDATE_WORK_PROFILE =
            "com.google.android.flipendo.workprofile.UPDATE";
    private static final int MIN_UNSUSPEND_DURATION_MINUTES = 3;

    private static final Duration WARNING_DURATION = Duration.ofMinutes(1);
    private static final Duration CLEANUP_DELAY = Duration.ofMinutes(1);
    private static final Duration DELAYED_SUSPEND_DURATION = Duration.ofMinutes(2);
    private static final long ONE_MINUTE_MS = Duration.ofMinutes(1).toMillis();
    private static final long ONE_SECOND_MS = Duration.ofSeconds(1).toMillis();
    private static final int CLEANUP_INTENT_REQUEST_CODE = "CLEANUP".hashCode();
    private static final int WORK_PROFILE_UPDATE_REQUEST_CODE = WORK_PROFILE.hashCode();

    private static SuspensionManager sInstance;
    @VisibleForTesting static Duration sUnsuspendDuration;

    private final AlarmManager mAlarmManager;
    private final AppActivityManager mAppActivityManager;
    private final AppColorManager mAppColorManager;
    private final AppListFetcher mAppListFetcher;
    private Clock mClock;
    private final Context mContext;
    private final InternalStateController mInternalStateController;
    private final PackageManager mPackageManager;
    private final SettingsController mSettingsController;
    private final Map<Integer, Context> mUserToContextMap = new ArrayMap<>();
    private WorkProfileManager mWorkProfileManager;

    public static synchronized SuspensionManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SuspensionManager(context);
        }
        return sInstance;
    }

    private SuspensionManager(Context context) {
        mContext = context.getApplicationContext();
        mAlarmManager = mContext.getSystemService(AlarmManager.class);
        mAppActivityManager = AppActivityManager.getInstance(context);
        mAppColorManager = AppColorManager.getInstance(mContext);
        mAppListFetcher = AppListFetcher.getInstance(mContext);
        mWorkProfileManager = WorkProfileManagerImpl.getInstance(mContext);
        mPackageManager = mContext.getPackageManager();
        mSettingsController = SettingsController.getInstance(mContext);
        mInternalStateController = InternalStateController.getInstance(mContext);
        sUnsuspendDuration =
                Duration.ofMinutes(
                        Math.max(
                                DeviceConfigUtils.getResuspendDelayMinutes(),
                                MIN_UNSUSPEND_DURATION_MINUTES));
        mClock = Clock.SYSTEM;
    }

    public void suspendAllApps() {
        suspend(mAppListFetcher.getAllPackagesToSuspend(), false);
        if (AllowedAppsUtils.isWorkProfileTabEnable(mContext)) {
            suspendForWork(mAppListFetcher.getAllPackagesToSuspendForWork(), false);
        }
    }

    public void suspendTerminatorModeApps() {
        suspend(mAppListFetcher.getTerminatorModePackagesToSuspend(), true);
    }

    /** Unpauses the apps that terminator mode paused but the regular mode wouldn't. */
    public void recoverSuspendAppsFromTerminatorMode() {
        ImmutableSet<String> terminatorModePackages =
                mAppListFetcher.getTerminatorModePackagesToSuspend();
        ImmutableSet<String> allPackages = mAppListFetcher.getAllPackagesToSuspend();
        Set<String> packagesToUnsuspend =
                terminatorModePackages.stream()
                        .filter(packageName -> !allPackages.contains(packageName))
                        .collect(Collectors.toSet());
        if (packagesToUnsuspend.isEmpty()) {
            return;
        }
        unsuspend(mContext, packagesToUnsuspend.toArray(String[]::new), false);
    }

    public void suspend(String packageName) {
        suspend(packageName, true, false);
    }

    public void suspendWithoutAllowListCheck(String key) {
        Log.i(TAG, "suspendWithoutAllowListCheck: " + key);
        if (key.contains("|")) {
            Log.i(TAG, "select work app to suspend: ".concat(key));
            suspendForWork(Set.of(AllowedAppsUtils.getPackageName(key)), true);
        } else {
            suspend(key, true, true);
        }
    }

    private void suspend(String packageName, boolean force, boolean skipAllowListCheck) {
        Log.i(TAG, "suspend: " + packageName);
        if (!skipAllowListCheck && !mAppListFetcher.shouldSuspendPackage(packageName)) {
            Log.d(
                    TAG,
                    packageName + " will not be suspended because it's either allowed or skipped.");
            return;
        }
        cancelResuspend(packageName);
        suspend(ImmutableSet.of(packageName), force);
    }

    /**
     * Suspends the given packages. Unless forced, apps in the foreground are kept unpaused for the
     * current session.
     */
    private void suspend(Set<String> packageNames, boolean force) {
        if (!mInternalStateController.isFlipendoEnabled()) {
            Log.w(TAG, "Flipendo is off. Not suspending.");
            return;
        }
        HistoricalLogUtil.logSuspendState(mContext, true);
        ImmutableSet<String> foregroundPackages = mAppActivityManager.getForegroundPackages();
        Log.d(TAG, "packagesWithProcessInForeground = " + foregroundPackages);
        List<String> packagesToKeepUnpaused = new ArrayList<>();
        String[] packagesToSuspend =
                packageNames.stream()
                        .filter(
                                packageName -> {
                                    boolean suspend =
                                            force || !foregroundPackages.contains(packageName);
                                    if (!suspend) {
                                        packagesToKeepUnpaused.add(packageName);
                                    }
                                    return suspend;
                                })
                        .toArray(String[]::new);
        Log.d(TAG, "Suspending apps ...");
        LogUtils.logLongMessage(Log.DEBUG, TAG, Arrays.toString(packagesToSuspend));
        String[] failedPackages =
                mPackageManager.setPackagesSuspended(
                        packagesToSuspend, true, null, null, getSuspendDialogInfo());
        if (failedPackages.length != 0) {
            Log.w(TAG, "Apps not suspended ... ");
            LogUtils.logLongMessage(Log.WARN, TAG, Arrays.toString(failedPackages));
        }
        ImmutableSet<String> failedPackageSet = ImmutableSet.copyOf(failedPackages);
        for (String packageName : packagesToSuspend) {
            if (failedPackageSet.contains(packageName)) {
                continue;
            }
            if (mAppColorManager.isAppGrayscale(packageName)) {
                mAppColorManager.unsetAppGrayscaleForSuspend(packageName);
            } else {
                Log.i(TAG, "App " + packageName + " is not currently grayscale, not clearing");
            }
        }
        if (!packagesToKeepUnpaused.isEmpty()) {
            ImmutableList<String> unsuspendablePackages =
                    ImmutableList.copyOf(
                            mAppListFetcher.identifyUnsuspendablePackages(
                                    packagesToKeepUnpaused.toArray(
                                            new String[packagesToKeepUnpaused.size()])));
            if (!unsuspendablePackages.isEmpty()) {
                Log.w(
                        TAG,
                        "asked to suspend unsuspendable packages. Skipping: "
                                + unsuspendablePackages);
                packagesToKeepUnpaused.removeAll(unsuspendablePackages);
            }
        }
        packagesToKeepUnpaused.forEach(
                packageName -> getInstance(mContext).keepAppUnpaused(packageName));
    }

    private void suspendForWork(Set<String> packageNames, boolean force) {
        Log.d(TAG, "suspendForWork...");
        if (!mInternalStateController.isFlipendoEnabled()) {
            Log.w(TAG, "Flipendo is off. Not suspending.");
            return;
        }
        UserHandle managedProfile = AllowedAppsUtils.getManagedProfile(mContext);
        if (managedProfile == null) {
            Log.d(TAG, "Work profile does not exist! Not suspending for work.");
            return;
        }
        HistoricalLogUtil.logSuspendState(mContext, true);
        ImmutableSet<String> foregroundPackages =
                mAppActivityManager.getForegroundPackages(managedProfile.getIdentifier());
        Log.d(TAG, "packagesWithProcessInForegroundForWork = " + foregroundPackages);
        String[] packagesToSuspend =
                packageNames.stream()
                        .filter(packageName -> force || !foregroundPackages.contains(packageName))
                        .toArray(String[]::new);
        Log.d(TAG, "Suspending work apps ...");
        LogUtils.logLongMessage(Log.DEBUG, TAG, Arrays.toString(packagesToSuspend));
        String[] failedPackages =
                getContextAsUser(managedProfile.getIdentifier())
                        .getPackageManager()
                        .setPackagesSuspended(
                                packagesToSuspend, true, null, null, getSuspendDialogInfo());
        if (failedPackages.length != 0) {
            Log.w(TAG, "Work apps not suspended ... ");
            LogUtils.logLongMessage(Log.WARN, TAG, Arrays.toString(failedPackages));
        }
        mAppActivityManager.resetForegroundPackagesMaps();
    }

    public void unsuspendAllApps(Context context) {
        boolean isUserUnlocked = context.getSystemService(UserManager.class).isUserUnlocked();
        boolean isDirectBoot = !isUserUnlocked;
        if (isUserUnlocked) {
            cancelAllResuspensionTimers();
        }
        unsuspend(context, getInstalledApplications(mContext), isDirectBoot);
        if (AllowedAppsUtils.isWorkProfileTabEnable(mContext)) {
            unsuspendAllAppsForWork(isDirectBoot);
        }
    }

    private void unsuspendAllAppsForWork(boolean isDirectBoot) {
        int workProfileUserId = AllowedAppsUtils.getWorkProfileUserId(mContext);
        if (workProfileUserId == UserHandle.USER_NULL) {
            Log.d(TAG, "Work profile is null, skipping work apps un-suspension...");
            return;
        }
        Context workContext = getContextAsUser(workProfileUserId);
        unsuspend(workContext, getInstalledApplications(workContext), isDirectBoot);
    }

    public void unsuspend(String key) {
        if (key.contains("|")) {
            String[] parts = key.split("\\|");
            if (parts.length != 2) {
                return;
            }
            unsuspend(getContextAsUser(Integer.valueOf(parts[0])), new String[] {parts[1]}, false);
            return;
        }
        cancelResuspend(key);
        unsuspend(mContext, new String[] {key}, false);
    }

    private void unsuspend(Context context, String[] packageNames, boolean isDirectBoot) {
        Log.d(TAG, "Unsuspending apps... ");
        LogUtils.logLongMessage(Log.DEBUG, TAG, Arrays.toString(packageNames));
        HistoricalLogUtil.logSuspendState(mContext, false);
        String[] failedPackages =
                context.getPackageManager()
                        .setPackagesSuspended(
                                packageNames, false, null, null, (SuspendDialogInfo) null);
        if (failedPackages.length != 0) {
            Log.w(TAG, "Apps not unsuspended...");
            LogUtils.logLongMessage(Log.WARN, TAG, Arrays.toString(failedPackages));
        }
        if (isDirectBoot) {
            return;
        }
        getInstance(context).updateCleanupTimer();
    }

    private SuspendDialogInfo getSuspendDialogInfo() {
        return new SuspendDialogInfo.Builder()
                .setTitle(R.string.suspend_dialog_title)
                .setMessage(R.string.suspend_dialog_text)
                .setNeutralButtonText(R.string.suspend_dialog_no)
                .setNeutralButtonAction(SuspendDialogInfo.BUTTON_ACTION_UNSUSPEND)
                .build();
    }

    public void queueResuspend(String packageName) {
        queueResuspend(packageName, calculateResuspendTime(sUnsuspendDuration));
    }

    public void pauseWorkProfile() {
        if (DeviceConfigUtils.isWorkProfileTabEnabled()) {
            Log.i(TAG, "Work profile tab is enabled. Skip the pause.");
            return;
        }
        if (!DeviceConfigUtils.getIsWorkProfileLeverEnabled()) {
            Log.i(TAG, "Not touching work profile");
            return;
        }
        if (!mWorkProfileManager.isWorkProfilePresentAndOn()) {
            Log.i(TAG, "No work profile or work profile is off");
            return;
        }
        if (mSettingsController.shouldIgnoreWorkProfile()) {
            Log.i(TAG, "Work profile lever disabled by user");
            return;
        }
        if (mWorkProfileManager.isWorkAppInForeground()) {
            Log.i(TAG, "Do not turn off work profile for this session");
            getInstance(mContext).keepAppUnpaused(WORK_PROFILE);
        } else {
            Log.i(TAG, "Turning off work profile now");
            mWorkProfileManager.turnOffWorkProfile();
            WestworldLogger.logWorkProfileTurnedOff(
                    PixelAtoms.FlipendoWorkProfileTurnedOff.Type.RIGHT_AWAY);
        }
    }

    public void updateWorkProfileState() {
        if (!getWorkProfilePauseTimestamp().isPresent()) {
            Log.w(TAG, "Unexpected state for work profile timer");
            return;
        }
        if (mInternalStateController.hasWorkProfileWarningPosted()) {
            Log.i(TAG, "Work profile warning already posted");
            cancelWorkProfilePause();
            mWorkProfileManager.turnOffWorkProfile();
            WestworldLogger.logWorkProfileTurnedOff(
                    PixelAtoms.FlipendoWorkProfileTurnedOff.Type.AFTER_DELAY);
            return;
        }
        Log.i(TAG, "Showing work profile warning");
        long pauseTimestamp = getWorkProfilePauseTimestamp().get();
        showWorkProfileWarningNotification(pauseTimestamp);
        startWorkProfileUpdateTimer(pauseTimestamp);
    }

    void cancelResuspend(String packageName) {
        cancelResuspend(packageName, true);
    }

    private void cancelResuspend(String packageName, boolean updateCleanupTimer) {
        cancelUpdateStateTimer(packageName);
        mInternalStateController.clearResuspendState(packageName);
        mAppColorManager.unsetAppGrayscale(packageName);
        if (updateCleanupTimer) {
            updateCleanupTimer();
        }
    }

    public void cancelAllResuspensionTimers() {
        Log.i(TAG, "cancelAllResuspensionTimers");
        cancelAllResuspensionTimers(
                ImmutableSet.copyOf(mInternalStateController.getAllResuspendTimestamps().keySet()));
    }

    public void restoreWorkProfile() {
        if (DeviceConfigUtils.isWorkProfileTabEnabled()) {
            return;
        }
        cancelWorkProfilePause();
        if (!mInternalStateController.shouldTurnWorkProfileBackOn()) {
            Log.i(TAG, "No need to touch work profile");
            return;
        }
        mWorkProfileManager.restoreWorkProfile();
    }

    public void cancelWorkProfilePause() {
        Log.i(TAG, "cancelWorkProfilePause");
        cancelWorkProfileUpdateTimer();
        mInternalStateController.clearWorkProfileState();
        updateCleanupTimer();
    }

    private void cancelAllResuspensionTimers(Collection<String> packageNames) {
        for (String packageName : packageNames) {
            Log.i(TAG, "Cancelling " + packageName);
            cancelResuspend(packageName, false);
        }
        updateCleanupTimer();
    }

    void updateResuspendState(String packageName) {
        if (!InternalStateController.getInstance(mContext).isFlipendoEnabled()) {
            Log.w(TAG, "Not updating state");
            cancelAllResuspensionTimers();
            return;
        }
        Optional<Long> resuspendTimestamp = getResuspendTimestamp(packageName);
        if (!resuspendTimestamp.isPresent()) {
            Log.w(TAG, "Bad state while updating " + packageName);
            cancelResuspend(packageName);
            return;
        }
        if (mInternalStateController.hasResuspendWarningNotificationPosted(packageName)) {
            Log.i(TAG, "Notification has been shown for " + packageName);
            suspend(packageName);
            return;
        }
        if (mClock.elapsedRealtime() > resuspendTimestamp.get()) {
            Log.i(TAG, "Resuspension expired for " + packageName);
            suspend(packageName);
            return;
        }
        showWarningNotification(packageName);
    }

    public void keepAppUnpaused(String packageName) {
        cancelResuspend(packageName);
        WestworldLogger.logFlipendoAppUnsuspended(
                packageName,
                mContext,
                PixelAtoms.FlipendoAppUnsuspended.Type.KEPT_UNPAUSED_FOR_SESSION);
    }

    public void keepWorkProfileUnpaused() {
        cancelWorkProfilePause();
    }

    public void onWorkProfileTurnedOff() {
        if (getWorkProfilePauseTimestamp().isPresent()) {
            cancelWorkProfilePause();
            mInternalStateController.setTurnedOffWorkProfile(true);
            WestworldLogger.logWorkProfileTurnedOff(
                    PixelAtoms.FlipendoWorkProfileTurnedOff.Type.MANUALLY);
        }
    }

    public void onWorkProfileTurnedOn() {
        TurnWorkProfileBackOnNotification.hide(mContext);
    }

    /** Re-pauses everything whose resuspension alarm was missed, e.g. while the device slept. */
    void cleanUpMissedResuspensionTriggers() {
        ImmutableMap<String, Long> resuspendTimestamps =
                ImmutableMap.copyOf(mInternalStateController.getAllResuspendTimestamps());
        if (resuspendTimestamps.isEmpty() && !getWorkProfilePauseTimestamp().isPresent()) {
            Log.w(TAG, "cleanUpMissedResuspensionTriggers no longer needed.");
            updateCleanupTimer();
            return;
        }
        long latestTimestamp =
                Math.max(
                        resuspendTimestamps.values().stream().max(Long::compareTo).orElse(0L),
                        getWorkProfilePauseTimestamp().orElse(0L));
        if (mClock.elapsedRealtime() < latestTimestamp) {
            Log.w(TAG, "cleanUpMissedResuspensionTriggers triggered early.");
            updateCleanupTimer();
            return;
        }
        Log.i(TAG, "cleanUpMissedResuspensionTriggers");
        ImmutableSet<String> packageNames = resuspendTimestamps.keySet();
        cancelAllResuspensionTimers(packageNames);
        for (String packageName : packageNames) {
            suspend(packageName);
        }
        getWorkProfilePauseTimestamp()
                .ifPresent(
                        timestamp -> {
                            mWorkProfileManager.turnOffWorkProfile();
                            WestworldLogger.logWorkProfileTurnedOff(
                                    PixelAtoms.FlipendoWorkProfileTurnedOff.Type.AFTER_DELAY);
                        });
        cancelWorkProfilePause();
    }

    private void queueResuspend(String packageName, long resuspendTime) {
        Log.i(TAG, "queueResuspend " + packageName + " at " + resuspendTime);
        mInternalStateController.setResuspendTimestamp(packageName, resuspendTime);
        startUpdateStateTimer(packageName, resuspendTime - WARNING_DURATION.toMillis());
        updateCleanupTimer();
    }

    private void startUpdateStateTimer(String packageName, long triggerTime) {
        Log.i(TAG, "startUpdateStateTimer " + packageName + " at " + triggerTime);
        mAlarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME, triggerTime, getUpdateStateIntent(packageName));
    }

    private void startWorkProfileUpdateTimer(long triggerTime) {
        Log.i(TAG, "startWorkProfileUpdateTimer at " + triggerTime);
        mAlarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME, triggerTime, getUpdateWorkProfileIntent());
    }

    private void cancelUpdateStateTimer(String packageName) {
        mAlarmManager.cancel(getUpdateStateIntent(packageName));
    }

    private void cancelWorkProfileUpdateTimer() {
        mAlarmManager.cancel(getUpdateWorkProfileIntent());
    }

    private PendingIntent getUpdateStateIntent(String packageName) {
        return PendingIntent.getBroadcast(
                mContext,
                getUpdateStateIntentRequestCode(packageName),
                new Intent(UpdateResuspendStateBroadcastReceiver.ACTION_UPDATE)
                        .setClass(mContext, UpdateResuspendStateBroadcastReceiver.class)
                        .setPackage(mContext.getPackageName())
                        .setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent getUpdateWorkProfileIntent() {
        return PendingIntent.getBroadcast(
                mContext,
                WORK_PROFILE_UPDATE_REQUEST_CODE,
                new Intent(ACTION_UPDATE_WORK_PROFILE)
                        .setClass(mContext, UpdateWorkProfileStateBroadcastReceiver.class)
                        .setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        .setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateCleanupTimer() {
        Log.i(TAG, "updateCleanupTimer");
        Set<Long> timestamps =
                new HashSet<>(mInternalStateController.getAllResuspendTimestamps().values());
        getWorkProfilePauseTimestamp().ifPresent(timestamps::add);
        if (timestamps.isEmpty()) {
            Log.i(TAG, "cancelling cleanup timer");
            cancelCleanupTimer();
            return;
        }
        long cleanupTime =
                timestamps.stream().max(Long::compareTo).get() + CLEANUP_DELAY.toMillis();
        Log.i(TAG, "setting cleanup timer for " + cleanupTime);
        mAlarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME, cleanupTime, getCleanupIntent());
    }

    private void cancelCleanupTimer() {
        mAlarmManager.cancel(getCleanupIntent());
    }

    private PendingIntent getCleanupIntent() {
        return PendingIntent.getBroadcast(
                mContext,
                CLEANUP_INTENT_REQUEST_CODE,
                new Intent(UpdateResuspendStateBroadcastReceiver.ACTION_CLEANUP)
                        .setClass(mContext, UpdateResuspendStateBroadcastReceiver.class)
                        .setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        .setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // Stock no longer posts a notification here; it only records the warning as posted.
    private void showWorkProfileWarningNotification(long pauseTimestamp) {
        Log.i(TAG, "showWorkProfileWarningNotification");
        mInternalStateController.setWorkProfileWarningPosted();
    }

    private void showWarningNotification(String packageName) {
        Log.i(TAG, "showWarningNotification " + packageName);
        Optional<Long> resuspendTimestamp = getResuspendTimestamp(packageName);
        if (!resuspendTimestamp.isPresent()) {
            Log.w(TAG, "Bad state trying to show warning for " + packageName);
            cancelResuspend(packageName);
            return;
        }
        mInternalStateController.setResuspendWarningNotificationPosted(packageName);
        startUpdateStateTimer(packageName, resuspendTimestamp.get());
        mAppColorManager.setAppGrayscale(packageName);
    }

    /** Returns the elapsed realtime to resuspend at, aligned to the start of a wall minute. */
    long calculateResuspendTime(Duration duration) {
        return mClock.elapsedRealtime()
                - (mClock.currentTimeMillis() % ONE_MINUTE_MS)
                + duration.toMillis()
                + ONE_SECOND_MS;
    }

    public Optional<Long> getResuspendTimestamp(String packageName) {
        try {
            if (mContext.getPackageManager().isPackageSuspended(packageName)) {
                Log.w(TAG, "App already suspended.");
                return Optional.empty();
            }
            return mInternalStateController.getResuspendTimestamp(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "App not found. Ignoring.");
            return Optional.empty();
        }
    }

    Optional<Long> getWorkProfilePauseTimestamp() {
        return mInternalStateController.getWorkProfilePauseTimestamp();
    }

    private static int getUpdateStateIntentRequestCode(String packageName) {
        return (packageName + ".resuspend").hashCode();
    }

    @VisibleForTesting
    public void overrideWorkProfileManager(WorkProfileManager workProfileManager) {
        mWorkProfileManager = workProfileManager;
    }

    @VisibleForTesting
    void overrideClock(Clock clock) {
        mClock = clock;
    }

    @VisibleForTesting
    public void resetForTest() {
        cancelAllResuspensionTimers();
        cancelWorkProfilePause();
        sInstance = null;
        AppListFetcher.resetForTest();
    }

    public void dump(PrintWriter pw) {
        pw.println("\tSuspend packages:");
        ImmutableSet<String> packageNames = mAppListFetcher.getAllPackagesToSuspend();
        if (packageNames == null || packageNames.isEmpty()) {
            return;
        }
        for (String packageName : packageNames) {
            try {
                if (mPackageManager.isPackageSuspended(packageName)) {
                    pw.println(packageName);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // Uninstalled since the list was computed.
            }
        }
    }

    private static String[] getInstalledApplications(Context context) {
        return context.getPackageManager().getInstalledApplications(0).stream()
                .map(info -> info.packageName)
                .toArray(String[]::new);
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
}
