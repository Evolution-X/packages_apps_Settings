/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.settings.deviceinfo.hardwareinfo

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.graphics.shapes.toPath
import com.android.settings.R
import com.android.settings.widget.insetFocusRing
import com.android.settingslib.spa.framework.theme.SettingsTheme

/**
 * Material 3 Expressive shapes for the model page's View-based hero. The cookie is
 * [MaterialShapes.Cookie9Sided], so the tile, maintainer avatar and verified badge use the real
 * shape instead of a hand-built approximation.
 */
object ModelPageShapes {

    /**
     * The cookie polygon as a plain [android.graphics.Path] and its bounds. [toPath] needs no
     * composition, unlike the Compose `toShape()`, so this can be used from View code.
     */
    private val cookiePolygon = MaterialShapes.Cookie9Sided
    private val badgePolygon = MaterialShapes.SoftBurst
    private val cookieBounds: FloatArray by lazy { cookiePolygon.calculateBounds() }
    private val cookieSource: android.graphics.Path by lazy { cookiePolygon.toPath() }
    private val badgeBounds: FloatArray by lazy { badgePolygon.calculateBounds() }
    private val badgeSource: android.graphics.Path by lazy { badgePolygon.toPath() }

    /** Android [android.graphics.Path] of the cookie, fitted to [w] x [h]. */
    private fun cookiePath(w: Float, h: Float): android.graphics.Path =
        fitted(cookieSource, cookieBounds, w, h)

    private fun fitted(
        source: android.graphics.Path,
        b: FloatArray,
        w: Float,
        h: Float,
    ): android.graphics.Path {
        val m =
            Matrix().apply {
                postTranslate(-b[0], -b[1])
                postScale(w / (b[2] - b[0]), h / (b[3] - b[1]))
            }
        return android.graphics.Path(source).apply { transform(m) }
    }

    /** Clips [view] to the SoftBurst used for the verified badge. */
    @JvmStatic
    fun clipToBadge(view: View) {
        view.outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: android.graphics.Outline) {
                    if (v.width <= 0 || v.height <= 0) return
                    outline.setPath(
                        fitted(badgeSource, badgeBounds, v.width.toFloat(), v.height.toFloat())
                    )
                }
            }
        view.clipToOutline = true
    }

    /** Clips [view] (and its children) to the cookie, sized to the view. */
    @JvmStatic
    fun clipToCookie(view: View) {
        view.outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: android.graphics.Outline) {
                    if (v.width <= 0 || v.height <= 0) return
                    val path =
                        cookiePath(v.width.toFloat(), v.height.toFloat())
                    outline.setPath(path)
                }
            }
        view.clipToOutline = true
    }

    /** A cookie-shaped solid fill. Unlike [clipToCookie] it never clips the view's children. */
    @JvmStatic
    fun cookieBackground(color: Int): Drawable = CookieDrawable(color)

    /**
     * Shows or stops the shape-morphing [ContainedLoadingIndicator] in [view]. It draws its own
     * Cookie9Sided container in the same 12% onPrimaryContainer tint as the tile, so the tile's
     * static cookie is switched off while it runs. The composition is only kept while loading so
     * the animation doesn't run while the view is hidden.
     */
    @JvmStatic
    fun setLoaderActive(view: ComposeView, active: Boolean) {
        if (!active) {
            view.disposeComposition()
            return
        }
        view.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        view.setContent {
            SettingsTheme {
                val colors = MaterialTheme.colorScheme
                ContainedLoadingIndicator(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = colors.onPrimaryContainer.copy(alpha = 0.12f),
                    indicatorColor = colors.onPrimaryContainer,
                    containerShape = MaterialShapes.Cookie9Sided.toShape(),
                )
            }
        }
    }

    /**
     * Composes the maintainer's Support button into [view] as a Material 3 [Button] with the
     * expressive shape morph.
     */
    @JvmStatic
    fun setSupportButton(view: ComposeView, onClick: Runnable) {
        view.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        view.setContent {
            SettingsTheme {
                val shapes = ButtonDefaults.shapes()
                Button(
                    onClick = { onClick.run() },
                    shapes = shapes,
                    modifier = Modifier.insetFocusRing(
                        shape = shapes.shape,
                        ringHeight = ButtonDefaults.MinHeight,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_model_heart),
                        contentDescription = null,
                        modifier =
                            Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
                    )
                    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight)))
                    Text(stringResource(R.string.maintainer_support))
                }
            }
        }
    }

    private class CookieDrawable(color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        private var path: android.graphics.Path? = null

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            path = cookiePath(bounds.width().toFloat(), bounds.height().toFloat())
        }

        override fun draw(canvas: Canvas) {
            val p = path ?: return
            val save = canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.drawPath(p, paint)
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
