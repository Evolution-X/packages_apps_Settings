package com.google.android.settings.fuelgauge.batterysaver

import androidx.preference.Preference
import com.android.settingslib.metadata.PreferenceGroup
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.preference.PreferenceBinding

class BatterSaverModePreferenceGroup : PreferenceGroup, PreferenceBinding {

    override val key: String
        get() = "battery_saver_group"

    override fun bind(preference: Preference, preferenceMetadata: PreferenceMetadata) {
        preference.setLayoutResource(com.android.settings.R.layout.preference_category_no_label)
        super.bind(preference, preferenceMetadata)
    }
}
