package com.trafficcraft.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/** Simple counters used by /traffic stats and the automated test. */
public final class TrafficStats {
    public static final Map<String, Long> C = new LinkedHashMap<>();

    public static void inc(String k) {
        C.merge(k, 1L, Long::sum);
    }

    public static void add(String k, long v) {
        C.merge(k, v, Long::sum);
    }

    public static long get(String k) {
        return C.getOrDefault(k, 0L);
    }
}
