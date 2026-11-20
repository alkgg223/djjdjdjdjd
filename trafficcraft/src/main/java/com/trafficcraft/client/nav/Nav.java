package com.trafficcraft.client.nav;

import com.trafficcraft.road.ClientRoads;
import com.trafficcraft.road.EdgeGeom;
import com.trafficcraft.road.Place;
import com.trafficcraft.road.RoadEdge;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side navigation: destination, shortest route over the road network (one-way roads respected), re-routing when the car
 * leaves the route, arrival. Also keeps the map view (follow mode or a free view that was panned / zoomed).
 */
public final class Nav {
    public static Vec3 dest;
    public static String destName = "";
    public static final List<Vec3> route = new ArrayList<>();
    public static double routeLen;
    public static boolean follow = true;
    /** free view (map screen): centre and zoom in pixels per block */
    public static double viewX, viewZ;
    public static float zoom = 2.6f;
    static int lastRoute = -1000, version = -1;
    static Vec3 routedFrom;

    private Nav() {}

    public static boolean active() {
        return dest != null;
    }

    public static void setDestination(Vec3 p, String name) {
        dest = p;
        destName = name;
        route.clear();
        lastRoute = -1000;
    }

    public static void cancel() {
        dest = null;
        destName = "";
        route.clear();
        routeLen = 0;
    }

    /** Called every client tick while driving. */
    public static void tick(VehicleEntity car) {
        if (dest == null || car == null) return;
        Vec3 p = car.position();
        if (p.distanceTo(dest) < 12) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable("msg.trafficcraft.nav_arrived", destName), true);
            cancel();
            return;
        }
        RoadNetwork net = ClientRoads.net;
        boolean need = route.isEmpty() || version != net.version || distToRoute(p) > 14;
        if (need && car.tickCount - lastRoute > 20) {
            lastRoute = car.tickCount;
            version = net.version;
            compute(net, p, car.forward(), dest);
        }
        // eat the part of the route already driven
        while (route.size() > 2 && route.get(0).distanceTo(p) < 6 && route.get(1).distanceTo(p) < route.get(0).distanceTo(p) + 3) route.remove(0);
    }

    static double distToRoute(Vec3 p) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < route.size(); i++) best = Math.min(best, segDist(p, route.get(i), route.get(i + 1)));
        return best;
    }

    static double segDist(Vec3 p, Vec3 a, Vec3 b) {
        double dx = b.x - a.x, dz = b.z - a.z, l2 = dx * dx + dz * dz;
        double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.z - a.z) * dz) / l2));
        double ex = a.x + dx * t - p.x, ez = a.z + dz * t - p.z;
        return Math.sqrt(ex * ex + ez * ez);
    }

    /** Nearest road edge to p (any height), with arc position and distance. */
    static RoadEdge nearest(RoadNetwork net, Vec3 p, double[] sOut, double max) {
        RoadEdge best = null;
        double bd = max;
        double[] out = new double[2];
        for (RoadEdge e : net.edges.values()) {
            if (e.ped) continue;
            EdgeGeom g = net.geom(e);
            if (g == null) continue;
            double s = g.project(p, out);
            if (out[1] < bd) {
                bd = out[1];
                best = e;
                sOut[0] = s;
            }
        }
        return best;
    }

    /** Shortest route by distance from the car (heading fwd) to target; result in 'route' (empty if unreachable). */
    public static void compute(RoadNetwork net, Vec3 from, Vec3 fwd, Vec3 target) {
        route.clear();
        routeLen = 0;
        double[] s0 = new double[1], s1 = new double[1];
        RoadEdge e0 = nearest(net, from, s0, 40), e1 = nearest(net, target, s1, 80);
        if (e0 == null || e1 == null) {
            route.add(from);
            route.add(target);
            routeLen = from.distanceTo(target);
            return;
        }
        EdgeGeom g0 = net.geom(e0), g1 = net.geom(e1);
        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, int[]> prev = new HashMap<>();         // node -> {previous node, edge id}
        PriorityQueue<double[]> q = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        // start: go on along the current road (towards b if heading that way and allowed, else towards a)
        Vec3 tg = g0.tangent(s0[0]);
        boolean towardsB = tg.x * fwd.x + tg.z * fwd.z >= 0;
        double tailB = g0.length - s0[0], tailA = s0[0];
        if (same(net, e0, e1)) {
            boolean ok = s1[0] >= s0[0] ? e0.allows(e0.a) : e0.allows(e0.b);
            if (ok && (s1[0] >= s0[0]) == towardsB) {
                addAlong(net, e0, s0[0], s1[0]);
                route.add(target);
                routeLen = Math.abs(s1[0] - s0[0]);
                return;
            }
        }
        if (e0.allows(e0.a)) seed(q, dist, e0.b, tailB + (towardsB ? 0 : 25));
        if (e0.allows(e0.b)) seed(q, dist, e0.a, tailA + (towardsB ? 25 : 0));
        while (!q.isEmpty()) {
            double[] cur = q.poll();
            int u = (int) cur[1];
            if (cur[0] > dist.getOrDefault(u, Double.MAX_VALUE) + 1e-6) continue;
            for (RoadEdge e : net.roadEdgesOf(u)) {
                if (!e.allows(u)) continue;
                int v = e.other(u);
                double nd = cur[0] + net.geom(e).length;
                if (nd < dist.getOrDefault(v, Double.MAX_VALUE)) {
                    dist.put(v, nd);
                    prev.put(v, new int[]{u, e.id});
                    q.add(new double[]{nd, v});
                }
            }
        }
        // finish on the destination road from whichever end is cheaper (and allowed towards the target point)
        double viaA = e1.allows(e1.a) && dist.containsKey(e1.a) ? dist.get(e1.a) + s1[0] : Double.MAX_VALUE;
        double viaB = e1.allows(e1.b) && dist.containsKey(e1.b) ? dist.get(e1.b) + (g1.length - s1[0]) : Double.MAX_VALUE;
        if (viaA == Double.MAX_VALUE && viaB == Double.MAX_VALUE) {
            route.add(from);
            route.add(target);
            routeLen = from.distanceTo(target);
            return;
        }
        int end = viaA <= viaB ? e1.a : e1.b;
        List<int[]> chain = new ArrayList<>();
        int n = end;
        int guard = 0;
        while (prev.containsKey(n) && guard++ < 10000) {
            int[] pe = prev.get(n);
            chain.add(0, new int[]{pe[0], pe[1], n});
            n = pe[0];
        }
        // first leg along the car's road to the node where the chain starts
        route.add(from);
        addAlong(net, e0, s0[0], n == e0.b ? g0.length : 0);
        for (int[] c : chain) {
            RoadEdge e = net.edges.get(c[1]);
            EdgeGeom g = net.geom(e);
            addAlong(net, e, c[0] == e.a ? 0 : g.length, c[0] == e.a ? g.length : 0);
        }
        addAlong(net, e1, end == e1.a ? 0 : g1.length, s1[0]);
        route.add(target);
        for (int i = 0; i + 1 < route.size(); i++) routeLen += route.get(i).distanceTo(route.get(i + 1));
    }

    static boolean same(RoadNetwork net, RoadEdge a, RoadEdge b) {
        return a.id == b.id;
    }

    static void seed(PriorityQueue<double[]> q, Map<Integer, Double> dist, int node, double d) {
        if (d < dist.getOrDefault(node, Double.MAX_VALUE)) {
            dist.put(node, d);
            q.add(new double[]{d, node});
        }
    }

    static void addAlong(RoadNetwork net, RoadEdge e, double sa, double sb) {
        EdgeGeom g = net.geom(e);
        int n = Math.max(1, (int) Math.ceil(Math.abs(sb - sa) / 3.0));
        for (int i = 0; i <= n; i++) route.add(g.point(sa + (sb - sa) * i / n, 0));
    }

    /** Places whose name or type matches the query (case-insensitive), nearest first. */
    public static List<Place> search(String query, Vec3 near) {
        String q = query.strip().toLowerCase();
        List<Place> out = new ArrayList<>();
        for (Place p : ClientRoads.net.places.values()) {
            String tn = Component.translatable("place.trafficcraft." + p.type).getString().toLowerCase();
            if (q.isEmpty() || p.name.toLowerCase().contains(q) || p.type.contains(q) || tn.contains(q)) out.add(p);
        }
        if (near != null) out.sort((a, b) -> Double.compare(a.pos().distanceToSqr(near), b.pos().distanceToSqr(near)));
        return out;
    }

    public static String distText(double m) {
        return m >= 1000 ? String.format("%.1f km", m / 1000) : Math.round(m / 10) * 10 + " m";
    }
}
