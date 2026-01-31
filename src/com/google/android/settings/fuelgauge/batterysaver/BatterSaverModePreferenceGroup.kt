package com.google.android.settings.fuelgauge.batterysaver

import androidx.preference.Preference
import com.android.settingslib.metadata.PreferenceGroup
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.preference.PreferenceBinding

class BatterSaverModePreferenceGroup : PreferenceGroup, PreferenceBinding {

    override val key: String
        get() = "battery_saver_group"

    override fun bind(preference: Preference, preferenceMetadata: PreferenceMetadata) {
        preference.setLayoutResource(
                    com.android.settingslib.widget.category.R.layout
                            .settingslib_expressive_untitled_preference_category)
        super.bind(preference, preferenceMetadata)
    }
}
