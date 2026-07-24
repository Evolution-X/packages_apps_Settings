package com.google.android.settings.fuelgauge.batterysaver

import android.app.settings.SettingsEnums
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import androidx.preference.Preference
import com.android.settings.R
import com.android.settings.metrics.PreferenceActionMetricsProvider
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.metadata.BooleanValuePreference
import com.android.settingslib.metadata.PreferenceLifecycleContext
import com.android.settingslib.metadata.PreferenceLifecycleProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.preference.PreferenceBinding
import com.android.settingslib.preference.forEachRecursively
import com.android.settingslib.widget.SelectorWithWidgetPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BatterySaverModePreference(protected val dataStore: BatterySaverModeDataStore) :
    BooleanValuePreference,
    PreferenceBinding,
    Preference.OnPreferenceClickListener,
    SelectorWithWidgetPreference.OnClickListener,
    PreferenceLifecycleProvider {

    override val supportsWrite: Boolean = true

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int): Int =
        ReadWritePermit.ALLOW

    override val sensitivityLevel: Int = SensitivityLevel.NO_SENSITIVITY

    override fun getWritePermit(
        context: Context,
        value: Boolean?,
        callingPid: Int,
        callingUid: Int,
    ): Int = ReadWritePermit.ALLOW

    override fun onPreferenceClick(preference: Preference): Boolean = true

    override fun storage(context: Context): BatterySaverModeDataStore = dataStore

    override fun getReadPermissions(context: Context): Permissions = Permissions.EMPTY

    override fun getWritePermissions(context: Context): Permissions = Permissions.EMPTY

    override fun createWidget(context: Context): SelectorWithWidgetPreference =
        SelectorWithWidgetPreference(context)

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super<PreferenceBinding>.bind(preference, metadata)
        preference.isPersistent = false

        val selector = preference as SelectorWithWidgetPreference
        selector.isChecked = dataStore.getBoolean(key) == true
        selector.setOnPreferenceClickListener(this)
        selector.setOnClickListener(this)
    }

    override fun onPause(context: PreferenceLifecycleContext) {
        (context.findPreference<Preference>(key))?.isPersistent = true
    }

    override fun onRadioButtonClicked(clicked: SelectorWithWidgetPreference) {
        clicked.parent?.forEachRecursively { preference ->
            if (preference is SelectorWithWidgetPreference) {
                preference.isChecked = (preference == clicked)
            }
        }
    }
}

class BasicBatterySaverPreference(dataStore: BatterySaverModeDataStore) :
    BatterySaverModePreference(dataStore) {

    override val sensitivityLevel: Int = SensitivityLevel.NO_SENSITIVITY

    override val key: String = "basic_battery_saver"

    override val purpose: Int = R.string.basic_battery_saver_purpose

    override val title: Int = R.string.basic_battery_saver_title

    override val summary: Int = R.string.basic_battery_saver_summary
}

class ExtremeBatterySaverPreference(dataStore: BatterySaverModeDataStore) :
    BatterySaverModePreference(dataStore), PreferenceActionMetricsProvider, View.OnClickListener {

    override val preferenceActionMetrics: Int = SettingsEnums.ACTION_EXTREME_BATTERY_SAVER

    override val sensitivityLevel: Int = SensitivityLevel.NO_SENSITIVITY

    override val key: String = "extreme_battery_saver"

    override val purpose: Int = R.string.extreme_battery_saver_purpose

    override val title: Int = R.string.extreme_battery_saver_title

    override val summary: Int = R.string.extreme_battery_saver_summary

    override fun tags(context: Context): Array<String> =
        arrayOf("extreme_battery_saver", "mustpass_set")

    override fun intent(context: Context): Intent =
        Intent("android.settings.batterysaver.flipendo").setPackage("com.google.android.flipendo")

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
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

    override fun onStart(context: PreferenceLifecycleContext) {
        dataStore.refreshFlipendoStates(true)
    }

    override fun onPause(context: PreferenceLifecycleContext) {
        super.onPause(context)
        persistBatterySaverMode(context)
    }

    private fun persistBatterySaverMode(context: PreferenceLifecycleContext) {
        val preference =
            context.findPreference("extreme_battery_saver") as? SelectorWithWidgetPreference
                ?: return
        val isChecked = preference.isChecked
        if (isChecked == dataStore.getBoolean("extreme_battery_saver")) return

        CoroutineScope(Dispatchers.IO).launch {
            Log.i("BatterySaverMode", "Update extreme_battery_saver to $isChecked")
            dataStore.setBoolean("extreme_battery_saver", isChecked)
        }
    }
}
