/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.util.evolution.PixelPropsUtils;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.LayoutPreference;

public class EvolutionXLogoPreferenceController extends BasePreferenceController {

    private static final Uri INTENT_URI_DATA = Uri.parse("https://evolution-x.org/");
    private static final String TAG = "EvolutionXLogoPreferenceCtrl";

    private final PackageManager mPackageManager;

    public EvolutionXLogoPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mPackageManager = mContext.getPackageManager();
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final LayoutPreference logoPref = screen.findPreference(getPreferenceKey());
        if (logoPref == null) return;
        final View root = logoPref.findViewById(R.id.evolution_logo_container);
        if (root == null) return;

        // Check if we're in light theme
        int nightMode = mContext.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isLightTheme = (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_NO);

        if (isLightTheme) {
            // Light theme: dark outline logo, no glow/animation
            android.widget.ImageView logo = root.findViewById(R.id.evolution_logo_main);
            if (logo != null) {
                logo.setColorFilter(
                    android.graphics.Color.BLACK,
                    android.graphics.PorterDuff.Mode.SRC_ATOP);
            }
            // Hide the glow view entirely
            View glow = root.findViewById(R.id.evolution_rgb_glow);
            if (glow != null) glow.setVisibility(View.GONE);
        } else {
            // Dark theme: full synthwave animation
            EvolutionLogoAnimator.bind(root);
        }
    }

    @Override
    public int getAvailabilityStatus() {
        if (PixelPropsUtils.isCustomForkBuild()) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return AVAILABLE;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return false;
        }

        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(INTENT_URI_DATA);
        if (mPackageManager.queryIntentActivities(intent, 0).isEmpty()) {
            // Don't send out the intent to stop crash
            Log.w(TAG, "queryIntentActivities() returns empty");
            return true;
        }

        mContext.startActivity(intent);
        return true;
    }
}
