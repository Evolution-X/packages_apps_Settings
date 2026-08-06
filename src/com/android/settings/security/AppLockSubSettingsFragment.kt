/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.android.settings.security

import android.content.Context
import android.os.Bundle
import android.os.UserHandle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.internal.app.AppLockCredentialUtils
import com.android.internal.logging.nano.MetricsProto
import com.android.internal.widget.LockPatternView
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.spa.framework.theme.SettingsTheme

import android.app.Activity
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.android.internal.app.AppLockCustomCredentialView
import com.android.settings.password.ChooseLockSettingsHelper

class AppLockSubSettingsFragment : SettingsPreferenceFragment() {

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
                AppLockSubSettingsMainScreen(this@AppLockSubSettingsFragment)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val userId = UserHandle.myUserId()
        val isSecureNow = AppLockCredentialUtils.isAppLockSecure(requireContext(), userId)
        isDeviceSecureState.value = isSecureNow
        if (isSecureNow && !isAuthenticatedState.value) {
            val credType = AppLockCredentialUtils.getCredentialType(requireContext(), userId)
            if (credType == AppLockCredentialUtils.CREDENTIAL_TYPE_DEVICE) {
                val builder = ChooseLockSettingsHelper.Builder(requireActivity(), this)
                val launched = builder.setRequestCode(1)
                    .setTitle(getString(R.string.app_lock_title))
                    .setActivityResultLauncher(authenticateLauncher)
                    .show()
                if (!launched) {
                    isAuthenticatedState.value = true
                }
            } else if (AppLockCredentialUtils.isBiometricEnabled(requireContext(), userId)) {
                showBiometricPromptForSettings(userId)
            }
        } else if (!isSecureNow) {
            isAuthenticatedState.value = false
        }
    }

    private fun showBiometricPromptForSettings(userId: Int) {
        val prompt = BiometricPrompt.Builder(requireContext())
            .setTitle(getString(R.string.app_lock_title))
            .setSubtitle("Unlock App Lock Settings")
            .setNegativeButton(getString(R.string.cancel), requireActivity().mainExecutor) { _, _ -> }
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(
            CancellationSignal(),
            requireActivity().mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticatedState.value = true
                }
            }
        )
    }
}

@Composable
fun AppLockSubSettingsMainScreen(fragment: AppLockSubSettingsFragment) {
    val context = fragment.requireContext()
    val isSecure = fragment.isDeviceSecureState.value
    val isAuthenticated = fragment.isAuthenticatedState.value

    if (!isSecure) {
        return
    } else if (!isAuthenticated) {
        val credType = AppLockCredentialUtils.getCredentialType(context, UserHandle.myUserId())
        if (credType != AppLockCredentialUtils.CREDENTIAL_TYPE_DEVICE) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    AppLockCustomCredentialView(ctx).apply {
                        setUserId(UserHandle.myUserId())
                        setAppDetails("App Lock Settings", null)
                        setOnUnlockListener(object : AppLockCustomCredentialView.OnUnlockListener {
                            override fun onUnlocked() {
                                fragment.isAuthenticatedState.value = true
                            }
                            override fun onCancelled() {
                                fragment.requireActivity().finish()
                            }
                        })
                    }
                }
            )
        }
    } else {
        AppLockSubSettingsContent(context)
    }
}

@Composable
fun AppLockSubSettingsContent(context: Context) {
    val userId = UserHandle.myUserId()
    var credentialType by remember {
        mutableIntStateOf(AppLockCredentialUtils.getCredentialType(context, userId))
    }
    var biometricsAllowed by remember {
        mutableStateOf(AppLockCredentialUtils.isBiometricsAllowed(context, userId))
    }
    var currentTimeout by remember {
        mutableStateOf(
            android.provider.Settings.Secure.getLong(
                context.contentResolver,
                AppLockCredentialUtils.KEY_APP_LOCK_TIMEOUT,
                5000L
            )
        )
    }

    var showTimeoutDialog by remember { mutableStateOf(false) }
    var setupDialogType by remember { mutableIntStateOf(-1) } // 1: PIN, 2: Password, 3: Pattern

    val timeoutOptions = remember {
        listOf(
            0L to context.getString(R.string.app_lock_timeout_immediately),
            5000L to context.getString(R.string.app_lock_timeout_5s),
            30000L to context.getString(R.string.app_lock_timeout_30s),
            60000L to context.getString(R.string.app_lock_timeout_1m),
            300000L to context.getString(R.string.app_lock_timeout_5m),
            -1L to context.getString(R.string.app_lock_timeout_screen_lock)
        )
    }

    val currentTimeoutLabel = remember(currentTimeout) {
        timeoutOptions.find { it.first == currentTimeout }?.second
            ?: context.getString(R.string.app_lock_timeout_5s)
    }

    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Banner
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
                            text = stringResource(R.string.app_lock_settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.app_lock_settings_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lock Timeout Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright
                ),
                onClick = { showTimeoutDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_lock_timeout_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentTimeoutLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section Header
            Text(
                text = stringResource(R.string.app_lock_method_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )

            // Credential Methods Card Container
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright
                )
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    // Method 0: Device Lock
                    CredentialMethodItem(
                        icon = Icons.Default.Smartphone,
                        title = stringResource(R.string.app_lock_method_device_lock),
                        summary = stringResource(R.string.app_lock_method_device_lock_summary),
                        selected = (credentialType == AppLockCredentialUtils.CREDENTIAL_TYPE_DEVICE),
                        onClick = {
                            AppLockCredentialUtils.clearCustomCredential(context, userId)
                            credentialType = AppLockCredentialUtils.CREDENTIAL_TYPE_DEVICE
                        }
                    )

                    // Method 1: PIN
                    CredentialMethodItem(
                        icon = Icons.Default.Pin,
                        title = stringResource(R.string.app_lock_method_pin),
                        summary = stringResource(R.string.app_lock_method_pin_summary),
                        selected = (credentialType == AppLockCredentialUtils.CREDENTIAL_TYPE_PIN),
                        onClick = { setupDialogType = AppLockCredentialUtils.CREDENTIAL_TYPE_PIN }
                    )

                    // Method 2: Password
                    CredentialMethodItem(
                        icon = Icons.Default.Password,
                        title = stringResource(R.string.app_lock_method_password),
                        summary = stringResource(R.string.app_lock_method_password_summary),
                        selected = (credentialType == AppLockCredentialUtils.CREDENTIAL_TYPE_PASSWORD),
                        onClick = { setupDialogType = AppLockCredentialUtils.CREDENTIAL_TYPE_PASSWORD }
                    )

                    // Method 3: Pattern
                    CredentialMethodItem(
                        icon = Icons.Default.GridOn,
                        title = stringResource(R.string.app_lock_method_pattern),
                        summary = stringResource(R.string.app_lock_method_pattern_summary),
                        selected = (credentialType == AppLockCredentialUtils.CREDENTIAL_TYPE_PATTERN),
                        onClick = { setupDialogType = AppLockCredentialUtils.CREDENTIAL_TYPE_PATTERN }
                    )
                }
            }

            // Biometrics switch card (visible for custom lock methods)
            if (credentialType != AppLockCredentialUtils.CREDENTIAL_TYPE_DEVICE) {
                Spacer(modifier = Modifier.height(16.dp))

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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_lock_biometrics_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.app_lock_biometrics_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = biometricsAllowed,
                            onCheckedChange = { allowed ->
                                AppLockCredentialUtils.setBiometricsAllowed(context, userId, allowed)
                                biometricsAllowed = allowed
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Timeout Selection Dialog
        if (showTimeoutDialog) {
            AlertDialog(
                onDismissRequest = { showTimeoutDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.app_lock_timeout_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_lock_timeout_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        timeoutOptions.forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        android.provider.Settings.Secure.putLong(
                                            context.contentResolver,
                                            AppLockCredentialUtils.KEY_APP_LOCK_TIMEOUT,
                                            value
                                        )
                                        currentTimeout = value
                                        showTimeoutDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (currentTimeout == value),
                                    onClick = {
                                        android.provider.Settings.Secure.putLong(
                                            context.contentResolver,
                                            AppLockCredentialUtils.KEY_APP_LOCK_TIMEOUT,
                                            value
                                        )
                                        currentTimeout = value
                                        showTimeoutDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTimeoutDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        // Credential Setup Dialogs
        if (setupDialogType != -1) {
            CredentialSetupDialog(
                context = context,
                type = setupDialogType,
                onDismiss = { setupDialogType = -1 },
                onSuccess = {
                    credentialType = setupDialogType
                    setupDialogType = -1
                }
            )
        }
    }
}

@Composable
fun CredentialMethodItem(
    icon: ImageVector,
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}

@Composable
fun CredentialSetupDialog(
    context: Context,
    type: Int,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1: Enter, 2: Confirm
    var firstInput by remember { mutableStateOf("") }
    var currentInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val titleRes = when (type) {
        AppLockCredentialUtils.CREDENTIAL_TYPE_PIN ->
            if (step == 1) R.string.app_lock_setup_pin_title else R.string.app_lock_confirm_pin_title
        AppLockCredentialUtils.CREDENTIAL_TYPE_PASSWORD ->
            if (step == 1) R.string.app_lock_setup_password_title else R.string.app_lock_confirm_password_title
        else ->
            if (step == 1) R.string.app_lock_setup_pattern_title else R.string.app_lock_confirm_pattern_title
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                when (type) {
                    AppLockCredentialUtils.CREDENTIAL_TYPE_PIN -> {
                        OutlinedTextField(
                            value = currentInput,
                            onValueChange = { input ->
                                if (input.length <= 8 && input.all { it.isDigit() }) {
                                    currentInput = input
                                    errorMessage = ""
                                }
                            },
                            label = { Text(stringResource(R.string.app_lock_method_pin)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    AppLockCredentialUtils.CREDENTIAL_TYPE_PASSWORD -> {
                        OutlinedTextField(
                            value = currentInput,
                            onValueChange = { input ->
                                currentInput = input
                                errorMessage = ""
                            },
                            label = { Text(stringResource(R.string.app_lock_method_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    AppLockCredentialUtils.CREDENTIAL_TYPE_PATTERN -> {
                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    LockPatternView(ctx).apply {
                                        setOnPatternListener(object : LockPatternView.OnPatternListener {
                                            override fun onPatternDetected(pattern: MutableList<LockPatternView.Cell>?, inputMode: LockPatternView.InputMode?, patternSize: Byte) {
                                                if (pattern != null && pattern.size >= 4) {
                                                    val patternStr = pattern.joinToString("-") { "${it.row},${it.column}" }
                                                    currentInput = patternStr
                                                    errorMessage = ""
                                                } else {
                                                    setDisplayMode(LockPatternView.DisplayMode.Wrong)
                                                    errorMessage = "At least 4 dots required"
                                                }
                                            }
                                        })
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (currentInput.isEmpty()) {
                        errorMessage = "Cannot be empty"
                        return@TextButton
                    }
                    if (step == 1) {
                        firstInput = currentInput
                        currentInput = ""
                        step = 2
                    } else {
                        if (currentInput == firstInput) {
                            val saved = AppLockCredentialUtils.saveCredential(
                                context,
                                UserHandle.myUserId(),
                                type,
                                currentInput
                            )
                            if (saved) {
                                onSuccess()
                            } else {
                                errorMessage = "Failed to save credential"
                            }
                        } else {
                            errorMessage = context.getString(R.string.app_lock_credential_mismatch)
                            currentInput = ""
                        }
                    }
                }
            ) {
                Text(if (step == 1) "Next" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
