/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.settings.network.telephony.EnabledNetworkModePreferenceControllerHelperKt.getNetworkModePreferenceType;
import static com.android.settings.network.telephony.EnabledNetworkModePreferenceControllerHelperKt.setAllowedNetworkTypes;
import static com.android.settings.network.telephony.mode.NetworkModes.addNrToLteNetworkMode;
import static com.android.settings.network.telephony.mode.NetworkModes.reduceNrToLteNetworkMode;

import android.content.Context;
import android.content.res.Resources;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.telephony.CarrierConfigManager;
import android.telephony.RadioAccessFamily;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteModemStateCallback;
import android.telephony.satellite.SelectedNbIotSatelliteSubscriptionCallback;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
import androidx.preference.ListPreferenceDialogFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.telephony.flags.Flags;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.network.AllowedNetworkTypesListener;
import com.android.settings.network.CarrierConfigCache;
import com.android.settings.network.SubscriptionsChangeListener;
import com.android.settings.network.telephony.NetworkModeChoicesProto.EnabledNetworks;
import com.android.settings.network.telephony.NetworkModeChoicesProto.UiOptions;
import com.android.settingslib.RestrictedLockUtilsInternal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Preference controller for "Enabled network mode"
 */
// LINT.IfChange
public class EnabledNetworkModePreferenceController extends
        BasePreferenceController implements
        ListPreference.OnPreferenceChangeListener, DefaultLifecycleObserver,
        SubscriptionsChangeListener.SubscriptionsChangeListenerClient, AirplaneModeChangedCallback {

    private static final String LOG_TAG = "EnabledNetworkMode";

    private static final long BITMASK_2G = TelephonyManager.NETWORK_TYPE_BITMASK_GSM
            | TelephonyManager.NETWORK_TYPE_BITMASK_GPRS
            | TelephonyManager.NETWORK_TYPE_BITMASK_EDGE
            | TelephonyManager.NETWORK_TYPE_BITMASK_CDMA
            | TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT;
    private static final long BITMASK_3G = TelephonyManager.NETWORK_TYPE_BITMASK_HSDPA
            | TelephonyManager.NETWORK_TYPE_BITMASK_HSPA
            | TelephonyManager.NETWORK_TYPE_BITMASK_HSUPA
            | TelephonyManager.NETWORK_TYPE_BITMASK_HSPAP
            | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS
            | TelephonyManager.NETWORK_TYPE_BITMASK_TD_SCDMA
            | TelephonyManager.NETWORK_TYPE_BITMASK_EHRPD
            | TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_0
            | TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_A
            | TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_B;
    private static final long BITMASK_4G = TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
    private static final long BITMASK_5G = TelephonyManager.NETWORK_TYPE_BITMASK_NR;

    private static final int CUSTOM_MODE_BASE = 1000;
    private static final int GENERATION_2G = 1;
    private static final int GENERATION_3G = 1 << 1;
    private static final int GENERATION_4G = 1 << 2;
    private static final int GENERATION_5G = 1 << 3;
    private static final int GENERATION_ALL = GENERATION_2G | GENERATION_3G
            | GENERATION_4G | GENERATION_5G;

    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private AllowedNetworkTypesListener mAllowedNetworkTypesListener;
    private Preference mPreference;
    private PreferenceScreen mPreferenceScreen;
    private TelephonyManager mTelephonyManager;
    @VisibleForTesting
    PreferenceEntriesBuilder mBuilder;
    private SubscriptionsChangeListener mSubscriptionsListener;
    private int mCallState = TelephonyManager.CALL_STATE_IDLE;
    private PhoneCallStateTelephonyCallback mTelephonyCallback;
    private FragmentManager mFragmentManager;
    private LifecycleOwner mViewLifecycleOwner;
    private SatelliteManager mSatelliteManager;
    private boolean mIsSatelliteSessionStarted = false;
    private boolean mIsCurrentSubscriptionForSatellite = false;
    protected boolean mIsAirplaneModeOn = false;

    @VisibleForTesting
    final SelectedNbIotSatelliteSubscriptionCallback mSelectedNbIotSatelliteSubscriptionCallback =
            new SelectedNbIotSatelliteSubscriptionCallback() {
                @Override
                public void onSelectedNbIotSatelliteSubscriptionChanged(int selectedSubId) {
                    mIsCurrentSubscriptionForSatellite = selectedSubId == mSubId;
                    updatePreference();
                }
            };

    @VisibleForTesting
    final SatelliteModemStateCallback mSatelliteModemStateCallback =
            new SatelliteModemStateCallback() {
                @Override
                public void onSatelliteModemStateChanged(int state) {
                    switch (state) {
                        case SatelliteManager.SATELLITE_MODEM_STATE_OFF:
                        case SatelliteManager.SATELLITE_MODEM_STATE_UNAVAILABLE:
                        case SatelliteManager.SATELLITE_MODEM_STATE_UNKNOWN:
                            if (mIsSatelliteSessionStarted) {
                                mIsSatelliteSessionStarted = false;
                                updatePreference();
                            }
                            break;
                        default:
                            if (!mIsSatelliteSessionStarted) {
                                mIsSatelliteSessionStarted = true;
                                updatePreference();
                            }
                            break;
                    }
                }
            };

    public EnabledNetworkModePreferenceController(Context context, String key) {
        super(context, key);
        mSubscriptionsListener = new SubscriptionsChangeListener(context, this);
        if (mTelephonyCallback == null) {
            mTelephonyCallback = new PhoneCallStateTelephonyCallback();
        }
        mSatelliteManager = context.getSystemService(SatelliteManager.class);
    }

    @Override
    public int getAvailabilityStatus() {
        return getNetworkModePreferenceType(mContext, mSubId)
                == NetworkModePreferenceType.EnabledNetworkMode
                ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    protected boolean isCallStateIdle() {
        return mCallState == TelephonyManager.CALL_STATE_IDLE;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (mSatelliteManager != null) {
            try {
                mSatelliteManager.registerForModemStateChanged(
                        mContext.getMainExecutor(), mSatelliteModemStateCallback);
                mSatelliteManager.registerForSelectedNbIotSatelliteSubscriptionChanged(
                        mContext.getMainExecutor(),
                        mSelectedNbIotSatelliteSubscriptionCallback);
            } catch (IllegalStateException e) {
                Log.w(LOG_TAG, "IllegalStateException : " + e);
            }
        }

        mSubscriptionsListener.start();
        if (mAllowedNetworkTypesListener == null || mTelephonyCallback == null) {
            return;
        }
        mAllowedNetworkTypesListener.register(mContext, mSubId);
        mTelephonyCallback.register(mTelephonyManager, mSubId);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        mSubscriptionsListener.stop();
        if (mSatelliteManager != null) {
            try {
                mSatelliteManager.unregisterForModemStateChanged(mSatelliteModemStateCallback);
                mSatelliteManager.unregisterForSelectedNbIotSatelliteSubscriptionChanged(
                        mSelectedNbIotSatelliteSubscriptionCallback);
            } catch (IllegalStateException e) {
                Log.w(LOG_TAG, "IllegalStateException : " + e);
            }
        }

        if (mAllowedNetworkTypesListener == null || mTelephonyCallback == null) {
            return;
        }
        mAllowedNetworkTypesListener.unregister(mContext, mSubId);
        mTelephonyCallback.unregister();
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreferenceScreen = screen;
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);

        if (mBuilder == null) {
            return;
        }

        final ListPreference listPreference = (ListPreference) preference;
        mBuilder.refresh();
        mBuilder.updateListPreference(listPreference);

        boolean listPreferenceEnabled = isPreferenceShallEnabled();
        listPreference.setEnabled(listPreferenceEnabled);
        if (!listPreferenceEnabled) {
            // If dialog is already opened when ListPreference disabled, dismiss them.
            for (Fragment fragment : mFragmentManager.getFragments()) {
                if (fragment instanceof ListPreferenceDialogFragmentCompat) {
                    ((ListPreferenceDialogFragmentCompat) fragment).dismiss();
                }
            }
        }
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object object) {
        final int newPreferredNetworkMode = Integer.parseInt((String) object);
        final ListPreference listPreference = (ListPreference) preference;
        mBuilder.setPreferenceValueAndSummary(newPreferredNetworkMode);
        listPreference.setValue(Integer.toString(mBuilder.getSelectedEntryValue()));
        listPreference.setSummary(mBuilder.getSummary());

        if (mBuilder.isCustomMode(newPreferredNetworkMode)) {
            setAllowedNetworkTypes(
                    mTelephonyManager,
                    mViewLifecycleOwner,
                    mBuilder.getCustomAllowedNetworkTypes(newPreferredNetworkMode));
        } else {
            setAllowedNetworkTypes(mTelephonyManager, mViewLifecycleOwner, newPreferredNetworkMode);
        }
        return true;
    }

    @Override
    public void notifyAirplaneModeChanged(boolean isAirplaneModeOn) {
        this.mIsAirplaneModeOn = isAirplaneModeOn;
    }

    public void init(int subId, FragmentManager fragmentManager) {
        mSubId = subId;
        mFragmentManager = fragmentManager;
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class)
                .createForSubscriptionId(mSubId);
        mBuilder = new PreferenceEntriesBuilder(mContext, mSubId);

        if (mAllowedNetworkTypesListener == null) {
            mAllowedNetworkTypesListener = new AllowedNetworkTypesListener(
                    mContext.getMainExecutor());
            mAllowedNetworkTypesListener.setAllowedNetworkTypesListener(
                    () -> {
                        mBuilder.updateConfig();
                        updatePreference();
                    });
        }
    }

    @Override
    public void onViewCreated(@NonNull LifecycleOwner viewLifecycleOwner) {
        mViewLifecycleOwner = viewLifecycleOwner;
    }

    private void updatePreference() {
        if (mPreferenceScreen != null) {
            displayPreference(mPreferenceScreen);
        }
        if (mPreference != null) {
            updateState(mPreference);
        }
    }

    private boolean isPreferenceShallEnabled() {
        Log.d(LOG_TAG, "isPreferenceShallEnabled, mIsSatelliteSessionStarted : "
                + mIsSatelliteSessionStarted + " / mIsCurrentSubscriptionForSatellite : "
                + mIsCurrentSubscriptionForSatellite);
        return isCallStateIdle()
                && !(mIsSatelliteSessionStarted && mIsCurrentSubscriptionForSatellite)
                && !mIsAirplaneModeOn;
    }

    public static class PreferenceEntriesBuilder {
        private CarrierConfigCache mCarrierConfigCache;
        private Context mContext;
        private TelephonyManager mTelephonyManager;

        private boolean mIsGlobalCdma;
        private boolean mIs5gEntryDisplayed;
        private boolean mShow4gForLTE;
        private boolean mDisplay2gOptions;
        private boolean mDisplay3gOptions;
        private boolean mDisplay4gOptions;
        private boolean mDisplay5gOptions;
        private long mSupportedRaf;
        private int mSelectedEntry;
        private int mSubId;
        private String mSummary = "";

        private List<String> mEntries = new ArrayList<>();
        private List<Integer> mEntriesValue = new ArrayList<>();
        private final Map<Integer, Long> mCustomModeRaf = new HashMap<>();
        private final Map<Integer, String> mCustomModeSummary = new HashMap<>();

        PreferenceEntriesBuilder(Context context, int subId) {
            this.mContext = context;
            this.mSubId = subId;
            mCarrierConfigCache = CarrierConfigCache.getInstance(context);
            mTelephonyManager = mContext.getSystemService(TelephonyManager.class)
                    .createForSubscriptionId(mSubId);
            updateConfig();
        }

        public void updateConfig() {
            mTelephonyManager = mTelephonyManager.createForSubscriptionId(mSubId);
            final PersistableBundle carrierConfig = mCarrierConfigCache.getConfigForSubId(mSubId);

            // Load the network types actually supported by the baseband.
            final long supportedRaf = mTelephonyManager.getSupportedRadioAccessFamily();
            mSupportedRaf = supportedRaf;
            final boolean supported5g = checkSupportedRadioBitmask(supportedRaf, BITMASK_5G);
            final boolean supported4g = checkSupportedRadioBitmask(supportedRaf, BITMASK_4G);
            final boolean supported3g = checkSupportedRadioBitmask(supportedRaf, BITMASK_3G);
            final boolean supported2g = checkSupportedRadioBitmask(supportedRaf, BITMASK_2G);

            // mIsGlobalCdma, which eventually needs to be removed as 3GPP2 is deprecated.
            mIsGlobalCdma = false;
            if (carrierConfig != null) {
                mIsGlobalCdma = mTelephonyManager.isLteCdmaEvdoGsmWcdmaEnabled()
                        && carrierConfig.getBoolean(
                                CarrierConfigManager.KEY_SHOW_CDMA_CHOICES_BOOL);
            }
            Log.d(LOG_TAG, "PreferenceEntriesBuilder: subId" + mSubId
                    + " , mIsGlobalCdma: " + mIsGlobalCdma);

            // 2G option display - false by default, as per KEY_PREFER_2G_BOOOL
            boolean configKeyPrefer2g = false;
            if (carrierConfig != null) {
                configKeyPrefer2g = carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_PREFER_2G_BOOL);
            }
            final boolean allowed2gNetworkType =
                    checkSupportedRadioBitmask(mTelephonyManager.getAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G),
                        BITMASK_2G);
            final boolean enabledByAdmin2g = !is2gDisabledByAdmin();
            mDisplay2gOptions =
                supported2g
                && configKeyPrefer2g
                && allowed2gNetworkType
                && enabledByAdmin2g;
            Log.d(LOG_TAG, "mDisplay2gOptions: " + mDisplay2gOptions
                    + ", supported2g: " + supported2g
                    + ", configKeyPrefer2g: " + configKeyPrefer2g
                    + ", allowed2gNetworkType: " + allowed2gNetworkType
                    + ", enabledByAdmin2g: " + enabledByAdmin2g);

            // 3G option display
            final boolean flagHidePrefer3gItem = Flags.hidePrefer3gItem();
            boolean allowed3gNetworkType;
            if (flagHidePrefer3gItem && carrierConfig != null) {
                allowed3gNetworkType = carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_PREFER_3G_VISIBILITY_BOOL);
            } else {
                allowed3gNetworkType = getResourcesForSubId().getBoolean(
                        R.bool.config_display_network_mode_3g_option);
                int[] carriersWithout3gMenu = getResourcesForSubId().getIntArray(
                        R.array.network_mode_3g_deprecated_carrier_id);
                if ((carriersWithout3gMenu != null) && (carriersWithout3gMenu.length > 0)) {
                    SubscriptionManager sm = mContext.getSystemService(
                            SubscriptionManager.class);
                    SubscriptionInfo subInfo = sm.getActiveSubscriptionInfo(mSubId);
                    if (subInfo != null) {
                        int carrierId = subInfo.getCarrierId();
                        for (int idx = 0; idx < carriersWithout3gMenu.length; idx++) {
                            if (carrierId == carriersWithout3gMenu[idx]) {
                                allowed3gNetworkType = false;
                                break;
                            }
                        }
                    }
                }
            }
            mDisplay3gOptions = supported3g && allowed3gNetworkType;
            Log.d(LOG_TAG, "mDisplay3gOptions: " + mDisplay3gOptions
                    + ", supported3g: " + supported3g
                    + ", allowed3gNetworkType: " + allowed3gNetworkType);

            // 4G option display
            boolean allowed4gNetworkType = true;
            if (carrierConfig != null) {
                allowed4gNetworkType = carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_LTE_ENABLED_BOOL);
                mShow4gForLTE = carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL);
            }
            mDisplay4gOptions = supported4g && allowed4gNetworkType;
            Log.d(LOG_TAG, "mDisplay4gOptions: " + mDisplay4gOptions
                    + ", supported4g: " + supported4g
                    + ", allowed4gNetworkType: " + allowed4gNetworkType);

            // 5G option display
            final boolean allowed5gNetworkType = checkSupportedRadioBitmask(
                    mTelephonyManager.getAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER),
                    TelephonyManager.NETWORK_TYPE_BITMASK_NR);
            mDisplay5gOptions = supported5g && allowed5gNetworkType;
            Log.d(LOG_TAG, "mDisplay5gOptions: " + mDisplay5gOptions
                    + ", supported5g: " + supported5g
                    + ", allowed5gNetworkType: " + allowed5gNetworkType);
        }

        private boolean is2gDisabledByAdmin() {
            return RestrictedLockUtilsInternal.checkIfRestrictionEnforced(mContext,
                       UserManager.DISALLOW_CELLULAR_2G, UserHandle.myUserId())
                != null;
        }

        void setPreferenceEntries() {
            mTelephonyManager = mTelephonyManager.createForSubscriptionId(mSubId);

            clearAllEntries();
            UiOptions.Builder uiOptions = UiOptions.newBuilder();
            uiOptions.setType(getEnabledNetworkType());
            switch (uiOptions.getType()) {
                case ENABLED_NETWORKS_CDMA_CHOICES:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_cdma_values)
                            .addFormat(UiOptions.PresentFormat.add5gAndLteEntry)
                            .addFormat(UiOptions.PresentFormat.add3gEntry)
                            .addFormat(UiOptions.PresentFormat.add1xEntry)
                            .addFormat(UiOptions.PresentFormat.addGlobalEntry);
                    break;
                case ENABLED_NETWORKS_CDMA_NO_LTE_CHOICES:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_cdma_no_lte_values)
                            .addFormat(UiOptions.PresentFormat.add3gEntry)
                            .addFormat(UiOptions.PresentFormat.add1xEntry);
                    break;
                case ENABLED_NETWORKS_CDMA_ONLY_LTE_CHOICES:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_cdma_only_lte_values)
                            .addFormat(UiOptions.PresentFormat.addLteEntry)
                            .addFormat(UiOptions.PresentFormat.addGlobalEntry);
                    break;
                case ENABLED_NETWORKS_TDSCDMA_CHOICES:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_tdscdma_values)
                            .addFormat(UiOptions.PresentFormat.add5gAndLteEntry)
                            .addFormat(UiOptions.PresentFormat.add3gEntry)
                            .addFormat(UiOptions.PresentFormat.add2gEntry);
                    break;
                case ENABLED_NETWORKS_EXCEPT_GSM_LTE_CHOICES:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_except_gsm_lte_values)
                            .addFormat(UiOptions.PresentFormat.add3gEntry);
                    break;
                case ENABLED_NETWORKS_EXCEPT_GSM_4G_CHOICES:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_except_gsm_values)
                            .addFormat(UiOptions.PresentFormat.add5gAnd4gEntry)
                            .addFormat(UiOptions.PresentFormat.add3gEntry);
                    break;
                case ENABLED_NETWORKS_EXCEPT_GSM_CHOICES:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_except_gsm_values)
                            .addFormat(UiOptions.PresentFormat.add5gAndLteEntry)
                            .addFormat(UiOptions.PresentFormat.add3gEntry);
                    break;
                case ENABLED_NETWORKS_EXCEPT_LTE_CHOICES:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_except_lte_values)
                            .addFormat(UiOptions.PresentFormat.add3gEntry)
                            .addFormat(UiOptions.PresentFormat.add2gEntry);
                    break;
                case ENABLED_NETWORKS_4G_CHOICES:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_values)
                            .addFormat(UiOptions.PresentFormat.add5gAnd4gEntry)
                            .addFormat(UiOptions.PresentFormat.add3gEntry)
                            .addFormat(UiOptions.PresentFormat.add2gEntry);
                    break;
                case ENABLED_NETWORKS_CHOICES:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_values)
                            .addFormat(UiOptions.PresentFormat.add5gAndLteEntry)
                            .addFormat(UiOptions.PresentFormat.add3gEntry)
                            .addFormat(UiOptions.PresentFormat.add2gEntry);
                    break;
                case PREFERRED_NETWORK_MODE_CHOICES_WORLD_MODE:
                    uiOptions = uiOptions
                            .setChoices(R.array.preferred_network_mode_values_world_mode)
                            .addFormat(UiOptions.PresentFormat.addGlobalEntry)
                            .addFormat(UiOptions.PresentFormat.addWorldModeCdmaEntry)
                            .addFormat(UiOptions.PresentFormat.addWorldModeGsmEntry);
                    break;
                case ENABLED_NETWORKS_4G_CHOICES_EXCEPT_GSM_3G:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_except_gsm_3g_values)
                            .addFormat(UiOptions.PresentFormat.add5gAnd4gEntry);
                    break;
                case ENABLED_NETWORKS_CHOICES_EXCEPT_GSM_3G:
                    uiOptions = uiOptions
                            .setChoices(R.array.enabled_networks_values)
                            .addFormat(UiOptions.PresentFormat.add5gAndLteEntry);
                    break;
                default:
                    throw new IllegalArgumentException("Not supported enabled network types.");
            }

            String[] entryValues = getResourcesForSubId().getStringArray(uiOptions.getChoices());
            final int[] entryValuesInt = Stream.of(entryValues)
                    .mapToInt(Integer::parseInt).toArray();
            final List<UiOptions.PresentFormat> formatList = uiOptions.getFormatList();
            if (entryValuesInt.length < formatList.size()) {
                throw new IllegalArgumentException(
                        uiOptions.getType().name() + " index error.");
            }
            // Compose options based on given values and formats.
            IntStream.range(0, formatList.size()).forEach(entryIndex -> {
                switch (formatList.get(entryIndex)) {
                    case add1xEntry:
                        if (mDisplay2gOptions) {
                            add1xEntry(entryValuesInt[entryIndex]);
                        }
                        break;
                    case add2gEntry:
                        if (mDisplay2gOptions) {
                            add2gEntry(entryValuesInt[entryIndex]);
                        }
                        break;
                    case add3gEntry:
                        if (mDisplay3gOptions) {
                            add3gEntry(entryValuesInt[entryIndex]);
                        }
                        break;
                    case addGlobalEntry:
                        addGlobalEntry(entryValuesInt[entryIndex]);
                        break;
                    case addWorldModeCdmaEntry:
                        addCustomEntry(
                                getResourcesForSubId().getString(
                                        R.string.network_world_mode_cdma_lte),
                                entryValuesInt[entryIndex]);
                        break;
                    case addWorldModeGsmEntry:
                        addCustomEntry(
                                getResourcesForSubId().getString(
                                        R.string.network_world_mode_gsm_lte),
                                entryValuesInt[entryIndex]);
                        break;
                    case add4gEntry:
                        add4gEntry(entryValuesInt[entryIndex]);
                        break;
                    case addLteEntry:
                        addLteEntry(entryValuesInt[entryIndex]);
                        break;
                    case add5gEntry:
                        add5gEntry(addNrToLteNetworkMode(entryValuesInt[entryIndex]));
                        break;
                    case add5gAnd4gEntry:
                        add5gEntry(addNrToLteNetworkMode(entryValuesInt[entryIndex]));
                        add4gEntry(entryValuesInt[entryIndex]);
                        break;
                    case add5gAndLteEntry:
                        add5gEntry(addNrToLteNetworkMode(entryValuesInt[entryIndex]));
                        addLteEntry(entryValuesInt[entryIndex]);
                        break;
                    default:
                        throw new IllegalArgumentException("Not supported ui options format.");
                }
            });

            addAdvancedEntries();
        }

        private int getPreferredNetworkMode() {
            int networkMode = RadioAccessFamily.getNetworkTypeFromRaf(
                    (int) mTelephonyManager.getAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER));
            if (!mDisplay5gOptions) {
                Log.d(LOG_TAG, "Network mode :" + networkMode + " reduce NR");
                networkMode = reduceNrToLteNetworkMode(networkMode);
            }
            Log.d(LOG_TAG, "getPreferredNetworkMode: " + networkMode);
            return networkMode;
        }

        private EnabledNetworks getEnabledNetworkType() {
            EnabledNetworks enabledNetworkType = EnabledNetworks.ENABLED_NETWORKS_UNKNOWN;
            final int phoneType = mTelephonyManager.getPhoneType();

            if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
                final int lteForced = android.provider.Settings.Global.getInt(
                        mContext.getContentResolver(),
                        android.provider.Settings.Global.LTE_SERVICE_FORCED + mSubId,
                        0);
                final int settingsNetworkMode = getPreferredNetworkMode();
                if (mTelephonyManager.isLteCdmaEvdoGsmWcdmaEnabled()) {
                    if (lteForced == 0) {
                        enabledNetworkType = EnabledNetworks.ENABLED_NETWORKS_CDMA_CHOICES;
                    } else {
                        switch (settingsNetworkMode) {
                            case TelephonyManager.NETWORK_MODE_CDMA_EVDO:
                            case TelephonyManager.NETWORK_MODE_CDMA_NO_EVDO:
                            case TelephonyManager.NETWORK_MODE_EVDO_NO_CDMA:
                                enabledNetworkType =
                                        EnabledNetworks.ENABLED_NETWORKS_CDMA_NO_LTE_CHOICES;
                                break;
                            case TelephonyManager.NETWORK_MODE_GLOBAL:
                            case TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO:
                            case TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                            case TelephonyManager.NETWORK_MODE_LTE_ONLY:
                                enabledNetworkType =
                                        EnabledNetworks.ENABLED_NETWORKS_CDMA_ONLY_LTE_CHOICES;
                                break;
                            default:
                                enabledNetworkType = EnabledNetworks.ENABLED_NETWORKS_CDMA_CHOICES;
                                break;
                        }
                    }
                }
            } else if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                if (mIsGlobalCdma) {
                    enabledNetworkType = EnabledNetworks.ENABLED_NETWORKS_CDMA_CHOICES;
                } else if (MobileNetworkUtils.isTdscdmaSupported(mContext, mSubId)) {
                    enabledNetworkType = EnabledNetworks.ENABLED_NETWORKS_TDSCDMA_CHOICES;
                } else if (!mDisplay2gOptions && !mDisplay3gOptions) {
                    enabledNetworkType = mShow4gForLTE
                            ? EnabledNetworks.ENABLED_NETWORKS_4G_CHOICES_EXCEPT_GSM_3G
                            : EnabledNetworks.ENABLED_NETWORKS_CHOICES_EXCEPT_GSM_3G;
                } else if (!mDisplay2gOptions && !mDisplay4gOptions) {
                    enabledNetworkType = EnabledNetworks.ENABLED_NETWORKS_EXCEPT_GSM_LTE_CHOICES;
                } else if (!mDisplay2gOptions) {
                    enabledNetworkType = mShow4gForLTE
                            ? EnabledNetworks.ENABLED_NETWORKS_EXCEPT_GSM_4G_CHOICES
                            : EnabledNetworks.ENABLED_NETWORKS_EXCEPT_GSM_CHOICES;
                } else if (!mDisplay4gOptions) {
                    enabledNetworkType = EnabledNetworks.ENABLED_NETWORKS_EXCEPT_LTE_CHOICES;
                } else {
                    enabledNetworkType = mShow4gForLTE ? EnabledNetworks.ENABLED_NETWORKS_4G_CHOICES
                            : EnabledNetworks.ENABLED_NETWORKS_CHOICES;
                }
            }
            //TODO(b/117881708): figure out what world mode is, then we can optimize code. Otherwise
            // I prefer to keep this old code
            if (MobileNetworkUtils.isWorldMode(mContext, mSubId)) {
                enabledNetworkType = EnabledNetworks.PREFERRED_NETWORK_MODE_CHOICES_WORLD_MODE;
            }

            if (phoneType == TelephonyManager.PHONE_TYPE_NONE) {
                Log.d(LOG_TAG, "phoneType: PHONE_TYPE_NONE");
                enabledNetworkType = mShow4gForLTE
                        ? EnabledNetworks.ENABLED_NETWORKS_4G_CHOICES_EXCEPT_GSM_3G
                        : EnabledNetworks.ENABLED_NETWORKS_CHOICES_EXCEPT_GSM_3G;
            }

            Log.d(LOG_TAG, "enabledNetworkType: " + enabledNetworkType);
            return enabledNetworkType;
        }

        /**
         * Sets the display string for the network mode choice and selects the corresponding item
         *
         * @param networkMode the current network mode. The current mode might not be an option in
         *                    the choice list. The nearest choice is selected instead
         */
        void setPreferenceValueAndSummary(int networkMode) {
            if (isCustomMode(networkMode)) {
                setSelectedEntry(networkMode);
                setSummary(mCustomModeSummary.get(networkMode));
                return;
            }

            setSelectedEntry(networkMode);
            switch (networkMode) {
                case TelephonyManager.NETWORK_MODE_TDSCDMA_WCDMA:
                case TelephonyManager.NETWORK_MODE_TDSCDMA_GSM_WCDMA:
                case TelephonyManager.NETWORK_MODE_TDSCDMA_GSM:
                    setSelectedEntry(TelephonyManager.NETWORK_MODE_TDSCDMA_GSM_WCDMA);
                    setSummary(R.string.network_3G);
                    break;
                case TelephonyManager.NETWORK_MODE_WCDMA_ONLY:
                case TelephonyManager.NETWORK_MODE_GSM_UMTS:
                case TelephonyManager.NETWORK_MODE_WCDMA_PREF:
                    if (!mIsGlobalCdma) {
                        setSelectedEntry(TelephonyManager.NETWORK_MODE_WCDMA_PREF);
                        setSummary(R.string.network_3G);
                    } else {
                        setSelectedEntry(TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA);
                        setSummary(R.string.network_global);
                    }
                    break;
                case TelephonyManager.NETWORK_MODE_GSM_ONLY:
                    if (!mIsGlobalCdma) {
                        setSelectedEntry(TelephonyManager.NETWORK_MODE_GSM_ONLY);
                        setSummary(R.string.network_2G);
                    } else {
                        setSelectedEntry(TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA);
                        setSummary(R.string.network_global);
                    }
                    break;
                case TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA:
                    if (MobileNetworkUtils.isWorldMode(mContext, mSubId)) {
                        setSummary(
                                R.string.preferred_network_mode_lte_gsm_umts_summary);
                        break;
                    }
                case TelephonyManager.NETWORK_MODE_LTE_ONLY:
                case TelephonyManager.NETWORK_MODE_LTE_WCDMA:
                    if (!mIsGlobalCdma) {
                        setSelectedEntry(TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA);
                        if (is5gEntryDisplayed()) {
                            setSummary(mShow4gForLTE
                                    ? R.string.network_4G_pure : R.string.network_lte_pure);
                        } else {
                            setSummary(mShow4gForLTE
                                    ? R.string.network_4G : R.string.network_lte);
                        }
                    } else {
                        setSelectedEntry(TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA);
                        setSummary(R.string.network_global);
                    }
                    break;
                case TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO:
                    if (MobileNetworkUtils.isWorldMode(mContext, mSubId)) {
                        setSummary(
                                R.string.preferred_network_mode_lte_cdma_summary);
                    } else {
                        setSelectedEntry(TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO);
                        setSummary(is5gEntryDisplayed()
                                ? R.string.network_lte_pure : R.string.network_lte);
                    }
                    break;
                case TelephonyManager.NETWORK_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                    setSelectedEntry(TelephonyManager.NETWORK_MODE_TDSCDMA_CDMA_EVDO_GSM_WCDMA);
                    setSummary(R.string.network_3G);
                    break;
                case TelephonyManager.NETWORK_MODE_CDMA_EVDO:
                case TelephonyManager.NETWORK_MODE_EVDO_NO_CDMA:
                case TelephonyManager.NETWORK_MODE_GLOBAL:
                    setSelectedEntry(TelephonyManager.NETWORK_MODE_CDMA_EVDO);
                    setSummary(R.string.network_3G);
                    break;
                case TelephonyManager.NETWORK_MODE_CDMA_NO_EVDO:
                    setSelectedEntry(TelephonyManager.NETWORK_MODE_CDMA_NO_EVDO);
                    setSummary(R.string.network_1x);
                    break;
                case TelephonyManager.NETWORK_MODE_TDSCDMA_ONLY:
                    setSelectedEntry(TelephonyManager.NETWORK_MODE_TDSCDMA_ONLY);
                    setSummary(R.string.network_3G);
                    break;
                case TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_GSM:
                case TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_GSM_WCDMA:
                case TelephonyManager.NETWORK_MODE_LTE_TDSCDMA:
                case TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_WCDMA:
                case TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                case TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                    if (MobileNetworkUtils.isTdscdmaSupported(mContext, mSubId)) {
                        setSelectedEntry(
                                TelephonyManager.NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA);
                        setSummary(is5gEntryDisplayed()
                                ? R.string.network_lte_pure : R.string.network_lte);
                    } else {
                        setSelectedEntry(
                                TelephonyManager.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA);
                        if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA
                                || mIsGlobalCdma
                                || MobileNetworkUtils.isWorldMode(mContext, mSubId)) {
                            setSummary(R.string.network_global);
                        } else {
                            if (is5gEntryDisplayed()) {
                                setSummary(mShow4gForLTE
                                        ? R.string.network_4G_pure : R.string.network_lte_pure);
                            } else {
                                setSummary(mShow4gForLTE
                                        ? R.string.network_4G : R.string.network_lte);
                            }
                        }
                    }
                    break;

                case TelephonyManager.NETWORK_MODE_NR_ONLY:
                case TelephonyManager.NETWORK_MODE_NR_LTE:
                case TelephonyManager.NETWORK_MODE_NR_LTE_GSM_WCDMA:
                case TelephonyManager.NETWORK_MODE_NR_LTE_WCDMA:
                    setSelectedEntry(TelephonyManager.NETWORK_MODE_NR_LTE_GSM_WCDMA);
                    setSummary(getResourcesForSubId().getString(R.string.network_5G_recommended));
                    break;
                case TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA:
                case TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_GSM:
                case TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_WCDMA:
                case TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_GSM_WCDMA:
                case TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                    setSelectedEntry(
                            TelephonyManager.NETWORK_MODE_NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA);
                    setSummary(getResourcesForSubId().getString(R.string.network_5G_recommended));
                    break;
                case TelephonyManager.NETWORK_MODE_NR_LTE_CDMA_EVDO:
                    setSelectedEntry(TelephonyManager.NETWORK_MODE_NR_LTE_CDMA_EVDO);
                    setSummary(getResourcesForSubId().getString(R.string.network_5G_recommended));
                    break;
                case TelephonyManager.NETWORK_MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA:
                    setSelectedEntry(TelephonyManager.NETWORK_MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA);
                    if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA
                            || mIsGlobalCdma
                            || MobileNetworkUtils.isWorldMode(mContext, mSubId)) {
                        setSummary(R.string.network_global);
                    } else {
                        setSummary(getResourcesForSubId().getString(
                                R.string.network_5G_recommended));
                    }
                    break;
                default:
                    setSummary(
                            getResourcesForSubId().getString(
                                    R.string.mobile_network_mode_error, networkMode));
            }
        }

        private void setPreferenceValueAndSummary() {
            final long currentUserRaf = mTelephonyManager.getAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
            for (Map.Entry<Integer, Long> entry : mCustomModeRaf.entrySet()) {
                if (entry.getValue() == currentUserRaf) {
                    setSelectedEntry(entry.getKey());
                    setSummary(mCustomModeSummary.get(entry.getKey()));
                    return;
                }
            }
            setPreferenceValueAndSummary(getPreferredNetworkMode());
        }

        private boolean checkSupportedRadioBitmask(long supportedRadioBitmask, long targetBitmask) {
            return (targetBitmask & supportedRadioBitmask) > 0;
        }

        /**
         * Add 5G option. Only show the UI when device supported 5G and allowed 5G.
         */
        private void add5gEntry(int value) {
            boolean isNRValue = value >= TelephonyManager.NETWORK_MODE_NR_ONLY;
            if (mDisplay5gOptions && isNRValue) {
                mEntries.add(getResourcesForSubId().getString(R.string.network_5G_recommended));
                mEntriesValue.add(value);
                mIs5gEntryDisplayed = true;
            } else {
                mIs5gEntryDisplayed = false;
                Log.d(LOG_TAG, "Hide 5G option. "
                        + " mDisplay5gOptions: " + mDisplay5gOptions
                        + " isNRValue: " + isNRValue);
            }
        }

        private void addGlobalEntry(int value) {
            Log.d(LOG_TAG, "addGlobalEntry. display5gOptions: " + mDisplay5gOptions);
            mEntries.add(getResourcesForSubId().getString(R.string.network_global));
            if (mDisplay5gOptions) {
                value = addNrToLteNetworkMode(value);
            }
            mEntriesValue.add(value);
        }

        /**
         * Add LTE entry. If device supported 5G, show "LTE" instead of "LTE (recommended)".
         */
        private void addLteEntry(int value) {
            if (mDisplay5gOptions) {
                mEntries.add(getResourcesForSubId().getString(R.string.network_lte_pure));
            } else {
                mEntries.add(getResourcesForSubId().getString(R.string.network_lte));
            }
            mEntriesValue.add(value);
        }

        /**
         * Add 4G entry. If device supported 5G, show "4G" instead of "4G (recommended)".
         */
        private void add4gEntry(int value) {
            if (mDisplay5gOptions) {
                mEntries.add(getResourcesForSubId().getString(R.string.network_4G_pure));
            } else {
                mEntries.add(getResourcesForSubId().getString(R.string.network_4G));
            }
            mEntriesValue.add(value);
        }

        private void add3gEntry(int value) {
            mEntries.add(getResourcesForSubId().getString(R.string.network_3G));
            mEntriesValue.add(value);
        }

        private void add2gEntry(int value) {
            mEntries.add(getResourcesForSubId().getString(R.string.network_2G));
            mEntriesValue.add(value);
        }

        private void add1xEntry(int value) {
            mEntries.add(getResourcesForSubId().getString(R.string.network_1x));
            mEntriesValue.add(value);
        }


        private int getSelectableGenerationMaskInternal() {
            int generations = 0;
            if (mDisplay2gOptions) generations |= GENERATION_2G;
            if (mDisplay3gOptions) generations |= GENERATION_3G;
            if (mDisplay4gOptions) generations |= GENERATION_4G;
            if (mDisplay5gOptions) generations |= GENERATION_5G;
            return generations;
        }

        private void addAdvancedEntries() {
            final int selectableGenerations = getSelectableGenerationMaskInternal();
            for (int generationCount = Integer.bitCount(GENERATION_ALL);
                    generationCount >= 1; generationCount--) {
                for (int combination = GENERATION_ALL; combination >= GENERATION_2G;
                        combination--) {
                    if (Integer.bitCount(combination) == generationCount
                            && (combination & selectableGenerations) == combination) {
                        addGenerationCombination(combination);
                    }
                }
            }
        }

        private void addGenerationCombination(int generations) {
            final long raf = buildGenerationRaf(generations);
            if (raf == 0 || containsEntryWithRaf(raf)) {
                return;
            }

            final int customValue = CUSTOM_MODE_BASE + generations;
            final String label = getResourcesForSubId().getString(
                    R.string.evolver_network_mode_only_format,
                    buildGenerationLabel(generations));
            mEntries.add(label);
            mEntriesValue.add(customValue);
            mCustomModeRaf.put(customValue, raf);
            mCustomModeSummary.put(customValue, label);
        }

        private long buildGenerationRaf(int generations) {
            long raf = 0;
            if ((generations & GENERATION_2G) != 0) raf |= mSupportedRaf & BITMASK_2G;
            if ((generations & GENERATION_3G) != 0) raf |= mSupportedRaf & BITMASK_3G;
            if ((generations & GENERATION_4G) != 0) raf |= mSupportedRaf & BITMASK_4G;
            if ((generations & GENERATION_5G) != 0) raf |= mSupportedRaf & BITMASK_5G;
            return raf;
        }

        private String buildGenerationLabel(int generations) {
            final List<String> labels = new ArrayList<>();
            if ((generations & GENERATION_5G) != 0) labels.add("5G");
            if ((generations & GENERATION_4G) != 0) labels.add(mShow4gForLTE ? "4G" : "LTE");
            if ((generations & GENERATION_3G) != 0) labels.add("3G");
            if ((generations & GENERATION_2G) != 0) labels.add("2G");
            return String.join(" / ", labels);
        }

        private boolean containsEntryWithRaf(long targetRaf) {
            final long selectableRaf = buildGenerationRaf(getSelectableGenerationMaskInternal());
            for (Integer value : mEntriesValue) {
                final Long customRaf = mCustomModeRaf.get(value);
                final long entryRaf = customRaf != null
                        ? customRaf
                        : Integer.toUnsignedLong(RadioAccessFamily.getRafFromNetworkType(value));
                if ((entryRaf & selectableRaf) == targetRaf) {
                    return true;
                }
            }
            return false;
        }

        @VisibleForTesting
        int getSelectableGenerationMask() {
            return getSelectableGenerationMaskInternal();
        }

        @VisibleForTesting
        boolean hasGenerationCombination(int generations) {
            final int selectableGenerations = getSelectableGenerationMaskInternal();
            if ((generations & selectableGenerations) != generations) {
                return false;
            }
            final long targetRaf = buildGenerationRaf(generations);
            if (targetRaf == 0) {
                return false;
            }
            return containsEntryWithRaf(targetRaf);
        }

        boolean isCustomMode(int value) {
            return mCustomModeRaf.containsKey(value);
        }

        long getCustomAllowedNetworkTypes(int value) {
            final Long raf = mCustomModeRaf.get(value);
            return raf != null ? raf : 0;
        }

        private void addCustomEntry(String name, int value) {
            mEntries.add(name);
            mEntriesValue.add(value);
        }

        private String[] getEntries() {
            return mEntries.toArray(new String[0]);
        }

        private void clearAllEntries() {
            mEntries.clear();
            mEntriesValue.clear();
            mCustomModeRaf.clear();
            mCustomModeSummary.clear();
            mIs5gEntryDisplayed = false;
        }

        private String[] getEntryValues() {
            final Integer[] intArr = mEntriesValue.toArray(new Integer[0]);
            return Arrays.stream(intArr)
                    .map(String::valueOf)
                    .toArray(String[]::new);
        }

        /** Return the selected entry. */
        public int getSelectedEntryValue() {
            return mSelectedEntry;
        }

        private void setSelectedEntry(int value) {
            boolean isInEntriesValue = mEntriesValue.stream()
                    .anyMatch(v -> v == value);

            if (isInEntriesValue) {
                mSelectedEntry = value;
            } else if (mEntriesValue.size() > 0) {
                // if the value isn't in entriesValue, select on the first one.
                mSelectedEntry = mEntriesValue.get(0);
            } else {
                Log.e(LOG_TAG, "entriesValue is empty");
            }
        }

        /** Return the summary. */
        public String getSummary() {
            return mSummary;
        }

        private void setSummary(int summaryResId) {
            setSummary(getResourcesForSubId().getString(summaryResId));
        }

        private void setSummary(String summary) {
            this.mSummary = summary;
        }

        private boolean is5gEntryDisplayed() {
            return mIs5gEntryDisplayed;
        }

        /**
         * Returns the resources associated with Subscription.
         *
         * @return Resources associated with Subscription.
         */
        @VisibleForTesting
        Resources getResourcesForSubId() {
            return SubscriptionManager.getResourcesForSubId(mContext, mSubId);
        }

        /** Refresh builder data */
        public void refresh() {
            setPreferenceEntries();
            setPreferenceValueAndSummary();
        }

        /** Updates the list preference */
        public void updateListPreference(ListPreference listPreference) {
            refresh();
            listPreference.setEntries(getEntries());
            listPreference.setEntryValues(getEntryValues());
            listPreference.setValue(Integer.toString(getSelectedEntryValue()));
            listPreference.setSummary(getSummary());
        }
    }

    @VisibleForTesting
    class PhoneCallStateTelephonyCallback extends TelephonyCallback implements
            TelephonyCallback.CallStateListener {

        private TelephonyManager mTelephonyManager;

        @Override
        public void onCallStateChanged(int state) {
            Log.d(LOG_TAG, "onCallStateChanged:" + state);
            mCallState = state;
            mBuilder.updateConfig();
            updatePreference();
        }

        public void register(TelephonyManager telephonyManager, int subId) {
            mTelephonyManager = telephonyManager;

            // assign current call state so that it helps to show correct preference state even
            // before first onCallStateChanged() by initial registration.
            try {
                mCallState = mTelephonyManager.getCallState(subId);
            } catch (UnsupportedOperationException e) {
                // Device doesn't support FEATURE_TELEPHONY_CALLING
                mCallState = TelephonyManager.CALL_STATE_IDLE;
            }
            mTelephonyManager.registerTelephonyCallback(
                    mContext.getMainExecutor(), mTelephonyCallback);
        }

        public void unregister() {
            mCallState = TelephonyManager.CALL_STATE_IDLE;
            if (mTelephonyManager != null) {
                mTelephonyManager.unregisterTelephonyCallback(this);
            }
        }
    }

    @Override
    public void onAirplaneModeChanged(boolean airplaneModeEnabled) {
    }

    @Override
    public void onSubscriptionsChanged() {
        if (mBuilder != null) {
            mBuilder.updateConfig();
            updatePreference();
        }
    }
}
// LINT.ThenChange(EnabledNetworkModePreference.kt)
