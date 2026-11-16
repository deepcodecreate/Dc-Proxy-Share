/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import proxy.share.dc.R;

public final class ThemeManager {
    public static final class ThemeDef {
        public final String id;
        public final int labelRes;
        public final int darkStyle;
        public final int lightStyle;
        public final int[] dark;
        public final int[] light;

        ThemeDef(String id, int labelRes, int darkStyle, int lightStyle, int[] dark, int[] light) {
            this.id = id;
            this.labelRes = labelRes;
            this.darkStyle = darkStyle;
            this.lightStyle = lightStyle;
            this.dark = dark;
            this.light = light;
        }
    }

    public static final ThemeDef[] THEMES = {
        new ThemeDef("mint", R.string.theme_mint, R.style.Theme_App_Mint_Dark, R.style.Theme_App_Mint_Light,
            new int[]{0xFF111512, 0xFF1D231E, 0xFFA8E6B0, 0xFF23672C},
            new int[]{0xFFF7FBF7, 0xFFEAF3EB, 0xFF1C9C2D, 0xFFCEF8D4}),
        new ThemeDef("ocean", R.string.theme_ocean, R.style.Theme_App_Ocean_Dark, R.style.Theme_App_Ocean_Light,
            new int[]{0xFF111415, 0xFF1D2123, 0xFFA4D7EA, 0xFF1E576B},
            new int[]{0xFFF7FAFB, 0xFFEAF1F3, 0xFF157DA2, 0xFFCCEEFA}),
        new ThemeDef("sky", R.string.theme_sky, R.style.Theme_App_Sky_Dark, R.style.Theme_App_Sky_Light,
            new int[]{0xFF111315, 0xFF1D1F23, 0xFFA2C2EC, 0xFF1C3F6E},
            new int[]{0xFFF7F8FB, 0xFFEAEEF3, 0xFF1152A6, 0xFFCBE0FB}),
        new ThemeDef("dusk", R.string.theme_dusk, R.style.Theme_App_Dusk_Dark, R.style.Theme_App_Dusk_Light,
            new int[]{0xFF131115, 0xFF1F1D23, 0xFFBFA8E6, 0xFF3C2367},
            new int[]{0xFFF8F7FB, 0xFFEDEAF3, 0xFF4B1C9C, 0xFFDDCEF8}),
        new ThemeDef("rose", R.string.theme_rose, R.style.Theme_App_Rose_Dark, R.style.Theme_App_Rose_Light,
            new int[]{0xFF151112, 0xFF231D1F, 0xFFE9A5BC, 0xFF6A2038},
            new int[]{0xFFFBF7F8, 0xFFF3EAED, 0xFFA11745, 0xFFF9CDDB}),
        new ThemeDef("ember", R.string.theme_ember, R.style.Theme_App_Ember_Dark, R.style.Theme_App_Ember_Light,
            new int[]{0xFF151211, 0xFF231F1D, 0xFFEEB7A0, 0xFF703319},
            new int[]{0xFFFBF8F7, 0xFFF3EDEA, 0xFFAA3D0E, 0xFFFCD9CA}),
        new ThemeDef("sand", R.string.theme_sand, R.style.Theme_App_Sand_Dark, R.style.Theme_App_Sand_Light,
            new int[]{0xFF151411, 0xFF23211D, 0xFFE6D3A8, 0xFF675223},
            new int[]{0xFFFBF9F7, 0xFFF3F0EA, 0xFF9C761C, 0xFFF8EBCE}),
        new ThemeDef("slate", R.string.theme_slate, R.style.Theme_App_Slate_Dark, R.style.Theme_App_Slate_Light,
            new int[]{0xFF121314, 0xFF1E1F21, 0xFFBDC4D1, 0xFF3A4150},
            new int[]{0xFFF8F8F9, 0xFFEDEEF0, 0xFF3E527A, 0xFFD8DFEE})
    };

    private ThemeManager() {
    }

    public static ThemeDef find(String id) {
        for (ThemeDef d : THEMES) {
            if (d.id.equals(id)) return d;
        }
        return THEMES[0];
    }

    public static boolean isDark(Context c) {
        String mode = AppPreferences.getThemeMode(c);
        if (AppPreferences.MODE_DARK.equals(mode)) return true;
        if (AppPreferences.MODE_LIGHT.equals(mode)) return false;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void apply(Activity a) {
        boolean dark = isDark(a);
        ThemeDef def = find(AppPreferences.getThemeId(a));
        a.setTheme(dark ? def.darkStyle : def.lightStyle);
        if (Build.VERSION.SDK_INT >= 31 && AppPreferences.isWallpaperColors(a)) {
            a.getTheme().applyStyle(dark
                    ? com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Dark
                    : com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Light, true);
        }
        if (dark && AppPreferences.isPureBlack(a)) {
            a.getTheme().applyStyle(R.style.ThemeOverlay_App_PureBlack, true);
        }
    }

    public static void applyWindow(Activity a) {
        Window w = a.getWindow();
        WindowCompat.setDecorFitsSystemWindows(w, false);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(0);
        w.setNavigationBarColor(0);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(w, w.getDecorView());
        boolean light = !isDark(a);
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
    }

    public static String signature(Context c) {
        return AppPreferences.getThemeId(c) + "|" + AppPreferences.getThemeMode(c) + "|"
                + AppPreferences.isPureBlack(c) + "|" + AppPreferences.isWallpaperColors(c) + "|"
                + AppPreferences.getIconShape(c) + "|" + AppPreferences.getFontScale(c) + "|"
                + AppPreferences.getMotionStyle(c) + "|" + AppPreferences.isLiveBackground(c) + "|"
                + AppPreferences.isNavLabelsHidden(c) + "|" + AppPreferences.getLanguage(c);
    }
}
