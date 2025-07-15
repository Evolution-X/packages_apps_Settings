package com.google.android.settings.fuelgauge.batterysaver

import android.content.Context
import com.android.settings.fuelgauge.batterysaver.BatterySaverPreference
import com.android.settings.fuelgauge.batterysaver.BatterySaverScreen
import com.android.settingslib.metadata.PreferenceGroup
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.preferenceHierarchy

class BatterySaverGoogleScreen : BatterySaverScreen() {
    companion object {}

    override fun getPreferenceHierarchy(context: Context): PreferenceHierarchy {
        return preferenceHierarchy(context, this) {
            (+BatterySaverPreference()).order(10)

            (+(BatterSaverModePreferenceGroup() as PreferenceGroup)).order(30).apply {
                val batterySaverModeDataStore = BatterySaverModeDataStore(context)
                +BasicBatterySaverPreference(batterySaverModeDataStore)
                +ExtremeBatterySaverPreference(batterySaverModeDataStore)
            }

            (+(AdaptiveBatteryExpandPreferenceGroup() as PreferenceGroup)).order(70).apply {
                (+AdaptiveBatteryTopIntroPreference()).order(
                    androidx.constraintlayout.widget.R.styleable
                        .ConstraintLayout_Layout_layout_goneMarginStart
                )
                (+AdaptiveBatteryPreference()).order(130)
            }
        }
    }
}
