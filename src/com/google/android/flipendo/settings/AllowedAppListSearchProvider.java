package com.google.android.flipendo.settings;

import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_ACTION;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_TARGET_CLASS;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_KEY;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_KEYWORDS;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_TITLE;
import static android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS;
import static android.provider.SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS;
import static android.provider.SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.SearchIndexablesProvider;

import androidx.annotation.VisibleForTesting;

import com.google.android.flipendo.R;

/** Makes the essential apps screen searchable in Settings. */
public class AllowedAppListSearchProvider extends SearchIndexablesProvider {
    @VisibleForTesting
    static String INDEX_RAW_INTENT_ACTION = "android.settings.batterysaver.flipendo";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryXmlResources(String[] projection) {
        return new MatrixCursor(INDEXABLES_XML_RES_COLUMNS);
    }

    @Override
    public Cursor queryRawData(String[] projection) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        Object[] row = new Object[INDEXABLES_RAW_COLUMNS.length];
        row[COLUMN_INDEX_RAW_KEY] = context.getString(R.string.allowed_app_list_indexing_key);
        row[COLUMN_INDEX_RAW_TITLE] = context.getString(R.string.extreme_battery_saver_label);
        row[COLUMN_INDEX_RAW_INTENT_ACTION] = INDEX_RAW_INTENT_ACTION;
        row[COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE] = context.getPackageName();
        row[COLUMN_INDEX_RAW_INTENT_TARGET_CLASS] = AllowedAppListSelectionActivity.class.getName();
        row[COLUMN_INDEX_RAW_KEYWORDS] =
                context.getString(R.string.allowed_app_list_search_keywords);
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_RAW_COLUMNS);
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public Cursor queryNonIndexableKeys(String[] projection) {
        return new MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS);
    }
}
