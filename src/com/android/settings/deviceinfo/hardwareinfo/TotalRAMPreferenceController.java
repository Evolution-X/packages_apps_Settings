/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.deviceinfo.hardwareinfo;

import android.app.ActivityManager;
import android.content.Context;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.slices.Sliceable;

import java.util.Locale;

public class TotalRAMPreferenceController extends BasePreferenceController {

    public TotalRAMPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(R.bool.config_show_device_model)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return true;
    }

    @Override
    public boolean isSliceable() {
        return true;
    }

    @Override
    public CharSequence getSummary() {
        return getTotalRam(mContext);
    }

    /** Total RAM rounded to the nearest marketed size; empty when unavailable. */
    public static String getTotalRam(Context context) {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        if (am == null) return "";
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long rounded = roundToTier(mi.totalMem / (1024 * 1024));
        if (rounded >= 1024) {
            float gb = rounded / 1024f;
            return (gb == (long) gb)
                    ? (long) gb + " GB"
                    : String.format(Locale.getDefault(), "%.1f GB", gb);
        }
        return rounded + " MB";
    }

    /**
     * MemoryInfo.totalMem is MemTotal from /proc/meminfo, which is the installed RAM minus what
     * the kernel, modem, DSP, camera and GPU carve out at boot. It is therefore always below the
     * marketed size, and on some SoCs by well over 1 GB. Round up to the next marketed tier
     * rather than to the nearest one, otherwise an 8 GB device reporting ~6.9 GB is shown as 6 GB.
     */
    private static long roundToTier(long mb) {
        for (long tier : MARKETED_TIERS_MB) {
            if (mb <= tier) return tier;
        }
        // Above the largest known tier: round up to the next whole GB.
        return ((mb + 1023) / 1024) * 1024;
    }

    private static final long[] MARKETED_TIERS_MB = {
        512, 1024, 2048, 3072, 4096, 6144, 8192, 10240, 12288, 16384, 18432, 24576, 32768
    };
}
