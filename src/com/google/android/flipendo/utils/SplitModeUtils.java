package com.google.android.flipendo.utils;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

import com.android.settingslib.activityembedding.ActivityEmbeddingUtils;

public abstract class SplitModeUtils {
    private static final String TAG = "SplitModeUtils";

    public static boolean isInMultiPaneUi(FragmentActivity activity, Intent intent) {
        if (ActivityEmbeddingUtils.isEmbeddingActivityEnabled(activity)) {
            return ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0
                            || activity.isTaskRoot())
                    && intent.getAction() != null;
        }
        return false;
    }

    public static boolean startMultiPaneUi(FragmentActivity activity, Intent intent) {
        try {
            activity.startActivity(
                    new Intent(Settings.ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY)
                            .setPackage("com.android.settings")
                            .replaceExtras(intent)
                            .putExtra(
                                    Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI,
                                    intent.toUri(Intent.URI_INTENT_SCHEME))
                            .putExtra(
                                    Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY,
                                    "top_level_battery")
                            .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT));
            return true;
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Deep link homepage is not available to show 2-pane UI", e);
            return false;
        }
    }
}
