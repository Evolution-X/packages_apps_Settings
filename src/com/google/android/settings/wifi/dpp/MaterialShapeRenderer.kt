package com.google.android.settings.wifi.dpp

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.VectorDrawable
import kotlin.math.cos

class MaterialShapeRenderer(
    private val srcImgSvg: VectorDrawable,
    val destRect: RectF,
    private var paint: Paint,
) {
    enum class EntryAnimationStyle {
        None,
        ZoomIn,
        EmphasizedZoomIn,
        SpringZoomIn,
        RotateEmphasizedZoomIn,
    }

    companion object {
        private val springScaleCache = HashMap<Int, Float>()
        private val dampedString = DampedString(60, 0.63f)

        fun calculateSpringScale(elapsedSinceDelayMs: Long): Float {
            val frame = (elapsedSinceDelayMs / 16).toInt()
            springScaleCache[frame]?.let {
                return it
            }
            val position = dampedString.calculatePosition(frame)
            springScaleCache[frame] = position
            return position
        }
    }

    private var animationStyle: EntryAnimationStyle = EntryAnimationStyle.None
    private var startDelay: Long = 0
    private var duration: Long = 0
    private var initialRotation: Int = 0
    private var skipStartProgress: Float = 0f
    private var isMotionPaused: Boolean = false

    fun setPaint(paint: Paint) {
        this.paint = paint
    }

    fun setStartDelay(delayMs: Long) {
        this.startDelay = delayMs
    }

    fun setDuration(durationMs: Long) {
        this.duration = durationMs
    }

    fun setInitialRotation(quarterTurns: Int) {
        this.initialRotation = quarterTurns
    }

    fun setSkipStartProgress(progress: Float) {
        this.skipStartProgress = progress
    }

    fun setAnimationStyle(style: EntryAnimationStyle) {
        this.animationStyle = style
    }

    fun draw(canvas: Canvas, elapsedMs: Long) {
        if (elapsedMs < startDelay) return
        val localElapsed = elapsedMs - startDelay
        when (animationStyle) {
            EntryAnimationStyle.None -> drawForNone(canvas, localElapsed)
            EntryAnimationStyle.ZoomIn -> drawForZoomIn(canvas, localElapsed)
            EntryAnimationStyle.SpringZoomIn -> drawForSpringZoomIn(canvas, localElapsed)
            EntryAnimationStyle.RotateEmphasizedZoomIn ->
                drawForRotateEmphasizedZoomIn(canvas, localElapsed)
            EntryAnimationStyle.EmphasizedZoomIn -> drawForEmphasizedZoomIn(canvas, localElapsed)
        }
    }

    private fun draw(canvas: Canvas, rect: RectF, paint: Paint) {
        canvas.save()
        canvas.rotate(initialRotation * 90f, rect.centerX(), rect.centerY())
        val bounds = Rect()
        rect.round(bounds)
        srcImgSvg.bounds = bounds
        srcImgSvg.colorFilter = paint.colorFilter
        srcImgSvg.draw(canvas)
        canvas.restore()
    }

    private fun drawForNone(canvas: Canvas, localElapsedMs: Long) {
        draw(canvas, destRect, paint)
    }

    private fun drawForZoomIn(canvas: Canvas, localElapsedMs: Long) {
        val effectiveDuration = if (duration > 0) duration.toFloat() else 1000f
        val t = localElapsedMs.toFloat()
        if (t / effectiveDuration < skipStartProgress) return
        if (t <= effectiveDuration) {
            val scale = (cos(((t - 1000f) / 1000f) * Math.PI.toFloat()) + 1f) / 2f
            draw(canvas, scaledRect(scale), paint)
        } else {
            drawForNone(canvas, localElapsedMs)
        }
    }

    private fun drawForEmphasizedZoomIn(canvas: Canvas, localElapsedMs: Long) {
        val effectiveDuration = if (duration > 0) duration.toFloat() else 1000f
        val t = localElapsedMs.toFloat()
        if (t <= effectiveDuration) {
            val scale = EmphasizedInterpolator.getInterpolation(t / effectiveDuration)
            draw(canvas, scaledRect(scale), paint)
        } else {
            drawForNone(canvas, localElapsedMs)
        }
    }

    private fun drawForSpringZoomIn(canvas: Canvas, localElapsedMs: Long) {
        if (localElapsedMs > 1500) {
            drawForNone(canvas, localElapsedMs)
        } else {
            val scale = calculateSpringScale(localElapsedMs)
            draw(canvas, scaledRect(scale), paint)
        }
    }

    private fun drawForRotateEmphasizedZoomIn(canvas: Canvas, localElapsedMs: Long) {
        canvas.save()
        val effectiveDuration = if (duration > 0) duration.toFloat() else 1000f
        val t = localElapsedMs.toFloat()
        var wobble = ((t - effectiveDuration) * 360f) / 4410f
        if (t <= effectiveDuration) {
            val progress = EmphasizedInterpolator.getInterpolation(t / effectiveDuration)
            val rect = scaledRect(progress)
            canvas.rotate(progress * 180f + wobble, destRect.centerX(), destRect.centerY())
            draw(canvas, rect, paint)
        } else {
            if (isMotionPaused) wobble = 0f
            canvas.rotate(wobble, destRect.centerX(), destRect.centerY())
            draw(canvas, destRect, paint)
        }
        canvas.restore()
    }

    private fun scaledRect(scale: Float): RectF {
        val halfW = (destRect.width() / 2f) * scale
        val halfH = (destRect.height() / 2f) * scale
        return RectF(
            destRect.centerX() - halfW,
            destRect.centerY() - halfH,
            destRect.centerX() + halfW,
            destRect.centerY() + halfH,
        )
    }
}
