package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

/** Picks the most threatening visible enemy every half second. */
public class BotTargetGoal extends Goal {
    private final BotEntity bot;
    private int cooldown;
    private LivingEntity found;

    public BotTargetGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = 10;
        double range = bot.getAttributeValue(Attributes.FOLLOW_RANGE);
        LivingEntity current = bot.getTarget();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : bot.level().getEntitiesOfClass(LivingEntity.class, bot.getBoundingBox().inflate(range, 16, range), bot::isEnemy)) {
            boolean see = bot.canSeeTarget(e);
            double d = bot.distanceTo(e);
            if (!see && d > 12 && e != bot.lastAttacker) continue;
            double score = d + (see ? 0 : 25) - (e == bot.lastAttacker ? 15 : 0) - (e == current ? 6 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best != null && best != current) {
            found = best;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        bot.setTarget(found);
    }
}
