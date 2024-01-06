/*
 * Copyright (C) 2023 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.display.refreshrate;

import static android.provider.Settings.System.MIN_REFRESH_RATE;
import static android.provider.Settings.System.PEAK_REFRESH_RATE;

import static android.provider.Settings.System.EXTREME_REFRESH_RATE;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.util.evolution.DisplayRefreshRateHelper;

import com.android.settings.core.BasePreferenceController;

import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.ArrayList;

public class ScreenRefreshRateController extends BasePreferenceController implements
        LifecycleObserver, OnStart, OnStop {

    private final DisplayRefreshRateHelper mHelper;

    private Preference mPreference;
    private SettingObserver mSettingObserver;

    public ScreenRefreshRateController(Context context, String key) {
        super(context, key);
        mHelper = DisplayRefreshRateHelper.getInstance(context);
    }

    @Override
    public int getAvailabilityStatus() {
        return mHelper.getSupportedRefreshRateList().size() > 1
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);

        mPreference = screen.findPreference(getPreferenceKey());
        mSettingObserver = new SettingObserver(mPreference);
    }

    @Override
    public void onStart() {
        if (mSettingObserver != null) {
            mSettingObserver.register(mContext.getContentResolver());
            mSettingObserver.onChange(false);
        }
    }

    @Override
    public void onStop() {
        if (mSettingObserver != null) {
            mSettingObserver.unregister(mContext.getContentResolver());
        }
    }

    @Override
    public void updateState(Preference preference) {
        String summary = null;

        final int minRefreshRate = mHelper.getMinimumRefreshRate();
        final int maxRefreshRate = mHelper.getPeakRefreshRate();

        final boolean extremeMode = Settings.System.getIntForUser(
                mContext.getContentResolver(), EXTREME_REFRESH_RATE,
                0, UserHandle.USER_CURRENT) != 0;
        final ArrayList<Integer> supportedList = mHelper.getSupportedRefreshRateList();

        if (extremeMode && supportedList.size() > 0) {
            final int maxAllowed = supportedList.get(supportedList.size() - 1);
            summary = String.valueOf(maxAllowed) + " Hz";
        } else if (minRefreshRate == maxRefreshRate) {
            summary = String.valueOf(maxRefreshRate) + " Hz";
        } else {
            summary = String.valueOf(minRefreshRate) + " ~ " + String.valueOf(maxRefreshRate) + " Hz";
        }

        if (summary != null) {
            preference.setSummary(summary);
        }

        super.updateState(preference);
    }

    private class SettingObserver extends ContentObserver {

        private final Uri mMinUri = Settings.System.getUriFor(MIN_REFRESH_RATE);
        private final Uri mPeakUri = Settings.System.getUriFor(PEAK_REFRESH_RATE);
        private final Uri mExtremeUri = Settings.System.getUriFor(EXTREME_REFRESH_RATE);

        private final Preference mPreference;

        SettingObserver(Preference preference) {
            super(new Handler());
            mPreference = preference;
        }

        public void register(ContentResolver cr) {
            cr.registerContentObserver(mMinUri, false, this);
            cr.registerContentObserver(mPeakUri, false, this);
        }

        public void unregister(ContentResolver cr) {
            cr.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateState(mPreference);
        }
    }
}
