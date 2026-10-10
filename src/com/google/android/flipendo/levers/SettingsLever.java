package com.google.android.flipendo.levers;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** A setting that is forced to a low power value while Flipendo is on and restored after. */
final class SettingsLever {
    private static final String TAG = "SettingsLever";

    static final String ORIGINAL_SUFFIX = "_original";

    private final ContentResolver mContentResolver;
    private final Supplier<String> mLowPowerStateSupplier;
    private final Namespace mNamespace;
    private final OriginalLocation mOriginalSettingLocation;
    private final String mSetting;
    ContentObserver mSettingModificationObserver = null;
    private final SharedPreferences mSharedPreferences;
    private final Predicate<String> mShouldEnableLever;

    enum Namespace {
        GLOBAL,
        SECURE,
        SYSTEM
    }

    enum OriginalLocation {
        DEVICE_SETTINGS,
        FLIPENDO_SETTINGS
    }

    SettingsLever(
            Context context,
            String setting,
            Predicate<String> shouldEnableLever,
            Supplier<String> lowPowerStateSupplier,
            Namespace namespace,
            OriginalLocation originalSettingLocation,
            SharedPreferences sharedPreferences) {
        mContentResolver = context.getContentResolver();
        mSetting = setting;
        mShouldEnableLever = shouldEnableLever;
        mLowPowerStateSupplier = lowPowerStateSupplier;
        mNamespace = namespace;
        mOriginalSettingLocation = originalSettingLocation;
        mSharedPreferences = sharedPreferences;
    }

    void enableLowPower() {
        if (mNamespace == Namespace.SYSTEM && !Settings.System.PUBLIC_SETTINGS.contains(mSetting)) {
            Log.e(TAG, String.format("Lever %s is not writable, skipping.", mSetting));
            return;
        }
        String currentValue = getSettingValue();
        if (currentValue == null) {
            Log.w(TAG, String.format("Current settings for lever %s is null; skipping", mSetting));
            return;
        }
        if (!mShouldEnableLever.test(currentValue)) {
            Log.v(
                    TAG,
                    String.format("ShouldEnableLever is false for setting %s; skipping", mSetting));
            return;
        }
        Uri settingUri = getSettingUri();
        String lowPowerValue = mLowPowerStateSupplier.get();
        Log.v(TAG, String.format("Updating lever %s to %s", mSetting, lowPowerValue));
        putSettingValue(lowPowerValue);
        putSettingOriginalValue(currentValue);
        mSettingModificationObserver =
                new ContentObserver(null) {
                    @Override
                    public void onChange(boolean selfChange) {
                        super.onChange(selfChange);
                        if (selfChange) {
                            return;
                        }
                        String newValue = getSettingValue();
                        if (newValue.equals(lowPowerValue)) {
                            Log.v(TAG, "New value from user matches low power state. Skipping");
                            return;
                        }
                        Log.v(
                                TAG,
                                String.format(
                                        "User changed value for lever %s to %s",
                                        mSetting, newValue));
                        mContentResolver.unregisterContentObserver(mSettingModificationObserver);
                        putSettingOriginalValue(null);
                    }
                };
        mContentResolver.registerContentObserver(settingUri, false, mSettingModificationObserver);
    }

    void disableLowPower() {
        String originalValue = getSettingOriginalValue();
        if (originalValue == null) {
            Log.v(
                    TAG,
                    String.format(
                            "Original value for lever %s is null; skipping restore", mSetting));
            return;
        }
        if (mSettingModificationObserver != null) {
            mContentResolver.unregisterContentObserver(mSettingModificationObserver);
            mSettingModificationObserver = null;
        }
        if (mLowPowerStateSupplier.get().equals(getSettingValue())) {
            Log.v(TAG, String.format("Restoring lever %s to %s", mSetting, originalValue));
            putSettingValue(originalValue);
            putSettingOriginalValue(null);
            return;
        }
        Log.v(
                TAG,
                String.format(
                        "User has changed lever %s without ContentObserver firing; skipping"
                                + " restore.",
                        mSetting));
    }

    private String getSettingValue() {
        switch (mNamespace) {
            case GLOBAL:
                return Settings.Global.getString(mContentResolver, mSetting);
            case SECURE:
                return Settings.Secure.getString(mContentResolver, mSetting);
            case SYSTEM:
                return Settings.System.getString(mContentResolver, mSetting);
            default:
                throw new RuntimeException("Invalid namespace");
        }
    }

    private String getSettingOriginalValue() {
        String originalSetting = mSetting + ORIGINAL_SUFFIX;
        switch (mOriginalSettingLocation) {
            case DEVICE_SETTINGS:
                switch (mNamespace) {
                    case GLOBAL:
                        return Settings.Global.getString(mContentResolver, originalSetting);
                    case SECURE:
                        return Settings.Secure.getString(mContentResolver, originalSetting);
                    case SYSTEM:
                        // Stock keeps the original values of system settings in Secure.
                        return Settings.Secure.getString(mContentResolver, originalSetting);
                    default:
                        throw new RuntimeException("Invalid namespace");
                }
            case FLIPENDO_SETTINGS:
                return mSharedPreferences.getString(originalSetting, null);
            default:
                throw new RuntimeException("Invalid original setting location");
        }
    }

    private Uri getSettingUri() {
        switch (mNamespace) {
            case GLOBAL:
                return Settings.Global.getUriFor(mSetting);
            case SECURE:
                return Settings.Secure.getUriFor(mSetting);
            case SYSTEM:
                return Settings.System.getUriFor(mSetting);
            default:
                throw new RuntimeException("Invalid namespace");
        }
    }

    private void putSettingValue(String value) {
        switch (mNamespace) {
            case GLOBAL:
                Settings.Global.putString(mContentResolver, mSetting, value);
                break;
            case SECURE:
                Settings.Secure.putString(mContentResolver, mSetting, value);
                break;
            case SYSTEM:
                Settings.System.putString(mContentResolver, mSetting, value);
                break;
        }
    }

    private void putSettingOriginalValue(String value) {
        String originalSetting = mSetting + ORIGINAL_SUFFIX;
        switch (mOriginalSettingLocation) {
            case DEVICE_SETTINGS:
                switch (mNamespace) {
                    case GLOBAL:
                        Settings.Global.putString(mContentResolver, originalSetting, value);
                        break;
                    case SECURE:
                        Settings.Secure.putString(mContentResolver, originalSetting, value);
                        break;
                    case SYSTEM:
                        // Stock keeps the original values of system settings in Secure.
                        Settings.Secure.putString(mContentResolver, originalSetting, value);
                        break;
                    default:
                        throw new RuntimeException("Invalid namespace");
                }
                break;
            case FLIPENDO_SETTINGS:
                mSharedPreferences.edit().putString(originalSetting, value).apply();
                break;
            default:
                throw new RuntimeException("Invalid original setting location");
        }
    }

    static Predicate<String> booleanSupplierToPredicate(BooleanSupplier supplier) {
        return value -> supplier.getAsBoolean();
    }

    static Predicate<String> checkGreaterThanPredicate(Supplier<String> supplier) {
        return value -> Integer.parseInt(value) > Integer.parseInt(supplier.get());
    }
}
