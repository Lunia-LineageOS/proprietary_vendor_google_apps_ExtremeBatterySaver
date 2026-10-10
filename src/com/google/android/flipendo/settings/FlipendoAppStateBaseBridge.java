package com.google.android.flipendo.settings;

import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.android.settingslib.applications.ApplicationsState;

import java.util.ArrayList;

/** Bridges extra app information into ApplicationsState, like Settings' AppStateBaseBridge. */
abstract class FlipendoAppStateBaseBridge implements ApplicationsState.Callbacks {
    protected final ApplicationsState.Session mAppSession;
    protected final ApplicationsState mAppState;
    protected final Callback mCallback;
    protected final BackgroundHandler mHandler;
    protected final MainHandler mMainHandler;

    interface Callback {
        void onExtraInfoUpdated();

        void onListItemClicked(ApplicationsState.AppEntry appEntry, boolean isChecked);
    }

    FlipendoAppStateBaseBridge(ApplicationsState appState, Callback callback) {
        mMainHandler = new MainHandler(Looper.getMainLooper());
        mAppState = appState;
        mAppSession = appState != null ? appState.newSession(this) : null;
        mCallback = callback;
        // Running on the ApplicationsState background thread keeps us from updating at the
        // same time as it does.
        mHandler =
                new BackgroundHandler(
                        appState != null ? appState.getBackgroundLooper() : Looper.getMainLooper());
    }

    void resume() {
        if (mAppSession != null) {
            mAppSession.onResume();
        }
    }

    void pause() {
        if (mAppSession != null) {
            mAppSession.onPause();
        }
    }

    void release() {
        if (mAppSession != null) {
            mAppSession.onDestroy();
        }
    }

    @Override
    public void onPackageListChanged() {
        mHandler.sendEmptyMessage(BackgroundHandler.MSG_LOAD_ALL);
    }

    @Override
    public void onLoadEntriesCompleted() {
        mHandler.sendEmptyMessage(BackgroundHandler.MSG_LOAD_ALL);
    }

    @Override
    public void onRunningStateChanged(boolean running) {}

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {}

    @Override
    public void onPackageIconChanged() {}

    @Override
    public void onPackageSizeChanged(String packageName) {}

    @Override
    public void onAllSizesComputed() {}

    @Override
    public void onLauncherInfoChanged() {}

    protected abstract void loadAllExtraInfo();

    protected abstract void updateExtraInfo(
            ApplicationsState.AppEntry app, String packageName, int uid);

    final class MainHandler extends Handler {
        private static final int MSG_INFO_UPDATED = 1;

        private MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_INFO_UPDATED) {
                mCallback.onExtraInfoUpdated();
            }
        }
    }

    final class BackgroundHandler extends Handler {
        private static final int MSG_LOAD_ALL = 1;
        private static final int MSG_FORCE_LOAD_PKG = 2;

        private BackgroundHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LOAD_ALL:
                    loadAllExtraInfo();
                    mMainHandler.sendEmptyMessage(MainHandler.MSG_INFO_UPDATED);
                    break;
                case MSG_FORCE_LOAD_PKG:
                    String packageName = (String) msg.obj;
                    int uid = msg.arg1;
                    mAppSession.getAllApps().stream()
                            .filter(
                                    app -> {
                                        ApplicationInfo info = app.info;
                                        return info.uid == uid
                                                && packageName.equals(info.packageName);
                                    })
                            .forEach(app -> updateExtraInfo(app, packageName, uid));
                    mMainHandler.sendEmptyMessage(MainHandler.MSG_INFO_UPDATED);
                    break;
            }
        }
    }
}
