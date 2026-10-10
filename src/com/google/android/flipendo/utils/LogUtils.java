package com.google.android.flipendo.utils;

import android.util.Log;

import com.google.common.base.Splitter;

public abstract class LogUtils {
    private static final int MAX_LOG_LENGTH = 500;

    public static void logLongMessage(int priority, String tag, String message) {
        if (message == null) {
            Log.println(priority, tag, "null");
            return;
        }
        if (message.length() < MAX_LOG_LENGTH) {
            Log.println(priority, tag, message);
            return;
        }
        for (String chunk : Splitter.fixedLength(MAX_LOG_LENGTH).split(message)) {
            Log.println(priority, tag, chunk);
        }
    }
}
