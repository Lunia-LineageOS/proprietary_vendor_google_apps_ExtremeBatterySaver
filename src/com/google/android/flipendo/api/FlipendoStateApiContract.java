package com.google.android.flipendo.api;

import android.net.Uri;

public abstract class FlipendoStateApiContract {
    public static final Uri IS_FLIPENDO_ENABLED_OBSERVABLE_URI =
            Uri.parse("content://com.google.android.flipendo.api/get_flipendo_state");
}
