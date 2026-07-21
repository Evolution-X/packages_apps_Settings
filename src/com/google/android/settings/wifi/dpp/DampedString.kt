package com.google.android.settings.wifi.dpp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class DampedString(periodFrames: Int, private val dampingRatio: Float) {
    private val stiffness: Float = ((2f * PI.toFloat() / periodFrames).pow(2)) * 1.0f // mass = 1.0
    private val undampedNaturalFrequency: Float = sqrt(stiffness / 1.0f)
    private val dampedNaturalFrequency: Float =
        undampedNaturalFrequency * sqrt(abs(1.0f - dampingRatio.pow(2)))

    fun calculatePosition(t: Int): Float {
        val decay = undampedNaturalFrequency * dampingRatio
        val wd = dampedNaturalFrequency
        val phaseCoeff = (-decay + 0.0f) / wd
        val tf = t.toFloat()
        val angle = wd * tf
        return exp(-decay * tf) * (phaseCoeff * sin(angle) + cos(angle) * -1.0f) + 1.0f
    }
}
