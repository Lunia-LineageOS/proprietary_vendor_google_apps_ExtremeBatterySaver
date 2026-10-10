package com.google.android.flipendo.receiver;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.google.pixel.vendor.PixelAtoms;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.settingslib.fuelgauge.BatteryUtils;

import com.google.android.flipendo.R;
import com.google.android.flipendo.logging.WestworldLogger;
import com.google.android.flipendo.notification.AppChannels;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.HashSet;
import java.util.Set;

/** Foreground service that keeps the process alive until handed over futures complete. */
public final class FutureForegroundService extends Service {
    private static final String TAG = "FutureFgSvc";

    private static final String ACTION_START_SERVICE = "start_service";
    private static final String ACTION_STOP_SERVICE = "stop_service";

    private static final Object sFutureListLock = new Object();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(
                R.id.service_notification_id,
                buildForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        WestworldLogger.logFlipendoUIElementAction(
                PixelAtoms.FlipendoUIElementAction.UIElement.NOTIFICATION_SETTINGS_UPDATE,
                PixelAtoms.FlipendoUIElementAction.Action.SHOWN);
        String action = intent.getAction();
        if (ACTION_STOP_SERVICE.equals(action)) {
            Log.d(TAG, "Stopping service...");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START_SERVICE.equals(action)) {
            Log.w(TAG, "No action (or unknown) passed in. Assume START_SERVICE intended");
        }
        Log.d(TAG, "Starting service...");
        if (BatteryUtils.isAdditionalProfile(getApplicationContext())) {
            Log.w(TAG, "Not running service on an additional profile.");
            stopSelf();
            return START_NOT_STICKY;
        }
        synchronized (sFutureListLock) {
            if (FutureHolder.isHolderEmpty()) {
                Log.w(TAG, "Service started without a future to hold");
                stopSelf();
            }
            return START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }

    static void startService(Context context) {
        Intent intent = new Intent(context, FutureForegroundService.class);
        intent.setAction(ACTION_START_SERVICE);
        context.startForegroundService(intent);
    }

    private static void stopService(Context context) {
        Intent intent = new Intent(context, FutureForegroundService.class);
        intent.setAction(ACTION_STOP_SERVICE);
        context.startForegroundService(intent);
    }

    public static void handOverFuture(Context context, ListenableFuture<?> future) {
        FutureHolder.holdFuture(future);
        Futures.addCallback(
                future,
                new FutureCallback<Object>() {
                    @Override
                    public void onSuccess(Object result) {
                        synchronized (sFutureListLock) {
                            Log.d(TAG, "Future succeeded in service");
                            FutureHolder.removeCompletedFuture(future);
                            if (FutureHolder.isHolderEmpty()) {
                                Log.d(TAG, "No more futures being held, stopping service");
                                stopService(context);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "Future failed in service, will stop and throw");
                        stopService(context);
                        throw new RuntimeException(t);
                    }
                },
                MoreExecutors.directExecutor());
        if (!future.isCancelled() && !future.isDone()) {
            Log.d(TAG, "Starting service to hold future");
            startService(context);
        } else {
            Log.d(TAG, "Future was already cancelled or done, not starting service.");
        }
    }

    private Notification buildForegroundNotification() {
        return new Notification.Builder(
                        this, AppChannels.SYSTEM.createNotificationChannel(getApplicationContext()))
                .setSmallIcon(R.drawable.ic_battery_saver)
                .setContentTitle(getString(R.string.service_notification_title))
                .setProgress(0, 0, true)
                .build();
    }

    enum FutureHolder {
        INSTANCE;

        private Set<ListenableFuture<?>> mFutures;

        static boolean isHolderEmpty() {
            synchronized (sFutureListLock) {
                return INSTANCE.mFutures == null || INSTANCE.mFutures.isEmpty();
            }
        }

        static void holdFuture(ListenableFuture<?> future) {
            synchronized (sFutureListLock) {
                if (INSTANCE.mFutures == null) {
                    INSTANCE.mFutures = new HashSet<>();
                }
                INSTANCE.mFutures.add(future);
            }
        }

        public static void removeCompletedFuture(ListenableFuture<?> future) {
            synchronized (sFutureListLock) {
                if (INSTANCE.mFutures == null) {
                    return;
                }
                INSTANCE.mFutures.remove(future);
                if (INSTANCE.mFutures.isEmpty()) {
                    INSTANCE.mFutures = null;
                }
            }
        }

        @VisibleForTesting
        static void resetFutures() {
            synchronized (sFutureListLock) {
                INSTANCE.mFutures = null;
            }
        }
    }
}
