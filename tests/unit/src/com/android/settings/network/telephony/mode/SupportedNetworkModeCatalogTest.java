/*
 * Copyright (C) 2026 The Evolution X Project
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

package com.android.settings.network.telephony.mode;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class SupportedNetworkModeCatalogTest {

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
    public void worldModem_exposesCuratedUserFacingModes() {
        final int fullMode =
                TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA;
        final long fullRaf = Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(fullMode));
        doReturn(fullRaf).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(fullRaf).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);

        final List<NetworkModeEntry> entries =
                SupportedNetworkModeCatalog.getSupportedNetworkModes(
                        mContext, mTelephonyManager);

        assertThat(entries).hasSize(10);

        final int[] expectedModes = {
                TelephonyManager.NETWORK_MODE_NR_ONLY,
                TelephonyManager.NETWORK_MODE_NR_LTE,
                TelephonyManager.NETWORK_MODE_NR_LTE_WCDMA,
                TelephonyManager.NETWORK_MODE_NR_LTE_GSM_WCDMA,
                TelephonyManager.NETWORK_MODE_LTE_ONLY,
                TelephonyManager.NETWORK_MODE_LTE_WCDMA,
                TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA,
                TelephonyManager.NETWORK_MODE_WCDMA_ONLY,
                TelephonyManager.NETWORK_MODE_WCDMA_PREF,
                TelephonyManager.NETWORK_MODE_GSM_ONLY,
        };

        final Set<Long> rafs = new HashSet<>();
        for (NetworkModeEntry entry : entries) {
            assertThat(entry.getBasicLabel()).isNotEmpty();
            assertThat(entry.getTechnicalLabel()).isNotEmpty();
            assertThat(entry.isHardwareSupported()).isTrue();
            assertThat(rafs.add(entry.getRaf())).isTrue();
        }
        for (int mode : expectedModes) {
            assertThat(find(entries, mode)).isNotNull();
        }

        assertThat(find(entries, TelephonyManager.NETWORK_MODE_TDSCDMA_ONLY)).isNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_CDMA_NO_EVDO)).isNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_EVDO_NO_CDMA)).isNull();
    }

    @Test
    public void missingBitmaskCapability_returnsEmptyModernCatalog() {
        final int fullMode =
                TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA;
        final long fullRaf = Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(fullMode));
        doReturn(fullRaf).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(fullRaf).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        when(mTelephonyManager.isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK))
                .thenReturn(false);

        assertThat(SupportedNetworkModeCatalog.getSupportedNetworkModes(
                mContext, mTelephonyManager)).isEmpty();

        final NetworkModeEntry fallback =
                SupportedNetworkModeCatalog.describeNetworkMode(
                        mContext, mTelephonyManager, TelephonyManager.NETWORK_MODE_NR_LTE);
        assertThat(fallback).isNotNull();
        assertThat(fallback.isSelectable()).isFalse();
    }

    @Test
    public void worldModem_doesNotExposeLegacyRadioFamilies() {
        final int fullMode =
                TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA;
        final long fullRaf = Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(fullMode));
        doReturn(fullRaf).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(fullRaf).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);

        final List<NetworkModeEntry> entries =
                SupportedNetworkModeCatalog.getSupportedNetworkModes(
                        mContext, mTelephonyManager);

        for (NetworkModeEntry entry : entries) {
            assertThat(entry.isLegacyRadioFamilyMode()).isFalse();
        }
    }

    @Test
    public void unsupportedCuratedModes_areHiddenByHardwareCapability() {
        final int supportedMode = TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA;
        final long supportedRaf = Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(supportedMode));
        doReturn(supportedRaf).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(supportedRaf).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);

        final List<NetworkModeEntry> entries =
                SupportedNetworkModeCatalog.getSupportedNetworkModes(
                        mContext, mTelephonyManager);

        assertThat(entries).hasSize(6);
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_NR_ONLY)).isNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_NR_LTE)).isNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_LTE_ONLY)).isNotNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_LTE_WCDMA)).isNotNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA)).isNotNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_WCDMA_ONLY)).isNotNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_WCDMA_PREF)).isNotNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_GSM_ONLY)).isNotNull();
    }

    @Test
    public void getCurrentNetworkMode_invalidRaf_returnsUnknown() {
        doReturn(-1L)
                .when(mTelephonyManager)
                .getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);

        assertThat(SupportedNetworkModeCatalog.getCurrentNetworkMode(mTelephonyManager))
                .isEqualTo(NetworkModes.NETWORK_MODE_UNKNOWN);
    }

    @Test
    public void getCurrentNetworkMode_ignoresFutureRadioBits() {
        final int requestedMode = TelephonyManager.NETWORK_MODE_NR_LTE;
        final long requestedRaf = Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(requestedMode));
        final long futureBit = 1L << 40;
        doReturn(requestedRaf | futureBit)
                .when(mTelephonyManager)
                .getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);

        assertThat(SupportedNetworkModeCatalog.getCurrentNetworkMode(mTelephonyManager))
                .isEqualTo(requestedMode);
    }

    @Test
    public void unsupportedFamilies_areFilteredByExactRadioFamily() {
        final int modernMode = TelephonyManager.NETWORK_MODE_NR_LTE_GSM_WCDMA;
        final long modernRaf = Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(modernMode));
        doReturn(modernRaf).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(modernRaf).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);

        final List<NetworkModeEntry> entries =
                SupportedNetworkModeCatalog.getSupportedNetworkModes(
                        mContext, mTelephonyManager);

        assertThat(find(entries, TelephonyManager.NETWORK_MODE_NR_ONLY)).isNotNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_LTE_ONLY)).isNotNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_WCDMA_ONLY)).isNotNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_GSM_ONLY)).isNotNull();

        assertThat(find(entries, TelephonyManager.NETWORK_MODE_TDSCDMA_ONLY)).isNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_CDMA_NO_EVDO)).isNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_EVDO_NO_CDMA)).isNull();
    }

    @Test
    public void testRestriction_doesNotRemoveHardwareSupportedMode() {
        final int mode = TelephonyManager.NETWORK_MODE_NR_LTE;
        final long raf = Integer.toUnsignedLong(RadioAccessFamily.getRafFromNetworkType(mode));
        doReturn(raf).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(raf).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        doReturn(~TelephonyManager.NETWORK_TYPE_BITMASK_NR)
                .when(mTelephonyManager)
                .getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_TEST);

        final List<NetworkModeEntry> entries =
                SupportedNetworkModeCatalog.getSupportedNetworkModes(
                        mContext, mTelephonyManager);
        final NetworkModeEntry nrOnly =
                find(entries, TelephonyManager.NETWORK_MODE_NR_ONLY);

        assertThat(nrOnly).isNotNull();
        assertThat(nrOnly.getRestrictionReasons())
                .contains(NetworkModeEntry.RestrictionReason.TEST);
    }

    @Test
    public void carrierRestriction_doesNotRemoveHardwareSupportedMode() {
        final int mode = TelephonyManager.NETWORK_MODE_NR_LTE;
        final long raf = Integer.toUnsignedLong(RadioAccessFamily.getRafFromNetworkType(mode));
        doReturn(raf).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(raf).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        doReturn(~TelephonyManager.NETWORK_TYPE_BITMASK_NR)
                .when(mTelephonyManager)
                .getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER);

        final List<NetworkModeEntry> entries =
                SupportedNetworkModeCatalog.getSupportedNetworkModes(
                        mContext, mTelephonyManager);
        final NetworkModeEntry nrOnly =
                find(entries, TelephonyManager.NETWORK_MODE_NR_ONLY);

        assertThat(nrOnly).isNotNull();
        assertThat(nrOnly.isCurrentlyAllowed()).isFalse();
        assertThat(nrOnly.getRestrictionReasons())
                .contains(NetworkModeEntry.RestrictionReason.CARRIER);
        assertThat(nrOnly.isSelectable()).isTrue();
    }


    @Test
    public void twoGRestriction_checksRequiredFamilyRatherThanAny2g() {
        final int modernMode = TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA;
        final long modernRaf = Integer.toUnsignedLong(
                RadioAccessFamily.getRafFromNetworkType(modernMode));
        doReturn(modernRaf).when(mTelephonyManager).getSupportedRadioAccessFamily();
        doReturn(modernRaf).when(mTelephonyManager).getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);

        // ENABLE_2G contains CDMA only, while this modem/mode requires GSM for its 2G path.
        doReturn(TelephonyManager.NETWORK_TYPE_BITMASK_CDMA)
                .when(mTelephonyManager)
                .getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G);

        final List<NetworkModeEntry> entries =
                SupportedNetworkModeCatalog.getSupportedNetworkModes(
                        mContext, mTelephonyManager);
        final NetworkModeEntry gsmOnly =
                find(entries, TelephonyManager.NETWORK_MODE_GSM_ONLY);

        assertThat(gsmOnly).isNotNull();
        assertThat(gsmOnly.getRestrictionReasons())
                .contains(NetworkModeEntry.RestrictionReason.TWO_G_DISABLED);
        assertThat(gsmOnly.isSelectable()).isFalse();
    }

    private static NetworkModeEntry find(List<NetworkModeEntry> entries, int networkMode) {
        for (NetworkModeEntry entry : entries) {
            if (entry.getNetworkMode() == networkMode) {
                return entry;
            }
        }
        return null;
    }
}
