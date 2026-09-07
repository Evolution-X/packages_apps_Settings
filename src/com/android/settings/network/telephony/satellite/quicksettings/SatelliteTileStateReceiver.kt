/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.android.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.network.telephony.satellite.quicksettings

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.OutcomeReceiver
import android.os.UserHandle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.satellite.SatelliteManager
import android.util.Log
import com.android.internal.annotations.VisibleForTesting
import com.android.settings.R
import com.android.settings.flags.Flags
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val TAG = "SatelliteTileStateReceiver"

/**
 * A [BroadcastReceiver] that listens for system events to manage the Satellite QS Tile state and
 * prompt notifications.
 *
 * This receiver handles:
 * 1. **Boot/Update**: Initializing listeners and updating the tile's enabled state based on NTN
 *    (Non-Terrestrial Network) support.
 * 2. **SIM/Config Changes**: Re-evaluating the tile's visibility when carrier config or SIM state
 *    changes.
 * 3. **Satellite Eligibility Job**: Schedules a [JobService] to monitor satellite eligibility when
 *    cellular data is lost.
 *
 * The enabled state of the service is updated based on the following logic:
 * 1. If the device supports any NTN, the service is enabled.
 * 2. If NTN is not supported, the service is disabled.
 *
 * This class is `open` to allow for spying in unit tests.
 */
open class SatelliteTileStateReceiver(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // SERVICE_STATE fires very often; keep this at debug so devices without
        // satellite support aren't spammed on every signal change.
        logd { "onReceive: intent=$intent" }
        if (UserHandle.myUserId() != UserHandle.USER_SYSTEM) {
            logd { "Not running on system user, ignoring." }
            return
        }

        if (!isSatelliteTileFeatureEnabled(context)) {
            logd { "Satellite tile feature is disabled. Ignoring intent." }
            // EXPLICITLY disable the tile service to clean up legacy state
            updateTileServiceEnabledState(context, false)
            return
        }

        val action = intent.action
        logd { "onReceive: $action" }

        val job: (suspend () -> Unit)? =
            when (action) {
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED -> { -> handleBootOrAppUpdate(context) }
                TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED,
                TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED,
                CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SERVICE_STATE -> { ->
                        handleSubscriptionOrConfigChanged(context)
                    }
                else -> null
            }

        if (job != null) {
            val pendingResult = goAsync()
            CoroutineScope(defaultDispatcher).launch {
                try {
                    job()
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            Log.w(TAG, "Received unsupported action: $action")
        }
    }

    /**
     * Handles boot completion (`ACTION_BOOT_COMPLETED`) and app update
     * (`ACTION_MY_PACKAGE_REPLACED`) events.
     *
     * This is the initialization phase where we:
     * 1. Register a [SatelliteManager] callback via [SatelliteSupportedStateChangeHandler] to keep
     *    tile visibility updated.
     * 2. Perform an immediate check to update the tile state.
     * 3. Schedule the Satellite Eligibility Job to monitor data loss events.
     */
    private suspend fun handleBootOrAppUpdate(context: Context) {
        Log.i(TAG, "Handling boot or app update.")
        SatelliteSupportedStateChangeHandler.register(context, defaultDispatcher)
        updateTileServiceEnabledState(context, isAnyNtnSupported(context))
        scheduleEligibilityJob(context)
    }

    /**
     * Handles default data subscription, SIM state, or Carrier Config changes.
     *
     * These events indicate that the device's capabilities or carrier support might have changed,
     * so we refresh the tile's enabled state.
     */
    private suspend fun handleSubscriptionOrConfigChanged(context: Context) {
        logd { "Handling subscription or config change." }
        updateTileServiceEnabledState(context, isAnyNtnSupported(context))
        scheduleEligibilityJob(context)
    }

    private suspend fun isAnyNtnSupported(context: Context): Boolean {
        return isAnyNtnSupportedFlow(context).first()
    }

    /**
     * Returns a [Flow] that emits true if NB-IoT-based NTN is supported, false otherwise.
     *
     * This flow uses a callback flow to asynchronously receive the supported state from
     * [SatelliteManager.requestIsSupported].
     */
    private fun isNbIotBasedNtnSupportedFlow(context: Context): Flow<Boolean> {
        val satelliteManager: SatelliteManager? =
            context.getSystemService(SatelliteManager::class.java)
        if (satelliteManager == null) {
            Log.w(TAG, "SatelliteManager is null, returning false")
            return flowOf(false)
        }
        logd { "isNbIotBasedNtnSupportedFlow started" }
        return callbackFlow {
            val callback =
                object : OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> {
                    override fun onResult(isSupported: Boolean) {
                        logd { "isNbIotBasedNtnSupportedFlow onResult: $isSupported" }
                        trySend(isSupported)
                        close()
                    }

                    override fun onError(error: SatelliteManager.SatelliteException) {
                        Log.e(TAG, "isNbIotBasedNtnSupportedFlow failed: $error")
                        trySend(false)
                        close()
                    }
                }
            satelliteManager.requestIsSupported(defaultDispatcher.asExecutor(), callback)
            awaitClose {}
        }
    }

    /**
     * Returns a [Flow] that emits `true` if any form of NTN (LTE-based or NB-IoT-based) is
     * supported, and `false` otherwise.
     *
     * This flow short-circuits and returns `true` immediately if LTE-based NTN is supported.
     */
    private fun isAnyNtnSupportedFlow(context: Context): Flow<Boolean> {
        if (SatelliteUtils.isLteBasedNtnSupportedByAnySub(context)) {
            return flowOf(true)
        }
        return isNbIotBasedNtnSupportedFlow(context)
    }

    companion object {
        /**
         * Schedules a JobService to monitor the data registration state.
         *
         * The job will be triggered when the data registration state changes (e.g., losing service).
         */
        fun scheduleEligibilityJob(context: Context) {
            val subId = SubscriptionManager.getDefaultDataSubscriptionId()
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                val jobScheduler = context.getSystemService(android.app.job.JobScheduler::class.java)
                val jobId = context.resources.getInteger(R.integer.satellite_eligibility_job)
                jobScheduler?.cancel(jobId)
                Log.i(TAG, "Default data subscription is invalid, cancelled job.")
            } else {
                SatelliteEligibilityJobService.schedule(context, forceImmediate = true)
            }
        }

        /**
         * Verifies that the satellite tile feature is enabled for the device.
         *
         * This function checks for the following conditions in order:
         * 1. The device is not running in the test harness environment.
         * 2. The master aconfig flag `FLAG_ENABLE_SATELLITE_TILE` is enabled.
         * 3. The device supports satellite telephony via `FEATURE_TELEPHONY_SATELLITE`.
         * 4. The feature is enabled by the OEM via `config_show_satellite_tile`.
         *
         * @param context The application context.
         * @return `true` if all conditions are met, `false` otherwise.
         */
        fun isSatelliteTileFeatureEnabled(context: Context): Boolean {
            // Suppress the feature in user test harness environments to prevent
            // intrusive prompts from interrupting automated tests (b/479033947).
            if (
                ActivityManager.isRunningInTestHarness() ||
                    ActivityManager.isRunningInUserTestHarness()
            ) {
                logd { "isRunningInTestHarness is true. Suppressing satellite tile." }
                return false
            }

            // Master aconfig flag check for the entire feature
            if (!Flags.enableSatelliteTile()) {
                logd { "enable_satellite_tile aconfig flag is false." }
                return false
            }

            // Hardware/Software Capability Check for NTN
            if (
                !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SATELLITE)
            ) {
                logd { "FEATURE_TELEPHONY_SATELLITE not supported." }
                return false
            }

            // OEM opt-in for the feature
            if (!context.resources.getBoolean(R.bool.config_show_satellite_tile)) {
                logd { "config_show_satellite_tile is false, feature disabled." }
                return false
            }

            logd { "Satellite tile static feature configs are enabled for this device." }
            return true
        }

        /**
         * Checks if the SatelliteTileService component is explicitly enabled based on runtime capability.
         */
        fun isTileServiceEnabled(context: Context): Boolean {
            val componentName = ComponentName(context, SatelliteTileService::class.java)
            val isEnabled =
                context.packageManager.getComponentEnabledSetting(componentName) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            Log.i(TAG, "SatelliteTileService runtime component state enabled: $isEnabled")
            return isEnabled
        }

        /**
         * Update the tile service state. This enables or disables the service, making the tile
         * appear or disappear.
         */
        fun updateTileServiceEnabledState(context: Context, isAnyNtnSupported: Boolean) {
            val componentName = ComponentName(context, SatelliteTileService::class.java)
            val packageManager = context.packageManager
            val oldState = packageManager.getComponentEnabledSetting(componentName)
            val newState =
                if (isAnyNtnSupported) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

                // This enables or disables the service, making the tile appear or disappear.
                packageManager.setComponentEnabledSetting(
                    componentName,
                    newState,
                    PackageManager.DONT_KILL_APP,
                )

		if (oldState != newState) {
                    Log.i(TAG, "Setting SatelliteTileService enabled state to: $isAnyNtnSupported")
                if(newState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    Log.i(TAG, "cc Scheduling eligibility job")
                    scheduleEligibilityJob(context)
                }
            }
        }
    }
}

/**
 * Handles the registration for satellite supported state changes.
 *
 * This object is used to register a callback with the [SatelliteManager] to listen for changes in
 * satellite support status. It is designed as a singleton to ensure that the callback is registered
 * only once and to prevent leaking the [BroadcastReceiver] context, as the callback may be held by
 * the system service for an extended period.
 */
@VisibleForTesting
internal object SatelliteSupportedStateChangeHandler {
    private var isSatelliteSupportedRegistered = false
    private val lock = Any()

    @VisibleForTesting
    internal fun reset() {
        synchronized(lock) { isSatelliteSupportedRegistered = false }
    }

    /**
     * Registers for [SatelliteManager] supported state changes.
     *
     * This is typically called on boot to ensure the [SatelliteTileService] enabled state tracks
     * the modem's satellite capabilities dynamically.
     */
    fun register(context: Context, dispatcher: CoroutineDispatcher) {
        synchronized(lock) {
            if (isSatelliteSupportedRegistered) {
                logd { "Already registered for satellite state changes." }
                return
            }

            // Use application context to avoid leaking the receiver context
            val appContext = context.applicationContext
            val satelliteManager = appContext.getSystemService(SatelliteManager::class.java)
            if (satelliteManager == null) {
                Log.e(TAG, "SatelliteManager is not available for registration.")
                return
            }

            try {
                satelliteManager.registerForSupportedStateChanged(dispatcher.asExecutor()) {
                    isNbIotBasedNtnSupported ->
                    logd {
                        "onSatelliteSupportedStateChanged: isSupported=$isNbIotBasedNtnSupported"
                    }
                    val isLteBasedNtnSupported =
                        SatelliteUtils.isLteBasedNtnSupportedByAnySub(appContext)
                    SatelliteTileStateReceiver.updateTileServiceEnabledState(
                        appContext,
                        isLteBasedNtnSupported || isNbIotBasedNtnSupported,
                    )
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to register for satellite supported state changes", e)
                return
            }

            isSatelliteSupportedRegistered = true
            logd { "Successfully registered callbacks for satellite state changes." }
        }
    }
}

private inline fun logd(message: () -> String) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, message())
    }
}
