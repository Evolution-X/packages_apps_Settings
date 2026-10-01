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

import android.content.ContextWrapper
import android.os.UserManager
import android.telephony.CarrierConfigManager
import android.telephony.RadioAccessFamily
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.android.settings.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

class MobileNetworkDataTest {
    private val mockUserManager = mock<UserManager>()
    private val mockTelephonyManager = mock<TelephonyManager>()
    private val mockSubscriptionManager = mock<SubscriptionManager>()
    private val mockSubscriptionInfo = mock<SubscriptionInfo>()

    private val context =
        object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getApplicationContext() = this

            override fun getSystemService(name: String): Any? =
                when (name) {
                    getSystemServiceName(UserManager::class.java) -> mockUserManager
                    getSystemServiceName(TelephonyManager::class.java) -> mockTelephonyManager
                    getSystemServiceName(SubscriptionManager::class.java) -> mockSubscriptionManager
                    else -> super.getSystemService(name)
                }
        }

    private lateinit var mobileNetworkData: MobileNetworkData
    private val testScope = TestScope(UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        CarrierConfigRepository.resetForTest()
        CarrierConfigRepository.setBooleanForTest(
            0,
            CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL,
            true,
        )
        mockUserManager.stub { on { isAdminUser } doReturn true }
        mockTelephonyManager.stub {
            on { isDataCapable } doReturn true
            on { isDeviceVoiceCapable } doReturn true
            on { createForSubscriptionId(anyInt()) } doReturn mockTelephonyManager
            on {
                isRadioInterfaceCapabilitySupported(
                    TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK
                )
            } doReturn true
            on { primaryImei } doReturn IMEI_1
        }
        mockSubscriptionInfo.stub { on { getMccString() } doReturn MCC }
        mockSubscriptionManager.stub {
            on { getActiveSubscriptionInfo(0) } doReturn mockSubscriptionInfo
            on { getPhoneNumber(0) } doReturn PHONE_NUMBER
            on { activeSubscriptionIdList } doReturn intArrayOf(0)
        }

        mobileNetworkData = MobileNetworkData(context, null, 0)
    }

    @Test
    fun isMobileNetworkAvailable_byDefault_returnTrue() {
        assertThat(mobileNetworkData.isMobileNetworkAvailable()).isTrue()
    }

    @Test
    fun isMobileNetworkAvailable_isNotAdminUser_returnFalse() {
        mockUserManager.stub { on { isAdminUser } doReturn false }

        mobileNetworkData = MobileNetworkData(context, null, 0)

        assertThat(mobileNetworkData.isMobileNetworkAvailable()).isFalse()
    }

    @Test
    fun isAvailable_noDataNorVoiceCapable_returnFalse() {
        mockTelephonyManager.stub {
            on { isDataCapable } doReturn false
            on { isDeviceVoiceCapable } doReturn false
        }

        mobileNetworkData = MobileNetworkData(context, null, 0)

        assertThat(mobileNetworkData.isMobileNetworkAvailable()).isFalse()
    }

    @Test
    fun getPhoneNumber_invalidSubscriptionId_returnEmptyString() {
        mobileNetworkData = MobileNetworkData(context, null, INVALID_SUBSCRIPTION_ID)

        assertThat(mobileNetworkData.getPhoneNumber().isEmpty()).isTrue()
    }

    @Test
    fun getPhoneNumber_hasPhoneNumber_returnFormattedPhoneNumber() {
        assertThat(mobileNetworkData.getPhoneNumber()).isEqualTo(FORMATTED_PHONE_NUMBER)
    }

    @Test
    fun refreshEnabledNetworkModeData_updatesSelectedAndEffectiveSummary() = runBlocking {
        val selectedMode = TelephonyManager.NETWORK_MODE_NR_LTE
        val effectiveMode = TelephonyManager.NETWORK_MODE_LTE_ONLY
        val selectedRaf =
            Integer.toUnsignedLong(RadioAccessFamily.getRafFromNetworkType(selectedMode))
        val effectiveRaf =
            Integer.toUnsignedLong(RadioAccessFamily.getRafFromNetworkType(effectiveMode))

        mockTelephonyManager.stub {
            on { supportedRadioAccessFamily } doReturn selectedRaf
            on {
                getAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
                )
            } doReturn selectedRaf
            on {
                getAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER
                )
            } doReturn effectiveRaf
            on {
                getAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER
                )
            } doReturn -1L
            on {
                getAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_TEST
                )
            } doReturn -1L
            on {
                getAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G
                )
            } doReturn -1L
        }

        mobileNetworkData = MobileNetworkData(context, testScope, 0)
        mobileNetworkData.refreshEnabledNetworkModeData()
        delay(100)

        assertThat(mobileNetworkData.enabledNetworkModeFlow.value.summary)
            .isEqualTo(
                context.getString(
                    R.string.network_mode_summary_effective,
                    "5G + 4G",
                    "4G",
                )
            )
    }

    @Test
    fun imeiDataFlow_refresh_updated() = runBlocking {
        mockTelephonyManager.stub {
            on { imei } doReturn IMEI_1
            on { getImei(0) } doReturn IMEI_1
            on { activeModemCount } doReturn 1
        }

        mobileNetworkData = MobileNetworkData(context, testScope, 0)
        delay(500)

        val imeiInfoData = mobileNetworkData.imeiInfoDataFlow.value
        assertThat(imeiInfoData.isAvailable).isTrue()
        assertThat(imeiInfoData.imeiData?.imei).isEqualTo(IMEI_1)
        assertThat(imeiInfoData.title).isEqualTo(context.getString(R.string.status_imei))
        assertThat(imeiInfoData.summary.toString()).isEqualTo(IMEI_1)
    }

    @Test
    fun refreshImeiData_multiSim_setsMultiSimTitle() = runBlocking {
        mockTelephonyManager.stub {
            on { activeModemCount } doReturn 2
            on { getImei(0) } doReturn IMEI_1
            on { getImei(1) } doReturn IMEI_2
            on { imei } doReturn IMEI_1
        }

        mobileNetworkData = MobileNetworkData(context, testScope, 0)
        delay(500)

        val imeiInfoData = mobileNetworkData.imeiInfoDataFlow.value
        assertThat(imeiInfoData.title).isEqualTo(context.getString(R.string.imei_multi_sim, 1))
        assertThat(imeiInfoData.imeiData?.imei).isEqualTo(IMEI_1)
    }

    companion object {
        private const val MCC = "310"
        private const val PHONE_NUMBER = "8881234567"
        private const val FORMATTED_PHONE_NUMBER = "(888) 123-4567"
        private const val IMEI_1 = "111111111111115"
        private const val IMEI_2 = "222222222222225"
    }
}
