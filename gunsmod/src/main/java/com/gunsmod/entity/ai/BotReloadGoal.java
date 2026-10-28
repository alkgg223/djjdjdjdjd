package com.gunsmod.entity.ai;

import com.gunsmod.block.LauncherBlockEntity;
import com.gunsmod.entity.BotEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Walks a soldier with a remote to an empty linked launch station and loads a missile from its rack. */
public class BotReloadGoal extends Goal {
    private final BotEntity bot;
    private int ticks;
    private int repath;

    public BotReloadGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean threatened() {
        LivingEntity t = bot.getTarget();
        return t != null && t.distanceToSqr(bot) < 20 * 20 && bot.canSeeTarget(t);
    }

    @Override
    public boolean canUse() {
        return bot.artillery.reloadTarget != null && !bot.isPassenger() && !threatened();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && ticks < 600;
    }

    @Override
    public void start() {
        ticks = 0;
        repath = 0;
        bot.setCrouch(false);
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
        if (ticks >= 600) bot.artillery.reloadTarget = null;     // could not get there
    }

    @Override
    public void tick() {
        ticks++;
        BlockPos p = bot.artillery.reloadTarget;
        if (p == null) return;
        bot.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5);
        double d2 = p.distToCenterSqr(bot.getX(), bot.getY(), bot.getZ());
        if (d2 < 4.2 * 4.2) {
            bot.getNavigation().stop();
            if (bot.level().getBlockEntity(p) instanceof LauncherBlockEntity be) bot.artillery.loadStation(be);
            else bot.artillery.reloadTarget = null;
            return;
        }
        if (--repath <= 0) {
            repath = 20;
            bot.getNavigation().moveTo(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, BotEntity.SPEED_JOG);
        }
    }
}
