package com.google.android.settings.fuelgauge.batterysaver

import android.app.settings.SettingsEnums.ACTION_EXTREME_BATTERY_SAVER
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import androidx.preference.Preference
import com.android.settings.metrics.PreferenceActionMetricsProvider
import com.android.settingslib.metadata.PreferenceLifecycleContext
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settings.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExtremeBatterySaverPreference(batterySaverModeDataStore: BatterySaverModeDataStore) :
    BatterySaverModePreference(batterySaverModeDataStore),
    PreferenceActionMetricsProvider,
    View.OnClickListener {

    companion object {}

    override val preferenceActionMetrics: Int
        get() = ACTION_EXTREME_BATTERY_SAVER

    override val key: String
        get() = "extreme_battery_saver"

    override val title: Int
        get() = R.string.extreme_battery_saver_title

    override val summary: Int
        get() = R.string.extreme_battery_saver_summary

    override fun tags(context: Context) = arrayOf("extreme_battery_saver")

    override fun intent(context: Context): Intent =
        Intent("android.settings.batterysaver.flipendo").setPackage("com.google.android.flipendo")

    override fun bind(preference: Preference, preferenceMetadata: PreferenceMetadata) {
        super.bind(preference, preferenceMetadata)
        (preference as SelectorWithWidgetPreference).setExtraWidgetOnClickListener(this)
    }

    override fun onClick(view: View) {
        try {
            val context = view.context
            context.startActivity(intent(context))
        } catch (e: Exception) {
            Log.e("BatterySaverMode", "launch Flipendo failed", e)
        }
    }

    override fun onStart(preferenceLifecycleContext: PreferenceLifecycleContext) {
        dataStoreProperty.refreshFlipendoStates(true)
    }

    override fun onPause(preferenceLifecycleContext: PreferenceLifecycleContext) {
        super.onPause(preferenceLifecycleContext)
        persistBatterySaverMode(preferenceLifecycleContext)
    }

    private fun persistBatterySaverMode(preferenceLifecycleContext: PreferenceLifecycleContext) {
        val selectorWithWidgetPreference =
            preferenceLifecycleContext.findPreference("extreme_battery_saver")
                as? SelectorWithWidgetPreference
        if (selectorWithWidgetPreference == null) {
            return
        }
        val isChecked = selectorWithWidgetPreference.isChecked
        if (isChecked == dataStoreProperty.getBoolean("extreme_battery_saver")) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            Log.i("BatterySaverMode", "Update extreme_battery_saver to $isChecked")
            dataStoreProperty.setBoolean("extreme_battery_saver", isChecked)
        }
    }
}
