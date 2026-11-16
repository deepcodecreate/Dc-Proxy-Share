/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class SparklineView extends View {
    private float[] a = new float[0];
    private float[] b = new float[0];
    private int slots = 60;
    private boolean gaps;
    private String maxLabel = "";
    private final Paint lineA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineB = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pathA = new Path();
    private final Path pathB = new Path();
    private final Path fillPath = new Path();
    private int colorA;
    private int colorB;
    private float pad;

    public SparklineView(Context context) {
        super(context);
        init();
    }

    public SparklineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        Context c = getContext();
        pad = UiKit.dp(c, 6);
        colorA = UiKit.primary(c);
        colorB = UiKit.color(c, "colorTertiary", 0xFFFFAA00);
        lineA.setStyle(Paint.Style.STROKE);
        lineA.setStrokeWidth(UiKit.dp(c, 2));
        lineA.setStrokeCap(Paint.Cap.ROUND);
        lineA.setStrokeJoin(Paint.Join.ROUND);
        lineB.setStyle(Paint.Style.STROKE);
        lineB.setStrokeWidth(UiKit.dp(c, 2));
        lineB.setStrokeCap(Paint.Cap.ROUND);
        lineB.setStrokeJoin(Paint.Join.ROUND);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(Math.max(1, UiKit.dp(c, 0.5f)));
        grid.setColor(UiKit.withAlpha(UiKit.outlineVariant(c), 0x99));
        label.setTextSize(UiKit.dp(c, 10));
        label.setColor(UiKit.onSurfaceVariant(c));
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeWidth(UiKit.dp(c, 2));
        mark.setStrokeCap(Paint.Cap.ROUND);
        mark.setColor(UiKit.error(c));
        lineA.setColor(colorA);
        lineB.setColor(colorB);
    }

    public void setGapsOnNegative(boolean gaps) {
        this.gaps = gaps;
    }

    public void setMaxLabel(String s) {
        maxLabel = s == null ? "" : s;
    }

    public void setColors(int a, int b) {
        colorA = a;
        colorB = b;
        lineA.setColor(a);
        lineB.setColor(b);
        rebuildShader();
        invalidate();
    }

    public void setSeries(float[] a, float[] b) {
        this.a = a == null ? new float[0] : a;
        this.b = b == null ? new float[0] : b;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildShader();
    }

    private void rebuildShader() {
        if (getHeight() <= 0) return;
        fillA.setShader(new LinearGradient(0, 0, 0, getHeight(),
                UiKit.withAlpha(colorA, 0x59), UiKit.withAlpha(colorA, 0x00), Shader.TileMode.CLAMP));
    }

    private void buildLine(float[] data, Path out, float w, float h, float max, boolean close) {
        out.reset();
        if (data.length == 0) return;
        float step = (w - pad * 2) / (slots - 1);
        float baseY = h - pad;
        float usable = h - pad * 2;
        float prevX = 0;
        float prevY = 0;
        boolean open = false;
        for (int i = 0; i < data.length; i++) {
            float x = w - pad - (data.length - 1 - i) * step;
            float v = data[i];
            if (v < 0) {
                open = false;
                continue;
            }
            float y = baseY - Math.min(1f, v / max) * usable;
            if (!open) {
                out.moveTo(x, y);
                open = true;
            } else {
                out.quadTo(prevX, prevY, (prevX + x) / 2f, (prevY + y) / 2f);
                if (i == data.length - 1) out.lineTo(x, y);
            }
            prevX = x;
            prevY = y;
        }
        if (close && !gaps && data.length > 1) {
            fillPath.reset();
            fillPath.addPath(out);
            fillPath.lineTo(w - pad, baseY);
            fillPath.lineTo(w - pad - (data.length - 1) * step, baseY);
            fillPath.close();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        for (int i = 0; i < 3; i++) {
            float y = pad + (h - pad * 2) * i / 2f;
            canvas.drawLine(pad, y, w - pad, y, grid);
        }
        float max = 1f;
        for (float v : a) max = Math.max(max, v);
        for (float v : b) max = Math.max(max, v);
        max *= 1.1f;
        fillPath.reset();
        buildLine(a, pathA, w, h, max, true);
        buildLine(b, pathB, w, h, max, false);
        if (!gaps && a.length > 1) canvas.drawPath(fillPath, fillA);
        if (b.length > 1) canvas.drawPath(pathB, lineB);
        if (a.length > 1) canvas.drawPath(pathA, lineA);
        if (gaps) {
            float step = (w - pad * 2) / (slots - 1);
            for (int i = 0; i < a.length; i++) {
                if (a[i] < 0) {
                    float x = w - pad - (a.length - 1 - i) * step;
                    canvas.drawLine(x, h - pad - UiKit.dp(getContext(), 10), x, h - pad, mark);
                }
            }
        }
        if (!maxLabel.isEmpty()) {
            canvas.drawText(maxLabel, pad * 1.5f, pad + label.getTextSize(), label);
        }
    }
}
