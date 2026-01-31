package com.google.android.settings.fuelgauge.batterysaver

import android.app.settings.SettingsEnums.ACTION_ADAPTIVE_BATTERY
import android.content.Context
import com.android.settings.metrics.PreferenceActionMetricsProvider
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.KeyValueStoreDelegate
import com.android.settingslib.datastore.SettingsGlobalStore
import com.android.settingslib.metadata.BooleanValuePreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.widget.MainSwitchPreferenceBinding
import com.android.settings.R

class AdaptiveBatteryPreference(
    private val dataStore: KeyValueStore
) : BooleanValuePreference,
    MainSwitchPreferenceBinding,
    PreferenceActionMetricsProvider,
    PreferenceAvailabilityProvider {

    companion object {
        fun getAdaptiveBatteryDataStore(context: Context): KeyValueStore {
            val store = SettingsGlobalStore.get(context)
            store.setDefaultValue(
                "adaptive_battery_management_enabled",
                true
            )
            return store
        }
    }

    override val key: String
        get() = "adaptive_battery_management_enabled"

    override val title: Int
        get() = R.string.adaptive_battery_switch_title

    override val preferenceActionMetrics: Int
        get() = ACTION_ADAPTIVE_BATTERY

    override fun getReadPermit(context: Context, myUid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override val sensitivityLevel
        get() = SensitivityLevel.NO_SENSITIVITY

    override fun getWritePermit(context: Context, value: Boolean?, myUid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override fun tags(context: Context) = arrayOf("adaptive_battery")

    override fun storage(context: Context): KeyValueStore =
        AdaptiveBatteryStore(SettingsGlobalStore.get(context))

    override fun isAvailable(context: Context) =
        AdaptiveBatteryExpandPreferenceGroup.isAdaptiveBatteryAvailable(context)

    override fun getReadPermissions(context: Context) = SettingsGlobalStore.getReadPermissions()

    override fun getWritePermissions(context: Context) = SettingsGlobalStore.getWritePermissions()

    inner class AdaptiveBatteryStore(private val settingsStore: KeyValueStore) :
        KeyValueStoreDelegate {
        override val keyValueStoreDelegate
            get() = this.settingsStore

        override fun <T : Any> getDefaultValue(key: String, valueType: Class<T>) = true as T
    }
}
