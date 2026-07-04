package com.google.android.settings.update

import android.os.SystemProperties

object SoftwareUpdateUtils {
    fun canShowSoftwareUpdateUi(): Boolean =
        SystemProperties.getBoolean("software_update_preference_visibility", true)
}
