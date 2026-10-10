package com.google.android.flipendo.bugreport;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.UserManager;

import com.google.android.flipendo.BatterySaverManager;
import com.google.android.flipendo.suspend.SuspensionManager;
import com.google.android.flipendo.workprofile.WorkProfileManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Dumps the Flipendo state into bug reports. */
public final class BugReportContentProvider extends ContentProvider {
    private static final String TAG = "BugReportCP";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        Context context = getContext();
        if (context == null) {
            pw.println("Failed to dump Flipendo state: null context.");
            return;
        }
        if (WorkProfileManager.isWorkProfile(context)) {
            return;
        }
        if (!context.getSystemService(UserManager.class).isUserUnlocked()) {
            pw.println("In direct-boot mode, skipping...");
            return;
        }
        pw.println("Flipendo state...");
        pw.println("\tCurrent:");
        BatterySaverManager.getInstance(context).ifPresent(manager -> manager.dump(pw));
        HistoricalLogUtil.printHistoricalLog(context, pw);
        SuspensionManager.getInstance(context).dump(pw);
        pw.println();
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        throw new UnsupportedOperationException("Unsupported!");
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException("Unsupported!");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Unsupported!");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Unsupported!");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Unsupported!");
    }
}
