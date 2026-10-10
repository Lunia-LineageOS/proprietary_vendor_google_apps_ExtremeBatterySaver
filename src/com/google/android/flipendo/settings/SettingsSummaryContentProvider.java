package com.google.android.flipendo.settings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;

/** Provides the summary of the injected Extreme Battery Saver settings entry. */
public final class SettingsSummaryContentProvider extends ContentProvider {
    @VisibleForTesting static final String GET_SUMMARY_METHOD_NAME = "getSummary";

    @VisibleForTesting
    static final String META_DATA_PREFERENCE_SUMMARY = "com.android.settings.summary";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!GET_SUMMARY_METHOD_NAME.equals(method)) {
            return null;
        }
        Bundle bundle = new Bundle();
        bundle.putString(
                META_DATA_PREFERENCE_SUMMARY,
                SettingsController.getInstance(getContext()).getModeTitle());
        return bundle;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
