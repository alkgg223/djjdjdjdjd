package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotTeam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What a side knows about the enemy: enemies spotted by its recon and surveillance drones (or marked by a drone pilot)
 * stay known for a while. Soldiers of the side turn towards a spotted enemy they cannot see yet, the players of the
 * side see it outlined through walls, and the tactical map shows it.
 */
public final class Spotting {
    private Spotting() {}

    /** level -> side -> entity id -> game time it stops being known */
    private static final Map<ResourceKey<Level>, Map<BotTeam, Map<Integer, Long>>> SPOTS = new HashMap<>();
    /** the last known place of each spotted entity (for the map, when it is too far to be sent to the client) */
    private static final Map<Integer, Vec3> LAST = new HashMap<>();

    private static Map<Integer, Long> of(Level level, BotTeam team) {
        return SPOTS.computeIfAbsent(level.dimension(), k -> new HashMap<>()).computeIfAbsent(team, k -> new HashMap<>());
    }

    public static void spot(Level level, BotTeam team, Entity e, int ticks) {
        if (team == null || level.isClientSide) return;
        long until = level.getGameTime() + ticks;
        of(level, team).merge(e.getId(), until, Math::max);
        LAST.put(e.getId(), e.position());
    }

    public static boolean spotted(Level level, BotTeam team, Entity e) {
        if (team == null || level.isClientSide) return false;
        Map<BotTeam, Map<Integer, Long>> m = SPOTS.get(level.dimension());
        if (m == null) return false;
        Map<Integer, Long> t = m.get(team);
        Long until = t == null ? null : t.get(e.getId());
        return until != null && until >= level.getGameTime();
    }

    /** the entities the side knows of now, with where they are */
    public static List<Entity> known(ServerLevel level, BotTeam team) {
        List<Entity> l = new ArrayList<>();
        Map<BotTeam, Map<Integer, Long>> m = SPOTS.get(level.dimension());
        if (m == null || team == null) return l;
        Map<Integer, Long> t = m.get(team);
        if (t == null) return l;
        long now = level.getGameTime();
        for (Map.Entry<Integer, Long> en : t.entrySet()) {
            if (en.getValue() < now) continue;
            Entity e = level.getEntity(en.getKey());
            if (e != null && e.isAlive()) l.add(e);
        }
        return l;
    }

    /** once a second: forget the old, keep the places fresh, and tell the players of each side what it knows */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            Map<BotTeam, Map<Integer, Long>> m = SPOTS.get(level.dimension());
            if (m == null) continue;
            long now = level.getGameTime();
            for (Map<Integer, Long> t : m.values()) {
                t.values().removeIf(u -> u < now);
                for (Integer id : t.keySet()) {
                    Entity e = level.getEntity(id);
                    if (e != null) LAST.put(id, e.position());
                }
            }
            for (ServerPlayer p : level.players()) {
                BotTeam team = BotTeam.of(p);
                Map<Integer, Long> t = team == null ? null : m.get(team);
                int[] ids = t == null ? new int[0] : t.keySet().stream().mapToInt(Integer::intValue).toArray();
                PacketDistributor.sendToPlayer(p, new com.gunsmod.net.Net.Spotted(ids));
            }
        }
        if (LAST.size() > 4096) LAST.clear();
    }

    public static Vec3 lastPlace(Entity e) {
        return LAST.getOrDefault(e.getId(), e.position());
    }
}
