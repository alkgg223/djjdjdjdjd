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
    static long speedN, overlapTicks, maxActive;
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
        log(String.format("demo town built in %.0f ms: %d nodes, %d edges", (System.nanoTime() - t0) / 1e6, RoadData.get(l).nodes.size(), RoadData.get(l).edges.size()));
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
            for (int j = i + 1; j < vs.size(); j++) if (a.overlaps(vs.get(j), a.position(), a.getYRot())) overlaps++;
        }
        if (overlaps > 0) overlapTicks++;
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
