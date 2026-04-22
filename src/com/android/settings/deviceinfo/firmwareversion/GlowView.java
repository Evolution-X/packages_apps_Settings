/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class GlowView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] mColors = new int[0];

    public GlowView(Context ctx) { super(ctx); }
    public GlowView(Context ctx, AttributeSet attrs) { super(ctx, attrs); }
    public GlowView(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
    }

    void setColors(int[] colors) {
        mColors = colors;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mColors.length == 0) return;

        final int   w      = getWidth();
        final int   h      = getHeight();
        final float radius = h * 1.8f;
        final int   n      = mColors.length;

        // Composite into an offscreen layer so DST_IN masking works correctly
        int saveCount = canvas.saveLayer(0, 0, w, h, null);

        // Draw one radial blob per colour stop
        for (int i = 0; i < n; i++) {
            float cx = (float) w * i / (n - 1);
            float cy = h * 0.5f;

            RadialGradient shader = new RadialGradient(
                    cx, cy, radius,
                    new int[]{
                        makeAlpha(mColors[i], 0.80f),
                        makeAlpha(mColors[i], 0.25f),
                        0x00000000
                    },
                    new float[]{ 0f, 0.50f, 1f },
                    Shader.TileMode.CLAMP);

            mPaint.setShader(shader);
            mPaint.setXfermode(null);
            canvas.drawRect(0, 0, w, h, mPaint);
        }

        // ---- Edge masking ------------------------------------------------
        // All four edges fade to alpha=0 so the glow dissolves into whatever
        // background color the host view has — works for both dark and light
        // Settings themes without knowing the actual background color.

        final int fadeTop    = Math.max(1, (int)(h * 0.50f)); // 50% top
        final int fadeBottom = Math.max(1, (int)(h * 0.50f)); // 50% bottom
        final int fadeSide   = Math.max(1, (int)(w * 0.22f)); // 22% left+right

        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        // Top: transparent → opaque
        mask.setShader(new LinearGradient(
                0, 0, 0, fadeTop,
                new int[]{ 0x00000000, 0xFF000000 }, null,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, fadeTop, mask);

        // Bottom: opaque → transparent
        mask.setShader(new LinearGradient(
                0, h - fadeBottom, 0, h,
                new int[]{ 0xFF000000, 0x00000000 }, null,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, h - fadeBottom, w, h, mask);

        // Left: transparent → opaque
        mask.setShader(new LinearGradient(
                0, 0, fadeSide, 0,
                new int[]{ 0x00000000, 0xFF000000 }, null,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, fadeSide, h, mask);

        // Right: opaque → transparent
        mask.setShader(new LinearGradient(
                w - fadeSide, 0, w, 0,
                new int[]{ 0xFF000000, 0x00000000 }, null,
                Shader.TileMode.CLAMP));
        canvas.drawRect(w - fadeSide, 0, w, h, mask);

        canvas.restoreToCount(saveCount);
    }

    private static int makeAlpha(int color, float alpha) {
        return (((int)(alpha * 255)) << 24) | (color & 0x00FFFFFF);
    }
}
