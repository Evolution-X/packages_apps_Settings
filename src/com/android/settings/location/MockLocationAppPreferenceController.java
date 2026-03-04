/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settings.location;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import android.preference.PreferenceManager.OnActivityResultListener;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.development.AppPicker;
import com.android.settings.development.DevelopmentAppPicker;
import com.android.settings.development.Flags;
import com.android.settingslib.core.AbstractPreferenceController;

import java.util.List;

/** Controller for choosing the mock location app in Location settings. */
public class MockLocationAppPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, OnActivityResultListener,
        Preference.OnPreferenceClickListener {

    private static final String MOCK_LOCATION_APP_KEY = "mock_location_app";
    private static final int REQUEST_MOCK_LOCATION_APP = 1000;
    private static final int[] MOCK_LOCATION_APP_OPS = new int[]{AppOpsManager.OP_MOCK_LOCATION};

    private final LocationSettings mFragment;
    @Nullable private Preference mPreference;
    private final AppOpsManager mAppsOpsManager;
    private final PackageManager mPackageManager;

    public MockLocationAppPreferenceController(Context context, LocationSettings fragment) {
        super(context);
        mFragment = fragment;
        mAppsOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        mPackageManager = context.getPackageManager();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return MOCK_LOCATION_APP_KEY;
    }

    @Override
    public void updateState(Preference preference) {
        mPreference = preference;
        final String mockLocationApp = getCurrentMockLocationApp();
        if (!TextUtils.isEmpty(mockLocationApp)) {
            preference.setSummary(
                    mContext.getResources().getString(
                            com.android.settingslib.R.string.mock_location_app_set,
                            getAppLabel(mockLocationApp)));
        } else {
            preference.setSummary(
                    mContext.getResources().getString(
                            com.android.settingslib.R.string.mock_location_app_not_set));
        }
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final Preference preference = screen.findPreference(getPreferenceKey());
        if (preference != null) {
            preference.setOnPreferenceClickListener(this);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return false;
        }

        if (Flags.deprecateListActivity()) {
            final Bundle args = new Bundle();
            args.putString(DevelopmentAppPicker.EXTRA_REQUESTING_PERMISSION,
                    Manifest.permission.ACCESS_MOCK_LOCATION);
            final String debugApp = Settings.Global.getString(
                    mContext.getContentResolver(), Settings.Global.DEBUG_APP);
            args.putString(DevelopmentAppPicker.EXTRA_SELECTING_APP, debugApp);
            new SubSettingLauncher(mContext)
                    .setDestination(DevelopmentAppPicker.class.getName())
                    .setSourceMetricsCategory(SettingsEnums.LOCATION)
                    .setArguments(args)
                    .setTitleRes(com.android.settingslib.R.string.select_application)
                    .setResultListener(mFragment, REQUEST_MOCK_LOCATION_APP)
                    .launch();
        } else {
            final Intent intent = new Intent(mContext, AppPicker.class);
            intent.putExtra(AppPicker.EXTRA_REQUESTIING_PERMISSION,
                    Manifest.permission.ACCESS_MOCK_LOCATION);
            mFragment.startActivityForResult(intent, REQUEST_MOCK_LOCATION_APP);
        }
        return true;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_MOCK_LOCATION_APP || resultCode != Activity.RESULT_OK
                || data == null) {
            return false;
        }
        writeMockLocation(data.getAction());
        if (mPreference != null) {
            updateState(mPreference);
        }
        return true;
    }

    private void writeMockLocation(String mockLocationAppName) {
        removeAllMockLocations();
        if (!TextUtils.isEmpty(mockLocationAppName)) {
            try {
                final ApplicationInfo ai = mPackageManager.getApplicationInfo(
                        mockLocationAppName, PackageManager.MATCH_DISABLED_COMPONENTS);
                mAppsOpsManager.setMode(AppOpsManager.OP_MOCK_LOCATION, ai.uid,
                        mockLocationAppName, AppOpsManager.MODE_ALLOWED);
            } catch (PackageManager.NameNotFoundException e) {
                // Ignore.
            }
        }
    }

    private String getAppLabel(String mockLocationApp) {
        try {
            final ApplicationInfo ai = mPackageManager.getApplicationInfo(
                    mockLocationApp, PackageManager.MATCH_DISABLED_COMPONENTS);
            final CharSequence appLabel = mPackageManager.getApplicationLabel(ai);
            return appLabel != null ? appLabel.toString() : mockLocationApp;
        } catch (PackageManager.NameNotFoundException e) {
            return mockLocationApp;
        }
    }

    private void removeAllMockLocations() {
        final List<AppOpsManager.PackageOps> packageOps =
                mAppsOpsManager.getPackagesForOps(MOCK_LOCATION_APP_OPS);
        if (packageOps == null) {
            return;
        }
        for (AppOpsManager.PackageOps packageOp : packageOps) {
            if (packageOp.getOps().get(0).getMode() != AppOpsManager.MODE_ERRORED) {
                removeMockLocationForApp(packageOp.getPackageName());
            }
        }
    }

    private void removeMockLocationForApp(String appName) {
        try {
            final ApplicationInfo ai = mPackageManager.getApplicationInfo(
                    appName, PackageManager.MATCH_DISABLED_COMPONENTS);
            mAppsOpsManager.setMode(AppOpsManager.OP_MOCK_LOCATION, ai.uid,
                    appName, AppOpsManager.MODE_ERRORED);
        } catch (PackageManager.NameNotFoundException e) {
            // Ignore.
        }
    }

    @Nullable
    private String getCurrentMockLocationApp() {
        final List<AppOpsManager.PackageOps> packageOps =
                mAppsOpsManager.getPackagesForOps(MOCK_LOCATION_APP_OPS);
        if (packageOps != null) {
            for (AppOpsManager.PackageOps packageOp : packageOps) {
                if (packageOp.getOps().get(0).getMode() == AppOpsManager.MODE_ALLOWED) {
                    return packageOp.getPackageName();
                }
            }
        }
        return null;
    }
}
