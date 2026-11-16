/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.ui;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public final class MotionHelper {
    private MotionHelper() {
    }

    public static int getDuration(Context c, int standardMs, int expressiveMs) {
        if (AppPreferences.isReduceMotion(c)) return 0;
        return AppPreferences.getMotionStyle(c) == AppPreferences.MOTION_EXPRESSIVE ? expressiveMs : standardMs;
    }

    public static TimeInterpolator interpolator(Context c) {
        if (AppPreferences.getMotionStyle(c) == AppPreferences.MOTION_EXPRESSIVE) {
            return new OvershootInterpolator(0.55f);
        }
        return new DecelerateInterpolator(1.7f);
    }

    public static void animateEntrance(View v, int index) {
        Context c = v.getContext();
        int d = getDuration(c, 280, 460);
        if (d == 0) return;
        float rise = UiKit.dp(c, 18);
        v.setAlpha(0f);
        v.setScaleX(0.88f);
        v.setScaleY(0.88f);
        v.setTranslationY(rise);
        v.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setStartDelay(45L * Math.min(index, 10))
                .setDuration(d)
                .setInterpolator(interpolator(c))
                .start();
    }

    public static void bounce(View v) {
        Context c = v.getContext();
        int d = getDuration(c, 300, 460);
        if (d == 0) return;
        v.setScaleX(0.72f);
        v.setScaleY(0.72f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(d)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();
    }

    public static void crossfadeIn(View v) {
        int d = getDuration(v.getContext(), 180, 260);
        if (d == 0) {
            v.setAlpha(1f);
            return;
        }
        v.setAlpha(0f);
        v.setTranslationY(UiKit.dp(v.getContext(), 8));
        v.animate().alpha(1f).translationY(0f).setDuration(d)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }
}
