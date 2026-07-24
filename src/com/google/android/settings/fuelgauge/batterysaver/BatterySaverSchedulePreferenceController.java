package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.fuelgauge.BatterySaverUtils;

public class BatterySaverSchedulePreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {

    static final int DEFAULT_MIN_SCHEDULE_THRESHOLD = 20;
    static final int DEFAULT_THRESHOLD = 0;
    public static final String KEY_BATTERY_SAVER_SCHEDULE = "battery_saver_base_on_percentage";

    private static final String KEY_PERCENTAGE = "key_battery_saver_percentage";
    private static final String KEY_NO_SCHEDULE = "key_battery_saver_no_schedule";
    private static final String SETTING_LOW_POWER_TRIGGER = "low_power_trigger_level";

    private PreferenceCategory mPreferenceCategory;
    BatterySaverSliderPreferenceController mSliderPreferenceController;
    private final int mThreshold;

    public BatterySaverSchedulePreferenceController(Context context, String str) {
        super(context, str);
        mSliderPreferenceController = new BatterySaverSliderPreferenceController(context);
        mThreshold =
                Settings.Global.getInt(
                        context.getContentResolver(), SETTING_LOW_POWER_TRIGGER, DEFAULT_THRESHOLD);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen preferenceScreen) {
        super.displayPreference(preferenceScreen);
        mPreferenceCategory =
                (PreferenceCategory) preferenceScreen.findPreference(getPreferenceKey());
        if (mPreferenceCategory != null) {
            initPreferences();
        }
    }

    private void initPreferences() {
        TwoStatePreference pref =
                (TwoStatePreference) mPreferenceCategory.findPreference(KEY_BATTERY_SAVER_SCHEDULE);
        if (pref == null) return;

        String scheduleKey = BatterySaverUtils.getBatterySaverScheduleKey(mContext);
        pref.setOnPreferenceChangeListener(this);
        pref.setChecked(KEY_PERCENTAGE.equals(scheduleKey));
        mSliderPreferenceController.updateSliderPreference(
                mPreferenceCategory, scheduleKey, mThreshold);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!KEY_BATTERY_SAVER_SCHEDULE.equals(preference.getKey())) {
            return true;
        }
        boolean enabled = (Boolean) newValue;
        String scheduleKey = enabled ? KEY_PERCENTAGE : KEY_NO_SCHEDULE;
        int threshold = enabled ? DEFAULT_MIN_SCHEDULE_THRESHOLD : DEFAULT_THRESHOLD;

        BatterySaverUtils.setBatterySaverScheduleMode(mContext, KEY_PERCENTAGE, threshold);
        mSliderPreferenceController.updateSliderPreference(
                mPreferenceCategory, scheduleKey, threshold);
        return true;
    }
}
