/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.graphics.PathParser;

import com.android.settings.R;

/**
 * Draws the Evolution X wordmark with a K-pop holographic treatment: a pink,
 * violet and cyan gradient sweeping across the outline, a soft neon glow with
 * a chromatic fringe, a periodic shine streak and a few twinkling sparkles.
 *
 * All geometry is expressed in the 1080x240 viewport of
 * ic_evolution_logo_outline and scaled to fit the view (centerInside).
 */
public class GlowView extends View {
    private static final float VIEWPORT_W = 1080f;
    private static final float VIEWPORT_H = 240f;

    private static final int PINK   = 0xFFFF2E9A;
    private static final int VIOLET = 0xFF8B5CF6;
    private static final int CYAN   = 0xFF22D3EE;

    // Gradient vector (viewport units). One sweep moves it by its own length,
    // so the repeating gradient loops seamlessly.
    private static final float GRAD_X1 = 190f;
    private static final float GRAD_Y1 = 60f;
    private static final float GRAD_X2 = 890f;
    private static final float GRAD_Y2 = 180f;
    private static final long  SWEEP_MS = 5000L;

    private static final float MAIN_STROKE   = 2.4f;
    private static final int   FILL_ALPHA    = 38;   // 15%
    private static final float GLOW_STROKE   = 9f;
    private static final float GLOW_SIGMA    = 7f;
    private static final int   GLOW_ALPHA    = 140;  // 55%
    private static final float FRINGE_STROKE = 3f;
    private static final float FRINGE_SIGMA  = 2.5f;
    private static final float FRINGE_SHIFT  = 2f;
    private static final int   FRINGE_ALPHA  = 128;  // 50%

    // Shine: a skewed white band crossing the logo, then a pause.
    private static final long  SHINE_MS     = 5000L;
    private static final float SHINE_ACTIVE = 0.3f;  // fraction of the period spent moving
    private static final float SHINE_FROM   = 120f;
    private static final float SHINE_TO     = 900f;
    private static final float SHINE_WIDTH  = 90f;
    private static final float SHINE_SKEW   = -0.36397f; // tan(-20 deg)
    private static final float SHINE_REACH  = 5000f;
    private static final int   SHINE_FILL_ALPHA = 64;    // 25%

    // Sparkles: x, y, size (viewport units), then twinkle start/peak/end as a
    // fraction of SPARKLE_MS.
    private static final long SPARKLE_MS = 3200L;
    private static final float[][] SPARKLES = {
        { 880f,  66f, 9f, 0.00f, 0.20f, 0.40f },
        { 792f, 176f, 7f, 0.35f, 0.50f, 0.65f },
        { 548f,  80f, 6f, 0.60f, 0.75f, 0.90f },
    };

    private final Path mLogoUnits = new Path();
    private final Path mLogo = new Path();
    private final Path mStar = new Path();
    private final Matrix mMatrix = new Matrix();
    private final Matrix mShaderMatrix = new Matrix();

    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCyanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPinkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShineFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShineStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final LinearGradient mBand = new LinearGradient(
            0f, 0f, SHINE_WIDTH, 0f,
            new int[]{ 0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF }, null,
            Shader.TileMode.CLAMP);
    private LinearGradient mHolo;

    private float mScale;
    private float mOx;
    private float mOy;
    private boolean mAnimate = true;
    private long mStartTime;

    public GlowView(Context ctx) {
        super(ctx);
        init(ctx);
    }

    public GlowView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init(ctx);
    }

    public GlowView(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
        init(ctx);
    }

    private void init(Context ctx) {
        for (String data : ctx.getResources().getStringArray(R.array.evolution_logo_paths)) {
            mLogoUnits.addPath(PathParser.createPathFromPathData(data));
        }

        mStar.moveTo(0f, -1f);
        mStar.quadTo(0f, 0f, 1f, 0f);
        mStar.quadTo(0f, 0f, 0f, 1f);
        mStar.quadTo(0f, 0f, -1f, 0f);
        mStar.quadTo(0f, 0f, 0f, -1f);
        mStar.close();

        mGlowPaint.setStyle(Paint.Style.STROKE);
        mGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        mGlowPaint.setAlpha(GLOW_ALPHA);

        mCyanPaint.setStyle(Paint.Style.STROKE);
        mCyanPaint.setStrokeJoin(Paint.Join.ROUND);
        mCyanPaint.setColor(CYAN);
        mCyanPaint.setAlpha(FRINGE_ALPHA);

        mPinkPaint.setStyle(Paint.Style.STROKE);
        mPinkPaint.setStrokeJoin(Paint.Join.ROUND);
        mPinkPaint.setColor(PINK);
        mPinkPaint.setAlpha(FRINGE_ALPHA);

        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setAlpha(FILL_ALPHA);

        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        mStrokePaint.setStrokeCap(Paint.Cap.ROUND);

        mShineFill.setStyle(Paint.Style.FILL);
        mShineFill.setColor(0xFFFFFFFF);
        mShineFill.setAlpha(SHINE_FILL_ALPHA);

        mShineStroke.setStyle(Paint.Style.STROKE);
        mShineStroke.setStrokeJoin(Paint.Join.ROUND);
        mShineStroke.setColor(0xFFFFFFFF);

        mBandPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        mSparklePaint.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Respect "remove animations": draw one still frame instead of looping.
        mAnimate = ValueAnimator.areAnimatorsEnabled();
        mStartTime = SystemClock.uptimeMillis();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mScale = Math.min(w / VIEWPORT_W, h / VIEWPORT_H);
        mOx = (w - VIEWPORT_W * mScale) / 2f;
        mOy = (h - VIEWPORT_H * mScale) / 2f;

        mMatrix.setScale(mScale, mScale);
        mMatrix.postTranslate(mOx, mOy);
        mLogoUnits.transform(mMatrix, mLogo);

        final BlurMaskFilter glowBlur = new BlurMaskFilter(
                blurRadius(GLOW_SIGMA * mScale), BlurMaskFilter.Blur.NORMAL);
        final BlurMaskFilter fringeBlur = new BlurMaskFilter(
                blurRadius(FRINGE_SIGMA * mScale), BlurMaskFilter.Blur.NORMAL);

        mGlowPaint.setStrokeWidth(GLOW_STROKE * mScale);
        mGlowPaint.setMaskFilter(glowBlur);
        mCyanPaint.setStrokeWidth(FRINGE_STROKE * mScale);
        mCyanPaint.setMaskFilter(fringeBlur);
        mPinkPaint.setStrokeWidth(FRINGE_STROKE * mScale);
        mPinkPaint.setMaskFilter(fringeBlur);
        mStrokePaint.setStrokeWidth(MAIN_STROKE * mScale);
        mShineStroke.setStrokeWidth(MAIN_STROKE * mScale);

        mHolo = new LinearGradient(
                mOx + GRAD_X1 * mScale, mOy + GRAD_Y1 * mScale,
                mOx + GRAD_X2 * mScale, mOy + GRAD_Y2 * mScale,
                new int[]{ PINK, VIOLET, CYAN, PINK },
                new float[]{ 0f, 0.33f, 0.66f, 1f },
                Shader.TileMode.REPEAT);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mHolo == null || mScale <= 0f) return;

        final long t = mAnimate ? SystemClock.uptimeMillis() - mStartTime : 0L;

        // Sweep the gradient along its own vector, which loops seamlessly.
        final float sweep = (t % SWEEP_MS) / (float) SWEEP_MS;
        mShaderMatrix.setTranslate(
                sweep * (GRAD_X2 - GRAD_X1) * mScale,
                sweep * (GRAD_Y2 - GRAD_Y1) * mScale);
        mHolo.setLocalMatrix(mShaderMatrix);
        mGlowPaint.setShader(mHolo);
        mFillPaint.setShader(mHolo);
        mStrokePaint.setShader(mHolo);

        // Neon glow, then the cyan/pink chromatic fringe
        canvas.drawPath(mLogo, mGlowPaint);

        canvas.save();
        canvas.translate(-FRINGE_SHIFT * mScale, 0f);
        canvas.drawPath(mLogo, mCyanPaint);
        canvas.restore();

        canvas.save();
        canvas.translate(FRINGE_SHIFT * mScale, 0f);
        canvas.drawPath(mLogo, mPinkPaint);
        canvas.restore();

        // Glassy fill and gradient outline
        canvas.drawPath(mLogo, mFillPaint);
        canvas.drawPath(mLogo, mStrokePaint);

        drawShine(canvas, t);
        drawSparkles(canvas, t);

        if (mAnimate) postInvalidateOnAnimation();
    }

    private void drawShine(Canvas canvas, long t) {
        final float p = (t % SHINE_MS) / (float) SHINE_MS;
        if (p >= SHINE_ACTIVE) return;

        final float bx = SHINE_FROM + (SHINE_TO - SHINE_FROM) * (p / SHINE_ACTIVE);

        final int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawPath(mLogo, mShineFill);
        canvas.drawPath(mLogo, mShineStroke);

        // Keep only the part of the white logo that sits under the band
        canvas.save();
        canvas.translate(mOx, mOy);
        canvas.scale(mScale, mScale);
        canvas.skew(SHINE_SKEW, 0f);
        mShaderMatrix.setTranslate(bx, 0f);
        mBand.setLocalMatrix(mShaderMatrix);
        mBandPaint.setShader(mBand);
        canvas.drawRect(-SHINE_REACH, -SHINE_REACH, SHINE_REACH, SHINE_REACH, mBandPaint);
        canvas.restore();
        canvas.restoreToCount(layer);
    }

    private void drawSparkles(Canvas canvas, long t) {
        final float p = (t % SPARKLE_MS) / (float) SPARKLE_MS;
        for (float[] s : SPARKLES) {
            final float k = pulse(p, s[3], s[4], s[5]) * s[2] * mScale;
            if (k <= 0f) continue;
            canvas.save();
            canvas.translate(mOx + s[0] * mScale, mOy + s[1] * mScale);
            canvas.scale(k, k);
            canvas.drawPath(mStar, mSparklePaint);
            canvas.restore();
        }
    }

    // 0 at start and end, 1 at peak, linear in between
    private static float pulse(float p, float start, float peak, float end) {
        if (p <= start || p >= end) return 0f;
        return p < peak ? (p - start) / (peak - start) : (end - p) / (end - peak);
    }

    // BlurMaskFilter takes a radius, Skia converts it with sigma = 0.57735 * r + 0.5
    private static float blurRadius(float sigma) {
        return Math.max(1f, (sigma - 0.5f) / 0.57735f);
    }
}
