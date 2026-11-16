/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import proxy.share.dc.monitor.MonitorHub;
import proxy.share.dc.monitor.NetworkInfoReader;
import proxy.share.dc.monitor.Quality;
import proxy.share.dc.ui.AppPreferences;
import proxy.share.dc.ui.MotionHelper;
import proxy.share.dc.ui.ShapeView;
import proxy.share.dc.ui.SparklineView;
import proxy.share.dc.ui.UiKit;

public class HomeFragment extends PageFragment implements MonitorHub.Listener {
    private static final class Tile {
        View root;
        ImageView icon;
        View dot;
        TextView label;
        TextView value;
    }

    private Tile tileVpn;
    private Tile tileHotspot;
    private Tile tileInternet;
    private Tile tileNetwork;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private TextView heroUptime;
    private View heroDot;
    private MaterialButton btnStartStop;
    private TextView endpointHint;
    private TextView httpValue;
    private TextView socksValue;
    private TextView txtDownload;
    private TextView txtUpload;
    private TextView txtDownloadTotal;
    private TextView txtUploadTotal;
    private SparklineView chart;
    private LinearLayout clientsList;
    private TextView clientsEmpty;
    private final List<String> shownClients = new ArrayList<>();
    private final Map<String, TextView[]> clientTexts = new HashMap<>();
    private String currentIp = "127.0.0.1";

    @Override
    protected int layoutRes() {
        return R.layout.fragment_home;
    }

    @Override
    protected void onViewReady(Bundle savedInstanceState) {
        bindHeader(R.string.app_name, getString(R.string.status_stopped_short), "ic_hotspot");

        ScrollView scroll = root.findViewById(R.id.scroll);
        host().setNavClearance(scroll);
        host().bindScrollPause(scroll);

        int primary = UiKit.primary(ctx);
        ShapeView a = root.findViewById(R.id.heroShapeA);
        ShapeView b = root.findViewById(R.id.heroShapeB);
        a.setFillColor(UiKit.withAlpha(primary, 0x26));
        b.setFillColor(UiKit.withAlpha(primary, 0x33));
        a.setIdleSpin(true);
        b.setIdleSpin(true);

        heroTitle = root.findViewById(R.id.heroTitle);
        heroSubtitle = root.findViewById(R.id.heroSubtitle);
        heroUptime = root.findViewById(R.id.heroUptime);
        heroDot = root.findViewById(R.id.heroDot);
        btnStartStop = root.findViewById(R.id.btnStartStop);
        endpointHint = root.findViewById(R.id.endpointHint);
        txtDownload = root.findViewById(R.id.txtDownload);
        txtUpload = root.findViewById(R.id.txtUpload);
        txtDownloadTotal = root.findViewById(R.id.txtDownloadTotal);
        txtUploadTotal = root.findViewById(R.id.txtUploadTotal);
        chart = root.findViewById(R.id.trafficChart);
        chart.setColors(primary, UiKit.color(ctx, "colorTertiary", 0xFFFFAA00));
        clientsList = root.findViewById(R.id.clientsList);
        clientsEmpty = root.findViewById(R.id.clientsEmpty);

        LinearLayout top = root.findViewById(R.id.tilesTop);
        LinearLayout bottom = root.findViewById(R.id.tilesBottom);
        tileVpn = makeTile(top, R.string.tile_vpn, "ic_vpn", MonitorFragment.TAB_VPN, false);
        tileHotspot = makeTile(top, R.string.tile_hotspot, "ic_hotspot", MonitorFragment.TAB_DEVICES, true);
        tileInternet = makeTile(bottom, R.string.tile_internet, "ic_ping", MonitorFragment.TAB_PING, false);
        tileNetwork = makeTile(bottom, R.string.tile_network, "ic_wifi", MonitorFragment.TAB_WIFI, true);

        View rowHttp = root.findViewById(R.id.rowHttp);
        View rowSocks = root.findViewById(R.id.rowSocks);
        httpValue = bindEndpoint(rowHttp, R.string.label_http_proxy);
        socksValue = bindEndpoint(rowSocks, R.string.label_socks_proxy);

        root.findViewById(R.id.clientsAll).setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            openMonitor(MonitorFragment.TAB_DEVICES);
        });

        btnStartStop.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            Intent service = new Intent(ctx, ProxyService.class);
            if (ProxyService.isRunning) {
                ctx.stopService(service);
            } else {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
                }
                ContextCompat.startForegroundService(ctx, service);
            }
            v.postDelayed(this::render, 450);
        });

        View[] sections = {root.findViewById(R.id.heroCard), root.findViewById(R.id.tilesTop), root.findViewById(R.id.tilesBottom),
                root.findViewById(R.id.endpointCard), root.findViewById(R.id.trafficCard), root.findViewById(R.id.clientsCard)};
        for (int i = 0; i < sections.length; i++) MotionHelper.animateEntrance(sections[i], i);
    }

    private Tile makeTile(LinearLayout parent, int labelRes, String icon, int tab, boolean second) {
        Tile t = new Tile();
        t.root = LayoutInflater.from(ctx).inflate(R.layout.item_status_tile, parent, false);
        t.icon = t.root.findViewById(R.id.tileIcon);
        t.dot = t.root.findViewById(R.id.tileDot);
        t.label = t.root.findViewById(R.id.tileLabel);
        t.value = t.root.findViewById(R.id.tileValue);
        t.icon.setImageDrawable(UiKit.icon(ctx, icon));
        t.label.setText(labelRes);
        t.root.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            openMonitor(tab);
        });
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.root.getLayoutParams();
        if (second) lp.setMarginStart(UiKit.dp(ctx, 12));
        parent.addView(t.root, lp);
        return t;
    }

    private TextView bindEndpoint(View row, int labelRes) {
        TextView label = row.findViewById(R.id.endpointLabel);
        TextView value = row.findViewById(R.id.endpointValue);
        ImageButton copy = row.findViewById(R.id.endpointCopy);
        label.setText(labelRes);
        copy.setImageDrawable(UiKit.icon(ctx, "ic_copy"));
        copy.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            UiKit.copy(ctx, getString(labelRes), value.getText().toString());
        });
        return value;
    }

    private void openMonitor(int tab) {
        ((MainActivity) requireActivity()).openMonitor(tab);
    }

    @Override
    protected void onActive() {
        MonitorHub.get().attach(this);
        render();
    }

    @Override
    protected void onInactive() {
        MonitorHub.get().detach(this);
    }

    @Override
    public void onTick() {
        render();
    }

    private void setTile(Tile t, String value, int dotColor) {
        if (!t.value.getText().toString().equals(value)) t.value.setText(value);
        t.dot.setBackgroundTintList(ColorStateList.valueOf(dotColor));
    }

    private void render() {
        MonitorHub h = MonitorHub.get();
        boolean running = ProxyService.isRunning;
        int http = AppPreferences.getHttpPort(ctx);
        int socks = AppPreferences.getSocksPort(ctx);
        int success = UiKit.success(ctx);
        int error = UiKit.error(ctx);
        int quiet = UiKit.outlineVariant(ctx);
        currentIp = NetworkUtils.getLocalIpAddress(ctx);

        if (running) {
            heroTitle.setText(ProxyService.bindError ? R.string.hero_bind_error : R.string.hero_running);
            heroSubtitle.setText(getString(R.string.hero_sub_running,
                    String.valueOf(h.clientCount()), String.valueOf(TrafficMonitor.activeConnections.get())));
            heroUptime.setText(UiKit.duration(System.currentTimeMillis() - TrafficMonitor.startedAt));
            heroDot.setBackgroundTintList(ColorStateList.valueOf(success));
            btnStartStop.setText(R.string.btn_stop_proxy);
            btnStartStop.setIcon(UiKit.icon(ctx, "ic_stop"));
            btnStartStop.setBackgroundTintList(ColorStateList.valueOf(UiKit.color(ctx, "colorErrorContainer", error)));
            int fg = UiKit.color(ctx, "colorOnErrorContainer", 0xFFFFFFFF);
            btnStartStop.setTextColor(fg);
            btnStartStop.setIconTint(ColorStateList.valueOf(fg));
            setHeaderSubtitle(getString(R.string.status_running_short));
        } else {
            heroTitle.setText(R.string.hero_stopped);
            heroSubtitle.setText(R.string.hero_sub_stopped);
            heroUptime.setText(R.string.status_inactive);
            heroDot.setBackgroundTintList(ColorStateList.valueOf(quiet));
            btnStartStop.setText(R.string.btn_start_proxy);
            btnStartStop.setIcon(UiKit.icon(ctx, "ic_play"));
            btnStartStop.setBackgroundTintList(ColorStateList.valueOf(UiKit.primary(ctx)));
            int fg = UiKit.color(ctx, "colorOnPrimary", 0xFF000000);
            btnStartStop.setTextColor(fg);
            btnStartStop.setIconTint(ColorStateList.valueOf(fg));
            setHeaderSubtitle(getString(R.string.status_stopped_short));
        }

        setTile(tileVpn, h.vpn ? (h.vpnIface.isEmpty() ? getString(R.string.status_connected) : h.vpnIface)
                : getString(R.string.status_disconnected), h.vpn ? success : error);
        setTile(tileHotspot, h.hotspot ? getString(R.string.status_on) : getString(R.string.status_off),
                h.hotspot ? success : quiet);
        float last = h.pingStats.last;
        String internet;
        int internetColor;
        if (h.pingStats.sent == 0) {
            internet = getString(R.string.status_checking);
            internetColor = quiet;
        } else if (last >= 0) {
            internet = UiKit.ms(last);
            internetColor = Quality.color(ctx, last);
        } else {
            internet = getString(R.string.status_no_reply);
            internetColor = error;
        }
        setTile(tileInternet, internet, internetColor);
        NetworkInfoReader.Active act = h.active;
        String net;
        String icon = "ic_wifi";
        if (act.wifi) {
            net = "Wi-Fi";
        } else if (act.cellular) {
            net = getString(R.string.net_cellular);
            icon = "ic_mobile";
        } else if (act.ethernet) {
            net = "Ethernet";
            icon = "ic_router";
        } else {
            net = getString(R.string.net_none);
        }
        tileNetwork.icon.setImageDrawable(UiKit.icon(ctx, icon));
        setTile(tileNetwork, net, act.connected ? success : error);

        endpointHint.setText(getString(R.string.endpoint_hint, currentIp));
        httpValue.setText(currentIp + ":" + http);
        socksValue.setText(currentIp + ":" + socks);

        txtDownload.setText(TrafficMonitor.formatSpeed(h.proxyRxRate));
        txtUpload.setText(TrafficMonitor.formatSpeed(h.proxyTxRate));
        txtDownloadTotal.setText(getString(R.string.total_fmt, TrafficMonitor.formatBytes(TrafficMonitor.rxBytes.get())));
        txtUploadTotal.setText(getString(R.string.total_fmt, TrafficMonitor.formatBytes(TrafficMonitor.txBytes.get())));
        float[] rx = h.proxyRxSeries.toArray();
        float[] tx = h.proxyTxSeries.toArray();
        float max = 0;
        for (float v : rx) max = Math.max(max, v);
        for (float v : tx) max = Math.max(max, v);
        chart.setMaxLabel(TrafficMonitor.formatSpeed((long) max));
        chart.setSeries(rx, tx);

        renderClients();
    }

    private void renderClients() {
        List<ClientRegistry.Client> all = ClientRegistry.snapshot();
        List<ClientRegistry.Client> top = all.size() > 3 ? all.subList(0, 3) : all;
        List<String> ips = new ArrayList<>();
        for (ClientRegistry.Client c : top) ips.add(c.ip);
        clientsEmpty.setVisibility(top.isEmpty() ? View.VISIBLE : View.GONE);
        if (!ips.equals(shownClients)) {
            shownClients.clear();
            shownClients.addAll(ips);
            clientTexts.clear();
            clientsList.removeAllViews();
            for (ClientRegistry.Client c : top) {
                View row = LayoutInflater.from(ctx).inflate(R.layout.item_client_row, clientsList, false);
                ShapeView avatar = row.findViewById(R.id.clientAvatar);
                avatar.setFillColor(UiKit.color(ctx, "colorPrimaryContainer", 0xFF444444));
                avatar.setIcon(UiKit.icon(ctx, "ic_devices"), UiKit.color(ctx, "colorOnPrimaryContainer", 0xFFFFFFFF));
                ((TextView) row.findViewById(R.id.clientTitle)).setText(c.ip);
                clientTexts.put(c.ip, new TextView[]{
                        row.findViewById(R.id.clientSubtitle), row.findViewById(R.id.clientTrailing)});
                clientsList.addView(row);
            }
        }
        for (ClientRegistry.Client c : top) {
            TextView[] tv = clientTexts.get(c.ip);
            if (tv == null) continue;
            tv[0].setText(getString(R.string.client_sub, String.valueOf(c.active.get()),
                    TrafficMonitor.formatBytes(c.rx.get()), TrafficMonitor.formatBytes(c.tx.get())));
            tv[1].setText(TrafficMonitor.formatBytes(c.rx.get() + c.tx.get()));
        }
    }
}
