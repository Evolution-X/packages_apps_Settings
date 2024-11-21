/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.android.settings.security.applock

import android.app.AppLockManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.os.Bundle
import android.view.View

import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference

import com.android.internal.logging.nano.MetricsProto
import com.android.internal.util.evolution.Utils

import com.android.settings.R
import com.android.settings.core.SubSettingLauncher
import com.android.settings.dashboard.DashboardFragment
import com.android.settingslib.PrimarySwitchPreference
import com.android.settingslib.widget.TwoTargetPreference.ICON_SIZE_MEDIUM

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TAG = AppLockPackageListFragment::class.simpleName
internal const val PACKAGE_INFO = "package_info"

class AppLockPackageListFragment : DashboardFragment() {

    private lateinit var appLockManager: AppLockManager
    private lateinit var pm: PackageManager
    private lateinit var whiteListedPackages: Array<String>
    private lateinit var launchablePackages: List<String>

    override fun onAttach(context: Context) {
        super.onAttach(context)
        appLockManager = context.getSystemService(AppLockManager::class.java)!!
        pm = context.packageManager
        launchablePackages = Utils.launchablePackages(context)
        whiteListedPackages = resources.getStringArray(
            com.android.internal.R.array.config_appLockAllowedSystemApps)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        lifecycleScope.launch {
            val selectedPackages = getSelectedPackages()
            val preferences = withContext(Dispatchers.Default) {
                pm.getInstalledPackages(
                    PackageInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                ).filter { packageInfo ->
                    val isSystemApp = packageInfo.applicationInfo?.isSystemApp ?: false
                    !isSystemApp || launchablePackages.contains(packageInfo.packageName) ||
                        whiteListedPackages.contains(packageInfo.packageName)
                }.sortedWith { first, second ->
                    getLabel(first).compareTo(getLabel(second))
                }
            }.map { packageInfo ->
                createPreference(packageInfo, selectedPackages.contains(packageInfo.packageName))
            }
            preferenceScreen?.let { preferenceScreen ->
                for (pref in preferences) {
                    preferenceScreen.addPreference(pref)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val selectedPackages = getSelectedPackages()
            preferenceScreen?.let { preferenceScreen ->
                for (i in 0 until preferenceScreen.preferenceCount) {
                    val pref = preferenceScreen.getPreference(i)
                    if (pref is PrimarySwitchPreference) {
                        pref.isChecked = selectedPackages.contains(pref.key)
                    }
                }
            }
        }
    }

    private suspend fun getSelectedPackages(): Set<String> {
        return withContext(Dispatchers.IO) {
            appLockManager.packageData.filter {
                it.shouldProtectApp == true
            }.map {
                it.packageName
            }.toSet()
        }
    }

    private fun getLabel(packageInfo: PackageInfo) =
        packageInfo.applicationInfo?.loadLabel(pm).toString()

    private fun createPreference(packageInfo: PackageInfo, isProtected: Boolean): Preference {
        val label = getLabel(packageInfo)
        return PrimarySwitchPreference(requireContext()).apply {
            key = packageInfo.packageName
            title = label
            icon = packageInfo.applicationInfo?.loadIcon(pm)
            setIconSize(ICON_SIZE_MEDIUM)
            isChecked = isProtected
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launch(Dispatchers.IO) {
                    appLockManager.setShouldProtectApp(packageInfo.packageName, newValue as Boolean)
                }
                return@setOnPreferenceChangeListener true
            }
            setOnPreferenceClickListener {
                SubSettingLauncher(requireContext())
                    .setDestination(AppLockPackageConfigFragment::class.qualifiedName)
                    .setSourceMetricsCategory(metricsCategory)
                    .setTitleText(label)
                    .setArguments(
                        Bundle(1).apply {
                            putParcelable(PACKAGE_INFO, packageInfo)
                        }
                    )
                    .launch()
                true
            }
        }
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.EVOLVER

    override protected fun getPreferenceScreenResId() = R.xml.app_lock_package_list_settings

    override protected fun getLogTag() = TAG
}
