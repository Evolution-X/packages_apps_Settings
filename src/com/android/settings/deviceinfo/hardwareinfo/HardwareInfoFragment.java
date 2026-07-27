/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.deviceinfo.hardwareinfo;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

// LINT.IfChange
@SearchIndexable
public class HardwareInfoFragment extends DashboardFragment {

    public static final String TAG = "HardwareInfo";

    private static final String KEY_DEVICE_IMAGE = "hardware_info_device_image";

    private ModelPageController mHardwareInfoController;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DIALOG_SETTINGS_HARDWARE_INFO;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.hardware_info;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return HardwareInfoScreen.KEY;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        List<AbstractPreferenceController> controllers = new ArrayList<>();
        List<AbstractPreferenceController> parentControllers =
                super.createPreferenceControllers(context);
        if (parentControllers != null) {
            controllers.addAll(parentControllers);
        }
        mHardwareInfoController =
                new ModelPageController(context, KEY_DEVICE_IMAGE);
        controllers.add(mHardwareInfoController);
        return controllers;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        // Everything except the hero LayoutPreference stays registered for search and
        // controllers but is not rendered, so no key list needs to be kept in sync.
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) return;
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            final Preference p = screen.getPreference(i);
            if (!KEY_DEVICE_IMAGE.equals(p.getKey())) p.setVisible(false);
        }
    }

    @Override
    public void onDestroy() {
        if (mHardwareInfoController != null) {
            mHardwareInfoController.onDestroy();
        }
        super.onDestroy();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.hardware_info) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return context.getResources().getBoolean(R.bool.config_show_device_model);
                }
            };
}
// LINT.ThenChange(HardwareInfoScreen.kt)
