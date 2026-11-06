package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Now and then a soldier looks at a friendly player close by. Enemies are never glanced at: they have to be seen. */
public class BotGlanceGoal extends Goal {
    private final BotEntity bot;
    private Player at;
    private int ticks;

    public BotGlanceGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (bot.getTarget() != null || bot.getRandom().nextFloat() >= 0.02f) return false;
        Player p = bot.level().getNearestPlayer(bot, 8);
        if (p == null || p.isSpectator() || bot.isEnemy(p) || !bot.getSensing().hasLineOfSight(p)) return false;
        at = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return at != null && at.isAlive() && ticks > 0 && bot.distanceToSqr(at) < 100 && bot.getTarget() == null;
    }

    @Override
    public void start() {
        ticks = 40 + bot.getRandom().nextInt(40);
    }

    @Override
    public void stop() {
        at = null;
    }

    @Override
    public void tick() {
        ticks--;
        bot.getLookControl().setLookAt(at.getX(), at.getEyeY(), at.getZ());
    }
}
