package com.google.android.flipendo.bugreport;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.google.android.flipendo.HistoricalLog;
import com.google.android.flipendo.HistoricalLogEntry;
import com.google.android.flipendo.InternalState;
import com.google.android.flipendo.R;
import com.google.android.flipendo.clock.Clock;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public abstract class HistoricalLogUtil {
    private static final String TAG = "HistoricalLogUtil";

    public static final String DEFAULT_FILE_NAME = "historical_logs";
    @VisibleForTesting static final int MAX_ENTRIES = 20;

    private static final String DEVICE_ENCRYPTED_FILE_NAME = "device_log";
    private static final String DATE_FORMAT = "MMM dd,yyyy HH:mm:ss";

    private static final String EVENT_SUSPEND_APPS = "suspend_apps";
    private static final String EVENT_UNSUSPEND_APPS = "unsuspend_apps";
    private static final String EVENT_DIM_WALLPAPER = "dim_wallpaper_event";
    private static final String EVENT_DIM_WALLPAPER_RESET = "dim_wallpaper_reset_event";

    private static Clock sClock = Clock.SYSTEM;

    public static void writeLog(Context context, InternalState state, String source) {
        if (!context.getSystemService(UserManager.class).isUserUnlocked()) {
            writeDirectBootLog(context, state, source);
            return;
        }
        writeLog(
                context,
                HistoricalLogEntry.newBuilder()
                        .setState(state)
                        .setTimestamp(sClock.currentTimeMillis())
                        .setPowerSaveMode(isPowerSaveMode(context))
                        .setTimestampLocal(getCurrentDateTime())
                        .setSource(source)
                        .build());
    }

    public static void logIntent(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if (!context.getSystemService(UserManager.class).isUserUnlocked()) {
            logDirectBootIntent(context, intent);
            return;
        }
        getSharedPreferences(context)
                .edit()
                .putString(intent.getAction(), getCurrentDateTime())
                .commit();
    }

    static void logDirectBootIntent(Context context, Intent intent) {
        getDeviceEncryptedSharedPreferences(context)
                .edit()
                .putString(intent.getAction(), getCurrentDateTime())
                .commit();
    }

    public static void logSuspendState(Context context, boolean suspended) {
        try {
            getSharedPreferences(context)
                    .edit()
                    .putString(
                            suspended ? EVENT_SUSPEND_APPS : EVENT_UNSUSPEND_APPS,
                            getCurrentDateTime())
                    .commit();
        } catch (Exception e) {
            Log.e(TAG, "logSuspendState failed", e);
        }
    }

    public static void logWallpaperDimEvent(Context context, boolean dimmed) {
        try {
            getSharedPreferences(context)
                    .edit()
                    .putString(
                            dimmed ? EVENT_DIM_WALLPAPER : EVENT_DIM_WALLPAPER_RESET,
                            getCurrentDateTime())
                    .commit();
        } catch (Exception e) {
            Log.e(TAG, "logWallpaperDimState failed", e);
        }
    }

    private static void writeLog(Context context, HistoricalLogEntry entry) {
        SharedPreferences prefs = getSharedPreferences(context);
        String keyLogs = getKeyLogs(context);
        HistoricalLog log = parseLogFromString(prefs.getString(keyLogs, ""));
        HistoricalLog.Builder builder = log.toBuilder();
        if (log.getLogEntryCount() >= MAX_ENTRIES) {
            builder.removeLogEntry(0);
        }
        builder.addLogEntry(entry);
        prefs.edit()
                .putString(
                        keyLogs,
                        Base64.encodeToString(builder.build().toByteArray(), Base64.DEFAULT))
                .apply();
    }

    public static void writeDirectBootLog(Context context, InternalState state, String source) {
        HistoricalLogEntry entry =
                HistoricalLogEntry.newBuilder()
                        .setState(state)
                        .setDirectBoot(true)
                        .setTimestamp(sClock.currentTimeMillis())
                        .setPowerSaveMode(isPowerSaveMode(context))
                        .setTimestampLocal(getCurrentDateTime())
                        .setSource(source)
                        .build();
        getDeviceEncryptedSharedPreferences(context)
                .edit()
                .putString(
                        getDeviceEncryptedKeyLog(context),
                        Base64.encodeToString(entry.toByteArray(), Base64.DEFAULT))
                .apply();
    }

    /** Moves the logs written before the user unlocked into credential-encrypted storage. */
    public static void moveDirectBootLog(Context context) {
        SharedPreferences dePrefs = getDeviceEncryptedSharedPreferences(context);
        String powerSaveModeChanged =
                dePrefs.getString(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED, "");
        if (!TextUtils.isEmpty(powerSaveModeChanged)) {
            getSharedPreferences(context)
                    .edit()
                    .putString(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED, powerSaveModeChanged)
                    .commit();
        }
        HistoricalLogEntry entry =
                parseLogEntryFromString(dePrefs.getString(getDeviceEncryptedKeyLog(context), ""));
        if (!entry.hasState()) {
            Log.d(TAG, "No direct boot log");
            return;
        }
        writeLog(context, entry);
        dePrefs.edit().clear().apply();
    }

    private static String getDeviceEncryptedKeyLog(Context context) {
        return context.getString(R.string.shared_pref_historical_log_directboot_key);
    }

    private static String getKeyLogs(Context context) {
        return context.getString(R.string.shared_pref_historical_log_logs_key);
    }

    static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(DEFAULT_FILE_NAME, Context.MODE_PRIVATE);
    }

    public static SharedPreferences getDeviceEncryptedSharedPreferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(DEVICE_ENCRYPTED_FILE_NAME, Context.MODE_PRIVATE);
    }

    private static HistoricalLogEntry parseLogEntryFromString(String serialized) {
        return parseProtoFromString(serialized, HistoricalLogEntry.getDefaultInstance());
    }

    private static HistoricalLog parseLogFromString(String serialized) {
        return parseProtoFromString(serialized, HistoricalLog.getDefaultInstance());
    }

    private static boolean isPowerSaveMode(Context context) {
        return context.getSystemService(PowerManager.class).isPowerSaveMode();
    }

    private static String getCurrentDateTime() {
        return new SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
                .format(new Date(System.currentTimeMillis()));
    }

    @SuppressWarnings("unchecked")
    private static <T extends MessageLite> T parseProtoFromString(
            String serialized, T defaultInstance) {
        if (serialized.isEmpty()) {
            return (T) defaultInstance.getDefaultInstanceForType();
        }
        try {
            return (T)
                    defaultInstance
                            .getParserForType()
                            .parseFrom(Base64.decode(serialized, Base64.DEFAULT));
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "Failed to deserialize proto class", e);
            return (T) defaultInstance.getDefaultInstanceForType();
        }
    }

    static void printHistoricalLog(Context context, PrintWriter pw) {
        pw.println("\tPast:");
        SharedPreferences prefs = getSharedPreferences(context);
        List<HistoricalLogEntry> entries =
                parseLogFromString(prefs.getString(getKeyLogs(context), "")).getLogEntryList();
        if (entries.isEmpty()) {
            pw.println("   No past logs.");
        } else {
            entries.forEach(entry -> pw.println(toString(entry)));
        }
        pw.println("\tIntents:");
        printEvent(prefs, pw, Intent.ACTION_BOOT_COMPLETED);
        printEvent(prefs, pw, PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        pw.println("\tWallpaper dim:");
        printEvent(prefs, pw, EVENT_DIM_WALLPAPER);
        printEvent(prefs, pw, EVENT_DIM_WALLPAPER_RESET);
        pw.println("\tSuspend state:");
        printEvent(prefs, pw, EVENT_SUSPEND_APPS);
        printEvent(prefs, pw, EVENT_UNSUSPEND_APPS);
    }

    private static String toString(HistoricalLogEntry entry) {
        return String.format(
                "%s\t%s\t%s\tsaverMode:%b\tdirectBoot:%b",
                entry.getTimestampLocal(),
                entry.getSource(),
                entry.getState(),
                entry.getPowerSaveMode(),
                entry.getDirectBoot());
    }

    private static void printEvent(SharedPreferences prefs, PrintWriter pw, String event) {
        pw.println(String.format("%s %s", event, prefs.getString(event, "")));
    }

    @VisibleForTesting
    static void overrideClock(Clock clock) {
        sClock = clock;
    }
}
