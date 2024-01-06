/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.display.refreshrate;

import android.os.Bundle;

import com.android.internal.util.evolution.DisplayRefreshRateHelper;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;

import org.evolution.display.RefreshRateManager;

import org.evolution.settings.fragment.PerAppConfigFragment;

public class PerAppRefreshRateFragment extends PerAppConfigFragment {

    private DisplayRefreshRateHelper mHelper;
    private RefreshRateManager mRefreshRateManager;

    private List<String> mEntries;
    private List<Integer> mValues;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHelper = DisplayRefreshRateHelper.getInstance(getActivity());
        mRefreshRateManager = getActivity().getSystemService(RefreshRateManager.class);

        mEntries = new ArrayList<>();
        mEntries.add(getString(R.string.per_app_refresh_rate_default));
        mValues = new ArrayList<>();
        mValues.add(-1);

        for (int refreshRate : mHelper.getSupportedRefreshRateList()) {
            mEntries.add(String.valueOf(refreshRate) + " Hz");
            mValues.add(refreshRate);
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.per_app_refresh_rate_config;
    }

    @Override
    protected List<String> getEntries() {
        return mEntries;
    }

    @Override
    protected List<Integer> getValues() {
        return mValues;
    }

    @Override
    protected int getCurrentValue(String packageName) {
        return mRefreshRateManager.getRefreshRateForPackage(packageName);
    }

    @Override
    protected void onValueChanged(String packageName, int value) {
        if (value > 0) {
            mRefreshRateManager.setRefreshRateForPackage(packageName, value);
        } else {
            mRefreshRateManager.unsetRefreshRateForPackage(packageName);
        }
    }
}
