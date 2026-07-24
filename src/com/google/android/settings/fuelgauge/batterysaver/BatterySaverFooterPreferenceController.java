package com.google.android.settings.fuelgauge.batterysaver;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.widget.FooterPreference;

public class BatterySaverFooterPreferenceController extends BasePreferenceController {

    private FooterPreference mPreference;

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    public BatterySaverFooterPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = (FooterPreference) screen.findPreference(getPreferenceKey());
        setupFooter();
    }

    void setupFooter() {
        if (TextUtils.isEmpty(mContext.getString(R.string.help_url_battery_saver_settings))) {
            return;
        }
        addHelpLink();
    }

    void addHelpLink() {
        if (mPreference == null) {
            return;
        }
        mPreference.setLearnMoreAction(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String helpUrl =
                                mContext.getString(R.string.help_url_battery_saver_settings);
                        mContext.startActivity(HelpUtils.getHelpIntent(mContext, helpUrl, ""));
                    }
                });
        mPreference.setLearnMoreText(mContext.getString(R.string.battery_saver_link_a11y));
    }
}
