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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable model for one user-selectable preferred network mode. */
public final class NetworkModeEntry {

    public enum RadioFamily {
        NR,
        LTE,
        TD_SCDMA,
        WCDMA,
        EVDO,
        CDMA,
        GSM,
    }

    public enum RestrictionReason {
        CARRIER,
        POWER,
        TEST,
        TWO_G_DISABLED,
        ADMIN,
    }

    private final int mNetworkMode;
    private final long mRaf;
    private final int mGenerationMask;
    private final Set<RadioFamily> mRadioFamilies;
    private final String mBasicLabel;
    private final String mTechnicalLabel;
    private final boolean mHardwareSupported;
    private final boolean mAllowedByRuntimePolicy;
    private final boolean mCurrent;
    private final boolean mEffective;
    private final boolean mSelectable;
    private final Set<RestrictionReason> mRestrictionReasons;

    public NetworkModeEntry(
            int networkMode,
            long raf,
            int generationMask,
            Set<RadioFamily> radioFamilies,
            String basicLabel,
            String technicalLabel,
            boolean hardwareSupported,
            boolean allowedByRuntimePolicy,
            boolean current,
            boolean effective,
            boolean selectable,
            Set<RestrictionReason> restrictionReasons) {
        mNetworkMode = networkMode;
        mRaf = raf;
        mGenerationMask = generationMask;
        mRadioFamilies = immutableEnumSet(radioFamilies, RadioFamily.class);
        mBasicLabel = basicLabel;
        mTechnicalLabel = technicalLabel;
        mHardwareSupported = hardwareSupported;
        mAllowedByRuntimePolicy = allowedByRuntimePolicy;
        mCurrent = current;
        mEffective = effective;
        mSelectable = selectable;
        mRestrictionReasons =
                immutableEnumSet(restrictionReasons, RestrictionReason.class);
    }

    public int getNetworkMode() {
        return mNetworkMode;
    }

    public long getRaf() {
        return mRaf;
    }

    public int getGenerationMask() {
        return mGenerationMask;
    }

    public Set<RadioFamily> getRadioFamilies() {
        return mRadioFamilies;
    }

    public String getBasicLabel() {
        return mBasicLabel;
    }

    public String getTechnicalLabel() {
        return mTechnicalLabel;
    }

    public boolean isHardwareSupported() {
        return mHardwareSupported;
    }

    /**
     * Returns whether non-USER runtime policy reasons currently permit this mode.
     *
     * <p>This deliberately excludes the user's own selected mask. A mode may therefore be
     * policy-allowed even when it is not the effective mode right now.</p>
     */
    public boolean isAllowedByRuntimePolicy() {
        return mAllowedByRuntimePolicy;
    }

    /** Compatibility alias for older callers. */
    public boolean isCurrentlyAllowed() {
        return isAllowedByRuntimePolicy();
    }

    public boolean isCurrent() {
        return mCurrent;
    }

    public boolean isEffective() {
        return mEffective;
    }

    public boolean isSelectable() {
        return mSelectable;
    }

    /** Returns whether this mode depends on legacy CDMA/EVDO/TD-SCDMA families. */
    public boolean isLegacyRadioFamilyMode() {
        return mRadioFamilies.contains(RadioFamily.CDMA)
                || mRadioFamilies.contains(RadioFamily.EVDO)
                || mRadioFamilies.contains(RadioFamily.TD_SCDMA);
    }

    public Set<RestrictionReason> getRestrictionReasons() {
        return mRestrictionReasons;
    }

    public NetworkModeEntry withTechnicalLabel(String technicalLabel) {
        return new NetworkModeEntry(
                mNetworkMode,
                mRaf,
                mGenerationMask,
                mRadioFamilies,
                mBasicLabel,
                technicalLabel,
                mHardwareSupported,
                mAllowedByRuntimePolicy,
                mCurrent,
                mEffective,
                mSelectable,
                mRestrictionReasons);
    }

    public NetworkModeEntry withCurrent(boolean current) {
        return new NetworkModeEntry(
                mNetworkMode,
                mRaf,
                mGenerationMask,
                mRadioFamilies,
                mBasicLabel,
                mTechnicalLabel,
                mHardwareSupported,
                mAllowedByRuntimePolicy,
                current,
                mEffective,
                mSelectable,
                mRestrictionReasons);
    }

    public NetworkModeEntry withEffective(boolean effective) {
        return new NetworkModeEntry(
                mNetworkMode,
                mRaf,
                mGenerationMask,
                mRadioFamilies,
                mBasicLabel,
                mTechnicalLabel,
                mHardwareSupported,
                mAllowedByRuntimePolicy,
                mCurrent,
                effective,
                mSelectable,
                mRestrictionReasons);
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(
            Set<E> source, Class<E> enumClass) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        final EnumSet<E> copy = EnumSet.noneOf(enumClass);
        copy.addAll(source);
        return Collections.unmodifiableSet(copy);
    }
}
