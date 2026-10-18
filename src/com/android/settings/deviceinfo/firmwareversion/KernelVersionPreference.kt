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

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import android.util.Log
import androidx.preference.Preference
import com.android.settings.R
import com.android.settingslib.DeviceInfoUtils
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.preference.PreferenceBinding
import java.io.File
import java.io.IOException

// LINT.IfChange
class KernelVersionPreference :
    PersistentPreference<String>,
    PreferenceMetadata,
    PreferenceSummaryProvider,
    PreferenceBinding,
    Preference.OnPreferenceClickListener {

    override val key: String
        get() = "kernel_version"

    override val purpose: Int
        get() = R.string.kernel_version_purpose

    override val title: Int
        get() = R.string.kernel_version

    override val supportsWrite = false

    override val valueType = String::class.javaObjectType

    override fun storage(context: Context): KeyValueStore = createSummaryStorage(context, key)

    override fun getSummary(context: Context): CharSequence? =
        DeviceInfoUtils.getFormattedKernelVersion(context)

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isCopyingEnabled = true
        preference.onPreferenceClickListener = this
    }

    private var showingFullVersion = false

    // Tap to toggle between the formatted and the unformatted /proc/version
    override fun onPreferenceClick(preference: Preference): Boolean {
        showingFullVersion = !showingFullVersion
        preference.summary =
            if (showingFullVersion) getFullKernelVersion()
            else DeviceInfoUtils.getFormattedKernelVersion(preference.context)
        return true
    }

    private fun getFullKernelVersion(): String =
        try {
            File(FILENAME_PROC_VERSION).bufferedReader(Charsets.UTF_8, 256).use { it.readLine() }
                ?: "Unavailable"
        } catch (e: IOException) {
            Log.e(TAG, "IO Exception when getting kernel version for Device Info screen", e)
            "Unavailable"
        }

    override val sensitivityLevel
        get() = SensitivityLevel.NO_SENSITIVITY

    companion object {
        private const val TAG = "KernelVersionPreference"
        private const val FILENAME_PROC_VERSION = "/proc/version"
    }
}
// LINT.ThenChange(KernelVersionPreferenceController.java)
