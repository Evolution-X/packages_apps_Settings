package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.settingslib.Utils;
import com.android.settingslib.widget.SliderPreference;

class BatterySaverSliderPreferenceController implements Preference.OnPreferenceChangeListener {
    private final Context mContext;
    int mPercentage;
    SliderPreference mSliderPreference;

    BatterySaverSliderPreferenceController(Context context) {
        this.mContext = context;
        SliderPreference sliderPreference = new SliderPreference(context);
        this.mSliderPreference = sliderPreference;
        sliderPreference.setOrder(50);
        this.mSliderPreference.setMax(15);
        this.mSliderPreference.setMin(4);
        this.mSliderPreference.setKey("battery_saver_seek_bar");
        this.mSliderPreference.setSliderIncrement(1);
        this.mSliderPreference.setTickVisible(false);
        this.mSliderPreference.setHapticFeedbackMode(1);
        this.mSliderPreference.setUpdatesContinuously(true);
        this.mSliderPreference.setOnPreferenceChangeListener(this);
    }

    void updateSliderPreference(PreferenceCategory preferenceCategory, String str, int i) {
        if ("key_battery_saver_percentage".equals(str)) {
            this.mSliderPreference.setValue(Math.max(i / 5, 4));
            this.mPercentage = this.mSliderPreference.getValue() * 5;
            this.mSliderPreference.setTitle(formatStateDescription());
            preferenceCategory.addPreference(this.mSliderPreference);
            return;
        }
        preferenceCategory.removePreference(this.mSliderPreference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object obj) {
        int intValue = ((Integer) obj).intValue() * 5;
        if (intValue <= 0 || intValue == this.mPercentage) {
            return true;
        }
        this.mPercentage = intValue;
        Settings.Global.putInt(
                this.mContext.getContentResolver(), "low_power_trigger_level", this.mPercentage);
        this.mSliderPreference.setTitle(formatStateDescription());
        return true;
    }

    private CharSequence formatStateDescription() {
        return this.mContext.getString(
                com.android.settings.R.string.battery_saver_seekbar_title,
                Utils.formatPercentage(this.mPercentage));
    }
}
