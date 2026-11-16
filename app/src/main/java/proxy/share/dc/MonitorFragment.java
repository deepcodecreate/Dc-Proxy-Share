/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import proxy.share.dc.monitor.DeviceScanner;
import proxy.share.dc.monitor.MonitorHub;
import proxy.share.dc.monitor.NetworkInfoReader;
import proxy.share.dc.monitor.PingStats;
import proxy.share.dc.monitor.PublicIpLookup;
import proxy.share.dc.monitor.Quality;
import proxy.share.dc.ui.AppPreferences;
import proxy.share.dc.ui.InfoCard;
import proxy.share.dc.ui.MotionHelper;
import proxy.share.dc.ui.ShapeView;
import proxy.share.dc.ui.SparklineView;
import proxy.share.dc.ui.UiKit;

public class MonitorFragment extends PageFragment implements MonitorHub.Listener, LogStore.Listener {
    public static final String EXTRA_TAB = "tab";
    public static final int TAB_OVERVIEW = 0;
    public static final int TAB_WIFI = 1;
    public static final int TAB_VPN = 2;
    public static final int TAB_DEVICES = 3;
    public static final int TAB_PING = 4;
    public static final int TAB_LOGS = 5;

    private static final int[] TAB_LABELS = {R.string.tab_overview, R.string.tab_wifi, R.string.tab_vpn,
            R.string.tab_devices, R.string.tab_ping, R.string.tab_logs};
    private static final String[] TAB_ICONS = {"ic_activity", "ic_wifi", "ic_vpn", "ic_devices", "ic_ping", "ic_log"};

    private final View[] panes = new View[6];
    private FrameLayout paneHost;
    private Chip[] tabChips;
    private int currentTab = -1;

    private InfoCard cInternet;
    private InfoCard cNet;
    private InfoCard cTraffic;
    private InfoCard cProxy;
    private SparklineView trafficChart;

    private MaterialCardView wifiPermCard;
    private InfoCard cWifi;
    private InfoCard cWifiIp;
    private InfoCard cHotspot;
    private LinearProgressIndicator signalBar;

    private InfoCard cVpn;
    private InfoCard cVpnNet;
    private InfoCard cVpnTraffic;
    private InfoCard cExit;
    private InfoCard cRoute;

    private InfoCard cDevices;
    private LinearLayout deviceList;
    private LinearProgressIndicator scanBar;
    private TextView scanStatus;
    private List<DeviceScanner.Device> devices = new ArrayList<>();
    private boolean scanStarted;

    private InfoCard cPing;
    private TextView pingBig;
    private TextView pingQuality;
    private InfoCard cPingHistory;
    private SparklineView pingChart;
    private InfoCard cCompare;
    private ChipGroup pingTargets;
    private Chip chipCustom;
    private final List<String> compareHosts = new ArrayList<>();

    private RecyclerView logRecycler;
    private LogAdapter logAdapter;
    private int logFilter = -1;

    @Override
    protected int layoutRes() {
        return R.layout.fragment_monitor;
    }

    @Override
    protected void onViewReady(Bundle savedInstanceState) {
        bindHeader(R.string.monitor_title, getString(R.string.monitor_subtitle), "ic_activity");
        paneHost = root.findViewById(R.id.paneHost);

        ChipGroup tabGroup = root.findViewById(R.id.tabGroup);
        int start = getArguments() == null ? TAB_OVERVIEW : getArguments().getInt(EXTRA_TAB, TAB_OVERVIEW);
        final Chip[] chips = new Chip[TAB_LABELS.length];
        tabChips = chips;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            Chip chip = (Chip) LayoutInflater.from(ctx).inflate(R.layout.item_tab_chip, tabGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(TAB_LABELS[i]);
            chip.setChipIcon(UiKit.icon(ctx, TAB_ICONS[i]));
            chip.setChipIconVisible(true);
            chip.setChipIconTint(UiKit.chipIconTint(ctx));
            chip.setTag(i);
            tabGroup.addView(chip);
            chips[i] = chip;
        }
        chips[start].setChecked(true);
        showTab(start);
        tabGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            View c = group.findViewById(ids.get(0));
            if (c != null) showTab((Integer) c.getTag());
        });
    }

    public static MonitorFragment newInstance(int tab) {
        MonitorFragment f = new MonitorFragment();
        Bundle b = new Bundle();
        b.putInt(EXTRA_TAB, tab);
        f.setArguments(b);
        return f;
    }

    public void selectTab(int tab) {
        if (tabChips != null && tab >= 0 && tab < tabChips.length) tabChips[tab].setChecked(true);
    }

    @Override
    protected void onActive() {
        MonitorHub.get().attach(this);
        LogStore.addListener(this);
        renderTab();
        if (currentTab == TAB_LOGS) refreshLogs();
    }

    @Override
    protected void onInactive() {
        MonitorHub.get().detach(this);
        LogStore.removeListener(this);
    }

    @Override
    public void onTick() {
        renderTab();
        MonitorHub h = MonitorHub.get();
        float last = h.pingStats.last;
        setHeaderSubtitle(h.pingStats.sent == 0 ? getString(R.string.monitor_subtitle)
                : getString(R.string.monitor_live, UiKit.ms(last), getString(Quality.labelRes(last))));
    }

    @Override
    public void onLogChanged() {
        if (currentTab == TAB_LOGS) refreshLogs();
    }

    private LinearLayout scrollPane() {
        ScrollView sv = new ScrollView(ctx);
        sv.setClipToPadding(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        int ph = getResources().getDimensionPixelSize(R.dimen.page_padding_h);
        content.setPadding(ph, UiKit.dp(ctx, 4), ph, 0);
        sv.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host().setNavClearance(content);
        host().bindScrollPause(sv);
        paneHost.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return content;
    }

    private void addCard(LinearLayout parent, View card) {
        UiKit.addSpaced(parent, card, parent.getChildCount() == 0 ? 0 : 12);
    }

    private void showTab(int tab) {
        if (tab == currentTab) return;
        if (panes[tab] == null) buildPane(tab);
        for (int i = 0; i < panes.length; i++) {
            if (panes[i] != null) panes[i].setVisibility(i == tab ? View.VISIBLE : View.GONE);
        }
        currentTab = tab;
        MotionHelper.crossfadeIn(panes[tab]);
        if (tab == TAB_DEVICES && !scanStarted) startScan();
        if (tab == TAB_LOGS) refreshLogs();
        renderTab();
    }

    private void buildPane(int tab) {
        int before = paneHost.getChildCount();
        switch (tab) {
            case TAB_OVERVIEW:
                buildOverview();
                break;
            case TAB_WIFI:
                buildWifi();
                break;
            case TAB_VPN:
                buildVpn();
                break;
            case TAB_DEVICES:
                buildDevices();
                break;
            case TAB_PING:
                buildPing();
                break;
            default:
                buildLogs();
                break;
        }
        panes[tab] = paneHost.getChildAt(before);
    }

    private void buildOverview() {
        LinearLayout c = scrollPane();
        cInternet = new InfoCard(ctx, R.string.card_internet, "ic_world");
        cInternet.setAction("ic_refresh", R.string.action_refresh, v -> MonitorHub.get().refreshNow());
        cInternet.row("status", R.string.row_status);
        cInternet.row("http", R.string.row_http_latency);
        cInternet.row("dns", R.string.row_dns_time);
        cInternet.row("ping", R.string.row_ping_avg);
        cInternet.row("loss", R.string.row_loss);
        cInternet.row("ip", R.string.row_public_ip);
        cInternet.row("country", R.string.row_country);
        cInternet.row("isp", R.string.row_isp);
        cInternet.setValueCopyable("ip", "IP");
        addCard(c, cInternet);

        cNet = new InfoCard(ctx, R.string.card_network, "ic_router");
        cNet.row("type", R.string.row_type);
        cNet.row("iface", R.string.row_interface);
        cNet.row("ip", R.string.row_ip);
        cNet.row("gateway", R.string.row_gateway);
        cNet.row("dns", R.string.row_dns);
        cNet.row("validated", R.string.row_validated);
        cNet.row("metered", R.string.row_metered);
        cNet.row("bw", R.string.row_bandwidth);
        cNet.row("cell", R.string.row_operator);
        addCard(c, cNet);

        cTraffic = new InfoCard(ctx, R.string.card_device_traffic, "ic_activity");
        cTraffic.row("down", R.string.row_download_speed);
        cTraffic.row("up", R.string.row_upload_speed);
        cTraffic.row("totalDown", R.string.row_total_down);
        cTraffic.row("totalUp", R.string.row_total_up);
        cTraffic.row("mobile", R.string.row_mobile_total);
        cTraffic.row("wifi", R.string.row_wifi_total);
        trafficChart = new SparklineView(ctx);
        trafficChart.setColors(UiKit.primary(ctx), UiKit.color(ctx, "colorTertiary", 0xFFFFAA00));
        cTraffic.addCustom(trafficChart);
        trafficChart.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.chart_height);
        addCard(c, cTraffic);

        cProxy = new InfoCard(ctx, R.string.card_proxy_traffic, "ic_server");
        cProxy.row("state", R.string.row_status);
        cProxy.row("down", R.string.row_download_speed);
        cProxy.row("up", R.string.row_upload_speed);
        cProxy.row("totalDown", R.string.row_total_down);
        cProxy.row("totalUp", R.string.row_total_up);
        cProxy.row("clients", R.string.row_clients);
        cProxy.row("active", R.string.row_active_conn);
        cProxy.row("total", R.string.row_total_conn);
        cProxy.row("failed", R.string.row_failed_conn);
        cProxy.row("uptime", R.string.row_uptime);
        addCard(c, cProxy);
    }

    private void buildWifi() {
        LinearLayout c = scrollPane();
        wifiPermCard = new MaterialCardView(ctx);
        wifiPermCard.setCardBackgroundColor(UiKit.color(ctx, "colorSecondaryContainer", 0xFF334455));
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = getResources().getDimensionPixelSize(R.dimen.card_padding);
        box.setPadding(pad, pad, pad, pad);
        TextView msg = new TextView(ctx);
        msg.setText(R.string.wifi_permission_hint);
        msg.setTextColor(UiKit.color(ctx, "colorOnSecondaryContainer", 0xFFFFFFFF));
        box.addView(msg);
        MaterialButton grant = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        grant.setText(R.string.action_grant);
        grant.setOnClickListener(v -> requestPermissions(
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 21));
        UiKit.addSpaced(box, grant, 10);
        wifiPermCard.addView(box);
        addCard(c, wifiPermCard);

        cWifi = new InfoCard(ctx, R.string.card_wifi, "ic_wifi");
        cWifi.row("status", R.string.row_status);
        cWifi.row("ssid", R.string.row_ssid);
        cWifi.row("bssid", R.string.row_bssid);
        cWifi.row("signal", R.string.row_signal);
        signalBar = new LinearProgressIndicator(ctx);
        signalBar.setMax(100);
        signalBar.setTrackThickness(UiKit.dp(ctx, 6));
        signalBar.setTrackCornerRadius(UiKit.dp(ctx, 3));
        cWifi.addCustom(signalBar);
        cWifi.row("link", R.string.row_link_speed);
        cWifi.row("txrx", R.string.row_txrx_speed);
        cWifi.row("band", R.string.row_band);
        cWifi.row("channel", R.string.row_channel);
        cWifi.row("width", R.string.row_width);
        cWifi.row("standard", R.string.row_standard);
        cWifi.row("security", R.string.row_security);
        cWifi.setValueCopyable("bssid", "BSSID");
        addCard(c, cWifi);

        cWifiIp = new InfoCard(ctx, R.string.card_wifi_ip, "ic_router");
        cWifiIp.row("iface", R.string.row_interface);
        cWifiIp.row("ip", R.string.row_ip);
        cWifiIp.row("gateway", R.string.row_gateway);
        cWifiIp.row("dns", R.string.row_dns);
        cWifiIp.row("dhcp", R.string.row_dhcp_server);
        cWifiIp.row("lease", R.string.row_lease);
        cWifiIp.row("mtu", R.string.row_mtu);
        cWifiIp.row("ipv6", R.string.row_ipv6);
        cWifiIp.row("domain", R.string.row_domain);
        cWifiIp.row("pdns", R.string.row_private_dns);
        cWifiIp.row("rx", R.string.row_iface_rx);
        cWifiIp.row("tx", R.string.row_iface_tx);
        cWifiIp.setValueCopyable("ip", "IP");
        addCard(c, cWifiIp);

        cHotspot = new InfoCard(ctx, R.string.card_hotspot, "ic_hotspot");
        cHotspot.row("state", R.string.row_status);
        cHotspot.row("iface", R.string.row_interface);
        cHotspot.row("ip", R.string.row_ip);
        cHotspot.row("tether", R.string.row_usb_tether);
        cHotspot.row("clients", R.string.row_clients);
        addCard(c, cHotspot);
    }

    private void buildVpn() {
        LinearLayout c = scrollPane();
        cVpn = new InfoCard(ctx, R.string.card_vpn, "ic_vpn");
        cVpn.row("status", R.string.row_status);
        cVpn.row("app", R.string.row_vpn_app);
        cVpn.row("protocol", R.string.row_protocol);
        cVpn.row("iface", R.string.row_interface);
        cVpn.row("underlying", R.string.row_underlying);
        cVpn.row("validated", R.string.row_validated);
        cVpn.row("since", R.string.row_since);
        addCard(c, cVpn);

        cVpnNet = new InfoCard(ctx, R.string.card_vpn_net, "ic_lock");
        cVpnNet.row("addr", R.string.row_vpn_address);
        cVpnNet.row("dns", R.string.row_dns);
        cVpnNet.row("routes", R.string.row_routes);
        cVpnNet.row("default", R.string.row_default_route);
        cVpnNet.row("mtu", R.string.row_mtu);
        cVpnNet.row("bw", R.string.row_bandwidth);
        addCard(c, cVpnNet);

        cVpnTraffic = new InfoCard(ctx, R.string.card_vpn_traffic, "ic_activity");
        cVpnTraffic.row("rx", R.string.row_iface_rx);
        cVpnTraffic.row("tx", R.string.row_iface_tx);
        addCard(c, cVpnTraffic);

        cExit = new InfoCard(ctx, R.string.card_exit_ip, "ic_world");
        cExit.setAction("ic_refresh", R.string.action_refresh, v -> MonitorHub.get().refreshNow());
        cExit.row("ip", R.string.row_public_ip);
        cExit.row("country", R.string.row_country);
        cExit.row("city", R.string.row_city);
        cExit.row("isp", R.string.row_isp);
        cExit.row("org", R.string.row_org);
        cExit.row("tz", R.string.row_timezone);
        cExit.setValueCopyable("ip", "IP");
        addCard(c, cExit);

        cRoute = new InfoCard(ctx, R.string.card_proxy_route, "ic_share");
        cRoute.row("route", R.string.row_proxy_exit);
        addCard(c, cRoute);
    }

    private void buildDevices() {
        LinearLayout c = scrollPane();
        cDevices = new InfoCard(ctx, R.string.card_devices, "ic_devices");
        cDevices.setAction("ic_scan", R.string.action_scan, v -> startScan());
        scanBar = new LinearProgressIndicator(ctx);
        scanBar.setMax(100);
        scanBar.setTrackThickness(UiKit.dp(ctx, 4));
        scanBar.setTrackCornerRadius(UiKit.dp(ctx, 2));
        scanBar.setVisibility(View.GONE);
        cDevices.addCustom(scanBar);
        scanStatus = new TextView(ctx);
        scanStatus.setTextColor(UiKit.onSurfaceVariant(ctx));
        scanStatus.setText(R.string.scan_idle);
        cDevices.addCustom(scanStatus);
        deviceList = new LinearLayout(ctx);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        cDevices.body().addView(deviceList);
        TextView note = new TextView(ctx);
        note.setText(R.string.scan_note);
        note.setTextSize(12);
        note.setTextColor(UiKit.onSurfaceVariant(ctx));
        cDevices.addCustom(note);
        addCard(c, cDevices);
    }

    private void buildPing() {
        LinearLayout c = scrollPane();
        pingTargets = new ChipGroup(ctx);
        pingTargets.setSingleSelection(true);
        pingTargets.setSelectionRequired(true);
        pingTargets.setSingleLine(true);
        String current = AppPreferences.getPingTarget(ctx);
        String[][] fixed = {{"1.1.1.1", "Cloudflare"}, {"8.8.8.8", "Google DNS"}, {"google.com", "google.com"}};
        boolean matched = false;
        for (String[] f : fixed) {
            Chip chip = (Chip) LayoutInflater.from(ctx).inflate(R.layout.item_tab_chip, pingTargets, false);
            chip.setId(View.generateViewId());
            chip.setText(f[1]);
            chip.setTag(f[0]);
            pingTargets.addView(chip);
            if (f[0].equals(current)) {
                chip.setChecked(true);
                matched = true;
            }
        }
        Chip gw = (Chip) LayoutInflater.from(ctx).inflate(R.layout.item_tab_chip, pingTargets, false);
        gw.setId(View.generateViewId());
        gw.setText(R.string.target_gateway);
        gw.setTag(AppPreferences.TARGET_GATEWAY);
        pingTargets.addView(gw);
        if (AppPreferences.TARGET_GATEWAY.equals(current)) {
            gw.setChecked(true);
            matched = true;
        }
        chipCustom = (Chip) LayoutInflater.from(ctx).inflate(R.layout.item_tab_chip, pingTargets, false);
        chipCustom.setId(View.generateViewId());
        chipCustom.setTag("custom");
        updateCustomChip();
        pingTargets.addView(chipCustom);
        if (!matched) chipCustom.setChecked(true);
        pingTargets.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = g.findViewById(ids.get(0));
            if (chip == null) return;
            String tag = (String) chip.getTag();
            if ("custom".equals(tag)) {
                promptCustomTarget();
            } else {
                MonitorHub.get().setPingTarget(tag);
            }
        });
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(pingTargets);
        addCard(c, hs);

        cPing = new InfoCard(ctx, R.string.card_live_ping, "ic_ping");
        cPing.setAction("ic_stop", R.string.action_pause, v -> togglePingPause());
        pingBig = new TextView(ctx);
        pingBig.setTextSize(44);
        pingBig.setTypeface(pingBig.getTypeface(), android.graphics.Typeface.BOLD);
        pingBig.setTextDirection(View.TEXT_DIRECTION_LTR);
        pingBig.setTextColor(UiKit.onSurface(ctx));
        cPing.addCustom(pingBig);
        pingQuality = new TextView(ctx);
        pingQuality.setTextSize(14);
        cPing.body().addView(pingQuality);
        cPing.row("target", R.string.row_target);
        cPing.row("method", R.string.row_method);
        cPing.row("min", R.string.row_min);
        cPing.row("avg", R.string.row_avg);
        cPing.row("max", R.string.row_max);
        cPing.row("jitter", R.string.row_jitter);
        cPing.row("loss", R.string.row_loss);
        cPing.row("sent", R.string.row_sent);
        addCard(c, cPing);

        cPingHistory = new InfoCard(ctx, R.string.card_ping_history, "ic_activity");
        cPingHistory.setAction("ic_refresh", R.string.action_reset, v -> MonitorHub.get().resetPing());
        pingChart = new SparklineView(ctx);
        pingChart.setGapsOnNegative(true);
        pingChart.setColors(UiKit.primary(ctx), UiKit.color(ctx, "colorTertiary", 0xFFFFAA00));
        cPingHistory.addCustom(pingChart);
        pingChart.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.chart_height);
        addCard(c, cPingHistory);

        cCompare = new InfoCard(ctx, R.string.card_compare, "ic_world");
        compareHosts.clear();
        compareHosts.add("1.1.1.1");
        compareHosts.add("8.8.8.8");
        compareHosts.add("google.com");
        compareHosts.add("github.com");
        for (String h : compareHosts) cCompare.row(h, h);
        cCompare.setAction("ic_play", R.string.action_test_all, v -> runCompare());
        addCard(c, cCompare);
    }

    private void updateCustomChip() {
        String custom = AppPreferences.getPingCustom(ctx);
        chipCustom.setText(custom.isEmpty() ? getString(R.string.target_custom) : custom);
    }

    private void promptCustomTarget() {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_port_input, null);
        TextInputEditText field = v.findViewById(R.id.inputField);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setText(AppPreferences.getPingCustom(ctx));
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.target_custom_title)
                .setView(v)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String host = String.valueOf(field.getText()).trim();
                    if (!host.isEmpty()) {
                        AppPreferences.setPingCustom(ctx, host);
                        MonitorHub.get().setPingTarget(host);
                    }
                    updateCustomChip();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void togglePingPause() {
        MonitorHub h = MonitorHub.get();
        h.setPingPaused(!h.isPingPaused());
        cPing.setAction(h.isPingPaused() ? "ic_play" : "ic_stop",
                h.isPingPaused() ? R.string.action_resume : R.string.action_pause, v -> togglePingPause());
    }

    private void runCompare() {
        for (String h : compareHosts) cCompare.set(h, getString(R.string.status_checking));
        MonitorHub.get().testAll(compareHosts, (host, ms) ->
                cCompare.set(host, ms < 0 ? getString(R.string.status_no_reply) : UiKit.ms(ms), Quality.color(ctx, ms)));
    }

    private void buildLogs() {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int ph = getResources().getDimensionPixelSize(R.dimen.page_padding_h);
        root.setPadding(ph, 0, ph, 0);

        ChipGroup filters = new ChipGroup(ctx);
        filters.setSingleSelection(true);
        filters.setSelectionRequired(true);
        filters.setSingleLine(true);
        int[] labels = {R.string.filter_all, R.string.filter_proxy, R.string.filter_clients, R.string.filter_network, R.string.filter_ping};
        int[] cats = {-1, LogStore.CAT_PROXY, LogStore.CAT_CLIENT, LogStore.CAT_NETWORK, LogStore.CAT_PING};
        for (int i = 0; i < labels.length; i++) {
            Chip chip = (Chip) LayoutInflater.from(ctx).inflate(R.layout.item_tab_chip, filters, false);
            chip.setId(View.generateViewId());
            chip.setText(labels[i]);
            chip.setTag(cats[i]);
            filters.addView(chip);
            if (i == 0) chip.setChecked(true);
        }
        filters.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = g.findViewById(ids.get(0));
            if (chip != null) {
                logFilter = (Integer) chip.getTag();
                refreshLogs();
            }
        });
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(filters);
        root.addView(hs);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton share = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        share.setText(R.string.action_share);
        share.setIcon(UiKit.icon(ctx, "ic_share"));
        share.setOnClickListener(v -> shareLogs());
        MaterialButton clear = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        clear.setText(R.string.action_clear);
        clear.setIcon(UiKit.icon(ctx, "ic_trash"));
        clear.setOnClickListener(v -> {
            AppPreferences.performHaptic(v);
            LogStore.clear();
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        actions.addView(share, ap);
        LinearLayout.LayoutParams ap2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ap2.setMarginStart(UiKit.dp(ctx, 8));
        actions.addView(clear, ap2);
        UiKit.addSpaced(root, actions, 4);

        logRecycler = new RecyclerView(ctx);
        logRecycler.setLayoutManager(new LinearLayoutManager(ctx));
        logRecycler.setClipToPadding(false);
        logAdapter = new LogAdapter();
        logRecycler.setAdapter(logAdapter);
        host().setNavClearance(logRecycler);
        host().bindScrollPause(logRecycler);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rp.topMargin = UiKit.dp(ctx, 8);
        root.addView(logRecycler, rp);
        paneHost.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void refreshLogs() {
        if (logAdapter == null) return;
        logAdapter.set(LogStore.snapshot(logFilter));
    }

    private void shareLogs() {
        String text = LogStore.export(ctx, LogStore.snapshot(logFilter));
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, getString(R.string.action_share)));
    }

    private final class LogAdapter extends RecyclerView.Adapter<LogAdapter.VH> {
        private List<LogStore.Entry> items = new ArrayList<>();

        void set(List<LogStore.Entry> list) {
            items = list;
            notifyDataSetChanged();
        }

        final class VH extends RecyclerView.ViewHolder {
            final View dot;
            final TextView message;
            final TextView meta;

            VH(View v) {
                super(v);
                dot = v.findViewById(R.id.logDot);
                message = v.findViewById(R.id.logMessage);
                meta = v.findViewById(R.id.logMeta);
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log_row, parent, false));
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            LogStore.Entry e = items.get(position);
            Context c = h.itemView.getContext();
            int color = e.level == LogStore.LEVEL_ERROR ? UiKit.error(c)
                    : (e.level == LogStore.LEVEL_WARN ? UiKit.warning(c) : UiKit.success(c));
            h.dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            h.message.setText(LogStore.message(c, e));
            h.meta.setText(LogStore.time(e) + "  ·  " + c.getString(categoryRes(e.category)));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static int categoryRes(int cat) {
        switch (cat) {
            case LogStore.CAT_PROXY:
                return R.string.filter_proxy;
            case LogStore.CAT_CLIENT:
                return R.string.filter_clients;
            case LogStore.CAT_NETWORK:
                return R.string.filter_network;
            case LogStore.CAT_PING:
                return R.string.filter_ping;
            default:
                return R.string.filter_app;
        }
    }

    private void startScan() {
        if (cDevices == null || DeviceScanner.isScanning()) return;
        scanStarted = true;
        scanBar.setVisibility(View.VISIBLE);
        scanBar.setProgressCompat(0, false);
        scanStatus.setText(R.string.scan_running);
        LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_NETWORK, R.string.log_scan_started);
        DeviceScanner.scan(ctx, new DeviceScanner.Callback() {
            @Override
            public void onProgress(int done, int total) {
                if (!isAdded() || total == 0) return;
                scanBar.setProgressCompat(done * 100 / total, true);
            }

            @Override
            public void onResult(List<DeviceScanner.Device> result) {
                if (!isAdded()) return;
                devices = result;
                scanBar.setVisibility(View.GONE);
                scanStatus.setText(getString(R.string.scan_done, String.valueOf(result.size())));
                LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_NETWORK, R.string.log_scan_done, String.valueOf(result.size()));
                renderDevices();
            }
        });
    }

    private void renderDevices() {
        deviceList.removeAllViews();
        for (DeviceScanner.Device d : devices) {
            View row = LayoutInflater.from(ctx).inflate(R.layout.item_client_row, deviceList, false);
            ShapeView avatar = row.findViewById(R.id.clientAvatar);
            String icon = d.self ? "ic_mobile" : (d.gateway ? "ic_router" : "ic_devices");
            avatar.setFillColor(UiKit.color(ctx, d.self ? "colorPrimaryContainer" : "colorSecondaryContainer", 0xFF444444));
            avatar.setIcon(UiKit.icon(ctx, icon), UiKit.color(ctx,
                    d.self ? "colorOnPrimaryContainer" : "colorOnSecondaryContainer", 0xFFFFFFFF));
            ((TextView) row.findViewById(R.id.clientTitle)).setText(d.name.isEmpty() ? d.ip : d.name);
            List<String> sub = new ArrayList<>();
            if (!d.name.isEmpty()) sub.add(d.ip);
            if (!d.mac.isEmpty()) sub.add(d.mac + (d.privateMac ? " (" + getString(R.string.badge_private_mac) + ")" : ""));
            if (d.self) sub.add(getString(R.string.badge_self));
            if (d.gateway) sub.add(getString(R.string.badge_gateway));
            if (d.proxyClient) sub.add(getString(R.string.badge_proxy));
            ((TextView) row.findViewById(R.id.clientSubtitle)).setText(join(sub, "  ·  "));
            TextView trailing = row.findViewById(R.id.clientTrailing);
            if (d.proxyClient) trailing.setText(TrafficMonitor.formatBytes(d.rx + d.tx));
            else if (d.rttMs >= 0) trailing.setText(UiKit.ms(d.rttMs));
            else trailing.setText("");
            deviceList.addView(row);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        renderTab();
    }

    private static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static String orDash(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }

    private static String bw(int kbps) {
        if (kbps <= 0) return "—";
        return String.format(Locale.US, "%.1f Mbps", kbps / 1000f);
    }

    private String standardName(int s) {
        switch (s) {
            case 1:
                return "802.11a/b/g";
            case 4:
                return "Wi-Fi 4 (802.11n)";
            case 5:
                return "Wi-Fi 5 (802.11ac)";
            case 6:
                return "Wi-Fi 6 (802.11ax)";
            case 7:
                return "802.11ad";
            case 8:
                return "Wi-Fi 7 (802.11be)";
            default:
                return "—";
        }
    }

    private int signalLabel(int percent) {
        if (percent >= 75) return R.string.quality_excellent;
        if (percent >= 50) return R.string.quality_good;
        if (percent >= 30) return R.string.quality_fair;
        return R.string.quality_poor;
    }

    private void renderTab() {
        switch (currentTab) {
            case TAB_OVERVIEW:
                renderOverview();
                break;
            case TAB_WIFI:
                renderWifi();
                break;
            case TAB_VPN:
                renderVpn();
                break;
            case TAB_PING:
                renderPing();
                break;
            default:
                break;
        }
    }

    private void renderPublicIp(InfoCard card, boolean full) {
        MonitorHub h = MonitorHub.get();
        PublicIpLookup.Info ip = h.publicIp;
        if (!AppPreferences.isOnlineChecks(ctx)) {
            card.set("ip", getString(R.string.status_disabled));
            return;
        }
        if (ip == null) {
            card.set("ip", getString(h.publicIpLoading ? R.string.status_checking : R.string.status_unavailable));
            return;
        }
        card.set("ip", orDash(ip.ip));
        String country = ip.country.isEmpty() ? "—" : (UiKit.flag(ip.countryCode) + " " + ip.country).trim();
        card.set("country", country);
        card.set("isp", orDash(ip.isp));
        if (full) {
            card.set("city", orDash(join(nonEmpty(ip.city, ip.region), ", ")));
            card.set("org", orDash(join(nonEmpty(ip.org, ip.asn), " · ")));
            card.set("tz", orDash(ip.timezone));
        }
    }

    private List<String> nonEmpty(String a, String b) {
        List<String> l = new ArrayList<>();
        if (a != null && !a.isEmpty()) l.add(a);
        if (b != null && !b.isEmpty()) l.add(b);
        return l;
    }

    private void renderOverview() {
        if (cInternet == null) return;
        MonitorHub h = MonitorHub.get();
        int success = UiKit.success(ctx);
        int warn = UiKit.warning(ctx);
        int error = UiKit.error(ctx);
        switch (h.connState) {
            case MonitorHub.CONN_ONLINE:
                cInternet.set("status", getString(R.string.conn_online), success);
                break;
            case MonitorHub.CONN_LIMITED:
                cInternet.set("status", getString(R.string.conn_limited), warn);
                break;
            case MonitorHub.CONN_OFFLINE:
                cInternet.set("status", getString(R.string.conn_offline), error);
                break;
            default:
                cInternet.set("status", getString(R.string.status_checking), UiKit.onSurface(ctx));
                break;
        }
        cInternet.set("http", h.httpMs < 0 ? "—" : h.httpMs + " ms");
        cInternet.set("dns", h.dnsMs < 0 ? "—" : h.dnsMs + " ms");
        PingStats ps = h.pingStats;
        cInternet.set("ping", UiKit.ms(ps.avg()));
        cInternet.set("loss", ps.sent == 0 ? "—" : ps.lossPercent() + "%");
        renderPublicIp(cInternet, false);

        NetworkInfoReader.Active a = h.active;
        String type = a.wifi ? "Wi-Fi" : a.cellular ? getString(R.string.net_cellular) : a.ethernet ? "Ethernet" : getString(R.string.net_none);
        if (a.vpn) type = type + " + VPN";
        cNet.set("type", type);
        cNet.set("iface", orDash(a.iface));
        cNet.set("ip", orDash(a.ip));
        cNet.set("gateway", orDash(a.gateway));
        cNet.set("dns", orDash(join(a.dns, ", ")));
        cNet.set("validated", a.validated ? getString(R.string.yes) : getString(R.string.no));
        cNet.set("metered", a.metered ? getString(R.string.yes) : getString(R.string.no));
        cNet.set("bw", bw(a.downKbps) + " / " + bw(a.upKbps));
        NetworkInfoReader.Cell cell = NetworkInfoReader.cellular(ctx);
        cNet.showRow("cell", cell.active || !cell.operator.isEmpty());
        cNet.set("cell", orDash(cell.operator) + (cell.roaming ? " (" + getString(R.string.roaming) + ")" : ""));

        cTraffic.set("down", TrafficMonitor.formatSpeed(h.rxRate));
        cTraffic.set("up", TrafficMonitor.formatSpeed(h.txRate));
        cTraffic.set("totalDown", TrafficMonitor.formatBytes(h.totalRx));
        cTraffic.set("totalUp", TrafficMonitor.formatBytes(h.totalTx));
        cTraffic.set("mobile", TrafficMonitor.formatBytes(h.mobileRx + h.mobileTx));
        cTraffic.set("wifi", TrafficMonitor.formatBytes(Math.max(0, h.totalRx + h.totalTx - h.mobileRx - h.mobileTx)));
        float[] rx = h.rxSeries.toArray();
        float[] tx = h.txSeries.toArray();
        float max = 0;
        for (float v : rx) max = Math.max(max, v);
        for (float v : tx) max = Math.max(max, v);
        trafficChart.setMaxLabel(TrafficMonitor.formatSpeed((long) max));
        trafficChart.setSeries(rx, tx);

        boolean running = ProxyService.isRunning;
        cProxy.set("state", running ? getString(R.string.status_active) : getString(R.string.status_inactive),
                running ? success : error);
        cProxy.set("down", TrafficMonitor.formatSpeed(h.proxyRxRate));
        cProxy.set("up", TrafficMonitor.formatSpeed(h.proxyTxRate));
        cProxy.set("totalDown", TrafficMonitor.formatBytes(TrafficMonitor.rxBytes.get()));
        cProxy.set("totalUp", TrafficMonitor.formatBytes(TrafficMonitor.txBytes.get()));
        cProxy.set("clients", String.valueOf(h.clientCount()));
        cProxy.set("active", String.valueOf(TrafficMonitor.activeConnections.get()));
        cProxy.set("total", String.valueOf(TrafficMonitor.totalConnections.get()));
        cProxy.set("failed", String.valueOf(TrafficMonitor.failedConnections.get()));
        cProxy.set("uptime", running ? UiKit.duration(System.currentTimeMillis() - TrafficMonitor.startedAt) : "—");
    }

    private void renderWifi() {
        if (cWifi == null) return;
        NetworkInfoReader.Wifi w = NetworkInfoReader.wifi(ctx);
        wifiPermCard.setVisibility(w.locationGranted ? View.GONE : View.VISIBLE);
        int success = UiKit.success(ctx);
        int error = UiKit.error(ctx);
        if (!w.enabled) cWifi.set("status", getString(R.string.wifi_off), error);
        else if (!w.connected) cWifi.set("status", getString(R.string.status_disconnected), error);
        else cWifi.set("status", getString(R.string.status_connected), success);
        String ssidFallback = w.connected ? getString(R.string.wifi_hidden) : "—";
        cWifi.set("ssid", w.ssid.isEmpty() ? ssidFallback : w.ssid);
        cWifi.set("bssid", w.bssid.isEmpty() ? ssidFallback : w.bssid);
        if (w.connected && w.rssi > -127) {
            int pct = NetworkInfoReader.signalPercent(w.rssi);
            int color = Quality.signalColor(ctx, pct);
            cWifi.set("signal", w.rssi + " dBm · " + getString(signalLabel(pct)), color);
            signalBar.setIndicatorColor(color);
            signalBar.setProgressCompat(pct, true);
        } else {
            cWifi.reset("signal");
            signalBar.setProgressCompat(0, false);
        }
        cWifi.set("link", w.linkMbps > 0 ? w.linkMbps + " Mbps" : "—");
        cWifi.set("txrx", w.txMbps > 0 || w.rxMbps > 0 ? "↑ " + w.txMbps + " · ↓ " + w.rxMbps + " Mbps" : "—");
        String band = w.freq <= 0 ? "—" : (w.freq < 3000 ? "2.4 GHz" : (w.freq < 5900 ? "5 GHz" : "6 GHz")) + " (" + w.freq + " MHz)";
        cWifi.set("band", band);
        cWifi.set("channel", w.channel > 0 ? String.valueOf(w.channel) : "—");
        cWifi.set("width", w.widthMhz > 0 ? w.widthMhz + " MHz" : "—");
        cWifi.set("standard", standardName(w.standard));
        cWifi.set("security", w.security.isEmpty() ? "—" : w.security);

        cWifiIp.set("iface", orDash(w.iface));
        cWifiIp.set("ip", w.ip.isEmpty() ? "—" : w.ip + "/" + w.prefix);
        cWifiIp.set("gateway", orDash(w.gateway));
        cWifiIp.set("dns", orDash(join(w.dns, ", ")));
        cWifiIp.set("dhcp", orDash(w.dhcpServer));
        cWifiIp.set("lease", w.leaseSeconds > 0 ? UiKit.duration(w.leaseSeconds * 1000L) : "—");
        cWifiIp.set("mtu", w.mtu > 0 ? String.valueOf(w.mtu) : "—");
        cWifiIp.set("ipv6", orDash(join(w.ipv6, "\n")));
        cWifiIp.set("domain", orDash(w.domains));
        cWifiIp.set("pdns", w.privateDns ? (w.privateDnsName.isEmpty() ? getString(R.string.status_on) : w.privateDnsName)
                : getString(R.string.status_off));
        cWifiIp.set("rx", TrafficMonitor.formatBytes(w.rxBytes));
        cWifiIp.set("tx", TrafficMonitor.formatBytes(w.txBytes));

        MonitorHub h = MonitorHub.get();
        cHotspot.set("state", h.hotspot ? getString(R.string.status_on) : getString(R.string.status_off),
                h.hotspot ? success : UiKit.onSurface(ctx));
        String hIface = "—";
        String hIp = "—";
        boolean tether = false;
        for (NetworkUtils.LocalAddress a : NetworkUtils.localAddresses(ctx)) {
            if (a.ap && hIface.equals("—")) {
                hIface = a.iface;
                hIp = a.ip + "/" + a.prefix;
            }
            if (a.tether) tether = true;
        }
        cHotspot.set("iface", hIface);
        cHotspot.set("ip", hIp);
        cHotspot.set("tether", tether ? getString(R.string.status_on) : getString(R.string.status_off));
        cHotspot.set("clients", String.valueOf(h.clientCount()));
    }

    private void renderVpn() {
        if (cVpn == null) return;
        NetworkInfoReader.Vpn v = NetworkInfoReader.vpn(ctx);
        MonitorHub h = MonitorHub.get();
        int success = UiKit.success(ctx);
        int error = UiKit.error(ctx);
        cVpn.set("status", v.active ? getString(R.string.status_connected) : getString(R.string.status_disconnected),
                v.active ? success : error);
        cVpn.set("app", orDash(v.app));
        cVpn.set("protocol", orDash(v.protocol));
        cVpn.set("iface", orDash(v.iface));
        cVpn.set("underlying", orDash(v.underlying));
        cVpn.set("validated", v.active ? (v.validated ? getString(R.string.yes) : getString(R.string.no)) : "—");
        cVpn.set("since", h.vpnSince > 0 ? UiKit.duration(System.currentTimeMillis() - h.vpnSince) : "—");
        cVpn.showRow("app", v.active && !v.app.isEmpty());
        cVpnNet.setVisibility(v.active ? View.VISIBLE : View.GONE);
        cVpnTraffic.setVisibility(v.active ? View.VISIBLE : View.GONE);
        if (v.active) {
            cVpnNet.set("addr", orDash(join(v.addresses, "\n")));
            cVpnNet.set("dns", orDash(join(v.dns, ", ")));
            cVpnNet.set("routes", v.routeCount + (v.routes.isEmpty() ? "" : "\n" + join(v.routes, "\n")));
            cVpnNet.set("default", v.defaultRoute ? getString(R.string.yes) : getString(R.string.no));
            cVpnNet.set("mtu", v.mtu > 0 ? String.valueOf(v.mtu) : "—");
            cVpnNet.set("bw", bw(v.downKbps) + " / " + bw(v.upKbps));
            cVpnTraffic.set("rx", TrafficMonitor.formatBytes(v.rxBytes));
            cVpnTraffic.set("tx", TrafficMonitor.formatBytes(v.txBytes));
        }
        renderPublicIp(cExit, true);
        cRoute.set("route", v.active && v.defaultRoute
                ? getString(R.string.route_via_vpn, orDash(v.iface)) : getString(R.string.route_direct));
    }

    private void renderPing() {
        if (cPing == null) return;
        MonitorHub h = MonitorHub.get();
        PingStats ps = h.pingStats;
        float last = ps.last;
        pingBig.setText(ps.sent == 0 ? "—" : (last < 0 ? getString(R.string.status_no_reply) : UiKit.ms(last)));
        pingQuality.setText(getString(Quality.labelRes(ps.sent == 0 ? -1 : last)));
        pingQuality.setTextColor(Quality.color(ctx, ps.sent == 0 ? -1 : last));
        String host = h.pingTarget.isEmpty() ? h.resolvePingHost() : h.pingTarget;
        cPing.set("target", orDash(host));
        cPing.set("method", orDash(h.pingMethod));
        cPing.set("min", UiKit.ms(ps.min));
        cPing.set("avg", UiKit.ms(ps.avg()));
        cPing.set("max", UiKit.ms(ps.max));
        cPing.set("jitter", UiKit.ms(ps.jitter()));
        cPing.set("loss", ps.sent == 0 ? "—" : ps.lossPercent() + "%");
        cPing.set("sent", ps.received + " / " + ps.sent);
        float[] series = h.pingSeries.toArray();
        float max = 0;
        for (float v : series) max = Math.max(max, v);
        pingChart.setMaxLabel(UiKit.ms(max));
        pingChart.setSeries(series, null);
    }
}
