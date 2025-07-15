package com.google.android.settings.fuelgauge.batterysaver

import android.app.Activity
import android.content.Context
import androidx.preference.Preference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceGroup
import com.android.settingslib.metadata.PreferenceLifecycleContext
import com.android.settingslib.metadata.PreferenceLifecycleProvider
import com.android.settingslib.preference.PreferenceBinding
import com.android.settingslib.preference.forEachRecursively
import com.android.settings.R

class AdaptiveBatteryExpandPreferenceGroup :
    PreferenceGroup,
    PreferenceBinding,
    PreferenceAvailabilityProvider,
    PreferenceLifecycleProvider {

    override val key: String
        get() = "battery_saver_expand_entry"

    override val title: Int
        get() = R.string.smart_battery_title

    override fun createWidget(context: Context): ExpandDividerPreference =
        ExpandDividerPreference(context, null)

    override fun isAvailable(context: Context): Boolean = isAdaptiveBatteryAvailable(context)

    override fun onCreate(preferenceLifecycleContext: PreferenceLifecycleContext) {
        val expandDividerPreference =
            preferenceLifecycleContext.findPreference<ExpandDividerPreference>(key)!!
        initExpandedState(expandDividerPreference)

        val listener =
            ExpandDividerPreference.OnExpandListener { isExpanded ->
                expandDividerPreference.forEachRecursively { preference: Preference ->
                    if (preference.key != "battery_saver_expand_entry") {
                        preference.isVisible = expandDividerPreference.isVisible && isExpanded
                    }
                }
            }
        expandDividerPreference.setOnExpandListener(listener)
        listener.onExpand(expandDividerPreference.isExpended())
    }

    private fun initExpandedState(expandDividerPreference: ExpandDividerPreference) {
        val context = expandDividerPreference.context
        val intentExtra =
            (context as? Activity)?.intent?.getStringExtra(":settings:fragment_args_key")
        expandDividerPreference.setExpanded(intentExtra == "adaptive_battery_management_enabled")
    }

    companion object {
        fun isAdaptiveBatteryAvailable(context: Context): Boolean =
            context.resources.getBoolean(com.android.internal.R.bool.config_swipeDisambiguation)
    }
}
