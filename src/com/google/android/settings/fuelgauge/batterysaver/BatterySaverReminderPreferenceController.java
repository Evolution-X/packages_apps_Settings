package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

public class BatterySaverReminderPreferenceController extends TogglePreferenceController {

    private static final String SETTING_REMINDER_ENABLED = "low_power_mode_reminder_enabled";

    public BatterySaverReminderPreferenceController(Context context, String str) {
        super(context, str);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean isChecked() {
        return Settings.Global.getInt(
                        mContext.getContentResolver(), "low_power_mode_reminder_enabled", 1)
                == 1;
    }

    @Override
    public boolean setChecked(boolean z) {
        return Settings.Global.putInt(
                mContext.getContentResolver(), "low_power_mode_reminder_enabled", z ? 1 : 0);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_battery;
    }
}
