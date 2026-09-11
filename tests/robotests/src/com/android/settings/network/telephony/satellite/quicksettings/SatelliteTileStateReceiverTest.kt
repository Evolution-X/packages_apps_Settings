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

package com.android.settings.network.telephony.satellite.quicksettings

import android.app.ActivityManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.satellite.SatelliteManager
import com.android.settings.R
import com.android.settings.flags.Flags
import com.android.settings.network.telephony.TelephonyFeatureProvider
import com.android.settings.network.telephony.satellite.SatelliteSettingsRepository
import com.android.settings.testutils.FakeFeatureFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowDeviceConfig
import org.robolectric.shadows.ShadowProcess
import org.robolectric.shadows.ShadowSatelliteManager
import org.robolectric.shadows.ShadowSubscriptionManager
import org.robolectric.shadows.ShadowSystemProperties

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SatelliteTileStateReceiverTest {
    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val mocks = MockitoJUnit.rule()

    @Mock private lateinit var packageManager: PackageManager
    @Mock private lateinit var pendingResult: BroadcastReceiver.PendingResult
    @Mock private lateinit var subInfo: SubscriptionInfo
    @Mock private lateinit var resources: Resources
    @Mock private lateinit var mockTelephonyManager: TelephonyManager
    @Mock private lateinit var mockJobScheduler: JobScheduler

    private lateinit var context: Context
    private var componentEnabledState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    private lateinit var receiver: SatelliteTileStateReceiver
    private lateinit var shadowSatelliteManager: ShadowSatelliteManager
    private val testDispatcher = StandardTestDispatcher()
    private val SUB_ID = 1
    private var jobId = 0

    private lateinit var fakeFeatureFactory: FakeFeatureFactory
    @Mock private lateinit var mockTelephonyFeatureProvider: TelephonyFeatureProvider
    @Mock private lateinit var mockSatelliteSettingsRepository: SatelliteSettingsRepository

    @Before
    fun setUp() {
        context = spy(RuntimeEnvironment.getApplication())
        fakeFeatureFactory = FakeFeatureFactory.setupForTest()
        `when`(fakeFeatureFactory.telephonyFeatureProvider.satelliteSettingsRepository)
            .thenReturn(mockSatelliteSettingsRepository)

        jobId = context.resources.getInteger(R.integer.satellite_eligibility_job)
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.packageManager).thenReturn(packageManager)
        `when`(packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SATELLITE))
            .thenReturn(true)
        `when`(context.resources).thenReturn(resources)
        `when`(resources.getInteger(R.integer.satellite_eligibility_job)).thenReturn(jobId)
        `when`(resources.getBoolean(R.bool.config_show_satellite_tile)).thenReturn(true)
        `when`(context.getSystemService(TelephonyManager::class.java))
            .thenReturn(mockTelephonyManager)
        `when`(context.getSystemService(JobScheduler::class.java)).thenReturn(mockJobScheduler)
        shadowSatelliteManager =
            Shadow.extract(context.getSystemService(SatelliteManager::class.java))
        `when`(subInfo.subscriptionId).thenReturn(SUB_ID)
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
        shadowOf(subscriptionManager).setActiveSubscriptionInfoList(listOf(subInfo))
        ShadowSubscriptionManager.setDefaultDataSubscriptionId(SUB_ID)
        receiver = spy(SatelliteTileStateReceiver(testDispatcher))
        `when`(receiver.goAsync()).thenReturn(pendingResult)

        // Reset the singleton state of SatelliteSupportedStateChangeHandler to ensure test
        // isolation
        SatelliteSupportedStateChangeHandler.reset()

        shadowSatelliteManager.setIsSupportedResponse(false, null)

        componentEnabledState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        `when`(packageManager.getComponentEnabledSetting(any())).thenAnswer {
            componentEnabledState
        }
        `when`(packageManager.setComponentEnabledSetting(any(), anyInt(), anyInt())).then {
            componentEnabledState = it.getArgument(1)
        }
    }

    @After
    fun tearDown() {
        ShadowDeviceConfig.reset()
        ShadowSystemProperties.reset()
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_flagDisabled_disablesTile() = runTest {
        sendBootCompletedBroadcast()

        verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        verify(receiver, never()).goAsync()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_notSystemUser_doesNothing() {
        // UID for user 1. USER_SYSTEM is 0.
        ShadowProcess.setUid(100000)
        sendBootCompletedBroadcast()

        verify(receiver, never()).goAsync()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_configIsFalse_disablesTile() = runTest {
        `when`(resources.getBoolean(R.bool.config_show_satellite_tile)).thenReturn(false)

        sendBootCompletedBroadcast()

        verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        verify(receiver, never()).goAsync()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_bootCompleted_isLteBasedNtnSupported_enablesTileAndSchedulesJob() =
        runTest(testDispatcher) {
            setLteNtnSupported(true)

            sendBootCompletedBroadcast()
            advanceUntilIdle()

            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            verifyJobScheduled()
            verify(pendingResult).finish()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_bootCompleted_isNbIotBasedNtnSupported_enablesTileAndSchedulesJob() =
        runTest(testDispatcher) {
            setLteNtnSupported(false)
            shadowSatelliteManager.setIsSupportedResponse(true, null)

            sendBootCompletedBroadcast()
            advanceUntilIdle()

            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            verifyJobScheduled()
            verify(pendingResult).finish()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_bootCompleted_noNtnSupport_disablesTileDoesNotScheduleJob() =
        runTest(testDispatcher) {
            setLteNtnSupported(false)
            shadowSatelliteManager.setIsSupportedResponse(false, null)

            sendBootCompletedBroadcast()
            advanceUntilIdle()

            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            verify(mockJobScheduler, never()).schedule(any())
            verify(pendingResult).finish()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_noSatelliteFeature_disablesTile() = runTest {
        `when`(packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SATELLITE))
            .thenReturn(false)

        sendBootCompletedBroadcast()

        verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        verify(receiver, never()).goAsync()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_unknownAction_doesNothing() = runTest {
        val intent = Intent("com.android.settings.UNKNOWN_ACTION")
        receiver.onReceive(context, intent)

        verify(receiver, never()).goAsync()
        verify(packageManager, never()).setComponentEnabledSetting(any(), anyInt(), anyInt())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_simCardStateChanged_isLteBasedNtnSupported_enablesTileAndSchedulesJob() =
        runTest(testDispatcher) {
            setLteNtnSupported(true)

            sendSimCardStateChangedBroadcast()
            advanceUntilIdle()

            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            verifyJobScheduled()
            verify(pendingResult).finish()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_simCardStateChanged_noNtnSupport_disablesTileDoesNotScheduleJob() =
        runTest(testDispatcher) {
            setLteNtnSupported(false)
            shadowSatelliteManager.setIsSupportedResponse(false, null)

            sendSimCardStateChangedBroadcast()
            advanceUntilIdle()

            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            verify(mockJobScheduler, never()).schedule(any())
            verify(pendingResult).finish()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_carrierConfigChanged_isLteBasedNtnSupported_enablesTileAndSchedulesJob() =
        runTest(testDispatcher) {
            setLteNtnSupported(true)

            sendCarrierConfigChangedBroadcast()
            advanceUntilIdle()

            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            verifyJobScheduled()
            verify(pendingResult).finish()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_carrierConfigChanged_noNtnSupport_disablesTileDoesNotScheduleJob() =
        runTest(testDispatcher) {
            setLteNtnSupported(false)
            shadowSatelliteManager.setIsSupportedResponse(false, null)

            sendCarrierConfigChangedBroadcast()
            advanceUntilIdle()

            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            verify(mockJobScheduler, never()).schedule(any())
            verify(pendingResult).finish()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_defaultDataSubscriptionChanged_isLteBasedNtnSupported_enablesTileAndSchedulesJob() =
        runTest(testDispatcher) {
            setLteNtnSupported(true)

            val intent = Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)
            receiver.onReceive(context, intent)
            advanceUntilIdle()

            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            verifyJobScheduled()
            verify(pendingResult).finish()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onReceive_defaultDataSubscriptionChanged_invalidSubId_cancelsJob() =
        runTest(testDispatcher) {
            ShadowSubscriptionManager.setDefaultDataSubscriptionId(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            )

            val intent = Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)
            receiver.onReceive(context, intent)
            advanceUntilIdle()

            verify(mockJobScheduler).cancel(jobId)
            verify(mockJobScheduler, never()).schedule(any())
            verify(pendingResult).finish()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onSupportedStateChanged_ntnBecomesSupported_enablesTile() =
        runTest(testDispatcher) {
            setLteNtnSupported(false)
            sendBootCompletedBroadcast()
            advanceUntilIdle()

            // Simulate the state changing to supported
            shadowSatelliteManager.triggerOnSupportedStateChanged(true)
            advanceUntilIdle()

            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onSupportedStateChanged_ntnBecomesUnsupported_disablesTile() =
        runTest(testDispatcher) {
            setLteNtnSupported(false)
            shadowSatelliteManager.setIsSupportedResponse(true, null)
            sendBootCompletedBroadcast()
            advanceUntilIdle()
            // Verify that the tile is initially enabled when NB-IoT NTN is supported before
            // testing the state change.
            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

            // Simulate the state changing to not supported
            shadowSatelliteManager.triggerOnSupportedStateChanged(false)
            advanceUntilIdle()

            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun onSupportedStateChanged_lteNtnSupported_tileStateUnchanged() =
        runTest(testDispatcher) {
            setLteNtnSupported(true)
            sendBootCompletedBroadcast()
            advanceUntilIdle()

            // Verify that the tile is initially enabled
            verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            clearInvocations(packageManager)

            // Simulate the NB-IoT state changing to not supported
            shadowSatelliteManager.triggerOnSupportedStateChanged(false)
            advanceUntilIdle()

            // The tile should remain enabled because LTE NTN is supported; with no state
            // transition the PackageManager write is skipped.
            verify(packageManager, never()).setComponentEnabledSetting(any(), anyInt(), anyInt())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun registerForSupportedStateChanged_throwsException_doesNotCrash() {
        val mockSatelliteManager = mock(SatelliteManager::class.java)
        `when`(context.getSystemService(SatelliteManager::class.java))
            .thenReturn(mockSatelliteManager)
        `when`(mockSatelliteManager.registerForSupportedStateChanged(any(), any()))
            .thenThrow(IllegalStateException("Registration failed"))

        // This should not crash
        SatelliteSupportedStateChangeHandler.register(context, testDispatcher)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun registerForSupportedStateChanged_satelliteManagerNull_doesNothing() {
        `when`(context.getSystemService(SatelliteManager::class.java)).thenReturn(null)

        // This should not crash
        SatelliteSupportedStateChangeHandler.register(context, testDispatcher)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    fun isSatelliteTileFeatureEnabled_inTestHarness_returnsFalse() {
        ShadowSystemProperties.override("ro.test_harness", "true")

        assertThat(SatelliteTileStateReceiver.isSatelliteTileFeatureEnabled(context)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SATELLITE_TILE)
    @Config(shadows = [TestShadowActivityManager::class])
    fun isSatelliteTileFeatureEnabled_inUserTestHarness_returnsFalse() {
        TestShadowActivityManager.setIsRunningInUserTestHarness(true)

        assertThat(SatelliteTileStateReceiver.isSatelliteTileFeatureEnabled(context)).isFalse()
    }

    @Test
    fun isTileServiceEnabled_componentEnabled_returnsTrue() {
        componentEnabledState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        assertThat(SatelliteTileStateReceiver.isTileServiceEnabled(context)).isTrue()
    }

    @Test
    fun isTileServiceEnabled_componentDisabled_returnsFalse() {
        componentEnabledState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        assertThat(SatelliteTileStateReceiver.isTileServiceEnabled(context)).isFalse()
    }

    @Test
    fun isTileServiceEnabled_componentDefault_returnsFalse() {
        componentEnabledState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        assertThat(SatelliteTileStateReceiver.isTileServiceEnabled(context)).isFalse()
    }

    @Test
    fun updateTileServiceEnabledState_ntnSupported_stateChanged_enablesTileAndSchedulesJob() {
        componentEnabledState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        SatelliteTileStateReceiver.updateTileServiceEnabledState(context, true)

        verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        verifyJobScheduled()
    }

    @Test
    fun updateTileServiceEnabledState_ntnSupported_stateUnchanged_skipsWriteAndDoesNotScheduleJob() {
        componentEnabledState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        SatelliteTileStateReceiver.updateTileServiceEnabledState(context, true)

        // No state transition: the PackageManager write is skipped.
        verify(packageManager, never()).setComponentEnabledSetting(any(), anyInt(), anyInt())
        verify(mockJobScheduler, never()).schedule(any())
    }

    @Test
    fun updateTileServiceEnabledState_ntnNotSupported_stateChanged_disablesTileAndDoesNotScheduleJob() {
        componentEnabledState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        SatelliteTileStateReceiver.updateTileServiceEnabledState(context, false)

        verifyTileEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        verify(mockJobScheduler, never()).schedule(any())
    }

    @Test
    fun updateTileServiceEnabledState_ntnNotSupported_stateUnchanged_skipsWriteAndDoesNotScheduleJob() {
        componentEnabledState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        SatelliteTileStateReceiver.updateTileServiceEnabledState(context, false)

        // No state transition: the PackageManager write is skipped.
        verify(packageManager, never()).setComponentEnabledSetting(any(), anyInt(), anyInt())
        verify(mockJobScheduler, never()).schedule(any())
    }

    private fun sendBootCompletedBroadcast() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)
    }

    private fun sendSimCardStateChangedBroadcast() {
        val intent = Intent(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED)
        receiver.onReceive(context, intent)
    }

    private fun sendCarrierConfigChangedBroadcast() {
        val intent = Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)
        receiver.onReceive(context, intent)
    }

    private fun verifyTileEnabledState(expectedState: Int) {
        val componentName = ComponentName(context, SatelliteTileService::class.java)
        verify(packageManager)
            .setComponentEnabledSetting(
                eq(componentName),
                eq(expectedState),
                eq(PackageManager.DONT_KILL_APP),
            )
    }

    /**
     * Configures the test environment to simulate whether LTE NTN is supported.
     *
     * @param isSupported `true` to simulate that LTE NTN is supported, `false` otherwise.
     */
    private fun setLteNtnSupported(isSupported: Boolean) {
        // A non-empty set of restriction reasons means that attach is restricted.
        val reasons = if (isSupported) emptySet() else setOf(1)
        shadowSatelliteManager.setAttachRestrictionReasonsForCarrier(SUB_ID, reasons)

        `when`(mockSatelliteSettingsRepository.isSatelliteAttachSupported(SUB_ID))
            .thenReturn(isSupported)
        if (isSupported) {
            `when`(mockSatelliteSettingsRepository.getSatelliteNtnConnectType(SUB_ID))
                .thenReturn(CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC)
        }
    }

    private fun verifyJobScheduled() {
        val jobInfoCaptor = ArgumentCaptor.forClass(JobInfo::class.java)
        verify(mockJobScheduler, atLeastOnce()).schedule(jobInfoCaptor.capture())

        val jobInfo = jobInfoCaptor.value
        assertThat(jobInfo.id).isEqualTo(jobId)
        assertThat(jobInfo.service.className)
            .isEqualTo(SatelliteEligibilityJobService::class.java.name)
        assertThat(jobInfo.isPersisted).isFalse()
        assertThat(jobInfo.triggerContentUris).isNotEmpty()
        // Verify that the job is scheduled immediately (override deadline is set to 0)
        assertThat(jobInfo.maxExecutionDelayMillis).isEqualTo(0)
    }

    @Implements(ActivityManager::class)
    class TestShadowActivityManager : ShadowActivityManager() {
        companion object {
            private var isRunningInUserTestHarness = false

            @JvmStatic
            fun setIsRunningInUserTestHarness(value: Boolean) {
                isRunningInUserTestHarness = value
            }

            @JvmStatic
            @Implementation
            fun isRunningInUserTestHarness(): Boolean {
                return isRunningInUserTestHarness
            }

            @Resetter
            fun reset() {
                isRunningInUserTestHarness = false
            }
        }
    }
}
