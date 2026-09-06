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
package com.android.settings.network.telephony.satellite

import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.telephony.CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC
import android.telephony.CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_HYBRID
import android.telephony.CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_MANUAL
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settings.core.BasePreferenceController.AVAILABLE
import com.android.settings.core.BasePreferenceController.CONDITIONALLY_UNAVAILABLE
import com.android.settings.network.SatelliteRepository
import com.android.settings.testutils.FakeFeatureFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SatelliteSettingPreferenceControllerTest {
    private var mockSatelliteRepository =
        mock<SatelliteRepository>().stub {
            on { isSatelliteAccessConfigurationForCurrentLocationFlow(TEST_SUB_ID) }
                .thenReturn(flowOf(true))
            on { requestIsSupportedFlow() }.thenReturn(flowOf(true))
            on { carrierRoamingNtnAvailableServicesChangedFlow(TEST_SUB_ID) }
                .thenReturn(flowOf(true))
        }
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferenceIntent = Intent()
    private val preference =
        Preference(context).apply {
            key = KEY
            intent = preferenceIntent
        }
    private val preferenceScreen = PreferenceManager(context).createPreferenceScreen(context)

    private lateinit var fakeFeatureFactory: FakeFeatureFactory
    @Mock private lateinit var mockSatelliteSettingsRepository: SatelliteSettingsRepository

    private lateinit var controller: SatelliteSettingPreferenceController

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        fakeFeatureFactory = FakeFeatureFactory.setupForTest()
        whenever(fakeFeatureFactory.telephonyFeatureProvider.satelliteSettingsRepository)
            .thenReturn(mockSatelliteSettingsRepository)

        controller =
            SatelliteSettingPreferenceController(
                context = context,
                key = KEY,
                satelliteRepository = mockSatelliteRepository,
            )
    }

    @Test
    fun getAvailabilityStatus_unsupportedSubscription_unavailable() {
        whenever(mockSatelliteSettingsRepository.isSatelliteAttachSupported(TEST_SUB_ID))
            .thenReturn(false)

        assertThat(controller.getAvailabilityStatus(TEST_SUB_ID))
            .isEqualTo(CONDITIONALLY_UNAVAILABLE)
    }

    @Test
    fun getAvailabilityStatus_supportedSubscription_available() {
        whenever(mockSatelliteSettingsRepository.isSatelliteAttachSupported(TEST_SUB_ID))
            .thenReturn(true)

        assertThat(controller.getAvailabilityStatus(TEST_SUB_ID)).isEqualTo(AVAILABLE)
    }

    @Test
    fun getAvailabilityStatus_invalidSubscription_unavailable() {
        assertThat(controller.getAvailabilityStatus(SubscriptionManager.INVALID_SUBSCRIPTION_ID))
            .isEqualTo(CONDITIONALLY_UNAVAILABLE)
    }

    @Test
    fun onViewCreated_inFence_preferenceIsEnabled() = runBlocking {
        // Arrange: Set up repository to make the preference visible.
        whenever(mockSatelliteSettingsRepository.isSatelliteAttachSupported(TEST_SUB_ID))
            .thenReturn(true)
        whenever(mockSatelliteSettingsRepository.getSatelliteNtnConnectType(TEST_SUB_ID))
            .thenReturn(CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC)
        // The default mock for isSatelliteAccessConfigurationForCurrentLocationFlow returns true.

        // Arrange: Initialize the controller and display the preference.
        controller.initialize(TEST_SUB_ID)
        preferenceScreen.addPreference(preference)
        controller.displayPreference(preferenceScreen)

        // Act: Trigger the flow collection.
        controller.onViewCreated(TestLifecycleOwner())
        delay(100) // Allow the coroutine to collect the initial value.

        // Assert: The preference should be enabled.
        assertThat(preference.isEnabled).isTrue()
    }

    @Test
    fun isVisible_connectionManualTypeAndoutOfFence_disabled() = runBlocking {
        whenever(mockSatelliteSettingsRepository.isSatelliteAttachSupported(TEST_SUB_ID))
            .thenReturn(true)
        whenever(mockSatelliteSettingsRepository.getSatelliteNtnConnectType(TEST_SUB_ID))
            .thenReturn(CARRIER_ROAMING_NTN_CONNECT_MANUAL)

        whenever(
                mockSatelliteRepository.isSatelliteAccessConfigurationForCurrentLocationFlow(
                    TEST_SUB_ID
                )
            )
            .thenReturn(flowOf(false))

        preference.key = controller.preferenceKey

        controller.initialize(TEST_SUB_ID)
        preferenceScreen.addPreference(preference)

        controller.displayPreference(preferenceScreen)
        controller.onViewCreated(TestLifecycleOwner())
        delay(100)

        assertThat(preference.isEnabled).isEqualTo(false)
    }

    @Test
    fun isVisible_connectionHybridTypeAndoutOfFence_disabled() = runBlocking {
        whenever(mockSatelliteSettingsRepository.isSatelliteAttachSupported(TEST_SUB_ID))
            .thenReturn(true)
        whenever(mockSatelliteSettingsRepository.getSatelliteNtnConnectType(TEST_SUB_ID))
            .thenReturn(CARRIER_ROAMING_NTN_CONNECT_HYBRID)

        whenever(
                mockSatelliteRepository.isSatelliteAccessConfigurationForCurrentLocationFlow(
                    TEST_SUB_ID
                )
            )
            .thenReturn(flowOf(false))

        preference.key = controller.preferenceKey

        controller.initialize(TEST_SUB_ID)
        preferenceScreen.addPreference(preference)

        controller.displayPreference(preferenceScreen)
        controller.onViewCreated(TestLifecycleOwner())
        delay(100)

        assertThat(preference.isEnabled).isEqualTo(false)
    }

    @Test
    fun onViewCreated_satelliteAccessChangesWithManualConnectionType_updatesPreferenceEnabledState() =
        runBlocking {
            // This test verifies that the preference's enabled state is dynamically updated
            // when the isSatelliteAccessConfigurationForCurrentLocationFlow emits new values,
            // which is the core behavior of using .collect { ... } on the flow.

            // Arrange: Set up repository to make the preference visible.
            whenever(mockSatelliteSettingsRepository.isSatelliteAttachSupported(TEST_SUB_ID))
                .thenReturn(true)
            whenever(mockSatelliteSettingsRepository.getSatelliteNtnConnectType(TEST_SUB_ID))
                .thenReturn(CARRIER_ROAMING_NTN_CONNECT_MANUAL)

            // Arrange: Use a MutableStateFlow to control the satellite access value, allowing
            // us to emit new values during the test. Start with satellite access being disabled.
            val isSatelliteAccessAllowedFlow = MutableStateFlow(false)
            whenever(
                    mockSatelliteRepository.isSatelliteAccessConfigurationForCurrentLocationFlow(
                        TEST_SUB_ID
                    )
                )
                .thenReturn(isSatelliteAccessAllowedFlow)

            // Arrange: Initialize the controller and display the preference.
            controller.initialize(TEST_SUB_ID)
            preferenceScreen.addPreference(preference)
            controller.displayPreference(preferenceScreen)

            // Act: Trigger the flow collection.
            controller.onViewCreated(TestLifecycleOwner())
            delay(100) // Allow the coroutine to collect the initial value.

            // Assert: The preference should be disabled with the initial value of 'false'.
            assertThat(preference.isEnabled).isFalse()

            // Act: Emit 'true' from the flow, simulating entering a satellite coverage area.
            isSatelliteAccessAllowedFlow.value = true
            delay(100) // Allow the coroutine to collect the new value.

            // Assert: The preference should now be enabled.
            assertThat(preference.isEnabled).isTrue()

            // Act: Emit 'false' again, simulating leaving the coverage area.
            isSatelliteAccessAllowedFlow.value = false
            delay(100) // Allow the coroutine to collect the new value.

            // Assert: The preference should be disabled again.
            assertThat(preference.isEnabled).isFalse()
        }

    @Test
    fun isVisible_satelliteIsNotSupported_inVisible() = runBlocking {
        whenever(mockSatelliteSettingsRepository.isSatelliteAttachSupported(TEST_SUB_ID))
            .thenReturn(false)

        controller.initialize(TEST_SUB_ID)
        preferenceScreen.addPreference(preference)
        controller.displayPreference(preferenceScreen)

        controller.onViewCreated(TestLifecycleOwner())
        delay(100)

        assertThat(preference.isVisible).isEqualTo(false)
    }

    @Test
    fun isVisible_autoType_visible() = runBlocking {
        whenever(mockSatelliteSettingsRepository.isSatelliteAttachSupported(TEST_SUB_ID))
            .thenReturn(true)
        whenever(mockSatelliteSettingsRepository.getSatelliteNtnConnectType(TEST_SUB_ID))
            .thenReturn(CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC)

        controller.initialize(TEST_SUB_ID)
        preferenceScreen.addPreference(preference)
        controller.displayPreference(preferenceScreen)

        controller.onViewCreated(TestLifecycleOwner())
        delay(100)

        assertThat(preference.isVisible).isEqualTo(true)
    }

    companion object {
        private const val KEY = "telephony_satellite_setting_key"
        private const val TEST_SUB_ID = 5
    }
}
