package com.google.android.settings.update

import android.content.Context
import com.android.settings.R
import com.android.settings.core.BasePreferenceController
import com.android.settingslib.widget.SettingsThemeHelper

class SoftwareUpdateController2(context: Context, key: String) :
    BasePreferenceController(context, key) {

    override fun getAvailabilityStatus(): Int {
        return if (SettingsThemeHelper.isExpressiveTheme(mContext)) {
            AVAILABLE
        } else {
            CONDITIONALLY_UNAVAILABLE
        }
    }

    override fun getSummary(): CharSequence {
        return if (SystemUpdatePreferenceController.isSystemUpdatable(mContext)) {
            mContext.getString(R.string.software_update_can_be_updated_header)
        } else {
            mContext.getString(R.string.software_update_up_to_date_header)
        }
    }
}
