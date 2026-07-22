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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

import java.util.List;

public class AppLockPreferenceController extends BasePreferenceController {

    public AppLockPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return android.security.Flags.appLockApis() && android.security.Flags.appLockCore()
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        final List<ApplicationInfo> apps = mContext.getPackageManager().getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_APP_LOCK_INFO));
        int count = 0;
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).isAppLockEnabled) {
                count++;
            }
        }
        if (count == 0) {
            return mContext.getText(R.string.app_lock_settings_summary);
        }
        return mContext.getResources().getQuantityString(
                R.plurals.app_lock_summary_count, count, count);
    }
}
