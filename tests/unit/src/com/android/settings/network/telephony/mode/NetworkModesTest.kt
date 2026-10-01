/*
 * Copyright (C) 2026 The Evolution X Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.android.settings.network.telephony.mode

import android.telephony.RadioAccessFamily
import android.telephony.TelephonyManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkModesTest {

    @Test
    fun mergePreferredNetworkModeRaf_preservesSupportedFutureBits() {
        val controlledRaf =
            Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(
                    TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA
                )
            )
        val requestedRaf =
            Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(TelephonyManager.NETWORK_MODE_NR_LTE)
            )
        val futureBit = 1L shl 40
        val previousUserRaf = controlledRaf or futureBit
        val supportedRaf = controlledRaf or futureBit

        val merged =
            NetworkModes.mergePreferredNetworkModeRaf(
                previousUserRaf,
                supportedRaf,
                TelephonyManager.NETWORK_MODE_NR_LTE,
            )

        assertThat(merged).isEqualTo(requestedRaf or futureBit)
    }

    @Test
    fun mergePreferredNetworkModeRaf_dropsUnsupportedFutureBits() {
        val controlledRaf =
            Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(
                    TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA
                )
            )
        val futureBit = 1L shl 40

        val merged =
            NetworkModes.mergePreferredNetworkModeRaf(
                controlledRaf or futureBit,
                controlledRaf,
                TelephonyManager.NETWORK_MODE_LTE_ONLY,
            )

        assertThat(merged and futureBit).isEqualTo(0L)
        assertThat(merged)
            .isEqualTo(
                Integer.toUnsignedLong(
                    RadioAccessFamily.getRafFromNetworkType(
                        TelephonyManager.NETWORK_MODE_LTE_ONLY
                    )
                )
            )
    }

    @Test
    fun mergePreferredNetworkModeRaf_replacesOnlyControlledDomain() {
        val fullRaf =
            Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(
                    TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA
                )
            )
        val requestedRaf =
            Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(TelephonyManager.NETWORK_MODE_GSM_ONLY)
            )

        val merged =
            NetworkModes.mergePreferredNetworkModeRaf(
                fullRaf,
                fullRaf,
                TelephonyManager.NETWORK_MODE_GSM_ONLY,
            )

        assertThat(merged).isEqualTo(requestedRaf)
    }
    @Test
    fun getStandardNetworkModeFromRaf_roundTripsEveryStandardMode() {
        for (mode in 0..33) {
            val raf =
                Integer.toUnsignedLong(RadioAccessFamily.getRafFromNetworkType(mode))
            val resolved = NetworkModes.getStandardNetworkModeFromRaf(raf)

            // Modes 0 and 3 intentionally share the same GSM|WCDMA RAF and canonicalize to 0.
            if (mode == TelephonyManager.NETWORK_MODE_GSM_UMTS) {
                assertThat(resolved).isEqualTo(TelephonyManager.NETWORK_MODE_WCDMA_PREF)
            } else {
                assertThat(resolved).isEqualTo(mode)
            }
        }
    }

    @Test
    fun getStandardNetworkModeFromRaf_futureOnly_returnsUnknown() {
        assertThat(NetworkModes.getStandardNetworkModeFromRaf(1L shl 40))
            .isEqualTo(NetworkModes.NETWORK_MODE_UNKNOWN)
    }

    @Test
    fun getStandardNetworkModeFromRaf_unrepresentableStandardCombination_returnsUnknown() {
        val gsm =
            Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(TelephonyManager.NETWORK_MODE_GSM_ONLY)
            )
        val nr =
            Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(TelephonyManager.NETWORK_MODE_NR_ONLY)
            )

        // Android has no legacy NETWORK_MODE_NR_GSM constant.
        assertThat(NetworkModes.getStandardNetworkModeFromRaf(nr or gsm))
            .isEqualTo(NetworkModes.NETWORK_MODE_UNKNOWN)
    }


}
