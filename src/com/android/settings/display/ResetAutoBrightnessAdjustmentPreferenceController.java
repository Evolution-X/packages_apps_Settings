/*
 * Copyright (C) 2022 Project Kaleidoscope
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
 * limitations under the License
 */

package com.android.settings.display;

import static android.provider.Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ;

import android.content.Context;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.R;

public class ResetAutoBrightnessAdjustmentPreferenceController extends
        BasePreferenceController implements Preference.OnPreferenceClickListener {

    public ResetAutoBrightnessAdjustmentPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    @AvailabilityStatus
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        screen.findPreference(getPreferenceKey()).setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        Settings.System.putFloat(mContext.getContentResolver(), SCREEN_AUTO_BRIGHTNESS_ADJ, 0f);
        Toast.makeText(mContext, mContext.getString(
                            R.string.reset_auto_brightness_adjustment_done),
                            Toast.LENGTH_SHORT).show();
        return true;
    }
}
