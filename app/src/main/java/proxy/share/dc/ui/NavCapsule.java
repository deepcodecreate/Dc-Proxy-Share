/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class NavCapsule extends FrameLayout {
    public interface OnSelectListener {
        void onSelect(int index);
    }

    private static final int LABELED_HEIGHT = 62;
    private static final int COMPACT_HEIGHT = 64;
    private final LinearLayout row;
    private final LinearLayout[] items;
    private final ImageView[] icons;
    private final TextView[] labels;
    private final boolean compact;
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF target = new RectF();
    private final int fromIndex;
    private int selected;
    private boolean laidOut;
    private ValueAnimator anim;
    private OnSelectListener listener;
    private final int selectedColor;
    private final int normalColor;

    public NavCapsule(Context context, String[] iconNames, int[] labelRes, int selected, int fromIndex) {
        super(context);
        this.selected = selected;
        this.fromIndex = fromIndex;
        this.compact = AppPreferences.isNavLabelsHidden(context);
        setWillNotDraw(false);
        setElevation(UiKit.dp(context, 10));
        setClipToPadding(false);

        selectedColor = UiKit.primary(context);
        normalColor = UiKit.onSurfaceVariant(context);
        pill.setColor(UiKit.color(context, "colorSecondaryContainer", 0xFF334455));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiKit.withAlpha(UiKit.color(context, "colorSurfaceContainer", 0xFF222222), 0xEB));
        bg.setCornerRadius(UiKit.dp(context, 48));
        bg.setStroke(Math.max(1, UiKit.dp(context, 0.5f)), UiKit.withAlpha(UiKit.outlineVariant(context), 0x99));
        setBackground(bg);

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int n = iconNames.length;
        items = new LinearLayout[n];
        icons = new ImageView[n];
        labels = new TextView[n];
        int iconSize = UiKit.dp(context, compact ? 22 : 20);
        for (int i = 0; i < n; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription(context.getString(labelRes[i]));

            ImageView icon = new ImageView(context);
            icon.setImageDrawable(UiKit.icon(context, iconNames[i]));
            item.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

            TextView label = new TextView(context);
            label.setText(labelRes[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = UiKit.dp(context, 3);
            item.addView(label, llp);
            if (compact) label.setVisibility(GONE);

            item.setOnClickListener(v -> {
                if (index == this.selected) return;
                AppPreferences.performHaptic(v);
                select(index, true);
                if (listener != null) listener.onSelect(index);
            });
            row.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            items[i] = item;
            icons[i] = icon;
            labels[i] = label;
        }
        applyColors();
    }

    public static int heightDp(Context c) {
        return AppPreferences.isNavLabelsHidden(c) ? COMPACT_HEIGHT : LABELED_HEIGHT;
    }

    public void setOnSelectListener(OnSelectListener l) {
        listener = l;
    }

    public void applyBottomInset(int inset) {
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams f = (FrameLayout.LayoutParams) lp;
            f.bottomMargin = UiKit.dp(getContext(), 12) + inset;
            setLayoutParams(f);
        }
    }

    private void applyColors() {
        for (int i = 0; i < items.length; i++) {
            int c = i == selected ? selectedColor : normalColor;
            icons[i].setColorFilter(c);
            labels[i].setTextColor(c);
        }
    }

    private void computeTarget(int index, RectF out) {
        View it = items[index];
        float l = it.getLeft();
        float r = it.getRight();
        float t = it.getTop();
        float b = it.getBottom();
        if (compact) {
            float w = Math.min(UiKit.dp(getContext(), 64), r - l);
            float h = UiKit.dp(getContext(), 32);
            float cx = (l + r) / 2f;
            float cy = (t + b) / 2f;
            out.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        } else {
            out.set(l + UiKit.dp(getContext(), 6), t + UiKit.dp(getContext(), 5),
                    r - UiKit.dp(getContext(), 6), b - UiKit.dp(getContext(), 5));
        }
    }

    public void select(int index, boolean animate) {
        int previous = selected;
        selected = index;
        applyColors();
        MotionHelper.bounce(icons[index]);
        if (laidOut) animateTo(index, animate && previous != index);
    }

    private void animateTo(int index, boolean animate) {
        computeTarget(index, target);
        int d = MotionHelper.getDuration(getContext(), 260, 360);
        if (anim != null) anim.cancel();
        if (!animate || d == 0) {
            rect.set(target);
            invalidate();
            return;
        }
        final float sl = rect.left;
        final float sr = rect.right;
        final float st = rect.top;
        final float sb = rect.bottom;
        final float tl = target.left;
        final float tr = target.right;
        final float tt = target.top;
        final float tb = target.bottom;
        final boolean movingEnd = (tl + tr) / 2f >= (sl + sr) / 2f;
        final DecelerateInterpolator lead = new DecelerateInterpolator(1.6f);
        final AccelerateDecelerateInterpolator trail = new AccelerateDecelerateInterpolator();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(d);
        anim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            float fl = movingEnd ? trail.getInterpolation(f) : lead.getInterpolation(f);
            float fr = movingEnd ? lead.getInterpolation(f) : trail.getInterpolation(f);
            rect.left = sl + (tl - sl) * fl;
            rect.right = sr + (tr - sr) * fr;
            rect.top = st + (tt - st) * f;
            rect.bottom = sb + (tb - sb) * f;
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!laidOut) {
            laidOut = true;
            if (fromIndex >= 0 && fromIndex < items.length && fromIndex != selected) {
                computeTarget(fromIndex, rect);
                post(() -> animateTo(selected, true));
            } else {
                computeTarget(selected, rect);
            }
        } else if (changed) {
            computeTarget(selected, rect);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (rect.width() > 0) {
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, pill);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
