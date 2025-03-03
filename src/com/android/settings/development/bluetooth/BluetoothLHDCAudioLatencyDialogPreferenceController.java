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

import android.bluetooth.BluetoothCodecConfig;
import android.content.Context;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.development.BluetoothA2dpConfigStore;
import com.android.settingslib.core.lifecycle.Lifecycle;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog preference controller to set the Bluetooth A2DP config of LHDC quality
 */
public class BluetoothLHDCAudioLatencyDialogPreferenceController extends
        AbstractBluetoothDialogPreferenceController {
    private static final String KEY = "bluetooth_enable_a2dp_codec_lhdc_latency";
    private static final String TAG = "BtLhdcLowLatnecyCtr";
    private static final int DEFAULT_MASK = 0xC000;
    private static final int DEFAULT_TAG = 0x8000;
    private static final int LHDC_LL_MODE = 0x1;
    private static final int MAX_INDEX_NUM = 1;

    public BluetoothLHDCAudioLatencyDialogPreferenceController(Context context, Lifecycle lifecycle,
                                                      BluetoothA2dpConfigStore store) {
        super(context, lifecycle, store);
    }

    @Override
    public String getPreferenceKey() {
        return KEY;
    }


    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        ((BaseBluetoothDialogPreference) mPreference).setCallback(this);
    }


    @Override
    protected void writeConfigurationValues(final int index) {
        synchronized (mBluetoothA2dpConfigStore) {
            long codecSpecific2Value = mBluetoothA2dpConfigStore.getCodecSpecific2Value();
            codecSpecific2Value &= ~ DEFAULT_MASK;
            codecSpecific2Value |= DEFAULT_TAG;
            if (index != 0) {
                codecSpecific2Value |= LHDC_LL_MODE;
            } else {
                codecSpecific2Value &= ~ LHDC_LL_MODE;
            }
            mBluetoothA2dpConfigStore.setCodecSpecific2Value(codecSpecific2Value);
        }
    }

    @Override
    protected int getCurrentIndexByConfig(BluetoothCodecConfig config) {
        if (config == null) {
            Log.e(TAG, "Unable to get current config index. Config is null.");
        }
        int index = 0;
        long codecSpecific2Value = config.getCodecSpecific2();
        int codecType = config.getCodecType();
        long featureTag = 0;
        if (codecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LHDCV3 ||
            codecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LHDCV5) {
            featureTag = DEFAULT_TAG;
        }
        index = convertCfgToBtnIndex(featureTag, codecSpecific2Value);

        // make a sync from current to storage while get
        synchronized (mBluetoothA2dpConfigStore) {
            mBluetoothA2dpConfigStore.setCodecSpecific2Value(codecSpecific2Value);
        }
        return index;
    }

    @Override
    public List<Integer> getSelectableIndex() {
        List<Integer> selectableIndex = new ArrayList<>();
        for (int i = 0; i <= MAX_INDEX_NUM; i++) {
            selectableIndex.add(i);
        }
        return selectableIndex;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        final BluetoothCodecConfig currentConfig = getCurrentCodecConfig();
        if (currentConfig != null &&
           (currentConfig.getCodecType() == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LHDCV3 ||
            currentConfig.getCodecType() == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LHDCV5)) {
            preference.setEnabled(true);
        } else {
            preference.setEnabled(false);
            //preference.setSummary("");
        }
    }

    @Override
    public void onHDAudioEnabled(boolean enabled) {
        mPreference.setEnabled(false);
    }

    @VisibleForTesting
    int convertCfgToBtnIndex(long tag, long index) {
        int ret = 0;
        long tmp = index & DEFAULT_MASK;
        if (tmp == tag) {
            if ((index & LHDC_LL_MODE) != 0) {
                ret = 1;
            } else {
                ret = 0;
            }
        } else {
            ret = getDefaultIndex();
        }
        return ret;
    }
}
