/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import proxy.share.dc.ui.AppPreferences;

public class ProxyService extends Service {
    public static final String ACTION_STOP = "proxy.share.dc.STOP";
    private static final String CHANNEL_ID = "ProxyServiceChannel";
    private HttpProxyServer httpServer;
    private Socks5ProxyServer socksServer;
    public static volatile boolean isRunning = false;
    public static volatile boolean httpBound = false;
    public static volatile boolean socksBound = false;
    public static volatile boolean bindError = false;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.setLocale(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (isRunning) return START_STICKY;

        TrafficMonitor.reset();
        bindError = false;
        int httpPort = AppPreferences.getHttpPort(this);
        int socksPort = AppPreferences.getSocksPort(this);
        boolean verbose = AppPreferences.isLogConnections(this);

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, ProxyService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text, String.valueOf(httpPort), String.valueOf(socksPort)))
                .setSmallIcon(R.drawable.ic_stat_proxy)
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(0, getString(R.string.notif_stop), stopPi)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }

        httpServer = new HttpProxyServer(httpPort, verbose);
        socksServer = new Socks5ProxyServer(socksPort, verbose);
        new Thread(httpServer).start();
        new Thread(socksServer).start();

        isRunning = true;
        LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_PROXY, R.string.log_proxy_started,
                String.valueOf(httpPort), String.valueOf(socksPort));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (httpServer != null) httpServer.stop();
        if (socksServer != null) socksServer.stop();
        if (isRunning) LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_PROXY, R.string.log_proxy_stopped);
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(ch);
        }
    }
}
