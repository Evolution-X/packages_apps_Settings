/*
 * Copyright (C) 2019-2023 crDroid Android Project
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
import android.content.Intent
import android.os.Build
import android.os.SystemProperties
import androidx.preference.Preference
import com.android.settings.R
import com.android.settings.contract.TAG_DEVICE_STATE_PREFERENCE
import com.android.settings.deviceinfo.DeviceNameUtils
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.preference.PreferenceBinding

/** "Market name | codename" entry on the Android version screen. */
class AboutDeviceNamePreference :
    PreferenceMetadata, PreferenceSummaryProvider, PreferenceBinding {

    override val key: String
        get() = KEY

    override val purpose: Int
        get() = R.string.device_name_purpose

    override val title: Int
        get() = R.string.device_name

    override val indexable
        get() = false

    override fun tags(context: Context) = arrayOf(TAG_DEVICE_STATE_PREFERENCE)

    // Tapping the entry launches the device changelog (nothing swallows the click).
    override fun intent(context: Context): Intent? =
        Intent().setClassName(
            "com.android.settings",
            "org.evolution.settings.fragments.about.ChangelogActivity",
        )

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isCopyingEnabled = true
    }

    override fun getSummary(context: Context): CharSequence {
        val fallback = context.getString(R.string.device_info_default)

        var manufacturer =
            DeviceNameUtils.sanitize(SystemProperties.get(BRAND_NAME_PROPERTY, Build.MANUFACTURER))
        if (manufacturer.isEmpty()) manufacturer = DeviceNameUtils.sanitize(fallback)

        var codename =
            DeviceNameUtils.sanitize(SystemProperties.get(DEVICE_NAME_PROPERTY, Build.DEVICE))
        if (codename.isEmpty()) codename = DeviceNameUtils.sanitize(fallback)

        var marketName = DeviceNameUtils.sanitize(SystemProperties.get(MARKET_NAME_PROPERTY, ""))
        if (marketName.isEmpty()) marketName = DeviceNameUtils.sanitize(Build.MODEL)
        if (marketName.isEmpty()) marketName = fallback

        val displayName = DeviceNameUtils.prefixIfNeeded(manufacturer, marketName)
        return "$displayName | $codename"
    }

    companion object {
        const val KEY = "about_device_name"
        private const val MARKET_NAME_PROPERTY = "ro.product.marketname"
        private const val BRAND_NAME_PROPERTY = "ro.product.manufacturer"
        private const val DEVICE_NAME_PROPERTY = "ro.product.device"
    }
}
