package com.trafficcraft.ai;

import com.trafficcraft.TCConfig;
import com.trafficcraft.TrafficCraft;
import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.road.EdgeGeom;
import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadEdge;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleInput;
import com.trafficcraft.vehicle.VehicleSpec;
import com.trafficcraft.vehicle.VehicleSpecs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Per-level traffic simulation: spatial grid, junction reservations, spawning/despawning and AI scheduling. */
public final class TrafficManager {
    private static final Map<ServerLevel, TrafficManager> ALL = new WeakHashMap<>();

    public final SpatialGrid grid = new SpatialGrid();
    public final JunctionManager junctions = new JunctionManager();
    public final ParkingManager parking = new ParkingManager();
    public final FuelService fuel = new FuelService();
    public final BorderControl border = new BorderControl();
    public final com.trafficcraft.npc.PedSpawner peds = new com.trafficcraft.npc.PedSpawner();
    final Map<Integer, Set<Integer>> bySpawner = new HashMap<>();
    final Map<Integer, Long> lastSpawn = new HashMap<>();
    final Set<Integer> aiVehicles = new HashSet<>();
    public int spawnedTotal, despawnedTotal;

    public static TrafficManager get(ServerLevel l) {
        return ALL.computeIfAbsent(l, k -> new TrafficManager());
    }

    public static void tick(ServerLevel level, RoadNetwork net) {
        TrafficManager tm = get(level);
        tm.doTick(level, net);
    }

    void doTick(ServerLevel level, RoadNetwork net) {
        long now = level.getGameTime();
        grid.clear();
        aiVehicles.clear();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof VehicleEntity v) {
                grid.add(e);
                if (v.getMode() == VehicleEntity.MODE_AI) aiVehicles.add(v.getId());
            } else if (e instanceof net.minecraft.world.entity.LivingEntity && !e.isPassenger()) grid.add(e);
        }
        if (now % 100 == 0) junctions.expire(now);
        if (now % 5 == 0) {
            gates(level, net);
            border.playerTick(level, net);
        }
        if (now % 20 == 0) {
            spawn(level, net, now);
            despawn(level, net);
        }
        if (now % 20 == 10) peds.tick(level, net, now);
    }

    /** Called by the vehicle each tick; runs the AI at the LOD interval and reuses the last input otherwise. */
    public static VehicleInput input(VehicleEntity v) {
        if (!(v.level() instanceof ServerLevel level)) return VehicleInput.PARK;
        TrafficDriver d = v.driver;
        if (d == null) return VehicleInput.PARK;
        TrafficManager tm = get(level);
        RoadNetwork net = RoadData.get(level);
        double nearest = nearestPlayerDist(level, v.position());
        int interval = nearest < 48 ? TCConfig.AI_TICK_NEAR.get() : nearest < 112 ? Math.max(2, TCConfig.AI_TICK_NEAR.get() * 2) : TCConfig.AI_TICK_FAR.get();
        if ((v.tickCount + v.getId()) % interval != 0) return d.last;
        try {
            VehicleInput in = d.compute(v, level, net, tm);
            if (d.state == TrafficDriver.State.DESPAWN) {
                if (!v.getPassengers().isEmpty()) {
                    // end of the route with someone on board: pull over and let them out
                    v.setFlag(VehicleEntity.F_HAZARD, true);
                    if (!d.lastStopTold) {
                        d.lastStopTold = true;
                        for (var p : v.getPassengers()) if (p instanceof net.minecraft.world.entity.player.Player pl)
                            pl.displayClientMessage(Component.translatable("msg.trafficcraft.last_stop"), true);
                    }
                    return VehicleInput.PARK;
                }
                remove(v, tm);
            }
            return in;
        } catch (RuntimeException ex) {
            TrafficCraft.LOG.warn("AI error for vehicle {}: {}", v.getId(), ex.toString());
            remove(v, tm);
            return VehicleInput.PARK;
        }
    }

    /** Optional virtual observer (headless tests / servers that want traffic without players). */
    public static Vec3 observer;

    static double nearestPlayerDist(ServerLevel level, Vec3 p) {
        double best = observer != null ? observer.distanceToSqr(p) : Double.MAX_VALUE;
        for (ServerPlayer sp : level.players()) best = Math.min(best, sp.position().distanceToSqr(p));
        return Math.sqrt(best);
    }

    static void remove(VehicleEntity v, TrafficManager tm) {
        tm.junctions.releaseAll(v.getId());
        tm.parking.releaseVehicle(v.getId());
        tm.fuel.forget(v.getId());
        tm.border.forget(v.getId());
        for (Set<Integer> s : tm.bySpawner.values()) s.remove(v.getId());
        tm.despawnedTotal++;
        v.discard();
    }

    // ------------------------------------------------------------------------------------ spawning
    void spawn(ServerLevel level, RoadNetwork net, long now) {
        int max = TCConfig.MAX_AI_VEHICLES.get();
        if (aiVehicles.size() >= max) return;
        double sim = TCConfig.SIM_DISTANCE.get();
        boolean day = level.getDayTime() % 24000 < 12500;
        long tod = level.getDayTime() % 24000;
        for (RoadNode n : net.nodesOfType(NodeType.SPAWN)) {
            if (n.cfg.contains("enabled") && !n.cfg.getBoolean("enabled")) continue;
            if (nearestPlayerDist(level, n.pos()) > sim) continue;
            if (!level.isLoaded(BlockPos.containing(n.pos()))) continue;
            String time = n.cfg.contains("time") ? n.cfg.getString("time") : "always";
            if (time.equals("day") && !day || time.equals("night") && day) continue;
            boolean rush = (tod > 0 && tod < 2500) || (tod > 10000 && tod < 12500);
            Set<Integer> mine = bySpawner.computeIfAbsent(n.id, k -> new HashSet<>());
            mine.removeIf(id -> !(level.getEntity(id) instanceof VehicleEntity ve) || ve.isRemoved());
            int cap = n.cfg.contains("max") ? n.cfg.getInt("max") : 10;
            if (mine.size() >= cap) continue;
            int interval = (n.cfg.contains("interval") ? n.cfg.getInt("interval") : TCConfig.DEFAULT_SPAWN_INTERVAL.get()) * 20;
            double density = (n.cfg.contains("density") ? n.cfg.getInt("density") : 100) / 100.0 * TCConfig.TRAFFIC_DENSITY.get();
            if (time.equals("rush")) density *= rush ? 1.8 : 0.5;
            if (density <= 0) continue;
            long last = lastSpawn.getOrDefault(n.id, 0L);
            if (now - last < interval / density) continue;
            String model = pickModel(level, n, mine);
            if (model == null) continue;
            if (spawnAt(level, net, n, model) instanceof VehicleEntity v) {
                mine.add(v.getId());
                lastSpawn.put(n.id, now);
                spawnedTotal++;
                if (aiVehicles.size() + 1 >= max) return;
            } else {
                lastSpawn.put(n.id, now - interval / 2); // retry a bit later
            }
        }
    }

    String pickModel(ServerLevel level, RoadNode n, Set<Integer> mine) {
        Map<String, Integer> have = new HashMap<>();
        for (int id : mine) if (level.getEntity(id) instanceof VehicleEntity v) have.merge(v.getModel(), 1, Integer::sum);
        List<String> ids = VehicleSpecs.ids();
        boolean any = false;
        for (String id : ids) if (n.cfg.getInt("m_" + id) > 0) any = true;
        List<String> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (String id : ids) {
            VehicleSpec s = VehicleSpecs.get(id);
            int limit = n.cfg.getInt("m_" + id);
            if (any) {
                if (limit <= 0 || have.getOrDefault(id, 0) >= limit) continue;
                pool.add(id);
                weights.add(n.cfg.getBoolean("random") || !n.cfg.contains("random") ? limit : 1);
            } else {
                pool.add(id);
                weights.add(Math.max(1, s.spawnWeight));
            }
        }
        if (pool.isEmpty()) return null;
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        int r = level.random.nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            r -= weights.get(i);
            if (r < 0) return pool.get(i);
        }
        return pool.get(0);
    }

    Entity spawnAt(ServerLevel level, RoadNetwork net, RoadNode n, String model) {
        List<RoadEdge> out = new ArrayList<>();
        for (RoadEdge e : net.roadEdgesOf(n.id)) if (e.allows(n.id) && !e.lanesFrom(n.id).isEmpty()) out.add(e);
        if (out.isEmpty()) return null;
        RoadEdge e = out.get(level.random.nextInt(out.size()));
        List<Integer> lanes = e.lanesFrom(n.id);
        int lane = lanes.get(level.random.nextInt(lanes.size()));
        EdgeGeom g = net.geom(e);
        VehicleSpec spec = VehicleSpecs.get(model);
        double sStart = (e.a == n.id ? g.trimA : g.length - g.trimB);
        double sv = sStart + (e.a == n.id ? 1 : -1) * (spec.length / 2 + 0.5);
        Vec3 p = g.point(sv, e.laneOffset(lane));
        // room check (capacity): nothing within 2.5 vehicle lengths
        List<Entity> near = new ArrayList<>();
        grid.near(p.x, p.z, spec.length * 2.5 + 4, near);
        for (Entity o : near) if (o.position().distanceTo(p) < spec.length * 1.4 + 3) return null;
        if (!level.noCollision(new net.minecraft.world.phys.AABB(p.x - 0.8, p.y + 0.3, p.z - 0.8, p.x + 0.8, p.y + 1.4, p.z + 0.8))) return null;
        VehicleEntity v = new VehicleEntity(TCRegistry.VEHICLE.get(), level);
        v.setModel(model);
        v.randomizeConfig(level.random);
        v.setColor(spec.colors[level.random.nextInt(spec.colors.length)]);
        v.setPlate(VehicleEntity.randomPlate(level.random));
        Vec3 t = g.tangent(sv).scale(e.a == n.id ? 1 : -1);
        float yaw = (float) Math.toDegrees(Math.atan2(-t.x, t.z));
        v.moveTo(p.x, p.y, p.z, yaw, 0);
        v.setFuel(spec.fuelCapacity * (0.15f + level.random.nextFloat() * 0.8f));
        v.setMode(VehicleEntity.MODE_AI);
        v.setFlag(VehicleEntity.F_ENGINE, true);
        Personality pers = pickPersonality(level, n);
        float minS = (n.cfg.contains("minSpeed") ? n.cfg.getInt("minSpeed") : 40) / 3.6f, maxS = (n.cfg.contains("maxSpeed") ? n.cfg.getInt("maxSpeed") : 80) / 3.6f;
        float cap = minS + level.random.nextFloat() * Math.max(0, maxS - minS);
        TrafficDriver d = new TrafficDriver(pers, n.id, 0.9f + level.random.nextFloat() * 0.2f, Math.max(5, cap));
        d.start(net, e, n.id, lane, sv);
        v.driver = d;
        float initial = Math.min(cap, RoutePlanner.edgeSpeed(e)) * 0.6f;
        v.speed = initial;
        level.addFreshEntity(v);
        return v;
    }

    Personality pickPersonality(ServerLevel level, RoadNode n) {
        int p = n.cfg.contains("patient") ? n.cfg.getInt("patient") : 30, m = n.cfg.contains("normal") ? n.cfg.getInt("normal") : 50,
                i = n.cfg.contains("impatient") ? n.cfg.getInt("impatient") : 20, pr = n.cfg.contains("pro") ? n.cfg.getInt("pro") : 10;
        int t = Math.max(1, p + m + i + pr);
        int r = level.random.nextInt(t);
        return r < p ? Personality.PATIENT : r < p + m ? Personality.NORMAL : r < p + m + i ? Personality.IMPATIENT : Personality.PRO;
    }

    void despawn(ServerLevel level, RoadNetwork net) {
        double dd = TCConfig.DESPAWN_DISTANCE.get();
        for (int id : new ArrayList<>(aiVehicles)) {
            if (!(level.getEntity(id) instanceof VehicleEntity v)) continue;
            if (v.driver == null) {
                v.setMode(VehicleEntity.MODE_FREE);
                continue;
            }
            double near = nearestPlayerDist(level, v.position());
            boolean stuck = v.driver.stateTicks > 20 * 180 && Math.abs(v.speed) < 0.1f && v.driver.state == TrafficDriver.State.DRIVE && near > 32;
            if (near > dd || stuck) remove(v, this);
        }
    }

    /** Automatic barriers near parking nodes rise for vehicles coming toward them (border gates are opened by inspections). */
    void gates(ServerLevel level, RoadNetwork net) {
        for (RoadNode n : net.nodesOfType(NodeType.PARKING)) {
            if (nearestPlayerDist(level, n.pos()) > 96) continue;
            BlockPos c = BlockPos.containing(n.pos());
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-12, -2, -12), c.offset(12, 2, 12))) {
                if (!(level.getBlockEntity(q) instanceof com.trafficcraft.block.BarrierGateBE g) || g.manual) continue;
                List<Entity> near = new ArrayList<>();
                grid.near(q.getX() + 0.5, q.getZ() + 0.5, 12, near);
                for (Entity e : near) {
                    if (!(e instanceof VehicleEntity v)) continue;
                    Vec3 to = Vec3.atCenterOf(q).subtract(v.position());
                    double d = to.length();
                    if (d < 11 && (d < 4 || v.forward().dot(to.normalize()) > 0.3)) {
                        g.setOpen(true, 60);
                        break;
                    }
                }
            }
        }
    }

    public int aiCount() {
        return aiVehicles.size();
    }
}
