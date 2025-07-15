package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.content.IntentFilter;
import android.text.TextUtils;
import android.view.View;

import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.widget.FooterPreference;

import com.android.settings.R;

public class BatterySaverFooterPreferenceController extends BasePreferenceController {
    private FooterPreference mPreference;

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

    public BatterySaverFooterPreferenceController(Context context, String str) {
        super(context, str);
    }

    @Override
    public void displayPreference(PreferenceScreen preferenceScreen) {
        super.displayPreference(preferenceScreen);
        this.mPreference = (FooterPreference) preferenceScreen.findPreference(getPreferenceKey());
        setupFooter();
    }

    void setupFooter() {
        if (TextUtils.isEmpty(mContext.getString(R.string.help_url_battery_saver_settings))) {
            return;
        }
        addHelpLink();
    }

    void addHelpLink() {
        FooterPreference footerPreference = this.mPreference;
        if (footerPreference != null) {
            footerPreference.setLearnMoreAction(
                    new View.OnClickListener() {
                        @Override
                        public final void onClick(View view) {
                            mContext.startActivity(
                                    HelpUtils.getHelpIntent(
                                            mContext,
                                            mContext.getString(
                                                    R.string.help_url_battery_saver_settings),
                                            ""));
                        }
                    });
            this.mPreference.setLearnMoreText(
                    mContext.getString(com.android.settings.R.string.battery_saver_link_a11y));
        }
    }
}
