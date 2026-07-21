package com.google.android.settings.wifi.dpp

import android.graphics.Path
import android.view.animation.PathInterpolator

object EmphasizedInterpolator {
    private val interpolator: PathInterpolator =
        Path()
            .apply {
                moveTo(0.0f, 0.0f)
                cubicTo(0.05f, 0.0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
                cubicTo(0.208333f, 0.82f, 0.25f, 1.0f, 1.0f, 1.0f)
            }
            .let { PathInterpolator(it) }

    fun getInterpolation(fraction: Float): Float = interpolator.getInterpolation(fraction)
}
