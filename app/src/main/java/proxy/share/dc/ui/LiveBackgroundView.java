/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;

public class LiveBackgroundView extends View {
    private static final float[] BX = {0.15f, 0.9f, 0.5f};
    private static final float[] BY = {0.12f, 0.45f, 0.95f};
    private static final float[] SPEED = {0.00031f, 0.00023f, 0.00037f};
    private static final float[] PHASE = {0f, 2.1f, 4.2f};
    private final Paint[] paints = {new Paint(Paint.ANTI_ALIAS_FLAG), new Paint(Paint.ANTI_ALIAS_FLAG), new Paint(Paint.ANTI_ALIAS_FLAG)};
    private boolean animate;
    private boolean scrollPaused;
    private boolean running;
    private float radius;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, 50);
        }
    };

    public LiveBackgroundView(Context context) {
        super(context);
        setWillNotDraw(false);
        refreshMode();
    }

    private void refreshMode() {
        Context c = getContext();
        boolean enabled = AppPreferences.isLiveBackground(c);
        setVisibility(enabled ? VISIBLE : GONE);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        boolean saver = pm != null && pm.isPowerSaveMode();
        animate = enabled && !AppPreferences.isReduceMotion(c) && !saver;
    }

    private void rebuildShaders() {
        Context c = getContext();
        boolean dark = ThemeManager.isDark(c);
        boolean black = dark && AppPreferences.isPureBlack(c);
        int alpha = black ? 0x30 : (dark ? 0x3C : 0x2A);
        int[] colors = {
                UiKit.primary(c),
                UiKit.color(c, "colorTertiary", 0xFFFFAA00),
                UiKit.color(c, "colorSecondary", 0xFF88AACC)
        };
        radius = Math.min(getWidth(), getHeight()) * 0.62f;
        if (radius <= 0) return;
        for (int i = 0; i < 3; i++) {
            paints[i].setShader(new RadialGradient(0, 0, radius,
                    UiKit.withAlpha(colors[i], alpha), UiKit.withAlpha(colors[i], 0), Shader.TileMode.CLAMP));
        }
    }

    public void setScrollPaused(boolean paused) {
        if (scrollPaused == paused) return;
        scrollPaused = paused;
        updateRunning();
    }

    private void updateRunning() {
        boolean should = animate && !scrollPaused && isAttachedToWindow() && getVisibility() == VISIBLE;
        if (should == running) return;
        running = should;
        if (running) post(tick);
        else removeCallbacks(tick);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshMode();
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildShaders();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (radius <= 0) return;
        long t = animate ? SystemClock.uptimeMillis() : 0;
        float w = getWidth();
        float h = getHeight();
        for (int i = 0; i < 3; i++) {
            float x = w * (BX[i] + 0.12f * (float) Math.sin(t * SPEED[i] + PHASE[i]));
            float y = h * (BY[i] + 0.08f * (float) Math.cos(t * SPEED[i] * 1.3f + PHASE[i]));
            canvas.save();
            canvas.translate(x, y);
            canvas.drawCircle(0, 0, radius, paints[i]);
            canvas.restore();
        }
    }
}
