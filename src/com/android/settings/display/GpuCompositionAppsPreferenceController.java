package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.SubSettingLauncher;

public class GpuCompositionAppsPreferenceController extends BasePreferenceController {

    private static final String KEY = "gpu_composition_apps";

    public GpuCompositionAppsPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (KEY.equals(preference.getKey())) {
            new SubSettingLauncher(mContext)
                    .setDestination(GpuCompositionAppsSelectionFragment.class.getName())
                    .setTitleRes(R.string.use_gpu_for_screen_composition_title)
                    .setSourceMetricsCategory(SettingsEnums.DISPLAY)
                    .launch();
            return true;
        }
        return false;
    }

    @Override
    public void updateState(Preference preference) {
        final String enabledApps = Settings.Secure.getString(
                mContext.getContentResolver(), Settings.Secure.DISABLE_HW_OVERLAYS_APPS);

        int count = 0;
        if (!TextUtils.isEmpty(enabledApps)) {
            for (String pkg : enabledApps.split(",")) {
                if (!pkg.trim().isEmpty()) {
                    count++;
                }
            }
        }

        preference.setSummary(mContext.getResources().getQuantityString(
                R.plurals.gpu_composition_apps_summary, count, count));
    }
}
