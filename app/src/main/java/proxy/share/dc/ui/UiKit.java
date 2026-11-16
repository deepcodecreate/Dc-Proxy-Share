/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.content.res.AppCompatResources;
import com.google.android.material.color.MaterialColors;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import proxy.share.dc.R;

public final class UiKit {
    private static final ConcurrentHashMap<String, Integer> ATTR_IDS = new ConcurrentHashMap<>();

    private UiKit() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int color(Context c, String attrName, int fallback) {
        Integer id = ATTR_IDS.get(attrName);
        if (id == null) {
            id = c.getResources().getIdentifier(attrName, "attr", c.getPackageName());
            ATTR_IDS.put(attrName, id);
        }
        if (id == 0) return fallback;
        return MaterialColors.getColor(c, id, fallback);
    }

    public static int primary(Context c) {
        return color(c, "colorPrimary", Color.GREEN);
    }

    public static int onSurface(Context c) {
        return color(c, "colorOnSurface", Color.WHITE);
    }

    public static int onSurfaceVariant(Context c) {
        return color(c, "colorOnSurfaceVariant", Color.GRAY);
    }

    public static int outlineVariant(Context c) {
        return color(c, "colorOutlineVariant", Color.DKGRAY);
    }

    public static int success(Context c) {
        return color(c, "colorSuccess", Color.GREEN);
    }

    public static int warning(Context c) {
        return color(c, "colorWarning", Color.YELLOW);
    }

    public static int error(Context c) {
        return color(c, "colorError", Color.RED);
    }

    public static ColorStateList chipIconTint(Context c) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        int[] colors = {color(c, "colorOnSecondaryContainer", Color.WHITE), onSurfaceVariant(c)};
        return new ColorStateList(states, colors);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static Drawable icon(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        if (id == 0) id = R.drawable.ic_circle;
        return AppCompatResources.getDrawable(c, id);
    }

    public static void copy(Context c, String label, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(c, c.getString(R.string.toast_copied, text), Toast.LENGTH_SHORT).show();
        }
    }

    public static void addSpaced(LinearLayout parent, android.view.View child, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(parent.getContext(), topDp);
        parent.addView(child, lp);
    }

    public static String ms(float v) {
        if (v < 0) return "—";
        if (v < 10) return String.format(Locale.US, "%.1f ms", v);
        return String.format(Locale.US, "%d ms", Math.round(v));
    }

    public static String duration(long millis) {
        long s = Math.max(0, millis / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }

    public static String flag(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) return "";
        int a = Character.codePointAt(countryCode.toUpperCase(Locale.US), 0) - 'A' + 0x1F1E6;
        int b = Character.codePointAt(countryCode.toUpperCase(Locale.US), 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(a)) + new String(Character.toChars(b));
    }
}
