package proxy.share.dc;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Socks5ProxyServer implements Runnable {
    private final int port;
    private ServerSocket serverSocket;
    private boolean running = false;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public Socks5ProxyServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            running = true;
            while (running) {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(() -> handleSocks5(clientSocket));
            }
        } catch (IOException e) {
            stop();
        }
    }

    private void handleSocks5(Socket clientSocket) {
        try {
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            int version = in.read();
            if (version != 5) { clientSocket.close(); return; }
            int numMethods = in.read();
            byte[] methods = new byte[numMethods];
            in.read(methods);

            out.write(new byte[]{5, 0});
            out.flush();

            int ver = in.read();
            int cmd = in.read();
            in.read();
            int atyp = in.read();

            if (ver != 5 || cmd != 1) { clientSocket.close(); return; }

            String targetHost = "";
            if (atyp == 1) { 
                byte[] ipv4 = new byte[4];
                in.read(ipv4);
                targetHost = InetAddress.getByAddress(ipv4).getHostAddress();
            } else if (atyp == 3) { 
                int domainLength = in.read();
                byte[] domainBytes = new byte[domainLength];
                in.read(domainBytes);
                targetHost = new String(domainBytes);
            } else {
                clientSocket.close();
                return;
            }

            int p1 = in.read();
            int p2 = in.read();
            int targetPort = (p1 << 8) | p2;

            Socket remoteSocket;
            try {
                remoteSocket = new Socket(targetHost, targetPort);
                byte[] reply = new byte[]{5, 0, 0, 1, 0, 0, 0, 0, 0, 0};
                out.write(reply);
                out.flush();
            } catch (IOException e) {
                out.write(new byte[]{5, 1, 0, 1, 0, 0, 0, 0, 0, 0}); 
                out.flush();
                clientSocket.close();
                return;
            }

            Socket finalRemoteSocket = remoteSocket;
            threadPool.submit(() -> pipeStreams(clientSocket, finalRemoteSocket, true));
            threadPool.submit(() -> pipeStreams(finalRemoteSocket, clientSocket, false));

        } catch (Exception ignored) {
            try { clientSocket.close(); } catch (IOException e) {}
        }
    }

    private void pipeStreams(Socket src, Socket dest, boolean isUpload) {
        try (InputStream in = src.getInputStream(); OutputStream out = dest.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while (running && (read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();

                if (isUpload) {
                    TrafficMonitor.txBytes.addAndGet(read);
                } else {
                    TrafficMonitor.rxBytes.addAndGet(read);
                }
            }
        } catch (IOException ignored) {}
        finally {
            try { src.close(); } catch (Exception ignored) {}
            try { dest.close(); } catch (Exception ignored) {}
        }
    }

    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        threadPool.shutdownNow();
    }
}
