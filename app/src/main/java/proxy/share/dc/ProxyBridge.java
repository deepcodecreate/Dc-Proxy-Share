/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

final class ProxyBridge {
    private ProxyBridge() {
    }

    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                int n = sb.length();
                if (n > 0 && sb.charAt(n - 1) == '\r') sb.setLength(n - 1);
                return sb.toString();
            }
            sb.append((char) b);
            if (sb.length() > 16384) throw new IOException("line too long");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static Network activeNetwork() {
        try {
            Context c = App.context;
            if (c == null) return null;
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm == null ? null : cm.getActiveNetwork();
        } catch (Exception e) {
            return null;
        }
    }

    static Socket connect(String host, int port, int timeoutMs) throws IOException {
        Network net = activeNetwork();
        InetAddress[] resolved = null;
        if (net != null) {
            try {
                resolved = net.getAllByName(host);
            } catch (Exception ignored) {
            }
        }
        if (resolved == null || resolved.length == 0) resolved = InetAddress.getAllByName(host);
        List<InetAddress> ordered = new ArrayList<>();
        for (InetAddress a : resolved) {
            if (a instanceof Inet4Address) ordered.add(a);
        }
        for (InetAddress a : resolved) {
            if (!(a instanceof Inet4Address)) ordered.add(a);
        }
        IOException last = null;
        for (InetAddress a : ordered) {
            Socket s = new Socket();
            try {
                if (net != null) {
                    try {
                        net.bindSocket(s);
                    } catch (Exception ignored) {
                    }
                }
                s.connect(new InetSocketAddress(a, port), timeoutMs);
                return s;
            } catch (IOException e) {
                last = e;
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
        }
        throw last != null ? last : new IOException("no route");
    }

    static String clientIp(Socket s) {
        try {
            String a = s.getInetAddress().getHostAddress();
            int i = a.indexOf('%');
            return i > 0 ? a.substring(0, i) : a;
        } catch (Exception e) {
            return "?";
        }
    }

    static void bridge(ExecutorService pool, Socket client, Socket remote, ClientRegistry.Client info) {
        TrafficMonitor.activeConnections.incrementAndGet();
        TrafficMonitor.totalConnections.incrementAndGet();
        final AtomicInteger left = new AtomicInteger(2);
        Runnable done = () -> {
            if (left.decrementAndGet() == 0) {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
                try {
                    remote.close();
                } catch (Exception ignored) {
                }
                TrafficMonitor.activeConnections.decrementAndGet();
                ClientRegistry.close(info);
            }
        };
        try {
            client.setSoTimeout(0);
            remote.setSoTimeout(0);
            client.setKeepAlive(true);
            remote.setKeepAlive(true);
        } catch (Exception ignored) {
        }
        pool.submit(() -> pipe(client, remote, true, info, done));
        pool.submit(() -> pipe(remote, client, false, info, done));
    }

    private static void pipe(Socket src, Socket dst, boolean upload, ClientRegistry.Client info, Runnable done) {
        byte[] buf = new byte[16384];
        try {
            InputStream in = src.getInputStream();
            OutputStream out = dst.getOutputStream();
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
                info.lastSeen = System.currentTimeMillis();
                if (upload) {
                    TrafficMonitor.txBytes.addAndGet(n);
                    info.tx.addAndGet(n);
                } else {
                    TrafficMonitor.rxBytes.addAndGet(n);
                    info.rx.addAndGet(n);
                }
            }
            try {
                dst.shutdownOutput();
            } catch (Exception ignored) {
            }
        } catch (IOException ignored) {
            try {
                src.close();
            } catch (Exception e) {
            }
            try {
                dst.close();
            } catch (Exception e) {
            }
        } finally {
            done.run();
        }
    }

    static void count(ClientRegistry.Client info, boolean upload, int n) {
        if (upload) {
            TrafficMonitor.txBytes.addAndGet(n);
            info.tx.addAndGet(n);
        } else {
            TrafficMonitor.rxBytes.addAndGet(n);
            info.rx.addAndGet(n);
        }
    }
}
