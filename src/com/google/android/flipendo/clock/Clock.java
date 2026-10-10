package com.google.android.flipendo.clock;

import android.os.SystemClock;

public interface Clock {
    Clock SYSTEM =
            new Clock() {
                @Override
                public long currentTimeMillis() {
                    return System.currentTimeMillis();
                }

                @Override
                public long elapsedRealtime() {
                    return SystemClock.elapsedRealtime();
                }
            };

    long currentTimeMillis();

    long elapsedRealtime();
}
