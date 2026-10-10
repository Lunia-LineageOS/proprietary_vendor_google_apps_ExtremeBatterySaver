package com.google.android.flipendo.utils;

import android.os.Looper;

public abstract class ThreadUtils {
    private static Thread sMainThread;

    public static boolean isMainThread() {
        if (sMainThread == null) {
            sMainThread = Looper.getMainLooper().getThread();
        }
        return Thread.currentThread() == sMainThread;
    }
}
