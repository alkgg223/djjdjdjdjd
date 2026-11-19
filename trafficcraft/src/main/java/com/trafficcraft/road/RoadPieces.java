package com.trafficcraft.road;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * One-click road pieces for the Road Planner: sharp 90° turns (left / right), T-junctions and crossroads.
 * Pieces are oriented to where the player looks; clicked on the end of an existing road they continue it,
 * and arm ends snap to nearby road nodes so pieces join up.
 */
public final class RoadPieces {
    public static final int TURN_LEFT = 0, TURN_RIGHT = 1, T_JUNCTION = 2, CROSSROADS = 3;

    final ServerLevel level;
    final RoadNetwork net;
    final CompoundTag tool;
    final List<RoadEdge> made = new ArrayList<>();
    final List<Integer> junctionsHit = new ArrayList<>();

    RoadPieces(ServerLevel level, RoadNetwork net, CompoundTag tool) {
        this.level = level;
        this.net = net;
        this.tool = tool;
    }

    static Vec3 dir(Direction d) {
        return new Vec3(d.getStepX(), 0, d.getStepZ());
    }

    /** Unit direction rotated 90° to the left (seen from above, Minecraft coordinates). */
    static Vec3 left(Vec3 f) {
        return new Vec3(f.z, 0, -f.x);
    }

    float width() {
        return tool.contains("width") ? tool.getFloat("width") : 5f;
    }

    double arm() {
        return Math.max(6, tool.contains("armLength") ? tool.getInt("armLength") : 16);
    }

    /** Existing road node near p (to join pieces), or a new one. */
    RoadNode nodeAt(Vec3 p, double snap) {
        RoadNode n = net.nearestNode(p, snap, false);
        if (n != null) return n;
        if (snap > 1) {
            // landing on an existing road: split it and join there
            RoadEdge e = net.nearestEdge(p, 0.5, false);
            if (e != null && !made.contains(e)) {
                EdgeGeom g = net.geom(e);
                double sv = g.project(p, null);
                if (sv > 6 && sv < g.length - 6) {
                    RoadNode j = net.splitEdge(e, sv, NodeType.ROAD);
                    j.flags |= RoadNode.F_CROSSWALK;
                    junctionsHit.add(j.id);
                    return j;
                }
            }
        }
        RoadNode nn = net.addNode(p.x, p.y, p.z, NodeType.ROAD);
        return nn;
    }

    RoadEdge edge(RoadNode a, RoadNode b, Vec3 ctrl) {
        if (a.id == b.id) return null;
        RoadEdge e = net.edgeBetween(a.id, b.id);
        if (e != null) return e;
        e = net.connect(a.id, b.id, false);
        RoadEditing.applyToolToEdge(e, tool, false);
        e.ctrl = ctrl;
        made.add(e);
        return e;
    }

    void finish(List<Integer> junctions) {
        net.invalidateGeometry();
        net.changed();
        if (!tool.contains("pave") || tool.getBoolean("pave")) {
            for (RoadEdge e : made) RoadPaver.paveEdge(level, net, e, tool, RoadPaver.WALKS);
            for (RoadEdge e : made) RoadPaver.paveEdge(level, net, e, tool, RoadPaver.ROAD);
            for (int j : junctions) if (net.isJunction(j)) RoadPaver.paveJunction(level, net, j);
            for (int j : junctionsHit) if (net.isJunction(j)) RoadPaver.paveJunction(level, net, j);
        }
    }

    /** Direction an existing dead-end road at node n arrives from (pointing into the node), or null. */
    Vec3 arrivalAt(RoadNode n) {
        List<RoadEdge> es = net.roadEdgesOf(n.id);
        if (es.size() != 1) return null;
        RoadEdge e = es.get(0);
        EdgeGeom g = net.geom(e);
        Vec3 t = e.a == n.id ? g.tangent(0).scale(-1) : g.tangent(g.length);
        Vec3 h = new Vec3(t.x, 0, t.z);
        return h.lengthSqr() < 1e-6 ? null : h.normalize();
    }

    public static boolean build(ServerPlayer p, Vec3 click, int kind, CompoundTag tool) {
        return build(p.serverLevel(), click, p.getDirection(), kind, tool);
    }

    public static boolean build(ServerLevel level, Vec3 click, Direction facing, int kind, CompoundTag tool) {
        RoadNetwork net = RoadData.get(level);
        RoadPieces rp = new RoadPieces(level, net, tool);
        RoadNode start = net.nearestNode(click, 2.0, false);
        Vec3 fwd = dir(facing);
        Vec3 arrive = start != null ? rp.arrivalAt(start) : null;
        if (arrive != null) fwd = arrive; // continue the road the player clicked on
        return switch (kind) {
            case TURN_LEFT, TURN_RIGHT -> rp.turn(click, start, arrive != null, fwd, kind == TURN_LEFT);
            default -> rp.junction(click, start, arrive != null, fwd, kind == CROSSROADS);
        };
    }

    /**
     * Sharp 90° bend: straight in, a tight arc (radius about one road width) and straight out.
     * The arc is a curve with its control point on the corner, so traffic follows a smooth but tight turn.
     */
    boolean turn(Vec3 click, RoadNode start, boolean continuing, Vec3 fwd, boolean toLeft) {
        double r = Math.max(6, width() * 1.1), L = arm();
        Vec3 side = toLeft ? left(fwd) : left(fwd).scale(-1);
        Vec3 corner, p1;
        RoadNode n1;
        if (continuing) {
            p1 = start.pos();
            n1 = start;
            corner = p1.add(fwd.scale(r));
        } else {
            corner = click;
            p1 = corner.subtract(fwd.scale(r));
            RoadNode back = nodeAt(corner.subtract(fwd.scale(L)), 3.5);
            n1 = nodeAt(p1, 0.5);
            edge(back, n1, null);
        }
        Vec3 p2 = corner.add(side.scale(r));
        RoadNode n2 = nodeAt(p2, 0.5);
        edge(n1, n2, new Vec3(corner.x, corner.y, corner.z));
        RoadNode end = nodeAt(corner.add(side.scale(L)), 3.5);
        edge(n2, end, null);
        finish(List.of());
        return true;
    }

    /** T-junction (stem toward the player / the clicked road, arms left and right) or crossroads (also straight on). */
    boolean junction(Vec3 click, RoadNode start, boolean continuing, Vec3 fwd, boolean cross) {
        double L = arm();
        RoadNode c;
        if (continuing) c = start;
        else {
            c = nodeAt(click, 1.5);
            RoadNode back = nodeAt(click.subtract(fwd.scale(L)), 3.5);
            edge(back, c, null);
        }
        Vec3 cp = c.pos();
        List<Vec3> arms = new ArrayList<>(List.of(left(fwd), left(fwd).scale(-1)));
        if (cross) arms.add(fwd);
        for (Vec3 a : arms) {
            // skip a direction that already has a road
            boolean taken = false;
            for (RoadEdge e : net.roadEdgesOf(c.id)) {
                RoadNode o = net.nodes.get(e.other(c.id));
                Vec3 d = new Vec3(o.x - c.x, 0, o.z - c.z).normalize();
                if (d.dot(a) > 0.85) taken = true;
            }
            if (taken) continue;
            RoadNode end = nodeAt(cp.add(a.scale(L)), 3.5);
            edge(c, end, null);
        }
        c.flags |= RoadNode.F_CROSSWALK;
        finish(List.of(c.id));
        return true;
    }
}
