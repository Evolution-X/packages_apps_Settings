/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.deviceinfo.aboutphone

import android.app.settings.SettingsEnums
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.fragment.app.Fragment
import com.android.settings.R
import com.android.settings.Settings.MyDeviceInfoActivity
import com.android.settings.core.PreferenceScreenMixin
import com.android.settings.deviceinfo.firmwareversion.FirmwareVersionScreen
import com.android.settings.deviceinfo.hardwareinfo.HardwareInfoScreen
import com.android.settings.deviceinfo.imei.ImeiPreference
import com.android.settings.deviceinfo.imei.getImeiList
import com.android.settings.deviceinfo.simstatus.SimEidPreference
import com.android.settings.flags.Flags
import com.android.settings.utils.makeLaunchIntent
import com.android.settings.wifi.utils.activeModemCount
import com.android.settingslib.metadata.PreferenceCategory
import com.android.settingslib.metadata.PreferenceIconProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.ProvidePreferenceScreen
import com.android.settingslib.metadata.UI_ONLY_PREFERENCE
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.widget.SettingsThemeHelper.isExpressiveTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreen.Companion.APP_FUNCTION_UNCATEGORIZED

// LINT.IfChange
@ProvidePreferenceScreen(MyDeviceInfoScreen.KEY)
open class MyDeviceInfoScreen :
    PreferenceScreenMixin, PreferenceSummaryProvider, PreferenceIconProvider {
    override fun tags(context: Context) = arrayOf(APP_FUNCTION_UNCATEGORIZED)

    override val key: String
        get() = KEY

    override val purpose: Int
        get() = R.string.my_device_info_pref_screen_purpose

    override val title: Int
        get() = R.string.about_settings

    override fun getSummary(context: Context): CharSequence? {
        return Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?: Build.MODEL
    }

    override fun getIcon(context: Context) =
        when {
            isExpressiveTheme(context) -> R.drawable.ic_homepage_about
            else -> R.drawable.ic_settings_about_device_filled
        }

    override val highlightMenuKey: Int
        get() = R.string.menu_key_about_device

    override fun getMetricsCategory() = SettingsEnums.DEVICEINFO

    override fun isFlagEnabled(context: Context) = Flags.catalystMyDeviceInfoPrefScreen()

    override fun fragmentClass(): Class<out Fragment>? = MyDeviceInfoFragment::class.java

    override fun getLaunchIntent(context: Context, metadata: PreferenceMetadata?): Intent? =
        makeLaunchIntent(context, MyDeviceInfoActivity::class.java, metadata?.key)

    override fun getPreferenceHierarchy(context: Context, coroutineScope: CoroutineScope) =
        preferenceHierarchy(context) {
            +PreferenceCategory(
                key = DEVICE_DETAIL_CATEGORY,
                purpose = R.string.device_detail_category_purpose,
                title = R.string.my_device_info_device_details_category_title,
            ) +=
                {
                    +HardwareInfoScreen.KEY order 30
                    addAsync(coroutineScope, Dispatchers.Default) {
                        +SimEidPreference(context) order 31
                    }
                    val activeModemCount = context.activeModemCount
                    val imeiList = context.getImeiList
                    for (i in 0 until activeModemCount) {
                        +ImeiPreference(context, i, activeModemCount, imeiList) order (i + 33)
                    }
                    +FirmwareVersionScreen.KEY order 42
                }
        }

    override fun hasCompleteHierarchy() = false

    companion object {
        const val KEY = "my_device_info_pref_screen"

        internal const val DEVICE_DETAIL_CATEGORY = "device_detail_category"
    }
}
// LINT.ThenChange(MyDeviceInfoFragment.java, MyDeviceInfoApiFirstScreen.kt)
