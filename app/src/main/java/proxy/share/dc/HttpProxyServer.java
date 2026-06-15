package proxy.share.dc;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpProxyServer implements Runnable {
    private final int port;
    private ServerSocket serverSocket;
    private boolean running = false;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public HttpProxyServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            running = true;
            while (running) {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            stop();
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            clientSocket.setSoTimeout(30000);
            InputStream in = clientSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            
            String line = reader.readLine();
            if (line == null) return;

            String[] parts = line.split(" ");
            if (parts.length < 2) return;

            String method = parts[0];
            String url = parts[1];

            String host;
            int port = 80;

            if (method.equalsIgnoreCase("CONNECT")) {
                
                String[] hostPort = url.split(":");
                host = hostPort[0];
                port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 443;

                while ((line = reader.readLine()) != null && !line.isEmpty());

                Socket remoteSocket = new Socket(host, port);
                OutputStream out = clientSocket.getOutputStream();
                out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                out.flush();

                bridgeSockets(clientSocket, remoteSocket);
            } else {
                URL parsedUrl = new URL(url);
                host = parsedUrl.getHost();
                port = parsedUrl.getPort() != -1 ? parsedUrl.getPort() : 80;

                Socket remoteSocket = new Socket(host, port);
                OutputStream remoteOut = remoteSocket.getOutputStream();
                remoteOut.write((line + "\r\n").getBytes());
                
                bridgeSockets(clientSocket, remoteSocket);
            }
        } catch (Exception ignored) {
            try { clientSocket.close(); } catch (IOException e) {}
        }
    }

    private void bridgeSockets(Socket client, Socket remote) {
        threadPool.submit(() -> pipeStreams(client, remote, true));
        threadPool.submit(() -> pipeStreams(remote, client, false));
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
