package com.google.android.flipendo.settings;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

/** Shows a loading view in place of the content if loading takes a while. */
class LoadingViewController {
    private static final long DELAY_SHOW_LOADING_CONTAINER_THRESHOLD_MS = 100L;

    private final View mContentView;
    private final View mLoadingView;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mShowLoadingContainerRunnable = this::showLoadingView;

    LoadingViewController(View loadingView, View contentView) {
        mLoadingView = loadingView;
        mContentView = contentView;
    }

    void showContent() {
        mMainHandler.removeCallbacks(mShowLoadingContainerRunnable);
        handleLoadingContainer(true);
    }

    boolean isContentReady() {
        return mContentView.getVisibility() != View.VISIBLE;
    }

    void showLoadingView() {
        handleLoadingContainer(false);
    }

    void showLoadingViewDelayed() {
        mMainHandler.postDelayed(
                mShowLoadingContainerRunnable, DELAY_SHOW_LOADING_CONTAINER_THRESHOLD_MS);
    }

    private void handleLoadingContainer(boolean showContent) {
        mLoadingView.clearAnimation();
        mLoadingView.setVisibility(showContent ? View.GONE : View.VISIBLE);
        mContentView.clearAnimation();
        mContentView.setVisibility(showContent ? View.VISIBLE : View.GONE);
    }
}
