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

    private static long roundToTier(long mb) {
        long[] tiers = {1024, 2048, 4096, 6144, 8192, 12288, 16384, 24576};
        long best = mb, bestDelta = Long.MAX_VALUE;
        for (long t : tiers) {
            long d = Math.abs(mb - t);
            if (d < bestDelta) { bestDelta = d; best = t; }
        }
        return best;
    }
}
