package com.google.android.settings.update

import android.content.Context
import android.os.SystemUpdateManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.android.settings.R
import com.android.settings.core.BasePreferenceController
import com.android.settingslib.DeviceInfoUtils
import com.android.settingslib.widget.SettingsThemeHelper
import com.android.settingslib.widget.StatusBannerPreference

class SystemUpdatePreferenceController(context: Context, preferenceKey: String) :
    BasePreferenceController(context, preferenceKey), DefaultLifecycleObserver {

    private var mPreferenceScreen: PreferenceScreen? = null

    override fun getAvailabilityStatus(): Int {
        return if (SettingsThemeHelper.isExpressiveTheme(mContext)) {
            AVAILABLE
        } else {
            CONDITIONALLY_UNAVAILABLE
        }
    }

    override fun displayPreference(screen: PreferenceScreen) {
        super.displayPreference(screen)
        updateUi(screen)
    }

    override fun onResume(owner: LifecycleOwner) {
        updateUi(mPreferenceScreen)
    }

    private fun updateUi(screen: PreferenceScreen?) {
        if (screen == null) {
            Log.d(TAG, "No PreferenceScreen.")
            return
        }
        mPreferenceScreen = screen
        updateSystemPreference(screen.findPreference(KEY_SYSTEM_UPDATE_PREFERENCE))
        updateStatusBanner(screen.findPreference(KEY_STATUS_BANNER))
    }

    private fun updateSystemPreference(preference: Preference?) {
        if (preference == null) {
            Log.d(TAG, "Can not update system preference due to no preference.")
            return
        }

        var summary = preference.context.getString(R.string.software_update_pending_update_summary)
        var icon =
            com.android.settingslib.widget.theme.R.drawable.settingslib_expressive_icon_level_medium

        if (!Companion.isSystemUpdatable(preference.context)) {
            val securityPatch = DeviceInfoUtils.getSecurityPatch()
            summary =
                if (securityPatch != null) {
                    preference.context.getString(
                        R.string.software_update_up_to_specific_date_summary,
                        securityPatch,
                    )
                } else {
                    ""
                }
            icon =
                com.android.settingslib.widget.theme.R.drawable
                    .settingslib_expressive_icon_level_low
        }

        preference.setIcon(icon)
        preference.summary = summary
    }

    private fun updateStatusBanner(preference: Preference?) {
        if (preference == null) {
            Log.d(TAG, "Can not update banner due to no preference.")
            return
        }

        var title = R.string.software_update_banner_up_to_date
        var icon = R.drawable.software_update_banner_up_to_date
        var status = StatusBannerPreference.BannerStatus.LOW

        if (Companion.isSystemUpdatable(preference.context)) {
            title = R.string.software_update_banner_update_available
            icon = R.drawable.software_update_banner_pending_update
            status = StatusBannerPreference.BannerStatus.MEDIUM
        }

        preference.setTitle(title)
        preference.setIcon(icon)
        (preference as StatusBannerPreference).iconLevel = status
    }

    companion object {
        const val KEY_SYSTEM_UPDATE_PREFERENCE = "key_system_update_preference"
        const val KEY_STATUS_BANNER = "key_status_banner"
        private const val TAG = "SoftwareUpdatePreferencesController"

        fun isSystemUpdatable(context: Context): Boolean {
            val manager = context.getSystemService(SystemUpdateManager::class.java)
            if (manager == null) {
                Log.d(TAG, "Can not get SystemUpdateManager.")
                return false
            }

            val status =
                try {
                    manager.retrieveSystemUpdateInfo().getInt("status")
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting system update info. $e")
                    0
                }
            return status == 2 || status == 3 || status == 4 || status == 5
        }
    }
}
