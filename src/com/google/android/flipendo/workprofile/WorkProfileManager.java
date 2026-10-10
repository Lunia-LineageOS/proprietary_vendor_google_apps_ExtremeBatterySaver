package com.google.android.flipendo.workprofile;

import android.content.Context;
import android.util.Log;

import com.android.settingslib.fuelgauge.BatteryUtils;

public interface WorkProfileManager {
    boolean isWorkAppInForeground();

    boolean isWorkProfilePresent();

    boolean isWorkProfilePresentAndOn();

    void restoreWorkProfile();

    void turnOffWorkProfile();

    void turnOnWorkProfile(boolean fromUser);

    static boolean isWorkProfile(Context context) {
        boolean isWorkProfile = BatteryUtils.isWorkProfile(context);
        Log.i("WorkProfileManager", "isWorkProfile: " + isWorkProfile);
        return isWorkProfile;
    }
}
