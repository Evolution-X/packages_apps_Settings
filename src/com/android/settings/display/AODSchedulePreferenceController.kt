/*
 * Copyright (C) 2021 Yet Another AOSP Project
 * Copyright (C) 2022 FlamingoOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.settings.display

import android.content.Context
import android.hardware.display.AmbientDisplayConfiguration
import android.os.SystemProperties
import android.os.UserHandle
import android.provider.Settings

import com.android.settings.R
import com.android.settings.core.BasePreferenceController

class AODSchedulePreferenceController(
    context: Context,
    key: String,
) : BasePreferenceController(context, key) {

    private var ambientDisplayConfig: AmbientDisplayConfiguration? = null

    override fun getAvailabilityStatus() =
        if (isAODAvailable()) AVAILABLE else UNSUPPORTED_ON_DEVICE

    override fun getSummary(): CharSequence {
        val mode = Settings.Secure.getIntForUser(mContext.contentResolver,
                Settings.Secure.DOZE_ALWAYS_ON_AUTO_MODE, 0, UserHandle.USER_CURRENT)
        return when (mode) {
            MODE_NIGHT -> mContext.getString(R.string.night_display_auto_mode_twilight)
            MODE_TIME -> mContext.getString(R.string.night_display_auto_mode_custom)
            MODE_MIXED_SUNSET -> mContext.getString(R.string.always_on_display_schedule_mixed_sunset)
            MODE_MIXED_SUNRISE -> mContext.getString(R.string.always_on_display_schedule_mixed_sunrise)
            else -> mContext.getString(R.string.string_disabled)
        }
    }

    fun setConfig(config: AmbientDisplayConfiguration): AODSchedulePreferenceController {
        ambientDisplayConfig = config
        return this
    }

    private fun getConfig(): AmbientDisplayConfiguration {
        if (ambientDisplayConfig == null) {
            ambientDisplayConfig = AmbientDisplayConfiguration(mContext)
        }
        return ambientDisplayConfig!!
    }

    private fun isAODAvailable(): Boolean {
        return getConfig().alwaysOnAvailableForUser(UserHandle.myUserId()) &&
            !SystemProperties.getBoolean(PROP_AWARE_AVAILABLE, false)
    }

    companion object {
        const val MODE_DISABLED = 0
        const val MODE_NIGHT = 1
        const val MODE_TIME = 2
        const val MODE_MIXED_SUNSET = 3
        const val MODE_MIXED_SUNRISE = 4

        private const val PROP_AWARE_AVAILABLE = "ro.vendor.aware_available"
    }
}
