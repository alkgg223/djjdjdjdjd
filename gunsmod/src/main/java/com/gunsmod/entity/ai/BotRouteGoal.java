package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A soldier given a route with the route planner walks it point by point, taking a branch at random where it splits.
 * Fights come first; afterwards he carries on from where he was. Long legs are walked in steps the path finder can
 * manage. At the end of the route he stays and guards the last point.
 */
public class BotRouteGoal extends Goal {
    private static final double STEP = 24;
    private final BotEntity bot;
    private int ticks, stuck;
    private Vec3 lastPos = Vec3.ZERO;

    public BotRouteGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean onRoute() {
        return bot.waypoints != null && bot.waypoints.active() && bot.getTarget() == null && !bot.isPassenger();
    }

    @Override
    public boolean canUse() {
        return onRoute();
    }

    @Override
    public boolean canContinueToUse() {
        return onRoute();
    }

    @Override
    public void start() {
        go();
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
    }

    @Override
    public void tick() {
        Waypoints w = bot.waypoints;
        Vec3 t = w.target();
        if (t == null) return;
        ticks++;
        if (ticks % 20 == 0) {
            stuck = bot.position().distanceToSqr(lastPos) < 0.6 ? stuck + 1 : 0;
            lastPos = bot.position();
        }
        double dx = t.x - bot.getX(), dz = t.z - bot.getZ();
        if (dx * dx + dz * dz < 2.2 * 2.2) {
            BlockPos reached = w.points.get(w.at);
            if (!w.advance(bot.getRandom())) {
                // end of the route: this is his post now
                bot.home = reached;
                bot.getNavigation().stop();
                return;
            }
            go();
        } else if (bot.getNavigation().isDone() || ticks > 300) {
            go();
        } else if (stuck >= 4) {
            // cannot get there (water, a wall): on to the next point
            if (w.advance(bot.getRandom())) go();
            stuck = 0;
        }
    }

    private void go() {
        ticks = 0;
        Vec3 t = bot.waypoints.target();
        if (t == null) return;
        Vec3 d = t.subtract(bot.position());
        double len = Math.sqrt(d.x * d.x + d.z * d.z);
        if (len > STEP + 4) {
            // a far point: the next stretch towards it, on the ground
            double x = bot.getX() + d.x / len * STEP, z = bot.getZ() + d.z / len * STEP;
            int y = bot.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            t = new Vec3(x, y, z);
        }
        bot.getNavigation().moveTo(t.x, t.y, t.z, BotEntity.SPEED_WALK);
    }
}
