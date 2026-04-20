/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.hardwareinfo;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Material 3 inset focus ring for the View-based model page, mirroring the ring Compose Material3
 * 1.5.0-alpha23 draws for {@code RippleThemeConfiguration.Focus.InsetRing}: an inner stroke
 * (3dp, inset 1dp) painted first and an outer stroke (2dp, inset 0) painted over it. Both widths
 * and insets scale with a 0..1 progress, so the ring grows in and out with the focus spring.
 *
 * <p>The ring follows the live corner radii of the view it is the foreground of, so it stays
 * flush with the shape while that shape is morphing.
 */
final class FocusRingDrawable extends Drawable {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mRect = new RectF();
    private final float[] mScratch = new float[8];
    private final float mOuterWidth;
    private final float mOuterInset;
    private final float mInnerWidth;
    private final float mInnerInset;
    private final int mOuterColor;
    private final int mInnerColor;

    private float[] mRadii = new float[8];
    private float mProgress;

    FocusRingDrawable(float outerWidth, float outerInset, int outerColor,
            float innerWidth, float innerInset, int innerColor) {
        mOuterWidth = outerWidth;
        mOuterInset = outerInset;
        mOuterColor = outerColor;
        mInnerWidth = innerWidth;
        mInnerInset = innerInset;
        mInnerColor = innerColor;
        mPaint.setStyle(Paint.Style.STROKE);
    }

    /** The array is read on every draw, so the owner may keep mutating it in place. */
    void setRadii(float[] radii) {
        mRadii = radii;
        invalidateSelf();
    }

    void setProgress(float progress) {
        mProgress = progress;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        if (mProgress <= 0f) return;
        // Inner first, outer on top, same order as Compose.
        drawStroke(canvas, mInnerInset * mProgress, mInnerWidth * mProgress, mInnerColor);
        drawStroke(canvas, mOuterInset * mProgress, mOuterWidth * mProgress, mOuterColor);
    }

    private void drawStroke(Canvas canvas, float inset, float width, int color) {
        if (width < 0.5f) return;
        // Strokes are centered on the path; offset by half the width so the outer edge of the
        // stroke lines up with the view edge plus the inset.
        final float offset = inset + width / 2f;
        mRect.set(getBounds());
        mRect.inset(offset, offset);
        if (mRect.width() <= 0f || mRect.height() <= 0f) return;
        for (int k = 0; k < 8; k++) mScratch[k] = Math.max(0f, mRadii[k] - offset);
        mPath.reset();
        mPath.addRoundRect(mRect, mScratch, Path.Direction.CW);
        mPaint.setStrokeWidth(width);
        mPaint.setColor(color);
        canvas.drawPath(mPath, mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
