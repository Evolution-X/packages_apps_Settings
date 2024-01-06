/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.fragment;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.widget.EmptyTextSettings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.evolution.settings.widget.AppListPreference;

public abstract class PerAppConfigFragment extends EmptyTextSettings {

    private Context mContext;
    private PackageManager mPackageManager;

    /**
     * Comparator by label, if null or empty then packageName.
     */
    static class AppComparator implements Comparator<Pair<String, String>> {

        private final Collator mCollator = Collator.getInstance();

        public final int compare(Pair<String, String> a,
                Pair<String, String> b) {
            String sa = a.first;
            if (TextUtils.isEmpty(sa)) sa = a.second;
            String sb = b.first;
            if (TextUtils.isEmpty(sb)) sb = b.second;
            return mCollator.compare(sa, sb);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mContext = getActivity();
        mPackageManager = mContext.getPackageManager();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Clear the prefs
        final PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();

        final ArrayList<Pair<String, String>> apps = collectApps();

        // Rebuild the list of prefs
        final Context prefContext = getPrefContext();
        for (final Pair<String, String> appData : apps) {
            final ListPreference pref = new AppListPreference(prefContext);
            pref.setIcon(getIcon(appData.second));
            pref.setTitle(appData.first);
            pref.setDialogTitle(appData.first);

            int size = getEntries().size();

            CharSequence[] entries = new CharSequence[size];
            CharSequence[] values = new CharSequence[size];
            for (int i = 0; i < size; ++i) {
                entries[i] = getEntries().get(i);
                values[i] = String.valueOf(getValues().get(i));
            }
            pref.setEntries(entries);
            pref.setEntryValues(values);

            final String value = String.valueOf(getCurrentValue(appData.second));
            CharSequence summary = "-1";
            for (int i = 0; i < size; ++i) {
                if (values[i].equals(value)) {
                    summary = entries[i];
                    break;
                }
            }
            pref.setValue(value);
            pref.setSummary(summary);

            pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final String newValueStr = String.valueOf(newValue);
                    onValueChanged(appData.second, Integer.parseInt(newValueStr));
                    CharSequence[] entries = pref.getEntries();
                    CharSequence[] values = pref.getEntryValues();
                    CharSequence summary = entries[0];
                    for (int i = 0; i < entries.length; ++i) {
                        if (values[i].equals(newValueStr)) {
                            summary = entries[i];
                            break;
                        }
                    }
                    pref.setSummary(summary);
                    return true;
                }
            });
            screen.addPreference(pref);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setEmptyText(R.string.per_app_config_empty_text);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    /**
     * @return the sorted list of pair<label, packageName> of all applications for current user,
     * with extra system applications defined in R.array.config_perAppConfAllowedSystemApps.
     */
    private ArrayList<Pair<String, String>> collectApps() {
        final ArrayList<Pair<String, String>> apps = new ArrayList<>();
        final List<PackageInfo> installedPackages =
                mPackageManager.getInstalledPackages(0);
        for (PackageInfo pi : installedPackages) {
            if ((pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }
            apps.add(new Pair<>(
                    pi.applicationInfo.loadLabel(mPackageManager).toString(),
                    pi.packageName));
        }
        final String[] systemApps = mContext.getResources().getStringArray(
                R.array.config_perAppConfAllowedSystemApps);
        for (String app : systemApps) {
            try {
                final PackageInfo pi = mPackageManager.getPackageInfo(app, 0);
                pi.applicationInfo.loadLabel(mPackageManager).toString();
                apps.add(new Pair<>(
                        pi.applicationInfo.loadLabel(mPackageManager).toString(), app));
            } catch (NameNotFoundException e) {
            }
        }
        Collections.sort(apps, new AppComparator());
        return apps;
    }

    private Drawable getIcon(String packageName) {
        Drawable loadIcon = null;
        if (packageName != null) {
            try {
                loadIcon = mPackageManager.getApplicationIcon(packageName);
            } catch (NameNotFoundException e) {
            }
        }
        return loadIcon != null ? loadIcon : mPackageManager.getDefaultActivityIcon();
    }

    protected abstract List<String> getEntries();

    protected abstract List<Integer> getValues();

    protected abstract int getCurrentValue(String packageName);

    protected abstract void onValueChanged(String packageName, int value);
}
