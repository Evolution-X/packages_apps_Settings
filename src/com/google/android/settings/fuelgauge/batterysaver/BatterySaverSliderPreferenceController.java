package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.settings.R;
import com.android.settingslib.Utils;
import com.android.settingslib.widget.SliderPreference;

class BatterySaverSliderPreferenceController implements Preference.OnPreferenceChangeListener {

    private static final String KEY_BATTERY_SAVER_PERCENTAGE = "key_battery_saver_percentage";
    private static final String KEY_SLIDER = "battery_saver_seek_bar";
    private static final String SETTING_LOW_POWER_TRIGGER = "low_power_trigger_level";

    private static final int SLIDER_ORDER = 50;
    private static final int SLIDER_MAX = 15;
    private static final int SLIDER_MIN = 4;
    private static final int SLIDER_INCREMENT = 1;
    private static final int PERCENTAGE_STEP = 5;

    private final Context mContext;
    private final SliderPreference mSliderPreference;
    int mPercentage;

    BatterySaverSliderPreferenceController(Context context) {
        mContext = context;
        mSliderPreference = new SliderPreference(context);
        mSliderPreference.setOrder(SLIDER_ORDER);
        mSliderPreference.setMax(SLIDER_MAX);
        mSliderPreference.setMin(SLIDER_MIN);
        mSliderPreference.setKey(KEY_SLIDER);
        mSliderPreference.setSliderIncrement(SLIDER_INCREMENT);
        mSliderPreference.setTickVisible(false);
        mSliderPreference.setHapticFeedbackMode(1);
        mSliderPreference.setUpdatesContinuously(true);
        mSliderPreference.setOnPreferenceChangeListener(this);
    }

    void updateSliderPreference(
            PreferenceCategory preferenceCategory, String scheduleKey, int triggerLevel) {
        if (!KEY_BATTERY_SAVER_PERCENTAGE.equals(scheduleKey)) {
            preferenceCategory.removePreference(mSliderPreference);
            return;
        }
        mSliderPreference.setValue(Math.max(triggerLevel / PERCENTAGE_STEP, SLIDER_MIN));
        mPercentage = mSliderPreference.getValue() * PERCENTAGE_STEP;
        mSliderPreference.setTitle(formatStateDescription());
        preferenceCategory.addPreference(mSliderPreference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        int newPercentage = (Integer) newValue * PERCENTAGE_STEP;
        if (newPercentage <= 0 || newPercentage == mPercentage) {
            return true;
        }
        mPercentage = newPercentage;
        Settings.Global.putInt(
                mContext.getContentResolver(), SETTING_LOW_POWER_TRIGGER, mPercentage);
        mSliderPreference.setTitle(formatStateDescription());
        return true;
    }

    private CharSequence formatStateDescription() {
        return mContext.getString(
                R.string.battery_saver_seekbar_title,
                Utils.formatPercentage(mPercentage));
    }
}
