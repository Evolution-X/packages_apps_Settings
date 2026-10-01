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

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteModemStateCallback;
import android.telephony.satellite.SelectedNbIotSatelliteSubscriptionCallback;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.network.AllowedNetworkTypesListener;
import com.android.settings.network.SubscriptionsChangeListener;
import com.android.settings.network.telephony.mode.LegacyNetworkModeFallback;
import com.android.settings.network.telephony.mode.NetworkModeCapabilitySnapshot;
import com.android.settings.network.telephony.mode.NetworkModeEntry;
import com.android.settings.network.telephony.mode.NetworkModeEntry.RadioFamily;
import com.android.settings.network.telephony.mode.NetworkModeEntry.RestrictionReason;
import com.android.settings.network.telephony.mode.NetworkModes;
import com.android.settings.network.telephony.mode.SupportedNetworkModeCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Preference controller for the curated preferred-network-mode selector.
 */
// LINT.IfChange
public class EnabledNetworkModePreferenceController extends
        BasePreferenceController implements
        Preference.OnPreferenceChangeListener, DefaultLifecycleObserver,
        SubscriptionsChangeListener.SubscriptionsChangeListenerClient, AirplaneModeChangedCallback {

    private static final String LOG_TAG = "EnabledNetworkMode";

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
    private boolean mIsSatelliteSessionStarted;
    private boolean mIsCurrentSubscriptionForSatellite;
    protected boolean mIsAirplaneModeOn;
    private boolean mNetworkModeApplyInFlight;
    private int mPendingNetworkMode = NetworkModes.NETWORK_MODE_UNKNOWN;

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
        mTelephonyCallback = new PhoneCallStateTelephonyCallback();
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
                Log.w(LOG_TAG, "Unable to register satellite callbacks", e);
            }
        }

        mSubscriptionsListener.start();
        if (mAllowedNetworkTypesListener == null || mTelephonyCallback == null
                || mTelephonyManager == null) {
            return;
        }
        mAllowedNetworkTypesListener.register(mContext, mSubId);
        mTelephonyCallback.register(mTelephonyManager, mSubId);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        mSubscriptionsListener.stop();
        dismissPreferenceDialog();
        // A lifecycle-scoped apply coroutine is cancelled with the view. Always rebuild from
        // telephony on the next start rather than leaving a stale in-flight UI state behind.
        mNetworkModeApplyInFlight = false;
        mPendingNetworkMode = NetworkModes.NETWORK_MODE_UNKNOWN;

        if (mSatelliteManager != null) {
            try {
                mSatelliteManager.unregisterForModemStateChanged(mSatelliteModemStateCallback);
                mSatelliteManager.unregisterForSelectedNbIotSatelliteSubscriptionChanged(
                        mSelectedNbIotSatelliteSubscriptionCallback);
            } catch (IllegalStateException e) {
                Log.w(LOG_TAG, "Unable to unregister satellite callbacks", e);
            }
        }

        if (mAllowedNetworkTypesListener != null) {
            mAllowedNetworkTypesListener.unregister(mContext, mSubId);
        }
        if (mTelephonyCallback != null) {
            mTelephonyCallback.unregister();
        }
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
        if (mBuilder == null || !(preference instanceof NetworkModePreference)) {
            return;
        }

        final NetworkModePreference networkPreference = (NetworkModePreference) preference;
        mBuilder.updateListPreference(networkPreference);

        final boolean enabled = isPreferenceShallEnabled();
        networkPreference.setEnabled(enabled);
        if (!enabled) {
            dismissPreferenceDialog();
        }
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object object) {
        if (!(preference instanceof NetworkModePreference)
                || mTelephonyManager == null
                || mViewLifecycleOwner == null
                || mNetworkModeApplyInFlight) {
            return false;
        }

        final int requestedMode;
        try {
            requestedMode = Integer.parseInt(String.valueOf(object));
        } catch (NumberFormatException e) {
            Log.w(LOG_TAG, "Ignoring invalid network mode: " + object, e);
            return false;
        }

        mNetworkModeApplyInFlight = true;
        mPendingNetworkMode = requestedMode;
        preference.setEnabled(false);
        preference.setSummary(R.string.network_mode_applying);

        final int requestSubId = mSubId;
        setAllowedNetworkTypes(
                mTelephonyManager,
                mViewLifecycleOwner,
                requestedMode,
                (requested, actual, success) -> {
                    if (requestSubId != mSubId || requested != mPendingNetworkMode) {
                        return;
                    }

                    mNetworkModeApplyInFlight = false;
                    mPendingNetworkMode = NetworkModes.NETWORK_MODE_UNKNOWN;

                    if (!success) {
                        Log.w(LOG_TAG, "Network mode rejected. requested=" + requested
                                + ", actual=" + actual);
                        Toast.makeText(
                                mContext,
                                R.string.network_mode_apply_failed,
                                Toast.LENGTH_LONG).show();
                    }
                    updatePreference();
                });

        // The UI is updated only after telephony confirms the USER reason value.
        return false;
    }

    @Override
    public void notifyAirplaneModeChanged(boolean isAirplaneModeOn) {
        mIsAirplaneModeOn = isAirplaneModeOn;
    }

    public void init(int subId, FragmentManager fragmentManager) {
        if (mSubId != subId) {
            mNetworkModeApplyInFlight = false;
            mPendingNetworkMode = NetworkModes.NETWORK_MODE_UNKNOWN;
            dismissPreferenceDialog();
        }
        mSubId = subId;
        mFragmentManager = fragmentManager;
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class)
                .createForSubscriptionId(mSubId);
        mBuilder = new PreferenceEntriesBuilder(mContext, mSubId);

        if (mAllowedNetworkTypesListener == null) {
            mAllowedNetworkTypesListener =
                    new AllowedNetworkTypesListener(mContext.getMainExecutor());
            mAllowedNetworkTypesListener.setAllowedNetworkTypesListener(this::updatePreference);
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
        return !mNetworkModeApplyInFlight
                && isCallStateIdle()
                && !(mIsSatelliteSessionStarted && mIsCurrentSubscriptionForSatellite)
                && !mIsAirplaneModeOn;
    }

    private void dismissPreferenceDialog() {
        NetworkModePreference.dismissDialog(mFragmentManager);
    }

    public static class PreferenceEntriesBuilder {
        private final Context mContext;
        private final int mSubId;
        private TelephonyManager mTelephonyManager;

        private List<NetworkModeEntry> mEntries = new ArrayList<>();
        private NetworkModeCapabilitySnapshot mCapabilities;
        private int mSelectedEntry = NetworkModes.NETWORK_MODE_UNKNOWN;
        private int mInjectedCurrentMode = NetworkModes.NETWORK_MODE_UNKNOWN;
        private String mSummary = "";

        PreferenceEntriesBuilder(Context context, int subId) {
            mContext = context;
            mSubId = subId;
            updateConfig();
        }

        public void updateConfig() {
            mTelephonyManager = mContext.getSystemService(TelephonyManager.class)
                    .createForSubscriptionId(mSubId);
        }

        private void setPreferenceEntries() {
            mInjectedCurrentMode = NetworkModes.NETWORK_MODE_UNKNOWN;
            final SupportedNetworkModeCatalog.Resolution resolution =
                    SupportedNetworkModeCatalog.resolve(mContext, mTelephonyManager);
            mCapabilities = resolution.getCapabilities();
            mEntries = new ArrayList<>(resolution.getEntries());

            final int currentMode = getPreferredNetworkMode();
            if (mEntries.isEmpty()) {
                mEntries = new ArrayList<>(
                        LegacyNetworkModeFallback.build(
                                mContext, mTelephonyManager, mCapabilities));
                if (!mEntries.isEmpty()) {
                    mInjectedCurrentMode = currentMode;
                }
            }

            if (findEntryIndex(currentMode) < 0
                    && currentMode != NetworkModes.NETWORK_MODE_UNKNOWN) {
                final NetworkModeEntry currentEntry =
                        SupportedNetworkModeCatalog.describeNetworkMode(
                                mContext, mCapabilities, currentMode);
                if (currentEntry != null) {
                    mEntries.add(0, currentEntry.withCurrent(true));
                    mInjectedCurrentMode = currentMode;
                }
            }

            Log.d(LOG_TAG, "Resolved " + mEntries.size()
                    + " preferred network modes for subId=" + mSubId
                    + ", capabilities=" + mCapabilities);
        }

        private int getPreferredNetworkMode() {
            return mCapabilities != null
                    ? mCapabilities.getUserNetworkMode()
                    : SupportedNetworkModeCatalog.getCurrentNetworkMode(mTelephonyManager);
        }

        void setPreferenceValueAndSummary(int networkMode) {
            int index = findEntryIndex(networkMode);
            if (index < 0 && networkMode != NetworkModes.NETWORK_MODE_UNKNOWN) {
                final NetworkModeEntry currentEntry =
                        SupportedNetworkModeCatalog.describeNetworkMode(
                                mContext, mTelephonyManager, networkMode);
                if (currentEntry != null) {
                    mEntries.add(0, currentEntry.withCurrent(true));
                    index = 0;
                }
            }

            if (index >= 0) {
                mSelectedEntry = networkMode;
                final String selectedLabel = mEntries.get(index).getBasicLabel();
                final String effectiveLabel = findEffectiveLabel();
                mSummary = effectiveLabel != null
                                && !effectiveLabel.equals(selectedLabel)
                        ? mContext.getString(
                                R.string.network_mode_summary_effective,
                                selectedLabel,
                                effectiveLabel)
                        : selectedLabel;
            } else {
                mSelectedEntry = NetworkModes.NETWORK_MODE_UNKNOWN;
                mSummary = mContext.getString(R.string.mobile_network_mode_error, networkMode);
            }
        }

        private void setPreferenceValueAndSummary() {
            setPreferenceValueAndSummary(getPreferredNetworkMode());
        }

        public int getSelectedEntryValue() {
            return mSelectedEntry;
        }

        public String getSummary() {
            return mSummary;
        }

        public List<NetworkModeEntry> getModeEntries() {
            return new ArrayList<>(mEntries);
        }

        public NetworkModeCapabilitySnapshot getCapabilities() {
            return mCapabilities;
        }

        public void refresh() {
            updateConfig();
            setPreferenceEntries();
            setPreferenceValueAndSummary();
        }

        public void updateListPreference(NetworkModePreference preference) {
            refresh();

            final CharSequence[] sections = new CharSequence[mEntries.size()];
            final CharSequence[] labels = new CharSequence[mEntries.size()];
            final CharSequence[] values = new CharSequence[mEntries.size()];
            final CharSequence[] details = new CharSequence[mEntries.size()];
            final CharSequence[] statuses = new CharSequence[mEntries.size()];
            final boolean[] selectable = new boolean[mEntries.size()];

            int previousGroup = Integer.MIN_VALUE;
            for (int index = 0; index < mEntries.size(); index++) {
                final NetworkModeEntry entry = mEntries.get(index);
                final int group = getGroup(entry);
                if (group != previousGroup) {
                    sections[index] = getGroupLabel(group);
                    previousGroup = group;
                }
                labels[index] = entry.getBasicLabel();
                values[index] = String.valueOf(entry.getNetworkMode());
                details[index] = entry.getNetworkMode() == mInjectedCurrentMode
                        ? mContext.getString(
                                R.string.network_mode_technical_variant,
                                entry.getTechnicalLabel(),
                                mContext.getString(
                                        R.string.network_mode_current_unlisted_detail))
                        : entry.getTechnicalLabel();
                statuses[index] = buildStatus(entry);
                selectable[index] = entry.isSelectable();
            }

            preference.setEntries(labels);
            preference.setEntryValues(values);
            preference.setEntrySections(sections);
            preference.setEntryDetails(details);
            preference.setEntryStatuses(statuses);
            preference.setEntrySelectable(selectable);
            if (mSelectedEntry != NetworkModes.NETWORK_MODE_UNKNOWN) {
                preference.setValue(String.valueOf(mSelectedEntry));
            }
            preference.setSummary(mSummary);
        }

        private int findEntryIndex(int networkMode) {
            for (int index = 0; index < mEntries.size(); index++) {
                if (mEntries.get(index).getNetworkMode() == networkMode) {
                    return index;
                }
            }
            return -1;
        }

        private String findEffectiveLabel() {
            for (NetworkModeEntry entry : mEntries) {
                if (entry.isEffective()) {
                    return entry.getBasicLabel();
                }
            }
            if (mCapabilities != null && mCapabilities.isEffectiveStateKnown()) {
                return SupportedNetworkModeCatalog.getBasicLabelForRaf(
                        mContext, mCapabilities.getEffectiveRaf());
            }
            return null;
        }

        private String getGroupLabel(int group) {
            switch (group) {
                case 5:
                    return mContext.getString(R.string.network_mode_group_5g);
                case 4:
                    return mContext.getString(R.string.network_mode_group_4g);
                case 3:
                    return mContext.getString(R.string.network_mode_group_3g);
                case 2:
                    return mContext.getString(R.string.network_mode_group_2g);
                default:
                    return mContext.getString(R.string.network_mode_group_legacy);
            }
        }

        private static int getGroup(NetworkModeEntry entry) {
            return entry.isLegacyRadioFamilyMode()
                    ? 0
                    : getHighestGeneration(entry.getGenerationMask());
        }

        private static int getHighestGeneration(int generationMask) {
            if ((generationMask & SupportedNetworkModeCatalog.GENERATION_5G) != 0) {
                return 5;
            }
            if ((generationMask & SupportedNetworkModeCatalog.GENERATION_4G) != 0) {
                return 4;
            }
            if ((generationMask & SupportedNetworkModeCatalog.GENERATION_3G) != 0) {
                return 3;
            }
            if ((generationMask & SupportedNetworkModeCatalog.GENERATION_2G) != 0) {
                return 2;
            }
            return 0;
        }

        private String buildStatus(NetworkModeEntry entry) {
            final List<String> status = new ArrayList<>();
            if (entry.isCurrent()
                    || entry.getNetworkMode() == mSelectedEntry) {
                status.add(mContext.getString(R.string.network_mode_current_selection));
            }
            if (entry.isEffective()) {
                status.add(mContext.getString(R.string.network_mode_effective_now));
            }
            if (entry.getNetworkMode() == mInjectedCurrentMode) {
                status.add(mContext.getString(R.string.network_mode_current_unlisted));
            }

            for (RestrictionReason reason : entry.getRestrictionReasons()) {
                switch (reason) {
                    case CARRIER:
                        status.add(mContext.getString(
                                R.string.network_mode_restricted_carrier));
                        break;
                    case POWER:
                        status.add(mContext.getString(
                                R.string.network_mode_restricted_power));
                        break;
                    case TEST:
                        status.add(mContext.getString(
                                R.string.network_mode_restricted_test));
                        break;
                    case TWO_G_DISABLED:
                        status.add(mContext.getString(
                                R.string.network_mode_restricted_2g));
                        break;
                    case ADMIN:
                        status.add(mContext.getString(
                                R.string.network_mode_restricted_admin));
                        break;
                }
            }

            if ((entry.isCurrent() || entry.isEffective())
                    && entry.getRadioFamilies().contains(RadioFamily.NR)
                    && mCapabilities != null) {
                if (mCapabilities.isNrDualConnectivitySupported()
                        && mCapabilities.isNrDualConnectivityStateKnown()) {
                    status.add(mContext.getString(
                            mCapabilities.isNrDualConnectivityEnabled()
                                    ? R.string.network_mode_nr_dc_enabled
                                    : R.string.network_mode_nr_dc_disabled));
                }
                if (mCapabilities.isVoNrStateKnown()) {
                    status.add(mContext.getString(
                            mCapabilities.isVoNrEnabled()
                                    ? R.string.network_mode_vonr_enabled
                                    : R.string.network_mode_vonr_disabled));
                }
            }

            final String separator =
                    mContext.getString(R.string.network_mode_status_separator);
            final StringBuilder builder = new StringBuilder();
            for (String item : status) {
                if (builder.length() > 0) {
                    builder.append(separator);
                }
                builder.append(item);
            }
            return builder.toString();
        }
    }

    @VisibleForTesting
    class PhoneCallStateTelephonyCallback extends TelephonyCallback implements
            TelephonyCallback.CallStateListener {

        private TelephonyManager mRegisteredTelephonyManager;

        @Override
        public void onCallStateChanged(int state) {
            Log.d(LOG_TAG, "onCallStateChanged:" + state);
            mCallState = state;
            updatePreference();
        }

        public void register(TelephonyManager telephonyManager, int subId) {
            mRegisteredTelephonyManager = telephonyManager;
            try {
                mCallState = telephonyManager.getCallState(subId);
            } catch (UnsupportedOperationException e) {
                mCallState = TelephonyManager.CALL_STATE_IDLE;
            }
            telephonyManager.registerTelephonyCallback(
                    mContext.getMainExecutor(), this);
        }

        public void unregister() {
            mCallState = TelephonyManager.CALL_STATE_IDLE;
            if (mRegisteredTelephonyManager != null) {
                mRegisteredTelephonyManager.unregisterTelephonyCallback(this);
                mRegisteredTelephonyManager = null;
            }
        }
    }

    @Override
    public void onAirplaneModeChanged(boolean airplaneModeEnabled) {
    }

    @Override
    public void onSubscriptionsChanged() {
        if (mBuilder != null) {
            updatePreference();
        }
    }
}
// LINT.ThenChange(EnabledNetworkModePreference.kt)
