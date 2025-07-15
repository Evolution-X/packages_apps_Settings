package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.content.IntentFilter;
import android.provider.Settings;
import android.widget.CompoundButton;

import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.TopIntroPreference;

public class AdaptiveBatteryExpandController extends BasePreferenceController
        implements ExpandDividerPreference.OnExpandListener,
                CompoundButton.OnCheckedChangeListener {
    static final String ADAPTIVE_BATTERY_INTRO_KEY = "adaptive_battery_top_intro";
    static final String ADAPTIVE_BATTERY_SWITCH_KEY = "adaptive_battery_management_enabled";
    static final int OFF = 0;
    static final int ON = 1;
    private MainSwitchPreference mAdaptiveBatterySwitchPreference;
    private TopIntroPreference mAdaptiveBatteryTopIntroPreference;

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

    public AdaptiveBatteryExpandController(Context context, String str) {
        super(context, str);
    }

    @Override
    public int getAvailabilityStatus() {
        return this.mContext
                        .getResources()
                        .getBoolean(com.android.internal.R.bool.config_swipeDisambiguation)
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(PreferenceScreen preferenceScreen) {
        super.displayPreference(preferenceScreen);
        ExpandDividerPreference expandDividerPreference =
                (ExpandDividerPreference) preferenceScreen.findPreference(getPreferenceKey());
        expandDividerPreference.setOnExpandListener(this);
        this.mAdaptiveBatteryTopIntroPreference =
                (TopIntroPreference)
                        expandDividerPreference.findPreference(ADAPTIVE_BATTERY_INTRO_KEY);
        MainSwitchPreference mainSwitchPreference =
                (MainSwitchPreference)
                        expandDividerPreference.findPreference(ADAPTIVE_BATTERY_SWITCH_KEY);
        this.mAdaptiveBatterySwitchPreference = mainSwitchPreference;
        mainSwitchPreference.addOnSwitchChangeListener(this);
        this.mAdaptiveBatterySwitchPreference.setChecked(
                Settings.Global.getInt(
                                this.mContext.getContentResolver(), ADAPTIVE_BATTERY_SWITCH_KEY, 1)
                        == 1);
        onExpand(expandDividerPreference.isExpended());
    }

    @Override
    public void onExpand(boolean z) {
        TopIntroPreference topIntroPreference = this.mAdaptiveBatteryTopIntroPreference;
        if (topIntroPreference != null) {
            topIntroPreference.setVisible(z);
        }
        MainSwitchPreference mainSwitchPreference = this.mAdaptiveBatterySwitchPreference;
        if (mainSwitchPreference != null) {
            mainSwitchPreference.setVisible(z);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean z) {
        Settings.Global.putInt(
                this.mContext.getContentResolver(), ADAPTIVE_BATTERY_SWITCH_KEY, z ? ON : OFF);
    }
}
