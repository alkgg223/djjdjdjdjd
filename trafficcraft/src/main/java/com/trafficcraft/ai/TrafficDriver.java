package com.trafficcraft.ai;

import com.trafficcraft.TCConfig;
import com.trafficcraft.road.EdgeGeom;
import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.ParkingLayout;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadEdge;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.road.TrafficLights;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleInput;
import com.trafficcraft.vehicle.VehicleSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * AI driver of one vehicle: lane following (pure pursuit), car following (IDM), junction rules (signals, stop/yield,
 * reservations, no blocking the box), overtaking, indicators/lights/horn, parking, refuelling and border stops.
 */
public final class TrafficDriver {
    public enum State { DRIVE, PARK_IN, PARKED, PARK_OUT, TO_PUMP, FUELING, BORDER_WAIT, BROKEN, DESPAWN }

    public final Personality pers;
    public final int spawner;
    final ArrayDeque<Piece> path = new ArrayDeque<>();
    double s;              // progress along path.peekFirst()
    float cruise;          // personal cruise factor
    float maxSpeed;        // m/s cap from spawner
    public State state = State.DRIVE;
    int goal = -1;         // goal node id (parking / gas / border)
    int parkingSpace = -1;
    int lastPlanNode = -1;
    int waitTicks, blockedTicks, honkCooldown, stateTicks, stopSignTicks;
    boolean stopSignDone;
    int reservedNode = -1;
    double stopTargetGap = 99;
    public boolean lastStopTold;
    int lastApproach = -1;
    int reverseTicks, unstickCooldown;
    int stillTicks, blipQueue, blipDelay, launchTicks, liftTicks;
    boolean spotMode;
    final java.util.Set<Integer> consideredSpots = new java.util.HashSet<>();
    com.trafficcraft.road.RoadsideSpot pendingSpot;
    VehicleInput last = VehicleInput.NONE;
    boolean overtaking;
    float laneOffsetNow;
    int noProgressTicks;
    Vec3 lastPos;
    String reason = "";
    double lastGap = 999;
    Entity lastLeader;
    int specialStuck;

    public TrafficDriver(Personality p, int spawner, float cruise, float maxSpeed) {
        this.pers = p;
        this.spawner = spawner;
        this.cruise = cruise;
        this.maxSpeed = maxSpeed;
    }

    public String debug() {
        String who = lastLeader instanceof com.trafficcraft.npc.PedestrianEntity pe ? String.format("[%s %.1f,%.1f]", pe.kind(), pe.getX(), pe.getZ()) : "";
        return state + " " + reason + who + String.format(" gap=%.1f", lastGap);
    }

    // =================================================================================== setup
    /** Place the driver on lane 'lane' of edge e starting near arc position sOnEdge, travelling away from node 'from'. */
    public void start(RoadNetwork net, RoadEdge e, int from, int lane, double sOnEdge) {
        path.clear();
        Piece p = RoutePlanner.edgePiece(net, e, from, lane, sOnEdge, null, 0);
        path.add(p);
        s = 0;
        lastPlanNode = -1;
    }

    // =================================================================================== main entry
    public VehicleInput compute(VehicleEntity v, ServerLevel level, RoadNetwork net, TrafficManager tm) {
        stateTicks++;
        if (honkCooldown > 0) honkCooldown--;
        VehicleSpec spec = v.spec();
        if (state == State.PARKED) return parked(v, level, net, tm);
        if (state == State.FUELING) return fueling(v, level, net, tm);
        if (state == State.BROKEN) {
            v.setFlag(VehicleEntity.F_HAZARD, true);
            if (stateTicks > 20 * (30 + v.getRandom().nextInt(60))) {
                state = State.DRIVE;
                v.setFlag(VehicleEntity.F_HAZARD, false);
            }
            return VehicleInput.PARK;
        }
        if ((state == State.TO_PUMP && stateTicks > 20 * 40 || state == State.PARK_IN && stateTicks > 20 * 50) && Math.abs(v.speed) < 0.3f) abandon(v, net, tm);
        if (path.isEmpty()) {
            state = State.DESPAWN;
            return VehicleInput.PARK;
        }
        if (!v.has(VehicleEntity.F_ENGINE)) v.setFlag(VehicleEntity.F_ENGINE, true);
        Vec3 pos = v.position();
        // ---------- progress along the path
        Piece cur = path.peekFirst();
        s = cur.project(pos, s);
        if (cur.node <= -2 && Math.abs(v.speed) < 0.3f && !reason.equals("vehicle") && !reason.equals("pedestrian")) specialStuck++;
        else specialStuck = 0;
        boolean stuckThere = specialStuck > 60 && stopTargetGap < 6 || specialStuck > 200;
        while ((s >= cur.length - 0.35 || (cur.node <= -2 && (cur.length - s < 1.8 || stopTargetGap < 2.6 || stuckThere) && Math.abs(v.speed) < 0.5f)) && path.size() > 0) {
            if (stuckThere && cur.node <= -2) {
                // could not complete the manoeuvre: settle onto the final point of the piece
                Vec3 end = cur.at(cur.length);
                Vec3 dir = cur.tangent(cur.length);
                v.moveTo(end.x, v.getY(), end.z, (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), 0);
                specialStuck = 0;
                TrafficStats.inc("manoeuvre_assist");
            }
            Piece done = path.pollFirst();
            if (!done.connector) lastApproach = done.edge;
            if (done.connector) {
                tm.junctions.release(done.node, v.getId());
                TrafficStats.inc("junctions_crossed");
            } else if (!path.isEmpty() && path.peekFirst().connector) {
                Piece con = path.peekFirst();
                RoadNode jn = net.nodes.get(con.node);
                if (jn != null && jn.has(RoadNode.F_LIGHTS) && jn.cfg.getString("mode").isEmpty() || jn != null && jn.has(RoadNode.F_LIGHTS) && jn.cfg.getString("mode").equals("auto")) {
                    int sig = TrafficLights.signal(net, jn, done.edge, con.movement, level.getGameTime())[0];
                    TrafficStats.inc(sig == TrafficLights.RED ? "red_light_violations" : sig == TrafficLights.YELLOW ? "entered_on_yellow" : "entered_on_green");
                }
            }
            if (!done.connector && done.node >= 0) onArrive(v, level, net, tm, done);
            if (state == State.PARK_OUT && stateTicks > 30) {
                state = State.DRIVE;
                spotMode = false;
            }
            if (!done.connector && done.node <= -2) {
                stopTargetGap = 99;
                if (arriveSpecial(v, level, tm, done)) return VehicleInput.PARK;
            }
            if (state != State.DRIVE && state != State.PARK_IN && state != State.TO_PUMP && state != State.PARK_OUT) return VehicleInput.PARK;
            if (path.isEmpty()) {
                state = State.DESPAWN;
                return VehicleInput.PARK;
            }
            cur = path.peekFirst();
            s = cur.project(pos, 0);
        }
        extend(v, net, tm);
        // ---------- lateral control (pure pursuit)
        float vel = v.speed;
        if (state == State.PARK_OUT && spotMode && stateTicks > 70) {
            state = State.DRIVE;
            spotMode = false;
        }
        if (state == State.DRIVE && goal < 0 && !overtaking && ((v.tickCount + v.getId()) % 10 == 0 || pendingSpot != null)) roadsideParking(v, net, tm);
        boolean reverse = state == State.PARK_OUT && stateTicks < (spotMode ? 22 : 50);
        boolean inTurn = path.peekFirst().connector && path.peekFirst().movement != TrafficLights.MOVE_STRAIGHT;
        double look = inTurn ? Math.max(1.4, 1.2 + Math.abs(vel) * 0.22) : Math.max(2.2, 2.0 + Math.abs(vel) * 0.38);
        Vec3 target = pointAhead(look);
        Vec3 f = v.forward();
        Vec3 rear = pos.subtract(f.scale(spec.wheelbase / 2));
        Vec3 d = target.subtract(rear);
        Vec3 left = new Vec3(f.z, 0, -f.x);
        double alpha = Math.atan2(d.dot(left), d.dot(f));
        double ld = Math.max(1.0, Math.hypot(d.x, d.z));
        double steerRad = Math.atan(2 * spec.wheelbase * Math.sin(alpha) / ld);
        float maxSteer = 36f / (1f + Math.abs(vel) / 9f);
        // cross-track correction at the front axle (keeps long vehicles from cutting corners)
        Vec3 front = pos.add(f.scale(spec.wheelbase / 2));
        Piece cp = path.peekFirst();
        double sf = cp.project(front, s);
        Vec3 onPath = cp.at(sf);
        double cte = front.subtract(onPath).dot(left); // + = we are left of the path
        steerRad -= Math.atan(0.6 * cte / Math.max(1.5, Math.abs(vel)));
        float steerIn = (float) Mth.clamp(Math.toDegrees(steerRad) / maxSteer, -1, 1);
        if (reverse) steerIn = 0;
        // ---------- longitudinal control
        float vDesired = desiredSpeed(v, net);
        Constraint c = constraints(v, level, net, tm, vel);
        stopTargetGap = c.why.equals("stop-target") ? c.gap : 99;
        lastGap = c.gap;
        lastLeader = c.leader;
        float b = pers.brakeComfort;
        float aMax = spec.accel * pers.accelUse;
        float v0 = Math.max(0.5f, vDesired);
        float vv = Math.max(0, vel);
        double s0 = c.byRule ? 0.25 : 2.0;
        double sStar = s0 + vv * (c.byRule ? 0.4f : pers.headway()) + vv * (vv - c.speed) / (2 * Math.sqrt(aMax * b));
        double acc = aMax * (1 - Math.pow(vv / v0, 4) - Math.pow(Math.max(0, sStar) / Math.max(0.1, c.gap), 2));
        if (c.gap < 0.6) acc = -spec.brake;
        float fwd;
        if (acc >= 0) fwd = (float) Mth.clamp(acc / Math.max(0.5, spec.accel) + 0.08, 0, 1);
        else fwd = (float) Mth.clamp(acc / spec.brake, -1, 0);
        if (c.gap < 1.5 && vel < 0.4f) fwd = -0.3f; // hold the brake when stopped close to something
        if (reverse) {
            fwd = vel < -1.8f ? 0 : -0.45f;
        }
        // ---------- un-sticking: back up a few metres, then go round
        if (reverseTicks > 0) {
            reverseTicks--;
            fwd = vel < -1.5f ? 0 : -0.5f;
            steerIn = 0;
            if (reverseTicks == 0) {
                tryOvertake(v, net, tm, c, true);
                blockedTicks = 0;
            }
        } else if (state == State.DRIVE && Math.abs(vel) < 0.3f && !c.byRule && c.gap < 4 && unstickCooldown == 0 && wantsUnstick(c)
                && behindFree(v, level, tm)) {
            reverseTicks = 30 + v.getRandom().nextInt(15);
            unstickCooldown = 20 * 20;
            TrafficStats.inc("reversed_out");
        }
        if (unstickCooldown > 0) unstickCooldown--;
        // ---------- blocked / horn / stuck handling
        if (Math.abs(vel) < 0.3f && c.why.equals("blocked")) {
            blockedTicks++;
            if (blockedTicks > 50 && blockedTicks % 20 == 0) tryOvertake(v, net, tm, c, true);
            if (blockedTicks > 20 * 30) v.setFlag(VehicleEntity.F_HAZARD, true);
        } else if (Math.abs(vel) < 0.3f && c.byVehicle && !c.byRule) {
            blockedTicks++;
            int patience = (int) (TCConfig.HORN_PATIENCE.get() * 20 * pers.patience);
            if (blockedTicks > patience && honkCooldown == 0 && TCConfig.HORNS.get()) {
                v.honk();
                TrafficStats.inc("honks");
                honkCooldown = 60 + v.getRandom().nextInt(120);
            }
            if (blockedTicks > 20 * 4 && c.leader instanceof VehicleEntity lv && isStalled(lv)) tryOvertake(v, net, tm, c, true);
        } else if (Math.abs(vel) < 0.3f && c.why.equals("pedestrian")) {
            // somebody standing in the way: a short toot after a few seconds makes them step aside
            if (++blockedTicks > 60 && honkCooldown == 0 && TCConfig.HORNS.get()) {
                v.honk();
                TrafficStats.inc("honks_ped");
                honkCooldown = 80;
            }
        } else if (vel > 1) blockedTicks = 0;
        if (!overtaking && c.leader instanceof VehicleEntity lv && c.gap < 25 && lv.speed < vDesired * 0.55f && vel > 3) tryOvertake(v, net, tm, c, false);
        if (overtaking) checkReturn(v, net, tm);
        // breakdown
        if (state == State.DRIVE && v.getRandom().nextFloat() < TCConfig.BREAKDOWN_CHANCE.get() / 1200f && vel > 4) {
            TrafficStats.inc("breakdowns");
            state = State.BROKEN;
            stateTicks = 0;
        }
        if (spec.backfire > 0 && state == State.DRIVE) fwd = riderHabits(v, vel, vDesired, c, fwd);
        // ---------- lamps
        lights(v, level);
        indicators(v, net);
        boolean hb = fwd <= 0 && Math.abs(vel) < 0.2f;
        last = new VehicleInput(fwd, steerIn, hb);
        reason = c.why;
        return last;
    }

    /** Stuck behind something that is not going to move: a broken-down/parked car, a block, or a gridlock that has lasted long. */
    boolean wantsUnstick(Constraint c) {
        if (c.why.equals("blocked")) return blockedTicks > 20 * 6;
        if (c.leader instanceof VehicleEntity lv) {
            if (isStalled(lv)) return blockedTicks > 20 * 6;
            if (lv.driver != null) {
                String r = lv.driver.reason;
                if (r.contains("light") || r.contains("junction") || r.contains("stop") || r.contains("give way") || r.contains("border")) return false;
            }
            return blockedTicks > 20 * 40;
        }
        return false;
    }

    /** Nothing within a car length or two behind us (vehicles, people, solid blocks). */
    boolean behindFree(VehicleEntity v, ServerLevel level, TrafficManager tm) {
        Vec3 f = v.forward();
        double halfL = v.spec().length / 2;
        List<Entity> near = new ArrayList<>();
        tm.grid.near(v.getX(), v.getZ(), 12, near);
        for (Entity o : near) {
            if (o == v || o.isPassenger()) continue;
            Vec3 to = o.position().subtract(v.position());
            double along = -(to.x * f.x + to.z * f.z), lat = Math.abs(to.x * f.z - to.z * f.x);
            double oh = o instanceof VehicleEntity ov ? ov.spec().length / 2 : 0.4;
            if (along > 0 && along - oh < halfL + 6 && lat < v.spec().width / 2 + 1.0) return false;
        }
        for (double d = halfL + 0.5; d < halfL + 4.5; d += 1) {
            BlockPos bp = BlockPos.containing(v.getX() - f.x * d, v.getY() + 0.6, v.getZ() - f.z * d);
            if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) return false;
        }
        return true;
    }

    /**
     * Motorcycle riders: blip the throttle while waiting at lights / in queues (sometimes two or three blips),
     * now and then while cruising, and occasionally launch hard from a stop and snap the throttle shut (backfire).
     */
    float riderHabits(VehicleEntity v, float vel, float vDesired, Constraint c, float fwd) {
        var r = v.getRandom();
        boolean still = Math.abs(vel) < 0.25f;
        if (still) stillTicks++;
        if (blipQueue > 0 && --blipDelay <= 0) {
            v.revBlip(0.5f + r.nextFloat() * 0.5f, 2 + r.nextInt(4));
            blipQueue--;
            blipDelay = 7 + r.nextInt(8);
        }
        if (still && stillTicks > 30 && (c.why.contains("light") || c.byVehicle || c.why.contains("junction")) && r.nextFloat() < 0.0035f * Math.max(0.3f, 2 - pers.patience)) {
            v.revBlip(0.55f + r.nextFloat() * 0.45f, 3 + r.nextInt(5));
            blipQueue = r.nextFloat() < 0.5f ? 1 + r.nextInt(2) : 0;
            blipDelay = 8 + r.nextInt(6);
            TrafficStats.inc("bike_revs");
        }
        if (!still && vel > 6 && c.gap > 30 && fwd > 0.05f && launchTicks == 0 && r.nextFloat() < 0.0012f) {
            v.revBlip(0.45f + r.nextFloat() * 0.3f, 3);
            TrafficStats.inc("bike_revs");
        }
        // hard launch from a standstill when the road ahead is clear
        if (!still && stillTicks > 20 && fwd > 0.2f && c.gap > 25 && r.nextFloat() < 0.3f * Math.max(0.3f, 2 - pers.patience)) {
            launchTicks = 22 + r.nextInt(22);
            TrafficStats.inc("bike_launches");
        }
        if (!still) stillTicks = 0;
        if (launchTicks > 0) {
            launchTicks--;
            if (c.gap < 18 || vel > vDesired * 1.12f) launchTicks = 0;
            else fwd = 1f;
            if (launchTicks == 0) liftTicks = 5;
        } else if (liftTicks > 0) {
            liftTicks--;
            fwd = Math.min(fwd, 0f);
        }
        return fwd;
    }

    boolean isStalled(VehicleEntity o) {
        return o.has(VehicleEntity.F_HAZARD) || (o.getControllingPassenger() instanceof Player && Math.abs(o.speed) < 0.1f);
    }

    // =================================================================================== path helpers
    Vec3 pointAhead(double dist) {
        double rem = dist;
        boolean first = true;
        for (Piece p : path) {
            double start = first ? s : 0;
            if (start + rem <= p.length) return p.at(start + rem);
            rem -= p.length - start;
            first = false;
        }
        Piece lastP = path.peekLast();
        return lastP.at(lastP.length);
    }

    double remainingOnPath() {
        double tot = 0;
        boolean first = true;
        for (Piece p : path) {
            tot += p.length - (first ? s : 0);
            first = false;
        }
        return tot;
    }

    /** Plans pieces until at least ~70 m (or a stop target) are queued. */
    void extend(VehicleEntity v, RoadNetwork net, TrafficManager tm) {
        int guard = 0;
        while (remainingOnPath() < 70 && guard++ < 6) {
            Piece lastP = path.peekLast();
            if (lastP == null) return;
            if (lastP.connector) {
                // after a connector comes the next edge
                RoadEdge e = net.edges.get(lastP.nextEdge);
                if (e == null) return;
                int lane = laneForConnector(lastP, e);
                path.add(RoutePlanner.edgePiece(net, e, lastP.node, lane, null, null, 0));
                continue;
            }
            RoadEdge in = net.edges.get(lastP.edge);
            int node = lastP.node;
            RoadNode n = net.nodes.get(node);
            if (in == null || n == null) return;
            if (n.type == NodeType.DESPAWN) return;
            if (n.type == NodeType.PARKING || n.type == NodeType.GAS_STATION || n.type == NodeType.BORDER) {
                if (n.type != NodeType.BORDER || goal == node || state != State.DRIVE) return; // handled on arrival
            }
            RoadEdge out = chooseExit(v, net, in, node, tm);
            if (out == null) return;
            int move = RoutePlanner.movement(net, in, node, out);
            int outLane = RoutePlanner.chooseLane(out, node, nextMoveAfter(net, out, node, v), -1, v.getRandom());
            // make sure we are in a lane that can make this movement
            int needLane = RoutePlanner.chooseLane(in, in.other(node), move, lastP.lane, v.getRandom());
            if (needLane != lastP.lane && !lastP.connector) {
                boolean isFirst = lastP == path.peekFirst();
                EdgeGeom gIn = net.geom(in);
                double[] po = new double[2];
                double sNow = gIn.project(isFirst ? v.position() : lastP.at(0), po);
                Piece rebuilt = RoutePlanner.edgePiece(net, in, in.other(node), needLane, sNow, (float) po[0], Math.min(18, Math.max(6, lastP.length * 0.6)));
                if (lastP == path.peekFirst()) {
                    path.pollFirst();
                    path.addFirst(rebuilt);
                    s = 0;
                } else {
                    path.pollLast();
                    path.addLast(rebuilt);
                }
                lastP = rebuilt;
            }
            Piece con = RoutePlanner.connector(net, in, lastP.lane, node, out, outLane);
            if (con != null) path.add(con);
            else {
                int dirOut = out.a == node ? 1 : -1;
                float travelOff = lastP.endOffset * lastP.dir;
                path.add(RoutePlanner.edgePiece(net, out, node, outLane, null, travelOff * dirOut, 8));
            }
        }
    }

    int laneForConnector(Piece con, RoadEdge out) {
        // connectors were built for a specific out lane: find it by matching the end offset
        int best = 0;
        double bd = 1e9;
        for (int i = 0; i < out.lanes.size(); i++) {
            double dd = Math.abs(out.laneOffset(i) - con.endOffset);
            if (dd < bd) {
                bd = dd;
                best = i;
            }
        }
        return best;
    }

    int nextMoveAfter(RoadNetwork net, RoadEdge e, int from, VehicleEntity v) {
        return TrafficLights.MOVE_STRAIGHT;
    }

    RoadEdge chooseExit(VehicleEntity v, RoadNetwork net, RoadEdge in, int node, TrafficManager tm) {
        // goals: fuel, parking
        if (goal < 0 && state == State.DRIVE) pickGoal(v, net, node, tm);
        if (goal >= 0) {
            Integer hop = RoutePlanner.nextHop(net, node, goal);
            if (hop != null) {
                RoadEdge e = net.edgeBetween(node, hop);
                if (e != null && e.allows(node) && RoutePlanner.turnAllowed(net, node, in, e)) return e;
            }
            goal = -1;
            parkingSpace = -1;
        }
        List<RoadEdge> ex = RoutePlanner.exits(net, node, in, v.spec());
        if (ex.isEmpty()) return null;
        if (ex.size() == 1 && ex.get(0) == in && net.roadDegree(node) == 1) {
            state = State.DESPAWN; // dead end: leave the map
            return null;
        }
        return RoutePlanner.pickRandom(ex, net, v.getRandom());
    }

    void pickGoal(VehicleEntity v, RoadNetwork net, int node, TrafficManager tm) {
        float fuelFrac = v.getFuel() / Math.max(1, v.spec().fuelCapacity);
        if (TCConfig.AI_FUEL.get() && fuelFrac < 0.25f) {
            RoadNode best = null;
            double bd = 400 * 400;
            for (RoadNode n : net.nodesOfType(NodeType.GAS_STATION)) {
                double dd = n.pos().distanceToSqr(net.nodes.get(node).pos());
                if (dd < bd && RoutePlanner.nextHop(net, node, n.id) != null) {
                    bd = dd;
                    best = n;
                }
            }
            if (best != null) {
                goal = best.id;
                return;
            }
        }
        // parking: occasionally drive to a nearby parking node with free spaces
        if (v.getRandom().nextFloat() < 0.25f && !v.spec().isHeavy() && v.spec().length < 5.6f) {
            for (RoadNode n : net.nodesOfType(NodeType.PARKING)) {
                if (n.pos().distanceToSqr(net.nodes.get(node).pos()) > 80 * 80) continue;
                int chance = n.cfg.contains("parkChance") ? n.cfg.getInt("parkChance") : (int) (TCConfig.PARK_CHANCE.get() * 100);
                if (v.getRandom().nextInt(100) >= chance) continue;
                int space = tm.parking.claim(net, n, v);
                if (space >= 0 && RoutePlanner.nextHop(net, node, n.id) != null) {
                    goal = n.id;
                    parkingSpace = space;
                    return;
                }
                if (space >= 0) tm.parking.release(n.id, v.getId());
            }
        }
    }

    // =================================================================================== arrivals at special nodes
    /** Gives up reaching a pump / parking space (blocked): free it and rejoin the road at the goal node. */
    void abandon(VehicleEntity v, RoadNetwork net, TrafficManager tm) {
        TrafficStats.inc("manoeuvre_abandoned");
        tm.fuel.forget(v.getId());
        tm.parking.releaseVehicle(v.getId());
        RoadNode n = net.nodes.get(goal);
        goal = -1;
        parkingSpace = -1;
        state = State.DRIVE;
        stateTicks = 0;
        path.clear();
        if (n == null) return;
        List<Vec3> pts = new ArrayList<>();
        Vec3 start = v.position();
        for (int i = 0; i <= 8; i++) pts.add(start.add(n.pos().subtract(start).scale(i / 8.0)));
        path.add(new Piece(pts, -1, 1, 0, n.id, false, 3f));
        s = 0;
        leaveNode(v, net, n, null);
    }

    /** Reached a parking space (-2) or a pump (-3). True when the vehicle now stands still in its new state. */
    boolean arriveSpecial(VehicleEntity v, ServerLevel level, TrafficManager tm, Piece done) {
        if (done.node == -2 && state == State.PARK_IN && spotMode) {
            // settle neatly into the kerbside spot (parallel to the kerb, centred)
            RoadNode sn = RoadData.get(level).nodes.get(goal);
            com.trafficcraft.road.RoadsideSpot sp = sn == null ? null : com.trafficcraft.road.RoadsideSpot.of(RoadData.get(level), sn);
            if (sp != null && v.position().distanceTo(sp.center()) < 3.5) {
                float yaw = (float) Math.toDegrees(Math.atan2(-sp.facing().x, sp.facing().z));
                v.moveTo(sp.center().x, v.getY(), sp.center().z, yaw, 0);
            }
        }
        if (done.node == -2 && state == State.PARK_IN) {
            TrafficStats.inc("parked");
            state = State.PARKED;
            stateTicks = 0;
            return true;
        }
        if (done.node == -3 && state == State.TO_PUMP) {
            state = State.FUELING;
            stateTicks = 0;
            tm.fuel.startService(level, v);
            TrafficStats.inc("refuel_started");
            return true;
        }
        return false;
    }

    void onArrive(VehicleEntity v, ServerLevel level, RoadNetwork net, TrafficManager tm, Piece done) {
        RoadNode n = net.nodes.get(done.node);
        if (n == null) return;
        if (n.type == NodeType.PARKING && goal == n.id && parkingSpace >= 0) {
            List<ParkingLayout.Space> spaces = ParkingLayout.spaces(net, n);
            if (parkingSpace >= spaces.size()) return;
            ParkingLayout.Space sp = spaces.get(parkingSpace);
            path.clear();
            List<Vec3> pts = new ArrayList<>();
            Vec3 start = v.position();
            Vec3 a = sp.aisle();
            Vec3 c = sp.center().subtract(sp.facing().scale(0.2));
            // straight to the aisle point, then a smooth curve into the bay
            for (int i = 0; i <= 6; i++) pts.add(start.add(a.subtract(start).scale(i / 6.0)));
            Vec3 t0 = a.subtract(start).normalize();
            double h = a.distanceTo(c) * 0.6;
            Vec3 p1 = a.add(t0.scale(h)), p2 = c.subtract(sp.facing().scale(h));
            for (int i = 1; i <= 12; i++) {
                double t = i / 12.0, u = 1 - t;
                pts.add(a.scale(u * u * u).add(p1.scale(3 * u * u * t)).add(p2.scale(3 * u * t * t)).add(c.scale(t * t * t)));
            }
            Piece in = new Piece(pts, -1, 1, 0, -2, false, 2.6f);
            in.stopS = in.length - 0.2;
            path.add(in);
            s = 0;
            state = State.PARK_IN;
            stateTicks = 0;
            return;
        }
        if (n.type == NodeType.GAS_STATION && goal == n.id) {
            BlockPos pump = tm.fuel.claimPump(level, n, v);
            if (pump == null) {
                goal = -1;
                leaveNode(v, net, n, null);
                return;
            }
            Vec3 stop = tm.fuel.stopPoint(level, pump);
            Vec3 along = tm.fuel.pumpDir(level, pump);
            Vec3 pre = stop.subtract(along.scale(6));
            path.clear();
            List<Vec3> pts = new ArrayList<>();
            Vec3 start = v.position();
            for (int i = 0; i <= 8; i++) pts.add(start.add(pre.subtract(start).scale(i / 8.0)));
            for (int i = 1; i <= 6; i++) pts.add(pre.add(stop.subtract(pre).scale(i / 6.0)));
            Piece in = new Piece(pts, -1, 1, 0, -3, false, 3f);
            in.stopS = in.length - 0.2;
            path.add(in);
            s = 0;
            state = State.TO_PUMP;
            stateTicks = 0;
        }
        if (done.node == -2 && state == State.PARK_IN) {
            TrafficStats.inc("parked");
            state = State.PARKED;
            stateTicks = 0;
        }
        if (done.node == -3 && state == State.TO_PUMP) {
            state = State.FUELING;
            stateTicks = 0;
            tm.fuel.startService(level, v);
            TrafficStats.inc("refuel_started");
        }
    }

    VehicleInput parked(VehicleEntity v, ServerLevel level, RoadNetwork net, TrafficManager tm) {
        v.setFlag(VehicleEntity.F_ENGINE, false);
        v.setFlag(VehicleEntity.F_LOW | VehicleEntity.F_IND_L | VehicleEntity.F_IND_R, false);
        if (v.getMode() != VehicleEntity.MODE_AI) return VehicleInput.PARK;
        RoadNode n = net.nodes.get(goal);
        if (spotMode) return leaveSpot(v, net, tm, n);
        int min = n != null && n.cfg.contains("stayMin") ? n.cfg.getInt("stayMin") : 30, max = n != null && n.cfg.contains("stayMax") ? n.cfg.getInt("stayMax") : 180;
        if (stateTicks > 20 * (min + (spawner * 7919L + v.getId()) % Math.max(1, max - min + 1))) {
            TrafficStats.inc("left_parking");
            // leave: reverse out of the bay then drive back to the node and onward
            v.setFlag(VehicleEntity.F_ENGINE, true);
            List<ParkingLayout.Space> spaces = n != null ? ParkingLayout.spaces(net, n) : List.of();
            tm.parking.release(goal, v.getId());
            if (n == null || parkingSpace < 0 || parkingSpace >= spaces.size()) {
                state = State.DESPAWN;
                return VehicleInput.PARK;
            }
            ParkingLayout.Space sp = spaces.get(parkingSpace);
            path.clear();
            List<Vec3> pts = new ArrayList<>();
            Vec3 back = sp.aisle().add(sp.facing().scale(0.3));
            Vec3 dirOut = n.pos().subtract(sp.aisle()).normalize();
            Vec3 c0 = v.position().add(v.forward().scale(-(sp.length() / 2 + 1.2)));
            // after reversing we drive forward along the aisle back to the node
            pts.add(c0);
            Vec3 p1 = c0.add(dirOut.scale(2));
            for (int i = 1; i <= 10; i++) {
                double t = i / 10.0;
                pts.add(p1.add(n.pos().subtract(p1).scale(t)));
            }
            Piece out = new Piece(pts, -1, 1, 0, n.id, false, 3f);
            path.add(out);
            s = 0;
            state = State.PARK_OUT;
            stateTicks = 0;
            int keep = goal;
            goal = -1;
            parkingSpace = -1;
            leaveNode(v, net, net.nodes.get(keep), null);
        }
        return VehicleInput.PARK;
    }

    // =================================================================================== kerbside spots
    /** Passing a free roadside spot on our side: maybe pull straight in. */
    void roadsideParking(VehicleEntity v, RoadNetwork net, TrafficManager tm) {
        Piece cur = path.peekFirst();
        if (cur == null || v.spec().isHeavy() || v.spec().length > 5.6f) return;
        // 1) a spot chosen earlier on the edge we have just entered
        if (pendingSpot != null && !cur.connector && cur.edge == pendingSpot.edge()) {
            com.trafficcraft.road.RoadsideSpot sp = pendingSpot;
            pendingSpot = null;
            RoadEdge e = net.edges.get(cur.edge);
            EdgeGeom g = e == null ? null : net.geom(e);
            if (g != null && (sp.sv() - g.project(v.position(), null)) * sp.dir() > 2.5) {
                enterSpot(v, e, g, cur, sp);
                return;
            }
            tm.parking.releaseSpot(sp.node());
        }
        if (pendingSpot != null) return;
        // 2) look for spots on the current edge and the next edge of the route
        double dist = 0;
        boolean first = true;
        for (Piece p : path) {
            double from = first ? s : 0;
            if (!p.connector && p.edge >= 0) {
                List<com.trafficcraft.road.RoadsideSpot> spots = com.trafficcraft.road.RoadsideSpot.onEdge(net, p.edge);
                RoadEdge e = net.edges.get(p.edge);
                EdgeGeom g = e == null ? null : net.geom(e);
                if (g != null && !spots.isEmpty()) {
                    double psv = g.project(first ? v.position() : p.at(0), null);
                    for (com.trafficcraft.road.RoadsideSpot sp : spots) {
                        if (sp.dir() != p.dir || consideredSpots.contains(sp.node())) continue;
                        double ahead = dist + (sp.sv() - psv) * sp.dir();
                        if (ahead < 6 || ahead > 45) continue;
                        consideredSpots.add(sp.node());
                        RoadNode n = net.nodes.get(sp.node());
                        int chance = n.cfg.contains("parkChance") ? n.cfg.getInt("parkChance") : 30;
                        if (v.getRandom().nextInt(100) >= chance || !tm.parking.claimSpot(sp, v)) continue;
                        if (first) enterSpot(v, e, g, p, sp);
                        else pendingSpot = sp;
                        return;
                    }
                }
                if (!first) return; // only the current and the next edge
            }
            dist += p.length - from;
            first = false;
            if (dist > 45) return;
        }
    }

    /** Ease over from our lane to the kerb and stop with the car centred on the spot. */
    void enterSpot(VehicleEntity v, RoadEdge e, EdgeGeom g, Piece cur, com.trafficcraft.road.RoadsideSpot sp) {
        double vsv = g.project(v.position(), null);
        double halfL = v.spec().length / 2;
        double laneOff = e.laneOffset(Math.max(0, Math.min(e.lanes.size() - 1, cur.lane)));
        double endSv = sp.sv() + sp.dir() * (halfL - 0.3);
        double total = Math.abs(endSv - vsv), blend = Math.max(2, Math.min(11, total - 1));
        List<Vec3> pts = new ArrayList<>();
        int steps = Math.max(2, (int) Math.ceil(total));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps, d = t * total;
            double k = Math.max(0, Math.min(1, (d - (total - blend)) / blend));
            k = k * k * (3 - 2 * k);
            pts.add(g.point(vsv + sp.dir() * d, laneOff + (sp.offset() - laneOff) * k));
        }
        Piece in = new Piece(pts, -1, 1, 0, -2, false, 4f);
        in.stopS = in.length - 0.2;
        path.clear();
        path.add(in);
        s = 0;
        goal = sp.node();
        spotMode = true;
        state = State.PARK_IN;
        stateTicks = 0;
        TrafficStats.inc("roadside_park");
    }

    /** Leave a kerbside spot: back up a little, pull out into the lane and continue along the road. */
    VehicleInput leaveSpot(VehicleEntity v, RoadNetwork net, TrafficManager tm, RoadNode n) {
        int min = n != null && n.cfg.contains("stayMin") ? n.cfg.getInt("stayMin") : 15, max = n != null && n.cfg.contains("stayMax") ? n.cfg.getInt("stayMax") : 60;
        if (stateTicks <= 20 * (min + (v.getId() * 31L) % Math.max(1, max - min + 1))) return VehicleInput.PARK;
        com.trafficcraft.road.RoadsideSpot sp = n == null ? null : com.trafficcraft.road.RoadsideSpot.of(net, n);
        tm.parking.releaseSpot(goal);
        goal = -1;
        if (sp == null) {
            state = State.DESPAWN;
            spotMode = false;
            return VehicleInput.PARK;
        }
        RoadEdge e = net.edges.get(sp.edge());
        EdgeGeom g = net.geom(e);
        int from = sp.dir() > 0 ? e.a : e.b;
        List<Integer> lanes = e.lanesFrom(from);
        if (lanes.isEmpty()) {
            state = State.DESPAWN;
            return VehicleInput.PARK;
        }
        double vsv = g.project(v.position(), null);
        double start = vsv - sp.dir() * 1.5; // where we are after backing up
        float offStart = (float) sp.offset();
        path.clear();
        path.add(RoutePlanner.edgePiece(net, e, from, lanes.get(0), start, offStart, 12));
        s = 0;
        v.setFlag(VehicleEntity.F_ENGINE, true);
        state = State.PARK_OUT;
        stateTicks = 0;
        TrafficStats.inc("left_roadside");
        return VehicleInput.PARK;
    }

    VehicleInput fueling(VehicleEntity v, ServerLevel level, RoadNetwork net, TrafficManager tm) {
        v.setFlag(VehicleEntity.F_ENGINE, false);
        boolean done = tm.fuel.serviceDone(level, v) || stateTicks > 20 * 75;
        if (done) {
            tm.fuel.finishService(level, v, net.nodes.get(goal));
            TrafficStats.inc("refuel_done");
            v.setFlag(VehicleEntity.F_ENGINE, true);
            RoadNode n = net.nodes.get(goal);
            goal = -1;
            state = State.DRIVE;
            stateTicks = 0;
            path.clear();
            if (n != null) {
                List<Vec3> pts = new ArrayList<>();
                Vec3 start = v.position();
                Vec3 ahead = start.add(v.forward().scale(5));
                pts.add(start);
                pts.add(ahead);
                for (int i = 1; i <= 8; i++) pts.add(ahead.add(n.pos().subtract(ahead).scale(i / 8.0)));
                path.add(new Piece(pts, -1, 1, 0, n.id, false, 3f));
                s = 0;
                leaveNode(v, net, n, null);
            }
        }
        return VehicleInput.PARK;
    }

    /** Append the first edge leaving a special node. */
    void leaveNode(VehicleEntity v, RoadNetwork net, RoadNode n, RoadEdge except) {
        if (n == null) return;
        List<RoadEdge> ex = new ArrayList<>();
        for (RoadEdge e : net.roadEdgesOf(n.id)) if (e.allows(n.id) && e != except) ex.add(e);
        if (ex.isEmpty()) return;
        RoadEdge e = ex.get(v.getRandom().nextInt(ex.size()));
        int lane = e.lanesFrom(n.id).isEmpty() ? 0 : e.lanesFrom(n.id).get(0);
        path.add(RoutePlanner.edgePiece(net, e, n.id, lane, null, null, 0));
    }

    // =================================================================================== speed
    float desiredSpeed(VehicleEntity v, RoadNetwork net) {
        VehicleSpec spec = v.spec();
        float base = Math.min(maxSpeed, spec.maxSpeedKmh / 3.6f * 0.95f);
        Piece cur = path.peekFirst();
        float lim = cur.speedLimit * pers.speedFactor() * cruise;
        float vd = Math.min(base, lim);
        // anticipate slower pieces / curves ahead within braking distance
        double dist = cur.length - s;
        boolean first = true;
        for (Piece p : path) {
            if (first) {
                first = false;
                continue;
            }
            float pl = p.speedLimit * (p.connector ? 1f : pers.speedFactor() * cruise);
            float allowed = (float) Math.sqrt(pl * pl + 2 * pers.brakeComfort * Math.max(0, dist));
            vd = Math.min(vd, allowed);
            dist += p.length;
            if (dist > 120) break;
        }
        // curvature of the current piece
        Vec3 t0 = cur.tangent(s), t1 = cur.tangent(s + 6);
        double ang = Math.acos(Mth.clamp(t0.dot(t1), -1, 1));
        if (ang > 0.05) {
            double radius = 6 / ang;
            vd = (float) Math.min(vd, Math.sqrt(3.8 * radius) + 1);
        }
        if (state == State.PARK_IN && spotMode && !path.isEmpty()) vd = Math.min(vd, (float) Math.max(2.6, (path.peekFirst().length - s) * 0.45));
        else if (state == State.PARK_IN || state == State.TO_PUMP) vd = Math.min(vd, 2.6f);
        if (state == State.PARK_OUT) vd = Math.min(vd, 3f);
        return vd;
    }

    // =================================================================================== constraints
    static final class Constraint {
        double gap = 999;
        float speed = 30;
        boolean byVehicle, byRule;
        Entity leader;
        String why = "free";

        void offer(double g, float spd, String w, Entity e, boolean rule) {
            if (g < gap) {
                gap = g;
                speed = spd;
                why = w;
                leader = e;
                byRule = rule;
                byVehicle = e instanceof VehicleEntity;
            }
        }
    }

    Constraint constraints(VehicleEntity v, ServerLevel level, RoadNetwork net, TrafficManager tm, float vel) {
        Constraint c = new Constraint();
        VehicleSpec spec = v.spec();
        double halfL = spec.length / 2, halfW = spec.width / 2;
        double D = Math.max(28, Math.abs(vel) * 3.6 + 12);
        // ---------- samples along the path ahead
        List<double[]> samples = new ArrayList<>(); // x, z, sAhead, tx, tz
        double acc = 0;
        boolean first = true;
        for (Piece p : path) {
            double start = first ? s : 0;
            for (double q = start; q <= p.length && acc + (q - start) <= D; q += 1.0) {
                Vec3 pt = p.at(q), t = p.tangent(q);
                samples.add(new double[]{pt.x, pt.z, acc + q - start, t.x, t.z, pt.y});
            }
            acc += p.length - start;
            first = false;
            if (acc > D) break;
        }
        // ---------- other road users
        List<Entity> near = new ArrayList<>();
        tm.grid.near(v.getX(), v.getZ(), D + 8, near);
        for (Entity o : near) {
            if (o == v || o.isPassenger() || !o.isAlive()) continue;
            if (Math.abs(o.getY() - v.getY()) > 2.5) continue;
            boolean isVeh = o instanceof VehicleEntity;
            if (!isVeh && !(o instanceof LivingEntity)) continue;
            if (!isVeh && o instanceof Player pl && (pl.isCreative() && pl.getAbilities().flying)) continue;
            double ox = o.getX() - v.getX(), oz = o.getZ() - v.getZ();
            if (ox * ox + oz * oz > (D + 8) * (D + 8)) continue;
            // ignore things behind us
            Vec3 f = v.forward();
            if (ox * f.x + oz * f.z < -halfL) continue;
            double hl = isVeh ? ((VehicleEntity) o).spec().length / 2 : 0.35, hw = isVeh ? ((VehicleEntity) o).spec().width / 2 : 0.35;
            float oyaw = o.getYRot();
            double r = Math.toRadians(oyaw);
            double ofx = -Math.sin(r), ofz = Math.cos(r);
            double margin = isVeh ? halfW * 0.85 : halfW + 0.6;
            // manoeuvring in a car park: cars standing in their own bays only matter if really in the way
            if (isVeh && (state == State.PARK_IN || state == State.PARK_OUT) && ((VehicleEntity) o).driver != null
                    && ((VehicleEntity) o).driver.state == State.PARKED) margin = halfW * 0.3;
            for (double[] sm : samples) {
                double dx = sm[0] - o.getX(), dz = sm[1] - o.getZ();
                double along = dx * ofx + dz * ofz, lat = dx * ofz - dz * ofx;
                double ex = Math.max(0, Math.abs(along) - hl), ez = Math.max(0, Math.abs(lat) - hw);
                if (ex * ex + ez * ez < margin * margin) {
                    double g = sm[2] - halfL - (isVeh ? 0.3 : 1.8);
                    float ospd = isVeh ? (float) (((VehicleEntity) o).visSpeed() * (ofx * sm[3] + ofz * sm[4])) : 0f;
                    if (!isVeh) ospd = 0;
                    c.offer(Math.max(0, g), Math.max(0, ospd), isVeh ? "vehicle" : "pedestrian", o, false);
                    break;
                }
            }
        }
        // ---------- solid blocks on the path (road blocked)
        for (double[] sm : samples) {
            if (sm[2] > 16) break;
            BlockPos bp = BlockPos.containing(sm[0], sm[5] + 0.6, sm[1]);
            BlockState st = level.getBlockState(bp);
            if (!st.isAir() && !st.getCollisionShape(level, bp).isEmpty()) {
                c.offer(Math.max(0, sm[2] - halfL - 0.5), 0, "blocked", null, false);
                break;
            }
        }
        // ---------- junction rules on the path ahead
        acc = 0;
        first = true;
        Piece prev = null;
        for (Piece p : path) {
            double start = first ? s : 0;
            if (prev != null && !prev.connector && prev.stopS >= 0 && p.connector) {
                double distToStop = acc - (prev.length - prev.stopS) - halfL + 0.2;
                junctionRule(v, level, net, tm, prev, p, distToStop, vel, c);
            }
            if (!p.connector && p.node >= 0 && p.stopS < 0) {
                RoadNode n = net.nodes.get(p.node);
                if (n != null && n.type == NodeType.BORDER && state == State.DRIVE) {
                    double distToStop = acc + (p.length - start) - halfL - 2.0;
                    if (!tm.border.cleared(level, n, v, distToStop)) c.offer(Math.max(0, distToStop), 0, "border", null, true);
                }
            }
            if (p.stopS >= 0 && (p.node == -2 || p.node == -3)) c.offer(Math.max(0, acc + (p.stopS - start) - 0.2), 0, "stop-target", null, true);
            acc += p.length - start;
            first = false;
            prev = p;
            if (acc > D + 20) break;
        }
        return c;
    }

    void junctionRule(VehicleEntity v, ServerLevel level, RoadNetwork net, TrafficManager tm, Piece approach, Piece con, double dist, float vel, Constraint c) {
        RoadNode n = net.nodes.get(con.node);
        if (n == null) return;
        if (tm.junctions.holds(n.id, v.getId())) return;
        long now = level.getGameTime();
        boolean mayGo = true;
        String why = "junction";
        if (n.has(RoadNode.F_LIGHTS) && !n.cfg.getString("mode").equals("off") && !n.cfg.getString("mode").equals("flash")) {
            int[] sig = TrafficLights.signal(net, n, approach.edge, con.movement, now);
            if (sig[0] == TrafficLights.RED || sig[0] == TrafficLights.RED_YELLOW) {
                mayGo = false;
                why = "red light";
            } else if (sig[0] == TrafficLights.YELLOW) {
                double stopDist = vel * vel / (2 * pers.brakeComfort * 1.3);
                if (stopDist < dist - 1 || dist > 4 || pers == Personality.PATIENT) {
                    mayGo = false;
                    why = "yellow light";
                }
            }
            if (mayGo && (con.movement == TrafficLights.MOVE_LEFT || con.movement == TrafficLights.MOVE_UTURN) && !n.cfg.getBoolean("leftPhase")) {
                if (oncoming(v, net, tm, n, approach)) {
                    mayGo = false;
                    why = "yield (left turn)";
                }
            }
        } else {
            // unsignalised: stop signs, give-way signs, roundabout entries
            boolean minor = isMinor(net, n, approach.edge);
            boolean stopSign = n.has(RoadNode.F_STOP) || contains(n.cfg.getIntArray("stopEdges"), approach.edge);
            if (stopSign && !stopSignDone) {
                if (dist < 1.5 && Math.abs(vel) < 0.3f) stopSignTicks++;
                if (stopSignTicks > 25) stopSignDone = true;
                else {
                    mayGo = false;
                    why = "stop sign";
                }
            }
            if (mayGo && (minor || stopSign) && priorityTraffic(v, net, tm, n, approach.edge)) {
                mayGo = false;
                why = "give way";
            }
            if (mayGo && yieldsByPriority(v, net, tm, n, approach.edge)) {
                mayGo = false;
                why = "give way (priority)";
            }
        }
        // only try to reserve when close to the line
        if (mayGo && dist < Math.max(7, Math.abs(vel) * 1.6 + 5)) {
            if (!exitClear(v, net, tm, con)) {
                mayGo = false;
                why = "exit blocked";
            } else {
                waitTicks++;
                boolean force = waitTicks > 20 * 25;
                if (!tm.junctions.request(n.id, v.getId(), con, now, force)) {
                    mayGo = false;
                    why = "junction busy";
                } else {
                    waitTicks = 0;
                    stopSignDone = false;
                    stopSignTicks = 0;
                }
            }
        }
        if (!mayGo) c.offer(Math.max(0, dist), 0, why, null, true);
    }

    static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    boolean isMinor(RoadNetwork net, RoadNode n, int approachEdge) {
        if (n.has(RoadNode.F_YIELD) || contains(n.cfg.getIntArray("yieldEdges"), approachEdge)) return true;
        if (n.has(RoadNode.F_RING)) {
            RoadEdge e = net.edges.get(approachEdge);
            RoadNode o = e == null ? null : net.nodes.get(e.other(n.id));
            return o == null || !o.has(RoadNode.F_RING);
        }
        return false;
    }

    /** Vehicles approaching n on non-minor edges (or the ring) close enough that we must wait. */
    boolean priorityTraffic(VehicleEntity v, RoadNetwork net, TrafficManager tm, RoadNode n, int myEdge) {
        List<Entity> near = new ArrayList<>();
        tm.grid.near(n.x, n.z, 26, near);
        for (Entity o : near) {
            if (!(o instanceof VehicleEntity ov) || ov == v) continue;
            double d = ov.position().distanceTo(n.pos());
            if (d > 24 || d < 1) continue;
            Vec3 toNode = n.pos().subtract(ov.position()).normalize();
            double closing = ov.visSpeed() * (ov.forward().x * toNode.x + ov.forward().z * toNode.z);
            if (closing < 0.8) continue;
            if (d / Math.max(0.5, closing) > 4.5) continue;
            TrafficDriver od = ov.driver;
            if (od != null && od.currentApproach() == myEdge) continue;
            if (od != null && od.currentApproach() >= 0 && isMinor(net, n, od.currentApproach())) continue;
            return true;
        }
        return false;
    }

    /** Priority set with the Road Planner: traffic from road A goes first, road B waits while A traffic is near or in the junction. */
    boolean yieldsByPriority(VehicleEntity v, RoadNetwork net, TrafficManager tm, RoadNode n, int myEdge) {
        int[] pr = n.cfg.getIntArray("prio");
        if (pr.length == 0) return false;
        java.util.Set<Integer> major = new java.util.HashSet<>();
        for (int k : pr) if ((k & 0xFFFF) == myEdge) major.add(k >>> 16);
        if (major.isEmpty()) return false;
        float jr = net.junctionRadius(n.id);
        List<Entity> near = new ArrayList<>();
        tm.grid.near(n.x, n.z, 30, near);
        for (Entity o : near) {
            if (!(o instanceof VehicleEntity ov) || ov == v) continue;
            double d = ov.position().distanceTo(n.pos());
            if (d > 28) continue;
            int app = ov.driver != null ? ov.driver.currentApproach() : approachByPosition(net, n, ov);
            boolean inJunction = d < jr + 0.5 && ov.driver != null && tm.junctions.holds(n.id, ov.getId());
            if (inJunction && ov.driver.lastApproach >= 0 && major.contains(ov.driver.lastApproach)) return true;
            if (!major.contains(app)) continue;
            if (d < jr + 1) return true;
            Vec3 toNode = n.pos().subtract(ov.position()).normalize();
            double closing = ov.visSpeed() * (ov.forward().x * toNode.x + ov.forward().z * toNode.z);
            if (closing > 0.5 && d / closing < 5.5) return true;
            if (Math.abs(ov.visSpeed()) < 0.5f && d < jr + 9) return true; // queued right at the line
        }
        return false;
    }

    /** For vehicles without an AI driver: the junction road they are on (or -1). */
    static int approachByPosition(RoadNetwork net, RoadNode n, VehicleEntity ov) {
        double[] out = new double[2];
        for (RoadEdge e : net.roadEdgesOf(n.id)) {
            EdgeGeom g = net.geom(e);
            if (g == null) continue;
            g.project(ov.position(), out);
            if (out[1] < e.width / 2 + 0.5) return e.id;
        }
        return -1;
    }

    int currentApproach() {
        Piece p = path.peekFirst();
        return p == null || p.connector ? -1 : p.edge;
    }

    /** Oncoming traffic for a permissive left turn. */
    boolean oncoming(VehicleEntity v, RoadNetwork net, TrafficManager tm, RoadNode n, Piece approach) {
        Vec3 myDir = TrafficLights.arrivalDir(net, net.edges.get(approach.edge), n.id);
        List<Entity> near = new ArrayList<>();
        tm.grid.near(n.x, n.z, 34, near);
        for (Entity o : near) {
            if (!(o instanceof VehicleEntity ov) || ov == v) continue;
            double d = ov.position().distanceTo(n.pos());
            if (d > 32) continue;
            Vec3 of = ov.forward();
            if (of.x * myDir.x + of.z * myDir.z > -0.7) continue; // not opposite direction
            Vec3 toNode = n.pos().subtract(ov.position());
            if (toNode.x * of.x + toNode.z * of.z < -2) continue; // already past
            if (ov.visSpeed() < 0.8 && d > 10) continue;
            return true;
        }
        return false;
    }

    /** Is there room after the junction on the exit lane? */
    boolean exitClear(VehicleEntity v, RoadNetwork net, TrafficManager tm, Piece con) {
        Vec3 exit = con.at(con.length);
        double need = v.spec().length + 3;
        List<Entity> near = new ArrayList<>();
        tm.grid.near(exit.x, exit.z, need + 6, near);
        Vec3 dir = con.tangent(con.length);
        for (Entity o : near) {
            if (!(o instanceof VehicleEntity ov) || ov == v) continue;
            Vec3 d = ov.position().subtract(exit);
            double along = d.x * dir.x + d.z * dir.z, lat = Math.abs(d.x * dir.z - d.z * dir.x);
            if (lat > 1.6 || along < -1 || along > need + ov.spec().length / 2) continue;
            if (Math.abs(ov.visSpeed()) < 2.0f) return false;
        }
        return true;
    }

    // =================================================================================== overtaking
    void tryOvertake(VehicleEntity v, RoadNetwork net, TrafficManager tm, Constraint c, boolean stalled) {
        Piece cur = path.peekFirst();
        if (cur == null || cur.connector || cur.edge < 0) return;
        RoadEdge e = net.edges.get(cur.edge);
        if (e == null) return;
        if (v.getRandom().nextFloat() > pers.overtake * TCConfig.OVERTAKE_CHANCE.get() * 2 && !stalled) return;
        double remaining = cur.length - s;
        if (remaining < (stalled ? 10 : 40)) return;
        int from = e.other(cur.node);
        // 1) same-direction neighbour lane
        List<Integer> same = e.lanesFrom(from);
        int idx = same.indexOf(cur.lane);
        if (same.size() > 1 && idx >= 0) {
            int target = idx + 1 < same.size() ? same.get(idx + 1) : same.get(idx - 1);
            if (laneFree(v, net, tm, e, target, 18)) {
                changeLane(v, net, e, from, target, idx + 1 < same.size() ? VehicleEntity.F_IND_L : VehicleEntity.F_IND_R);
                return;
            }
        }
        // 2) opposite lane on two-way roads where overtaking is allowed
        if (!e.overtake || !e.twoWay()) return;
        List<Integer> opp = e.lanesFrom(cur.node);
        if (opp.isEmpty()) return;
        int oppLane = opp.get(opp.size() - 1);
        double need = c.gap + 25 + v.spec().length * 2;
        if (remaining < need + 20) return;
        if (oncomingOnEdge(v, net, tm, e, cur, need * 2.2)) return;
        changeLane(v, net, e, from, oppLane, VehicleEntity.F_IND_L);
        overtaking = true;
        TrafficStats.inc("overtakes");
    }

    void checkReturn(VehicleEntity v, RoadNetwork net, TrafficManager tm) {
        Piece cur = path.peekFirst();
        if (cur == null || cur.connector) {
            overtaking = false;
            return;
        }
        RoadEdge e = net.edges.get(cur.edge);
        if (e == null) return;
        int from = e.other(cur.node);
        List<Integer> own = e.lanesFrom(from);
        if (own.isEmpty()) return;
        int home = own.get(0);
        boolean danger = oncomingOnEdge(v, net, tm, e, cur, 45);
        if (laneFree(v, net, tm, e, home, 10) || danger || cur.length - s < 25) {
            changeLane(v, net, e, from, home, VehicleEntity.F_IND_R);
            overtaking = false;
        }
    }

    void changeLane(VehicleEntity v, RoadNetwork net, RoadEdge e, int from, int lane, int indicator) {
        Piece cur = path.peekFirst();
        EdgeGeom g = net.geom(e);
        double[] out = new double[2];
        double sv = g.project(v.position(), out);
        float curOff = (float) out[0];
        Piece np = RoutePlanner.edgePiece(net, e, from, lane, sv, curOff, Math.max(10, Math.abs(v.speed) * 1.6));
        // replace the current piece and everything planned after it
        path.clear();
        path.add(np);
        s = 0;
        v.setFlag(VehicleEntity.F_IND_L | VehicleEntity.F_IND_R, false);
        v.setFlag(indicator, true);
        indicatorHold = 50;
    }

    int indicatorHold;

    boolean laneFree(VehicleEntity v, RoadNetwork net, TrafficManager tm, RoadEdge e, int lane, double range) {
        EdgeGeom g = net.geom(e);
        double[] out = new double[2];
        double sv = g.project(v.position(), out);
        List<Entity> near = new ArrayList<>();
        tm.grid.near(v.getX(), v.getZ(), range + 8, near);
        float off = e.laneOffset(lane);
        for (Entity o : near) {
            if (!(o instanceof VehicleEntity ov) || ov == v) continue;
            double so = g.project(ov.position(), out);
            if (Math.abs(out[0] - off) > e.laneWidth() * 0.7) continue;
            if (Math.abs(so - sv) < range + ov.spec().length / 2) return false;
        }
        return true;
    }

    boolean oncomingOnEdge(VehicleEntity v, RoadNetwork net, TrafficManager tm, RoadEdge e, Piece cur, double range) {
        EdgeGeom g = net.geom(e);
        double[] out = new double[2];
        double sv = g.project(v.position(), out);
        int myDir = cur.dir;
        List<Entity> near = new ArrayList<>();
        tm.grid.near(v.getX(), v.getZ(), range + 10, near);
        for (Entity o : near) {
            if (!(o instanceof VehicleEntity ov) || ov == v) continue;
            double so = g.project(ov.position(), out);
            if (out[1] > e.width) continue;
            double ahead = (so - sv) * myDir;
            if (ahead < 0 || ahead > range) continue;
            Vec3 t = g.tangent(so).scale(myDir);
            if (ov.forward().dot(t) < -0.3) return true;
        }
        return false;
    }

    // =================================================================================== lamps
    void lights(VehicleEntity v, ServerLevel level) {
        if (level.getGameTime() % 40 != v.getId() % 40) return;
        boolean dark = level.getSkyDarken() >= 4 || (level.isRaining() && level.getSkyDarken() >= 2);
        v.setFlag(VehicleEntity.F_LOW, dark);
    }

    void indicators(VehicleEntity v, RoadNetwork net) {
        if (indicatorHold > 0) {
            indicatorHold--;
            return;
        }
        if (state == State.BROKEN) return;
        int ind = 0;
        double acc = 0;
        boolean first = true;
        for (Piece p : path) {
            double start = first ? s : 0;
            if (p.connector && acc < 38) {
                if (p.movement == TrafficLights.MOVE_LEFT || p.movement == TrafficLights.MOVE_UTURN) ind = VehicleEntity.F_IND_L;
                else if (p.movement == TrafficLights.MOVE_RIGHT) ind = VehicleEntity.F_IND_R;
                break;
            }
            acc += p.length - start;
            first = false;
            if (acc > 38) break;
        }
        if (state == State.PARK_IN || state == State.TO_PUMP) ind = VehicleEntity.F_IND_R;
        v.setFlag(VehicleEntity.F_IND_L, ind == VehicleEntity.F_IND_L);
        v.setFlag(VehicleEntity.F_IND_R, ind == VehicleEntity.F_IND_R);
        if (state == State.DRIVE && !v.has(VehicleEntity.F_HAZARD)) return;
        if (state == State.DRIVE && blockedTicks < 20 * 40) v.setFlag(VehicleEntity.F_HAZARD, false);
    }

    public void onRemoved(TrafficManager tm) {
        if (goal >= 0) tm.parking.release(goal, -1);
    }
}
