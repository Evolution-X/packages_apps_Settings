/*
 * Copyright (C) 2019-2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.gestures;

import static android.provider.Settings.System.DOZE_TRIGGER_DOUBLETAP;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.settings.R;

public class DoubleTapAmbientPreferenceController extends GesturePreferenceController {

    private final int ON = 1;
    private final int OFF = 0;

    private static final String PREF_KEY_VIDEO = "gesture_double_tap_screen_video";

    public DoubleTapAmbientPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(R.bool.config_supports_double_tap_ambient)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean isSliceable() {
        return TextUtils.equals(getPreferenceKey(), "doze_trigger_doubletap");
    }

    @Override
    protected String getVideoPrefKey() {
        return PREF_KEY_VIDEO;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.System.putInt(mContext.getContentResolver(), DOZE_TRIGGER_DOUBLETAP,
                isChecked ? ON : OFF);
    }

    @Override
    public boolean isChecked() {
        return Settings.System.getInt(mContext.getContentResolver(), DOZE_TRIGGER_DOUBLETAP, 0) != 0;
    }
}
