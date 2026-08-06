/*
 * Copyright (C) 2026 The Android Open Source Project
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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.android.settings.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settings.password.ChooseLockSettingsHelper
import com.android.settingslib.spa.framework.theme.SettingsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.evolution.settings.fragments.miscellaneous.AppPickerItem
import org.evolution.settings.fragments.miscellaneous.AppPickerSearchField

class AppLockSettingsFragment : SettingsPreferenceFragment() {

    private lateinit var authenticateLauncher: ActivityResultLauncher<Intent>
    val isAuthenticatedState = mutableStateOf(false)
    val isDeviceSecureState = mutableStateOf(true)

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.VIEW_UNKNOWN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authenticateLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                isAuthenticatedState.value = true
            } else {
                requireActivity().finish()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Compose fragment, no preference XML resources to load
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SettingsTheme {
                AppLockSettingsMainScreen(this@AppLockSettingsFragment)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val km = requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isSecureNow = km.isDeviceSecure
        isDeviceSecureState.value = isSecureNow
        if (isSecureNow && !isAuthenticatedState.value) {
            val builder = ChooseLockSettingsHelper.Builder(requireActivity(), this)
            val launched = builder.setRequestCode(1)
                .setTitle(getString(R.string.app_lock_title))
                .setActivityResultLauncher(authenticateLauncher)
                .show()
            if (!launched) {
                isAuthenticatedState.value = true
            }
        } else if (!isSecureNow) {
            isAuthenticatedState.value = false
        }
    }
}

@Composable
fun AppLockSettingsMainScreen(fragment: AppLockSettingsFragment) {
    val isSecure = fragment.isDeviceSecureState.value
    val isAuthenticated = fragment.isAuthenticatedState.value

    if (!isSecure) {
        AppLockNotSecureContent {
            val intent = Intent("android.app.action.SET_NEW_PASSWORD")
            intent.setPackage(fragment.requireContext().packageName)
            fragment.startActivity(intent)
        }
    } else if (!isAuthenticated) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    } else {
        AppLockSettingsContent(fragment, fragment.requireContext().packageManager)
    }
}

@Composable
fun AppLockNotSecureContent(onSetLockClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_lock_not_secure_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.app_lock_not_secure_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(
                    onClick = onSetLockClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.app_lock_not_secure_button))
                }
            }
        }
    }
}

@Composable
fun AppLockSettingsContent(
    fragment: AppLockSettingsFragment,
    pm: PackageManager
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val allApps = remember { mutableStateListOf<AppEntry>() }

    fun loadApps() {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.GET_APP_LOCK_INFO)
            val installedApps = pm.getInstalledApplications(flags)
            val filtered = installedApps
                .filter { it.isAppLockSupported }
                .map { appInfo ->
                    AppEntry(
                        packageName = appInfo.packageName,
                        label = appInfo.loadLabel(pm).toString(),
                        icon = appInfo.loadIcon(pm),
                        isEnabled = appInfo.isAppLockEnabled,
                        isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedWith(compareBy(
                    { !it.isEnabled },
                    { it.label.lowercase() }
                ))
            withContext(Dispatchers.Main) {
                allApps.clear()
                allApps.addAll(filtered)
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadApps()
    }

    val filteredApps = remember(searchQuery, showSystemApps, allApps.toList()) {
        val query = searchQuery.lowercase()
        allApps.filter { app ->
            if (!showSystemApps && app.isSystem) return@filter false
            query.isEmpty() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query)
        }
    }

    val lockedCount = allApps.count { it.isEnabled }

    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.app_lock_dashboard_card_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lockedCount == 0)
                                stringResource(R.string.app_lock_dashboard_card_summary_none)
                            else
                                stringResource(R.string.app_lock_dashboard_card_summary_multiple, lockedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppPickerSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showSystemApps,
                    onClick = { showSystemApps = !showSystemApps },
                    label = { Text(stringResource(R.string.app_lock_show_system)) },
                    leadingIcon = if (showSystemApps) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppPickerItem(
                            packageName = app.packageName,
                            label = app.label,
                            icon = app.icon,
                            isSystem = app.isSystem,
                            checked = app.isEnabled,
                            onToggle = { nowEnabled ->
                                scope.launch(Dispatchers.IO) {
                                    val success = pm.setPackageAppLockEnabled(app.packageName, nowEnabled)
                                    if (success) {
                                        withContext(Dispatchers.Main) {
                                            val index = allApps.indexOfFirst { it.packageName == app.packageName }
                                            if (index >= 0) {
                                                allApps[index] = allApps[index].copy(isEnabled = nowEnabled)
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    var isEnabled: Boolean
)
