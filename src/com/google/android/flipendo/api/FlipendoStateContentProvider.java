package com.google.android.flipendo.api;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.settingslib.fuelgauge.BatterySaverLogging;
import com.android.settingslib.fuelgauge.BatterySaverUtils;

import com.google.android.flipendo.BatterySaverManager;
import com.google.android.flipendo.InternalStateController;
import com.google.android.flipendo.settings.SettingsController;
import com.google.common.collect.ImmutableSet;

import java.security.AccessControlException;
import java.util.Set;

/** Lets trusted apps query and control Extreme Battery Saver. */
public class FlipendoStateContentProvider extends ContentProvider {
    private static final String TAG = "FlipendoStateCP";

    private static final String METHOD_GET_FLIPENDO_STATE = "get_flipendo_state";
    private static final String METHOD_FORCE_ENABLE_FLIPENDO = "force_enable_flipendo_method";
    private static final String METHOD_UPDATE_FLIPENDO_MODE = "update_flipendo_mode_method";
    private static final String METHOD_UPDATE_TERMINATOR_MODE = "update_terminator_mode_method";

    private static final String EXTRA_ENABLE_TERMINATOR = "enable_terminator";
    private static final String EXTRA_UPDATE_FLIPENDO_MODE = "update_flipendo_mode";
    private static final String KEY_FLIPENDO_STATE = "flipendo_state";
    private static final String KEY_IS_FLIPENDO_AGGRESSIVE = "is_flipendo_aggressive";

    private static final Set<String> AVAILABLE_METHODS =
            ImmutableSet.of(
                    METHOD_GET_FLIPENDO_STATE,
                    METHOD_FORCE_ENABLE_FLIPENDO,
                    METHOD_UPDATE_FLIPENDO_MODE,
                    METHOD_UPDATE_TERMINATOR_MODE);

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!AVAILABLE_METHODS.contains(method)) {
            throw new UnsupportedOperationException("Unsupported method: " + method);
        }
        String callingPackage = getCallingPackageNonFinal();
        if (callingPackage == null
                || !ApiCallingPackageVerifier.isAllowedToUseApi(getContext(), callingPackage)) {
            throw new AccessControlException(
                    "Calling package is null or not allowed: " + callingPackage);
        }
        Log.v(
                TAG,
                "Responded to " + method + " with bundle: " + (extras == null ? "null" : extras));
        if (METHOD_UPDATE_TERMINATOR_MODE.equals(method)) {
            if (isValidTerminatorModeRequest(extras)) {
                return extras.getBoolean(EXTRA_ENABLE_TERMINATOR, false)
                        ? enableTerminatorMode()
                        : disableTerminatorMode();
            }
            Log.w(TAG, "Invalid update terminator mode extras");
        } else if (METHOD_FORCE_ENABLE_FLIPENDO.equals(method)) {
            return forceEnableFlipendo();
        } else if (METHOD_UPDATE_FLIPENDO_MODE.equals(method)) {
            return updateFlipendoMode(extras);
        } else if (METHOD_GET_FLIPENDO_STATE.equals(method)) {
            return getFlipendoState();
        }
        return Bundle.EMPTY;
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

    private Bundle forceEnableFlipendo() {
        Context context = getContext();
        if (!BatterySaverManager.getInstance(context).isPresent()) {
            return Bundle.EMPTY;
        }
        BatterySaverManager batterySaverManager = BatterySaverManager.getInstance(context).get();
        batterySaverManager.setForceEnableFlipendo(true);
        boolean isPowerSaveMode = context.getSystemService(PowerManager.class).isPowerSaveMode();
        Log.d(TAG, "Force enable Flipendo, is power save mode: " + isPowerSaveMode);
        if (isPowerSaveMode) {
            batterySaverManager.updateState(TAG);
        } else {
            // The resulting POWER_SAVE_MODE_CHANGED broadcast enables Flipendo.
            BatterySaverUtils.setPowerSaveMode(
                    context, true, false, BatterySaverLogging.SAVER_ENABLED_SEVERE_WARNING);
        }
        return Bundle.EMPTY;
    }

    private Bundle enableTerminatorMode() {
        Context context = getContext();
        if (!BatterySaverManager.getInstance(context).isPresent()) {
            return Bundle.EMPTY;
        }
        BatterySaverManager batterySaverManager = BatterySaverManager.getInstance(context).get();
        batterySaverManager.setForceEnableFlipendo(true);
        SettingsController settingsController = SettingsController.getInstance(context);
        if (!settingsController.isTerminatorModeEnabled()) {
            settingsController.setEnableTerminatorRequest(true);
        }
        batterySaverManager.updateState(TAG);
        return Bundle.EMPTY;
    }

    private Bundle disableTerminatorMode() {
        Context context = getContext();
        if (!BatterySaverManager.getInstance(context).isPresent()) {
            return Bundle.EMPTY;
        }
        SettingsController settingsController = SettingsController.getInstance(context);
        settingsController.setEnableTerminatorRequest(false);
        settingsController.setTerminatorModeEnabled(false);
        BatterySaverManager batterySaverManager = BatterySaverManager.getInstance(context).get();
        batterySaverManager.setForceEnableFlipendo(false);
        if (settingsController
                        .getSavedSavingsMode()
                        .equals(SettingsController.SavingsMode.AGGRESSIVE)
                && context.getSystemService(PowerManager.class).isPowerSaveMode()) {
            batterySaverManager.recoverFlipendo(TAG);
        } else {
            batterySaverManager.updateState(TAG);
        }
        return Bundle.EMPTY;
    }

    private Bundle updateFlipendoMode(Bundle extras) {
        if (extras != null) {
            int mode = extras.getInt(EXTRA_UPDATE_FLIPENDO_MODE, 0);
            BatterySaverManager.getInstance(getContext())
                    .ifPresent(manager -> manager.updateFlipendoMode(mode));
        }
        return Bundle.EMPTY;
    }

    private Bundle getFlipendoState() {
        Bundle state = new Bundle(2);
        state.putBoolean(
                KEY_FLIPENDO_STATE,
                InternalStateController.getInstance(getContext()).isFlipendoEnabled());
        state.putBoolean(
                KEY_IS_FLIPENDO_AGGRESSIVE,
                SettingsController.getInstance(getContext()).getSavedSavingsMode()
                        == SettingsController.SavingsMode.AGGRESSIVE);
        return state;
    }

    private static boolean isValidTerminatorModeRequest(Bundle extras) {
        return extras != null && !extras.isEmpty() && extras.containsKey(EXTRA_ENABLE_TERMINATOR);
    }

    @VisibleForTesting
    String getCallingPackageNonFinal() {
        return getCallingPackage();
    }
}
