package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.content.IntentFilter;
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
    private PreferenceCategory mPreferenceCategory;
    BatterySaverSliderPreferenceController mSliderPreferenceController;
    private final int mThreshold;

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
    public int getSliceHighlightMenuRes() {
        return super.getSliceHighlightMenuRes();
    }

    @Override
    public boolean hasAsyncUpdate() {
        return super.hasAsyncUpdate();
    }

    @Override
    public boolean isPublicSlice() {
        return super.isPublicSlice();
    }

    @Override
    public boolean isSliceable() {
        return super.isSliceable();
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return super.useDynamicSliceSummary();
    }

    public BatterySaverSchedulePreferenceController(Context context, String str) {
        super(context, str);
        this.mSliderPreferenceController = new BatterySaverSliderPreferenceController(context);
        this.mThreshold =
                Settings.Global.getInt(context.getContentResolver(), "low_power_trigger_level", 0);
    }

    @Override
    public void displayPreference(PreferenceScreen preferenceScreen) {
        super.displayPreference(preferenceScreen);
        PreferenceCategory preferenceCategory =
                (PreferenceCategory) preferenceScreen.findPreference(getPreferenceKey());
        this.mPreferenceCategory = preferenceCategory;
        if (preferenceCategory != null) {
            initPreferences();
        }
    }

    private void initPreferences() {
        TwoStatePreference twoStatePreference =
                (TwoStatePreference)
                        this.mPreferenceCategory.findPreference(KEY_BATTERY_SAVER_SCHEDULE);
        if (twoStatePreference != null) {
            twoStatePreference.setOnPreferenceChangeListener(this);
            twoStatePreference.setChecked(
                    BatterySaverUtils.getBatterySaverScheduleKey(this.mContext)
                            .equals("key_battery_saver_percentage"));
            this.mSliderPreferenceController.updateSliderPreference(
                    this.mPreferenceCategory,
                    BatterySaverUtils.getBatterySaverScheduleKey(this.mContext),
                    this.mThreshold);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object obj) {
        if (!preference.getKey().equals(KEY_BATTERY_SAVER_SCHEDULE)) {
            return true;
        }
        boolean booleanValue = ((Boolean) obj).booleanValue();
        BatterySaverUtils.setBatterySaverScheduleMode(
                this.mContext,
                "key_battery_saver_percentage",
                booleanValue ? DEFAULT_MIN_SCHEDULE_THRESHOLD : DEFAULT_THRESHOLD);
        this.mSliderPreferenceController.updateSliderPreference(
                this.mPreferenceCategory,
                booleanValue ? "key_battery_saver_percentage" : "key_battery_saver_no_schedule",
                booleanValue ? DEFAULT_MIN_SCHEDULE_THRESHOLD : DEFAULT_THRESHOLD);
        return true;
    }
}
