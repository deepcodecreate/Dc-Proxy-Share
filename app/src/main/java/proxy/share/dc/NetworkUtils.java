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
import android.net.NetworkCapabilities;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class NetworkUtils {
    public static final class LocalAddress {
        public final String iface;
        public final String ip;
        public final int prefix;
        public final boolean ap;
        public final boolean tether;

        LocalAddress(String iface, String ip, int prefix, boolean ap, boolean tether) {
            this.iface = iface;
            this.ip = ip;
            this.prefix = prefix;
            this.ap = ap;
            this.tether = tether;
        }
    }

    private static final Pattern AP_NAME = Pattern.compile("^(ap\\d*|swlan\\d+|softap\\d*|wlan[1-9]\\d*|wigig\\d+)$");
    private static final Pattern TETHER_NAME = Pattern.compile("^(rndis\\d+|usb\\d+|ncm\\d+|bt-pan)$");
    private static final Pattern LAN_NAME = Pattern.compile("^(wlan\\d+|ap\\d*|swlan\\d+|softap\\d*|rndis\\d+|usb\\d+|ncm\\d+|bt-pan|eth\\d+|wigig\\d+)$");

    public static boolean isWifiClientConnected(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static List<LocalAddress> localAddresses(Context context) {
        List<LocalAddress> out = new ArrayList<>();
        boolean wifiClient = isWifiClientConnected(context);
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : all) {
                if (!intf.isUp() || intf.isLoopback()) continue;
                String name = intf.getName();
                if (!LAN_NAME.matcher(name).matches()) continue;
                for (InterfaceAddress ia : intf.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (!(a instanceof Inet4Address) || a.isLinkLocalAddress()) continue;
                    boolean ap = AP_NAME.matcher(name).matches() || (name.equals("wlan0") && !wifiClient);
                    boolean tether = TETHER_NAME.matcher(name).matches();
                    out.add(new LocalAddress(name, a.getHostAddress(), ia.getNetworkPrefixLength(), ap, tether));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String getLocalIpAddress(Context context) {
        List<LocalAddress> list = localAddresses(context);
        for (LocalAddress a : list) {
            if (a.ap) return a.ip;
        }
        for (LocalAddress a : list) {
            if (a.tether) return a.ip;
        }
        for (LocalAddress a : list) {
            return a.ip;
        }
        return "127.0.0.1";
    }

    public static boolean isHotspotActive(Context context) {
        for (LocalAddress a : localAddresses(context)) {
            if (a.ap) return true;
        }
        return false;
    }

    public static boolean isVpnActive(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
                return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
