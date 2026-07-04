package com.google.android.settings.update

import android.app.settings.SettingsEnums
import android.content.Context
import com.android.settings.R
import com.android.settings.dashboard.DashboardFragment
import com.android.settings.search.BaseSearchIndexProvider

class SoftwareUpdateFragment : DashboardFragment() {

    override fun getLogTag(): String = "SoftwareUpdate"

    override fun getMetricsCategory(): Int = SettingsEnums.SETTINGS_SOFTWARE_UPDATES

    override fun getPreferenceScreenBindingKey(context: Context): String =
        "software_update_settings_v2"

    override fun getPreferenceScreenResId(): Int = R.xml.software_update

    companion object {
        @JvmField
        val SEARCH_INDEX_DATA_PROVIDER: BaseSearchIndexProvider =
            BaseSearchIndexProvider(R.xml.software_update)
    }
}
