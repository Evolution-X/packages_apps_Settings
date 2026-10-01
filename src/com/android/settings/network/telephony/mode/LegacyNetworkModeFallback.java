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
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservative fallback for devices that do not expose a usable supported RAF.
 *
 * <p>Rather than guessing capabilities from CarrierConfig, expose the exact USER mode returned by
 * telephony. This keeps the Settings UI truthful while still allowing the current state to be
 * inspected and re-selected.</p>
 */
public final class LegacyNetworkModeFallback {

    private LegacyNetworkModeFallback() {}

    public static List<NetworkModeEntry> build(
            Context context, TelephonyManager telephonyManager) {
        return build(
                context,
                telephonyManager,
                NetworkModeCapabilitySnapshot.capture(context, telephonyManager));
    }

    public static List<NetworkModeEntry> build(
            Context context,
            TelephonyManager telephonyManager,
            NetworkModeCapabilitySnapshot capabilities) {
        final List<NetworkModeEntry> result = new ArrayList<>();
        if (capabilities == null) {
            return result;
        }

        final int currentMode = capabilities.getUserNetworkMode();
        if (currentMode == NetworkModes.NETWORK_MODE_UNKNOWN) {
            return result;
        }

        final NetworkModeEntry current =
                SupportedNetworkModeCatalog.describeNetworkMode(
                        context, capabilities, currentMode);
        if (current != null) {
            result.add(current.withCurrent(true));
        }
        return result;
    }
}
