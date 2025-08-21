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
import android.os.Bundle
import android.provider.Settings
import android.os.SystemProperties
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.preference.PreferenceCategory
import com.android.settings.preferences.BasePreferenceFragment
import com.android.settings.R
import org.evolution.settings.preferences.RadioButtonPreference

class RefreshRateSettings : BasePreferenceFragment(R.xml.refresh_rate_settings),
    RadioButtonPreference.OnRadioButtonClickedListener {

    private lateinit var radioPrefs: MutableList<RadioButtonPreference>
    private val cr by lazy { requireContext().contentResolver }

    private val modeMap = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupModes()
        setupRadioButtons()
        refreshRadios()
    }

    private fun setupModes() {
        val displayManager = requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val refreshRates = display.supportedModes.map { it.refreshRate.toInt() }.distinct().sorted()
        if (refreshRates.isEmpty()) return
        val supportsDynamic = SystemProperties.getBoolean(
            "ro.surface_flinger.use_content_detection_for_refresh_rate", false
        )
        if (supportsDynamic) {
            modeMap["refresh_rate_dynamic"] = 0
        }
        refreshRates.forEach { hz ->
            modeMap["refresh_rate_$hz"] = hz
        }
    }

    private fun setupRadioButtons() {
        val category = findPreference<PreferenceCategory>("refresh_rate_category") ?: return
        radioPrefs = mutableListOf()
        modeMap.forEach { (key, value) ->
            val radio = RadioButtonPreference(requireContext()).apply {
                this.key = key
                title = if (value == 0) {
                    getString(R.string.refresh_rate_dynamic)
                } else {
                    "${value}Hz"
                }
                summary = null
                setOnRadioButtonClickedListener(this@RefreshRateSettings)
            }
            radioPrefs.add(radio)
            category.addPreference(radio)
        }
    }

    override fun onRadioButtonClicked(pref: RadioButtonPreference) {
        val modeValue = modeMap[pref.key] ?: return
        Settings.Global.putInt(cr, "display_refresh_rate_mode", modeValue)
        refreshRadios()
    }

    private fun refreshRadios() {
        val currentMode = Settings.Global.getInt(cr, "display_refresh_rate_mode", 0)
        radioPrefs.forEach { radio ->
            val modeValue = modeMap[radio.key] ?: 0
            radio.isSelected = currentMode == modeValue
        }
    }
}
