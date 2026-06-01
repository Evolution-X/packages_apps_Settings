/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.settings.applications;

import android.content.pm.ApplicationInfo;
import android.os.Process;

import androidx.annotation.Nullable;

import com.android.settingslib.applications.StorageStatsSource.AppStorageStats;

public final class AppStorageStatsUtils {
    private AppStorageStatsUtils() {}

    @Nullable
    public static AppStorageStats normalizeStats(
            ApplicationInfo info, @Nullable AppStorageStats stats) {
        if (stats != null && shouldIgnoreCodeBytes(info)) {
            return new AppStorageStatsWrapper(stats);
        }
        return stats;
    }

    private static boolean shouldIgnoreCodeBytes(ApplicationInfo info) {
        return info.isSystemApp() && !info.isUpdatedSystemApp()
                && !Process.isApplicationUid(info.uid);
    }

    private static final class AppStorageStatsWrapper implements AppStorageStats {
        private final AppStorageStats mStats;

        AppStorageStatsWrapper(AppStorageStats stats) {
            mStats = stats;
        }

        @Override
        public long getCodeBytes() {
            return 0;
        }

        @Override
        public long getDataBytes() {
            return mStats.getDataBytes();
        }

        @Override
        public long getCacheBytes() {
            return mStats.getCacheBytes();
        }

        @Override
        public long getTotalBytes() {
            return mStats.getDataBytes();
        }
    }
}
