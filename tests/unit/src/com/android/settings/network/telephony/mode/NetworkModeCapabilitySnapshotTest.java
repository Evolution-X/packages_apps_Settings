/*
 * Copyright (C) 2026 The Evolution X Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.android.settings.network.telephony.mode;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.telephony.RadioAccessFamily;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class NetworkModeCapabilitySnapshotTest {

    @Mock
    private TelephonyManager mTelephonyManager;

    private Context mContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        when(mTelephonyManager.getAllowedNetworkTypesForReason(anyInt())).thenReturn(-1L);
        when(mTelephonyManager.isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK))
                .thenReturn(true);
    }

    @Test
    public void carrierRestriction_separatesUserAndEffectiveModes() {
        final long supported = raf(TelephonyManager.NETWORK_MODE_NR_LTE);
        final long user = raf(TelephonyManager.NETWORK_MODE_NR_LTE);
        final long carrier = raf(TelephonyManager.NETWORK_MODE_LTE_ONLY);

        doReturn(supported).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(user).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        doReturn(carrier).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER);

        final NetworkModeCapabilitySnapshot snapshot =
                NetworkModeCapabilitySnapshot.capture(mContext, mTelephonyManager);

        assertThat(snapshot.getUserNetworkMode())
                .isEqualTo(TelephonyManager.NETWORK_MODE_NR_LTE);
        assertThat(snapshot.getEffectiveNetworkMode())
                .isEqualTo(TelephonyManager.NETWORK_MODE_LTE_ONLY);
        assertThat(snapshot.getEffectiveRaf()).isEqualTo(carrier);
        assertThat(snapshot.isEffectiveStateKnown()).isTrue();
    }

    @Test
    public void unknownRuntimeReasons_doNotReduceUserSelection() {
        final long supported = raf(TelephonyManager.NETWORK_MODE_NR_LTE_WCDMA);
        final long user = raf(TelephonyManager.NETWORK_MODE_NR_LTE);

        doReturn(supported).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(user).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);

        final NetworkModeCapabilitySnapshot snapshot =
                NetworkModeCapabilitySnapshot.capture(mContext, mTelephonyManager);

        assertThat(snapshot.getEffectiveRaf()).isEqualTo(user);
        assertThat(snapshot.getEffectiveNetworkMode())
                .isEqualTo(TelephonyManager.NETWORK_MODE_NR_LTE);
    }

    @Test
    public void unknownUserReason_neverClaimsAnEffectiveMode() {
        doReturn(raf(TelephonyManager.NETWORK_MODE_NR_LTE))
                .when(mTelephonyManager)
                .getSupportedRadioAccessFamily();
        doReturn(-1L).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);

        final NetworkModeCapabilitySnapshot snapshot =
                NetworkModeCapabilitySnapshot.capture(mContext, mTelephonyManager);

        assertThat(snapshot.getUserNetworkMode()).isEqualTo(NetworkModes.NETWORK_MODE_UNKNOWN);
        assertThat(snapshot.getEffectiveNetworkMode())
                .isEqualTo(NetworkModes.NETWORK_MODE_UNKNOWN);
        assertThat(snapshot.isEffectiveStateKnown()).isFalse();
    }

    @Test
    public void zeroEnable2gMask_isTreatedAsCorruptSentinel() {
        final long modeRaf = raf(TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA);
        doReturn(modeRaf).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(modeRaf).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        doReturn(0L).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G);

        final NetworkModeCapabilitySnapshot snapshot =
                NetworkModeCapabilitySnapshot.capture(mContext, mTelephonyManager);

        assertThat(snapshot.getEffectiveRaf()).isEqualTo(modeRaf);
        assertThat(snapshot.getEnable2gRaf()).isEqualTo(-1L);
    }

    @Test
    public void advanced5gCapabilities_areCapturedWhenAvailable() {
        final long modeRaf = raf(TelephonyManager.NETWORK_MODE_NR_LTE);
        doReturn(modeRaf).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(modeRaf).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        when(mTelephonyManager.isRadioInterfaceCapabilitySupported(anyString()))
                .thenReturn(false);
        when(mTelephonyManager.isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK))
                .thenReturn(true);
        when(mTelephonyManager.isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE))
                .thenReturn(true);
        doReturn(true).when(mTelephonyManager).isNrDualConnectivityEnabled();
        doReturn(true).when(mTelephonyManager).isVoNrEnabled();

        final NetworkModeCapabilitySnapshot snapshot =
                NetworkModeCapabilitySnapshot.capture(mContext, mTelephonyManager);

        assertThat(snapshot.isAllowedNetworkTypesBitmaskSupported()).isTrue();
        assertThat(snapshot.isNrDualConnectivitySupported()).isTrue();
        assertThat(snapshot.isNrDualConnectivityStateKnown()).isTrue();
        assertThat(snapshot.isNrDualConnectivityEnabled()).isTrue();
        assertThat(snapshot.isVoNrStateKnown()).isTrue();
        assertThat(snapshot.isVoNrEnabled()).isTrue();
    }

    @Test
    public void missingBitmaskCapability_producesUnknownEffectiveState() {
        final long modeRaf = raf(TelephonyManager.NETWORK_MODE_NR_LTE);
        doReturn(modeRaf).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(modeRaf).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        when(mTelephonyManager.isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK))
                .thenReturn(false);

        final NetworkModeCapabilitySnapshot snapshot =
                NetworkModeCapabilitySnapshot.capture(mContext, mTelephonyManager);

        assertThat(snapshot.isAllowedNetworkTypesBitmaskSupported()).isFalse();
        assertThat(snapshot.isEffectiveStateKnown()).isFalse();
    }

    @Test
    public void missingHardwareCapabilities_producesUnknownEffectiveState() {
        doReturn(0L).when(mTelephonyManager).getSupportedRadioAccessFamily();

        final NetworkModeCapabilitySnapshot snapshot =
                NetworkModeCapabilitySnapshot.capture(mContext, mTelephonyManager);

        assertThat(snapshot.hasKnownHardwareCapabilities()).isFalse();
        assertThat(snapshot.getEffectiveRaf()).isEqualTo(0L);
        assertThat(snapshot.isEffectiveStateKnown()).isFalse();
    }

    private static long raf(int networkMode) {
        return Integer.toUnsignedLong(RadioAccessFamily.getRafFromNetworkType(networkMode));
    }
}
