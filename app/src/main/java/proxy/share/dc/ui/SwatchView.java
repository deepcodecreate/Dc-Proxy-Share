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
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class SwatchView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int surface;
    private int container;
    private int primary;
    private int primaryContainer;
    private boolean selected;

    public SwatchView(Context context) {
        super(context);
    }

    public SwatchView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setColors(int[] c) {
        surface = c[0];
        container = c[1];
        primary = c[2];
        primaryContainer = c[3];
        invalidate();
    }

    public void setSelectedTile(boolean s) {
        selected = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float stroke = UiKit.dp(getContext(), 2.5f);
        float corner = UiKit.dp(getContext(), 20);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(surface);
        rect.set(stroke, stroke, w - stroke, h - stroke);
        canvas.drawRoundRect(rect, corner, corner, paint);
        paint.setColor(container);
        rect.set(w * 0.16f, h * 0.18f, w * 0.84f, h * 0.46f);
        canvas.drawRoundRect(rect, corner / 2f, corner / 2f, paint);
        paint.setColor(primaryContainer);
        rect.set(w * 0.16f, h * 0.54f, w * 0.52f, h * 0.82f);
        canvas.drawRoundRect(rect, corner / 2f, corner / 2f, paint);
        paint.setColor(primary);
        canvas.drawCircle(w * 0.70f, h * 0.68f, h * 0.14f, paint);
        if (selected) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(UiKit.primary(getContext()));
            rect.set(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f);
            canvas.drawRoundRect(rect, corner, corner, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, UiKit.dp(getContext(), 0.5f)));
            paint.setColor(UiKit.outlineVariant(getContext()));
            rect.set(stroke, stroke, w - stroke, h - stroke);
            canvas.drawRoundRect(rect, corner, corner, paint);
        }
    }
}
