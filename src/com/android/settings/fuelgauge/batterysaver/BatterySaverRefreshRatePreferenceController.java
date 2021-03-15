/*
 * Copyright (C) 2021 WaveOS
 * Copyright (C) 2023-2024 Nameless-AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.fuelgauge.batterysaver;

import static android.provider.Settings.Global.LOW_POWER_REFRESH_RATE;
import static android.provider.Settings.System.EXTREME_REFRESH_RATE;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.evolution.DisplayRefreshRateHelper;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

public class BatterySaverRefreshRatePreferenceController extends TogglePreferenceController {

    private final DisplayRefreshRateHelper mHelper;

    private SwitchPreferenceCompat mPreference;

    public BatterySaverRefreshRatePreferenceController(Context context, String key) {
        super(context, key);
        mHelper = DisplayRefreshRateHelper.getInstance(context);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);

        final boolean extremeRefreshRateEnabled =
                Settings.System.getIntForUser(mContext.getContentResolver(),
                EXTREME_REFRESH_RATE, 0, UserHandle.USER_CURRENT) == 1;
        mPreference = screen.findPreference(getPreferenceKey());
        mPreference.setSummary(extremeRefreshRateEnabled ?
                R.string.battery_saver_refresh_rate_disabled_summary :
                R.string.battery_saver_refresh_rate_summary);
        mPreference.setEnabled(!extremeRefreshRateEnabled);
    }

    @Override
    public boolean isChecked() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                LOW_POWER_REFRESH_RATE, 1) == 1;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.Global.putInt(mContext.getContentResolver(),
                LOW_POWER_REFRESH_RATE, isChecked ? 1 : 0);
    }

    @Override
    public int getAvailabilityStatus() {
        return mHelper.getSupportedRefreshRateList().size() > 1
                        ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_battery;
    }
}
