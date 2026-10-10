package com.google.android.flipendo.utils;

public final class BatteryUsageData {
    public final long mBackgroundUsageTimeInMs;
    public final long mForegroundUsageTimeInMs;
    public final String mPackageName;
    public final double mUsagePercentage;
    public final long mUserId;

    public BatteryUsageData(
            long userId,
            String packageName,
            double usagePercentage,
            long foregroundUsageTimeInMs,
            long backgroundUsageTimeInMs) {
        mUserId = userId;
        mPackageName = packageName;
        mUsagePercentage = usagePercentage;
        mForegroundUsageTimeInMs = foregroundUsageTimeInMs;
        mBackgroundUsageTimeInMs = backgroundUsageTimeInMs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BatteryUsageData {");
        sb.append("\n\tuserId=" + mUserId);
        sb.append("\n\tpackageName=" + mPackageName);
        sb.append("\n\tusagePercentage=" + mUsagePercentage);
        sb.append("\n\tforegroundUsageTimeInMs=" + mForegroundUsageTimeInMs);
        sb.append("\n\tbackgroundUsageTimeInMs=" + mBackgroundUsageTimeInMs);
        sb.append("}");
        return sb.toString();
    }
}
