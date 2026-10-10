package com.google.android.flipendo.settings;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.settingslib.applications.ApplicationsState;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Orders the app preferences: essential apps first (work profile, user selected, then system apps),
 * followed by the non-essential apps.
 */
class AllowedListController {
    private static final String KEY_APPS = "allowed_app_list_apps";
    // Orders of the "Essential apps" and "Non-essential apps" categories are 0 and 1000.
    private static final int ESSENTIAL_APPS_START_ORDER = 2;
    private static final int NON_ESSENTIAL_APPS_START_ORDER = 1002;

    private final FlipendoAppStateBaseBridge mExtraInfoBridge;
    private final AllowedAppListFragment mFragment;
    private final PreferenceGroup mPreferenceGroup;
    private final ApplicationsState mState;
    @VisibleForTesting ArrayList<ApplicationsState.AppEntry> mEssentialEntries = new ArrayList<>();

    @VisibleForTesting
    ArrayList<ApplicationsState.AppEntry> mNonEssentialEntries = new ArrayList<>();

    AllowedListController(
            ApplicationsState state,
            AllowedAppListFragment fragment,
            FlipendoAppStateBaseBridge extraInfoBridge) {
        mState = state;
        mExtraInfoBridge = extraInfoBridge;
        mFragment = fragment;
        mPreferenceGroup = fragment.findPreference(KEY_APPS);
        mPreferenceGroup.setOrderingAsAdded(false);
        mPreferenceGroup.setVisible(false);
    }

    void updateAppEntries(ApplicationsState.AppEntry appEntry, boolean isEssential) {
        if (isEssential) {
            updateEssentialEntries(mEssentialEntries, appEntry);
            mNonEssentialEntries.remove(appEntry);
        } else {
            updateNonEssentialEntries(
                    appEntry,
                    FlipendoAppStateRecentlyUsedBridge.getRecentlyUsedState(appEntry)
                            .mIsWorkProfile,
                    mFragment.getFilterType());
            mEssentialEntries.remove(appEntry);
        }
    }

    void refresh() {
        int order = ESSENTIAL_APPS_START_ORDER;
        for (ApplicationsState.AppEntry entry : mEssentialEntries) {
            addAppEntryToGroup(entry, order++);
        }
        order = NON_ESSENTIAL_APPS_START_ORDER;
        for (ApplicationsState.AppEntry entry : mNonEssentialEntries) {
            addAppEntryToGroup(entry, order++);
        }
        removeUnusedPreferences();
        mPreferenceGroup.setVisible(true);
    }

    void setEssentialEntries(ArrayList<ApplicationsState.AppEntry> entries) {
        updateEssentialEntries(entries, null);
    }

    void setNonEssentialEntries(ArrayList<ApplicationsState.AppEntry> entries) {
        mNonEssentialEntries = entries;
    }

    private void updateEssentialEntries(
            ArrayList<ApplicationsState.AppEntry> entries, ApplicationsState.AppEntry newEntry) {
        if (newEntry == null) {
            separateAndReorderEssentialList(entries, null, findWorkProfileEntry(entries));
        } else if (FlipendoAppStateRecentlyUsedBridge.getRecentlyUsedState(newEntry)
                .mIsWorkProfile) {
            separateAndReorderEssentialList(entries, null, newEntry);
        } else {
            separateAndReorderEssentialList(entries, newEntry, findWorkProfileEntry(entries));
        }
    }

    private void separateAndReorderEssentialList(
            ArrayList<ApplicationsState.AppEntry> entries,
            ApplicationsState.AppEntry newEntry,
            ApplicationsState.AppEntry workProfileEntry) {
        reorderEssentialList(
                createEssentialAppEntries(entries, false, newEntry),
                createEssentialAppEntries(entries, true, null),
                workProfileEntry);
    }

    private ArrayList<ApplicationsState.AppEntry> createEssentialAppEntries(
            ArrayList<ApplicationsState.AppEntry> entries,
            boolean systemApps,
            ApplicationsState.AppEntry newEntry) {
        ArrayList<ApplicationsState.AppEntry> result = new ArrayList<>();
        for (ApplicationsState.AppEntry entry : entries) {
            if (systemApps
                    == FlipendoAppStateRecentlyUsedBridge.getRecentlyUsedState(entry)
                            .mIsSystemApp) {
                result.add(entry);
            }
        }
        if (newEntry != null) {
            result.add(newEntry);
        }
        result.sort(ApplicationsState.ALPHA_COMPARATOR);
        return result;
    }

    private void reorderEssentialList(
            ArrayList<ApplicationsState.AppEntry> userEntries,
            ArrayList<ApplicationsState.AppEntry> systemEntries,
            ApplicationsState.AppEntry workProfileEntry) {
        mEssentialEntries.clear();
        if (workProfileEntry != null) {
            mEssentialEntries.add(workProfileEntry);
        }
        mEssentialEntries.addAll(userEntries);
        mEssentialEntries.addAll(systemEntries);
    }

    private void updateNonEssentialEntries(
            ApplicationsState.AppEntry entry, boolean isWorkProfile, int filterType) {
        if (isWorkProfile) {
            mNonEssentialEntries.add(0, entry);
            return;
        }
        mNonEssentialEntries.add(entry);
        mNonEssentialEntries.sort(getComparatorByType(filterType));
        ApplicationsState.AppEntry workProfileEntry = findWorkProfileEntry(mNonEssentialEntries);
        if (workProfileEntry != null) {
            mNonEssentialEntries.add(0, workProfileEntry);
        }
    }

    /** Removes the work profile entry from the list and returns it. */
    private ApplicationsState.AppEntry findWorkProfileEntry(
            ArrayList<ApplicationsState.AppEntry> entries) {
        if (!mFragment.isWorkProfileEnabled()) {
            return null;
        }
        ApplicationsState.AppEntry workProfileEntry = null;
        for (ApplicationsState.AppEntry entry : entries) {
            if (FlipendoAppStateRecentlyUsedBridge.getRecentlyUsedState(entry).mIsWorkProfile) {
                workProfileEntry = entry;
                break;
            }
        }
        if (workProfileEntry != null) {
            entries.remove(workProfileEntry);
        }
        return workProfileEntry;
    }

    private static Comparator<ApplicationsState.AppEntry> getComparatorByType(int filterType) {
        switch (filterType) {
            case AllowedAppListFragment.FILTER_TYPE_FREQUENTLY_USED:
                return FlipendoAppStateRecentlyUsedBridge.LAUNCH_COUNTS_COMPARATOR;
            case AllowedAppListFragment.FILTER_TYPE_BATTERY:
                return FlipendoAppStateRecentlyUsedBridge.BATTERY_USAGE_COMPARATOR;
            default:
                return ApplicationsState.ALPHA_COMPARATOR;
        }
    }

    private void addAppEntryToGroup(ApplicationsState.AppEntry entry, int order) {
        AllowedListAppPreference preference =
                mFragment.findPreference(Integer.toString(entry.info.uid));
        if (preference == null) {
            preference =
                    new AllowedListAppPreference(
                            mFragment.getContext(),
                            mState,
                            entry,
                            (FlipendoAppStateRecentlyUsedBridge) mExtraInfoBridge);
            mPreferenceGroup.addPreference(preference);
        }
        preference.setOrder(order);
        preference.setFilterType(mFragment.getFilterType());
    }

    // Matches stock: keys (uids) are compared against labels, and the screen can't remove the
    // nested app preferences, so in practice nothing gets removed.
    private void removeUnusedPreferences() {
        ArrayList<String> unusedKeys = fetchAppPreferenceKeysFromGroup();
        for (ApplicationsState.AppEntry entry : mEssentialEntries) {
            unusedKeys.remove(entry.label);
        }
        for (ApplicationsState.AppEntry entry : mNonEssentialEntries) {
            unusedKeys.remove(entry.label);
        }
        for (String key : unusedKeys) {
            Preference preference = mFragment.findPreference(key);
            if (preference != null) {
                mFragment.getPreferenceScreen().removePreference(preference);
            }
        }
    }

    private ArrayList<String> fetchAppPreferenceKeysFromGroup() {
        ArrayList<String> keys = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            Preference preference = mPreferenceGroup.getPreference(i);
            if (preference instanceof AllowedListAppPreference) {
                keys.add(preference.getKey());
            }
        }
        return keys;
    }
}
