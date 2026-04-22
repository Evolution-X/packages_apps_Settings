/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import com.android.settings.R;

public final class EvolutionLogoAnimator {
    private EvolutionLogoAnimator() {}

    // Synthwave/retrowave palette inspired by deep purple night sky,
    // electric blue ocean, hot pink/magenta sun glow, warm golden horizon,
    // with a neon green stop woven through the cycle.
    // Each row is one keyframe; columns spread left→right across the banner.
    private static final int[][] FRAMES = new int[][] {
        // deep purple → electric blue → deep purple
        { 0xFF1a0040, 0xFF2a0a6e, 0xFF0033cc, 0xFF0066ff, 0xFF0033cc, 0xFF2a0a6e, 0xFF1a0040 },
        // electric blue → cyan → electric blue
        { 0xFF0033cc, 0xFF0066ff, 0xFF00aaff, 0xFF00ddff, 0xFF00aaff, 0xFF0066ff, 0xFF0033cc },
        // cyan → neon green → cyan
        { 0xFF00ccff, 0xFF00cc88, 0xFF00dd44, 0xFF33ff00, 0xFF00dd44, 0xFF00cc88, 0xFF00ccff },
        // neon green → hot pink → magenta
        { 0xFF00ff44, 0xFF44cc00, 0xFFcc0077, 0xFFff0088, 0xFFcc0077, 0xFF880055, 0xFF440033 },
        // hot pink → warm orange → hot pink
        { 0xFFff0088, 0xFFff3366, 0xFFff6600, 0xFFffaa00, 0xFFff6600, 0xFFff3366, 0xFFff0088 },
        // golden orange → magenta → violet
        { 0xFFff8800, 0xFFdd4400, 0xFFaa0066, 0xFFcc00aa, 0xFFaa0066, 0xFF770055, 0xFF440033 },
        // magenta → violet → deep purple
        { 0xFFcc00aa, 0xFF880077, 0xFF660088, 0xFF4400aa, 0xFF330077, 0xFF220055, 0xFF1a0040 },
        // violet → deep purple → back to start
        { 0xFF4400aa, 0xFF330077, 0xFF220055, 0xFF1a0040, 0xFF220055, 0xFF2a0a6e, 0xFF1a0040 },
    };

    public static void bind(View root) {
        final GlowView  glow = root.findViewById(R.id.evolution_rgb_glow);
        final ImageView logo = root.findViewById(R.id.evolution_logo_main);

        if (logo != null) {
            logo.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        }

        if (glow == null) return;

        ValueAnimator cycle = ValueAnimator.ofFloat(0f, FRAMES.length);
        cycle.setDuration(6000L);
        cycle.setRepeatCount(ValueAnimator.INFINITE);
        cycle.setInterpolator(new LinearInterpolator());

        cycle.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            int   i = (int) v % FRAMES.length;
            int   j = (i + 1) % FRAMES.length;
            float t = v - (int) v;
            glow.setColors(blend(FRAMES[i], FRAMES[j], t));
        });

        cycle.start();
    }

    private static int[] blend(int[] a, int[] b, float t) {
        int[] result = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            int ar = (a[i] >> 16) & 0xFF, ag = (a[i] >> 8) & 0xFF, ab = a[i] & 0xFF;
            int br = (b[i] >> 16) & 0xFF, bg = (b[i] >> 8) & 0xFF, bb = b[i] & 0xFF;
            int r  = (int)(ar + (br - ar) * t);
            int g  = (int)(ag + (bg - ag) * t);
            int bv = (int)(ab + (bb - ab) * t);
            result[i] = 0xFF000000 | (r << 16) | (g << 8) | bv;
        }
        return result;
    }
}
