package com.google.android.settings.update

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.android.settings.R
import com.android.settings.spa.SpaActivity.Companion.startSpaActivity
import com.android.settings.spa.preference.ComposePreferenceController
import com.android.settings.system.SystemUpdateRepository
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.ui.SettingsIcon
import com.android.settingslib.widget.SettingsThemeHelper

class SoftwareUpdateController(context: Context, key: String) :
    ComposePreferenceController(context, key) {

    private val systemUpdateRepository = SystemUpdateRepository(context)

    @Composable
    override fun Content() {
        val status by SoftwareUpdatePageProvider.rememberSystemUpdateStatus()
        val header = getUpdateHeader(status)

        Preference(
            model =
                object : PreferenceModel {
                    override val title = stringResource(R.string.software_update_entry_title)
                    override val summary = { header }
                    override val icon: @Composable () -> Unit = {
                        SettingsIcon(ImageVector.vectorResource(R.drawable.ic_system_update))
                    }
                    override val onClick = {
                        mContext.startSpaActivity(SoftwareUpdatePageProvider.name)
                    }
                }
        )
    }

    override fun getAvailabilityStatus(): Int {
        return if (
            SettingsThemeHelper.isExpressiveTheme(mContext) ||
                !SoftwareUpdateUtils.canShowSoftwareUpdateUi() ||
                systemUpdateRepository.getSystemUpdateIntent() == null
        ) {
            CONDITIONALLY_UNAVAILABLE
        } else {
            AVAILABLE
        }
    }
}
