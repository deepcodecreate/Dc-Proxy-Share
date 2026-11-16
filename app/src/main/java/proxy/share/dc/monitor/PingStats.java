/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.monitor;

public final class PingStats {
    public int sent;
    public int received;
    public float min = -1;
    public float max = -1;
    public float last = -1;
    private double sum;
    private double jitterSum;
    private int jitterCount;
    private float previous = -1;

    public void add(float ms) {
        sent++;
        last = ms;
        if (ms < 0) return;
        received++;
        sum += ms;
        if (min < 0 || ms < min) min = ms;
        if (ms > max) max = ms;
        if (previous >= 0) {
            jitterSum += Math.abs(ms - previous);
            jitterCount++;
        }
        previous = ms;
    }

    public float avg() {
        return received == 0 ? -1 : (float) (sum / received);
    }

    public float jitter() {
        return jitterCount == 0 ? -1 : (float) (jitterSum / jitterCount);
    }

    public int lossPercent() {
        return sent == 0 ? 0 : Math.round(100f * (sent - received) / sent);
    }

    public void reset() {
        sent = 0;
        received = 0;
        min = -1;
        max = -1;
        last = -1;
        sum = 0;
        jitterSum = 0;
        jitterCount = 0;
        previous = -1;
    }
}
