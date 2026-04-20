/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.widget

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import kotlin.math.max
import kotlin.math.min

/*
 * Material 3 1.5.0-alpha23 inset focus ring, drawn by hand so it works on alpha16.
 *
 * Mirrors alpha23's RippleDefaults.InsetFocusRingRippleThemeConfiguration and the ripple node that
 * draws it: an inner stroke (3dp wide, 1dp inset, onSecondary) painted first, then an outer stroke
 * (2dp wide, no inset, secondary) on top. Both scale with one progress value that springs in on
 * motionScheme.fastSpatialSpec and out on fastEffectsSpec. The View-side twin is
 * FocusRingDrawable in deviceinfo/hardwareinfo.
 */
private val OuterWidth = 2.dp
private val OuterInset = 0.dp
private val InnerWidth = 3.dp
private val InnerInset = 1.dp

/**
 * Draws the ring over the component while it has keyboard focus.
 *
 * Apply it to the component's own modifier, ahead of its click handling, so it observes that
 * component's focus target. Only the component's own focus counts, not a child's, so a row that
 * contains a switch doesn't ring together with it.
 *
 * Material components pad their touch target out to 48dp but draw a smaller container. Pass
 * [ringWidth] / [ringHeight] to centre the ring on that container instead of the full bounds.
 * Shapes other than rounded rectangles ignore the inset.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Modifier.insetFocusRing(
    shape: Shape,
    ringWidth: Dp = Dp.Unspecified,
    ringHeight: Dp = Dp.Unspecified,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val active = focused && keyboard
    val motion = MaterialTheme.motionScheme
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        progress.animateTo(
            if (active) 1f else 0f,
            if (active) motion.fastSpatialSpec<Float>() else motion.fastEffectsSpec<Float>(),
        )
    }
    val outerColor = MaterialTheme.colorScheme.secondary
    val innerColor = MaterialTheme.colorScheme.onSecondary
    this
        .onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            val p = progress.value
            if (p > 0f) drawFocusRing(shape, ringWidth, ringHeight, p, innerColor, outerColor)
        }
}

private fun ContentDrawScope.drawFocusRing(
    shape: Shape,
    ringWidth: Dp,
    ringHeight: Dp,
    progress: Float,
    innerColor: Color,
    outerColor: Color,
) {
    val w = if (ringWidth.isSpecified) min(size.width, ringWidth.toPx()) else size.width
    val h = if (ringHeight.isSpecified) min(size.height, ringHeight.toPx()) else size.height
    val ringSize = Size(w, h)
    val outline = shape.createOutline(ringSize, layoutDirection, this)
    translate(left = (size.width - w) / 2f, top = (size.height - h) / 2f) {
        // Inner first, outer on top, same order as the ripple node.
        drawRingStroke(
            outline,
            InnerInset.toPx() * progress,
            InnerWidth.toPx() * progress,
            innerColor,
        )
        drawRingStroke(
            outline,
            OuterInset.toPx() * progress,
            OuterWidth.toPx() * progress,
            outerColor,
        )
    }
}

private fun DrawScope.drawRingStroke(outline: Outline, inset: Float, width: Float, color: Color) {
    if (width < 0.5f) return
    val rect: RoundRect = when (outline) {
        is Outline.Rounded -> outline.roundRect
        is Outline.Rectangle ->
            outline.rect.let { RoundRect(it.left, it.top, it.right, it.bottom, 0f, 0f) }
        is Outline.Generic -> {
            clipPath(outline.path) {
                drawPath(outline.path, color, style = Stroke(width = width * 2f))
            }
            return
        }
    }
    val outer = rect.shrunkBy(inset)
    val inner = rect.shrunkBy(inset + width)
    val ring = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(outer)
        // When the stroke is thicker than the area, fill it solid, as alpha23 does.
        if (inner.right > inner.left && inner.bottom > inner.top) addRoundRect(inner)
    }
    drawPath(ring, color)
}

private fun RoundRect.shrunkBy(v: Float) = RoundRect(
    left = left + v,
    top = top + v,
    right = right - v,
    bottom = bottom - v,
    topLeftCornerRadius = topLeftCornerRadius.shrunkBy(v),
    topRightCornerRadius = topRightCornerRadius.shrunkBy(v),
    bottomRightCornerRadius = bottomRightCornerRadius.shrunkBy(v),
    bottomLeftCornerRadius = bottomLeftCornerRadius.shrunkBy(v),
)

private fun CornerRadius.shrunkBy(v: Float) = CornerRadius(max(0f, x - v), max(0f, y - v))
