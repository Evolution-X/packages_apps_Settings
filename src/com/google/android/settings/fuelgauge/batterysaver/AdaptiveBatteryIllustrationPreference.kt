package com.google.android.settings.fuelgauge.batterysaver

import android.content.Context
import androidx.preference.Preference
import com.android.settings.R
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.preference.PreferenceBinding
import com.android.settingslib.widget.IllustrationPreference

class AdaptiveBatteryIllustrationPreference : PreferenceMetadata, PreferenceBinding {

    override val key: String = "adaptive_battery_illustration"

    override val purpose: Int = R.string.adaptive_battery_illustration_purpose

    override val indexable: Boolean = false

    override fun tags(context: Context): Array<String> = arrayOf("ui_only_preference")

    override fun createWidget(context: Context): IllustrationPreference {
        return IllustrationPreference(context).apply {
            setLottieAnimationResId(R.raw.auto_awesome_battery_expressive_lottie)
            applyDynamicColor()
        }
    }

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isSelectable = false
    }
}
