package com.google.android.flipendo.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public abstract class BatteryUsageUtils {
    private static final String TAG = "BatteryUsageUtils";

    static final Uri BATTERY_USAGE_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority("com.google.android.settings.fuelgauge.provider")
                    .appendPath("BatteryUsageState")
                    .build();

    public static List<BatteryUsageData> getAllBatteryUsageData(Context context) {
        try (Cursor cursor =
                context.getContentResolver().query(BATTERY_USAGE_URI, null, null, null, null)) {
            if (cursor == null || cursor.getCount() == 0) {
                Log.w(TAG, "cursor is null or not data found");
                return null;
            }
            List<BatteryUsageData> usageData = new ArrayList<>();
            while (cursor.moveToNext()) {
                insertData(usageData, cursor);
            }
            Log.d(TAG, "getAllBatteryUsageData():" + usageData.size());
            return usageData;
        }
    }

    private static void insertData(List<BatteryUsageData> usageData, Cursor cursor) {
        usageData.add(
                new BatteryUsageData(
                        cursor.getLong(cursor.getColumnIndex("userId")),
                        cursor.getString(cursor.getColumnIndex("packageName")),
                        cursor.getDouble(cursor.getColumnIndex("percentage")),
                        cursor.getLong(cursor.getColumnIndex("foregroundElapsedTime")),
                        cursor.getLong(cursor.getColumnIndex("backgroundElapsedTime"))));
    }
}
