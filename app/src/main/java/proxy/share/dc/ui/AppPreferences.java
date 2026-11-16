/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;

public final class AppPreferences {
    public static final String FILE = "ProxyPrefs";
    public static final int MOTION_EXPRESSIVE = 0;
    public static final int MOTION_STANDARD = 1;
    public static final int MOTION_REDUCED = 2;
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String TARGET_GATEWAY = "gateway";

    private AppPreferences() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int getHttpPort(Context c) {
        return p(c).getInt("http_port", 8080);
    }

    public static void setHttpPort(Context c, int v) {
        p(c).edit().putInt("http_port", v).apply();
    }

    public static int getSocksPort(Context c) {
        return p(c).getInt("socks_port", 1080);
    }

    public static void setSocksPort(Context c, int v) {
        p(c).edit().putInt("socks_port", v).apply();
    }

    public static String getLanguage(Context c) {
        return p(c).getString("app_lang", "fa");
    }

    public static void setLanguage(Context c, String v) {
        p(c).edit().putString("app_lang", v).apply();
    }

    public static String getThemeId(Context c) {
        return p(c).getString("theme_id", "mint");
    }

    public static void setThemeId(Context c, String v) {
        p(c).edit().putString("theme_id", v).apply();
    }

    public static String getThemeMode(Context c) {
        return p(c).getString("theme_mode", MODE_SYSTEM);
    }

    public static void setThemeMode(Context c, String v) {
        p(c).edit().putString("theme_mode", v).apply();
    }

    public static boolean isPureBlack(Context c) {
        return p(c).getBoolean("pure_black", false);
    }

    public static void setPureBlack(Context c, boolean v) {
        p(c).edit().putBoolean("pure_black", v).apply();
    }

    public static boolean isWallpaperColors(Context c) {
        return p(c).getBoolean("wallpaper_colors", false);
    }

    public static void setWallpaperColors(Context c, boolean v) {
        p(c).edit().putBoolean("wallpaper_colors", v).apply();
    }

    public static String getIconShape(Context c) {
        return p(c).getString("icon_shape", "squircle");
    }

    public static void setIconShape(Context c, String v) {
        p(c).edit().putString("icon_shape", v).apply();
    }

    public static float getFontScale(Context c) {
        return p(c).getFloat("font_scale", 1f);
    }

    public static void setFontScale(Context c, float v) {
        p(c).edit().putFloat("font_scale", v).apply();
    }

    public static int getMotionStyle(Context c) {
        return p(c).getInt("motion_style", MOTION_EXPRESSIVE);
    }

    public static void setMotionStyle(Context c, int v) {
        p(c).edit().putInt("motion_style", v).apply();
    }

    public static boolean isHaptics(Context c) {
        return p(c).getBoolean("haptics", true);
    }

    public static void setHaptics(Context c, boolean v) {
        p(c).edit().putBoolean("haptics", v).apply();
    }

    public static boolean isLiveBackground(Context c) {
        return p(c).getBoolean("live_background", true);
    }

    public static void setLiveBackground(Context c, boolean v) {
        p(c).edit().putBoolean("live_background", v).apply();
    }

    public static boolean isNavLabelsHidden(Context c) {
        return p(c).getBoolean("nav_hide_labels", false);
    }

    public static void setNavLabelsHidden(Context c, boolean v) {
        p(c).edit().putBoolean("nav_hide_labels", v).apply();
    }

    public static String getPingTarget(Context c) {
        return p(c).getString("ping_target", "1.1.1.1");
    }

    public static void setPingTarget(Context c, String v) {
        p(c).edit().putString("ping_target", v).apply();
    }

    public static String getPingCustom(Context c) {
        return p(c).getString("ping_custom", "");
    }

    public static void setPingCustom(Context c, String v) {
        p(c).edit().putString("ping_custom", v).apply();
    }

    public static int getPingInterval(Context c) {
        return p(c).getInt("ping_interval", 1000);
    }

    public static void setPingInterval(Context c, int v) {
        p(c).edit().putInt("ping_interval", v).apply();
    }

    public static int getLogLimit(Context c) {
        return p(c).getInt("log_limit", 500);
    }

    public static void setLogLimit(Context c, int v) {
        p(c).edit().putInt("log_limit", v).apply();
    }

    public static boolean isLogConnections(Context c) {
        return p(c).getBoolean("log_connections", false);
    }

    public static void setLogConnections(Context c, boolean v) {
        p(c).edit().putBoolean("log_connections", v).apply();
    }

    public static boolean isOnlineChecks(Context c) {
        return p(c).getBoolean("online_checks", true);
    }

    public static void setOnlineChecks(Context c, boolean v) {
        p(c).edit().putBoolean("online_checks", v).apply();
    }

    public static boolean isReduceMotion(Context c) {
        if (getMotionStyle(c) == MOTION_REDUCED) return true;
        try {
            return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Exception e) {
            return false;
        }
    }

    public static void performHaptic(View v) {
        if (isHaptics(v.getContext())) {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        }
    }
}
