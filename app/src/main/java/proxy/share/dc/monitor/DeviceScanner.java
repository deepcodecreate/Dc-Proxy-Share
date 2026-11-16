/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.monitor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import proxy.share.dc.ClientRegistry;
import proxy.share.dc.NetworkUtils;

public final class DeviceScanner {
    public static final class Device {
        public String ip = "";
        public String name = "";
        public String mac = "";
        public String iface = "";
        public boolean self;
        public boolean gateway;
        public boolean privateMac;
        public boolean proxyClient;
        public float rttMs = -1;
        public long rx;
        public long tx;
        public int active;
    }

    public interface Callback {
        void onProgress(int done, int total);

        void onResult(List<Device> devices);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean scanning;

    private DeviceScanner() {
    }

    public static boolean isScanning() {
        return scanning;
    }

    private static long toLong(String ip) {
        String[] p = ip.split("\\.");
        long v = 0;
        for (String s : p) v = (v << 8) | Integer.parseInt(s);
        return v;
    }

    private static String fromLong(long v) {
        return ((v >> 24) & 0xFF) + "." + ((v >> 16) & 0xFF) + "." + ((v >> 8) & 0xFF) + "." + (v & 0xFF);
    }

    private static Map<String, String> readArp() {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line = r.readLine();
            while ((line = r.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length >= 4 && !p[3].equals("00:00:00:00:00:00") && p[3].contains(":")) {
                    map.put(p[0], p[3].toUpperCase());
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private static boolean isPrivateMac(String mac) {
        if (mac == null || mac.length() < 2) return false;
        try {
            int first = Integer.parseInt(mac.substring(0, 2), 16);
            return (first & 0x02) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static float probe(String ip) {
        try {
            InetAddress a = InetAddress.getByName(ip);
            long t0 = System.nanoTime();
            if (a.isReachable(250)) return (System.nanoTime() - t0) / 1_000_000f;
            for (int port : new int[]{80, 443}) {
                Socket s = new Socket();
                long t1 = System.nanoTime();
                try {
                    s.connect(new InetSocketAddress(a, port), 150);
                    return (System.nanoTime() - t1) / 1_000_000f;
                } catch (ConnectException e) {
                    String m = String.valueOf(e.getMessage()).toLowerCase();
                    if (m.contains("refused")) return (System.nanoTime() - t1) / 1_000_000f;
                } catch (Exception ignored) {
                } finally {
                    try {
                        s.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    public static void scan(Context context, Callback cb) {
        if (scanning) return;
        scanning = true;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            List<Device> result = new ArrayList<>();
            try {
                result = run(app, cb);
            } catch (Exception ignored) {
            }
            final List<Device> out = result;
            scanning = false;
            MAIN.post(() -> cb.onResult(out));
        }).start();
    }

    private static List<Device> run(Context ctx, Callback cb) {
        List<NetworkUtils.LocalAddress> locals = NetworkUtils.localAddresses(ctx);
        Set<String> selfIps = new HashSet<>();
        for (NetworkUtils.LocalAddress l : locals) selfIps.add(l.ip);
        String gateway = NetworkInfoReader.gateway(ctx);

        final Map<String, Device> found = new ConcurrentHashMap<>();
        final List<String[]> targets = new ArrayList<>();
        for (NetworkUtils.LocalAddress l : locals) {
            long ip = toLong(l.ip);
            int prefix = l.prefix >= 24 ? l.prefix : 24;
            long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            long net = ip & mask;
            long bcast = net | (~mask & 0xFFFFFFFFL);
            for (long h = net + 1; h < bcast; h++) {
                String s = fromLong(h);
                if (!selfIps.contains(s)) targets.add(new String[]{s, l.iface});
            }
        }
        final int total = targets.size();
        final AtomicInteger done = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(48);
        ExecutorService names = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        for (String[] t : targets) {
            futures.add(pool.submit(() -> {
                float rtt = probe(t[0]);
                if (rtt >= 0) {
                    Device d = new Device();
                    d.ip = t[0];
                    d.iface = t[1];
                    d.rttMs = rtt;
                    found.put(d.ip, d);
                }
                int n = done.incrementAndGet();
                if (n % 8 == 0 || n == total) MAIN.post(() -> cb.onProgress(n, total));
            }));
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception ignored) {
            }
        }
        pool.shutdown();

        Map<String, String> arp = readArp();
        List<Future<?>> nameFutures = new ArrayList<>();
        for (Device d : found.values()) {
            final Device dev = d;
            nameFutures.add(names.submit(() -> {
                try {
                    String n = InetAddress.getByName(dev.ip).getCanonicalHostName();
                    if (n != null && !n.equals(dev.ip)) dev.name = n;
                } catch (Exception ignored) {
                }
            }));
        }
        for (Future<?> f : nameFutures) {
            try {
                f.get(900, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
        }
        names.shutdownNow();

        for (ClientRegistry.Client c : ClientRegistry.snapshot()) {
            Device d = found.get(c.ip);
            if (d == null) {
                d = new Device();
                d.ip = c.ip;
                found.put(c.ip, d);
            }
            d.proxyClient = true;
            d.rx = c.rx.get();
            d.tx = c.tx.get();
            d.active = c.active.get();
        }
        for (NetworkUtils.LocalAddress l : locals) {
            Device d = new Device();
            d.ip = l.ip;
            d.iface = l.iface;
            d.self = true;
            found.put(l.ip, d);
        }
        List<Device> list = new ArrayList<>(found.values());
        for (Device d : list) {
            String mac = arp.get(d.ip);
            if (mac != null) {
                d.mac = mac;
                d.privateMac = isPrivateMac(mac);
            }
            if (d.ip.equals(gateway)) d.gateway = true;
        }
        Collections.sort(list, (a, b) -> {
            if (a.self != b.self) return a.self ? -1 : 1;
            if (a.gateway != b.gateway) return a.gateway ? -1 : 1;
            return Long.compare(toLong(a.ip), toLong(b.ip));
        });
        return list;
    }
}
