package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import com.gunsmod.world.BattleMap;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Soldiers never stand around: with no enemy they keep exploring the area around their post, walking from one
 * waypoint straight on to the next. The last known enemy position is checked first.
 * On an orchard battlefield they know the field instead: they march on the enemy base along their road and only on
 * the roads, halting briefly at every crossroads, and take up the march again where they left it after a fight.
 * At the enemy base they hold the last stretch of road.
 */
public class BotPatrolGoal extends Goal {
    private final BotEntity bot;
    private Vec3 dest;
    private int ticks, stuck, retry, halt;
    private Vec3 lastPos = Vec3.ZERO;
    private List<Vec3> route;
    private BattleMap routeMap;
    private int routeLane;

    public BotPatrolGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (bot.getTarget() != null || bot.isPassenger()) return false;
        if (retry > 0) {
            retry--;
            return false;
        }
        if (!pick()) {
            retry = 10;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return bot.getTarget() == null && !bot.isPassenger();
    }

    @Override
    public void start() {
        go();
    }

    @Override
    public void stop() {
        dest = null;
        halt = 0;
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % 20 == 0) {
            stuck = bot.position().distanceToSqr(lastPos) < 0.6 ? stuck + 1 : 0;
            lastPos = bot.position();
        }
        if (marching()) {
            march();
            return;
        }
        boolean arrived = dest == null || bot.position().distanceToSqr(dest) < 2.5;
        if (arrived || bot.getNavigation().isDone() || stuck >= 2 || ticks > 500) {
            if (retry > 0) {
                retry--;
                return;
            }
            if (pick()) go();
            else retry = 10;
        }
    }

    private void go() {
        ticks = 0;
        stuck = 0;
        lastPos = bot.position();
        if (!bot.getNavigation().moveTo(dest.x, dest.y, dest.z, BotEntity.SPEED_WALK)) retry = 5;
    }

    private boolean marching() {
        return bot.battle != null && !bot.operator && bot.battle.contains(bot.position());
    }

    /** the next waypoint: straight on with some wandering, turning back towards the post near the edge of the area */
    private boolean pick() {
        if (marching()) return pickRoad();
        if (bot.lastKnownEnemyPos != null && !bot.operator) {
            dest = bot.lastKnownEnemyPos;
            bot.lastKnownEnemyPos = null;
            return true;
        }
        RandomSource r = bot.getRandom();
        BlockPos home = bot.home != null ? bot.home : bot.blockPosition();
        Vec3 center = Vec3.atBottomCenterOf(home);
        double leash = bot.operator ? BotEntity.OPERATOR_LEASH * 0.7 : bot.arenaPos != null ? 22 : bot.spawnerPos != null ? 36 : 48;
        double fromHome = Math.sqrt(bot.position().distanceToSqr(center.x, bot.getY(), center.z));
        for (int i = 0; i < 10; i++) {
            float dir;
            if (fromHome > leash * 0.7) {
                Vec3 back = center.subtract(bot.position());
                dir = (float) (Mth.atan2(back.z, back.x) * Mth.RAD_TO_DEG) - 90f + (r.nextFloat() - 0.5f) * 120f;
            } else if (r.nextInt(4) == 0) {
                dir = r.nextFloat() * 360f;
            } else {
                dir = bot.getYRot() + (r.nextFloat() - 0.5f) * 150f;
            }
            double dist = 8 + r.nextDouble() * 14;
            double a = dir * Mth.DEG_TO_RAD;
            Vec3 p = bot.position().add(-Math.sin(a) * dist, 0, Math.cos(a) * dist);
            if (Math.sqrt(p.distanceToSqr(center.x, p.y, center.z)) > leash) continue;
            BlockPos g = CoverFinder.ground(bot.level(), BlockPos.containing(p));
            if (g == null) continue;
            dest = Vec3.atBottomCenterOf(g);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------ the march
    /** back on the way: the closest point of the route that is not behind the last one reached */
    private boolean pickRoad() {
        bot.lastKnownEnemyPos = null;
        if (route == null || !bot.battle.equals(routeMap) || routeLane != bot.lane) {
            route = bot.battle.route(bot.getBotTeam(), bot.lane, 10);
            routeMap = bot.battle;
            routeLane = bot.lane;
        }
        bot.routeIdx = BattleMap.resume(route, bot.position(), Math.min(route.size() - 1, bot.routeIdx - 1));
        dest = point(bot.routeIdx);
        return true;
    }

    /** a route point, a little to the side for each soldier so a column does not walk in single file */
    private Vec3 point(int i) {
        Vec3 p = route.get(Mth.clamp(i, 0, route.size() - 1));
        long h = bot.getUUID().getLeastSignificantBits();
        double ox = ((h & 255) / 255.0 - 0.5) * 2.2, oz = ((h >> 8 & 255) / 255.0 - 0.5) * 2.2;
        return new Vec3(p.x + ox, p.y, p.z + oz);
    }

    private void march() {
        if (route == null || dest == null) {
            if (pickRoad()) go();
            return;
        }
        RandomSource r = bot.getRandom();
        if (halt > 0) {
            // a halt at the crossroads (the look goals have them look down the roads meanwhile), then march on
            if (--halt == 0) go();
            return;
        }
        double dx = bot.getX() - dest.x, dz = bot.getZ() - dest.z;
        if (dx * dx + dz * dz < 2.5) {
            int last = route.size() - 1;
            if (bot.routeIdx >= last) {
                // at the enemy base: stand guard, now and then moving about the last stretch of road
                bot.routeIdx = last - r.nextInt(4);
                dest = point(bot.routeIdx);
                bot.getNavigation().stop();
                halt = 100 + r.nextInt(140);
                return;
            }
            Vec3 reached = route.get(bot.routeIdx);
            bot.routeIdx++;
            dest = point(bot.routeIdx);
            if (bot.battle.junction(reached)) {
                bot.getNavigation().stop();
                halt = 30 + r.nextInt(60);
                return;
            }
            go();
        } else if (bot.getNavigation().isDone() && ticks > 10 || stuck >= 2 || ticks > 300) {
            // blocked on the road (a wreck, a crowd): try the next point along
            if (stuck >= 2 && bot.routeIdx < route.size() - 1) {
                bot.routeIdx++;
                dest = point(bot.routeIdx);
            }
            go();
        }
    }
}
