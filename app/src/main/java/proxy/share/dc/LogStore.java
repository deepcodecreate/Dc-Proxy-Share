/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import proxy.share.dc.ui.AppPreferences;

public final class LogStore {
    public static final int LEVEL_INFO = 0;
    public static final int LEVEL_WARN = 1;
    public static final int LEVEL_ERROR = 2;
    public static final int CAT_APP = 0;
    public static final int CAT_PROXY = 1;
    public static final int CAT_CLIENT = 2;
    public static final int CAT_NETWORK = 3;
    public static final int CAT_PING = 4;

    public static final class Entry {
        public final long time;
        public final int level;
        public final int category;
        public final int messageRes;
        public final String[] args;

        Entry(long time, int level, int category, int messageRes, String[] args) {
            this.time = time;
            this.level = level;
            this.category = category;
            this.messageRes = messageRes;
            this.args = args;
        }
    }

    public interface Listener {
        void onLogChanged();
    }

    private static final ArrayDeque<Entry> ENTRIES = new ArrayDeque<>();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean PENDING = new AtomicBoolean(false);
    private static int limit = 500;

    private LogStore() {
    }

    public static void init(Context c) {
        limit = AppPreferences.getLogLimit(c);
    }

    public static void setLimit(int v) {
        synchronized (ENTRIES) {
            limit = v;
            while (ENTRIES.size() > limit) ENTRIES.pollFirst();
        }
    }

    public static void add(int level, int category, int messageRes, String... args) {
        synchronized (ENTRIES) {
            ENTRIES.addLast(new Entry(System.currentTimeMillis(), level, category, messageRes, args));
            while (ENTRIES.size() > limit) ENTRIES.pollFirst();
        }
        if (PENDING.compareAndSet(false, true)) {
            MAIN.postDelayed(() -> {
                PENDING.set(false);
                for (Listener l : LISTENERS) l.onLogChanged();
            }, 250);
        }
    }

    public static List<Entry> snapshot(int category) {
        List<Entry> out = new ArrayList<>();
        synchronized (ENTRIES) {
            Iterator<Entry> it = ENTRIES.descendingIterator();
            while (it.hasNext()) {
                Entry e = it.next();
                if (category < 0 || e.category == category) out.add(e);
            }
        }
        return out;
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
        for (Listener l : LISTENERS) l.onLogChanged();
    }

    public static void addListener(Listener l) {
        LISTENERS.addIfAbsent(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    public static String message(Context c, Entry e) {
        return c.getString(e.messageRes, (Object[]) e.args);
    }

    public static String time(Entry e) {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(e.time));
    }

    public static String export(Context c, List<Entry> list) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (Entry e : list) {
            sb.append(f.format(new Date(e.time))).append("  ").append(message(c, e)).append('\n');
        }
        return sb.toString();
    }
}
