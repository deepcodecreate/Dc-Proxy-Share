package proxy.share.dc;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Locale;

public class TrafficMonitor {
    public static final AtomicLong rxBytes = new AtomicLong(0);
    public static final AtomicLong txBytes = new AtomicLong(0);

    public static void reset() {
        rxBytes.set(0);
        txBytes.set(0);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.US, "%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
