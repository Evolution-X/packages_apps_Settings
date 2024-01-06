/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.fragment;

import android.content.Context;
import android.util.ArrayMap;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.List;

import org.evolution.settings.widget.AppSwitchPreference;

public abstract class PerAppSwitchConfigFragment extends BasePerAppConfigFragment {

    private final ArrayMap<String, Boolean> mPkgCheckState = new ArrayMap<>();

    @Override
    protected Preference createAppPreference(Context prefContext, AppData appData) {
        final SwitchPreferenceCompat pref = new AppSwitchPreference(prefContext);
        pref.setIcon(getIcon(appData.packageName));
        pref.setTitle(appData.label);
        pref.setSummary(appData.packageName);

        final boolean checked = isChecked(appData.packageName, appData.uid);
        pref.setChecked(checked);
        mPkgCheckState.put(appData.packageName, checked);

        pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final boolean ret = onSetChecked((SwitchPreferenceCompat) preference,
                        appData.packageName, appData.uid, (Boolean) newValue);
                if (ret) {
                    mPkgCheckState.put(appData.packageName, (Boolean) newValue);
                    onCheckedListUpdated(generateCheckedPkgList());
                }
                return ret;
            }
        });

        return pref;
    }

    private List<String> generateCheckedPkgList() {
        final List<String> ret = new ArrayList<>();
        for (String pkg : mPkgCheckState.keySet()) {
            if (mPkgCheckState.get(pkg)) {
                ret.add(pkg);
            }
        }
        return ret;
    }

    protected abstract boolean isChecked(String packageName, int uid);

    protected abstract boolean onSetChecked(SwitchPreferenceCompat pref, String packageName, int uid, boolean checked);

    protected abstract void onCheckedListUpdated(List<String> pkgList);
}
