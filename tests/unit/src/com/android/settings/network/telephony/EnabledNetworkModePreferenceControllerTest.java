/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.settings.network.telephony;

import static android.telephony.satellite.SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_MODEM_STATE_OFF;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.telephony.RadioAccessFamily;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settings.network.telephony.mode.NetworkModeEntry;
import com.android.settings.network.telephony.mode.NetworkModeEntry.RestrictionReason;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class EnabledNetworkModePreferenceControllerTest {
    private static final int SUB_ID = 2;
    private static final String KEY = "enabled_network";

    @Mock
    private TelephonyManager mTelephonyManager;
    @Mock
    private TelephonyManager mInvalidTelephonyManager;
    @Mock
    private ServiceState mServiceState;
    @Mock
    private FragmentManager mFragmentManager;

    private EnabledNetworkModePreferenceController mController;
    private NetworkModePreference mPreference;
    private Context mContext;

    @UiThreadTest
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());

        when(mContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mTelephonyManager);
        when(mContext.getSystemService(TelephonyManager.class)).thenReturn(mTelephonyManager);
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(SUB_ID);
        doReturn(mInvalidTelephonyManager).when(mTelephonyManager).createForSubscriptionId(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        doReturn(mServiceState).when(mTelephonyManager).getServiceState();
        when(mTelephonyManager.getAllowedNetworkTypesForReason(anyInt())).thenReturn(-1L);
        when(mTelephonyManager.isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK))
                .thenReturn(true);

        mockAccessFamily(TelephonyManager.NETWORK_MODE_NR_LTE_GSM_WCDMA);
        mockUserNetworkMode(TelephonyManager.NETWORK_MODE_NR_LTE_GSM_WCDMA);

        mPreference = new NetworkModePreference(mContext, null);
        mController = new EnabledNetworkModePreferenceController(mContext, KEY);
        mController.init(SUB_ID, mFragmentManager);
        mPreference.setKey(mController.getPreferenceKey());
    }

    @UiThreadTest
    @Test
    public void updateState_modern5gModem_showsCuratedNetworkModes() {
        mController.updateState(mPreference);

        assertThat(mPreference.getEntries()).asList().containsExactly(
                "5G",
                "5G + 4G",
                "5G + 4G + 3G",
                "5G + 4G + 3G + 2G",
                "4G",
                "4G + 3G",
                "4G + 3G + 2G",
                "3G",
                "3G + 2G",
                "2G").inOrder();

        assertThat(mPreference.getEntryValues()).asList().containsExactly(
                String.valueOf(TelephonyManager.NETWORK_MODE_NR_ONLY),
                String.valueOf(TelephonyManager.NETWORK_MODE_NR_LTE),
                String.valueOf(TelephonyManager.NETWORK_MODE_NR_LTE_WCDMA),
                String.valueOf(TelephonyManager.NETWORK_MODE_NR_LTE_GSM_WCDMA),
                String.valueOf(TelephonyManager.NETWORK_MODE_LTE_ONLY),
                String.valueOf(TelephonyManager.NETWORK_MODE_LTE_WCDMA),
                String.valueOf(TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA),
                String.valueOf(TelephonyManager.NETWORK_MODE_WCDMA_ONLY),
                String.valueOf(TelephonyManager.NETWORK_MODE_WCDMA_PREF),
                String.valueOf(TelephonyManager.NETWORK_MODE_GSM_ONLY)).inOrder();
    }

    @UiThreadTest
    @Test
    public void updateState_equivalentLegacyModes_areOneSelectableRafWithAliases() {
        mController.updateState(mPreference);

        final List<NetworkModeEntry> entries = mController.mBuilder.getModeEntries();
        final NetworkModeEntry gsmWcdma =
                find(entries, TelephonyManager.NETWORK_MODE_WCDMA_PREF);

        assertThat(gsmWcdma).isNotNull();
        assertThat(find(entries, TelephonyManager.NETWORK_MODE_GSM_UMTS)).isNull();

        final String[] names =
                mContext.getResources().getStringArray(R.array.preferred_network_mode_choices);
        assertThat(gsmWcdma.getTechnicalLabel()).contains(names[0]);
        assertThat(gsmWcdma.getTechnicalLabel()).contains(names[3]);
    }

    @UiThreadTest
    @Test
    public void updateState_carrierDisables5g_modesRemainVisibleAndAnnotated() {
        when(mTelephonyManager.getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER))
                .thenReturn(~TelephonyManager.NETWORK_TYPE_BITMASK_NR);

        mController.updateState(mPreference);

        assertThat(mPreference.getEntries()).asList().contains("5G");
        final NetworkModeEntry nrOnly =
                find(mController.mBuilder.getModeEntries(),
                        TelephonyManager.NETWORK_MODE_NR_ONLY);
        assertThat(nrOnly).isNotNull();
        assertThat(nrOnly.isCurrentlyAllowed()).isFalse();
        assertThat(nrOnly.getRestrictionReasons()).contains(RestrictionReason.CARRIER);
    }

    @UiThreadTest
    @Test
    public void updateState_2gReasonDisabled_2gModesRemainVisibleAndAnnotated() {
        when(mTelephonyManager.getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G))
                .thenReturn((long) RadioAccessFamily.getRafFromNetworkType(
                        TelephonyManager.NETWORK_MODE_LTE_ONLY));

        mController.updateState(mPreference);

        assertThat(mPreference.getEntries()).asList().contains("2G");
        final NetworkModeEntry gsmOnly =
                find(mController.mBuilder.getModeEntries(),
                        TelephonyManager.NETWORK_MODE_GSM_ONLY);
        assertThat(gsmOnly).isNotNull();
        assertThat(gsmOnly.getRestrictionReasons())
                .contains(RestrictionReason.TWO_G_DISABLED);
    }

    @UiThreadTest
    @Test
    public void updateState_powerRestrictsNr_5gRemainsVisibleAndAnnotated() {
        when(mTelephonyManager.getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER))
                .thenReturn(~TelephonyManager.NETWORK_TYPE_BITMASK_NR);

        mController.updateState(mPreference);

        final NetworkModeEntry nrLte =
                find(mController.mBuilder.getModeEntries(),
                        TelephonyManager.NETWORK_MODE_NR_LTE);
        assertThat(nrLte).isNotNull();
        assertThat(nrLte.getRestrictionReasons()).contains(RestrictionReason.POWER);
    }

    @UiThreadTest
    @Test
    public void updateState_carrierLimitsSelected5g_summaryShowsEffective4g() {
        mockAccessFamily(TelephonyManager.NETWORK_MODE_NR_LTE);
        mockUserNetworkMode(TelephonyManager.NETWORK_MODE_NR_LTE);
        doReturn((long) RadioAccessFamily.getRafFromNetworkType(
                TelephonyManager.NETWORK_MODE_LTE_ONLY))
                .when(mTelephonyManager)
                .getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER);
        mController.init(SUB_ID, mFragmentManager);

        mController.updateState(mPreference);

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(
                        R.string.network_mode_summary_effective,
                        "5G + 4G",
                        "4G"));

        final NetworkModeEntry selected =
                find(mController.mBuilder.getModeEntries(),
                        TelephonyManager.NETWORK_MODE_NR_LTE);
        final NetworkModeEntry effective =
                find(mController.mBuilder.getModeEntries(),
                        TelephonyManager.NETWORK_MODE_LTE_ONLY);
        assertThat(selected).isNotNull();
        assertThat(selected.isCurrent()).isTrue();
        assertThat(effective).isNotNull();
        assertThat(effective.isEffective()).isTrue();
    }

    @UiThreadTest
    @Test
    public void updateState_groupsModesByHighestGeneration() {
        mController.updateState(mPreference);

        final CharSequence[] sections = mPreference.getEntrySectionsForTest();
        assertThat(sections).hasLength(10);
        assertThat(sections[0]).isEqualTo(
                mContext.getString(R.string.network_mode_group_5g));
        assertThat(sections[4]).isEqualTo(
                mContext.getString(R.string.network_mode_group_4g));
        assertThat(sections[7]).isEqualTo(
                mContext.getString(R.string.network_mode_group_3g));
        assertThat(sections[9]).isEqualTo(
                mContext.getString(R.string.network_mode_group_2g));
        assertThat(sections[1]).isNull();
        assertThat(sections[5]).isNull();
        assertThat(sections[8]).isNull();
    }

    @UiThreadTest
    @Test
    public void updateState_active5gMode_showsNrDcAndVoNrState() {
        when(mTelephonyManager.isRadioInterfaceCapabilitySupported(
                TelephonyManager.CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE))
                .thenReturn(true);
        doReturn(true).when(mTelephonyManager).isNrDualConnectivityEnabled();
        doReturn(true).when(mTelephonyManager).isVoNrEnabled();

        mController.updateState(mPreference);

        final CharSequence[] statuses = mPreference.getEntryStatusesForTest();
        final int index = mPreference.findIndexOfValue(
                String.valueOf(TelephonyManager.NETWORK_MODE_NR_LTE_GSM_WCDMA));
        assertThat(index).isAtLeast(0);
        assertThat(statuses[index].toString())
                .contains(mContext.getString(R.string.network_mode_nr_dc_enabled));
        assertThat(statuses[index].toString())
                .contains(mContext.getString(R.string.network_mode_vonr_enabled));
    }

    @UiThreadTest
    @Test
    public void updateState_currentDistinctRaf_isNeverCollapsedToNearestGeneration() {
        mockUserNetworkMode(TelephonyManager.NETWORK_MODE_NR_LTE_WCDMA);

        mController.updateState(mPreference);

        assertThat(mPreference.getValue()).isEqualTo(
                String.valueOf(TelephonyManager.NETWORK_MODE_NR_LTE_WCDMA));
        assertThat(mController.mBuilder.getSelectedEntryValue()).isEqualTo(
                TelephonyManager.NETWORK_MODE_NR_LTE_WCDMA);
    }

    @UiThreadTest
    @Test
    public void updateState_missingSupportedRaf_fallsBackToExactCurrentMode() {
        doReturn(0L).when(mTelephonyManager).getSupportedRadioAccessFamily();
        mockUserNetworkMode(TelephonyManager.NETWORK_MODE_NR_LTE);

        mController.updateState(mPreference);

        assertThat(mPreference.getEntries()).asList().containsExactly("5G + 4G");
        assertThat(mPreference.getEntryValues()).asList().containsExactly(
                String.valueOf(TelephonyManager.NETWORK_MODE_NR_LTE));
        assertThat(mPreference.getValue()).isEqualTo(
                String.valueOf(TelephonyManager.NETWORK_MODE_NR_LTE));
    }

    @UiThreadTest
    @Test
    public void updateState_supported4g5g_showsOnlyHardwareCompatibleModes() {
        mockAccessFamily(TelephonyManager.NETWORK_MODE_NR_LTE);
        mockUserNetworkMode(TelephonyManager.NETWORK_MODE_NR_LTE);
        mController.init(SUB_ID, mFragmentManager);

        mController.updateState(mPreference);

        assertThat(mPreference.getEntries()).asList().containsExactly(
                "5G",
                "5G + 4G",
                "4G").inOrder();
    }

    @UiThreadTest
    @Test
    public void onSubscriptionsChanged_rebuildsFromHardwareCapabilities() {
        final PreferenceManager preferenceManager = new PreferenceManager(mContext);
        final PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        screen.addPreference(mPreference);
        mController.displayPreference(screen);

        mockAccessFamily(TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA);
        mockUserNetworkMode(TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA);

        mController.onSubscriptionsChanged();

        assertThat(mPreference.getEntries()).asList().containsExactly(
                "4G",
                "4G + 3G",
                "4G + 3G + 2G",
                "3G",
                "3G + 2G",
                "2G").inOrder();
    }

    @UiThreadTest
    @Test
    public void updateState_satelliteIsStartedAndSelectedSubForSatellite_disablePreference() {
        mController.mSatelliteModemStateCallback
                .onSatelliteModemStateChanged(SATELLITE_MODEM_STATE_CONNECTED);
        mController.mSelectedNbIotSatelliteSubscriptionCallback
                .onSelectedNbIotSatelliteSubscriptionChanged(SUB_ID);

        mController.updateState(mPreference);

        assertFalse(mPreference.isEnabled());
    }

    @UiThreadTest
    @Test
    public void updateState_satelliteIsIdle_enablePreference() {
        mController.mSatelliteModemStateCallback
                .onSatelliteModemStateChanged(SATELLITE_MODEM_STATE_OFF);
        mController.mSelectedNbIotSatelliteSubscriptionCallback
                .onSelectedNbIotSatelliteSubscriptionChanged(SUB_ID);

        mController.updateState(mPreference);

        assertTrue(mPreference.isEnabled());
    }

    @UiThreadTest
    @Test
    public void updateState_notSelectedSubForSatellite_enablePreference() {
        mController.mSatelliteModemStateCallback
                .onSatelliteModemStateChanged(SATELLITE_MODEM_STATE_CONNECTED);
        mController.mSelectedNbIotSatelliteSubscriptionCallback
                .onSelectedNbIotSatelliteSubscriptionChanged(0);

        mController.updateState(mPreference);

        assertTrue(mPreference.isEnabled());
    }

    @UiThreadTest
    @Test
    public void updateState_isAirplaneModeOn_setEnableFalse() {
        mController.notifyAirplaneModeChanged(true);

        mController.updateState(mPreference);

        assertFalse(mPreference.isEnabled());
    }

    private static NetworkModeEntry find(List<NetworkModeEntry> entries, int networkMode) {
        for (NetworkModeEntry entry : entries) {
            if (entry.getNetworkMode() == networkMode) {
                return entry;
            }
        }
        return null;
    }

    private void mockAccessFamily(int networkMode) {
        doReturn((long) RadioAccessFamily.getRafFromNetworkType(networkMode))
                .when(mTelephonyManager)
                .getSupportedRadioAccessFamily();
    }

    private void mockUserNetworkMode(int networkMode) {
        doReturn((long) RadioAccessFamily.getRafFromNetworkType(networkMode))
                .when(mTelephonyManager)
                .getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
    }
}
