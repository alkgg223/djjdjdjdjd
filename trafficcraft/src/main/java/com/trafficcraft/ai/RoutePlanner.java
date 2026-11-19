package com.trafficcraft.ai;

import com.trafficcraft.road.EdgeGeom;
import com.trafficcraft.road.Lane;
import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.RoadEdge;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.road.TrafficLights;
import com.trafficcraft.vehicle.VehicleSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Builds drivable pieces from the road network and makes routing decisions. */
public final class RoutePlanner {
    private static final Map<String, Piece> CONNECTORS = new HashMap<>();
    private static final Map<Integer, Map<Integer, Integer>> NEXT_HOP = new HashMap<>();
    private static int cacheVersion = -1;
    private static RoadNetwork cacheNet;

    static void check(RoadNetwork net) {
        if (net.version != cacheVersion || net != cacheNet) {
            CONNECTORS.clear();
            NEXT_HOP.clear();
            cacheVersion = net.version;
            cacheNet = net;
        }
    }

    public static float edgeSpeed(RoadEdge e) {
        return Math.max(3f, e.speed / 3.6f);
    }

    public static double stopOffset(RoadNetwork net, int node) {
        RoadNode n = net.nodes.get(node);
        return (n != null && n.has(RoadNode.F_CROSSWALK) && net.isJunction(node)) ? 4.0 : 0.5;
    }

    /** Lane piece from 'from' toward the other end of e, starting at arc position sStart (null = edge start). */
    public static Piece edgePiece(RoadNetwork net, RoadEdge e, int from, int lane, Double sStart, Float offsetStart, double blend) {
        EdgeGeom g = net.geom(e);
        int dir = from == e.a ? 1 : -1;
        int to = e.other(from);
        double a = dir > 0 ? g.trimA : g.length - g.trimB;
        double b = dir > 0 ? g.length - g.trimB : g.trimA;
        if (sStart != null) a = sStart;
        double total = Math.abs(b - a);
        float off = e.laneOffset(Math.max(0, Math.min(e.lanes.size() - 1, lane)));
        List<Vec3> pts = new ArrayList<>();
        int n = Math.max(1, (int) Math.ceil(total / 1.0));
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            double s = a + (b - a) * t;
            double o = off;
            if (offsetStart != null && blend > 0) {
                double d = t * total;
                double k = Math.min(1, d / blend);
                k = k * k * (3 - 2 * k);
                o = offsetStart + (off - offsetStart) * k;
            }
            pts.add(g.point(s, o));
        }
        Piece p = new Piece(pts, e.id, dir, lane, to, false, edgeSpeed(e));
        p.endOffset = off;
        if (net.isJunction(to)) p.stopS = Math.max(0, p.length - stopOffset(net, to));
        return p;
    }

    /** Smooth connector through node from the end of (inEdge, inLane) to the start of (outEdge, outLane); null if contiguous. */
    public static Piece connector(RoadNetwork net, RoadEdge in, int inLane, int node, RoadEdge out, int outLane) {
        check(net);
        String key = node + ":" + in.id + ":" + inLane + ":" + out.id + ":" + outLane;
        if (CONNECTORS.containsKey(key)) return CONNECTORS.get(key);
        EdgeGeom gi = net.geom(in), go = net.geom(out);
        int dirIn = in.b == node ? 1 : -1;
        int dirOut = out.a == node ? 1 : -1;
        double sIn = dirIn > 0 ? gi.length - gi.trimB : gi.trimA;
        double sOut = dirOut > 0 ? go.trimA : go.length - go.trimB;
        Vec3 p0 = gi.point(sIn, in.laneOffset(inLane)), p3 = go.point(sOut, out.laneOffset(outLane));
        Vec3 t0 = gi.tangent(sIn).scale(dirIn), t3 = go.tangent(sOut).scale(dirOut);
        double d = p0.distanceTo(p3);
        Piece res = null;
        if (d > 0.3) {
            double h = d * 0.5;
            Vec3 p1 = p0.add(t0.scale(h)), p2 = p3.subtract(t3.scale(h));
            List<Vec3> pts = new ArrayList<>();
            int n = Math.max(2, (int) Math.ceil(d / 0.5));
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n, u = 1 - t;
                double x = u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x;
                double z = u * u * u * p0.z + 3 * u * u * t * p1.z + 3 * u * t * t * p2.z + t * t * t * p3.z;
                double y = p0.y + (p3.y - p0.y) * t;
                pts.add(new Vec3(x, y, z));
            }
            int move = TrafficLights.movement(t0, t3);
            float lim = switch (move) {
                case TrafficLights.MOVE_STRAIGHT -> Math.min(edgeSpeed(in), edgeSpeed(out));
                case TrafficLights.MOVE_UTURN -> 3.5f;
                default -> Math.min(7f, Math.min(edgeSpeed(in), edgeSpeed(out)));
            };
            if (move != TrafficLights.MOVE_STRAIGHT) {
                // tighter radius -> slower
                double ang = Math.acos(Math.max(-1, Math.min(1, t0.dot(t3))));
                double radius = Math.max(2, d / Math.max(0.3, 2 * Math.sin(ang / 2)) * 0.9);
                lim = (float) Math.min(lim, Math.sqrt(3.6 * radius));
            }
            res = new Piece(pts, -1, 1, inLane, node, true, lim);
            res.movement = move;
            res.fromEdge = in.id;
            res.nextEdge = out.id;
            res.endOffset = out.laneOffset(outLane);
        }
        CONNECTORS.put(key, res);
        return res;
    }

    /** Edges a vehicle may take leaving node after arriving on 'in' (U-turns only at dead ends). */
    public static List<RoadEdge> exits(RoadNetwork net, int node, RoadEdge in, VehicleSpec spec) {
        List<RoadEdge> out = new ArrayList<>();
        for (RoadEdge e : net.roadEdgesOf(node)) {
            if (e == in || !e.allows(node)) continue;
            if ((e.restrict & RoadEdge.R_NO_TRUCKS) != 0 && spec.isHeavy()) continue;
            if ((e.restrict & RoadEdge.R_BUS_ONLY) != 0 && !spec.category.equals("bus")) continue;
            if ((e.restrict & (e.a == node ? RoadEdge.R_NO_ENTRY_AB : RoadEdge.R_NO_ENTRY_BA)) != 0) continue;
            RoadNode other = net.nodes.get(e.other(node));
            if (other != null && other.type == NodeType.PARKING) continue; // only entered on purpose
            if (other != null && other.type == NodeType.GAS_STATION) continue;
            out.add(e);
        }
        if (out.isEmpty() && in != null && in.allows(node)) out.add(in);
        return out;
    }

    public static int movement(RoadNetwork net, RoadEdge in, int node, RoadEdge out) {
        return TrafficLights.movement(TrafficLights.arrivalDir(net, in, node), TrafficLights.departDir(net, out, node));
    }

    /** Choose a lane on e (travelling away from 'from') suited to the next movement. */
    public static int chooseLane(RoadEdge e, int from, int nextMove, int current, RandomSource r) {
        List<Integer> lanes = e.lanesFrom(from); // right-most first
        if (lanes.isEmpty()) return 0;
        List<Integer> ok = new ArrayList<>();
        for (int k = 0; k < lanes.size(); k++) {
            int li = lanes.get(k);
            int type = e.lanes.get(li).type();
            if (type == Lane.PARKING || type == Lane.BUS) continue;
            boolean right = k == 0, left = k == lanes.size() - 1;
            boolean fits = switch (nextMove) {
                case TrafficLights.MOVE_LEFT, TrafficLights.MOVE_UTURN -> left || type == Lane.LEFT_ONLY;
                case TrafficLights.MOVE_RIGHT -> right || type == Lane.RIGHT_ONLY;
                default -> type != Lane.LEFT_ONLY && type != Lane.RIGHT_ONLY;
            };
            if (fits) ok.add(li);
        }
        if (ok.isEmpty()) ok.add(lanes.get(0));
        if (ok.contains(current)) return current;
        return ok.get(r.nextInt(ok.size()));
    }

    /** Next node toward 'goal' from 'node' (Dijkstra on reversed graph, cached per goal). */
    public static Integer nextHop(RoadNetwork net, int node, int goal) {
        check(net);
        Map<Integer, Integer> m = NEXT_HOP.computeIfAbsent(goal, g -> dijkstra(net, g));
        return m.get(node);
    }

    private static Map<Integer, Integer> dijkstra(RoadNetwork net, int goal) {
        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> next = new HashMap<>();
        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        dist.put(goal, 0.0);
        pq.add(new double[]{0, goal});
        while (!pq.isEmpty()) {
            double[] cur = pq.poll();
            int u = (int) cur[1];
            if (cur[0] > dist.getOrDefault(u, Double.MAX_VALUE)) continue;
            for (RoadEdge e : net.roadEdgesOf(u)) {
                int w = e.other(u);
                if (!e.allows(w)) continue; // must be able to drive w -> u
                EdgeGeom g = net.geom(e);
                double nd = cur[0] + (g == null ? 10 : g.length);
                if (nd < dist.getOrDefault(w, Double.MAX_VALUE)) {
                    dist.put(w, nd);
                    next.put(w, u);
                    pq.add(new double[]{nd, w});
                }
            }
        }
        return next;
    }

    public static RoadEdge pickRandom(List<RoadEdge> exits, RoadNetwork net, RandomSource r) {
        double total = 0;
        for (RoadEdge e : exits) total += 1 + e.lanes.size() * 0.5;
        double x = r.nextDouble() * total;
        for (RoadEdge e : exits) {
            x -= 1 + e.lanes.size() * 0.5;
            if (x <= 0) return e;
        }
        return exits.get(exits.size() - 1);
    }
}
