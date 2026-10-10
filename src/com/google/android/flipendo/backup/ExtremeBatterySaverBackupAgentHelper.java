package com.google.android.flipendo.backup;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;

/** Backs up the savings mode settings and the essential apps list. */
public final class ExtremeBatterySaverBackupAgentHelper extends BackupAgentHelper {
    private static final String PREFS_BACKUP_KEY_ALLOW_LIST = "pref_helper_flipendo_allow_list";
    private static final String PREFS_BACKUP_KEY_SETTINGS = "pref_helper_flipendo_settings";

    @Override
    public void onCreate() {
        Context deContext = createDeviceProtectedStorageContext();
        addHelper(
                PREFS_BACKUP_KEY_SETTINGS,
                new SharedPreferencesBackupHelper(deContext, "settings_preferences"));
        addHelper(
                PREFS_BACKUP_KEY_ALLOW_LIST,
                new SharedPreferencesBackupHelper(deContext, "allowed_app_list_preferences"));
    }
}
