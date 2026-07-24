package com.google.android.settings.fuelgauge.batterysaver

import android.app.settings.SettingsEnums
import android.content.Context
import com.android.settings.R
import com.android.settings.metrics.PreferenceActionMetricsProvider
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.datastore.SettingsGlobalStore
import com.android.settingslib.metadata.BooleanValuePreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.widget.MainSwitchPreferenceBinding

class AdaptiveBatteryPreference(private val dataStore: KeyValueStore) :
    BooleanValuePreference,
    MainSwitchPreferenceBinding,
    PreferenceActionMetricsProvider,
    PreferenceAvailabilityProvider {

    override val key: String = "adaptive_battery_management_enabled"

    override val purpose: Int = R.string.adaptive_battery_management_enabled_purpose

    override val title: Int = R.string.adaptive_battery_switch_title

    override fun tags(context: Context): Array<String> = arrayOf("adaptive_battery")

    override fun storage(context: Context): KeyValueStore = dataStore

    override val availabilityDescription: String = "The device must support adaptive battery."

    override fun getAvailabilityStability(): PreconditionStability =
        PreconditionStability.STABLE_UNTIL_APK_UPDATE

    override fun isAvailable(context: Context): Boolean =
        AdaptiveBatteryScreen.isAdaptiveBatteryAvailable(context)

    override fun getReadPermissions(context: Context): Permissions =
        SettingsGlobalStore.getReadPermissions()

    override fun getWritePermissions(context: Context): Permissions =
        SettingsGlobalStore.getWritePermissions()

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int): Int =
        ReadWritePermit.ALLOW

    override fun getWritePermit(
        context: Context,
        value: Boolean?,
        callingPid: Int,
        callingUid: Int,
    ): Int = ReadWritePermit.ALLOW

    override val supportsWrite: Boolean = true

    override val preferenceActionMetrics: Int = SettingsEnums.ACTION_ADAPTIVE_BATTERY

    override val sensitivityLevel: Int = SensitivityLevel.NO_SENSITIVITY

    companion object {
        fun getAdaptiveBatteryDataStore(context: Context): KeyValueStore {
            return SettingsGlobalStore.get(context).also { store ->
                store.setDefaultValue("adaptive_battery_management_enabled", true)
            }
        }
    }
}
