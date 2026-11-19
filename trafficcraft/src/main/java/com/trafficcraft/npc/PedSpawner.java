package com.trafficcraft.npc;

import com.trafficcraft.TCConfig;
import com.trafficcraft.registry.TCRegistry;
import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Spawns pedestrians at PED_SPAWN nodes near players and removes those far away. */
public final class PedSpawner {
    private final Map<Integer, Long> last = new HashMap<>();
    public int spawned;

    public void tick(ServerLevel level, RoadNetwork net, long now) {
        List<PedestrianEntity> all = new ArrayList<>();
        Map<Integer, Integer> per = new HashMap<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof PedestrianEntity p && !p.isWorker()) {
                all.add(p);
                per.merge(p.brain.spawner, 1, Integer::sum);
            }
        }
        double far = Math.min(128, TCConfig.DESPAWN_DISTANCE.get());
        for (PedestrianEntity p : all) {
            if (nearestPlayer(level, p.position()) > far) {
                p.discard();
                per.merge(p.brain.spawner, -1, Integer::sum);
            }
        }
        int total = all.size(), max = TCConfig.MAX_PEDESTRIANS.get();
        for (RoadNode n : net.nodesOfType(NodeType.PED_SPAWN)) {
            if (total >= max) break;
            if (n.cfg.contains("enabled") && !n.cfg.getBoolean("enabled")) continue;
            double d = nearestPlayer(level, n.pos());
            if (d > 96) continue;
            int interval = 20 * (n.cfg.contains("interval") ? n.cfg.getInt("interval") : TCConfig.DEFAULT_PED_INTERVAL.get());
            Long l = last.get(n.id);
            if (l != null && now - l < interval) continue;
            int cap = (int) Math.round((n.cfg.contains("max") ? n.cfg.getInt("max") : 12) * TCConfig.PED_DENSITY.get());
            if (per.getOrDefault(n.id, 0) >= cap) continue;
            Vec3 at = n.pos();
            if (!level.isLoaded(BlockPos.containing(at))) continue;
            if (!level.getEntitiesOfClass(PedestrianEntity.class, new AABB(at, at).inflate(0.8)).isEmpty()) continue;
            String kind = pickKind(n, level);
            if (kind == null) continue;
            PedestrianEntity p = spawn(level, at, kind, n.id);
            float sp = n.cfg.contains("speed") ? n.cfg.getFloat("speed") : 1.2f;
            p.brain.start(n.id, n.id, (kind.equals("elderly") ? 0.36 : kind.equals("child") ? 0.6 : 0.48) * sp * (0.9 + level.random.nextDouble() * 0.2));
            last.put(n.id, now);
            per.merge(n.id, 1, Integer::sum);
            total++;
            spawned++;
        }
    }

    public static PedestrianEntity spawn(ServerLevel level, Vec3 at, String kind, int spawner) {
        PedestrianEntity p = new PedestrianEntity(TCRegistry.PEDESTRIAN.get(), level);
        p.setKind(kind);
        p.setSkin(PedSkins.forKind(kind, level.random));
        p.moveTo(at.x, at.y + 0.05, at.z, level.random.nextFloat() * 360, 0);
        p.brain.spawner = spawner;
        level.addFreshEntity(p);
        return p;
    }

    static String pickKind(RoadNode n, ServerLevel level) {
        for (int i = 0; i < 8; i++) {
            String k = PedSkins.randomCivilian(level.random);
            String key = switch (k) {
                case "adult" -> "adults";
                case "child" -> "children";
                case "worker" -> "workers";
                default -> k;
            };
            if (!n.cfg.contains(key) || n.cfg.getBoolean(key)) return k;
        }
        return null;
    }

    static double nearestPlayer(ServerLevel level, Vec3 p) {
        Vec3 o = com.trafficcraft.ai.TrafficManager.observer;
        double best = o == null ? Double.MAX_VALUE : o.distanceTo(p);
        for (Player pl : level.players()) best = Math.min(best, pl.position().distanceTo(p));
        return best;
    }
}
