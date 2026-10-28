package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Follow the owner (order FOLLOW) or walk back to the post (order HOLD). */
public class BotFollowGoal extends Goal {
    private final BotEntity bot;
    private int repath;

    public BotFollowGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (bot.getTarget() != null || bot.isPassenger()) return false;
        if (bot.getOrder() == BotEntity.ORDER_FOLLOW) {
            Player p = bot.getOwnerPlayer();
            return p != null && !p.isSpectator() && bot.distanceToSqr(p) > 25;
        }
        if (bot.getOrder() == BotEntity.ORDER_HOLD && bot.home != null) {
            return bot.blockPosition().distSqr(bot.home) > 4;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (bot.getTarget() != null || bot.isPassenger()) return false;
        if (bot.getOrder() == BotEntity.ORDER_FOLLOW) {
            Player p = bot.getOwnerPlayer();
            return p != null && bot.distanceToSqr(p) > 9;
        }
        return bot.home != null && bot.blockPosition().distSqr(bot.home) > 2 && !bot.getNavigation().isDone();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
        bot.setSprinting(false);
    }

    @Override
    public void tick() {
        if (--repath > 0) return;
        repath = 10;
        if (bot.getOrder() == BotEntity.ORDER_FOLLOW) {
            Player p = bot.getOwnerPlayer();
            if (p == null) return;
            double d2 = bot.distanceToSqr(p);
            if (d2 > 48 * 48 && p.onGround()) {
                BlockPos t = CoverFinder.ground(bot.level(), p.blockPosition().offset(bot.getRandom().nextInt(5) - 2, 0, bot.getRandom().nextInt(5) - 2));
                if (t != null) bot.teleportTo(t.getX() + 0.5, t.getY(), t.getZ() + 0.5);
                return;
            }
            bot.setSprinting(d2 > 144);
            bot.getNavigation().moveTo(p, d2 > 144 ? 1.35 : 1.05);
        } else if (bot.home != null) {
            bot.getNavigation().moveTo(bot.home.getX() + 0.5, bot.home.getY(), bot.home.getZ() + 0.5, 1.0);
        }
    }
}
