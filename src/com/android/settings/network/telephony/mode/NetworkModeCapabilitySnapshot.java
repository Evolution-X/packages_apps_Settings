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

import android.content.Context;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.settingslib.RestrictedLockUtilsInternal;

/**
 * Immutable snapshot of every network-type mask that contributes to the effective radio policy.
 *
 * <p>This separates three different truths that must not be conflated by the UI:
 * hardware support, the user's preferred mask, and the effective mask after runtime policy
 * intersections.</p>
 */
public final class NetworkModeCapabilitySnapshot {

    private static final String TAG = "NetworkModeCapabilities";
    private static final long ALL_RAF = -1L;

    private final long mSupportedRaf;
    private final long mUserRaf;
    private final long mCarrierRaf;
    private final long mPowerRaf;
    private final long mTestRaf;
    private final long mEnable2gRaf;
    private final long mEffectiveRaf;
    private final boolean mAdminDisables2g;
    private final boolean mAllowedNetworkTypesBitmaskSupported;
    private final boolean mNrDualConnectivitySupported;
    private final boolean mNrDualConnectivityStateKnown;
    private final boolean mNrDualConnectivityEnabled;
    private final boolean mVoNrStateKnown;
    private final boolean mVoNrEnabled;

    private NetworkModeCapabilitySnapshot(
            long supportedRaf,
            long userRaf,
            long carrierRaf,
            long powerRaf,
            long testRaf,
            long enable2gRaf,
            long effectiveRaf,
            boolean adminDisables2g,
            boolean allowedNetworkTypesBitmaskSupported,
            boolean nrDualConnectivitySupported,
            boolean nrDualConnectivityStateKnown,
            boolean nrDualConnectivityEnabled,
            boolean voNrStateKnown,
            boolean voNrEnabled) {
        mSupportedRaf = supportedRaf;
        mUserRaf = userRaf;
        mCarrierRaf = carrierRaf;
        mPowerRaf = powerRaf;
        mTestRaf = testRaf;
        mEnable2gRaf = enable2gRaf;
        mEffectiveRaf = effectiveRaf;
        mAdminDisables2g = adminDisables2g;
        mAllowedNetworkTypesBitmaskSupported = allowedNetworkTypesBitmaskSupported;
        mNrDualConnectivitySupported = nrDualConnectivitySupported;
        mNrDualConnectivityStateKnown = nrDualConnectivityStateKnown;
        mNrDualConnectivityEnabled = nrDualConnectivityEnabled;
        mVoNrStateKnown = voNrStateKnown;
        mVoNrEnabled = voNrEnabled;
    }

    public static NetworkModeCapabilitySnapshot capture(
            Context context, TelephonyManager telephonyManager) {
        if (telephonyManager == null) {
            return new NetworkModeCapabilitySnapshot(
                    0L,
                    0L,
                    ALL_RAF,
                    ALL_RAF,
                    ALL_RAF,
                    ALL_RAF,
                    0L,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false);
        }

        final long supportedRaf = getSupportedRaf(telephonyManager);
        final boolean allowedNetworkTypesBitmaskSupported =
                isRadioCapabilitySupported(
                        telephonyManager,
                        TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK);

        // USER is still read best-effort for the legacy fallback, but the additional policy
        // reasons are meaningful only when the modem advertises the modern bitmask API.
        final long userRaf = getAllowedRaf(
                telephonyManager, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        final long carrierRaf = allowedNetworkTypesBitmaskSupported
                ? getAllowedRaf(
                        telephonyManager, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER)
                : ALL_RAF;
        final long powerRaf = allowedNetworkTypesBitmaskSupported
                ? getAllowedRaf(
                        telephonyManager, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER)
                : ALL_RAF;
        final long testRaf = allowedNetworkTypesBitmaskSupported && Build.IS_DEBUGGABLE
                ? getAllowedRaf(
                        telephonyManager, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_TEST)
                : ALL_RAF;
        final long rawEnable2gRaf = allowedNetworkTypesBitmaskSupported
                ? getAllowedRaf(
                        telephonyManager, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G)
                : ALL_RAF;
        // AOSP's 2G controller treats a zero ENABLE_2G mask as corrupted/uninitialized and
        // repairs it before use. Do not let that transient sentinel collapse the effective mask.
        final long enable2gRaf = rawEnable2gRaf == 0 ? ALL_RAF : rawEnable2gRaf;
        final boolean adminDisables2g = context != null && is2gDisabledByAdmin(context);

        long effectiveRaf = supportedRaf;
        effectiveRaf = intersectIfKnown(effectiveRaf, userRaf);
        effectiveRaf = intersectIfKnown(effectiveRaf, carrierRaf);
        effectiveRaf = intersectIfKnown(effectiveRaf, powerRaf);
        effectiveRaf = intersectIfKnown(effectiveRaf, testRaf);
        effectiveRaf = intersectIfKnown(effectiveRaf, enable2gRaf);
        if (adminDisables2g) {
            effectiveRaf &= ~NetworkModes.get2gRaf();
        }

        final boolean nrDualConnectivitySupported =
                isRadioCapabilitySupported(
                        telephonyManager,
                        TelephonyManager.CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE);

        boolean nrDualConnectivityStateKnown = false;
        boolean nrDualConnectivityEnabled = false;
        if (nrDualConnectivitySupported) {
            try {
                nrDualConnectivityEnabled = telephonyManager.isNrDualConnectivityEnabled();
                nrDualConnectivityStateKnown = true;
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to read NR dual-connectivity state", e);
            }
        }

        boolean voNrStateKnown = false;
        boolean voNrEnabled = false;
        if ((supportedRaf & NetworkModes.get5gRaf()) != 0) {
            try {
                voNrEnabled = telephonyManager.isVoNrEnabled();
                voNrStateKnown = true;
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to read VoNR state", e);
            }
        }

        return new NetworkModeCapabilitySnapshot(
                supportedRaf,
                userRaf,
                carrierRaf,
                powerRaf,
                testRaf,
                enable2gRaf,
                effectiveRaf,
                adminDisables2g,
                allowedNetworkTypesBitmaskSupported,
                nrDualConnectivitySupported,
                nrDualConnectivityStateKnown,
                nrDualConnectivityEnabled,
                voNrStateKnown,
                voNrEnabled);
    }

    public long getSupportedRaf() {
        return mSupportedRaf;
    }

    public long getUserRaf() {
        return mUserRaf;
    }

    public long getCarrierRaf() {
        return mCarrierRaf;
    }

    public long getPowerRaf() {
        return mPowerRaf;
    }

    public long getTestRaf() {
        return mTestRaf;
    }

    public long getEnable2gRaf() {
        return mEnable2gRaf;
    }

    public long getEffectiveRaf() {
        return mEffectiveRaf;
    }

    public boolean isAdmin2gDisabled() {
        return mAdminDisables2g;
    }

    public boolean isAllowedNetworkTypesBitmaskSupported() {
        return mAllowedNetworkTypesBitmaskSupported;
    }

    public boolean isNrDualConnectivitySupported() {
        return mNrDualConnectivitySupported;
    }

    public boolean isNrDualConnectivityStateKnown() {
        return mNrDualConnectivityStateKnown;
    }

    public boolean isNrDualConnectivityEnabled() {
        return mNrDualConnectivityEnabled;
    }

    public boolean isVoNrStateKnown() {
        return mVoNrStateKnown;
    }

    public boolean isVoNrEnabled() {
        return mVoNrEnabled;
    }

    public int getUserNetworkMode() {
        return mUserRaf < 0
                ? NetworkModes.NETWORK_MODE_UNKNOWN
                : NetworkModes.getStandardNetworkModeFromRaf(mUserRaf);
    }

    public int getEffectiveNetworkMode() {
        return mUserRaf < 0
                ? NetworkModes.NETWORK_MODE_UNKNOWN
                : NetworkModes.getStandardNetworkModeFromRaf(mEffectiveRaf);
    }

    public boolean isEffectiveStateKnown() {
        return mAllowedNetworkTypesBitmaskSupported && mSupportedRaf > 0 && mUserRaf >= 0;
    }

    public boolean hasKnownHardwareCapabilities() {
        return mSupportedRaf > 0;
    }

    @Override
    public String toString() {
        return "NetworkModeCapabilitySnapshot{"
                + "supported=0x" + Long.toHexString(mSupportedRaf)
                + ", user=0x" + Long.toHexString(mUserRaf)
                + ", carrier=0x" + Long.toHexString(mCarrierRaf)
                + ", power=0x" + Long.toHexString(mPowerRaf)
                + ", test=0x" + Long.toHexString(mTestRaf)
                + ", enable2g=0x" + Long.toHexString(mEnable2gRaf)
                + ", effective=0x" + Long.toHexString(mEffectiveRaf)
                + ", admin2g=" + mAdminDisables2g
                + ", bitmaskApi=" + mAllowedNetworkTypesBitmaskSupported
                + ", nrDcSupported=" + mNrDualConnectivitySupported
                + ", nrDc=" + (mNrDualConnectivityStateKnown
                        ? Boolean.toString(mNrDualConnectivityEnabled) : "unknown")
                + ", voNr=" + (mVoNrStateKnown ? Boolean.toString(mVoNrEnabled) : "unknown")
                + '}';
    }

    private static boolean isRadioCapabilitySupported(
            TelephonyManager telephonyManager, String capability) {
        try {
            return telephonyManager.isRadioInterfaceCapabilitySupported(capability);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to query radio capability " + capability, e);
            return false;
        }
    }

    private static long getSupportedRaf(TelephonyManager telephonyManager) {
        try {
            return telephonyManager.getSupportedRadioAccessFamily();
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read supported radio access family", e);
            return 0L;
        }
    }

    private static long getAllowedRaf(TelephonyManager telephonyManager, int reason) {
        try {
            return telephonyManager.getAllowedNetworkTypesForReason(reason);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read allowed network types for reason " + reason, e);
            return ALL_RAF;
        }
    }

    private static long intersectIfKnown(long base, long constraint) {
        return constraint < 0 ? base : base & constraint;
    }

    private static boolean is2gDisabledByAdmin(Context context) {
        try {
            return RestrictedLockUtilsInternal.checkIfRestrictionEnforced(
                    context,
                    UserManager.DISALLOW_CELLULAR_2G,
                    UserHandle.myUserId()) != null;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read 2G admin restriction", e);
            return false;
        }
    }
}
