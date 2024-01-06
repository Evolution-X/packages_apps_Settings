/*
 * Copyright (C) 2023 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

/**
 * The ListPreference for the pages need to show apps icon.
*/
public class AppListPreference extends ListPreference {

    public AppListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(com.android.settingslib.widget.preference.app.R.layout.preference_app);
    }

    public AppListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(com.android.settingslib.widget.preference.app.R.layout.preference_app);
    }

    public AppListPreference(Context context) {
        super(context);
        setLayoutResource(com.android.settingslib.widget.preference.app.R.layout.preference_app);
    }

    public AppListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(com.android.settingslib.widget.preference.app.R.layout.preference_app);
    }
}
