package com.google.android.settings.fuelgauge.batterysaver

import android.content.Context
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.preference.PreferenceBinding
import com.android.settingslib.widget.TopIntroPreference
import com.android.settings.R

class AdaptiveBatteryTopIntroPreference :
    PreferenceMetadata, PreferenceBinding, PreferenceAvailabilityProvider {

    companion object {}

    override val key: String
        get() = "adaptive_battery_top_intro"

    override val title: Int
        get() = R.string.smart_battery_summary

    override fun createWidget(context: Context): TopIntroPreference =
        TopIntroPreference(context, null, 0, 0)

    override fun isAvailable(context: Context): Boolean =
        AdaptiveBatteryExpandPreferenceGroup.isAdaptiveBatteryAvailable(context)
}
