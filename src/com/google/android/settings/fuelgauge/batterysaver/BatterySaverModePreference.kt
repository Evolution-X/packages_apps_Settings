package com.google.android.settings.fuelgauge.batterysaver

import android.content.Context
import androidx.preference.Preference
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

abstract class BatterySaverModePreference(private val dataStore: BatterySaverModeDataStore) :
    BooleanValuePreference,
    PreferenceBinding,
    Preference.OnPreferenceClickListener,
    SelectorWithWidgetPreference.OnClickListener,
    PreferenceLifecycleProvider {

    protected val dataStoreProperty: BatterySaverModeDataStore
        get() = dataStore

    override fun getReadPermit(context: Context, myUid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override val sensitivityLevel
        get() = SensitivityLevel.NO_SENSITIVITY

    override fun getWritePermit(context: Context, value: Boolean?, myUid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override fun onPreferenceClick(preference: Preference): Boolean {
        return true
    }

    override fun storage(context: Context): BatterySaverModeDataStore = this.dataStore

    override fun getReadPermissions(context: Context) = Permissions.EMPTY

    override fun getWritePermissions(context: Context) = Permissions.EMPTY

    override fun createWidget(context: Context): SelectorWithWidgetPreference =
        SelectorWithWidgetPreference(context)

    override fun bind(preference: Preference, preferenceMetadata: PreferenceMetadata) {
        super<PreferenceBinding>.bind(preference, preferenceMetadata)
        preference.isPersistent = false

        val selectorWithWidgetPreference = preference as SelectorWithWidgetPreference
        selectorWithWidgetPreference.isChecked = dataStore.getBoolean(key) == true
        selectorWithWidgetPreference.onPreferenceClickListener = this
        selectorWithWidgetPreference.setOnClickListener(this)
    }

    override fun onPause(preferenceLifecycleContext: PreferenceLifecycleContext) {
        val preference = preferenceLifecycleContext.findPreference(key) as? Preference
        preference?.isPersistent = true
    }

    override fun onRadioButtonClicked(selectorWithWidgetPreference: SelectorWithWidgetPreference) {
        val parent = selectorWithWidgetPreference.parent
        parent?.forEachRecursively { preference ->
            if (preference is SelectorWithWidgetPreference) {
                preference.isChecked = (preference == selectorWithWidgetPreference)
            }
        }
    }
}
