/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.settings.gestures;

import android.content.Context;
import android.hardware.display.AmbientDisplayConfiguration;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class DoubleTapAmbientSettingsPreferenceController extends BasePreferenceController {

    private AmbientDisplayConfiguration mAmbientConfig;

    public DoubleTapAmbientSettingsPreferenceController(@NonNull Context context, @NonNull String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        boolean nativeDoubleTapToDozeAvailable = !android.text.TextUtils.isEmpty(
                mContext.getResources().getString(com.android.internal.R.string.config_dozeDoubleTapSensorType));
        // avoid conflict with single tap sensor devices e.g: pixels
        boolean singleTapSensorAvailable = getAmbientConfig().tapSensorAvailable();
        boolean supported = !nativeDoubleTapToDozeAvailable && !singleTapSensorAvailable;
        return supported ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(@NonNull PreferenceScreen screen) {
        super.displayPreference(screen);
    }

    @Override
    @NonNull
    public CharSequence getSummary() {
        return mContext.getText(com.android.settings.R.string.doze_double_tap_summary);
    }
    
    private AmbientDisplayConfiguration getAmbientConfig() {
        if (mAmbientConfig == null) {
            mAmbientConfig = new AmbientDisplayConfiguration(mContext);
        }
        return mAmbientConfig;
    }
}
