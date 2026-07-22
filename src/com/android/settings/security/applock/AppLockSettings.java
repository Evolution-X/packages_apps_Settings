/*
 * Copyright (C) 2026 VoltageOS
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

package com.android.settings.security.applock;

import android.app.Activity;
import android.app.AppLockExtras;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.IconDrawableFactory;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.app.AppLockActivity;
import com.android.internal.content.PackageMonitor;
import com.android.internal.os.BackgroundThread;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.util.ArrayList;
import java.util.List;

public class AppLockSettings extends SettingsPreferenceFragment {

    private static final String KEY_AUTO_PROMPT = "app_lock_auto_biometric_prompt";
    private static final String KEY_RELOCK_BEHAVIOR = "app_lock_relock_behavior";
    private static final String KEY_GRACE_PERIOD = "app_lock_grace_period";
    private static final String KEY_CREDENTIAL = "app_lock_separate_credential";
    private static final String KEY_APPS_CATEGORY = "app_lock_apps_category";

    private static final String SYSTEM_PACKAGE_NAME = "android";
    private static final String CREDENTIAL_ACTIVITY =
            "com.android.internal.app.AppLockCredentialActivity";

    private static final int REQUEST_VERIFY_CREDENTIAL = 1;

    private SwitchPreferenceCompat mAutoPrompt;
    private ListPreference mRelockBehavior;
    private ListPreference mGracePeriod;
    private Preference mCredential;
    private PreferenceCategory mAppsCategory;
    private boolean mAuthenticated;
    private boolean mMonitoring;

    private final PackageMonitor mPackageMonitor = new PackageMonitor() {
        @Override
        public void onPackageAppLockEnabled(String packageName) {
            postRefreshApps();
        }

        @Override
        public void onPackageAppLockDisabled(String packageName) {
            postRefreshApps();
        }

        @Override
        public void onSomePackagesChanged() {
            postRefreshApps();
        }
    };

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.app_lock_settings);

        mAutoPrompt = findPreference(KEY_AUTO_PROMPT);
        mRelockBehavior = findPreference(KEY_RELOCK_BEHAVIOR);
        mGracePeriod = findPreference(KEY_GRACE_PERIOD);
        mCredential = findPreference(KEY_CREDENTIAL);
        mAppsCategory = findPreference(KEY_APPS_CATEGORY);

        mAutoPrompt.setOnPreferenceChangeListener((pref, value) -> {
            Settings.Secure.putInt(getContentResolver(),
                    AppLockExtras.SETTING_AUTO_BIOMETRIC_PROMPT,
                    Boolean.TRUE.equals(value) ? 1 : 0);
            return true;
        });
        mRelockBehavior.setOnPreferenceChangeListener((pref, value) -> {
            Settings.Secure.putInt(getContentResolver(),
                    AppLockExtras.SETTING_RELOCK_BEHAVIOR,
                    Integer.parseInt((String) value));
            updateListSummary(mRelockBehavior, (String) value);
            return true;
        });
        mGracePeriod.setOnPreferenceChangeListener((pref, value) -> {
            Settings.Secure.putLong(getContentResolver(),
                    AppLockExtras.SETTING_GRACE_PERIOD_MS,
                    Long.parseLong((String) value));
            updateListSummary(mGracePeriod, (String) value);
            return true;
        });
        mCredential.setOnPreferenceClickListener(pref -> {
            showCredentialDialog();
            return true;
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mAuthenticated) {
            getPreferenceScreen().setVisible(false);
            requireAuthentication();
            return;
        }
        startMonitoring();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMonitoring) {
            mPackageMonitor.unregister();
            mMonitoring = false;
        }
    }

    private void requireAuthentication() {
        final int type = Settings.Secure.getInt(getContentResolver(),
                AppLockExtras.SETTING_SEPARATE_CREDENTIAL_TYPE,
                AppLockExtras.CREDENTIAL_TYPE_NONE);
        if (type != AppLockExtras.CREDENTIAL_TYPE_NONE) {
            final Intent intent = new Intent(AppLockExtras.ACTION_VERIFY_CREDENTIAL);
            intent.setClassName(SYSTEM_PACKAGE_NAME, CREDENTIAL_ACTIVITY);
            startActivityForResult(intent, REQUEST_VERIFY_CREDENTIAL);
            return;
        }
        final BiometricPrompt prompt = new BiometricPrompt.Builder(requireContext())
                .setTitle(getString(R.string.app_lock_settings_title))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        prompt.authenticate(new CancellationSignal(), requireContext().getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        onAuthenticated();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        requireActivity().finish();
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VERIFY_CREDENTIAL) {
            return;
        }
        if (resultCode == Activity.RESULT_OK) {
            onAuthenticated();
        } else {
            requireActivity().finish();
        }
    }

    private void onAuthenticated() {
        mAuthenticated = true;
        getPreferenceScreen().setVisible(true);
        startMonitoring();
    }

    private void startMonitoring() {
        if (mMonitoring) {
            return;
        }
        mMonitoring = true;
        mPackageMonitor.register(requireContext(), BackgroundThread.getHandler().getLooper(),
                UserHandle.of(UserHandle.myUserId()), false);
        refreshState();
        refreshApps();
    }

    private void refreshState() {
        mAutoPrompt.setChecked(Settings.Secure.getInt(getContentResolver(),
                AppLockExtras.SETTING_AUTO_BIOMETRIC_PROMPT,
                AppLockExtras.DEFAULT_AUTO_BIOMETRIC_PROMPT) != 0);

        final String relock = String.valueOf(Settings.Secure.getInt(getContentResolver(),
                AppLockExtras.SETTING_RELOCK_BEHAVIOR,
                AppLockExtras.DEFAULT_RELOCK_BEHAVIOR));
        mRelockBehavior.setValue(relock);
        updateListSummary(mRelockBehavior, relock);

        final String grace = String.valueOf(Settings.Secure.getLong(getContentResolver(),
                AppLockExtras.SETTING_GRACE_PERIOD_MS,
                AppLockExtras.DEFAULT_GRACE_PERIOD_MS));
        mGracePeriod.setValue(grace);
        updateListSummary(mGracePeriod, grace);

        updateCredentialSummary();
    }

    private void updateListSummary(ListPreference preference, String value) {
        final int index = preference.findIndexOfValue(value);
        preference.setSummary(index >= 0 ? preference.getEntries()[index] : null);
    }

    private int getCredentialType() {
        return Settings.Secure.getInt(getContentResolver(),
                AppLockExtras.SETTING_SEPARATE_CREDENTIAL_TYPE,
                AppLockExtras.CREDENTIAL_TYPE_NONE);
    }

    private void updateCredentialSummary() {
        final int type = getCredentialType();
        final int resId;
        if (type == AppLockExtras.CREDENTIAL_TYPE_PIN) {
            resId = R.string.app_lock_separate_credential_pin;
        } else if (type == AppLockExtras.CREDENTIAL_TYPE_PATTERN) {
            resId = R.string.app_lock_separate_credential_pattern;
        } else {
            resId = R.string.app_lock_separate_credential_none;
        }
        mCredential.setSummary(resId);
    }

    private void showCredentialDialog() {
        final List<CharSequence> items = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        items.add(getString(R.string.app_lock_credential_set_pin));
        actions.add(() -> launchCredentialActivity(AppLockExtras.ACTION_SET_CREDENTIAL,
                AppLockExtras.CREDENTIAL_TYPE_PIN));
        items.add(getString(R.string.app_lock_credential_set_pattern));
        actions.add(() -> launchCredentialActivity(AppLockExtras.ACTION_SET_CREDENTIAL,
                AppLockExtras.CREDENTIAL_TYPE_PATTERN));
        if (getCredentialType() != AppLockExtras.CREDENTIAL_TYPE_NONE) {
            items.add(getString(R.string.app_lock_credential_remove));
            actions.add(() -> launchCredentialActivity(AppLockExtras.ACTION_CLEAR_CREDENTIAL,
                    AppLockExtras.CREDENTIAL_TYPE_NONE));
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.app_lock_separate_credential_title)
                .setItems(items.toArray(new CharSequence[0]),
                        (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void launchCredentialActivity(String action, int type) {
        final Intent intent = new Intent(action)
                .setClassName(SYSTEM_PACKAGE_NAME, CREDENTIAL_ACTIVITY)
                .putExtra(AppLockExtras.EXTRA_CREDENTIAL_TYPE, type);
        startActivity(intent);
    }

    private void postRefreshApps() {
        final var activity = getActivity();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (isAdded()) {
                refreshApps();
            }
        });
    }

    private void refreshApps() {
        mAppsCategory.removeAll();
        final PackageManager pm = requireContext().getPackageManager();
        final IconDrawableFactory iconFactory = IconDrawableFactory.newInstance(requireContext());
        final List<ApplicationInfo> apps = pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_APP_LOCK_INFO));

        final ArrayList<AppEntry> entries = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            final ApplicationInfo app = apps.get(i);
            if (app.isAppLockSupported) {
                entries.add(new AppEntry(app, app.loadLabel(pm)));
            }
        }
        entries.sort((a, b) -> a.mLabel.toString().compareToIgnoreCase(b.mLabel.toString()));

        if (entries.isEmpty()) {
            final Preference empty = new Preference(getPrefContext());
            empty.setTitle(R.string.app_lock_no_lockable_apps);
            empty.setSelectable(false);
            mAppsCategory.addPreference(empty);
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            final AppEntry entry = entries.get(i);
            final SwitchPreferenceCompat preference =
                    new SwitchPreferenceCompat(getPrefContext());
            preference.setKey("app_lock_app_" + entry.mAppInfo.packageName);
            preference.setTitle(entry.mLabel);
            preference.setIcon(iconFactory.getBadgedIcon(entry.mAppInfo));
            preference.setPersistent(false);
            preference.setChecked(entry.mAppInfo.isAppLockEnabled);
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                startActivity(AppLockActivity.createAppLockActivityIntent(
                        entry.mAppInfo.packageName, Boolean.TRUE.equals(newValue)));
                return false;
            });
            mAppsCategory.addPreference(preference);
        }
    }

    private static final class AppEntry {
        final ApplicationInfo mAppInfo;
        final CharSequence mLabel;

        AppEntry(ApplicationInfo appInfo, CharSequence label) {
            mAppInfo = appInfo;
            mLabel = label;
        }
    }
}
