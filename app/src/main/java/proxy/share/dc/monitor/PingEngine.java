/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.monitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PingEngine {
    public static final class Result {
        public final float ms;
        public final String method;

        Result(float ms, String method) {
            this.ms = ms;
            this.method = method;
        }
    }

    private static final Pattern TIME = Pattern.compile("time[=<]\\s*([0-9.]+)\\s*ms");
    private static final Map<String, Integer> TCP_FIRST = new ConcurrentHashMap<>();
    private static volatile boolean icmpUnavailable;

    private PingEngine() {
    }

    public static Result ping(String host, int timeoutMs) {
        if (host == null || host.isEmpty()) return new Result(-1, "—");
        Integer skip = TCP_FIRST.get(host);
        boolean preferTcp = skip != null && skip > 0;
        if (icmpUnavailable || preferTcp) {
            if (preferTcp) TCP_FIRST.put(host, skip - 1);
            float t = tcp(host, timeoutMs);
            if (t >= 0) return new Result(t, "TCP");
            if (icmpUnavailable) return new Result(-1, "TCP");
            float i = icmp(host, timeoutMs);
            if (i >= 0) {
                TCP_FIRST.remove(host);
                return new Result(i, "ICMP");
            }
            return new Result(-1, "ICMP");
        }
        float i = icmp(host, timeoutMs);
        if (i >= 0) return new Result(i, "ICMP");
        float t = tcp(host, timeoutMs);
        if (t >= 0) {
            TCP_FIRST.put(host, 10);
            return new Result(t, "TCP");
        }
        return new Result(-1, "ICMP");
    }

    private static float icmp(String host, int timeoutMs) {
        Process p;
        try {
            int wait = Math.max(1, timeoutMs / 1000);
            p = new ProcessBuilder("/system/bin/ping", "-c", "1", "-W", String.valueOf(wait), host)
                    .redirectErrorStream(true).start();
        } catch (java.io.IOException e) {
            icmpUnavailable = true;
            return -1;
        }
        try {
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            p.waitFor(timeoutMs + 1500L, TimeUnit.MILLISECONDS);
            Matcher m = TIME.matcher(out);
            if (m.find()) return Float.parseFloat(m.group(1));
            return -1;
        } catch (Exception e) {
            return -1;
        } finally {
            p.destroy();
        }
    }

    private static float tcp(String host, int timeoutMs) {
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (Exception e) {
            return -1;
        }
        Socket s = new Socket();
        long t0 = System.nanoTime();
        try {
            s.connect(new InetSocketAddress(addr, 443), timeoutMs);
            return (System.nanoTime() - t0) / 1_000_000f;
        } catch (ConnectException e) {
            String msg = String.valueOf(e.getMessage()).toLowerCase();
            if (msg.contains("refused")) return (System.nanoTime() - t0) / 1_000_000f;
            return -1;
        } catch (Exception e) {
            return -1;
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }
}
