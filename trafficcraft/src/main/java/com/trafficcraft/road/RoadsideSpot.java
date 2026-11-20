package com.trafficcraft.road;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * A single kerbside parking place (PARK_SPOT node) next to a road: no edges, it belongs to the nearest road.
 * Cars travelling on that road on the spot's side pull straight in, wait, back up a little and drive on.
 */
public record RoadsideSpot(int node, int edge, double sv, double offset, int dir, Vec3 center, Vec3 facing, double length) {
    private static final Map<Integer, List<RoadsideSpot>> BY_EDGE = new HashMap<>();
    private static int version = -1;
    private static RoadNetwork cached;

    public static RoadsideSpot of(RoadNetwork net, RoadNode n) {
        if (n.type != NodeType.PARK_SPOT) return null;
        RoadEdge best = null;
        double bd = 12, bs = 0, bo = 0;
        double[] out = new double[2];
        for (RoadEdge e : net.edges.values()) {
            if (e.ped || e.lanes.isEmpty()) continue;
            EdgeGeom g = net.geom(e);
            if (g == null) continue;
            double sv = g.project(n.pos(), out);
            double kerb = out[1] - best_w(e); // distance from the road edge, not its centre line
            if (out[1] < 12 && kerb < bd && sv > 2 && sv < g.length - 2) {
                bd = kerb;
                best = e;
                bs = sv;
                bo = out[0];
            }
        }
        if (best == null) return null;
        // right-hand traffic: the spot on the right of A->B serves A->B traffic (one-way roads: their direction)
        int dir = bo < 0 ? 1 : -1;
        if (!best.allows(dir > 0 ? best.a : best.b)) dir = -dir;
        if (!best.allows(dir > 0 ? best.a : best.b)) return null;
        // never stand in a driving lane: keep the car's inner side outside the outermost driving lane on that side
        double outer = 0;
        float lw = best.laneWidth();
        for (int i = 0; i < best.lanes.size(); i++) {
            if (best.lanes.get(i).type() == Lane.PARKING) continue;
            double lo = best.laneOffset(i);
            if (Math.signum(lo) == Math.signum(bo) || Math.abs(lo) < 1e-3) outer = Math.max(outer, Math.abs(lo) + lw / 2);
        }
        double minAbs = outer + 1.0;
        if (Math.abs(bo) < minAbs) bo = Math.signum(bo == 0 ? -1 : bo) * minAbs;
        EdgeGeom g = net.geom(best);
        Vec3 c = g.point(bs, bo);
        Vec3 f = g.tangent(bs).scale(dir);
        double len = n.cfg.contains("length") ? n.cfg.getFloat("length") : 6.0;
        return new RoadsideSpot(n.id, best.id, bs, bo, dir, c, f, len);
    }

    static double best_w(RoadEdge e) {
        return e.width / 2;
    }

    public static List<RoadsideSpot> onEdge(RoadNetwork net, int edge) {
        if (net != cached || net.version != version) {
            BY_EDGE.clear();
            for (RoadNode n : net.nodesOfType(NodeType.PARK_SPOT)) {
                RoadsideSpot s = of(net, n);
                if (s != null) BY_EDGE.computeIfAbsent(s.edge, k -> new ArrayList<>()).add(s);
            }
            cached = net;
            version = net.version;
        }
        return BY_EDGE.getOrDefault(edge, List.of());
    }
}
