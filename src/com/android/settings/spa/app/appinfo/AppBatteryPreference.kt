/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settings.spa.app.appinfo

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.settings.R
import com.android.settings.Utils
import com.android.settings.core.SubSettingLauncher
import com.android.settings.fuelgauge.AdvancedPowerUsageDetail
import com.android.settings.fuelgauge.BatteryOptimizeUtils
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spaprivileged.model.app.installed
import com.android.settingslib.spaprivileged.model.app.userHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppBatteryPreference(app: ApplicationInfo) {
    val context = LocalContext.current
    val presenter = remember(app) { AppBatteryPresenter(context, app) }
    if (!presenter.isAvailable()) return

    Preference(object : PreferenceModel {
        override val title = stringResource(R.string.battery_details_title)
        override val summary = presenter.summary
        override val onClick = presenter::startActivity
    })

    presenter.Updater()
}

private class AppBatteryPresenter(private val context: Context, private val app: ApplicationInfo) {
    private var optimizationModeState: LoadingState<Int> by mutableStateOf(LoadingState.Loading)

    @Composable
    fun isAvailable() = remember {
        context.resources.getBoolean(R.bool.config_show_app_info_settings_battery)
    }

    @Composable
    fun Updater() {
        if (!app.installed) return
        val current = LocalLifecycleOwner.current
        LaunchedEffect(app) {
            current.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { optimizationModeState = LoadingState.Done(getBatteryDiffEntry()) }
            }
        }
    }

    private suspend fun getBatteryDiffEntry(): Int = withContext(Dispatchers.IO) {
        return@withContext BatteryOptimizeUtils(context, app.uid, app.packageName)
            .getAppOptimizationMode(false, false);
    }

    val summary = {
        if (app.installed) {
            optimizationModeState.let { batteryDiffEntryState ->
                when (batteryDiffEntryState) {
                    is LoadingState.Loading -> context.getString(R.string.summary_placeholder)
                    is LoadingState.Done -> {
                        val optimizationMode = when (batteryDiffEntryState.result) {
                            BatteryOptimizeUtils.MODE_RESTRICTED ->
                                R.string.manager_battery_usage_restricted_title
                            BatteryOptimizeUtils.MODE_UNRESTRICTED ->
                                R.string.manager_battery_usage_unrestricted_title
                            BatteryOptimizeUtils.MODE_OPTIMIZED ->
                                R.string.manager_battery_usage_optimized_title
                            else -> return@let ""
                        }
                        return@let context.getString(optimizationMode)
                    }
                }
            }
        } else ""
    }

    fun startActivity() {
        Log.i(TAG, "Launch : ${app.packageName} with package name")
        val args = bundleOf(
            AdvancedPowerUsageDetail.EXTRA_PACKAGE_NAME to app.packageName,
            AdvancedPowerUsageDetail.EXTRA_POWER_USAGE_PERCENT to Utils.formatPercentage(0),
            AdvancedPowerUsageDetail.EXTRA_UID to app.uid,
        )
        SubSettingLauncher(context)
            .setDestination(AdvancedPowerUsageDetail::class.java.name)
            .setTitleRes(R.string.battery_details_title)
            .setArguments(args)
            .setUserHandle(app.userHandle)
            .setSourceMetricsCategory(AppInfoSettingsProvider.METRICS_CATEGORY)
            .launch()
    }

    companion object {
        private const val TAG = "AppBatteryPresenter"
    }
}

private sealed class LoadingState<out T> {
    data object Loading : LoadingState<Nothing>()

    data class Done<T>(val result: T) : LoadingState<T>()

    val resultOrNull: T? get() = if (this is Done) result else null
}
