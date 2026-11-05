package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.VehicleEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Picks the most threatening enemy it can see every half second: only what is in front of its eyes (see {@link Sight}),
 * or whoever has just shot at it.
 */
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
            if (VehicleEntity.shieldOf(e) != null) continue;   // the vehicle itself is the target
            if (e instanceof com.gunsmod.entity.JetEntity jet && jet.isAirborne()) continue;   // rifles cannot catch a jet
            if (e instanceof com.gunsmod.entity.JetEntity j && j.type.rpgOnly()) continue;   // only an RPG has a chance (BotSkyGoal)
            boolean see = Sight.soldier(bot, e) && bot.canSeeTarget(e);
            double d = bot.distanceTo(e);
            if (!see) {
                // not in sight (behind it, or behind a wall): footsteps only make it turn its head
                if (current == null && d < Sight.hearing(e)) bot.hear(e.position());
                continue;
            }
            double score = d + (see ? 0 : 25) - (e == bot.lastAttacker ? 15 : 0) - (e == current ? 6 : 0) - (e.getId() == bot.focusTarget ? 200 : 0);
            // soldiers go for enemy players first; rifles are no use against armour
            if (e instanceof Player) score -= 12;
            if (e instanceof VehicleEntity v && v.isArmored() && !bot.hasAntiArmor()) score += 45;
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
