package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Patrol around the home / last known enemy position when idle. */
public class BotPatrolGoal extends Goal {
    private final BotEntity bot;
    private Vec3 dest;

    public BotPatrolGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (bot.getTarget() != null || bot.isPassenger() || bot.getOrder() == BotEntity.ORDER_FOLLOW) return false;
        if (bot.lastKnownEnemyPos != null) {
            dest = bot.lastKnownEnemyPos;
            bot.lastKnownEnemyPos = null;
            return true;
        }
        if (bot.getOrder() == BotEntity.ORDER_HOLD || bot.getRandom().nextInt(120) != 0) return false;
        BlockPos center = bot.home != null ? bot.home : bot.blockPosition();
        int r = bot.arenaPos != null ? 20 : 10;
        BlockPos p = CoverFinder.ground(bot.level(), center.offset(bot.getRandom().nextInt(r * 2 + 1) - r, 0, bot.getRandom().nextInt(r * 2 + 1) - r));
        if (p == null) return false;
        dest = Vec3.atBottomCenterOf(p);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return bot.getTarget() == null && !bot.getNavigation().isDone();
    }

    @Override
    public void start() {
        bot.getNavigation().moveTo(dest.x, dest.y, dest.z, 0.9);
    }
}
