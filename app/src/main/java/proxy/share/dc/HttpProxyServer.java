/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpProxyServer implements Runnable {
    private final int port;
    private final boolean verbose;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public HttpProxyServer(int port, boolean verbose) {
        this.port = port;
        this.verbose = verbose;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port), 64);
            running = true;
            ProxyService.httpBound = true;
            LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_PROXY, R.string.log_http_listening, String.valueOf(port));
            while (running) {
                Socket c = serverSocket.accept();
                pool.submit(() -> handle(c));
            }
        } catch (IOException e) {
            if (!running && !ProxyService.httpBound) {
                LogStore.add(LogStore.LEVEL_ERROR, LogStore.CAT_PROXY, R.string.log_bind_failed, "HTTP", String.valueOf(port));
                ProxyService.bindError = true;
            }
        } finally {
            ProxyService.httpBound = false;
        }
    }

    private void handle(Socket client) {
        ClientRegistry.Client info = null;
        boolean bridged = false;
        try {
            client.setSoTimeout(15000);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            String requestLine = ProxyBridge.readLine(in);
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 3) return;
            String method = parts[0];
            String target = parts[1];
            String version = parts[2];
            List<String> headers = new ArrayList<>();
            String line;
            String hostHeader = null;
            while ((line = ProxyBridge.readLine(in)) != null && !line.isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("host:")) hostHeader = line.substring(5).trim();
                if (lower.startsWith("proxy-connection:") || lower.startsWith("proxy-authorization:")
                        || lower.startsWith("connection:") || lower.startsWith("keep-alive:")) {
                    continue;
                }
                headers.add(line);
            }
            info = ClientRegistry.open(ProxyBridge.clientIp(client));
            String host;
            int port;
            String path = null;
            boolean connect = method.equalsIgnoreCase("CONNECT");
            if (connect) {
                int idx = target.lastIndexOf(':');
                host = idx > 0 ? target.substring(0, idx) : target;
                port = idx > 0 ? Integer.parseInt(target.substring(idx + 1)) : 443;
            } else if (target.startsWith("http://")) {
                URL u = new URL(target);
                host = u.getHost();
                port = u.getPort() != -1 ? u.getPort() : 80;
                path = u.getFile().isEmpty() ? "/" : u.getFile();
            } else {
                if (hostHeader == null) return;
                int idx = hostHeader.lastIndexOf(':');
                host = idx > 0 ? hostHeader.substring(0, idx) : hostHeader;
                port = idx > 0 ? Integer.parseInt(hostHeader.substring(idx + 1)) : 80;
                path = target;
            }
            if (verbose) {
                LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_PROXY, R.string.log_http_request,
                        info.ip, method, host + ":" + port);
            }
            Socket remote;
            try {
                remote = ProxyBridge.connect(host, port, 8000);
            } catch (IOException e) {
                TrafficMonitor.failedConnections.incrementAndGet();
                LogStore.add(LogStore.LEVEL_WARN, LogStore.CAT_PROXY, R.string.log_connect_failed, host + ":" + port);
                if (connect) {
                    out.write(("HTTP/1.1 502 Bad Gateway\r\n\r\n").getBytes());
                    out.flush();
                }
                ClientRegistry.close(info);
                info = null;
                return;
            }
            if (connect) {
                out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                out.flush();
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(method).append(' ').append(path).append(' ').append(version).append("\r\n");
                for (String h : headers) sb.append(h).append("\r\n");
                sb.append("Connection: close\r\n\r\n");
                byte[] data = sb.toString().getBytes();
                remote.getOutputStream().write(data);
                remote.getOutputStream().flush();
                ProxyBridge.count(info, true, data.length);
            }
            bridged = true;
            ProxyBridge.bridge(pool, client, remote, info);
        } catch (Exception ignored) {
        } finally {
            if (!bridged) {
                if (info != null) ClientRegistry.close(info);
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        pool.shutdownNow();
    }
}
