/*
 * Copyright (c) 2026 @deepcodecreate (https://github.com/deepcodecreate)
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for the full license text.
 */

package proxy.share.dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientRegistry {
    public static final class Client {
        public final String ip;
        public final long firstSeen = System.currentTimeMillis();
        public volatile long lastSeen = firstSeen;
        public final AtomicLong rx = new AtomicLong();
        public final AtomicLong tx = new AtomicLong();
        public final AtomicInteger active = new AtomicInteger();
        public final AtomicLong connections = new AtomicLong();

        Client(String ip) {
            this.ip = ip;
        }
    }

    private static final ConcurrentHashMap<String, Client> CLIENTS = new ConcurrentHashMap<>();

    private ClientRegistry() {
    }

    public static Client get(String ip) {
        Client c = CLIENTS.get(ip);
        if (c == null) {
            Client created = new Client(ip);
            c = CLIENTS.putIfAbsent(ip, created);
            if (c == null) {
                c = created;
                LogStore.add(LogStore.LEVEL_INFO, LogStore.CAT_CLIENT, R.string.log_client_new, ip);
            }
        }
        return c;
    }

    public static Client open(String ip) {
        Client c = get(ip);
        c.active.incrementAndGet();
        c.connections.incrementAndGet();
        c.lastSeen = System.currentTimeMillis();
        return c;
    }

    public static void close(Client c) {
        c.active.decrementAndGet();
        c.lastSeen = System.currentTimeMillis();
    }

    public static List<Client> snapshot() {
        List<Client> list = new ArrayList<>(CLIENTS.values());
        Collections.sort(list, (a, b) -> Long.compare(b.lastSeen, a.lastSeen));
        return list;
    }

    public static void reset() {
        CLIENTS.clear();
    }
}
