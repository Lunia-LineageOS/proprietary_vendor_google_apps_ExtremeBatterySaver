package com.google.android.flipendo.settings;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.widget.AppPreference;

import com.google.android.flipendo.R;

/** An app in the essential apps list, with a button to add it to or remove it from the list. */
class AllowedListAppPreference extends AppPreference {
    private final ApplicationsState.AppEntry mAppEntry;
    private final Context mContext;
    private final FlipendoAppStateRecentlyUsedBridge mExtraInfoBridge;
    private int mFilterType;

    AllowedListAppPreference(
            Context context,
            ApplicationsState applicationsState,
            ApplicationsState.AppEntry appEntry,
            FlipendoAppStateRecentlyUsedBridge extraInfoBridge) {
        super(context);
        setWidgetLayoutResource(R.layout.allowed_apps_list_widget);
        mContext = context;
        mExtraInfoBridge = extraInfoBridge;
        mAppEntry = appEntry;
        applicationsState.ensureIcon(appEntry);
        setIcon(appEntry.icon);
        setTitle(appEntry.label);
        setKey(Integer.toString(appEntry.info.uid));
        setSelectable(!extraInfoBridge.isSystemApp(appEntry));
    }

    public void setFilterType(int filterType) {
        if (filterType != mFilterType) {
            mFilterType = filterType;
            notifyChanged();
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        boolean isSystemApp = mExtraInfoBridge.isSystemApp(mAppEntry);
        boolean isChecked = mExtraInfoBridge.isChecked(mAppEntry);
        boolean isWorkProfile = mExtraInfoBridge.isWorkProfile(mAppEntry);
        holder.itemView.setContentDescription(
                getAccessibilityDescription(mAppEntry.label, isChecked, !isSystemApp));
        ImageView checkbox = (ImageView) holder.findViewById(R.id.checkbox_widget);
        checkbox.setVisibility(isSystemApp ? View.GONE : View.VISIBLE);
        if (isSystemApp) {
            setOnPreferenceClickListener(null);
        } else {
            checkbox.setImageDrawable(mExtraInfoBridge.getCheckedIcon(isChecked));
            setOnPreferenceClickListener(
                    mExtraInfoBridge.getOnPreferenceClickListener(mAppEntry, this, mFilterType));
        }
        TextView batteryUsage = (TextView) holder.findViewById(R.id.battery_usage);
        batteryUsage.setVisibility(
                !isWorkProfile
                                && !isSystemApp
                                && !isChecked
                                && mFilterType == AllowedAppListFragment.FILTER_TYPE_BATTERY
                        ? View.VISIBLE
                        : View.GONE);
        batteryUsage.setText(mExtraInfoBridge.getBatteryUsage(mAppEntry));
    }

    private String getAccessibilityDescription(String label, boolean isChecked, boolean editable) {
        int description;
        if (editable) {
            description =
                    isChecked
                            ? R.string.allowed_app_list_essential_apps_a11y
                            : R.string.allowed_app_list_non_essential_apps_a11y;
        } else {
            description = R.string.allowed_app_list_fixed_essential_apps_a11y;
        }
        return mContext.getString(description, label);
    }
}
