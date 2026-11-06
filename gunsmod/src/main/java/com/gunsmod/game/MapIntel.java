package com.gunsmod.game;

import com.gunsmod.block.airdef.AirTracker;
import com.gunsmod.block.airdef.DefenseBlockEntity;
import com.gunsmod.block.airdef.RadarBlockEntity;
import com.gunsmod.entity.Aircraft;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.entity.ai.Spotting;
import com.gunsmod.net.Net;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * What the tactical map shows a player: his side's soldiers, players and vehicles, his own squad, the enemies his side
 * knows of (spotted by drones, or tracked by its radars in the air), his side's radars and launchers, and the
 * objectives round him.
 */
public final class MapIntel {
    private MapIntel() {}

    public static final byte SELF = 0, FRIEND_PLAYER = 1, FRIEND_SOLDIER = 2, FRIEND_VEHICLE = 3, FRIEND_AIR = 4, ENEMY_SOLDIER = 5, ENEMY_VEHICLE = 6,
            ENEMY_AIR = 7, RADAR = 8, LAUNCHER = 9, OBJECTIVE = 10, SQUAD = 11;

    public static Net.MapIntel build(ServerPlayer p, int radius) {
        ServerLevel level = p.serverLevel();
        BotTeam team = BotTeam.of(p);
        List<Net.MapMark> marks = new ArrayList<>();
        marks.add(mark(SELF, team, p, (byte) 0));
        // what the side's radars hold in the air
        Set<Integer> tracked = new HashSet<>();
        for (RadarBlockEntity r : AirTracker.radarsNear(level, p.blockPosition(), radius + 400)) {
            if (r.team() != team) continue;
            for (int id : r.tracked()) tracked.add(id);
            if (r.getBlockPos().distSqr(p.blockPosition()) <= (double) radius * radius) {
                BlockPos b = r.getBlockPos();
                marks.add(new Net.MapMark(RADAR, (byte) team.ordinal(), b.getX() + 0.5f, b.getZ() + 0.5f, 0f, (byte) 0));
            }
        }
        for (DefenseBlockEntity d : AirTracker.launchersNear(level, p.blockPosition(), radius)) {
            if (d.team() != team) continue;
            BlockPos b = d.getBlockPos();
            marks.add(new Net.MapMark(LAUNCHER, (byte) team.ordinal(), b.getX() + 0.5f, b.getZ() + 0.5f, 0f, (byte) 0));
        }
        for (Entity e : level.getEntities(p, new AABB(p.blockPosition()).inflate(radius, 256, radius), e -> e instanceof LivingEntity le && le.isAlive())) {
            if (marks.size() >= 560) break;
            if (e.getVehicle() != null) continue;   // crews are shown by their vehicle
            BotTeam t = BotTeam.of(e);
            if (t == null) continue;
            boolean air = e instanceof Aircraft a && a.isAirborne();
            boolean vehicle = e instanceof VehicleEntity || e instanceof JeepEntity;
            if (t == team) {
                byte k = e instanceof Player ? FRIEND_PLAYER : air ? FRIEND_AIR : vehicle ? FRIEND_VEHICLE
                        : e instanceof BotEntity b && p.getUUID().equals(b.squadLeader) ? SQUAD : e instanceof BotEntity ? FRIEND_SOLDIER : -1;
                if (k >= 0) marks.add(mark(k, t, e, (byte) 0));
            } else if (Spotting.spotted(level, team, e) || air && tracked.contains(e.getId())) {
                marks.add(mark(air ? ENEMY_AIR : vehicle ? ENEMY_VEHICLE : ENEMY_SOLDIER, t, e, (byte) 0));
            }
        }
        for (ObjectiveBlockEntity o : Objectives.near(level, p.blockPosition(), radius)) {
            if (o.kind() == ObjectiveBlock.Kind.CONTROL) continue;
            byte k = (byte) (OBJECTIVE + 0);
            marks.add(new Net.MapMark(k, (byte) (o.owner() == null ? -1 : o.owner().ordinal()), (float) o.center().x, (float) o.center().z, o.kind().ordinal(),
                    (byte) o.label()));
        }
        return new Net.MapIntel(marks);
    }

    private static Net.MapMark mark(byte kind, BotTeam t, Entity e, byte label) {
        return new Net.MapMark(kind, (byte) (t == null ? -1 : t.ordinal()), (float) e.getX(), (float) e.getZ(), e.getYRot(), label);
    }
}
