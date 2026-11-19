package com.trafficcraft.npc;

import com.trafficcraft.ai.BorderControl;
import com.trafficcraft.ai.TrafficManager;
import com.trafficcraft.road.EdgeGeom;
import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadEdge;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.road.TrafficLights;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Pedestrian behaviour: route over the pedestrian graph, safe crossings, reactions; or the worker's job. */
public final class PedBrain extends Goal {
    final PedestrianEntity ped;
    final ArrayDeque<Integer> route = new ArrayDeque<>();
    int at = -1;             // node we are standing at / last reached
    int spawner = -1;
    int dwell, pauseTicks, scareTicks, hurryTicks, repath, stuck;
    Vec3 lastPos;
    double speed = 0.55;
    Integer serving;
    Vec3 sideStep;
    int sideStepTicks;         // worker: vehicle being served / inspected

    private static final Map<Integer, Integer> CROSS = new HashMap<>(); // ped edge -> crossed road edge (or -1)
    private static int crossVersion = -1;

    PedBrain(PedestrianEntity ped) {
        this.ped = ped;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !ped.level().isClientSide;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void pause(int t) {
        pauseTicks = Math.max(pauseTicks, t);
        ped.getNavigation().stop();
    }

    public void scare(int t) {
        scareTicks = t;
        hurryTicks = Math.max(hurryTicks, t * 2);
    }

    public void start(int node, int spawner, double speed) {
        at = node;
        this.spawner = spawner;
        this.speed = speed;
    }

    /** A horn sounded nearby: hurry off the road, otherwise just look. */
    public void horn(VehicleEntity v) {
        ped.getLookControl().setLookAt(v, 30, 30);
        Vec3 to = ped.position().subtract(v.position());
        Vec3 f = v.forward();
        double along = to.x * f.x + to.z * f.z, lat = to.x * f.z - to.z * f.x;
        if (along > 0 && along < 10 && Math.abs(lat) < v.spec().width / 2 + 1.2) {
            // standing in the car's way: step to the nearer side
            double side = lat >= 0 ? 1 : -1;
            Vec3 perp = new Vec3(f.z, 0, -f.x).scale(side);
            sideStep = ped.position().add(perp.scale(v.spec().width / 2 + 2.2 - Math.abs(lat)));
            sideStepTicks = 50;
            return;
        }
        if (onRoad()) hurryTicks = 80;
        else pause(15);
    }

    boolean onRoad() {
        var st = ped.level().getBlockState(ped.blockPosition().below());
        String id = st.getBlock().builtInRegistryHolder().key().location().getPath();
        return id.startsWith("asphalt");
    }

    @Override
    public void tick() {
        if (!(ped.level() instanceof ServerLevel level)) return;
        RoadNetwork net = RoadData.get(level);
        if (sideStepTicks > 0) {
            sideStepTicks--;
            ped.setAnim(PedestrianEntity.A_WALK);
            if (walkTo(sideStep, speed * 1.4)) sideStepTicks = Math.min(sideStepTicks, 20);
            return;
        }
        if (ped.isWorker()) {
            Workplaces.workTick(ped, level, net, this);
            return;
        }
        if (pauseTicks > 0) {
            pauseTicks--;
            if (pauseTicks == 0) ped.setAnim(PedestrianEntity.A_WALK);
            return;
        }
        dangerCheck(level);
        if (scareTicks > 0) {
            scareTicks--;
            ped.setAnim(PedestrianEntity.A_SCARED);
            return;
        }
        if (hurryTicks > 0) hurryTicks--;
        if (dwell > 0) {
            dwell--;
            ped.setAnim(dwell % 200 < 120 ? PedestrianEntity.A_IDLE : PedestrianEntity.A_PHONE);
            return;
        }
        if (route.isEmpty()) {
            plan(level, net);
            if (route.isEmpty()) {
                ped.setAnim(PedestrianEntity.A_IDLE);
                return;
            }
        }
        int next = route.peekFirst();
        RoadNode nn = net.nodes.get(next);
        if (nn == null) {
            route.clear();
            return;
        }
        // crossing permission before stepping onto a crossing edge
        RoadEdge e = at >= 0 ? net.edgeBetween(at, next) : null;
        if (e != null && e.ped && ped.position().distanceTo(net.nodes.get(at).pos()) < 1.6) {
            int road = crossedRoad(net, e);
            if (road >= 0 && !mayCross(level, net, e, road)) {
                ped.getNavigation().stop();
                ped.setAnim(PedestrianEntity.A_WAIT);
                Vec3 mid = midpoint(net, e);
                ped.getLookControl().setLookAt(mid.x, mid.y + 1, mid.z);
                return;
            }
        }
        ped.setAnim(PedestrianEntity.A_WALK);
        double d = ped.position().distanceTo(nn.pos());
        if (d < 0.8) {
            RoadEdge done = at >= 0 ? net.edgeBetween(at, next) : null;
            if (done != null && crossedRoad(net, done) >= 0) com.trafficcraft.ai.TrafficStats.inc("ped_crossings");
            at = next;
            route.pollFirst();
            if (route.isEmpty()) arrive(level, net, nn);
            return;
        }
        if (--repath <= 0 || ped.getNavigation().isDone()) {
            double sp = speed * (hurryTicks > 0 ? 1.7 : 1.0);
            ped.getNavigation().moveTo(nn.x, nn.y, nn.z, sp);
            repath = 20;
        }
        // stuck detection
        if (lastPos != null && ped.position().distanceToSqr(lastPos) < 0.0004) {
            if (++stuck > 80) {
                stuck = 0;
                ped.moveTo(nn.x, nn.y, nn.z);
            }
        } else stuck = 0;
        lastPos = ped.position();
    }

    void arrive(ServerLevel level, RoadNetwork net, RoadNode n) {
        if (n.type == NodeType.PED_SPAWN && n.id != spawner && ped.getRandom().nextFloat() < 0.7f) {
            ped.discard();
            return;
        }
        int min = n.cfg.contains("dwellMin") ? n.cfg.getInt("dwellMin") : 3, max = n.cfg.contains("dwellMax") ? n.cfg.getInt("dwellMax") : 20;
        if (n.type == NodeType.PED_DEST || n.cfg.getBoolean("wait")) dwell = 20 * (min + ped.getRandom().nextInt(Math.max(1, max - min + 1)));
        else dwell = 10 + ped.getRandom().nextInt(30);
    }

    void plan(ServerLevel level, RoadNetwork net) {
        if (at < 0 || !net.nodes.containsKey(at)) {
            RoadNode near = net.nearestNode(ped.position(), 24, true);
            if (near == null) return;
            at = near.id;
        }
        // BFS over pedestrian edges
        Map<Integer, Integer> prev = new HashMap<>();
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(at);
        prev.put(at, -1);
        List<Integer> goals = new ArrayList<>();
        while (!q.isEmpty()) {
            int u = q.poll();
            RoadNode un = net.nodes.get(u);
            if (u != at && un != null && (un.type == NodeType.PED_DEST || un.type == NodeType.PED_SPAWN || un.cfg.getBoolean("dest"))) goals.add(u);
            for (RoadEdge e : net.edgesOf(u)) {
                if (!e.ped) continue;
                int w = e.other(u);
                if (prev.containsKey(w)) continue;
                prev.put(w, u);
                q.add(w);
            }
        }
        if (goals.isEmpty()) {
            for (int k : prev.keySet()) if (k != at) goals.add(k);
            if (goals.isEmpty()) return;
        }
        int goal = goals.get(ped.getRandom().nextInt(goals.size()));
        ArrayDeque<Integer> path = new ArrayDeque<>();
        for (int x = goal; x != at && x != -1; x = prev.get(x)) path.addFirst(x);
        route.clear();
        route.addAll(path);
    }

    static Vec3 midpoint(RoadNetwork net, RoadEdge e) {
        return net.nodes.get(e.a).pos().add(net.nodes.get(e.b).pos()).scale(0.5);
    }

    /** Road edge crossed by a pedestrian edge (geometric test, cached per network version). */
    static int crossedRoad(RoadNetwork net, RoadEdge pe) {
        if (net.version != crossVersion) {
            CROSS.clear();
            crossVersion = net.version;
        }
        return CROSS.computeIfAbsent(pe.id, k -> {
            Vec3 a = net.nodes.get(pe.a).pos(), b = net.nodes.get(pe.b).pos();
            double[] out = new double[2];
            for (RoadEdge re : net.edges.values()) {
                if (re.ped) continue;
                EdgeGeom g = net.geom(re);
                if (g == null) continue;
                for (int i = 1; i < 10; i++) {
                    Vec3 p = a.add(b.subtract(a).scale(i / 10.0));
                    g.project(p, out);
                    if (out[1] < re.width / 2 - 0.3 && Math.abs(p.y - a.y) < 3) return re.id;
                }
            }
            // crossing inside a junction area counts as crossing its approaches
            for (RoadNode n : net.nodes.values()) {
                if (!net.isJunction(n.id) || n.type.ped) continue;
                Vec3 m = a.add(b).scale(0.5);
                if (m.distanceTo(n.pos()) < net.junctionRadius(n.id) + 4) {
                    RoadEdge best = null;
                    double bd = 1e9;
                    for (RoadEdge re : net.roadEdgesOf(n.id)) {
                        EdgeGeom g = net.geom(re);
                        g.project(m, out);
                        if (out[1] < bd) {
                            bd = out[1];
                            best = re;
                        }
                    }
                    if (best != null && bd < best.width / 2 + 3) return best.id;
                }
            }
            return -1;
        });
    }

    boolean mayCross(ServerLevel level, RoadNetwork net, RoadEdge pe, int roadEdge) {
        RoadEdge re = net.edges.get(roadEdge);
        if (re == null) return true;
        Vec3 mid = midpoint(net, pe);
        // signalised junction nearby?
        RoadNode lights = null;
        for (int id : new int[]{re.a, re.b}) {
            RoadNode n = net.nodes.get(id);
            if (n != null && n.has(RoadNode.F_LIGHTS) && n.pos().distanceTo(mid) < net.junctionRadius(id) + 8) lights = n;
        }
        if (lights != null) {
            int[] sig = TrafficLights.pedSignal(net, lights, roadEdge, level.getGameTime());
            if (sig[0] == TrafficLights.FLASH) return gapOk(level, mid);
            return sig[0] == TrafficLights.GREEN && gapOk(level, mid);
        }
        return gapOk(level, mid);
    }

    /** No vehicle about to reach the crossing (unsignalised zebra or as a safety check). */
    boolean gapOk(ServerLevel level, Vec3 mid) {
        List<Entity> near = new ArrayList<>();
        TrafficManager.get(level).grid.near(mid.x, mid.z, 30, near);
        for (Entity o : near) {
            if (!(o instanceof VehicleEntity v)) continue;
            Vec3 to = mid.subtract(v.position());
            double d = to.length();
            if (d < 4.5) return false;
            double closing = v.visSpeed() * (v.forward().x * to.x + v.forward().z * to.z) / Math.max(0.1, d);
            if (closing > 0.5 && d / closing < 4.5) return false;
        }
        return true;
    }

    /** Step back from vehicles heading straight at us. */
    void dangerCheck(ServerLevel level) {
        if (ped.tickCount % 4 != 0) return;
        List<Entity> near = new ArrayList<>();
        TrafficManager.get(level).grid.near(ped.getX(), ped.getZ(), 10, near);
        for (Entity o : near) {
            if (!(o instanceof VehicleEntity v) || Math.abs(v.visSpeed()) < 3) continue;
            Vec3 to = ped.position().subtract(v.position());
            double d = to.length();
            if (d > 9) continue;
            Vec3 f = v.forward().scale(Math.signum(v.visSpeed()));
            double along = to.x * f.x + to.z * f.z, lat = Math.abs(to.x * f.z - to.z * f.x);
            if (along > 0 && lat < v.spec().width / 2 + 1.2 && along < 3 + Math.abs(v.visSpeed()) * 0.8) {
                Vec3 away = new Vec3(f.z, 0, -f.x).scale(to.x * f.z - to.z * f.x > 0 ? 1 : -1);
                ped.setDeltaMovement(away.scale(0.45).add(0, 0.25, 0));
                ped.hurtMarked = true;
                scare(25);
                com.trafficcraft.ai.TrafficStats.inc("ped_dodged");
                return;
            }
        }
    }

    /** Officer walks to a waiting vehicle; returns true when standing at its window. */
    boolean walkTo(Vec3 p, double sp) {
        if (ped.position().distanceTo(p) < 1.3) {
            ped.getNavigation().stop();
            return true;
        }
        if (--repath <= 0 || ped.getNavigation().isDone()) {
            ped.getNavigation().moveTo(p.x, p.y, p.z, sp);
            repath = 15;
        }
        return false;
    }

    static BorderControl border(ServerLevel l) {
        return TrafficManager.get(l).border;
    }
}
