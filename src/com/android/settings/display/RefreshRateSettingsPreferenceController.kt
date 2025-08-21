/*
 * Copyright (C) 2025 AxionOS Project
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
package com.android.settings.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.SystemProperties
import android.provider.Settings
import android.view.Display
import com.android.settings.R
import com.android.settings.core.BasePreferenceController

class RefreshRateSettingsPreferenceController(
    context: Context,
    preferenceKey: String
) : BasePreferenceController(context, preferenceKey) {

    private val displayManager =
        mContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private fun getModeMap(): Map<Int, String> {
        val modeMap = mutableMapOf<Int, String>()
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val refreshRates = display?.supportedModes
            ?.map { it.refreshRate.toInt() }
            ?.distinct()
            ?.sorted()
            ?: emptyList()

        if (refreshRates.isEmpty()) return emptyMap()

        val supportsDynamic = SystemProperties.getBoolean(
            "ro.surface_flinger.use_content_detection_for_refresh_rate",
            false
        )
        if (supportsDynamic) {
            modeMap[0] = mContext.getString(R.string.refresh_rate_dynamic)
        }
        refreshRates.forEach { hz ->
            modeMap[hz] = "${hz}Hz"
        }
        return modeMap
    }

    override fun getAvailabilityStatus(): Int {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val refreshRates = display?.supportedModes
            ?.map { it.refreshRate }
            ?.distinct()
            ?: emptyList()
        return if (refreshRates.size > 1) AVAILABLE else UNSUPPORTED_ON_DEVICE
    }

    override fun getSummary(): CharSequence {
        val modeValue = Settings.Global.getInt(
            mContext.contentResolver,
            "display_refresh_rate_mode",
            0
        )
        val modeMap = getModeMap()
        return modeMap[modeValue] ?: mContext.getString(R.string.refresh_rate_dynamic)
    }
}
