/*
 * Copyright (C) 2019-2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class EvolutionXBuildMaintainerPreferenceController extends BasePreferenceController {

    private static final String TAG = "EvolutionXBuildMaintainerCtrl";

    private String mDeviceMaintainer;

    public EvolutionXBuildMaintainerPreferenceController(Context context, String key) {
        super(context, key);
        mDeviceMaintainer = mContext.getResources().getString(R.string.build_maintainer_summary);
    }

    @Override
    public int getAvailabilityStatus() {
        if (mDeviceMaintainer.equalsIgnoreCase("UNKNOWN")) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return mDeviceMaintainer;
    }
}
