/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.hardwareinfo;

/**
 * M3 Expressive spring tokens (ExpressiveMotionTokens) for the View-based model page, which cannot
 * read Compose's MotionScheme. Each pair is damping ratio / stiffness, as used by
 * androidx.dynamicanimation's SpringForce. Keep these in sync with the Compose side when the
 * prebuilt material3 moves.
 */
final class ModelPageMotion {

    static final float FAST_SPATIAL_DAMPING = 0.6f;
    static final float FAST_SPATIAL_STIFFNESS = 800f;
    static final float DEFAULT_SPATIAL_DAMPING = 0.8f;
    static final float DEFAULT_SPATIAL_STIFFNESS = 380f;
    static final float FAST_EFFECTS_DAMPING = 1f;
    static final float FAST_EFFECTS_STIFFNESS = 3800f;
    static final float SLOW_EFFECTS_DAMPING = 1f;
    static final float SLOW_EFFECTS_STIFFNESS = 800f;

    private ModelPageMotion() {}
}
