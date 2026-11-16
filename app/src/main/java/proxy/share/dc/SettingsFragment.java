/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageInfo;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import proxy.share.dc.monitor.MonitorHub;
import proxy.share.dc.ui.AppPreferences;
import proxy.share.dc.ui.IconShapes;
import proxy.share.dc.ui.MotionHelper;
import proxy.share.dc.ui.SwatchView;
import proxy.share.dc.ui.ThemeManager;
import proxy.share.dc.ui.UiKit;

public class SettingsFragment extends PageFragment {
    private interface IntCallback {
        void on(int index);
    }

    private interface BoolCallback {
        void on(boolean value);
    }

    private LinearLayout groupAppearance;
    private LinearLayout groupBehavior;
    private LinearLayout groupProxy;
    private LinearLayout groupMonitoring;
    private LinearLayout groupAbout;
    private ScrollView scroll;
    private static int pendingScroll = -1;

    @Override
    protected int layoutRes() {
        return R.layout.fragment_settings;
    }

    @Override
    protected void onViewReady(Bundle savedInstanceState) {
        bindHeader(R.string.settings_title, getString(R.string.settings_subtitle), "ic_settings");
        scroll = root.findViewById(R.id.scroll);
        host().setNavClearance(scroll);
        host().bindScrollPause(scroll);
        groupAppearance = root.findViewById(R.id.groupAppearance);
        groupBehavior = root.findViewById(R.id.groupBehavior);
        groupProxy = root.findViewById(R.id.groupProxy);
        groupMonitoring = root.findViewById(R.id.groupMonitoring);
        groupAbout = root.findViewById(R.id.groupAbout);
        buildAll();
        LinearLayout[] groups = {groupAppearance, groupBehavior, groupProxy, groupMonitoring, groupAbout};
        if (pendingScroll >= 0) {
            final int y = pendingScroll;
            pendingScroll = -1;
            scroll.post(() -> scroll.scrollTo(0, y));
        } else {
            for (int i = 0; i < groups.length; i++) MotionHelper.animateEntrance(groups[i], i);
        }
    }

    private void applyChanges() {
        pendingScroll = scroll.getScrollY();
        requireActivity().recreate();
    }

    private void restartProxyIfRunning() {
        if (!ProxyService.isRunning) return;
        final Context app = ctx.getApplicationContext();
        final Intent service = new Intent(app, ProxyService.class);
        app.stopService(service);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ContextCompat.startForegroundService(app, service);
            Toast.makeText(app, R.string.restart_hint, Toast.LENGTH_LONG).show();
        }, 600);
    }

    private void buildAll() {
        groupAppearance.removeAllViews();
        groupBehavior.removeAllViews();
        groupProxy.removeAllViews();
        groupMonitoring.removeAllViews();
        groupAbout.removeAllViews();
        buildAppearance();
        buildBehavior();
        buildProxy();
        buildMonitoring();
        buildAbout();
        styleGroup(groupAppearance);
        styleGroup(groupBehavior);
        styleGroup(groupProxy);
        styleGroup(groupMonitoring);
        styleGroup(groupAbout);
    }

    private void buildAppearance() {
        View modes = LayoutInflater.from(ctx).inflate(R.layout.item_settings_modes_row, groupAppearance, false);
        MaterialButtonToggleGroup toggle = modes.findViewById(R.id.modeGroup);
        String mode = AppPreferences.getThemeMode(ctx);
        toggle.check(AppPreferences.MODE_LIGHT.equals(mode) ? R.id.modeLight
                : AppPreferences.MODE_DARK.equals(mode) ? R.id.modeDark : R.id.modeSystem);
        toggle.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            AppPreferences.performHaptic(g);
            String m = id == R.id.modeLight ? AppPreferences.MODE_LIGHT
                    : id == R.id.modeDark ? AppPreferences.MODE_DARK : AppPreferences.MODE_SYSTEM;
            if (!m.equals(AppPreferences.getThemeMode(ctx))) {
                AppPreferences.setThemeMode(ctx, m);
                applyChanges();
            }
        });
        groupAppearance.addView(modes);

        View themes = LayoutInflater.from(ctx).inflate(R.layout.item_settings_themes_row, groupAppearance, false);
        LinearLayout tiles = themes.findViewById(R.id.themeTiles);
        boolean dark = ThemeManager.isDark(ctx);
        String selectedId = AppPreferences.getThemeId(ctx);
        for (ThemeManager.ThemeDef def : ThemeManager.THEMES) {
            View tile = LayoutInflater.from(ctx).inflate(R.layout.item_theme_tile, tiles, false);
            SwatchView sw = tile.findViewById(R.id.swatch);
            sw.setColors(dark ? def.dark : def.light);
            sw.setSelectedTile(def.id.equals(selectedId));
            ((TextView) tile.findViewById(R.id.swatchLabel)).setText(def.labelRes);
            tile.setOnClickListener(v -> {
                AppPreferences.performHaptic(v);
                if (!def.id.equals(AppPreferences.getThemeId(ctx))) {
                    AppPreferences.setThemeId(ctx, def.id);
                    applyChanges();
                }
            });
            tiles.addView(tile);
        }
        groupAppearance.addView(themes);

        if (Build.VERSION.SDK_INT >= 31) {
            switchRow(groupAppearance, "ic_palette", R.string.set_wallpaper, R.string.set_wallpaper_sub,
                    AppPreferences.isWallpaperColors(ctx), true, v -> {
                        AppPreferences.setWallpaperColors(ctx, v);
                        applyChanges();
                    });
        }
        boolean blackAllowed = dark;
        View black = switchRow(groupAppearance, "ic_moon", R.string.set_pure_black, R.string.set_pure_black_sub,
                AppPreferences.isPureBlack(ctx), blackAllowed, v -> {
                    AppPreferences.setPureBlack(ctx, v);
                    applyChanges();
                });
        black.setAlpha(blackAllowed ? 1f : 0.45f);

        String shape = IconShapes.normalize(AppPreferences.getIconShape(ctx));
        valueRow(groupAppearance, "ic_shapes", R.string.set_icon_shape, getString(R.string.set_icon_shape_sub),
                getString(IconShapes.labelRes(shape)), v -> {
                    String[] labels = new String[IconShapes.IDS.length];
                    int sel = 0;
                    for (int i = 0; i < labels.length; i++) {
                        labels[i] = getString(IconShapes.labelRes(IconShapes.IDS[i]));
                        if (IconShapes.IDS[i].equals(shape)) sel = i;
                    }
                    showChoice(R.string.set_icon_shape, labels, sel, i -> {
                        AppPreferences.setIconShape(ctx, IconShapes.IDS[i]);
                        applyChanges();
                    });
                });

        float[] scales = {0.9f, 1f, 1.15f, 1.3f};
        int[] scaleLabels = {R.string.font_small, R.string.font_normal, R.string.font_large, R.string.font_xlarge};
        int curScale = 1;
        for (int i = 0; i < scales.length; i++) {
            if (Math.abs(scales[i] - AppPreferences.getFontScale(ctx)) < 0.01f) curScale = i;
        }
        final int fs = curScale;
        valueRow(groupAppearance, "ic_text_size", R.string.set_font_size, getString(R.string.set_font_size_sub),
                getString(scaleLabels[fs]), v -> {
                    String[] labels = new String[scales.length];
                    for (int i = 0; i < labels.length; i++) labels[i] = getString(scaleLabels[i]);
                    showChoice(R.string.set_font_size, labels, fs, i -> {
                        AppPreferences.setFontScale(ctx, scales[i]);
                        applyChanges();
                    });
                });

        switchRow(groupAppearance, "ic_bolt", R.string.set_live_bg, R.string.set_live_bg_sub,
                AppPreferences.isLiveBackground(ctx), true, v -> {
                    AppPreferences.setLiveBackground(ctx, v);
                    applyChanges();
                });
        switchRow(groupAppearance, "ic_home", R.string.set_nav_labels, R.string.set_nav_labels_sub,
                AppPreferences.isNavLabelsHidden(ctx), true, v -> {
                    AppPreferences.setNavLabelsHidden(ctx, v);
                    applyChanges();
                });
        final String lang = AppPreferences.getLanguage(ctx);
        valueRow(groupAppearance, "ic_language", R.string.set_language, getString(R.string.set_language_sub),
                lang.equals("fa") ? "فارسی" : "English", v ->
                        showChoice(R.string.set_language, new String[]{"فارسی", "English"}, lang.equals("fa") ? 0 : 1, i -> {
                            AppPreferences.setLanguage(ctx, i == 0 ? "fa" : "en");
                            applyChanges();
                        }));
    }

    private void buildBehavior() {
        int[] motionLabels = {R.string.motion_expressive, R.string.motion_standard, R.string.motion_reduced};
        int motion = AppPreferences.getMotionStyle(ctx);
        valueRow(groupBehavior, "ic_motion", R.string.set_motion, getString(R.string.set_motion_sub),
                getString(motionLabels[motion]), v -> {
                    String[] labels = new String[motionLabels.length];
                    for (int i = 0; i < labels.length; i++) labels[i] = getString(motionLabels[i]);
                    showChoice(R.string.set_motion, labels, motion, i -> {
                        AppPreferences.setMotionStyle(ctx, i);
                        applyChanges();
                    });
                });
        switchRow(groupBehavior, "ic_vibrate", R.string.set_haptics, R.string.set_haptics_sub,
                AppPreferences.isHaptics(ctx), true, v -> AppPreferences.setHaptics(ctx, v));
    }

    private void buildProxy() {
        valueRow(groupProxy, "ic_server", R.string.set_http_port, getString(R.string.set_port_sub),
                String.valueOf(AppPreferences.getHttpPort(ctx)), v -> showPortDialog(true));
        valueRow(groupProxy, "ic_server", R.string.set_socks_port, getString(R.string.set_port_sub),
                String.valueOf(AppPreferences.getSocksPort(ctx)), v -> showPortDialog(false));
    }

    private void buildMonitoring() {
        int[] intervals = {1000, 2000, 5000};
        String[] intervalLabels = {"1 s", "2 s", "5 s"};
        int curInterval = 0;
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i] == AppPreferences.getPingInterval(ctx)) curInterval = i;
        }
        final int ci = curInterval;
        valueRow(groupMonitoring, "ic_ping", R.string.set_ping_interval, getString(R.string.set_ping_interval_sub),
                intervalLabels[ci], v -> showChoice(R.string.set_ping_interval, intervalLabels, ci, i -> {
                    AppPreferences.setPingInterval(ctx, intervals[i]);
                    buildAll();
                }));
        switchRow(groupMonitoring, "ic_log", R.string.set_log_connections, R.string.set_log_connections_sub,
                AppPreferences.isLogConnections(ctx), true, v -> {
                    AppPreferences.setLogConnections(ctx, v);
                    restartProxyIfRunning();
                });
        int[] limits = {200, 500, 1000};
        String[] limitLabels = {"200", "500", "1000"};
        int curLimit = 1;
        for (int i = 0; i < limits.length; i++) {
            if (limits[i] == AppPreferences.getLogLimit(ctx)) curLimit = i;
        }
        final int cl = curLimit;
        valueRow(groupMonitoring, "ic_log", R.string.set_log_limit, getString(R.string.set_log_limit_sub),
                limitLabels[cl], v -> showChoice(R.string.set_log_limit, limitLabels, cl, i -> {
                    AppPreferences.setLogLimit(ctx, limits[i]);
                    LogStore.setLimit(limits[i]);
                    buildAll();
                }));
        switchRow(groupMonitoring, "ic_world", R.string.set_online_checks, R.string.set_online_checks_sub,
                AppPreferences.isOnlineChecks(ctx), true, v -> {
                    AppPreferences.setOnlineChecks(ctx, v);
                    MonitorHub.get().refreshNow();
                });
    }

    private void buildAbout() {
        String version = "1.0";
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            if (pi.versionName != null) version = pi.versionName;
        } catch (Exception ignored) {
        }
        valueRow(groupAbout, "ic_info", R.string.set_version, getString(R.string.app_name), version, null);
    }

    private View baseRow(LinearLayout group, String icon, int titleRes, String subtitle) {
        View row = LayoutInflater.from(ctx).inflate(R.layout.item_settings_row, group, false);
        ((ImageView) row.findViewById(R.id.rowIcon)).setImageDrawable(UiKit.icon(ctx, icon));
        ((TextView) row.findViewById(R.id.rowTitle)).setText(titleRes);
        TextView sub = row.findViewById(R.id.rowSubtitle);
        if (subtitle == null || subtitle.isEmpty()) sub.setVisibility(View.GONE);
        else sub.setText(subtitle);
        row.findViewById(R.id.rowValue).setVisibility(View.GONE);
        row.findViewById(R.id.rowChevron).setVisibility(View.GONE);
        group.addView(row);
        return row;
    }

    private View valueRow(LinearLayout group, String icon, int titleRes, String subtitle, String value, View.OnClickListener l) {
        View row = baseRow(group, icon, titleRes, subtitle);
        TextView v = row.findViewById(R.id.rowValue);
        v.setText(value);
        v.setVisibility(View.VISIBLE);
        if (l != null) {
            ImageView chevron = row.findViewById(R.id.rowChevron);
            chevron.setImageDrawable(UiKit.icon(ctx, "ic_chevron_right"));
            chevron.setVisibility(View.VISIBLE);
            row.setOnClickListener(x -> {
                AppPreferences.performHaptic(x);
                l.onClick(x);
            });
        }
        return row;
    }

    private View switchRow(LinearLayout group, String icon, int titleRes, int subRes, boolean initial,
                           boolean enabled, BoolCallback cb) {
        View row = baseRow(group, icon, titleRes, getString(subRes));
        MaterialSwitch sw = row.findViewById(R.id.rowSwitch);
        sw.setSaveEnabled(false);
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(initial);
        sw.setVisibility(View.VISIBLE);
        row.setOnClickListener(x -> {
            if (!enabled) return;
            AppPreferences.performHaptic(x);
            boolean next = !sw.isChecked();
            sw.setChecked(next);
            cb.on(next);
        });
        return row;
    }

    private void styleGroup(LinearLayout group) {
        int n = group.getChildCount();
        int fill = UiKit.color(ctx, "colorSurfaceContainer", 0xFF222222);
        int ripple = UiKit.withAlpha(UiKit.onSurface(ctx), 0x1F);
        float outer = Math.max(UiKit.dp(ctx, 8), UiKit.dp(ctx, 26));
        float inner = Math.max(UiKit.dp(ctx, 3), UiKit.dp(ctx, 6));
        for (int i = 0; i < n; i++) {
            View child = group.getChildAt(i);
            float top = i == 0 ? outer : inner;
            float bottom = i == n - 1 ? outer : inner;
            float[] radii = {top, top, top, top, bottom, bottom, bottom, bottom};
            GradientDrawable content = new GradientDrawable();
            content.setColor(fill);
            content.setCornerRadii(radii);
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(0xFFFFFFFF);
            mask.setCornerRadii(radii);
            child.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), content, mask));
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).bottomMargin = i == n - 1 ? 0 : UiKit.dp(ctx, 2);
                child.setLayoutParams(lp);
            }
        }
    }

    private void showChoice(int titleRes, String[] labels, int selected, IntCallback cb) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, selected, (d, which) -> {
                    d.dismiss();
                    cb.on(which);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showPortDialog(boolean http) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_port_input, null);
        TextInputLayout layout = v.findViewById(R.id.inputLayout);
        TextInputEditText field = v.findViewById(R.id.inputField);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(String.valueOf(http ? AppPreferences.getHttpPort(ctx) : AppPreferences.getSocksPort(ctx)));
        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(http ? R.string.set_http_port : R.string.set_socks_port)
                .setView(v)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            int port;
            try {
                port = Integer.parseInt(String.valueOf(field.getText()).trim());
            } catch (NumberFormatException e) {
                layout.setError(getString(R.string.port_invalid));
                return;
            }
            if (port < 1024 || port > 65535) {
                layout.setError(getString(R.string.port_invalid));
                return;
            }
            int other = http ? AppPreferences.getSocksPort(ctx) : AppPreferences.getHttpPort(ctx);
            if (port == other) {
                layout.setError(getString(R.string.port_same));
                return;
            }
            if (http) AppPreferences.setHttpPort(ctx, port);
            else AppPreferences.setSocksPort(ctx, port);
            dialog.dismiss();
            buildAll();
            restartProxyIfRunning();
        }));
        dialog.show();
    }
}
