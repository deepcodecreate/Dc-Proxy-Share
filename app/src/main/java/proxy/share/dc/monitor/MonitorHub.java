/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.monitor;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import proxy.share.dc.ClientRegistry;
import proxy.share.dc.LogStore;
import proxy.share.dc.NetworkUtils;
import proxy.share.dc.R;
import proxy.share.dc.TrafficMonitor;
import proxy.share.dc.ui.AppPreferences;

public final class MonitorHub {
    public interface Listener {
        void onTick();
    }

    public static final int HISTORY = 60;
    public static final int CONN_UNKNOWN = 0;
    public static final int CONN_ONLINE = 1;
    public static final int CONN_LIMITED = 2;
    public static final int CONN_OFFLINE = 3;

    private static MonitorHub instance;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService worker = Executors.newFixedThreadPool(6);
    private final ScheduledExecutorService pingExec = Executors.newSingleThreadScheduledExecutor();
    private boolean pingRunning;
    private boolean pingPaused;

    public long totalRx;
    public long totalTx;
    public long mobileRx;
    public long mobileTx;
    public long rxRate;
    public long txRate;
    public long proxyRxRate;
    public long proxyTxRate;
    public final Series rxSeries = new Series(HISTORY);
    public final Series txSeries = new Series(HISTORY);
    public final Series proxyRxSeries = new Series(HISTORY);
    public final Series proxyTxSeries = new Series(HISTORY);
    public final Series pingSeries = new Series(HISTORY);
    public NetworkInfoReader.Active active = new NetworkInfoReader.Active();
    public boolean hotspot;
    public boolean vpn;
    public String vpnIface = "";
    public long vpnSince;
    public int connState = CONN_UNKNOWN;
    public long httpMs = -1;
    public long dnsMs = -1;
    public PingStats pingStats = new PingStats();
    public String pingMethod = "";
    public String pingTarget = "";
    public PublicIpLookup.Info publicIp;
    public boolean publicIpLoading;

    private long lastRx = -1;
    private long lastTx = -1;
    private long lastProxyRx;
    private long lastProxyTx;
    private long lastTick;
    private long lastProbe;
    private long lastIpFetch;
    private boolean probing;
    private boolean forceRefresh = true;
    private boolean lastHotspot;
    private boolean lastVpn;
    private int failStreak;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            main.postDelayed(this, 1000);
        }
    };

    private MonitorHub(Context c) {
        ctx = c;
    }

    public static void init(Context c) {
        if (instance == null) {
            instance = new MonitorHub(c.getApplicationContext());
            instance.registerCallbacks();
        }
    }

    public static MonitorHub get() {
        return instance;
    }

    private String transportName(NetworkCapabilities c) {
        if (c == null) return "?";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Cellular";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        return "Other";
    }

    private void registerCallbacks() {
        final ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        try {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network n) {
                    String t = transportName(cm.getNetworkCapabilities(n));
                    LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_NETWORK, R.string.log_net_available, t);
                    forceRefresh = true;
                }

                @Override
                public void onLost(Network n) {
                    LogStore.add(LogStore.LEVEL_WARN, LogStore.CAT_NETWORK, R.string.log_net_lost);
                    forceRefresh = true;
                }
            });
        } catch (Exception ignored) {
        }
        try {
            NetworkRequest req = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build();
            cm.registerNetworkCallback(req, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network n) {
                    LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_NETWORK, R.string.log_vpn_up);
                    forceRefresh = true;
                }

                @Override
                public void onLost(Network n) {
                    LogStore.add(LogStore.LEVEL_WARN, LogStore.CAT_NETWORK, R.string.log_vpn_down);
                    forceRefresh = true;
                }
            });
        } catch (Exception ignored) {
        }
    }

    public void attach(Listener l) {
        boolean first = listeners.isEmpty();
        listeners.addIfAbsent(l);
        if (first) {
            main.post(ticker);
            startPing();
        }
    }

    public void detach(Listener l) {
        if (listeners.remove(l) && listeners.isEmpty()) {
            main.removeCallbacks(ticker);
            stopPing();
        }
    }

    public boolean isPingPaused() {
        return pingPaused;
    }

    public void setPingPaused(boolean paused) {
        pingPaused = paused;
    }

    public void refreshNow() {
        forceRefresh = true;
        lastProbe = 0;
        lastIpFetch = 0;
    }

    public String resolvePingHost() {
        String t = AppPreferences.getPingTarget(ctx);
        if (AppPreferences.TARGET_GATEWAY.equals(t)) return NetworkInfoReader.gateway(ctx);
        return t;
    }

    public void setPingTarget(String t) {
        AppPreferences.setPingTarget(ctx, t);
        resetPing();
    }

    public void resetPing() {
        pingStats.reset();
        pingSeries.clear();
        failStreak = 0;
    }

    private void tick() {
        long now = SystemClock.elapsedRealtime();
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        totalRx = Math.max(0, rx);
        totalTx = Math.max(0, tx);
        mobileRx = Math.max(0, TrafficStats.getMobileRxBytes());
        mobileTx = Math.max(0, TrafficStats.getMobileTxBytes());
        long prx = TrafficMonitor.rxBytes.get();
        long ptx = TrafficMonitor.txBytes.get();
        if (lastTick > 0 && now > lastTick) {
            long dt = now - lastTick;
            rxRate = lastRx < 0 || rx < lastRx ? 0 : (rx - lastRx) * 1000 / dt;
            txRate = lastTx < 0 || tx < lastTx ? 0 : (tx - lastTx) * 1000 / dt;
            proxyRxRate = prx < lastProxyRx ? 0 : (prx - lastProxyRx) * 1000 / dt;
            proxyTxRate = ptx < lastProxyTx ? 0 : (ptx - lastProxyTx) * 1000 / dt;
            rxSeries.add(rxRate);
            txSeries.add(txRate);
            proxyRxSeries.add(proxyRxRate);
            proxyTxSeries.add(proxyTxRate);
        }
        lastTick = now;
        lastRx = rx;
        lastTx = tx;
        lastProxyRx = prx;
        lastProxyTx = ptx;

        active = NetworkInfoReader.active(ctx);
        boolean nowVpn = NetworkUtils.isVpnActive(ctx);
        if (nowVpn != lastVpn) {
            lastVpn = nowVpn;
            forceRefresh = true;
            vpnSince = nowVpn ? System.currentTimeMillis() : 0;
        }
        vpn = nowVpn;
        if (vpn) vpnIface = NetworkInfoReader.vpn(ctx).iface;
        boolean nowHotspot = NetworkUtils.isHotspotActive(ctx);
        if (nowHotspot != lastHotspot) {
            lastHotspot = nowHotspot;
            LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_NETWORK,
                    nowHotspot ? R.string.log_hotspot_on : R.string.log_hotspot_off);
        }
        hotspot = nowHotspot;

        boolean checks = AppPreferences.isOnlineChecks(ctx);
        if (checks && !probing && (forceRefresh || now - lastProbe > 10000)) {
            lastProbe = now;
            probe();
        }
        if (checks && !publicIpLoading && (forceRefresh || publicIp == null && now - lastIpFetch > 15000
                || now - lastIpFetch > 180000)) {
            lastIpFetch = now;
            fetchPublicIp();
        }
        if (!checks) {
            connState = active.connected ? (active.validated ? CONN_ONLINE : CONN_LIMITED) : CONN_OFFLINE;
        }
        forceRefresh = false;
        for (Listener l : listeners) l.onTick();
    }

    public int clientCount() {
        return ClientRegistry.snapshot().size();
    }

    private void probe() {
        probing = true;
        worker.submit(() -> {
            int state;
            long http = -1;
            long dns = -1;
            if (!active.connected) {
                state = CONN_OFFLINE;
            } else {
                long t0 = System.nanoTime();
                int code = -1;
                boolean ssl = false;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL("https://www.gstatic.com/generate_204").openConnection();
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setInstanceFollowRedirects(false);
                    c.setUseCaches(false);
                    code = c.getResponseCode();
                    c.disconnect();
                } catch (SSLException e) {
                    ssl = true;
                } catch (Exception ignored) {
                }
                http = (System.nanoTime() - t0) / 1_000_000;
                state = code == 204 ? CONN_ONLINE : (code > 0 || ssl ? CONN_LIMITED : CONN_OFFLINE);
                if (state == CONN_OFFLINE) http = -1;
                long d0 = System.nanoTime();
                try {
                    InetAddress.getByName("p" + Long.toHexString(new Random().nextLong() & 0xFFFFFFFFL) + ".google.com");
                } catch (Exception ignored) {
                }
                dns = (System.nanoTime() - d0) / 1_000_000;
                if (dns > 4000) dns = -1;
            }
            final int fs = state;
            final long fh = http;
            final long fd = dns;
            main.post(() -> {
                if (fs != connState && connState != CONN_UNKNOWN) {
                    int res = fs == CONN_ONLINE ? R.string.log_online
                            : (fs == CONN_LIMITED ? R.string.log_limited : R.string.log_offline);
                    LogStore.add(fs == CONN_ONLINE ? LogStore.LEVEL_INFO : LogStore.LEVEL_WARN, LogStore.CAT_NETWORK, res);
                }
                connState = fs;
                httpMs = fh;
                dnsMs = fd;
                probing = false;
            });
        });
    }

    private void fetchPublicIp() {
        publicIpLoading = true;
        worker.submit(() -> {
            PublicIpLookup.Info info = PublicIpLookup.fetch();
            main.post(() -> {
                if (info != null) {
                    boolean changed = publicIp != null && !publicIp.ip.equals(info.ip);
                    publicIp = info;
                    if (changed) {
                        LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_NETWORK, R.string.log_ip_changed, info.ip);
                    }
                }
                publicIpLoading = false;
            });
        });
    }

    private void startPing() {
        if (pingRunning) return;
        pingRunning = true;
        pingExec.schedule(pingTask, 0, TimeUnit.MILLISECONDS);
    }

    private void stopPing() {
        pingRunning = false;
    }

    private final Runnable pingTask = new Runnable() {
        @Override
        public void run() {
            if (!pingRunning) return;
            if (!pingPaused) {
                final String host = resolvePingHost();
                final PingEngine.Result r = host == null || host.isEmpty() ? null : PingEngine.ping(host, 2000);
                main.post(() -> onPing(host, r));
            }
            if (pingRunning) {
                pingExec.schedule(this, AppPreferences.getPingInterval(ctx), TimeUnit.MILLISECONDS);
            }
        }
    };

    private void onPing(String host, PingEngine.Result r) {
        pingTarget = host == null ? "" : host;
        float ms = r == null ? -1 : r.ms;
        if (r != null) pingMethod = r.method;
        pingStats.add(ms);
        pingSeries.add(ms);
        if (ms < 0) {
            failStreak++;
            if (failStreak == 3) {
                LogStore.add(LogStore.LEVEL_WARN, LogStore.CAT_PING, R.string.log_ping_timeout, pingTarget);
            }
        } else {
            if (failStreak >= 3) {
                LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_PING, R.string.log_ping_recovered,
                        pingTarget, String.valueOf(Math.round(ms)));
            }
            failStreak = 0;
        }
    }

    public void testAll(final List<String> hosts, final ResultCallback cb) {
        for (final String h : hosts) {
            worker.submit(() -> {
                PingEngine.Result r = PingEngine.ping(h, 2000);
                main.post(() -> cb.onResult(h, r.ms));
            });
        }
    }

    public interface ResultCallback {
        void onResult(String host, float ms);
    }
}
