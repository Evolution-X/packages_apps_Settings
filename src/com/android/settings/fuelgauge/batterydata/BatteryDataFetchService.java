//
// Copyright (C) 2022 The Project Mia
//
// SPDX-License-Identifier: Apache-2.0
//

package com.android.settings.fuelgauge.batterydata;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.BatteryStatsManager;
import android.os.BatteryUsageStats;
import android.os.BatteryUsageStatsQuery;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.UidBatteryConsumer;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

public class BatteryDataFetchService extends JobIntentService {

    private static final String TAG = "BatteryDataFetchService";
    private static final Intent JOB_INTENT = new Intent("action.LOAD_BATTERY_USAGE_DATA");

    private static Intent sLastIntent;

    public static void enqueueWork(final Context context) {
        AsyncTask.execute(() -> loadUsageDataSafely(context, sLastIntent));
    }

    public static void enqueueWork(final Context context, final Intent intent) {
        sLastIntent = intent;
        AsyncTask.execute(() -> loadUsageDataSafely(context, intent));
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        loadUsageDataSafely(this, intent);
    }

    private static void loadUsageDataSafely(Context context, Intent intent) {
        try {
            loadUsageData(context, intent);
        } catch (RuntimeException e) {
            Log.e(TAG, "Fail load usage data: " + e);
            if (intent != null) {
                ResultReceiver resultReceiver =
                        intent.getParcelableExtra(Intent.EXTRA_RESULT_RECEIVER);
                if (resultReceiver != null) {
                    resultReceiver.send(1, null);
                }
            }
        }
    }

    private static void loadUsageData(Context context, Intent intent) {
        ResultReceiver resultReceiver = null;
        if (intent != null) {
            resultReceiver = intent.getParcelableExtra(Intent.EXTRA_RESULT_RECEIVER);
        }

        if (resultReceiver == null) {
            Log.w(TAG, "No result receiver found — widget won't update");
        }

        BatteryUsageStats batteryUsageStats = context
                .getSystemService(BatteryStatsManager.class)
                .getBatteryUsageStats(new BatteryUsageStatsQuery.Builder()
                        .includeBatteryHistory()
                        .build());

        if (batteryUsageStats == null) {
            Log.w(TAG, "batteryUsageStats is null");
            if (resultReceiver != null) resultReceiver.send(1, null);
            return;
        }

        Bundle bundle = new Bundle();

        bundle.putDouble("batteryCapacity",
                batteryUsageStats.getBatteryCapacity());
        bundle.putDouble("consumedPower",
                batteryUsageStats.getConsumedPower());
        bundle.putFloat("dischargingRate",
                batteryUsageStats.getDischargePercentage());
        bundle.putLong("statsStartTimestamp",
                batteryUsageStats.getStatsStartTimestamp());
        bundle.putLong("statsEndTimestamp",
                batteryUsageStats.getStatsEndTimestamp());

        int appCount = 0;
        for (UidBatteryConsumer consumer : batteryUsageStats.getUidBatteryConsumers()) {
            String packageName = consumer.getPackageWithHighestDrain();
            if (packageName == null) continue;
            bundle.putFloat("app_power_" + appCount, (float) consumer.getConsumedPower());
            bundle.putString("app_package_" + appCount, packageName);
            bundle.putInt("app_uid_" + appCount, consumer.getUid());
            appCount++;
        }
        bundle.putInt("appCount", appCount);

        if (resultReceiver != null) {
            resultReceiver.send(0, bundle);
            Log.d(TAG, "Battery usage data sent to Intelligence, apps=" + appCount);
        }

        try {
            batteryUsageStats.close();
        } catch (Exception e) {
            Log.w(TAG, "Failed to close batteryUsageStats: " + e);
        }
    }
}
