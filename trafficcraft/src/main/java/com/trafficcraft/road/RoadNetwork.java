package com.trafficcraft.road;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Graph of road and pedestrian nodes/edges for one dimension. Used on the server (saved) and mirrored on clients. */
public final class RoadNetwork {
    public final Map<Integer, RoadNode> nodes = new LinkedHashMap<>();
    public final Map<Integer, RoadEdge> edges = new LinkedHashMap<>();
    private final Map<Integer, List<RoadEdge>> adj = new HashMap<>();
    public int nextNode = 1, nextEdge = 1;
    public int version;
    public boolean dirty;

    // ------------------------------------------------------------------------ queries
    public List<RoadEdge> edgesOf(int node) {
        return adj.getOrDefault(node, List.of());
    }

    public List<RoadEdge> roadEdgesOf(int node) {
        List<RoadEdge> out = new ArrayList<>();
        for (RoadEdge e : edgesOf(node)) if (!e.ped) out.add(e);
        return out;
    }

    public int roadDegree(int node) {
        int n = 0;
        for (RoadEdge e : edgesOf(node)) if (!e.ped) n++;
        return n;
    }

    public boolean isJunction(int node) {
        RoadNode n = nodes.get(node);
        return roadDegree(node) >= 3 || (n != null && (n.has(RoadNode.F_LIGHTS) || n.has(RoadNode.F_STOP) || n.has(RoadNode.F_YIELD)));
    }

    public RoadEdge edgeBetween(int a, int b) {
        for (RoadEdge e : edgesOf(a)) if (e.other(a) == b) return e;
        return null;
    }

    public RoadNode nearestNode(Vec3 p, double max, Boolean ped) {
        RoadNode best = null;
        double bd = max * max;
        for (RoadNode n : nodes.values()) {
            if (ped != null && n.type.ped != ped) continue;
            double d = n.pos().distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        return best;
    }

    /** Nearest edge whose centre line passes within its half width (+margin) of p. */
    public RoadEdge nearestEdge(Vec3 p, double margin, Boolean ped) {
        RoadEdge best = null;
        double bd = Double.MAX_VALUE;
        double[] out = new double[2];
        for (RoadEdge e : edges.values()) {
            if (ped != null && e.ped != ped) continue;
            EdgeGeom g = geom(e);
            if (g == null) continue;
            g.project(p, out);
            double lim = e.width / 2 + margin;
            if (out[1] < lim && out[1] < bd && Math.abs(g.point(g.project(p, null), 0).y - p.y) < 3) {
                bd = out[1];
                best = e;
            }
        }
        return best;
    }

    public float junctionRadius(int node) {
        if (!isJunction(node) || roadDegree(node) < 2) return 0;
        float w = 0;
        for (RoadEdge e : roadEdgesOf(node)) w = Math.max(w, e.width);
        return w / 2 + 1.0f;
    }

    // ------------------------------------------------------------------------ geometry
    public EdgeGeom geom(RoadEdge e) {
        if (e.geom == null) e.geom = buildGeom(e);
        return e.geom;
    }

    public void invalidateGeometry() {
        for (RoadEdge e : edges.values()) e.geom = null;
    }

    private Vec3 tangentAt(int node, RoadEdge e, boolean atStart) {
        RoadNode n = nodes.get(node);
        RoadNode o = nodes.get(e.other(node));
        Vec3 dir = new Vec3(o.x - n.x, 0, o.z - n.z);
        if (e.ctrl != null) {
            Vec3 c = new Vec3(e.ctrl.x - n.x, 0, e.ctrl.z - n.z);
            if (c.lengthSqr() > 1e-6) dir = c;
        } else {
            List<RoadEdge> list = e.ped ? pedEdgesOf(node) : roadEdgesOf(node);
            if (list.size() == 2 && !nodes.get(node).has(RoadNode.F_LIGHTS)) {
                RoadEdge other = list.get(0) == e ? list.get(1) : list.get(0);
                if (other.ctrl == null) {
                    RoadNode prev = nodes.get(other.other(node));
                    Vec3 t = new Vec3(o.x - prev.x, 0, o.z - prev.z);
                    Vec3 in = new Vec3(n.x - prev.x, 0, n.z - prev.z).normalize();
                    Vec3 out = dir.normalize();
                    // only smooth gentle bends; sharp corners stay corners
                    if (t.lengthSqr() > 1e-6 && in.dot(out) > 0.2) dir = t;
                }
            }
        }
        Vec3 d = dir.normalize();
        return atStart ? d : d.scale(-1);
    }

    List<RoadEdge> pedEdgesOf(int node) {
        List<RoadEdge> out = new ArrayList<>();
        for (RoadEdge e : edgesOf(node)) if (e.ped) out.add(e);
        return out;
    }

    private EdgeGeom buildGeom(RoadEdge e) {
        RoadNode na = nodes.get(e.a), nb = nodes.get(e.b);
        if (na == null || nb == null) return null;
        Vec3 a = na.pos(), b = nb.pos();
        double d = Math.hypot(b.x - a.x, b.z - a.z);
        Vec3 p1, p2;
        if (e.ctrl != null) {
            p1 = a.add(e.ctrl.subtract(a).scale(2.0 / 3));
            p2 = b.add(e.ctrl.subtract(b).scale(2.0 / 3));
        } else {
            Vec3 ta = tangentAt(e.a, e, true);
            Vec3 tb = tangentAt(e.b, e, true); // points from B toward the edge interior
            p1 = a.add(ta.scale(d / 3));
            p2 = b.add(tb.scale(d / 3));
        }
        EdgeGeom g = new EdgeGeom(a, p1, p2, b);
        g.trimA = Math.min(g.length * 0.45, junctionRadius(e.a));
        g.trimB = Math.min(g.length * 0.45, junctionRadius(e.b));
        return g;
    }

    // ------------------------------------------------------------------------ editing
    public RoadNode addNode(double x, double y, double z, NodeType t) {
        RoadNode n = new RoadNode(nextNode++, x, y, z, t);
        nodes.put(n.id, n);
        changed();
        return n;
    }

    public void removeNode(int id) {
        for (RoadEdge e : new ArrayList<>(edgesOf(id))) removeEdge(e.id);
        nodes.remove(id);
        adj.remove(id);
        changed();
    }

    public RoadEdge connect(int a, int b, boolean ped) {
        if (a == b || !nodes.containsKey(a) || !nodes.containsKey(b)) return null;
        RoadEdge ex = edgeBetween(a, b);
        if (ex != null) return ex;
        RoadEdge e = new RoadEdge(nextEdge++, a, b);
        e.ped = ped;
        if (ped) {
            e.width = 2f;
            e.speed = 5f;
        }
        edges.put(e.id, e);
        adj.computeIfAbsent(a, k -> new ArrayList<>()).add(e);
        adj.computeIfAbsent(b, k -> new ArrayList<>()).add(e);
        changed();
        return e;
    }

    /** Inserts a node into a road at arc length sv; both halves keep the lanes, width, speed and restrictions. */
    public RoadNode splitEdge(RoadEdge e, double sv, NodeType type) {
        Vec3 p = geom(e).point(sv, 0);
        RoadNode n = addNode(p.x, p.y, p.z, type);
        removeEdge(e.id);
        for (int[] ab : new int[][]{{e.a, n.id}, {n.id, e.b}}) {
            RoadEdge h = connect(ab[0], ab[1], e.ped);
            h.lanes.clear();
            h.lanes.addAll(e.lanes);
            h.width = e.width;
            h.speed = e.speed;
            h.overtake = e.overtake;
            h.restrict = e.restrict;
            h.name = e.name;
        }
        invalidateGeometry();
        return n;
    }

    public void removeEdge(int id) {
        RoadEdge e = edges.remove(id);
        if (e == null) return;
        List<RoadEdge> la = adj.get(e.a), lb = adj.get(e.b);
        if (la != null) la.remove(e);
        if (lb != null) lb.remove(e);
        changed();
    }

    public void changed() {
        version++;
        dirty = true;
        invalidateGeometry();
    }

    private void rebuildAdjacency() {
        adj.clear();
        for (RoadEdge e : edges.values()) {
            adj.computeIfAbsent(e.a, k -> new ArrayList<>()).add(e);
            adj.computeIfAbsent(e.b, k -> new ArrayList<>()).add(e);
        }
    }

    public Collection<RoadNode> nodesOfType(NodeType t) {
        List<RoadNode> out = new ArrayList<>();
        for (RoadNode n : nodes.values()) if (n.type == t) out.add(n);
        return out;
    }

    // ------------------------------------------------------------------------ persistence
    // ------------------------------------------------------------------------ map locations
    public final java.util.Map<Integer, Place> places = new java.util.LinkedHashMap<>();
    public int nextPlace = 1;

    public Place addPlace(double x, double y, double z, String type, String name) {
        Place p = new Place(nextPlace++, x, y, z, type, name);
        places.put(p.id, p);
        changed();
        return p;
    }

    public Place placeNear(Vec3 at, double r) {
        Place best = null;
        double bd = r;
        for (Place p : places.values()) {
            double d = p.pos().distanceTo(at);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag ln = new ListTag();
        for (RoadNode n : nodes.values()) ln.add(n.save());
        ListTag le = new ListTag();
        for (RoadEdge e : edges.values()) le.add(e.save());
        t.put("nodes", ln);
        t.put("edges", le);
        t.putInt("nn", nextNode);
        t.putInt("ne", nextEdge);
        ListTag lp = new ListTag();
        for (Place p : places.values()) lp.add(p.save());
        t.put("places", lp);
        t.putInt("np", nextPlace);
        return t;
    }

    public void load(CompoundTag t) {
        nodes.clear();
        edges.clear();
        for (Tag x : t.getList("nodes", Tag.TAG_COMPOUND)) {
            RoadNode n = RoadNode.load((CompoundTag) x);
            nodes.put(n.id, n);
        }
        for (Tag x : t.getList("edges", Tag.TAG_COMPOUND)) {
            RoadEdge e = RoadEdge.load((CompoundTag) x);
            if (nodes.containsKey(e.a) && nodes.containsKey(e.b)) edges.put(e.id, e);
        }
        nextNode = Math.max(t.getInt("nn"), 1);
        nextEdge = Math.max(t.getInt("ne"), 1);
        places.clear();
        for (Tag x : t.getList("places", Tag.TAG_COMPOUND)) {
            Place p = Place.load((CompoundTag) x);
            places.put(p.id, p);
        }
        nextPlace = Math.max(t.getInt("np"), 1);
        rebuildAdjacency();
        version++;
        invalidateGeometry();
    }
}
