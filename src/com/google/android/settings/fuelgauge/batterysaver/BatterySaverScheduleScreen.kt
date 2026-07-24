package com.google.android.settings.fuelgauge.batterysaver

import android.app.settings.SettingsEnums
import android.content.Context
import android.content.Intent
import com.android.settings.CatalystSettingsActivity
import com.android.settings.R
import com.android.settings.core.PreferenceScreenMixin
import com.android.settings.utils.makeLaunchIntent
import com.android.settingslib.metadata.PreferenceCategory
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.preferenceHierarchy
import kotlinx.coroutines.CoroutineScope

class BatterySaverScheduleScreen : PreferenceScreenMixin {

    override fun getMetricsCategory(): Int = SettingsEnums.FUELGAUGE_BATTERY_SAVER_SCHEDULE

    override fun hasCompleteHierarchy(): Boolean = false

    override fun isFlagEnabled(context: Context): Boolean = true

    override val key: String =
        com.android.settings.fuelgauge.batterysaver.BatterySaverSchedulePreferenceController
            .KEY_BATTERY_SAVER_SCHEDULE

    override val purpose: Int = R.string.battery_saver_schedule_purpose

    override val title: Int = R.string.battery_schedule_title

    override val summary: Int = R.string.battery_schedule_summary

    override val keywords: Int = R.string.keywords_battery_saver_schedule

    override val highlightMenuKey: Int = R.string.menu_key_battery

    override fun fragmentClass(): Class<out androidx.fragment.app.Fragment> =
        BatterySaverScheduleAndRemindersSettings::class.java

    override fun getPreferenceHierarchy(
        context: Context,
        coroutineScope: CoroutineScope,
    ): PreferenceHierarchy =
        preferenceHierarchy(context) {
            +PreferenceCategory(
                "battery_saver_reminder_entry",
                R.string.battery_saver_reminder_entry_purpose,
                R.string.battery_saver_reminder_category,
            ) order 90 +=
                {
                    +BatterySaverRemindersPreference()
                }
        }

    override fun getLaunchIntent(context: Context, metadata: PreferenceMetadata?): Intent =
        makeLaunchIntent(context, BatterySaverScheduleTrampoline::class.java, metadata?.key)
}

class BatterySaverScheduleTrampoline :
    CatalystSettingsActivity(
        "battery_saver_schedule",
        BatterySaverScheduleAndRemindersSettings::class.java,
    )
