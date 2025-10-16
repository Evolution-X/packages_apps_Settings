/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settings.location;

import android.content.Context;
import android.os.SystemProperties;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnResume;

/**
 * Controller for the "Override MockLocation restriction" toggle in More security settings.
 * This toggle controls whether apps can detect mock locations through isMock() and 
 * isFromMockProvider() APIs.
 */
public class OverrideMockLocationPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener, LifecycleObserver, OnResume {

    private static final String KEY_OVERRIDE_MOCK_LOCATION = "override_mock_location";
    private static final String PROPERTY_OVERRIDE_MOCK = "persist.sys.override_mock_location";

    private SwitchPreferenceCompat mPreference;

    public OverrideMockLocationPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        // Always available in More security settings
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        if (mPreference != null) {
            updateState();
        }
    }

    @Override
    public void onResume() {
        updateState();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final boolean isEnabled = (Boolean) newValue;
        
        // Set the system property based on toggle state
        SystemProperties.set(PROPERTY_OVERRIDE_MOCK, isEnabled ? "true" : "false");
        
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        updateState();
    }

    private void updateState() {
        if (mPreference != null) {
            // Get current state from system property
            final boolean enabled = SystemProperties.getBoolean(PROPERTY_OVERRIDE_MOCK, false);
            mPreference.setChecked(enabled);
        }
    }

    @Override
    public String getPreferenceKey() {
        return KEY_OVERRIDE_MOCK_LOCATION;
    }
}
