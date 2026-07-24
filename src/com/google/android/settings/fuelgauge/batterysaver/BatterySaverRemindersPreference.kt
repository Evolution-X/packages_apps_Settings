package com.google.android.settings.fuelgauge.batterysaver

import android.content.Context
import com.android.settings.R
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.datastore.SettingsGlobalStore
import com.android.settingslib.datastore.SettingsSystemStore
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.SwitchPreference

class BatterySaverRemindersPreference :
    SwitchPreference(
        "low_power_mode_reminder_enabled",
        R.string.low_power_mode_reminder_enabled_purpose,
        R.string.battery_saver_reminder_switch_title,
        R.string.battery_saver_reminder_switch_summary,
    ) {

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int): Int =
        ReadWritePermit.ALLOW

    override val sensitivityLevel: Int = SensitivityLevel.NO_SENSITIVITY

    override fun getWritePermit(
        context: Context,
        value: Boolean?,
        callingPid: Int,
        callingUid: Int,
    ): Int = ReadWritePermit.ALLOW

    override fun storage(context: Context): KeyValueStore =
        SettingsGlobalStore.get(context).apply {
            setDefaultValue("low_power_mode_reminder_enabled", true)
        }

    override fun tags(context: Context): Array<String> = arrayOf("battery_saver_reminders")

    override fun getReadPermissions(context: Context): Permissions =
        SettingsSystemStore.getReadPermissions()

    override fun getWritePermissions(context: Context): Permissions =
        SettingsSystemStore.getWritePermissions()
}
