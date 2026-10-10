package com.google.android.flipendo.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.flipendo.R;
import com.google.android.flipendo.utils.AllowedAppsUtils;
import com.google.android.flipendo.utils.SplitModeUtils;

/** Hosts the essential apps list in Settings. */
public class AllowedAppListSelectionActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.base_settings_activity);
        Intent intent = getIntent();
        if (SplitModeUtils.isInMultiPaneUi(this, intent)
                && SplitModeUtils.startMultiPaneUi(this, intent)) {
            finish();
        } else if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_activity_fragment_container, getTargetFragment())
                    .commitNow();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    @VisibleForTesting
    Fragment getTargetFragment() {
        if (AllowedAppsUtils.isWorkProfileTabEnable(getApplicationContext())) {
            return new AllowedAppListProfileSelectFragment();
        }
        return new AllowedAppListFragment();
    }
}
