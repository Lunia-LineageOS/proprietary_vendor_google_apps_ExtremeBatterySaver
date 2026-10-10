package com.google.android.flipendo.onboarding;

import android.content.Context;
import android.content.Intent;
import android.hardware.google.pixel.vendor.PixelAtoms;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.settingslib.fuelgauge.BatterySaverLogging;
import com.android.settingslib.fuelgauge.BatterySaverUtils;
import com.android.settingslib.widget.SettingsThemeHelper;

import com.google.android.flipendo.BatterySaverManager;
import com.google.android.flipendo.R;
import com.google.android.flipendo.logging.WestworldLogger;
import com.google.android.flipendo.settings.AllowedAppListFragment;
import com.google.android.flipendo.settings.AllowedAppListProfileSelectFragment;
import com.google.android.flipendo.utils.AllowedAppsUtils;
import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupdesign.GlifPreferenceLayout;
import com.google.android.setupdesign.util.ThemeHelper;

/**
 * Lets the user pick essential apps before turning on Extreme Battery Saver. Started by the
 * SystemUI battery saver confirmation dialog.
 */
public class ExtremeBatterySaverOnboardingActivity extends FragmentActivity {
    private static final String ACTION_START_SAVER_CONFIRMATION = "FLIPENDO.startSaverConfirmation";
    private static final String PERMISSION_START_SAVER_CONFIRMATION =
            "com.google.android.flipendo.START_SAVER_CONFIRMATION";
    private static final String SYSTEMUI_PACKAGE_NAME = "com.android.systemui";
    // Matches BatterySaverManager.FLIPENDO_MODE_AGGRESSIVE.
    private static final int FLIPENDO_MODE_AGGRESSIVE = 1;

    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // Go back to the SystemUI confirmation dialog.
                    sendBroadcast(
                            getStartSaverConfirmationIntent(), PERMISSION_START_SAVER_CONFIRMATION);
                    WestworldLogger.logFlipendoUIElementAction(
                            PixelAtoms.FlipendoUIElementAction.UIElement.ONBOARDING_ALLOW_APPS,
                            PixelAtoms.FlipendoUIElementAction.Action.CANCEL_CLICKED);
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyTheme();
        setContentView(R.layout.base_settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_activity_fragment_container, getTargetFragment())
                    .commitNow();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        setUpActionButton();
    }

    private static Intent getStartSaverConfirmationIntent() {
        return new Intent(ACTION_START_SAVER_CONFIRMATION)
                .setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .setPackage(SYSTEMUI_PACKAGE_NAME);
    }

    private void setBatterySaverConfirmationAcknowledged() {
        Settings.Secure.putInt(
                getApplicationContext().getContentResolver(),
                Settings.Secure.LOW_POWER_WARNING_ACKNOWLEDGED,
                1);
        Settings.Secure.putInt(
                getApplicationContext().getContentResolver(),
                Settings.Secure.EXTRA_LOW_POWER_WARNING_ACKNOWLEDGED,
                1);
    }

    private void applyTheme() {
        Context appContext = getApplicationContext();
        if (!ThemeHelper.trySetSuwTheme(this)) {
            setTheme(ThemeHelper.getSuwDefaultTheme(appContext));
            ThemeHelper.trySetDynamicColor(this);
        }
        setTheme(
                SettingsThemeHelper.isExpressiveTheme(appContext)
                        ? R.style.FlipendoOnboardingTheme_Expressive
                        : R.style.FlipendoOnboardingTheme);
    }

    private FooterButton getPrimaryButton() {
        return new FooterButton.Builder(getApplicationContext())
                .setText(getString(R.string.onboarding_dialog_button_enable))
                .setListener(this::onPrimaryButtonClick)
                .setButtonType(FooterButton.ButtonType.DONE)
                .build();
    }

    private void onPrimaryButtonClick(View view) {
        AsyncTask.execute(
                () -> {
                    BatterySaverManager.getInstance(getApplicationContext())
                            .ifPresent(
                                    manager ->
                                            manager.updateFlipendoMode(FLIPENDO_MODE_AGGRESSIVE));
                    BatterySaverUtils.setPowerSaveMode(
                            getApplicationContext(),
                            true,
                            false,
                            BatterySaverLogging.SAVER_ENABLED_CONFIRMATION);
                    setBatterySaverConfirmationAcknowledged();
                    WestworldLogger.logFlipendoUIElementAction(
                            PixelAtoms.FlipendoUIElementAction.UIElement.ONBOARDING_ALLOW_APPS,
                            PixelAtoms.FlipendoUIElementAction.Action.TURN_ON_CLICKED);
                });
        finish();
    }

    private void setUpActionButton() {
        GlifPreferenceLayout layout = findViewById(android.R.id.list_container);
        layout.getMixin(FooterBarMixin.class).setPrimaryButton(getPrimaryButton());
        getOnBackPressedDispatcher().addCallback(mOnBackPressedCallback);
        if (ThemeHelper.shouldApplyGlifExpressiveStyle(getApplicationContext())) {
            return;
        }
        layout.getMixin(FooterBarMixin.class)
                .setSecondaryButton(
                        new FooterButton.Builder(getApplicationContext())
                                .setText(getString(R.string.onboarding_dialog_button_back))
                                .setListener(v -> getOnBackPressedDispatcher().onBackPressed())
                                .setButtonType(FooterButton.ButtonType.CANCEL)
                                .build());
    }

    private Fragment getTargetFragment() {
        if (AllowedAppsUtils.isWorkProfileTabEnable(getApplicationContext())) {
            return new AllowedAppListProfileSelectFragment();
        }
        return new AllowedAppListFragment();
    }
}
