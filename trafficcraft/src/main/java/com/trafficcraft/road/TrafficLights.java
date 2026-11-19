package com.trafficcraft.road;

import com.trafficcraft.TCConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * Signal controller for a junction node with F_LIGHTS. Approaches are grouped into opposing axes; each axis gets a
 * main phase (straight + right, permissive left) and optionally a protected left-turn phase. Only one axis is ever
 * green, so conflicting movements never get green together. Pedestrians cross the roads of the axis that is red.
 * The state is a pure function of game time + node config, so clients compute it themselves (no packets).
 */
public final class TrafficLights {
    public static final int RED = 0, YELLOW = 1, GREEN = 2, RED_YELLOW = 3, FLASH = 4, OFF = 5;
    public static final int MOVE_STRAIGHT = 0, MOVE_LEFT = 1, MOVE_RIGHT = 2, MOVE_UTURN = 3;

    public record Phase(int axis, boolean leftOnly, int green) {}

    public record Plan(List<List<Integer>> axes, List<Phase> phases, int yellow, int allRed, int cycle) {}

    /** Incoming road edges (vehicles can travel into the node on them). */
    public static List<RoadEdge> approaches(RoadNetwork net, int node) {
        List<RoadEdge> out = new ArrayList<>();
        for (RoadEdge e : net.roadEdgesOf(node)) {
            int dirInto = e.b == node ? 1 : -1;
            for (Lane l : e.lanes) if (l.dir() == dirInto) {
                out.add(e);
                break;
            }
        }
        return out;
    }

    /** Travel direction (unit, XZ) of a vehicle arriving at node on edge e. */
    public static Vec3 arrivalDir(RoadNetwork net, RoadEdge e, int node) {
        EdgeGeom g = net.geom(e);
        if (g == null) return new Vec3(1, 0, 0);
        return e.b == node ? g.tangent(g.length - 0.5) : g.tangent(0.5).scale(-1);
    }

    public static Vec3 departDir(RoadNetwork net, RoadEdge e, int node) {
        EdgeGeom g = net.geom(e);
        if (g == null) return new Vec3(1, 0, 0);
        return e.a == node ? g.tangent(0.5) : g.tangent(g.length - 0.5).scale(-1);
    }

    public static int movement(Vec3 in, Vec3 out) {
        double dot = in.x * out.x + in.z * out.z;
        double cross = in.x * out.z - in.z * out.x;
        if (dot < -0.85) return MOVE_UTURN;
        if (dot > 0.6) return MOVE_STRAIGHT;
        return cross > 0 ? MOVE_RIGHT : MOVE_LEFT;
    }

    public static Plan plan(RoadNetwork net, RoadNode n) {
        List<RoadEdge> app = approaches(net, n.id);
        List<List<Integer>> axes = new ArrayList<>();
        boolean[] used = new boolean[app.size()];
        for (int i = 0; i < app.size(); i++) {
            if (used[i]) continue;
            used[i] = true;
            List<Integer> axis = new ArrayList<>();
            axis.add(app.get(i).id);
            Vec3 di = arrivalDir(net, app.get(i), n.id);
            for (int j = i + 1; j < app.size(); j++) {
                if (used[j]) continue;
                Vec3 dj = arrivalDir(net, app.get(j), n.id);
                if (di.x * dj.x + di.z * dj.z < -0.7) {
                    used[j] = true;
                    axis.add(app.get(j).id);
                    break;
                }
            }
            axes.add(axis);
        }
        CompoundTag c = n.cfg;
        int green = c.contains("green") ? c.getInt("green") : (int) (double) TCConfig.LIGHT_GREEN.get();
        int yellow = c.contains("yellow") ? c.getInt("yellow") : 3;
        int allRed = c.contains("allred") ? c.getInt("allred") : 2;
        boolean protectedLeft = c.getBoolean("leftPhase");
        int leftGreen = c.contains("leftGreen") ? c.getInt("leftGreen") : 6;
        List<Phase> phases = new ArrayList<>();
        for (int a = 0; a < axes.size(); a++) {
            phases.add(new Phase(a, false, green * 20));
            if (protectedLeft) phases.add(new Phase(a, true, leftGreen * 20));
        }
        int cycle = 0;
        for (Phase p : phases) cycle += p.green() + (yellow + allRed) * 20;
        return new Plan(axes, phases, yellow * 20, allRed * 20, Math.max(cycle, 20));
    }

    /** Current phase index and ticks into it. Returns {phaseIndex, tickInPhase, phaseLength}. */
    public static int[] state(Plan plan, RoadNode n, long time) {
        String mode = n.cfg.getString("mode");
        if (mode.equals("manual")) {
            int idx = Math.floorMod(n.cfg.getInt("manualPhase"), Math.max(1, plan.phases().size()));
            return new int[]{idx, 0, Integer.MAX_VALUE};
        }
        long t = Math.floorMod(time - n.cfg.getLong("t0") + n.id * 37L, plan.cycle());
        for (int i = 0; i < plan.phases().size(); i++) {
            Phase p = plan.phases().get(i);
            int len = p.green() + plan.yellow() + plan.allRed();
            if (t < len) return new int[]{i, (int) t, len};
            t -= len;
        }
        return new int[]{0, 0, plan.cycle()};
    }

    /**
     * Signal for a vehicle approaching on edge 'approach' that wants to make 'move'.
     * Returns {signal, secondsLeft}. Permissive left turns get GREEN but must still yield (handled by the AI).
     */
    public static int[] signal(RoadNetwork net, RoadNode n, int approach, int move, long time) {
        String mode = n.cfg.getString("mode");
        if (mode.equals("off")) return new int[]{OFF, 0};
        if (mode.equals("flash")) return new int[]{FLASH, 0};
        Plan plan = plan(net, n);
        int axis = -1;
        for (int a = 0; a < plan.axes().size(); a++) if (plan.axes().get(a).contains(approach)) axis = a;
        if (axis < 0) return new int[]{RED, 0};
        int[] st = state(plan, n, time);
        Phase p = plan.phases().get(st[0]);
        boolean mine = p.axis() == axis && (!p.leftOnly() || move == MOVE_LEFT || move == MOVE_UTURN);
        boolean protectedLeftExists = n.cfg.getBoolean("leftPhase");
        if (mine && !p.leftOnly() && protectedLeftExists && (move == MOVE_LEFT || move == MOVE_UTURN)) mine = true; // permissive as well
        if (!mine) {
            // seconds until this axis gets green
            int left = ticksUntilAxis(plan, n, axis, time) / 20;
            return new int[]{RED, left};
        }
        int t = st[1];
        if (t < p.green()) return new int[]{GREEN, (p.green() - t) / 20};
        if (t < p.green() + plan.yellow()) return new int[]{YELLOW, (p.green() + plan.yellow() - t) / 20};
        return new int[]{RED, 0};
    }

    private static int ticksUntilAxis(Plan plan, RoadNode n, int axis, long time) {
        if (n.cfg.getString("mode").equals("manual")) return 0;
        int[] st = state(plan, n, time);
        int idx = st[0];
        int acc = st[2] - st[1];
        for (int k = 1; k <= plan.phases().size(); k++) {
            Phase p = plan.phases().get((idx + k) % plan.phases().size());
            if (p.axis() == axis && !p.leftOnly()) return acc;
            acc += p.green() + plan.yellow() + plan.allRed();
        }
        return acc;
    }

    /** Pedestrian signal for crossing the road 'edge' at node: walk while that road's axis is red (and not yellow). */
    public static int[] pedSignal(RoadNetwork net, RoadNode n, int edgeCrossed, long time) {
        String mode = n.cfg.getString("mode");
        if (mode.equals("off") || mode.equals("flash")) return new int[]{FLASH, 0};
        Plan plan = plan(net, n);
        int axis = -1;
        for (int a = 0; a < plan.axes().size(); a++) if (plan.axes().get(a).contains(edgeCrossed)) axis = a;
        if (axis < 0) {
            // outgoing-only road: walk when every axis is between phases, approximate with first axis red
            axis = 0;
        }
        int[] st = state(plan, n, time);
        Phase p = plan.phases().get(st[0]);
        boolean roadGreen = p.axis() == axis && st[1] < p.green() + plan.yellow() + 10;
        if (roadGreen) return new int[]{RED, 0};
        int left = Math.max(0, ticksUntilAxis(plan, n, axis, time) - 60) / 20;
        return left <= 0 ? new int[]{YELLOW, 0} : new int[]{GREEN, left};
    }

    public static void command(RoadNetwork net, int nodeId, String cmd, long time) {
        RoadNode n = net.nodes.get(nodeId);
        if (n == null) return;
        switch (cmd) {
            case "auto" -> {
                n.cfg.putString("mode", "auto");
                n.cfg.putLong("t0", time);
            }
            case "manual" -> n.cfg.putString("mode", "manual");
            case "next" -> {
                n.cfg.putString("mode", "manual");
                n.cfg.putInt("manualPhase", n.cfg.getInt("manualPhase") + 1);
            }
            case "flash", "off" -> n.cfg.putString("mode", cmd);
            default -> { }
        }
        net.changed();
    }
}
