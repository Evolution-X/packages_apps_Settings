package com.android.settings.fuelgauge;

import android.content.Context;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.spa.SpaActivity;
import com.android.settings.spa.app.battery.BatteryOptimizationModeAppListPageProvider;

public class AppBatteryUsagePrefController extends BasePreferenceController {

    public AppBatteryUsagePrefController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return false;
        }

        SpaActivity.startSpaActivity(mContext, BatteryOptimizationModeAppListPageProvider.INSTANCE.getName());
        return true;
    }
}
