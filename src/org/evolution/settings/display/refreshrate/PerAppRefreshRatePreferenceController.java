/*
 * Copyright (C) 2023 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.display.refreshrate;

import static android.provider.Settings.System.EXTREME_REFRESH_RATE;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.util.evolution.DisplayRefreshRateHelper;

import com.android.settings.core.BasePreferenceController;

public class PerAppRefreshRatePreferenceController extends BasePreferenceController {

    private final DisplayRefreshRateHelper mHelper;

    private Preference mPreference;

    public PerAppRefreshRatePreferenceController(Context context, String key) {
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
    }

    @Override
    public void updateState(Preference preference) {
        final boolean extremeMode = Settings.System.getIntForUser(
                mContext.getContentResolver(), EXTREME_REFRESH_RATE,
                0, UserHandle.USER_CURRENT) != 0;
        mPreference.setEnabled(!extremeMode);
    }
}
