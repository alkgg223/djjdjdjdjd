package com.gunsmod.entity.ai;

import com.gunsmod.entity.Aircraft;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.VehicleEntity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The B-2 only bombs groups: several enemy soldiers or vehicles close together (soldier 1, player 1.5, vehicle 2.5,
 * this much weight within GROUP_RADIUS of one of them), and none of its own side within SAFE_RADIUS of the spot.
 */
public final class GroupFinder {
    private GroupFinder() {}

    public static final double GROUP_RADIUS = 9, SAFE_RADIUS = 30;
    public static final float GROUP_WEIGHT = 4f;

    private static float weight(LivingEntity e) {
        if (e instanceof VehicleEntity || e instanceof JeepEntity) return 2.5f;
        return e instanceof Player ? 1.5f : 1f;
    }

    private static boolean enemy(LivingEntity e, BotTeam team) {
        if (!e.isAlive() || e.isSpectator()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (VehicleEntity.shieldOf(e) != null) return false;     // counted with its vehicle
        BotTeam t = BotTeam.of(e);
        if (t == null || t == team) return false;
        if (e instanceof VehicleEntity v) return v.isManned() && !(v instanceof Aircraft a && a.isAirborne());
        if (e instanceof JeepEntity j) return !j.getPassengers().isEmpty();
        return e instanceof BotEntity || e instanceof Player;
    }

    private static boolean friend(LivingEntity e, BotTeam team, Entity self) {
        if (!e.isAlive() || e == self || e.getVehicle() == self) return false;
        if (e instanceof Player p && p.isSpectator()) return false;
        return BotTeam.of(e) == team && (e instanceof BotEntity || e instanceof Player || e instanceof VehicleEntity || e instanceof JeepEntity);
    }

    /** the middle of the biggest safe group near {@code around}, on the ground; null when there is none */
    @Nullable
    public static Vec3 find(Level level, Entity self, BotTeam team, Vec3 around, double radius) {
        AABB box = new AABB(around.x - radius, around.y - 220, around.z - radius, around.x + radius, around.y + 40, around.z + radius);
        List<LivingEntity> foes = new ArrayList<>(), friends = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != self)) {
            if (enemy(e, team)) foes.add(e);
            else if (friend(e, team, self)) friends.add(e);
        }
        Vec3 best = null;
        float bestW = GROUP_WEIGHT - 0.01f;
        double r2 = GROUP_RADIUS * GROUP_RADIUS;
        for (LivingEntity a : foes) {
            float w = 0;
            double sx = 0, sy = 0, sz = 0;
            for (LivingEntity b : foes) {
                if (a.distanceToSqr(b) > r2) continue;
                float wb = weight(b);
                w += wb;
                sx += b.getX() * wb;
                sy += b.getY() * wb;
                sz += b.getZ() * wb;
            }
            if (w <= bestW) continue;
            Vec3 mid = new Vec3(sx / w, sy / w, sz / w);
            boolean safe = true;
            for (LivingEntity f : friends) {
                if (f.distanceToSqr(mid) < SAFE_RADIUS * SAFE_RADIUS) {
                    safe = false;
                    break;
                }
            }
            if (!safe) continue;
            bestW = w;
            best = mid;
        }
        return best;
    }
}
