/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.settings.development.bluetooth;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RadioGroup;

import com.android.settings.R;
import android.util.Log;

/**
 * Dialog preference to set the Bluetooth A2DP config of LHDC quality
 */
public class BluetoothLHDCAudioLosslessRawDialogPreference extends BaseBluetoothDialogPreference implements
        RadioGroup.OnCheckedChangeListener {

    private static final String TAG = "BtLhdcLLESSRawPref";

    public BluetoothLHDCAudioLosslessRawDialogPreference(Context context) {
        super(context);
        initialize(context);
    }

    public BluetoothLHDCAudioLosslessRawDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public BluetoothLHDCAudioLosslessRawDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    public BluetoothLHDCAudioLosslessRawDialogPreference(Context context, AttributeSet attrs, int defStyleAttr,
                                            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize(context);
    }

    @Override
    protected int getRadioButtonGroupId() {
        return R.id.bluetooth_lhdc_audio_lossless_raw_radio_group;
    }

    @Override
    protected int getDefaultIndex() {
        return 0; // OFF
    }

    private void initialize(Context context) {
        mRadioButtonIds.add(R.id.bluetooth_lhdc_lossless_raw_off);
        mRadioButtonIds.add(R.id.bluetooth_lhdc_lossless_raw_on);
        String[] stringArray = context.getResources().getStringArray(
                com.android.settingslib.R.array.bluetooth_enable_a2dp_codec_lhdc_lossless_raw_titles);
        for (int i = 0; i < stringArray.length; i++) {
            mRadioButtonStrings.add(stringArray[i]);
        }
        stringArray = context.getResources().getStringArray(
                com.android.settingslib.R.array.bluetooth_enable_a2dp_codec_lhdc_lossless_raw_summaries);
        for (int i = 0; i < stringArray.length; i++) {
            mSummaryStrings.add(stringArray[i]);
        }
    }
}
