package com.google.android.flipendo.settings;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.android.settingslib.widget.ProfileSelectFragment;

/** Shows the essential apps list with personal and work tabs. */
public class AllowedAppListProfileSelectFragment extends ProfileSelectFragment {
    @Override
    public Fragment createFragment(int position) {
        AllowedAppListFragment fragment = new AllowedAppListFragment();
        Bundle args = new Bundle();
        args.putInt(EXTRA_SHOW_FRAGMENT_TAB, position == PERSONAL_TAB ? PERSONAL_TAB : WORK_TAB);
        fragment.setArguments(args);
        return fragment;
    }
}
