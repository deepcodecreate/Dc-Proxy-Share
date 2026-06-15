package proxy.share.dc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class ProxyService extends Service {
    private static final String CHANNEL_ID = "ProxyServiceChannel";
    private HttpProxyServer httpServer;
    private Socks5ProxyServer socksServer;
    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        TrafficMonitor.reset();

        SharedPreferences prefs = getSharedPreferences("ProxyPrefs", MODE_PRIVATE);
        int httpPort = prefs.getInt("http_port", 8080);
        int socksPort = prefs.getInt("socks_port", 1080);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("سرور اشتراک پروکسی فعال است")
                .setContentText("HTTP: " + httpPort + " | SOCKS5: " + socksPort)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .build();

        startForeground(1, notification);

        httpServer = new HttpProxyServer(httpPort);
        socksServer = new Socks5ProxyServer(socksPort);
        
        new Thread(httpServer).start();
        new Thread(socksServer).start();
        
        isRunning = true;

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (httpServer != null) httpServer.stop();
        if (socksServer != null) socksServer.stop();
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Proxy Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }
}
