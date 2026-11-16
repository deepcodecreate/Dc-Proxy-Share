/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TrafficMonitor {
    public static final AtomicLong rxBytes = new AtomicLong(0);
    public static final AtomicLong txBytes = new AtomicLong(0);
    public static final AtomicInteger activeConnections = new AtomicInteger(0);
    public static final AtomicLong totalConnections = new AtomicLong(0);
    public static final AtomicLong failedConnections = new AtomicLong(0);
    public static volatile long startedAt = 0;

    public static void reset() {
        rxBytes.set(0);
        txBytes.set(0);
        activeConnections.set(0);
        totalConnections.set(0);
        failedConnections.set(0);
        startedAt = System.currentTimeMillis();
        ClientRegistry.reset();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "—";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.US, "%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public static String formatSpeed(long bytesPerSecond) {
        return formatBytes(bytesPerSecond) + "/s";
    }
}
