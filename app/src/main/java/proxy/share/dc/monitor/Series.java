/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc.monitor;

public final class Series {
    private final float[] buf;
    private int head;
    private int count;

    public Series(int capacity) {
        buf = new float[capacity];
    }

    public void add(float v) {
        buf[head] = v;
        head = (head + 1) % buf.length;
        if (count < buf.length) count++;
    }

    public int size() {
        return count;
    }

    public float[] toArray() {
        float[] out = new float[count];
        int start = (head - count + buf.length) % buf.length;
        for (int i = 0; i < count; i++) {
            out[i] = buf[(start + i) % buf.length];
        }
        return out;
    }

    public void clear() {
        head = 0;
        count = 0;
    }
}
