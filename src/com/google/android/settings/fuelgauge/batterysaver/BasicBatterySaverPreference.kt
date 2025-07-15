package com.google.android.settings.fuelgauge.batterysaver

import com.android.settings.R

class BasicBatterySaverPreference(batterySaverModeDataStore: BatterySaverModeDataStore) :
    BatterySaverModePreference(batterySaverModeDataStore) {
    companion object {}

    override val key: String
        get() = "basic_battery_saver"

    override val title: Int
        get() = R.string.basic_battery_saver_title

    override val summary: Int
        get() = R.string.basic_battery_saver_summary
}
