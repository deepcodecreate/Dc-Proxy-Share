/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.monitor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Process;
import android.telephony.TelephonyManager;
import androidx.core.content.ContextCompat;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public final class NetworkInfoReader {
    public static final class Active {
        public boolean connected;
        public boolean validated;
        public boolean metered;
        public boolean wifi;
        public boolean cellular;
        public boolean ethernet;
        public boolean vpn;
        public int downKbps;
        public int upKbps;
        public String iface = "";
        public String ip = "";
        public String gateway = "";
        public List<String> dns = new ArrayList<>();
    }

    public static final class Wifi {
        public boolean enabled;
        public boolean connected;
        public boolean locationGranted;
        public String ssid = "";
        public String bssid = "";
        public int rssi = -127;
        public int linkMbps;
        public int txMbps;
        public int rxMbps;
        public int freq;
        public int channel;
        public int widthMhz;
        public int standard;
        public String security = "";
        public String iface = "";
        public String ip = "";
        public int prefix;
        public String gateway = "";
        public List<String> dns = new ArrayList<>();
        public String dhcpServer = "";
        public int leaseSeconds;
        public int mtu;
        public List<String> ipv6 = new ArrayList<>();
        public String domains = "";
        public boolean privateDns;
        public String privateDnsName = "";
        public long rxBytes = -1;
        public long txBytes = -1;
    }

    public static final class Vpn {
        public boolean active;
        public boolean validated;
        public String iface = "";
        public String protocol = "";
        public String app = "";
        public String underlying = "";
        public List<String> addresses = new ArrayList<>();
        public List<String> dns = new ArrayList<>();
        public List<String> routes = new ArrayList<>();
        public int routeCount;
        public boolean defaultRoute;
        public int mtu;
        public int downKbps;
        public int upKbps;
        public long rxBytes = -1;
        public long txBytes = -1;
    }

    public static final class Cell {
        public boolean active;
        public String operator = "";
        public boolean roaming;
        public int downKbps;
        public int upKbps;
        public String iface = "";
    }

    private NetworkInfoReader() {
    }

    private static ConnectivityManager cm(Context c) {
        return (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private static Network physical(ConnectivityManager cm, int transport) {
        try {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(transport) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                        && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return n;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Network vpnNetwork(ConnectivityManager cm) {
        try {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return n;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String clean(InetAddress a) {
        String s = a.getHostAddress();
        int i = s.indexOf('%');
        return i > 0 ? s.substring(0, i) : s;
    }

    private static String gatewayOf(LinkProperties lp) {
        if (lp == null) return "";
        for (RouteInfo r : lp.getRoutes()) {
            if (r.isDefaultRoute() && r.getGateway() instanceof Inet4Address) {
                return clean(r.getGateway());
            }
        }
        return "";
    }

    private static void fillAddresses(LinkProperties lp, String[] ip, int[] prefix, List<String> v6) {
        for (LinkAddress la : lp.getLinkAddresses()) {
            InetAddress a = la.getAddress();
            if (a instanceof Inet4Address && ip[0].isEmpty()) {
                ip[0] = clean(a);
                prefix[0] = la.getPrefixLength();
            } else if (a instanceof Inet6Address) {
                v6.add(clean(a) + "/" + la.getPrefixLength());
            }
        }
    }

    public static Active active(Context ctx) {
        Active a = new Active();
        ConnectivityManager cm = cm(ctx);
        if (cm == null) return a;
        try {
            Network n = cm.getActiveNetwork();
            if (n == null) return a;
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            if (c == null) return a;
            a.connected = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            a.validated = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            a.metered = !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
            a.vpn = c.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            a.wifi = c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            a.cellular = c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            a.ethernet = c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            a.downKbps = c.getLinkDownstreamBandwidthKbps();
            a.upKbps = c.getLinkUpstreamBandwidthKbps();
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp != null) {
                a.iface = lp.getInterfaceName() == null ? "" : lp.getInterfaceName();
                String[] ip = {""};
                int[] prefix = {0};
                fillAddresses(lp, ip, prefix, new ArrayList<>());
                a.ip = ip[0];
                a.gateway = gatewayOf(lp);
                for (InetAddress d : lp.getDnsServers()) a.dns.add(clean(d));
            }
            if (a.vpn) {
                Network phys = physical(cm, NetworkCapabilities.TRANSPORT_WIFI);
                if (phys == null) phys = physical(cm, NetworkCapabilities.TRANSPORT_CELLULAR);
                if (phys != null) {
                    LinkProperties plp = cm.getLinkProperties(phys);
                    if (plp != null) a.gateway = gatewayOf(plp);
                }
            }
        } catch (Exception ignored) {
        }
        return a;
    }

    public static String gateway(Context ctx) {
        ConnectivityManager cm = cm(ctx);
        if (cm == null) return "";
        try {
            Network n = physical(cm, NetworkCapabilities.TRANSPORT_WIFI);
            if (n == null) n = physical(cm, NetworkCapabilities.TRANSPORT_ETHERNET);
            if (n == null) return "";
            return gatewayOf(cm.getLinkProperties(n));
        } catch (Exception e) {
            return "";
        }
    }

    public static int signalPercent(int rssi) {
        if (rssi <= -127) return 0;
        return Math.max(0, Math.min(100, 2 * (rssi + 100)));
    }

    public static int channelOf(int freq) {
        if (freq == 2484) return 14;
        if (freq >= 2412 && freq <= 2472) return (freq - 2412) / 5 + 1;
        if (freq >= 5160 && freq <= 5895) return (freq - 5000) / 5;
        if (freq >= 5955 && freq <= 7115) return (freq - 5950) / 5;
        return 0;
    }

    private static String securityOf(String caps) {
        if (caps == null) return "";
        boolean sae = caps.contains("SAE");
        boolean psk = caps.contains("PSK");
        boolean eap = caps.contains("EAP");
        boolean owe = caps.contains("OWE");
        boolean wep = caps.contains("WEP");
        if (eap) return caps.contains("SUITE_B") || caps.contains("WPA3") ? "WPA3-Enterprise" : "WPA2-Enterprise";
        if (sae && psk) return "WPA2/WPA3";
        if (sae) return "WPA3";
        if (owe) return "OWE";
        if (caps.contains("WPA2") && psk) return caps.contains("WPA-PSK") ? "WPA/WPA2" : "WPA2";
        if (caps.contains("WPA-PSK") || caps.contains("WPA]")) return "WPA";
        if (wep) return "WEP";
        return "Open";
    }

    private static int widthOf(int w) {
        switch (w) {
            case 0:
                return 20;
            case 1:
                return 40;
            case 2:
                return 80;
            case 3:
            case 4:
                return 160;
            case 5:
                return 320;
            default:
                return 0;
        }
    }

    private static String ipFromInt(int v) {
        return (v & 0xFF) + "." + ((v >> 8) & 0xFF) + "." + ((v >> 16) & 0xFF) + "." + ((v >> 24) & 0xFF);
    }

    @SuppressWarnings("deprecation")
    public static Wifi wifi(Context ctx) {
        Wifi w = new Wifi();
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager cm = cm(ctx);
        w.enabled = wm != null && wm.isWifiEnabled();
        w.locationGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (cm == null || wm == null) return w;
        Network net = physical(cm, NetworkCapabilities.TRANSPORT_WIFI);
        if (net == null) return w;
        w.connected = true;
        try {
            LinkProperties lp = cm.getLinkProperties(net);
            if (lp != null) {
                w.iface = lp.getInterfaceName() == null ? "" : lp.getInterfaceName();
                String[] ip = {""};
                int[] prefix = {0};
                fillAddresses(lp, ip, prefix, w.ipv6);
                w.ip = ip[0];
                w.prefix = prefix[0];
                w.gateway = gatewayOf(lp);
                for (InetAddress d : lp.getDnsServers()) w.dns.add(clean(d));
                w.domains = lp.getDomains() == null ? "" : lp.getDomains();
                if (Build.VERSION.SDK_INT >= 29) w.mtu = lp.getMtu();
                w.privateDns = lp.isPrivateDnsActive();
                w.privateDnsName = lp.getPrivateDnsServerName() == null ? "" : lp.getPrivateDnsServerName();
                long[] st = readIface(w.iface);
                if (st != null) {
                    w.rxBytes = st[0];
                    w.txBytes = st[1];
                }
            }
        } catch (Exception ignored) {
        }
        try {
            WifiInfo info = wm.getConnectionInfo();
            if (info != null) {
                String ssid = info.getSSID();
                if (ssid != null && !ssid.contains("unknown ssid")) {
                    if (ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                        ssid = ssid.substring(1, ssid.length() - 1);
                    }
                    w.ssid = ssid;
                }
                String bssid = info.getBSSID();
                if (bssid != null && !bssid.equals("02:00:00:00:00:00")) w.bssid = bssid;
                w.rssi = info.getRssi();
                w.linkMbps = info.getLinkSpeed();
                w.freq = info.getFrequency();
                w.channel = channelOf(w.freq);
                if (Build.VERSION.SDK_INT >= 29) {
                    w.txMbps = info.getTxLinkSpeedMbps();
                    w.rxMbps = info.getRxLinkSpeedMbps();
                }
                if (Build.VERSION.SDK_INT >= 30) w.standard = info.getWifiStandard();
            }
        } catch (Exception ignored) {
        }
        try {
            DhcpInfo d = wm.getDhcpInfo();
            if (d != null) {
                if (d.serverAddress != 0) w.dhcpServer = ipFromInt(d.serverAddress);
                w.leaseSeconds = d.leaseDuration;
            }
        } catch (Exception ignored) {
        }
        if (w.locationGranted && !w.bssid.isEmpty()) {
            try {
                List<ScanResult> results = wm.getScanResults();
                if (results != null) {
                    for (ScanResult r : results) {
                        if (w.bssid.equalsIgnoreCase(r.BSSID)) {
                            w.security = securityOf(r.capabilities);
                            w.widthMhz = widthOf(r.channelWidth);
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return w;
    }

    private static String protocolOf(String iface) {
        String n = iface.toLowerCase();
        if (n.startsWith("wg")) return "WireGuard";
        if (n.startsWith("ppp")) return "PPP (L2TP / PPTP)";
        if (n.startsWith("ipsec") || n.startsWith("xfrm")) return "IPsec";
        if (n.startsWith("tap")) return "TAP";
        if (n.startsWith("tun")) return "TUN";
        return "";
    }

    public static Vpn vpn(Context ctx) {
        Vpn v = new Vpn();
        ConnectivityManager cm = cm(ctx);
        if (cm == null) return v;
        try {
            Network n = vpnNetwork(cm);
            if (n == null) return v;
            v.active = true;
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            if (c != null) {
                v.validated = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                v.downKbps = c.getLinkDownstreamBandwidthKbps();
                v.upKbps = c.getLinkUpstreamBandwidthKbps();
                if (Build.VERSION.SDK_INT >= 31) {
                    int uid = c.getOwnerUid();
                    if (uid > 0 && uid != Process.myUid()) {
                        String[] pkgs = ctx.getPackageManager().getPackagesForUid(uid);
                        if (pkgs != null && pkgs.length > 0) {
                            try {
                                v.app = ctx.getPackageManager().getApplicationLabel(
                                        ctx.getPackageManager().getApplicationInfo(pkgs[0], 0)).toString();
                            } catch (Exception e) {
                                v.app = pkgs[0];
                            }
                        }
                    }
                }
            }
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp != null) {
                v.iface = lp.getInterfaceName() == null ? "" : lp.getInterfaceName();
                v.protocol = protocolOf(v.iface);
                for (LinkAddress la : lp.getLinkAddresses()) {
                    v.addresses.add(clean(la.getAddress()) + "/" + la.getPrefixLength());
                }
                for (InetAddress d : lp.getDnsServers()) v.dns.add(clean(d));
                List<RouteInfo> routes = lp.getRoutes();
                v.routeCount = routes.size();
                for (RouteInfo r : routes) {
                    if (r.isDefaultRoute()) v.defaultRoute = true;
                    if (v.routes.size() < 4) v.routes.add(r.toString());
                }
                if (Build.VERSION.SDK_INT >= 29) v.mtu = lp.getMtu();
                long[] st = readIface(v.iface);
                if (st != null) {
                    v.rxBytes = st[0];
                    v.txBytes = st[1];
                }
            }
            Network phys = physical(cm, NetworkCapabilities.TRANSPORT_WIFI);
            if (phys != null) {
                v.underlying = "Wi-Fi";
            } else if (physical(cm, NetworkCapabilities.TRANSPORT_CELLULAR) != null) {
                v.underlying = "Cellular";
            } else if (physical(cm, NetworkCapabilities.TRANSPORT_ETHERNET) != null) {
                v.underlying = "Ethernet";
            }
        } catch (Exception ignored) {
        }
        return v;
    }

    public static Cell cellular(Context ctx) {
        Cell c = new Cell();
        ConnectivityManager cm = cm(ctx);
        if (cm == null) return c;
        try {
            Network n = physical(cm, NetworkCapabilities.TRANSPORT_CELLULAR);
            if (n != null) {
                c.active = true;
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null) {
                    c.downKbps = caps.getLinkDownstreamBandwidthKbps();
                    c.upKbps = caps.getLinkUpstreamBandwidthKbps();
                }
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp != null && lp.getInterfaceName() != null) c.iface = lp.getInterfaceName();
            }
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String op = tm.getNetworkOperatorName();
                c.operator = op == null ? "" : op;
                c.roaming = tm.isNetworkRoaming();
            }
        } catch (Exception ignored) {
        }
        return c;
    }

    public static long[] readIface(String iface) {
        if (iface == null || iface.isEmpty()) return null;
        long rx = readLong("/sys/class/net/" + iface + "/statistics/rx_bytes");
        long tx = readLong("/sys/class/net/" + iface + "/statistics/tx_bytes");
        if (rx < 0 || tx < 0) return null;
        return new long[]{rx, tx};
    }

    private static long readLong(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String line = r.readLine();
            return line == null ? -1 : Long.parseLong(line.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
