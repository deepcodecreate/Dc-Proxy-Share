package proxy.share.dc;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class NetworkUtils {
    
    public static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.getName().contains("wlan") || intf.getName().contains("ap") || intf.getName().contains("rndis")) {
                    List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                    for (InetAddress addr : addrs) {
                        if (!addr.isLoopbackAddress()) {
                            String sAddr = addr.getHostAddress();
                            boolean isIPv4 = sAddr.indexOf(':') < 0;
                            if (isIPv4) return sAddr;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public static boolean isVpnActive(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network activeNetwork = cm.getActiveNetwork();
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isHotspotActive(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            Method method = wifiManager.getClass().getDeclaredMethod("getWifiApState");
            int state = (Integer) method.invoke(wifiManager);
            return state == 13;
        } catch (Exception e) {
            try {
                List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface intf : interfaces) {
                    String name = intf.getName().toLowerCase();
                    if ((name.contains("ap") || name.contains("softap") || name.contains("wlan1")) && intf.isUp()) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }
        return false;
    }
}
