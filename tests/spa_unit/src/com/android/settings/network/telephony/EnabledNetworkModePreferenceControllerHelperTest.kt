/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.android.settings.network.telephony

import android.content.Context
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy

@RunWith(AndroidJUnit4::class)
class EnabledNetworkModePreferenceControllerHelperTest {

    private var context: Context = spy(ApplicationProvider.getApplicationContext()) {}

    @Before
    fun setUp() {
        CarrierConfigRepository.resetForTest()
        CarrierConfigRepository.setBooleanForTest(
            SUB_ID,
            CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL,
            true,
        )
    }

    @Test
    fun getNetworkModePreferenceType_hideCarrierNetworkSettings_returnsNone() {
        CarrierConfigRepository.setBooleanForTest(
            SUB_ID,
            CarrierConfigManager.KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL,
            true,
        )

        assertThat(getNetworkModePreferenceType(context, SUB_ID))
            .isEqualTo(NetworkModePreferenceType.None)
    }

    @Test
    fun getNetworkModePreferenceType_hidePreferredNetworkType_returnsNone() {
        CarrierConfigRepository.setBooleanForTest(
            SUB_ID,
            CarrierConfigManager.KEY_HIDE_PREFERRED_NETWORK_TYPE_BOOL,
            true,
        )

        assertThat(getNetworkModePreferenceType(context, SUB_ID))
            .isEqualTo(NetworkModePreferenceType.None)
    }

    @Test
    fun getNetworkModePreferenceType_carrierConfigNotReady_returnsNone() {
        CarrierConfigRepository.setBooleanForTest(
            SUB_ID,
            CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL,
            false,
        )

        assertThat(getNetworkModePreferenceType(context, SUB_ID))
            .isEqualTo(NetworkModePreferenceType.None)
    }

    @Test
    fun getNetworkModePreferenceType_worldPhone_returnsPreferredNetworkMode() {
        CarrierConfigRepository.setBooleanForTest(
            SUB_ID,
            CarrierConfigManager.KEY_WORLD_PHONE_BOOL,
            true,
        )

        assertThat(getNetworkModePreferenceType(context, SUB_ID))
            .isEqualTo(NetworkModePreferenceType.PreferredNetworkMode)
    }

    @Test
    fun getNetworkModePreferenceType_validSubscription_returnsEnabledNetworkMode() {
        assertThat(getNetworkModePreferenceType(context, SUB_ID))
            .isEqualTo(NetworkModePreferenceType.EnabledNetworkMode)
    }

    @Test
    fun getNetworkModePreferenceType_invalidSubscription_returnsNone() {
        assertThat(
                getNetworkModePreferenceType(
                    context,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                )
            )
            .isEqualTo(NetworkModePreferenceType.None)
    }

    private companion object {
        const val SUB_ID = 10
    }
}
