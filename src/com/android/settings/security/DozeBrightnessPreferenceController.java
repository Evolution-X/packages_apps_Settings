package com.android.settings.security;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.R;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnResume;

import org.evolution.settings.preferences.SystemSettingSeekBarPreference;

public class DozeBrightnessPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener, LifecycleObserver, OnResume {

    private static final String KEY_DOZE_BRIGHTNESS = "doze_brightness";
    private static final String KEY_PULSE_BRIGHTNESS = "pulse_brightness";

    private SystemSettingSeekBarPreference mDozeBrightness;
    private SystemSettingSeekBarPreference mPulseBrightness;

    public DozeBrightnessPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mDozeBrightness = screen.findPreference(KEY_DOZE_BRIGHTNESS);
        mPulseBrightness = screen.findPreference(KEY_PULSE_BRIGHTNESS);
    }

    @Override
    public void onResume() {
        updateState(mDozeBrightness);
        updateState(mPulseBrightness);
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (preference == null) {
            return;
        }

        int defaultDoze = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_screenBrightnessDoze);
        int defaultPulse = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_screenBrightnessPulse);
        if (defaultPulse == -1) {
            defaultPulse = defaultDoze;
        }

        if (TextUtils.equals(preference.getKey(), KEY_DOZE_BRIGHTNESS)) {
            final int value = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.DOZE_BRIGHTNESS, defaultDoze);
            ((SystemSettingSeekBarPreference) preference).setValue(value);
        } else if (TextUtils.equals(preference.getKey(), KEY_PULSE_BRIGHTNESS)) {
            final int value = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.PULSE_BRIGHTNESS, defaultPulse);
            ((SystemSettingSeekBarPreference) preference).setValue(value);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        final int value = (Integer) newValue;

        if (TextUtils.equals(key, KEY_DOZE_BRIGHTNESS)) {
            Settings.System.putInt(mContext.getContentResolver(), Settings.System.DOZE_BRIGHTNESS, value);
            return true;
        } else if (TextUtils.equals(key, KEY_PULSE_BRIGHTNESS)) {
            Settings.System.putInt(mContext.getContentResolver(), Settings.System.PULSE_BRIGHTNESS, value);
            return true;
        }
        return false;
    }
}
