package com.google.android.flipendo;

import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.TetheringManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.android.settingslib.fuelgauge.BatteryUtils;

import com.google.android.flipendo.api.FlipendoStateApiContract;
import com.google.android.flipendo.bugreport.HistoricalLogUtil;
import com.google.android.flipendo.levers.SettingsLevers;
import com.google.android.flipendo.logging.WestworldLogger;
import com.google.android.flipendo.notification.TurnWorkProfileBackOnNotification;
import com.google.android.flipendo.settings.SettingsController;
import com.google.android.flipendo.settings.SettingsInjector;
import com.google.android.flipendo.suspend.SuspensionManager;
import com.google.android.flipendo.utils.DeviceConfigUtils;
import com.google.android.flipendo.utils.ThreadUtils;
import com.google.android.flipendo.workprofile.WorkProfileManager;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BatterySaverManager {
    private static final String TAG = "BatterySaverManager";

    // Modes accepted by FlipendoStateContentProvider's update_flipendo_mode_method.
    private static final int FLIPENDO_MODE_MILD = 0;
    private static final int FLIPENDO_MODE_AGGRESSIVE = 1;
    private static final int FLIPENDO_MODE_ASK = 2;

    private static final Executor sSequentialExecutor =
            MoreExecutors.newSequentialExecutor(Executors.newFixedThreadPool(1));
    private static Optional<BatterySaverManager> sSingletonOptional;

    private final BatterySaverPolicyManager mBatterySaverPolicyManager;
    private final Context mContext;
    private final AtomicBoolean mForceEnableFlipendo = new AtomicBoolean(false);
    private final InternalStateController mInternalStateController;
    private final NotificationManager mNotificationManager;
    private final PowerManager mPowerManager;
    private final SettingsController mSettingsController;
    private final SettingsLevers mSettingsLevers;
    private final SuspensionManager mSuspensionManager;
    private final TetheringManager mTetheringManager;

    /** Returns the manager, or empty when running in an additional (non-main) profile. */
    public static synchronized Optional<BatterySaverManager> getInstance(Context context) {
        if (sSingletonOptional == null) {
            Context appContext = context.getApplicationContext();
            sSingletonOptional =
                    BatteryUtils.isAdditionalProfile(appContext)
                            ? Optional.empty()
                            : Optional.of(new BatterySaverManager(appContext));
        }
        return sSingletonOptional;
    }

    BatterySaverManager(Context context) {
        mPowerManager = context.getSystemService(PowerManager.class);
        mInternalStateController = InternalStateController.getInstance(context);
        mSettingsController = SettingsController.getInstance(context);
        mContext = context;
        mNotificationManager = context.getSystemService(NotificationManager.class);
        mSuspensionManager = SuspensionManager.getInstance(context);
        mSettingsLevers = SettingsLevers.getInstance(context);
        mTetheringManager = context.getSystemService(TetheringManager.class);
        mBatterySaverPolicyManager = BatterySaverPolicyManager.getInstance(context);
    }

    public ListenableFuture<Void> updateState(String source) {
        return updateState(source, false);
    }

    public ListenableFuture<Void> updateState(String source, boolean forceUpdate) {
        Log.v(
                TAG,
                "updateState() from "
                        + source
                        + " forceEnableFlipendo:"
                        + mForceEnableFlipendo.get()
                        + " forceUpdate:"
                        + forceUpdate);
        long startTime = SystemClock.elapsedRealtime();
        if (!ThreadUtils.isMainThread()) {
            Log.w(
                    TAG,
                    "updateState not called from main thread. Current thread: "
                            + Thread.currentThread().getName());
        }
        Preconditions.checkState(!WorkProfileManager.isWorkProfile(mContext));
        return Futures.submitAsync(
                () -> {
                    if (mSettingsController.isTerminatorModeEnabled()) {
                        return Futures.immediateFuture(null);
                    }
                    if (mForceEnableFlipendo.get()) {
                        enable(startTime, source);
                        return Futures.immediateFuture(null);
                    }
                    boolean isPowerSaveMode = mPowerManager.isPowerSaveMode();
                    if (!SettingsInjector.isSettingsComponentEnabled(mContext)) {
                        Log.v(TAG, "Flipendo settings injection is disabled");
                        disable(startTime, isPowerSaveMode, source, forceUpdate);
                        return Futures.immediateFuture(null);
                    }
                    Log.v(TAG, "Power save mode enabled = " + isPowerSaveMode);
                    Log.v(TAG, "Settings mode = " + mSettingsController.getSavedSavingsMode());
                    if (!isPowerSaveMode) {
                        disable(startTime, isPowerSaveMode, source, forceUpdate);
                        return Futures.immediateFuture(null);
                    }
                    switch (mSettingsController.getSavedSavingsMode()) {
                        case ASK:
                            if (!mInternalStateController.isFlipendoEnabled()) {
                                BatterySaverUtils.sendUpdateIntent(
                                        mContext, isPowerSaveMode, false);
                            }
                            break;
                        case MILD:
                            disable(startTime, isPowerSaveMode, source, forceUpdate);
                            break;
                        case AGGRESSIVE:
                            enable(startTime, source);
                            break;
                    }
                    return Futures.immediateFuture(null);
                },
                sSequentialExecutor);
    }

    /** Re-applies the regular app suspension after leaving terminator mode. */
    public void recoverFlipendo(String source) {
        long startTime = SystemClock.elapsedRealtime();
        sSequentialExecutor.execute(
                () -> {
                    mSuspensionManager.recoverSuspendAppsFromTerminatorMode();
                    HistoricalLogUtil.writeLog(mContext, InternalState.ENABLED, source);
                    WestworldLogger.logFlipendoEnabledAtom(
                            true, WestworldLogger.FlipendoEnabledType.NORMAL);
                    notifyFlipendoStateChanged();
                    BatterySaverUtils.sendUpdateIntent(mContext, true, true);
                    endTimerAndLog(true, startTime);
                });
    }

    public void dump(PrintWriter pw) {
        if (!SettingsInjector.isSettingsComponentEnabled(mContext)) {
            pw.println("    Flipendo settings injection is disabled");
            return;
        }
        pw.println("    Power save mode enabled = " + mPowerManager.isPowerSaveMode());
        pw.println("    Settings mode = " + mSettingsController.getSavedSavingsMode());
        pw.println("    Flipendo enabled = " + mInternalStateController.isFlipendoEnabled());
    }

    public void setForceEnableFlipendo(boolean forceEnable) {
        mForceEnableFlipendo.set(forceEnable);
    }

    public void updateFlipendoMode(int mode) {
        SettingsController.SavingsMode savingsMode;
        switch (mode) {
            case FLIPENDO_MODE_MILD:
                savingsMode = SettingsController.SavingsMode.MILD;
                break;
            case FLIPENDO_MODE_AGGRESSIVE:
                savingsMode = SettingsController.SavingsMode.AGGRESSIVE;
                break;
            case FLIPENDO_MODE_ASK:
                savingsMode = SettingsController.SavingsMode.ASK;
                break;
            default:
                Log.v(TAG, "Unsupported mode: " + mode);
                return;
        }
        mSettingsController.setSavingsMode(savingsMode);
        updateState("FlipendoStateCP_updateMode");
    }

    public void removeAskSavingModeIfNeeded() {
        if (mSettingsController.getSavedSavingsMode().equals(SettingsController.SavingsMode.ASK)) {
            mSettingsController.setSavingsMode(SettingsController.SavingsMode.MILD);
        }
    }

    void enable(long startTime, String source) {
        Log.v(TAG, "Enable called from " + source);
        TurnWorkProfileBackOnNotification.hide(mContext);
        if (mSettingsController.shouldExecuteTerminator()) {
            enableFlipendo(startTime, source, true);
            mSettingsController.setEnableTerminatorRequest(false);
            mSettingsController.setTerminatorModeEnabled(true);
        } else if (mInternalStateController.isFlipendoEnabled()) {
            Log.v(TAG, "Flipendo is enabled no action needed");
            mForceEnableFlipendo.set(false);
        } else {
            enableFlipendo(startTime, source, false);
        }
    }

    private void enableFlipendo(long startTime, String source, boolean terminatorMode) {
        Log.v(TAG, "enableFlipendo() with terminatorMode: " + terminatorMode);
        mInternalStateController.setState(InternalState.ENABLED);
        if (terminatorMode) {
            mSuspensionManager.suspendTerminatorModeApps();
        } else {
            mSuspensionManager.suspendAllApps();
        }
        mSuspensionManager.pauseWorkProfile();
        mSettingsLevers.enableLowPower();
        if (DeviceConfigUtils.getIsHotspotLeverEnabled()) {
            turnOffTethering();
        }
        HistoricalLogUtil.writeLog(mContext, InternalState.ENABLED, source);
        WestworldLogger.logFlipendoEnabledAtom(true, WestworldLogger.FlipendoEnabledType.NORMAL);
        mBatterySaverPolicyManager.overrideBatterySaverPolicyForEBS();
        notifyFlipendoStateChanged();
        BatterySaverUtils.sendUpdateIntent(mContext, true, true);
        endTimerAndLog(true, startTime);
    }

    private void turnOffTethering() {
        mTetheringManager.stopAllTethering();
    }

    void disable(long startTime, boolean isPowerSaveMode, String source, boolean forceUpdate) {
        Log.v(TAG, "Disable called from " + source);
        if (isPowerSaveMode) {
            mBatterySaverPolicyManager.overrideBatterySaverPolicy();
        } else {
            mBatterySaverPolicyManager.restoreBatterySaverPolicy();
        }
        BatterySaverUtils.sendUpdateIntent(mContext, isPowerSaveMode, false);
        if (!mInternalStateController.isFlipendoEnabled() && !forceUpdate) {
            Log.v(TAG, "Flipendo is disabled no action needed");
            return;
        }
        Log.v(TAG, "Flipendo will be disabled");
        mSuspensionManager.unsuspendAllApps(mContext);
        for (StatusBarNotification notification : mNotificationManager.getActiveNotifications()) {
            Log.i(TAG, "Found notification: " + notification.getId());
            mNotificationManager.cancel(notification.getId());
        }
        mSuspensionManager.restoreWorkProfile();
        mSettingsLevers.disableLowPower();
        mInternalStateController.setState(InternalState.DISABLED);
        mInternalStateController.clearAllResuspensionStorage();
        notifyFlipendoStateChanged();
        HistoricalLogUtil.writeLog(mContext, InternalState.DISABLED, source);
        WestworldLogger.logFlipendoEnabledAtom(false, WestworldLogger.FlipendoEnabledType.NORMAL);
        endTimerAndLog(false, startTime);
    }

    private void notifyFlipendoStateChanged() {
        mContext.getContentResolver()
                .notifyChange(
                        FlipendoStateApiContract.IS_FLIPENDO_ENABLED_OBSERVABLE_URI,
                        (ContentObserver) null,
                        ContentResolver.NOTIFY_NO_DELAY);
    }

    private static void endTimerAndLog(boolean enabled, long startTime) {
        long elapsedTime = SystemClock.elapsedRealtime() - startTime;
        WestworldLogger.logFlipendoUpdateTimeElapsed(enabled, elapsedTime);
        Log.v(
                TAG,
                String.format(
                        Locale.ROOT,
                        "Flipendo state change to %s has finished in %d ms",
                        enabled ? "enable" : "disable",
                        elapsedTime));
    }
}
