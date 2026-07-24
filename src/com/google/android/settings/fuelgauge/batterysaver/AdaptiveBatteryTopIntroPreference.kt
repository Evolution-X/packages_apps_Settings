package com.google.android.settings.fuelgauge.batterysaver

import android.content.Context
import com.android.settings.R
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.preference.PreferenceBinding
import com.android.settingslib.widget.TopIntroPreference

class AdaptiveBatteryTopIntroPreference :
    PreferenceMetadata, PreferenceBinding, PreferenceAvailabilityProvider {

    override val availabilityDescription: String = "ui_only_preference"

    override val key: String = "adaptive_battery_top_intro"

    override val purpose: Int = R.string.adaptive_battery_top_intro_purpose

    override val title: Int = R.string.smart_battery_summary

    override val indexable: Boolean = false

    override fun tags(context: Context): Array<String> = arrayOf("ui_only_preference")

    override fun createWidget(context: Context): TopIntroPreference {
        return TopIntroPreference(context)
    }

    override fun getAvailabilityStability(): PreconditionStability =
        PreconditionStability.STABLE_UNTIL_APK_UPDATE

    override fun isAvailable(context: Context): Boolean {
        return AdaptiveBatteryScreen.isAdaptiveBatteryAvailable(context)
    }
}
