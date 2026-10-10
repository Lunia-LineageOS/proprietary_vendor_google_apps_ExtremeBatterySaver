package com.google.android.flipendo.logging;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.frameworks.stats.Annotation;
import android.frameworks.stats.AnnotationId;
import android.frameworks.stats.AnnotationSet;
import android.frameworks.stats.AnnotationValue;
import android.frameworks.stats.IStats;
import android.frameworks.stats.VendorAtom;
import android.frameworks.stats.VendorAtomValue;
import android.hardware.google.pixel.vendor.PixelAtoms;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;

import com.google.android.flipendo.settings.SettingsController;
import com.google.android.flipendo.utils.DeviceConfigUtils;

import java.util.Optional;

/** Reports Flipendo atoms to the vendor IStats service. */
public abstract class WestworldLogger {
    private static final String TAG = "WestworldLogger";

    // Atom ids of Google's internal pixelatoms.proto.
    private static final int ATOM_FLIPENDO_ENABLED = 100032;
    private static final int ATOM_FLIPENDO_ESSENTIAL_APPS_CHANGE = 100033;
    private static final int ATOM_FLIPENDO_APP_UNSUSPENDED = 100035;
    private static final int ATOM_FLIPENDO_UI_ELEMENT_ACTION = 100041;
    private static final int ATOM_FLIPENDO_UPDATE_TIME_ELAPSED = 100043;
    private static final int ATOM_FLIPENDO_WORK_PROFILE_PREFERENCE_CHANGE = 100048;
    private static final int ATOM_FLIPENDO_WORK_PROFILE_TURNED_OFF = 100049;

    private static final String PLAY_STORE_PACKAGE_NAME = "com.android.vending";

    public enum FlipendoEnabledType {
        BOOT_COMPLETED,
        NORMAL
    }

    public static void logFlipendoEnabledAtom(boolean enabled, FlipendoEnabledType type) {
        PixelAtoms.FlipendoEnabled.State state =
                enabled
                        ? PixelAtoms.FlipendoEnabled.State.ON
                        : PixelAtoms.FlipendoEnabled.State.OFF;
        PixelAtoms.FlipendoEnabled.Type atomType;
        switch (type) {
            case BOOT_COMPLETED:
                atomType = PixelAtoms.FlipendoEnabled.Type.BOOT_COMPLETED;
                break;
            case NORMAL:
                atomType = PixelAtoms.FlipendoEnabled.Type.NORMAL;
                break;
            default:
                atomType = PixelAtoms.FlipendoEnabled.Type.UNKNOWN_TYPE;
                break;
        }
        reportVendorAtom(
                createVendorAtomWithValues(
                        ATOM_FLIPENDO_ENABLED,
                        createVendorAtomIntValue(state.getNumber()),
                        createVendorAtomIntValue(atomType.getNumber())));
    }

    /** Logs a tap on the work profile entry; stock passes the state from before the tap. */
    public static void logWorkProfilePreferenceChange(boolean wasChecked) {
        // ??? The atom's enum didn't survive R8; 1 and 2 are the values stock logs.
        reportVendorAtom(
                createVendorAtomWithValues(
                        ATOM_FLIPENDO_WORK_PROFILE_PREFERENCE_CHANGE,
                        createVendorAtomIntValue(wasChecked ? 1 : 2)));
    }

    public static void logFlipendoEssentialAppsChange(
            boolean essential, String packageName, Context context, int filterType) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            if (isSideLoaded(info, context)) {
                Log.i(TAG, "Not logging side-loaded app: " + packageName);
                return;
            }
            PixelAtoms.FlipendoEssentialAppsChange.Type type =
                    essential
                            ? PixelAtoms.FlipendoEssentialAppsChange.Type.WHITELISTED
                            : PixelAtoms.FlipendoEssentialAppsChange.Type.NOT_WHITELISTED;
            VendorAtom atom =
                    createVendorAtomWithValues(
                            ATOM_FLIPENDO_ESSENTIAL_APPS_CHANGE,
                            createVendorAtomIntValue(info.uid),
                            createVendorAtomIntValue(type.getNumber()),
                            createVendorAtomIntValue(filterType));
            atom.valuesAnnotations = new AnnotationSet[] {createVendorAtomUidAnnotation(0)};
            reportVendorAtom(atom);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(
                    TAG,
                    "Can't find "
                            + packageName
                            + " package info. Essential app change isn't logged.",
                    e);
        }
    }

    public static void logFlipendoAppUnsuspended(
            String packageName, Context context, PixelAtoms.FlipendoAppUnsuspended.Type type) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            if (isSideLoaded(info, context)) {
                Log.i(TAG, "Not logging side-loaded app: " + packageName);
                return;
            }
            VendorAtom atom =
                    createVendorAtomWithValues(
                            ATOM_FLIPENDO_APP_UNSUSPENDED,
                            createVendorAtomIntValue(info.uid),
                            createVendorAtomIntValue(
                                    DeviceConfigUtils.getResuspendDelayMinutes() * 60),
                            createVendorAtomIntValue(type.getNumber()));
            atom.valuesAnnotations = new AnnotationSet[] {createVendorAtomUidAnnotation(0)};
            reportVendorAtom(atom);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(
                    TAG,
                    "Can't find " + packageName + " package info. Unsuspension isn't logged.",
                    e);
        }
    }

    public static void logWorkProfileTurnedOff(PixelAtoms.FlipendoWorkProfileTurnedOff.Type type) {
        reportVendorAtom(
                createVendorAtomWithValues(
                        ATOM_FLIPENDO_WORK_PROFILE_TURNED_OFF,
                        createVendorAtomIntValue(type.getNumber())));
    }

    public static void logFlipendoUIElementAction(
            PixelAtoms.FlipendoUIElementAction.UIElement element,
            PixelAtoms.FlipendoUIElementAction.Action action) {
        reportVendorAtom(
                createVendorAtomWithValues(
                        ATOM_FLIPENDO_UI_ELEMENT_ACTION,
                        createVendorAtomIntValue(element.getNumber()),
                        createVendorAtomIntValue(action.getNumber())));
    }

    public static void logFlipendoUpdateTimeElapsed(boolean enabled, long elapsedTimeMs) {
        PixelAtoms.FlipendoUpdateTimeElapsed.Type type =
                enabled
                        ? PixelAtoms.FlipendoUpdateTimeElapsed.Type.ENABLED
                        : PixelAtoms.FlipendoUpdateTimeElapsed.Type.DISABLED;
        reportVendorAtom(
                createVendorAtomWithValues(
                        ATOM_FLIPENDO_UPDATE_TIME_ELAPSED,
                        createVendorAtomIntValue(type.getNumber()),
                        createVendorAtomLongValue(elapsedTimeMs)));
    }

    public static void storeFlipendoSavingsMode(
            Context context, SettingsController.SavingsMode savingsMode) {
        Settings.Secure.putString(
                context.getContentResolver(),
                Settings.Secure.EXTRA_AUTOMATIC_POWER_SAVE_MODE,
                savingsMode.getKeyString(context));
    }

    private static VendorAtomValue createVendorAtomIntValue(int value) {
        VendorAtomValue atomValue = new VendorAtomValue();
        atomValue.setIntValue(value);
        return atomValue;
    }

    private static VendorAtomValue createVendorAtomLongValue(long value) {
        VendorAtomValue atomValue = new VendorAtomValue();
        atomValue.setLongValue(value);
        return atomValue;
    }

    private static VendorAtom createVendorAtomWithValues(int atomId, VendorAtomValue... values) {
        VendorAtom atom = new VendorAtom();
        atom.reverseDomainName = "";
        atom.atomId = atomId;
        atom.values = values;
        return atom;
    }

    private static AnnotationSet createVendorAtomUidAnnotation(int valueIndex) {
        Annotation annotation = new Annotation();
        annotation.annotationId = AnnotationId.IS_UID;
        annotation.value = AnnotationValue.boolValue(true);
        AnnotationSet annotationSet = new AnnotationSet();
        annotationSet.valueIndex = valueIndex;
        annotationSet.annotations = new Annotation[] {annotation};
        return annotationSet;
    }

    private static void reportVendorAtom(VendorAtom atom) {
        try {
            Optional<IStats> stats = tryConnectingToStatsService();
            if (stats.isPresent()) {
                stats.get().reportVendorAtom(atom);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to log atom to IStats service", e);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to connect to IStats service", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to log atom to IStats service", e);
        }
    }

    private static Optional<IStats> tryConnectingToStatsService() {
        String instanceName = IStats.DESCRIPTOR + "/default";
        if (!ServiceManager.isDeclared(instanceName)) {
            Log.e(TAG, "IStats is not registered");
            return Optional.empty();
        }
        return Optional.ofNullable(
                IStats.Stub.asInterface(ServiceManager.waitForDeclaredService(instanceName)));
    }

    private static boolean isSideLoaded(ApplicationInfo info, Context context) {
        if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            return false;
        }
        try {
            return !PLAY_STORE_PACKAGE_NAME.equals(
                    context.getPackageManager().getInstallerPackageName(info.packageName));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
