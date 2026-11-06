package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import com.gunsmod.game.MatchLogic;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * In a battle, a soldier with no order from a squad leader goes for his side's objective: the capture point it needs,
 * the enemy flag (or home with it), the target to attack or the one to guard, the convoy to escort or to ambush.
 * Once there it stays round the objective, moving about a little; a fight takes over whenever an enemy shows.
 */
public class BotObjectiveGoal extends Goal {
    private final BotEntity bot;
    @Nullable private MatchLogic.Goal goal;
    private int think, repath, wander;
    @Nullable private Vec3 spot;

    public BotObjectiveGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean free() {
        if (bot.order != Squad.Order.NONE || bot.isPassenger() || bot.operator) return false;
        LivingEntity t = bot.getTarget();
        // a fight close by comes first; a far one does not keep it from the objective
        return t == null || !t.isAlive() || bot.distanceTo(t) > 36;
    }

    @Override
    public boolean canUse() {
        if (--think > 0) return false;
        think = 20 + bot.getRandom().nextInt(10);
        if (!free()) return false;
        goal = MatchLogic.goalFor(bot, bot.getBotTeam());
        return goal != null;
    }

    @Override
    public boolean canContinueToUse() {
        return goal != null && free();
    }

    @Override
    public void start() {
        repath = 0;
        spot = null;
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
        goal = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (--think <= 0) {
            think = 20;
            goal = MatchLogic.goalFor(bot, bot.getBotTeam());
            if (goal == null) return;
            if (goal.chase() instanceof LivingEntity le && bot.isEnemy(le) && bot.getTarget() == null && bot.hasLineOfSight(le)) bot.setTarget(le);
        }
        if (goal == null) return;
        Vec3 at = goal.at();
        double d = Math.sqrt(bot.distanceToSqr(at.x, bot.getY(), at.z));
        if (d > goal.radius()) {
            spot = null;
            if (--repath > 0) return;
            repath = 15;
            double speed = d > 30 ? BotEntity.SPEED_RUN : d > 8 ? BotEntity.SPEED_JOG : BotEntity.SPEED_WALK;
            bot.getNavigation().moveTo(at.x, at.y, at.z, speed);
            return;
        }
        if (!goal.guard()) {
            bot.getNavigation().stop();
            return;
        }
        // holding it: a few steps about the place now and then, looking out
        if (spot == null || --wander <= 0) {
            wander = 60 + bot.getRandom().nextInt(80);
            double a = bot.getRandom().nextDouble() * Math.PI * 2, r = bot.getRandom().nextDouble() * goal.radius() * 0.8;
            spot = at.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            bot.getNavigation().moveTo(spot.x, spot.y, spot.z, BotEntity.SPEED_WALK);
        }
    }
}
