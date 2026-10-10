package com.google.android.flipendo.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.widget.ProfileSelectFragment;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import com.android.settingslib.widget.SettingsSpinnerAdapter;
import com.android.settingslib.widget.SettingsSpinnerPreference;
import com.android.settingslib.widget.SettingsThemeHelper;

import com.google.android.flipendo.R;
import com.google.android.flipendo.utils.DeviceConfigUtils;
import com.google.android.flipendo.workprofile.WorkProfileManagerImpl;
import com.google.android.setupdesign.GlifPreferenceLayout;

import java.util.ArrayList;
import java.util.Comparator;

/** Lists the apps that are kept running ("essential") or paused by Extreme Battery Saver. */
public class AllowedAppListFragment extends SettingsBasePreferenceFragment
        implements ApplicationsState.Callbacks,
                FlipendoAppStateBaseBridge.Callback,
                AdapterView.OnItemSelectedListener {
    private static final String TAG = "AllowedAppListFragment";

    // Positions in the sort spinner.
    static final int FILTER_TYPE_FREQUENTLY_USED = 0;
    static final int FILTER_TYPE_BATTERY = 1;
    static final int FILTER_TYPE_ALPHABETICAL = 2;

    private static final String FILTER_TYPE_FILE = "filter_type_file";
    private static final String KEY_FILTER_TYPE = "filter_type_key";
    private static final String KEY_FILTER_SPINNER = "filter_spinner";

    private AllowedListController mAllowedListController;
    private ApplicationsState mApplicationsState;
    private Context mContext;
    private FlipendoAppStateBaseBridge mExtraInfoBridge;
    private boolean mIsBridgeCallbackReceived;
    private boolean mIsLoadEntriesReceived;
    private boolean mIsWorkProfileTab;
    private LoadingViewController mLoadingViewController;
    private boolean mResumed;
    private ApplicationsState.Session mSession;
    private SharedPreferences mSharedPreferences;
    private SettingsSpinnerAdapter<CharSequence> mSpinnerAdapter;
    private int mSpinnerPosition;
    private SettingsSpinnerPreference mSpinnerPreference;
    private boolean mWorkProfileEnabled;
    private ApplicationsState.AppEntry mWorkProfileEntry;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentActivity activity = getActivity();
        mContext = getContext();
        mApplicationsState = ApplicationsState.getInstance(activity.getApplication());
        mSession = mApplicationsState.newSession(this);
        mSharedPreferences =
                mContext.getApplicationContext()
                        .getSharedPreferences(FILTER_TYPE_FILE, Context.MODE_PRIVATE);
        createWorkProfileEntryIfNeeded();
        mIsWorkProfileTab =
                getArguments() != null
                        && getArguments()
                                        .getInt(
                                                ProfileSelectFragment.EXTRA_SHOW_FRAGMENT_TAB,
                                                ProfileSelectFragment.PERSONAL_TAB)
                                == ProfileSelectFragment.WORK_TAB;
        FlipendoAppStateRecentlyUsedBridge bridge =
                new FlipendoAppStateRecentlyUsedBridge(
                        activity, mApplicationsState, this, mIsWorkProfileTab);
        mExtraInfoBridge = bridge;
        mAllowedListController = new AllowedListController(mApplicationsState, this, bridge);
        initSpinner();
    }

    @Override
    public RecyclerView onCreateRecyclerView(
            LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        // The onboarding activity hosts this fragment in a GlifPreferenceLayout.
        if (parent instanceof GlifPreferenceLayout) {
            return ((GlifPreferenceLayout) parent)
                    .onCreateRecyclerView(inflater, parent, savedInstanceState);
        }
        return super.onCreateRecyclerView(inflater, parent, savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mLoadingViewController =
                new LoadingViewController(
                        getActivity().findViewById(R.id.loading_container),
                        getActivity().findViewById(R.id.settings_activity_fragment_container));
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.allowed_app_list_preferences);
    }

    @Override
    public void onStart() {
        super.onStart();
        initWorkProfileIfAvailable();
        resumeAppStateBridge();
        updateLoadingContainer();
    }

    @Override
    public void onStop() {
        super.onStop();
        mSharedPreferences.edit().putInt(KEY_FILTER_TYPE, getFilterType()).apply();
        pauseAppStateBridge();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        releaseAppStateBridge();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (mSpinnerPosition != position) {
            mSpinnerPosition = position;
            rebuild();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
        if (apps.isEmpty()) {
            return;
        }
        ArrayList<ApplicationsState.AppEntry> essentialEntries = new ArrayList<>();
        ArrayList<ApplicationsState.AppEntry> nonEssentialEntries = new ArrayList<>();
        if (mWorkProfileEntry != null) {
            if (isChecked(mWorkProfileEntry)) {
                essentialEntries.add(mWorkProfileEntry);
            } else {
                nonEssentialEntries.add(mWorkProfileEntry);
            }
        }
        PackageManager packageManager = mContext.getPackageManager();
        for (ApplicationsState.AppEntry app : apps) {
            try {
                packageManager.getApplicationInfo(app.info.packageName, 0);
                if (FlipendoAppStateRecentlyUsedBridge.getRecentlyUsedState(app).mIsSystemApp
                        || isChecked(app)) {
                    essentialEntries.add(app);
                } else {
                    nonEssentialEntries.add(app);
                }
            } catch (Exception e) {
                Log.w(
                        TAG,
                        "Received package not belongs to current user:" + app.info.packageName,
                        e);
            }
        }
        mAllowedListController.setEssentialEntries(essentialEntries);
        mAllowedListController.setNonEssentialEntries(nonEssentialEntries);
        mAllowedListController.refresh();
        if (mLoadingViewController.isContentReady()) {
            mLoadingViewController.showContent();
        }
    }

    @Override
    public void onLoadEntriesCompleted() {
        Log.d(TAG, "onLoadEntriesCompleted");
        mIsLoadEntriesReceived = true;
        rebuild();
    }

    @Override
    public void onRunningStateChanged(boolean running) {}

    @Override
    public void onPackageListChanged() {}

    @Override
    public void onPackageIconChanged() {}

    @Override
    public void onPackageSizeChanged(String packageName) {}

    @Override
    public void onAllSizesComputed() {}

    @Override
    public void onLauncherInfoChanged() {}

    @Override
    public void onExtraInfoUpdated() {
        Log.d(TAG, "onExtraInfoUpdated");
        mIsBridgeCallbackReceived = true;
        rebuild();
    }

    @Override
    public void onListItemClicked(ApplicationsState.AppEntry appEntry, boolean isChecked) {
        mAllowedListController.updateAppEntries(appEntry, isChecked);
        mAllowedListController.refresh();
    }

    private void initSpinner() {
        mSpinnerPosition = mSharedPreferences.getInt(KEY_FILTER_TYPE, FILTER_TYPE_FREQUENTLY_USED);
        mSpinnerPreference = findPreference(KEY_FILTER_SPINNER);
        mSpinnerAdapter = new SettingsSpinnerAdapter<>(mContext);
        mSpinnerAdapter.addAll(
                mContext.getString(R.string.filter_frequently),
                mContext.getString(R.string.filter_battery),
                mContext.getString(R.string.filter_alphabetically));
        mSpinnerPreference.setAdapter(mSpinnerAdapter);
        mSpinnerPreference.setOnItemSelectedListener(this);
        mSpinnerPreference.setSelection(mSpinnerPosition);
    }

    private boolean isChecked(ApplicationsState.AppEntry app) {
        return ((FlipendoAppStateRecentlyUsedBridge) mExtraInfoBridge).isChecked(app);
    }

    /** The work profile is listed as an entry unless it has its own tab. */
    private boolean initWorkProfileIfAvailable() {
        mWorkProfileEnabled =
                DeviceConfigUtils.getIsWorkProfileLeverEnabled()
                        && WorkProfileManagerImpl.getInstance(mContext).isWorkProfilePresent()
                        && !DeviceConfigUtils.isWorkProfileTabEnabled();
        return mWorkProfileEnabled;
    }

    private void createWorkProfileEntryIfNeeded() {
        if (!initWorkProfileIfAvailable()) {
            return;
        }
        ApplicationInfo info = new ApplicationInfo();
        info.sourceDir = "unused_source_dir";
        FlipendoAppStateRecentlyUsedBridge.RecentlyUsedState state =
                new FlipendoAppStateRecentlyUsedBridge.RecentlyUsedState();
        state.mIsWorkProfile = true;
        mWorkProfileEntry = new ApplicationsState.AppEntry(mContext, info, -1);
        mWorkProfileEntry.label = getString(R.string.work_profile_entry_title);
        mWorkProfileEntry.extraInfo = state;
        mWorkProfileEntry.icon = getWorkProfileEntryIcon();
    }

    private Drawable getWorkProfileEntryIcon() {
        return mContext.getDrawable(
                SettingsThemeHelper.isExpressiveTheme(mContext)
                        ? R.drawable.ic_expressive_work_app_badge
                        : R.drawable.ic_work_app_badge);
    }

    private void updateLoadingContainer() {
        if (mIsLoadEntriesReceived) {
            mLoadingViewController.showContent();
        } else {
            mLoadingViewController.showLoadingViewDelayed();
        }
    }

    private void resumeAppStateBridge() {
        Log.d(TAG, "resumeAppStateBridge: " + mResumed);
        if (mResumed) {
            return;
        }
        mResumed = true;
        mSession.onResume();
        if (mExtraInfoBridge != null) {
            mExtraInfoBridge.resume();
        }
        rebuild();
    }

    private void pauseAppStateBridge() {
        if (!mResumed) {
            return;
        }
        Log.d(TAG, "pauseAppStateBridge");
        mResumed = false;
        mSession.onPause();
        if (mExtraInfoBridge != null) {
            mExtraInfoBridge.pause();
        }
    }

    private void releaseAppStateBridge() {
        Log.d(TAG, "releaseAppStateBridge");
        mSession.onDestroy();
        if (mExtraInfoBridge != null) {
            mExtraInfoBridge.release();
        }
    }

    private void rebuild() {
        if (allAppsEntriesNotLoaded()) {
            Log.d(TAG, "Do not rebuild the list since all app entries not ready");
            return;
        }
        ThreadUtils.postOnBackgroundThread(
                () -> {
                    ApplicationsState.AppFilter filter =
                            getFilterType() == FILTER_TYPE_BATTERY
                                    ? FlipendoAppStateRecentlyUsedBridge.FILTER_APP_BATTERY_USAGE
                                    : FlipendoAppStateRecentlyUsedBridge.FILTER_APP_LAUNCH_COUNTS;
                    filter =
                            new ApplicationsState.CompoundFilter(
                                    filter, ApplicationsState.FILTER_DOWNLOADED_AND_LAUNCHER);
                    filter =
                            new ApplicationsState.CompoundFilter(
                                    filter, ApplicationsState.FILTER_NOT_HIDE);
                    filter =
                            new ApplicationsState.CompoundFilter(
                                    filter,
                                    mIsWorkProfileTab
                                            ? ApplicationsState.FILTER_WORK
                                            : ApplicationsState.FILTER_PERSONAL);
                    mSession.rebuild(filter, getAppEntryComparator(), false);
                });
    }

    private boolean allAppsEntriesNotLoaded() {
        if (!mIsLoadEntriesReceived) {
            return true;
        }
        return mExtraInfoBridge != null && !mIsBridgeCallbackReceived;
    }

    private Comparator<ApplicationsState.AppEntry> getAppEntryComparator() {
        switch (getFilterType()) {
            case FILTER_TYPE_FREQUENTLY_USED:
                return FlipendoAppStateRecentlyUsedBridge.LAUNCH_COUNTS_COMPARATOR;
            case FILTER_TYPE_BATTERY:
                return FlipendoAppStateRecentlyUsedBridge.BATTERY_USAGE_COMPARATOR;
            default:
                return ApplicationsState.ALPHA_COMPARATOR;
        }
    }

    @VisibleForTesting
    int getFilterType() {
        return mSpinnerPosition;
    }

    boolean isWorkProfileEnabled() {
        return mWorkProfileEnabled;
    }
}
