package com.trafficcraft.dev;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.ai.TrafficDriver;
import com.trafficcraft.ai.TrafficManager;
import com.trafficcraft.ai.TrafficStats;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.world.DemoTown;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Headless traffic simulation test (enabled with -Dtrafficcraft.servertest=true): builds the demo town and records metrics. */
@EventBusSubscriber(modid = TrafficCraft.MODID)
public final class ServerTest {
    static final boolean ENABLED = Boolean.getBoolean("trafficcraft.servertest");
    static final int DURATION = Integer.getInteger("trafficcraft.servertest.ticks", 20 * 300);
    static int t = -1;
    static final List<String> log = new ArrayList<>();
    static double speedSum;
    static final Map<Integer, Integer> boulevardLane = new java.util.HashMap<>();
    static long speedN, overlapTicks, maxActive, headingN, spinLogs, gateLogs;
    static BlockPos origin;

    static void log(String s) {
        log.add(s);
        TrafficCraft.LOG.info("[servertest] " + s);
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        if (!ENABLED) return;
        MinecraftServer server = e.getServer();
        ServerLevel l = server.overworld();
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        origin = new BlockPos(0, y, 0);
        for (int cx = -6; cx <= 8; cx++) for (int cz = -5; cz <= 5; cz++) l.setChunkForced(cx, cz, true);
        long t0 = System.nanoTime();
        DemoTown.build(l, origin);
        // location kits next to the avenue (gas station and parking lot facing north, a bus stop)
        com.trafficcraft.world.Kits.place(l, origin.offset(-52, -1, -16), net.minecraft.core.Direction.NORTH, "gas_station", null);
        com.trafficcraft.world.Kits.place(l, origin.offset(-22, -1, -16), net.minecraft.core.Direction.NORTH, "parking", null);
        // road pieces south-east of the centre: a crossroads joined to the avenue, then a sharp turn from its west arm
        net.minecraft.nbt.CompoundTag tool = com.trafficcraft.road.RoadToolItem.settings(net.minecraft.world.item.ItemStack.EMPTY);
        tool.putFloat("width", 7);
        tool.putInt("sidewalk", 2);
        tool.putInt("armLength", 35);
        com.trafficcraft.road.RoadPieces.build(l, Vec3.atBottomCenterOf(origin).add(45, 0, 35), net.minecraft.core.Direction.NORTH, com.trafficcraft.road.RoadPieces.CROSSROADS, tool);
        tool.putInt("armLength", 16);
        com.trafficcraft.road.RoadPieces.build(l, Vec3.atBottomCenterOf(origin).add(45, 0, 70), net.minecraft.core.Direction.SOUTH, com.trafficcraft.road.RoadPieces.TURN_RIGHT, tool);
        com.trafficcraft.road.RoadPieces.build(l, Vec3.atBottomCenterOf(origin).add(80, 0, 35), net.minecraft.core.Direction.EAST, com.trafficcraft.road.RoadPieces.TURN_RIGHT, tool);
        com.trafficcraft.road.RoadPieces.build(l, Vec3.atBottomCenterOf(origin).add(10, 0, 35), net.minecraft.core.Direction.WEST, com.trafficcraft.road.RoadPieces.T_JUNCTION, tool);
        com.trafficcraft.world.Kits.place(l, origin.offset(60, -1, -9), net.minecraft.core.Direction.NORTH, "bus_stop", null);
        log(String.format("demo town built in %.0f ms: %d nodes, %d edges", (System.nanoTime() - t0) / 1e6, RoadData.get(l).nodes.size(), RoadData.get(l).edges.size()));
        {
            var sn = RoadData.get(l).addNode(origin.getX() - 40.5, origin.getY(), origin.getZ() + 6.5, com.trafficcraft.road.NodeType.PARK_SPOT);
            com.trafficcraft.road.Props.defaults(com.trafficcraft.road.NodeType.PARK_SPOT, sn.cfg);
            sn.cfg.putInt("parkChance", 100);
            sn.cfg.putInt("stayMin", 10);
            sn.cfg.putInt("stayMax", 20);
            RoadData.get(l).changed();
        }
        for (var n : RoadData.get(l).nodesOfType(com.trafficcraft.road.NodeType.PARK_SPOT)) log("spot " + n.id + " -> " + com.trafficcraft.road.RoadsideSpot.of(RoadData.get(l), n));
        {
            // six-lane boulevard (3 lanes each way) made of short segments with slight bends: lane keeping test
            var net = RoadData.get(l);
            double[][] pts = {{-60, -95}, {-30, -97}, {0, -94}, {30, -98}, {60, -95}, {90, -96}};
            int prev = -1;
            for (int i = 0; i < pts.length; i++) {
                var type = i == 0 || i == pts.length - 1 ? com.trafficcraft.road.NodeType.SPAWN : com.trafficcraft.road.NodeType.ROAD;
                var n = net.addNode(origin.getX() + pts[i][0], origin.getY(), origin.getZ() + pts[i][1], type);
                com.trafficcraft.road.Props.defaults(type, n.cfg);
                if (type == com.trafficcraft.road.NodeType.SPAWN) {
                    n.cfg.putInt("max", 8);
                    n.cfg.putInt("interval", 4);
                }
                if (prev >= 0) {
                    var e6 = net.connect(prev, n.id, false);
                    e6.lanes.clear();
                    e6.lanes.addAll(com.trafficcraft.road.RoadEdge.defaultLanes(6, false));
                    e6.width = 20;
                    e6.speed = 70;
                }
                prev = n.id;
            }
            for (int cx = -7; cx <= 7; cx++) for (int cz = -8; cz <= -5; cz++) l.setChunkForced(cx, cz, true);
            net.changed();
        }
        {
            // easy border checkpoint: a barrier gate placed like a player would, then the road on each side connected to its node
            var rnet = RoadData.get(l);
            BlockPos gp = origin.offset(-43, 0, 67);
            l.setBlock(gp, com.trafficcraft.registry.TCBlocks.get("barrier_gate").defaultBlockState()
                    .setValue(com.trafficcraft.block.FacingBlock.FACING, net.minecraft.core.Direction.EAST), 3);
            var bn = com.trafficcraft.block.GateEvents.setup(l, gp, null);
            var a = rnet.addNode(origin.getX() - 74, origin.getY(), origin.getZ() + 64.5, com.trafficcraft.road.NodeType.SPAWN);
            var b = rnet.addNode(origin.getX() - 12, origin.getY(), origin.getZ() + 64.5, com.trafficcraft.road.NodeType.SPAWN);
            for (var sp : new com.trafficcraft.road.RoadNode[]{a, b}) {
                com.trafficcraft.road.Props.defaults(com.trafficcraft.road.NodeType.SPAWN, sp.cfg);
                sp.cfg.putInt("max", 3);
                sp.cfg.putInt("interval", 8);
            }
            for (var e2 : new com.trafficcraft.road.RoadEdge[]{rnet.connect(a.id, bn.id, false), rnet.connect(bn.id, b.id, false)}) {
                e2.lanes.clear();
                e2.lanes.addAll(com.trafficcraft.road.RoadEdge.defaultLanes(2, false));
                e2.width = 7;
                e2.speed = 40;
            }
            rnet.changed();
            log(String.format("gate checkpoint node %d at (%.1f, %.1f), officer=%s", bn.id, bn.pos().x - origin.getX(), bn.pos().z - origin.getZ(),
                    com.trafficcraft.npc.Workplaces.officerNear(l, bn)));
        }
        com.trafficcraft.TCConfig.MAX_AI_VEHICLES.set(90);
        TrafficManager.observer = Vec3.atCenterOf(origin).add(20, 0, 0);
        l.setDayTime(1000);
        t = 0;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (!ENABLED || t < 0) return;
        t++;
        ServerLevel l = e.getServer().overworld();
        if (t == 20 * 120) l.setDayTime(14000); // night half way: headlights
        List<VehicleEntity> vs = new ArrayList<>();
        for (Entity en : l.getAllEntities()) if (en instanceof VehicleEntity v && v.getMode() == VehicleEntity.MODE_AI) vs.add(v);
        maxActive = Math.max(maxActive, vs.size());
        int overlaps = 0;
        for (int i = 0; i < vs.size(); i++) {
            VehicleEntity a = vs.get(i);
            if (Math.abs(a.speed) > 0.5f) {
                speedSum += Math.abs(a.speed);
                speedN++;
            }
            if (Math.abs(a.getZ() - origin.getZ() - 64.5) < 4 && Math.abs(a.getX() - origin.getX() + 42.5) < 14 && a.driver != null && a.driver.state != TrafficDriver.State.PARKED && gateLogs < 60 && t % 5 == 0) {
                gateLogs++;
                log(String.format("   near-gate %s #%d dx=%.1f v=%.1f %s %s", a.getModel(), a.getId(), a.getX() - origin.getX() + 42.5, a.speed * 3.6, a.driver.debug(), a.driver.pieceInfo()));
            }
            if (Math.abs(a.getZ() - origin.getZ() + 96) < 8 && a.speed > 2f) {
                // six-lane boulevard: which lane is the car in (by lateral offset)?
                var bnet = RoadData.get(l);
                var be = bnet.nearestEdge(a.position(), 12, false);
                if (be != null && be.lanes.size() == 6) {
                    double[] po = new double[2];
                    bnet.geom(be).project(a.position(), po);
                    int lane = (int) Math.floor((be.width / 2 - po[0]) / be.laneWidth());
                    Integer prevLane = boulevardLane.put(a.getId(), lane);
                    if (prevLane == null) TrafficStats.inc("boulevard_cars");
                    else if (prevLane != lane) TrafficStats.inc("boulevard_lane_switches");
                    TrafficStats.inc("boulevard_samples");
                    if (a.driver != null && a.driver.headingError(a) > 20) TrafficStats.inc("boulevard_heading_off_20deg");
                }
            }
            if (a.speed > 3f && a.driver != null && a.driver.state == TrafficDriver.State.DRIVE) {
                double he = a.driver.headingError(a);
                headingN++;
                if (he > 25) TrafficStats.inc("heading_off_25deg");
                if (he > 90) {
                    TrafficStats.inc("heading_reversed");
                    if (spinLogs++ < 40 && (spinLogs % 4 == 1)) log(String.format("   spin? %s #%d err=%.0f v=%.1f pos=(%.1f,%.1f) yaw=%.0f %s %s", a.getModel(), a.getId(), he, a.speed * 3.6,
                            a.getX(), a.getZ(), a.getYRot(), a.driver.debug(), a.driver.pieceInfo()));
                }
            }
            for (int j = i + 1; j < vs.size(); j++) if (a.overlaps(vs.get(j), a.position(), a.getYRot())) overlaps++;
        }
        if (overlaps > 0) overlapTicks++;
        // exercise the gas stations: some cars run low on fuel
        if (t % 300 == 100) {
            int k = 0;
            for (VehicleEntity v : vs) if (v.driver != null && v.driver.state == TrafficDriver.State.DRIVE && v.getFuel() > v.spec().fuelCapacity * 0.3f && k++ < 2)
                v.setFuel(v.spec().fuelCapacity * 0.08f);
        }
        if (t % 400 == 0) {
            Map<TrafficDriver.State, Integer> st = new EnumMap<>(TrafficDriver.State.class);
            int lightsOn = 0, indicators = 0, brakes = 0;
            for (VehicleEntity v : vs) {
                if (v.driver != null) st.merge(v.driver.state, 1, Integer::sum);
                if (v.has(VehicleEntity.F_LOW)) lightsOn++;
                if (v.has(VehicleEntity.F_IND_L) || v.has(VehicleEntity.F_IND_R)) indicators++;
                if (v.has(VehicleEntity.F_BRAKE)) brakes++;
            }
            for (VehicleEntity v : vs.subList(0, Math.min(16, vs.size()))) {
                if (v.driver != null) log(String.format("   %s #%d %s v=%.1f pos=(%.1f,%.1f,%.1f) yaw=%.0f", v.getModel(), v.getId(), v.driver.debug(), v.speed * 3.6,
                        v.getX() - origin.getX(), v.getY() - origin.getY(), v.getZ() - origin.getZ(), v.getYRot()));
            }
            for (VehicleEntity v : vs) if (Math.abs(v.getX() - origin.getX() + 43) < 32 && Math.abs(v.getZ() - origin.getZ() - 55) < 8 && v.driver != null)
                log(String.format("   gate-road %s #%d v=%.1f x=%.1f %s", v.getModel(), v.getId(), v.speed * 3.6, v.getX() - origin.getX(), v.driver.debug()));
            Map<String, Integer> peds = new java.util.TreeMap<>();
            for (Entity en : l.getAllEntities()) if (en instanceof com.trafficcraft.npc.PedestrianEntity p) peds.merge(p.kind() + ":" + p.anim(), 1, Integer::sum);
            log("   pedestrians (kind:anim)=" + peds);
            TrafficManager tm = TrafficManager.get(l);
            log(String.format("t=%ds active=%d spawned=%d despawned=%d states=%s headlights=%d indicating=%d braking=%d mspt=%.2f", t / 20, vs.size(), tm.spawnedTotal,
                    tm.despawnedTotal, st, lightsOn, indicators, brakes, e.getServer().getCurrentSmoothedTickTime()));
        }
        if (t >= DURATION) {
            TrafficManager tm = TrafficManager.get(l);
            log("==== RESULT ====");
            log("spawned=" + tm.spawnedTotal + " despawned=" + tm.despawnedTotal + " max_active=" + maxActive);
            log(String.format("average moving speed=%.1f km/h", speedN == 0 ? 0 : speedSum / speedN * 3.6));
            log("ticks with overlapping vehicles=" + overlapTicks + " of " + t);
            log("moving-car samples=" + headingN);
            log("counters=" + TrafficStats.C);
            try (FileWriter w = new FileWriter("traffic_report.txt")) {
                for (String s : log) w.write(s + "\n");
            } catch (Exception ex) {
                TrafficCraft.LOG.error("report", ex);
            }
            e.getServer().halt(false);
            t = -1;
        }
    }
}
