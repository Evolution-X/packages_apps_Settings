/*
 * Copyright (C) 2020 The Calyx Institute
 * Copyright (C) 2024 The LineageOS Project
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

package com.android.settings.backup.transport;

import android.os.Bundle;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.utils.InsetUtils;

/**
 * Activity to allow the user to choose the {@link android.app.backup.BackupTransport}.
 *
 * Set {@code config_backup_settings_intent} to {@code settings://com.android.settings.backup.transport} to activate.
 * Don't forget to also set {@code config_backup_settings_label} or else it won't be shown.
 */
public class TransportActivity extends SettingsActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        InsetUtils.applyWindowInsetsListener(findViewById(R.id.main_content));

        getSupportFragmentManager().beginTransaction()
            .replace(R.id.main_content, new TransportFragment())
            .commit();
    }

}
