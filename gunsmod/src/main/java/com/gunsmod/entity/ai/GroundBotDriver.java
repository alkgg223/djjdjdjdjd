package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import com.gunsmod.world.BattleMap;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * How soldiers drive ground vehicles: patrol from waypoint to waypoint with a stop at each one, and when an enemy is
 * in sight and in range, stop so the gunner can hit it. Too close: back off. Enemy hidden: move round until it shows.
 * On an orchard battlefield they drive the driver's road to the enemy base instead and never leave it.
 */
public final class GroundBotDriver {
    @Nullable private Vec3 waypoint;
    private int since, pause, hidden;
    @Nullable private List<Vec3> route;
    @Nullable private BattleMap routeMap;
    private int routeLane, routeIdx = -1;

    /** the driver knows the field this vehicle is on (an orchard battlefield) */
    public static boolean onField(Entity v, BotEntity driver) {
        return driver.battle != null && driver.battle.contains(v.position());
    }

    /**
     * Battlefield driving: along the road to the enemy base with a halt at every crossroads, halting to fight an
     * enemy in sight and in range. {@code reach} is how far along the way to go (1 = into the enemy base).
     * Null: stand (brake).
     */
    @Nullable
    public Vec3 road(Entity v, BotEntity driver, @Nullable LivingEntity enemy, boolean armed, double engage, double reach) {
        BattleMap map = driver.battle;
        if (route == null || !map.equals(routeMap) || routeLane != driver.lane) {
            route = map.route(driver.getBotTeam(), driver.lane, 6);
            routeMap = map;
            routeLane = driver.lane;
            routeIdx = -1;
        }
        if (routeIdx < 0 || horiz(v, route.get(routeIdx)) > 24) routeIdx = BattleMap.resume(route, v.position(), routeIdx - 1);
        if (enemy != null && enemy.isAlive() && armed && horiz(v, enemy.position()) < engage && canSee(v, enemy)) return null;
        if (pause > 0) {
            pause--;
            return null;
        }
        int end = Math.max(1, (int) Math.round((route.size() - 1) * reach));
        while (routeIdx < end && horiz(v, route.get(routeIdx)) < 6) {
            routeIdx++;
            if (map.junction(route.get(routeIdx - 1))) {
                // a halt at the crossroads: look down the roads, then drive on
                pause = 40 + v.getRandom().nextInt(80);
                return null;
            }
        }
        if (routeIdx >= end && horiz(v, route.get(end)) < 8) return null;    // there: hold the road
        return route.get(Math.min(routeIdx, end));
    }

    /** where to drive now, or null to stand (brake) */
    @Nullable
    public Vec3 goal(Entity v, @Nullable LivingEntity enemy, boolean armed, Vec3 home, double leash, double engage, double tooClose) {
        if (enemy != null && enemy.isAlive() && armed) {
            double d = horiz(v, enemy.position());
            boolean see = canSee(v, enemy);
            hidden = see ? 0 : hidden + 1;
            Vec3 away = v.position().subtract(enemy.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
            away = away.normalize();
            if (d < tooClose) return v.position().add(away.scale(18));
            if (see && d < engage) return null;                     // halt and fire
            if (hidden > 60 || d >= engage) {
                // swing round to the side and close in until it can be seen again
                double a = Math.toRadians(35);
                Vec3 side = new Vec3(away.x * Math.cos(a) - away.z * Math.sin(a), 0, away.x * Math.sin(a) + away.z * Math.cos(a));
                return enemy.position().add(side.scale(Math.max(tooClose + 6, engage * 0.55)));
            }
            return null;
        }
        hidden = 0;
        if (pause > 0) {
            pause--;
            return null;
        }
        if (waypoint == null || v.tickCount - since > 900) {
            waypoint = DriveAI.groundWaypoint(v, home, leash, 25, 55, 10);
            since = v.tickCount;
            if (waypoint == null) {
                pause = 40;
                return null;
            }
        }
        if (horiz(v, waypoint) < 9) {
            // a stop at every waypoint: look around for a while, then drive on
            waypoint = null;
            pause = 60 + v.getRandom().nextInt(140);
            return null;
        }
        return waypoint;
    }

    /** throttle that brings the vehicle to a halt without reversing */
    public static float brake(double speed) {
        return speed > 0.02 ? -1f : speed < -0.02 ? 1f : 0f;
    }

    public static boolean canSee(Entity v, LivingEntity e) {
        Vec3 from = v.position().add(0, v.getBbHeight() * 0.9, 0);
        Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0);
        return v.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, v)).getType() == HitResult.Type.MISS;
    }

    private static double horiz(Entity v, Vec3 p) {
        double dx = p.x - v.getX(), dz = p.z - v.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
