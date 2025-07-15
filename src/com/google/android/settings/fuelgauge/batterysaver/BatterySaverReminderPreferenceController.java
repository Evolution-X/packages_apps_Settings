package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.content.IntentFilter;
import android.provider.Settings;

import com.android.settings.core.TogglePreferenceController;

public class BatterySaverReminderPreferenceController extends TogglePreferenceController {
    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public Class getBackgroundWorkerClass() {
        return super.getBackgroundWorkerClass();
    }

    @Override
    public IntentFilter getIntentFilter() {
        return super.getIntentFilter();
    }

    @Override
    public boolean hasAsyncUpdate() {
        return super.hasAsyncUpdate();
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return super.useDynamicSliceSummary();
    }

    public BatterySaverReminderPreferenceController(Context context, String str) {
        super(context, str);
    }

    @Override
    public boolean isChecked() {
        return Settings.Global.getInt(
                        this.mContext.getContentResolver(), "low_power_mode_reminder_enabled", 1)
                == 1;
    }

    @Override
    public boolean setChecked(boolean z) {
        Settings.Global.putInt(
                this.mContext.getContentResolver(), "low_power_mode_reminder_enabled", z ? 1 : 0);
        return true;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return com.android.settings.R.string.menu_key_battery;
    }
}
