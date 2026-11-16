/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Socks5ProxyServer implements Runnable {
    private final int port;
    private final boolean verbose;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public Socks5ProxyServer(int port, boolean verbose) {
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
            ProxyService.socksBound = true;
            LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_PROXY, R.string.log_socks_listening, String.valueOf(port));
            while (running) {
                Socket c = serverSocket.accept();
                pool.submit(() -> handle(c));
            }
        } catch (IOException e) {
            if (!running && !ProxyService.socksBound) {
                LogStore.add(LogStore.LEVEL_ERROR, LogStore.CAT_PROXY, R.string.log_bind_failed, "SOCKS5", String.valueOf(port));
                ProxyService.bindError = true;
            }
        } finally {
            ProxyService.socksBound = false;
        }
    }

    private void reply(OutputStream out, int code) throws IOException {
        out.write(new byte[]{5, (byte) code, 0, 1, 0, 0, 0, 0, 0, 0});
        out.flush();
    }

    private void handle(Socket client) {
        ClientRegistry.Client info = null;
        boolean bridged = false;
        try {
            client.setSoTimeout(15000);
            DataInputStream in = new DataInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();
            if (in.readUnsignedByte() != 5) return;
            int count = in.readUnsignedByte();
            byte[] methods = new byte[count];
            in.readFully(methods);
            boolean noAuth = false;
            for (byte m : methods) {
                if (m == 0) noAuth = true;
            }
            if (!noAuth) {
                out.write(new byte[]{5, (byte) 0xFF});
                out.flush();
                return;
            }
            out.write(new byte[]{5, 0});
            out.flush();
            in.readUnsignedByte();
            int cmd = in.readUnsignedByte();
            in.readUnsignedByte();
            int atyp = in.readUnsignedByte();
            String host;
            if (atyp == 1) {
                byte[] b = new byte[4];
                in.readFully(b);
                host = InetAddress.getByAddress(b).getHostAddress();
            } else if (atyp == 3) {
                byte[] b = new byte[in.readUnsignedByte()];
                in.readFully(b);
                host = new String(b);
            } else if (atyp == 4) {
                byte[] b = new byte[16];
                in.readFully(b);
                host = InetAddress.getByAddress(b).getHostAddress();
            } else {
                reply(out, 8);
                return;
            }
            int targetPort = (in.readUnsignedByte() << 8) | in.readUnsignedByte();
            if (cmd != 1) {
                reply(out, 7);
                return;
            }
            info = ClientRegistry.open(ProxyBridge.clientIp(client));
            if (verbose) {
                LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_PROXY, R.string.log_socks_request,
                        info.ip, host + ":" + targetPort);
            }
            Socket remote;
            try {
                remote = ProxyBridge.connect(host, targetPort, 8000);
            } catch (IOException e) {
                TrafficMonitor.failedConnections.incrementAndGet();
                LogStore.add(LogStore.LEVEL_WARN, LogStore.CAT_PROXY, R.string.log_connect_failed, host + ":" + targetPort);
                reply(out, 5);
                ClientRegistry.close(info);
                info = null;
                return;
            }
            reply(out, 0);
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
