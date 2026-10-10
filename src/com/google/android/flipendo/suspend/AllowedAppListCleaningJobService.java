package com.google.android.flipendo.suspend;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

/** Drops essential apps that are no longer installed. */
public final class AllowedAppListCleaningJobService extends JobService {
    private static final String TAG = "CleaningJobSvc";

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "cleaning allowed app list");
        AllowedAppListManager.getInstance(getApplicationContext())
                .removePackagesNotPresentOnDevice();
        return false;
    }
}
