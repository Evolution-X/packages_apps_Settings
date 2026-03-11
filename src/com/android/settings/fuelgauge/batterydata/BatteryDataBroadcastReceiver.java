//
// Copyright (C) 2022 The Project Mia
//
// SPDX-License-Identifier: Apache-2.0
//

package com.android.settings.fuelgauge.batterydata;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BatteryDataBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "BatteryDataBroadcastReceiver";

    public boolean mFetchBatteryUsageData;

    @Override
    public void onReceive(Context context, Intent intent) {
        String batteryData = intent.getAction();
        if (batteryData == null) return;

        switch (batteryData) {
            case "settings.intelligence.battery.action.FETCH_BATTERY_USAGE_DATA":
                mFetchBatteryUsageData = true;
                BatteryDataFetchService.enqueueWork(context, intent);
                break;

            case "settings.intelligence.battery.action.FETCH_BLUETOOTH_BATTERY_DATA":
                try {
                    BluetoothBatteryDataFetch.returnBluetoothDevices(context, intent);
                } catch (Exception e) {
                    Log.e(TAG, "returnBluetoothDevices() error: ", e);
                }
                break;

            case Intent.ACTION_POWER_CONNECTED:
            case Intent.ACTION_POWER_DISCONNECTED:
            case Intent.ACTION_BATTERY_LOW:
            case Intent.ACTION_BATTERY_OKAY:
                Log.d(TAG, "Battery state changed: " + batteryData + " — notifying Intelligence");
                notifyIntelligence(context);
                break;

            default:
                break;
        }
    }

    private void notifyIntelligence(Context context) {
        Intent notify = new Intent(
                "settings.intelligence.battery.action.BATTERY_STATE_CHANGED");
        notify.setPackage("com.google.android.settings.intelligence");
        context.sendBroadcast(notify);
    }
}
