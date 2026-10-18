/*
 * Copyright (C) 2019-2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.preference.Preference
import com.android.settings.R
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.UI_ONLY_PREFERENCE
import com.android.settingslib.preference.PreferenceBinding
import com.android.settingslib.widget.LayoutPreference

/** Evolution X logo banner on the Android version screen. Tapping opens the website. */
class EvolutionXLogoPreference :
    PreferenceMetadata, PreferenceBinding, Preference.OnPreferenceClickListener {

    override val key: String
        get() = KEY

    override val purpose: Int
        get() = R.string.evolution_logo_purpose

    override val indexable
        get() = false

    override fun tags(context: Context) = arrayOf(UI_ONLY_PREFERENCE)

    override fun createWidget(context: Context): Preference =
        LayoutPreference(context, R.layout.evolution_logo)

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        // GlowView resolves its own ink color from the current theme.
        (preference as? LayoutPreference)
            ?.findViewById<View>(R.id.evolution_logo_container)
            ?.let { EvolutionLogoAnimator.bind(it) }
        preference.onPreferenceClickListener = this
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val context = preference.context
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL))
        if (context.packageManager.queryIntentActivities(intent, 0).isEmpty()) {
            // Don't send out the intent to stop crash
            Log.w(TAG, "queryIntentActivities() returns empty")
            return true
        }
        context.startActivity(intent)
        return true
    }

    companion object {
        const val KEY = "evolution_logo"
        private const val TAG = "EvolutionXLogoPref"
        private const val WEBSITE_URL = "https://evolution-x.org/"
    }
}
