/*
 * Copyright (C) 2023 the risingOS android project
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
package com.android.settings.sound;

import android.content.Context;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;

import com.android.settingslib.core.AbstractPreferenceController;

import org.evolution.settings.preferences.CustomSeekBarPreference;

import com.android.internal.util.evolution.VibrationUtils;

public class HapticsPreferenceFragmentController extends AbstractPreferenceController
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY = "haptics_settings";
    private static final String KEY_EDGE_SCROLLING_HAPTICS_INTENSITY = "edge_scrolling_haptics_intensity";
    private static final String KEY_VOLUME_SLIDER_HAPTICS_INTENSITY = "volume_slider_haptics_intensity";

    private CustomSeekBarPreference mEdgeScrollingIntensity;
    private CustomSeekBarPreference mVolumeSliderIntensity;

    private Context mContext;
    private Vibrator mVibrator;

    public HapticsPreferenceFragmentController(Context context) {
        super(context);
        mContext = context;
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public boolean isAvailable() {
        return mVibrator != null && mVibrator.hasVibrator();
    }

    @Override
    public String getPreferenceKey() {
        return KEY;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mEdgeScrollingIntensity = (CustomSeekBarPreference) screen.findPreference(KEY_EDGE_SCROLLING_HAPTICS_INTENSITY);
        mVolumeSliderIntensity = (CustomSeekBarPreference) screen.findPreference(KEY_VOLUME_SLIDER_HAPTICS_INTENSITY);
        updateSettings();
    }

   private void updateSettings() {
        int edgeScrollingIntensity = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.EDGE_SCROLLING_HAPTICS_INTENSITY, 1);
        mEdgeScrollingIntensity.setValue(edgeScrollingIntensity);

        int volumeSliderIntensity = Settings.System.getInt(mContext.getContentResolver(),
                KEY_VOLUME_SLIDER_HAPTICS_INTENSITY, 1);
        mVolumeSliderIntensity.setValue(volumeSliderIntensity);

        mEdgeScrollingIntensity.setOnPreferenceChangeListener(this);
        mVolumeSliderIntensity.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        int intensity = (Integer) newValue;
        boolean isChanged = false;
        if (preference == mEdgeScrollingIntensity) {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.EDGE_SCROLLING_HAPTICS_INTENSITY, intensity);
            mEdgeScrollingIntensity.setValue(intensity);
            isChanged = true;
        } else if (preference == mVolumeSliderIntensity) {
            Settings.System.putInt(mContext.getContentResolver(),
                    KEY_VOLUME_SLIDER_HAPTICS_INTENSITY, intensity);
            mVolumeSliderIntensity.setValue(intensity);
            isChanged = true;
        }
        if (isChanged) {
            VibrationUtils.triggerVibration(mContext, intensity);
            return true;
        }

        return false;
    }

}
