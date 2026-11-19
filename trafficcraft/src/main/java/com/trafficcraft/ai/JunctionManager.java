package com.trafficcraft.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Per-level junction reservations: a vehicle may enter a junction only when its connector conflicts with no active one. */
public final class JunctionManager {
    public static final class Res {
        final int vehicle;
        final Piece connector;
        long time;

        Res(int v, Piece c, long t) {
            vehicle = v;
            connector = c;
            time = t;
        }
    }

    private final Map<Integer, List<Res>> active = new HashMap<>();
    private final Map<Long, Boolean> conflictCache = new HashMap<>();

    public List<Res> at(int node) {
        return active.computeIfAbsent(node, k -> new ArrayList<>());
    }

    public boolean holds(int node, int vehicle) {
        for (Res r : at(node)) if (r.vehicle == vehicle) return true;
        return false;
    }

    /** Try to reserve; 'force' ignores stale reservations (dead-lock breaker). */
    public boolean request(int node, int vehicle, Piece c, long now, boolean force) {
        List<Res> list = at(node);
        for (Res r : list) if (r.vehicle == vehicle) {
            r.time = now;
            return true;
        }
        for (Res r : list) {
            if (force && now - r.time > 200) continue;
            if (conflicts(r.connector, c)) return false;
        }
        list.add(new Res(vehicle, c, now));
        return true;
    }

    public void release(int node, int vehicle) {
        List<Res> l = active.get(node);
        if (l != null) l.removeIf(r -> r.vehicle == vehicle);
    }

    public void releaseAll(int vehicle) {
        for (List<Res> l : active.values()) l.removeIf(r -> r.vehicle == vehicle);
    }

    public void expire(long now) {
        for (Iterator<List<Res>> it = active.values().iterator(); it.hasNext(); ) {
            List<Res> l = it.next();
            l.removeIf(r -> now - r.time > 400);
            if (l.isEmpty()) it.remove();
        }
        if (conflictCache.size() > 20000) conflictCache.clear();
    }

    /** True if the two connectors cross or merge (come within 1.7 m) - same start lane following is not a conflict. */
    boolean conflicts(Piece a, Piece b) {
        if (a == b) return true;
        if (a.fromEdge == b.fromEdge && a.lane == b.lane && a.nextEdge == b.nextEdge) return false; // same movement, queue
        long k = ((long) System.identityHashCode(a) << 32) ^ System.identityHashCode(b);
        Boolean c = conflictCache.get(k);
        if (c != null) return c;
        boolean res = false;
        double lim = 1.7 * 1.7;
        outer:
        for (int i = 0; i < a.x.length; i += 2) {
            for (int j = 0; j < b.x.length; j += 2) {
                double dx = a.x[i] - b.x[j], dz = a.z[i] - b.z[j];
                if (dx * dx + dz * dz < lim) {
                    // sharing the same origin point only (diverging movements from one lane) is fine
                    if (i < 4 && j < 4 && a.fromEdge == b.fromEdge && a.lane == b.lane) continue;
                    res = true;
                    break outer;
                }
            }
        }
        conflictCache.put(k, res);
        return res;
    }
}
