/* Copyright 2018 Dave Burke. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.example.pixelbotbrain;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Pixelbot face. Inspired by Eve :)
 */
public class PixelbotFace extends View {
    private static final int EYE_W = 700;
    private static final int EYE_H = (int) (EYE_W * 0.7);

    private static final int EYE_STATE_IDLE = 0;
    private static final int EYE_STATE_CLOSING = 1;
    private static final int EYE_STATE_OPENING = 2;
    private int mEyeState = EYE_STATE_IDLE;

    private Paint mPaint = new Paint();
    private long mStartTime = System.currentTimeMillis();
    private int mSubsequentBlinks = 0;

    public PixelbotFace(Context context) {
        super(context);
    }
    public PixelbotFace(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            this.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    public void blink() {
        mEyeState = EYE_STATE_CLOSING;
        mStartTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();
        Rect screenRect = new Rect(0, 0, w, h);

        // Make screen black
        mPaint.setColor(Color.BLACK);
        canvas.drawRect(screenRect, mPaint);

        // Draw eyes
        int x1 = w / 4;
        int x2 = w * 3 / 4;
        int y = h / 2;
        RectF leftRect = new RectF(x1 - EYE_W/2, y - EYE_H/2,
                x1 + EYE_W/2, y + EYE_H/2);
        RectF rightRect = new RectF(x2 - EYE_W/2, y - EYE_H/2,
                x2 + EYE_W/2, y + EYE_H/2);
        drawEye(leftRect, 10, canvas);
        drawEye(rightRect, -10, canvas);
    }

    private void drawEye(RectF rect, int angle, Canvas canvas) {
        canvas.rotate(+angle, rect.centerX(), rect.centerY());
        mPaint.setColor(0xFF378EDD);
        canvas.drawOval(rect, mPaint);
        mPaint.setColor(0xFF000000);
        mPaint.setStrokeWidth(6);
        for (int j = (int)rect.top; j < (int)rect.bottom; j += 16) {
            canvas.drawLine((int)rect.left, j, (int)rect.right, j, mPaint);
        }

        maybeAnimateBlink(canvas, new RectF(rect));
        canvas.rotate(-angle, rect.centerX(), rect.centerY());
    }

    private void maybeAnimateBlink(Canvas canvas, RectF rect) {
        if (mEyeState == EYE_STATE_IDLE) return;

        long elapsedTime = System.currentTimeMillis() - mStartTime;
        double fraction = elapsedTime / 175.0;

        if (fraction > 0.95) {
            if (mEyeState == EYE_STATE_CLOSING) {
                mEyeState = EYE_STATE_OPENING;
                mStartTime = System.currentTimeMillis();
                fraction = 0;
            } else if (mEyeState == EYE_STATE_OPENING) {
                mSubsequentBlinks++;
                if (Math.random() < 0.33 && mSubsequentBlinks < 2) {
                    mEyeState = EYE_STATE_CLOSING;
                    mStartTime = System.currentTimeMillis();
                    fraction = 0;
                } else {
                    mEyeState = EYE_STATE_IDLE;
                    mSubsequentBlinks = 0;
                    return;
                }
            }
        }

        mPaint.setColor(0xFF000000);
        int yOffset = -(int)rect.height();
        if (mEyeState == EYE_STATE_CLOSING) {
            yOffset += fraction * rect.height();
        } else {
            yOffset += (1.0 - fraction) * rect.height();
        }
        canvas.drawOval(rect.left, rect.top + yOffset, rect.right,
                rect.bottom + yOffset, mPaint);
        invalidate();
    }
}
