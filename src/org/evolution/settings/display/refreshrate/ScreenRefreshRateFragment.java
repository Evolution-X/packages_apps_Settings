/*
 * Copyright (C) 2023 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.display.refreshrate;

import static android.provider.Settings.System.MIN_REFRESH_RATE;
import static android.provider.Settings.System.PEAK_REFRESH_RATE;

import static android.provider.Settings.System.EXTREME_REFRESH_RATE;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;

public class ScreenRefreshRateFragment extends DashboardFragment {

    private static final String TAG = "ScreenRefreshRateFragment";

    private final SettingsObserver mObserver = new SettingsObserver();

    private final class SettingsObserver extends ContentObserver {

        private final Uri mMinUri = Settings.System.getUriFor(MIN_REFRESH_RATE);
        private final Uri mPeakUri = Settings.System.getUriFor(PEAK_REFRESH_RATE);
        private final Uri mExtremeUri = Settings.System.getUriFor(EXTREME_REFRESH_RATE);

        SettingsObserver() {
            super(new Handler());
        }

        void register(ContentResolver cr) {
            cr.registerContentObserver(mMinUri, false, this);
            cr.registerContentObserver(mPeakUri, false, this);
            cr.registerContentObserver(mExtremeUri, false, this);
        }

        void unregister(ContentResolver cr) {
            cr.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updatePreferenceStates();
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mObserver.register(getContext().getContentResolver());
    }

    @Override
    public void onDestroy() {
        mObserver.unregister(getContext().getContentResolver());
        super.onDestroy();
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.screen_refresh_rate;
    }
}
