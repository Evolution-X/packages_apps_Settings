/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.Manifest
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import com.android.settings.R
import com.android.settings.network.SatelliteRepository
import com.android.settings.wifi.repository.AirplaneModeRepository
import com.android.settingslib.datastore.AbstractKeyedDataObservable
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.metadata.PreferenceChangeReason
import com.android.settingslib.metadata.PreferenceLifecycleContext
import com.android.settingslib.metadata.PreferenceLifecycleProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.preference.PreferenceBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// LINT.IfChange
class EnabledNetworkModePreference(
    private val data: MobileNetworkData,
    private val airplaneModeRepository: AirplaneModeRepository =
        AirplaneModeRepository(data.context),
    private val callStateRepository: CallStateRepository = CallStateRepository(data.context),
    private val satelliteRepository: SatelliteRepository = SatelliteRepository(data.context),
) :
    PreferenceMetadata,
    PersistentPreference<CharSequence>,
    PreferenceAvailabilityProvider,
    PreferenceSummaryProvider,
    PreferenceBinding,
    PreferenceLifecycleProvider,
    Preference.OnPreferenceChangeListener {

    private fun isTelephonyChangeableFlow(): Flow<Boolean> =
        combine(
            airplaneModeRepository.isAirplaneModeFlow(),
            callStateRepository.isInCallFlow(),
            satelliteRepository.getIsSessionStartedFlow(),
        ) { isApmOn, isInCall, isSatelEnabled ->
            Log.d(TAG, "isApmOn=$isApmOn,isInCall=$isInCall,isSatelEnabled=$isSatelEnabled")
            !isApmOn && !isInCall && !isSatelEnabled
        }

    private var isEnabled: Boolean = true

    private lateinit var lifecycleOwner: LifecycleOwner

    override val key: String
        get() = KEY

    override val purpose: Int
        get() = R.string.enabled_networks_key_purpose

    override val title: Int
        get() = R.string.preferred_network_mode_title

    override val availabilityDescription =
        "The sim must be configurable for network mode."

    override fun getAvailabilityStability() = PreconditionStability.UNSTABLE

    override fun isAvailable(context: Context): Boolean =
        data.enabledNetworkModeFlow.value.isAvailable

    override fun isEnabled(context: Context): Boolean = isEnabled

    override fun getSummary(context: Context): CharSequence? =
        data.enabledNetworkModeFlow.value.summary

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.onPreferenceChangeListener = this
    }

    override fun onCreate(context: PreferenceLifecycleContext) {
        lifecycleOwner = context.lifecycleOwner
        data.coroutineScope?.launch {
            isTelephonyChangeableFlow().collect { isChangeable ->
                isEnabled = isChangeable
                if (!isChangeable) {
                    NetworkModePreference.dismissDialog(context.fragmentManager)
                }
                context.notifyPreferenceChange(KEY)
            }
        }
    }

    override fun onResume(context: PreferenceLifecycleContext) {
        val networkPreference = context.findPreference<NetworkModePreference>(KEY) ?: return
        data.enabledNetworkModeEntriesBuilder.updateListPreference(networkPreference)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val networkPreference = preference as? NetworkModePreference ?: return false
        val newPreferredNetworkMode = (newValue as? String)?.toIntOrNull() ?: return false
        val telephonyManager = data.context.telephonyManager(data.subId) ?: return false

        networkPreference.isEnabled = false
        networkPreference.setSummary(R.string.network_mode_applying)

        telephonyManager.setAllowedNetworkTypes(
            lifecycleOwner,
            newPreferredNetworkMode,
        ) { requestedMode, actualMode, success ->
            val builder = data.enabledNetworkModeEntriesBuilder
            builder.updateListPreference(networkPreference)
            data.refreshEnabledNetworkModeData()
            networkPreference.isEnabled = isEnabled

            if (!success) {
                Log.w(
                    TAG,
                    "Network mode rejected. requested=$requestedMode, actual=$actualMode",
                )
                Toast.makeText(
                        data.context,
                        R.string.network_mode_apply_failed,
                        Toast.LENGTH_LONG,
                    )
                    .show()
            }
        }

        // Apply only after the telephony stack confirms the USER reason value.
        return false
    }

    override val valueType: Class<CharSequence>
        get() = CharSequence::class.javaObjectType

    override fun storage(context: Context): KeyValueStore = Store(data)

    override fun getReadPermissions(context: Context) =
        Permissions.anyOf(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)

    override fun getWritePermissions(context: Context) = Permissions.EMPTY

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int) =
        ReadWritePermit.DISALLOW

    override val supportsWrite = false

    override val sensitivityLevel
        get() = SensitivityLevel.REQUIRES_CONFIRMATION

    @Suppress("UNCHECKED_CAST")
    private class Store(private val data: MobileNetworkData) :
        AbstractKeyedDataObservable<String>(), KeyValueStore {

        override fun contains(key: String) = key == KEY

        override fun <T : Any> getValue(key: String, valueType: Class<T>): T? =
            data.enabledNetworkModeFlow.value.summary as T?

        override fun <T : Any> setValue(key: String, valueType: Class<T>, value: T?) {}

        override fun onFirstObserverAdded() {
            data.coroutineScope?.launch {
                data.enabledNetworkModeFlow.collect {
                    notifyChange(KEY, PreferenceChangeReason.VALUE)
                    Log.d(TAG, "enabledNetworkModeFlow{}=$it")
                }
            }
        }

        override fun onLastObserverRemoved() {}
    }


    companion object {
        private const val TAG = "EnabledNetworkModePreference"
        const val KEY = "enabled_networks_key"
    }
}
// LINT.ThenChange(EnabledNetworkModePreferenceController.kt)
