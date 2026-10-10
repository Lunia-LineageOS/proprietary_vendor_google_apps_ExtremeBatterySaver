package com.google.android.flipendo.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Keeps a receiver alive while its work runs, handing the work over to {@link
 * FutureForegroundService} if it takes too long.
 */
public final class ReceiverHandoverDelegate {
    private static final String TAG = "ReceiverDelegate";

    @VisibleForTesting static final Duration DELAY_TO_STARTING_SERVICE = Duration.ofSeconds(6);

    private BroadcastReceiver.PendingResult mPendingResult;
    private ScheduledFuture<?> mScheduledFuture;
    private final ListenableFuture<?> mWorkToDo;
    private final Object mPendingResultLock = new Object();

    @VisibleForTesting
    public final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

    public ReceiverHandoverDelegate(ListenableFuture<?> workToDo) {
        mWorkToDo = workToDo;
    }

    public void onReceive(BroadcastReceiver receiver, Context context) {
        synchronized (mPendingResultLock) {
            mPendingResult = receiver.goAsync();
        }
        Futures.addCallback(
                mWorkToDo,
                new FutureCallback<Object>() {
                    @Override
                    public void onSuccess(Object result) {
                        Log.d(TAG, "Future succeeded in receiver");
                        cancelScheduledFuture();
                        finishReceiverAsync();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "Future failed in receiver");
                        throw new RuntimeException(t);
                    }
                },
                MoreExecutors.directExecutor());
        mScheduledFuture =
                mExecutor.schedule(
                        () -> {
                            if (mWorkToDo.isDone() || mWorkToDo.isCancelled()) {
                                Log.d(TAG, "Future already completed, not handing over.");
                                finishReceiverAsync();
                            } else {
                                Log.d(TAG, "Future in progress, handing over to service.");
                                FutureForegroundService.handOverFuture(context, mWorkToDo);
                                finishReceiverAsync();
                            }
                        },
                        DELAY_TO_STARTING_SERVICE.getSeconds(),
                        TimeUnit.SECONDS);
    }

    private void finishReceiverAsync() {
        synchronized (mPendingResultLock) {
            if (mPendingResult == null) {
                return;
            }
            mPendingResult.finish();
            mPendingResult = null;
        }
    }

    private void cancelScheduledFuture() {
        if (mScheduledFuture == null
                || mScheduledFuture.isCancelled()
                || mScheduledFuture.isDone()) {
            return;
        }
        mScheduledFuture.cancel(true);
    }
}
