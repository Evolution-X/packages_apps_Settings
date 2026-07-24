package com.google.android.settings.fuelgauge.batterysaver;

import android.app.settings.SettingsEnums;

import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;

import com.android.settings.R;

public class BatterySaverScheduleAndRemindersSettings extends DashboardFragment {

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.battery_saver_schedule_and_reminders);

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.FUELGAUGE_BATTERY_SAVER_SCHEDULE;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.battery_saver_schedule_and_reminders;
    }

    @Override
    protected String getLogTag() {
        return "BatterySaverScheduleAndRemindersSettings";
    }
}
