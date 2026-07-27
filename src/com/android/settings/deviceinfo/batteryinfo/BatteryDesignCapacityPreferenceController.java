/*
 * Copyright (C) 2024 Paranoid Android
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

package com.android.settings.deviceinfo.batteryinfo;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import com.android.internal.os.PowerProfile;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.fuelgauge.BatteryUtils;

/**
 * A controller that manages the information about battery design capacity.
 */
public class BatteryDesignCapacityPreferenceController extends BasePreferenceController {

    public BatteryDesignCapacityPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        boolean isFeatureEnabled = mContext.getResources().getBoolean(R.bool.config_show_battery_design_capacity);
        return isFeatureEnabled ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        final int designCapacity = getDesignCapacityMah(mContext);
        if (designCapacity > 0) {
            return mContext.getString(R.string.battery_design_capacity_summary, designCapacity);
        }

        return mContext.getString(R.string.battery_design_capacity_not_available);
    }

    /**
     * Design capacity in mAh from the battery intent, falling back to the power profile.
     * Returns 0 when the device reports none. Shared with the Model page.
     */
    public static int getDesignCapacityMah(Context context) {
        Intent batteryIntent = BatteryUtils.getBatteryIntent(context);
        int designCapacityUah =
                batteryIntent.getIntExtra(BatteryManager.EXTRA_DESIGN_CAPACITY, -1);
        if (designCapacityUah <= 0) {
            final PowerProfile profile = new PowerProfile(context);
            designCapacityUah = (int) profile.getBatteryCapacity() * 1000;
        }
        return designCapacityUah > 0 ? designCapacityUah / 1_000 : 0;
    }
}
