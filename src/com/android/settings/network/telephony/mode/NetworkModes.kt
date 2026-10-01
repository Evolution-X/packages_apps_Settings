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

package com.android.settings.network.telephony.mode

import android.telephony.TelephonyManager
import com.google.common.collect.ImmutableBiMap

/** Network mode related utilities. */
object NetworkModes {
    const val NETWORK_MODE_UNKNOWN = -1

    private val StandardModeRaf: Long =
        Integer.toUnsignedLong(
            android.telephony.RadioAccessFamily.getRafFromNetworkType(
                TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA
            )
        )

    private val GsmRaf: Long =
        TelephonyManager.NETWORK_TYPE_BITMASK_GSM or
            TelephonyManager.NETWORK_TYPE_BITMASK_GPRS or
            TelephonyManager.NETWORK_TYPE_BITMASK_EDGE

    private val CdmaRaf: Long =
        TelephonyManager.NETWORK_TYPE_BITMASK_CDMA or
            TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT

    private val EvdoRaf: Long =
        TelephonyManager.NETWORK_TYPE_BITMASK_EHRPD or
            TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_0 or
            TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_A or
            TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_B

    private val WcdmaRaf: Long =
        TelephonyManager.NETWORK_TYPE_BITMASK_UMTS or
            TelephonyManager.NETWORK_TYPE_BITMASK_HSDPA or
            TelephonyManager.NETWORK_TYPE_BITMASK_HSUPA or
            TelephonyManager.NETWORK_TYPE_BITMASK_HSPA or
            TelephonyManager.NETWORK_TYPE_BITMASK_HSPAP

    private val LteRaf: Long =
        TelephonyManager.NETWORK_TYPE_BITMASK_LTE or
            TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA

    /**
     * Resolves a USER-reason RAF to a standard preferred-network-mode constant.
     *
     * Returns [NETWORK_MODE_UNKNOWN] when the standard 2G/3G/4G/5G portion cannot be represented
     * exactly by Android's legacy preferred-network-mode model. Future radio bits are ignored.
     */
    @JvmStatic
    fun getStandardNetworkModeFromRaf(raf: Long): Int {
        val standardRaf = raf and StandardModeRaf
        if (standardRaf == 0L) {
            return NETWORK_MODE_UNKNOWN
        }

        val mode =
            android.telephony.RadioAccessFamily.getNetworkTypeFromRaf(standardRaf.toInt())
        val roundTripRaf =
            Integer.toUnsignedLong(
                android.telephony.RadioAccessFamily.getRafFromNetworkType(mode)
            )

        return if (normalizeStandardRaf(roundTripRaf) == normalizeStandardRaf(standardRaf)) {
            mode
        } else {
            NETWORK_MODE_UNKNOWN
        }
    }

    private fun normalizeStandardRaf(raf: Long): Long {
        var normalized = raf and StandardModeRaf
        normalized = expandFamily(normalized, GsmRaf)
        normalized = expandFamily(normalized, WcdmaRaf)
        normalized = expandFamily(normalized, CdmaRaf)
        normalized = expandFamily(normalized, EvdoRaf)
        normalized = expandFamily(normalized, LteRaf)
        return normalized
    }

    private fun expandFamily(raf: Long, family: Long): Long =
        if ((raf and family) != 0L) raf or family else raf

    @JvmStatic
    fun getStandardModeRaf(): Long = StandardModeRaf

    @JvmStatic
    fun get2gRaf(): Long = GsmRaf or CdmaRaf

    @JvmStatic
    fun get3gRaf(): Long = WcdmaRaf or EvdoRaf or TelephonyManager.NETWORK_TYPE_BITMASK_TD_SCDMA

    @JvmStatic
    fun get4gRaf(): Long = LteRaf

    @JvmStatic
    fun get5gRaf(): Long = TelephonyManager.NETWORK_TYPE_BITMASK_NR

    private val LteToNrNetworkModeMap =
        ImmutableBiMap.builder<Int, Int>()
            .put(TelephonyManager.NETWORK_MODE_LTE_ONLY, TelephonyManager.NETWORK_MODE_NR_LTE)
            .put(
                TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO,
                TelephonyManager.NETWORK_MODE_NR_LTE_CDMA_EVDO,
            )
            .put(
                TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA,
                TelephonyManager.NETWORK_MODE_NR_LTE_GSM_WCDMA,
            )
            .put(
                TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA,
                TelephonyManager.NETWORK_MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA,
            )
            .put(
                TelephonyManager.NETWORK_MODE_LTE_WCDMA,
                TelephonyManager.NETWORK_MODE_NR_LTE_WCDMA,
            )
            .put(
                TelephonyManager.NETWORK_MODE_LTE_TDSCDMA,
                TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA,
            )
            .put(
                TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_GSM,
                TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_GSM,
            )
            .put(
                TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_WCDMA,
                TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_WCDMA,
            )
            .put(
                TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_GSM_WCDMA,
                TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_GSM_WCDMA,
            )
            .put(
                TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA,
                TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA,
            )
            .build()

    /**
     * Returns the USER-reason RAF to write for a preferred network mode.
     *
     * The legacy preferred-mode selector owns the standard 2G/3G/4G/5G domain only. Any
     * hardware-supported future radio bits already present in the USER reason (for example NTN)
     * are preserved so changing a terrestrial preference cannot accidentally disable them.
     */
    @JvmStatic
    fun mergePreferredNetworkModeRaf(
        previousUserRaf: Long,
        supportedRaf: Long,
        requestedNetworkMode: Int,
    ): Long {
        val controlledRaf =
            Integer.toUnsignedLong(
                android.telephony.RadioAccessFamily.getRafFromNetworkType(
                    TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA
                )
            )
        val requestedRaf =
            Integer.toUnsignedLong(
                android.telephony.RadioAccessFamily.getRafFromNetworkType(requestedNetworkMode)
            )
        if (requestedRaf == 0L) {
            return previousUserRaf
        }
        val preservedRaf = previousUserRaf and supportedRaf and controlledRaf.inv()
        return requestedRaf or preservedRaf
    }

    /**
     * Transforms LTE network mode to 5G network mode.
     *
     * @param networkMode an LTE network mode without 5G.
     * @return the corresponding network mode with 5G.
     */
    @JvmStatic
    fun addNrToLteNetworkMode(networkMode: Int): Int =
        LteToNrNetworkModeMap.getOrElse(networkMode) { networkMode }

    /**
     * Transforms NR5G network mode to LTE network mode.
     *
     * @param networkMode an 5G network mode.
     * @return the corresponding network mode without 5G.
     */
    @JvmStatic
    fun reduceNrToLteNetworkMode(networkMode: Int): Int =
        LteToNrNetworkModeMap.inverse().getOrElse(networkMode) { networkMode }
}
