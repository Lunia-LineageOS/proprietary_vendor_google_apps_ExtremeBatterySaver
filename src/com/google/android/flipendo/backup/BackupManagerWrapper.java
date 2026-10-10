package com.google.android.flipendo.backup;

import android.app.backup.BackupManager;
import android.content.Context;

import androidx.annotation.VisibleForTesting;

public final class BackupManagerWrapper {
    private static BackupManagerWrapper sSingleton;

    private BackupManager mBackupManager;

    public static synchronized BackupManagerWrapper getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new BackupManagerWrapper(context.getApplicationContext());
        }
        return sSingleton;
    }

    private BackupManagerWrapper(Context context) {
        mBackupManager = new BackupManager(context);
    }

    public void dataChanged() {
        mBackupManager.dataChanged();
    }

    @VisibleForTesting
    public void setBackupManagerForTest(BackupManager backupManager) {
        mBackupManager = backupManager;
    }
}
