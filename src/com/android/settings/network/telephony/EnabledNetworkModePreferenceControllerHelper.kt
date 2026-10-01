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

package com.android.settings.network.telephony

import android.content.Context
import android.telephony.CarrierConfigManager
import android.telephony.RadioAccessFamily
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.settings.R
import com.android.settings.network.telephony.MobileNetworkSettingsSearchIndex.MobileNetworkSettingsSearchItem
import com.android.settings.network.telephony.MobileNetworkSettingsSearchIndex.MobileNetworkSettingsSearchResult
import com.android.settings.network.telephony.mode.NetworkModes.NETWORK_MODE_UNKNOWN
import com.android.settings.network.telephony.mode.NetworkModes.getStandardNetworkModeFromRaf
import com.android.settings.network.telephony.mode.NetworkModes.mergePreferredNetworkModeRaf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun interface NetworkModeApplyCallback {
    fun onResult(requestedMode: Int, actualMode: Int, success: Boolean)
}

private data class NetworkModeApplyResult(
    val actualMode: Int,
    val success: Boolean,
)

private const val NETWORK_MODE_VERIFY_ATTEMPTS = 8
private const val NETWORK_MODE_VERIFY_DELAY_MS = 125L

fun TelephonyManager.setAllowedNetworkTypes(
    viewLifecycleOwner: LifecycleOwner,
    newPreferredNetworkMode: Int,
) {
    setAllowedNetworkTypes(
        viewLifecycleOwner,
        newPreferredNetworkMode,
        callback = NetworkModeApplyCallback { _, _, _ -> },
    )
}

fun TelephonyManager.setAllowedNetworkTypes(
    viewLifecycleOwner: LifecycleOwner,
    newPreferredNetworkMode: Int,
    callback: NetworkModeApplyCallback,
) {
    viewLifecycleOwner.lifecycleScope.launch {
        val result =
            // Once the USER write begins, finish verification/rollback even if the screen is
            // destroyed. Lifecycle cancellation may suppress the callback, but must never leave
            // the radio configuration halfway through a failed transaction.
            withContext(Dispatchers.Default + NonCancellable) {
                val controlledRaf =
                    Integer.toUnsignedLong(
                        RadioAccessFamily.getRafFromNetworkType(
                            TelephonyManager
                                .NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA
                        )
                    )
                val requestedRaf =
                    Integer.toUnsignedLong(
                        RadioAccessFamily.getRafFromNetworkType(newPreferredNetworkMode)
                    )

                var previousUserRaf = -1L
                var writeAttempted = false

                try {
                    previousUserRaf =
                        getAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
                        )

                    if (requestedRaf == 0L) {
                        val currentMode =
                            if (previousUserRaf >= 0) {
                                getStandardNetworkModeFromRaf(
                                    previousUserRaf and controlledRaf
                                )
                            } else {
                                NETWORK_MODE_UNKNOWN
                            }
                        return@withContext NetworkModeApplyResult(
                            actualMode = currentMode,
                            success = false,
                        )
                    }

                    val requestedUserRaf =
                        mergePreferredNetworkModeRaf(
                            previousUserRaf,
                            supportedRadioAccessFamily,
                            newPreferredNetworkMode,
                        )

                    writeAttempted = true
                    setAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                        requestedUserRaf,
                    )

                    var actualRaf =
                        awaitControlledUserRaf(
                            controlledRaf = controlledRaf,
                            expectedControlledRaf = requestedRaf,
                        )
                    val success =
                        actualRaf >= 0 && (actualRaf and controlledRaf) == requestedRaf

                    if (!success && previousUserRaf >= 0) {
                        // Treat a rejected or normalized USER write as a transaction failure.
                        // Restore the exact previous USER reason so a failed experiment cannot
                        // leave a partially changed preferred-network configuration behind.
                        try {
                            setAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                                previousUserRaf,
                            )
                            actualRaf = awaitExactUserRaf(previousUserRaf)
                        } catch (rollbackError: RuntimeException) {
                            Log.w(
                                "EnabledNetworkMode",
                                "Unable to roll back rejected network mode",
                                rollbackError,
                            )
                        }
                    }

                    val actualControlledRaf =
                        if (actualRaf >= 0) actualRaf and controlledRaf else 0L
                    NetworkModeApplyResult(
                        actualMode =
                            if (actualRaf >= 0) {
                                getStandardNetworkModeFromRaf(actualControlledRaf)
                            } else {
                                NETWORK_MODE_UNKNOWN
                            },
                        success = success,
                    )
                } catch (e: RuntimeException) {
                    Log.w("EnabledNetworkMode", "Unable to apply network mode", e)

                    if (writeAttempted && previousUserRaf >= 0) {
                        try {
                            setAllowedNetworkTypesForReason(
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                                previousUserRaf,
                            )
                            awaitExactUserRaf(previousUserRaf)
                        } catch (rollbackError: RuntimeException) {
                            Log.w(
                                "EnabledNetworkMode",
                                "Unable to roll back network mode after exception",
                                rollbackError,
                            )
                        }
                    }

                    val actualMode =
                        try {
                            val actualRaf =
                                getAllowedNetworkTypesForReason(
                                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
                                )
                            if (actualRaf >= 0) {
                                getStandardNetworkModeFromRaf(actualRaf and controlledRaf)
                            } else {
                                NETWORK_MODE_UNKNOWN
                            }
                        } catch (_: RuntimeException) {
                            NETWORK_MODE_UNKNOWN
                        }
                    NetworkModeApplyResult(actualMode = actualMode, success = false)
                }
            }

        callback.onResult(newPreferredNetworkMode, result.actualMode, result.success)
    }
}

private suspend fun TelephonyManager.awaitControlledUserRaf(
    controlledRaf: Long,
    expectedControlledRaf: Long,
): Long {
    var actualRaf =
        getAllowedNetworkTypesForReason(
            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
        )
    for (attempt in 1 until NETWORK_MODE_VERIFY_ATTEMPTS) {
        if (actualRaf >= 0 && (actualRaf and controlledRaf) == expectedControlledRaf) {
            break
        }
        delay(NETWORK_MODE_VERIFY_DELAY_MS)
        actualRaf =
            getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
            )
    }
    return actualRaf
}

private suspend fun TelephonyManager.awaitExactUserRaf(expectedUserRaf: Long): Long {
    var actualRaf =
        getAllowedNetworkTypesForReason(
            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
        )
    for (attempt in 1 until NETWORK_MODE_VERIFY_ATTEMPTS) {
        if (actualRaf == expectedUserRaf) {
            break
        }
        delay(NETWORK_MODE_VERIFY_DELAY_MS)
        actualRaf =
            getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
            )
    }
    return actualRaf
}

enum class NetworkModePreferenceType {
    EnabledNetworkMode,
    PreferredNetworkMode,
    None,
}

fun getNetworkModePreferenceType(context: Context, subId: Int): NetworkModePreferenceType {
    if (!SubscriptionManager.isValidSubscriptionId(subId)) {
        return NetworkModePreferenceType.None
    }

    data class Config(
        val carrierConfigApplied: Boolean,
        val hideCarrierNetworkSettings: Boolean,
        val hidePreferredNetworkType: Boolean,
        val worldPhone: Boolean,
    )

    val config =
        CarrierConfigRepository(context).transformConfig(subId) {
            Config(
                carrierConfigApplied =
                    getBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL),
                hideCarrierNetworkSettings =
                    getBoolean(CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL),
                hidePreferredNetworkType =
                    getBoolean(CarrierConfigManager.KEY_HIDE_PREFERRED_NETWORK_TYPE_BOOL),
                worldPhone = getBoolean(CarrierConfigManager.KEY_WORLD_PHONE_BOOL),
            )
        }

    return when {
        !config.carrierConfigApplied ||
            config.hideCarrierNetworkSettings ||
            config.hidePreferredNetworkType -> NetworkModePreferenceType.None
        config.worldPhone -> NetworkModePreferenceType.PreferredNetworkMode
        else -> NetworkModePreferenceType.EnabledNetworkMode
    }
}

class PreferredNetworkModeSearchItem(private val context: Context) :
    MobileNetworkSettingsSearchItem {
    private val title: String = context.getString(R.string.preferred_network_mode_title)

    override fun getSearchResult(subId: Int): MobileNetworkSettingsSearchResult? =
        when (getNetworkModePreferenceType(context, subId)) {
            NetworkModePreferenceType.PreferredNetworkMode ->
                MobileNetworkSettingsSearchResult(
                    key = "preferred_network_mode_key",
                    title = title,
                )

            NetworkModePreferenceType.EnabledNetworkMode ->
                MobileNetworkSettingsSearchResult(
                    key = "enabled_networks_key",
                    title = title,
                )

            else -> null
        }
}
