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

import android.content.Context;
import android.telephony.RadioAccessFamily;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.network.telephony.mode.NetworkModeEntry.RadioFamily;
import com.android.settings.network.telephony.mode.NetworkModeEntry.RestrictionReason;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Resolves a curated set of practical preferred network modes against modem capabilities.
 *
 * <p>The user-facing selector intentionally stays small and predictable. Hardware support is
 * still resolved at runtime, so unsupported choices are hidden while carrier, power and 2G
 * policy restrictions remain visible on supported choices.</p>
 */
public final class SupportedNetworkModeCatalog {

    private static final String TAG = "SupportedNetworkModes";
    private static final long ALL_RAF = -1L;

    public static final int GENERATION_2G = 1 << 0;
    public static final int GENERATION_3G = 1 << 1;
    public static final int GENERATION_4G = 1 << 2;
    public static final int GENERATION_5G = 1 << 3;

    private static final long RAF_GSM = TelephonyManager.NETWORK_TYPE_BITMASK_GSM
            | TelephonyManager.NETWORK_TYPE_BITMASK_GPRS
            | TelephonyManager.NETWORK_TYPE_BITMASK_EDGE;

    private static final long RAF_CDMA = TelephonyManager.NETWORK_TYPE_BITMASK_CDMA
            | TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT;

    private static final long RAF_EVDO = TelephonyManager.NETWORK_TYPE_BITMASK_EHRPD
            | TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_0
            | TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_A
            | TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_B;

    private static final long RAF_WCDMA = TelephonyManager.NETWORK_TYPE_BITMASK_UMTS
            | TelephonyManager.NETWORK_TYPE_BITMASK_HSDPA
            | TelephonyManager.NETWORK_TYPE_BITMASK_HSUPA
            | TelephonyManager.NETWORK_TYPE_BITMASK_HSPA
            | TelephonyManager.NETWORK_TYPE_BITMASK_HSPAP;

    private static final long RAF_TD_SCDMA = TelephonyManager.NETWORK_TYPE_BITMASK_TD_SCDMA;
    private static final long RAF_LTE = TelephonyManager.NETWORK_TYPE_BITMASK_LTE
            | TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA;
    private static final long RAF_NR = TelephonyManager.NETWORK_TYPE_BITMASK_NR;

    private static final long RAF_2G = RAF_GSM | RAF_CDMA;
    private static final long RAF_3G = RAF_WCDMA | RAF_TD_SCDMA | RAF_EVDO;
    private static final long RAF_4G = RAF_LTE;
    private static final long RAF_5G = RAF_NR;
    private static final long STANDARD_MODE_RAF =
            RAF_GSM | RAF_CDMA | RAF_EVDO | RAF_WCDMA | RAF_TD_SCDMA | RAF_LTE | RAF_NR;

    // Keep the normal selector intentionally small. These cover the useful GSM/WCDMA/LTE/NR
    // fallback combinations without exposing every framework RAF alias or legacy radio family.
    private static final int[] USER_VISIBLE_NETWORK_MODES = {
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

    private SupportedNetworkModeCatalog() {}

    /**
     * Returns the curated user-facing network modes that are compatible with the modem.
     *
     * <p>The list is deliberately explicit so the UI remains stable across devices and framework
     * revisions. Runtime capability checks still hide modes the current modem cannot support.</p>
     */
    public static final class Resolution {
        private final List<NetworkModeEntry> mEntries;
        private final NetworkModeCapabilitySnapshot mCapabilities;

        private Resolution(
                List<NetworkModeEntry> entries,
                NetworkModeCapabilitySnapshot capabilities) {
            mEntries = new ArrayList<>(entries);
            mCapabilities = capabilities;
        }

        public List<NetworkModeEntry> getEntries() {
            return new ArrayList<>(mEntries);
        }

        public NetworkModeCapabilitySnapshot getCapabilities() {
            return mCapabilities;
        }
    }

    public static List<NetworkModeEntry> getSupportedNetworkModes(
            Context context, TelephonyManager telephonyManager) {
        return resolve(context, telephonyManager).getEntries();
    }

    /**
     * Resolves the complete selector from one best-effort capability snapshot.
     *
     * <p>All runtime policy reasons are read once per refresh. The framework does not expose an
     * atomic multi-reason read, but this avoids repeated binder calls for every row and minimizes
     * cross-row state skew while telephony is changing.</p>
     */
    public static Resolution resolve(Context context, TelephonyManager telephonyManager) {
        final List<NetworkModeEntry> result = new ArrayList<>();
        final NetworkModeCapabilitySnapshot capabilities =
                NetworkModeCapabilitySnapshot.capture(context, telephonyManager);
        if (context == null || telephonyManager == null
                || !capabilities.hasKnownHardwareCapabilities()
                || !capabilities.isAllowedNetworkTypesBitmaskSupported()) {
            return new Resolution(result, capabilities);
        }

        final long supportedRaf = capabilities.getSupportedRaf();
        final int currentMode = capabilities.getUserNetworkMode();
        final int effectiveMode = capabilities.getEffectiveNetworkMode();

        for (int networkMode : USER_VISIBLE_NETWORK_MODES) {
            final long raf = getRafForNetworkMode(networkMode);
            if (raf == 0 || !isModeCompatibleWithHardware(raf, supportedRaf)) {
                continue;
            }

            NetworkModeEntry entry = buildEntry(
                    context,
                    capabilities,
                    networkMode,
                    raf,
                    findResourceModeName(context, networkMode),
                    true,
                    networkMode == currentMode,
                    networkMode == effectiveMode);

            // Preserve useful framework aliases as technical detail without turning them into
            // additional user-facing choices.
            final List<String> aliases = findResourceModeAliases(context, raf);
            if (aliases.size() > 1) {
                entry = entry.withTechnicalLabel(
                        context.getString(
                                R.string.network_mode_technical_aliases,
                                entry.getTechnicalLabel(),
                                join(aliases, context.getString(
                                        R.string.network_mode_alias_separator))));
            }
            result.add(entry);
        }

        result.sort(SupportedNetworkModeCatalog::compareEntries);
        return new Resolution(result, capabilities);
    }

    /**
     * Describes an exact mode reported by the telephony stack even when it is absent from the
     * standard resource catalog. This is used to avoid displaying a false nearest-match state.
     */
    public static NetworkModeEntry describeNetworkMode(
            Context context, TelephonyManager telephonyManager, int networkMode) {
        if (context == null || telephonyManager == null) {
            return null;
        }
        return describeNetworkMode(
                context,
                NetworkModeCapabilitySnapshot.capture(context, telephonyManager),
                networkMode);
    }

    public static NetworkModeEntry describeNetworkMode(
            Context context,
            NetworkModeCapabilitySnapshot capabilities,
            int networkMode) {
        if (context == null || capabilities == null) {
            return null;
        }

        final long raf = getRafForNetworkMode(networkMode);
        final long supportedRaf = capabilities.getSupportedRaf();
        final boolean hardwareSupported =
                raf != 0 && (supportedRaf == 0 || isModeCompatibleWithHardware(raf, supportedRaf));
        final String sourceName = findResourceModeName(context, networkMode);

        return buildEntry(
                context,
                capabilities,
                networkMode,
                raf,
                sourceName,
                hardwareSupported,
                networkMode == capabilities.getUserNetworkMode(),
                networkMode == capabilities.getEffectiveNetworkMode());
    }

    public static int getCurrentNetworkMode(TelephonyManager telephonyManager) {
        if (telephonyManager == null) {
            return NetworkModes.NETWORK_MODE_UNKNOWN;
        }
        try {
            final long userRaf = telephonyManager.getAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
            if (userRaf < 0) {
                return NetworkModes.NETWORK_MODE_UNKNOWN;
            }
            return NetworkModes.getStandardNetworkModeFromRaf(userRaf);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read current user network mode", e);
            return NetworkModes.NETWORK_MODE_UNKNOWN;
        }
    }

    /** Returns the 2G/3G/4G/5G mask represented by a RIL network mode. */
    public static int getGenerationMaskForNetworkMode(int networkMode) {
        return getGenerationMaskFromRaf(getRafForNetworkMode(networkMode));
    }

    /** Returns a localized generation label for an arbitrary RAF intersection. */
    public static String getBasicLabelForRaf(Context context, long raf) {
        if (context == null || raf == 0) {
            return null;
        }
        return getBasicLabel(
                context,
                getGenerationMaskFromRaf(raf),
                NetworkModes.NETWORK_MODE_UNKNOWN);
    }

    /** Returns a technical radio-family label for an arbitrary RAF intersection. */
    public static String getTechnicalLabelForRaf(Context context, long raf) {
        if (context == null || raf == 0) {
            return null;
        }
        return getTechnicalLabel(context, getRadioFamilies(raf));
    }

    private static NetworkModeEntry buildEntry(
            Context context,
            NetworkModeCapabilitySnapshot capabilities,
            int networkMode,
            long raf,
            String sourceName,
            boolean hardwareSupported,
            boolean current,
            boolean effective) {
        final int generationMask = getGenerationMaskFromRaf(raf);
        final EnumSet<RadioFamily> families = getRadioFamilies(raf);
        final EnumSet<RestrictionReason> restrictions =
                getRestrictionReasons(capabilities, raf);

        String technicalLabel = getTechnicalLabel(context, families);
        if (technicalLabel.isEmpty()) {
            technicalLabel = sourceName != null
                    ? sourceName
                    : context.getString(R.string.network_mode_unknown_mode, networkMode);
        }

        final boolean selectable =
                hardwareSupported
                        && capabilities.isAllowedNetworkTypesBitmaskSupported()
                        && !restrictions.contains(RestrictionReason.TWO_G_DISABLED)
                        && !restrictions.contains(RestrictionReason.ADMIN);

        return new NetworkModeEntry(
                networkMode,
                raf,
                generationMask,
                families,
                getBasicLabel(context, generationMask, networkMode),
                technicalLabel,
                hardwareSupported,
                restrictions.isEmpty(),
                current,
                effective,
                selectable,
                restrictions);
    }

    private static int compareEntries(NetworkModeEntry left, NetworkModeEntry right) {
        int comparison = Boolean.compare(
                left.isLegacyRadioFamilyMode(),
                right.isLegacyRadioFamilyMode());
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(
                getHighestGeneration(right.getGenerationMask()),
                getHighestGeneration(left.getGenerationMask()));
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(
                Integer.bitCount(left.getGenerationMask()),
                Integer.bitCount(right.getGenerationMask()));
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(
                right.getGenerationMask(),
                left.getGenerationMask());
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(
                left.getRadioFamilies().size(),
                right.getRadioFamilies().size());
        if (comparison != 0) {
            return comparison;
        }

        return Integer.compare(left.getNetworkMode(), right.getNetworkMode());
    }

    private static EnumSet<RestrictionReason> getRestrictionReasons(
            NetworkModeCapabilitySnapshot capabilities, long raf) {
        final EnumSet<RestrictionReason> restrictions =
                EnumSet.noneOf(RestrictionReason.class);

        if (!isModeAllowedByRaf(raf, capabilities.getCarrierRaf())) {
            restrictions.add(RestrictionReason.CARRIER);
        }
        if (!isModeAllowedByRaf(raf, capabilities.getPowerRaf())) {
            restrictions.add(RestrictionReason.POWER);
        }
        if (!isModeAllowedByRaf(raf, capabilities.getTestRaf())) {
            restrictions.add(RestrictionReason.TEST);
        }

        if ((raf & RAF_2G) != 0) {
            if (!isModeAllowedByRaf(raf & RAF_2G, capabilities.getEnable2gRaf())) {
                restrictions.add(RestrictionReason.TWO_G_DISABLED);
            }
            if (capabilities.isAdmin2gDisabled()) {
                restrictions.add(RestrictionReason.ADMIN);
            }
        }

        return restrictions;
    }

    private static boolean isModeCompatibleWithHardware(long modeRaf, long supportedRaf) {
        return hasRequiredFamily(modeRaf, supportedRaf, RAF_NR)
                && hasRequiredFamily(modeRaf, supportedRaf, RAF_LTE)
                && hasRequiredFamily(modeRaf, supportedRaf, RAF_TD_SCDMA)
                && hasRequiredFamily(modeRaf, supportedRaf, RAF_WCDMA)
                && hasRequiredFamily(modeRaf, supportedRaf, RAF_EVDO)
                && hasRequiredFamily(modeRaf, supportedRaf, RAF_CDMA)
                && hasRequiredFamily(modeRaf, supportedRaf, RAF_GSM);
    }

    private static boolean isModeAllowedByRaf(long modeRaf, long allowedRaf) {
        if (allowedRaf == ALL_RAF) {
            return true;
        }
        return hasRequiredFamily(modeRaf, allowedRaf, RAF_NR)
                && hasRequiredFamily(modeRaf, allowedRaf, RAF_LTE)
                && hasRequiredFamily(modeRaf, allowedRaf, RAF_TD_SCDMA)
                && hasRequiredFamily(modeRaf, allowedRaf, RAF_WCDMA)
                && hasRequiredFamily(modeRaf, allowedRaf, RAF_EVDO)
                && hasRequiredFamily(modeRaf, allowedRaf, RAF_CDMA)
                && hasRequiredFamily(modeRaf, allowedRaf, RAF_GSM);
    }

    private static boolean hasRequiredFamily(
            long modeRaf, long availableRaf, long familyRaf) {
        final long required = modeRaf & familyRaf;
        return required == 0 || (required & availableRaf) != 0;
    }

    private static EnumSet<RadioFamily> getRadioFamilies(long raf) {
        final EnumSet<RadioFamily> families = EnumSet.noneOf(RadioFamily.class);
        if ((raf & RAF_NR) != 0) {
            families.add(RadioFamily.NR);
        }
        if ((raf & RAF_LTE) != 0) {
            families.add(RadioFamily.LTE);
        }
        if ((raf & RAF_TD_SCDMA) != 0) {
            families.add(RadioFamily.TD_SCDMA);
        }
        if ((raf & RAF_WCDMA) != 0) {
            families.add(RadioFamily.WCDMA);
        }
        if ((raf & RAF_EVDO) != 0) {
            families.add(RadioFamily.EVDO);
        }
        if ((raf & RAF_CDMA) != 0) {
            families.add(RadioFamily.CDMA);
        }
        if ((raf & RAF_GSM) != 0) {
            families.add(RadioFamily.GSM);
        }
        return families;
    }

    private static int getGenerationMaskFromRaf(long raf) {
        int generationMask = 0;
        if ((raf & RAF_2G) != 0) {
            generationMask |= GENERATION_2G;
        }
        if ((raf & RAF_3G) != 0) {
            generationMask |= GENERATION_3G;
        }
        if ((raf & RAF_4G) != 0) {
            generationMask |= GENERATION_4G;
        }
        if ((raf & RAF_5G) != 0) {
            generationMask |= GENERATION_5G;
        }
        return generationMask;
    }

    private static String getBasicLabel(Context context, int generationMask, int networkMode) {
        if (generationMask == 0) {
            return context.getString(R.string.network_mode_unknown_mode, networkMode);
        }

        final List<String> generations = new ArrayList<>();
        if ((generationMask & GENERATION_5G) != 0) {
            generations.add(context.getString(R.string.network_mode_generation_5g));
        }
        if ((generationMask & GENERATION_4G) != 0) {
            generations.add(context.getString(R.string.network_mode_generation_4g));
        }
        if ((generationMask & GENERATION_3G) != 0) {
            generations.add(context.getString(R.string.network_mode_generation_3g));
        }
        if ((generationMask & GENERATION_2G) != 0) {
            generations.add(context.getString(R.string.network_mode_generation_2g));
        }
        return join(generations, context.getString(R.string.network_mode_generation_separator));
    }

    private static String getTechnicalLabel(Context context, EnumSet<RadioFamily> families) {
        final List<String> labels = new ArrayList<>();
        for (RadioFamily family : RadioFamily.values()) {
            if (!families.contains(family)) {
                continue;
            }
            labels.add(getRadioFamilyLabel(context, family));
        }
        return join(labels, context.getString(R.string.network_mode_radio_separator));
    }

    private static String getRadioFamilyLabel(Context context, RadioFamily family) {
        switch (family) {
            case NR:
                return context.getString(R.string.network_mode_radio_nr);
            case LTE:
                return context.getString(R.string.network_mode_radio_lte);
            case TD_SCDMA:
                return context.getString(R.string.network_mode_radio_tdscdma);
            case WCDMA:
                return context.getString(R.string.network_mode_radio_wcdma);
            case EVDO:
                return context.getString(R.string.network_mode_radio_evdo);
            case CDMA:
                return context.getString(R.string.network_mode_radio_cdma);
            case GSM:
            default:
                return context.getString(R.string.network_mode_radio_gsm);
        }
    }

    private static int getHighestGeneration(int generationMask) {
        if ((generationMask & GENERATION_5G) != 0) {
            return 5;
        }
        if ((generationMask & GENERATION_4G) != 0) {
            return 4;
        }
        if ((generationMask & GENERATION_3G) != 0) {
            return 3;
        }
        if ((generationMask & GENERATION_2G) != 0) {
            return 2;
        }
        return 0;
    }

    private static long getRafForNetworkMode(int networkMode) {
        return Integer.toUnsignedLong(RadioAccessFamily.getRafFromNetworkType(networkMode));
    }

    private static List<String> findResourceModeAliases(Context context, long raf) {
        final String[] values =
                context.getResources().getStringArray(R.array.preferred_network_mode_values);
        final String[] names =
                context.getResources().getStringArray(R.array.preferred_network_mode_choices);
        final List<String> aliases = new ArrayList<>();

        for (int index = 0; index < values.length && index < names.length; index++) {
            try {
                final int mode = Integer.parseInt(values[index]);
                if (getRafForNetworkMode(mode) == raf
                        && !names[index].isEmpty()
                        && !aliases.contains(names[index])) {
                    aliases.add(names[index]);
                }
            } catch (NumberFormatException ignored) {
                // A malformed resource entry must not affect the curated selector.
            }
        }
        return aliases;
    }

    private static String findResourceModeName(Context context, int networkMode) {
        final String[] values =
                context.getResources().getStringArray(R.array.preferred_network_mode_values);
        final String[] names =
                context.getResources().getStringArray(R.array.preferred_network_mode_choices);
        for (int index = 0; index < values.length && index < names.length; index++) {
            try {
                if (Integer.parseInt(values[index]) == networkMode) {
                    return names[index];
                }
            } catch (NumberFormatException ignored) {
                // Keep scanning; malformed overlay entries must not break the entire selector.
            }
        }
        return null;
    }

    private static String join(List<String> items, String separator) {
        final StringBuilder builder = new StringBuilder();
        for (String item : items) {
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(item);
        }
        return builder.toString();
    }
}
