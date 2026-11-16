/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.ui;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class ShapeView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private String shapeId = "squircle";
    private Drawable icon;
    private float iconFraction = 0.5f;
    private boolean idleSpin;
    private ObjectAnimator spin;

    public ShapeView(Context context) {
        super(context);
        init();
    }

    public ShapeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        fill.setStyle(Paint.Style.FILL);
        shapeId = AppPreferences.getIconShape(getContext());
        fill.setColor(UiKit.color(getContext(), "colorPrimaryContainer", 0xFF444444));
    }

    public void setFillColor(int color) {
        fill.setColor(color);
        invalidate();
    }

    public void setIcon(Drawable d, int tint) {
        icon = d == null ? null : d.mutate();
        if (icon != null) icon.setTint(tint);
        layoutIcon();
        invalidate();
    }

    public void setIconFraction(float f) {
        iconFraction = f;
        layoutIcon();
        invalidate();
    }

    public void setIdleSpin(boolean enabled) {
        idleSpin = enabled;
        updateSpin();
    }

    private void updateSpin() {
        if (spin != null) {
            spin.cancel();
            spin = null;
        }
        if (idleSpin && isAttachedToWindow() && !AppPreferences.isReduceMotion(getContext())) {
            spin = ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 360f);
            spin.setDuration(26000);
            spin.setInterpolator(new LinearInterpolator());
            spin.setRepeatCount(ObjectAnimator.INFINITE);
            spin.start();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSpin();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (spin != null) {
            spin.cancel();
            spin = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
        layoutIcon();
    }

    private void rebuild() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        IconShapes.build(path, shapeId, w / 2f, h / 2f, Math.min(w, h) / 2f);
    }

    private void layoutIcon() {
        if (icon == null || getWidth() <= 0) return;
        int size = Math.round(Math.min(getWidth(), getHeight()) * iconFraction);
        int l = (getWidth() - size) / 2;
        int t = (getHeight() - size) / 2;
        icon.setBounds(l, t, l + size, t + size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(path, fill);
        if (icon != null) icon.draw(canvas);
    }
}
