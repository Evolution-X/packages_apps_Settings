/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion;

import android.view.View;
import android.widget.ImageView;

import com.android.settings.R;

public final class EvolutionLogoAnimator {
    private EvolutionLogoAnimator() {}

    public static void bind(View root) {
        final GlowView  holo = root.findViewById(R.id.evolution_rgb_glow);
        final ImageView logo = root.findViewById(R.id.evolution_logo_main);

        if (holo == null) return;

        // GlowView draws the whole animated wordmark itself. The ImageView only
        // stays around to reserve the same space, so keep it laid out but hidden.
        if (logo != null) {
            logo.setVisibility(View.INVISIBLE);
        }
        holo.setVisibility(View.VISIBLE);
    }
}
