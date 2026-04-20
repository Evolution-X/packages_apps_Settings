/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo;

import java.util.Locale;

public final class DeviceNameUtils {

    private DeviceNameUtils() {
        // Utility class
    }

    public static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    public static String prefixIfNeeded(String oem, String name) {
        String cleanOem = sanitize(oem);
        String cleanName = sanitize(name);

        if (cleanName.isEmpty()) return cleanOem;
        if (cleanOem.isEmpty()) return cleanName;

        String oemLower = cleanOem.toLowerCase(Locale.ROOT);
        String nameLower = cleanName.toLowerCase(Locale.ROOT);

        if (nameLower.equals(oemLower)
                || nameLower.startsWith(oemLower + " ")
                || nameLower.startsWith(oemLower + "-")
                || nameLower.startsWith(oemLower + "_")) {
            return cleanName;
        }

        return cleanOem + " " + cleanName;
    }
}
