/*
 * Copyright (C) 2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settings.display

import android.content.Context
import android.provider.Settings
import com.android.settings.core.BasePreferenceController
import android.view.CrossWindowBlurListeners.CROSS_WINDOW_BLUR_SUPPORTED
import kotlin.math.roundToInt

class BlurSettingsPreferenceController(
    context: Context
) : BasePreferenceController(context, "blur_settings") {

    override fun getAvailabilityStatus(): Int {
        return if (CROSS_WINDOW_BLUR_SUPPORTED) AVAILABLE else UNSUPPORTED_ON_DEVICE
    }

    override fun getSummary(): CharSequence {
        val blursEnabled = Settings.Global.getInt(
            mContext.contentResolver,
            Settings.Global.DISABLE_WINDOW_BLURS,
            0
        ) == 0

        if (blursEnabled) {
            val radiusPct = Settings.Secure.getFloat(
                mContext.contentResolver,
                KEY_SYSTEM_BLUR_RADIUS_PCT,
                MAX_BLUR_RADIUS_PCT
            )
            val percent = radiusPct.takeIf { it.isFinite() }
                ?.coerceIn(MIN_BLUR_RADIUS_PCT, MAX_BLUR_RADIUS_PCT)
                ?: MAX_BLUR_RADIUS_PCT
            return "On (${percent.roundToInt()}%)"
        }
        return "Off"
    }

    companion object {
        private const val KEY_SYSTEM_BLUR_RADIUS_PCT = "system_blur_radius_pct"
        private const val MIN_BLUR_RADIUS_PCT = 0f
        private const val MAX_BLUR_RADIUS_PCT = 100f
    }
}
