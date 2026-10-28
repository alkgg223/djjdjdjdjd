package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Soldiers never stand around: with no enemy they keep exploring the area around their post, walking from one
 * waypoint straight on to the next. The last known enemy position is checked first.
 */
public class BotPatrolGoal extends Goal {
    private final BotEntity bot;
    private Vec3 dest;
    private int ticks, stuck, retry;
    private Vec3 lastPos = Vec3.ZERO;

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
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % 20 == 0) {
            stuck = bot.position().distanceToSqr(lastPos) < 0.6 ? stuck + 1 : 0;
            lastPos = bot.position();
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

    /** the next waypoint: straight on with some wandering, turning back towards the post near the edge of the area */
    private boolean pick() {
        if (bot.lastKnownEnemyPos != null) {
            dest = bot.lastKnownEnemyPos;
            bot.lastKnownEnemyPos = null;
            return true;
        }
        RandomSource r = bot.getRandom();
        BlockPos home = bot.home != null ? bot.home : bot.blockPosition();
        Vec3 center = Vec3.atBottomCenterOf(home);
        double leash = bot.arenaPos != null ? 22 : bot.spawnerPos != null ? 36 : 48;
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
}
