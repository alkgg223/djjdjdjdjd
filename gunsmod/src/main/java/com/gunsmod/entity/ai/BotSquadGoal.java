package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Carrying out the squad leader's order when there is no fight on: keeping its place in the formation behind the
 * leader, holding a spot, moving on a point and then holding round it, walking a patrol, falling back home at a run,
 * keeping close round someone it guards.
 */
public class BotSquadGoal extends Goal {
    private final BotEntity bot;
    private int repath, slotCheck;
    private int index, count = 1;
    private boolean patrolOut = true;

    public BotSquadGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean active() {
        if (bot.order == Squad.Order.NONE || bot.isPassenger()) return false;
        return bot.getTarget() == null || bot.order == Squad.Order.RETREAT;
    }

    @Override
    public boolean canUse() {
        return active();
    }

    @Override
    public boolean canContinueToUse() {
        return active();
    }

    @Override
    public void start() {
        repath = 0;
        slotCheck = 0;
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private void go(Vec3 p, double far) {
        double d = bot.position().distanceTo(p);
        if (d < 1.4) {
            bot.getNavigation().stop();
            return;
        }
        if (--repath > 0) return;
        repath = 10;
        double speed = d > far ? BotEntity.SPEED_RUN : d > 6 ? BotEntity.SPEED_JOG : BotEntity.SPEED_WALK;
        if (bot.order == Squad.Order.RETREAT) speed = BotEntity.SPEED_RUN;
        bot.getNavigation().moveTo(p.x, p.y, p.z, speed);
    }

    @Override
    public void tick() {
        if (--slotCheck <= 0) {
            slotCheck = 40;
            var mates = Squad.mates(bot);
            index = Math.max(0, mates.indexOf(bot));
            count = Math.max(1, mates.size());
        }
        switch (bot.order) {
            case FOLLOW -> {
                Player leader = bot.squadLeader == null ? null : bot.level().getPlayerByUUID(bot.squadLeader);
                if (leader == null || !leader.isAlive()) {
                    bot.giveOrder(bot.squadLeader, Squad.Order.HOLD, bot.position(), -1, index, count);
                    return;
                }
                Vec3 at = leader.position().add(Squad.slot(bot.formation, index, count, leader.getYRot()));
                go(at, 12);
                if (bot.getNavigation().isDone()) bot.getLookControl().setLookAt(at.x + (at.x - leader.getX()), bot.getEyeY(), at.z + (at.z - leader.getZ()), 10f, 10f);
            }
            case HOLD -> go(bot.orderPos, 8);
            case ATTACK -> {
                Vec3 at = bot.orderPos.add(Squad.slot(bot.formation, index, count, bot.orderFacing));
                go(at, 10);
                if (bot.position().distanceTo(bot.orderPos) < 6) bot.order = Squad.Order.DEFEND;
            }
            case DEFEND -> go(bot.orderPos.add(Squad.slot(Squad.Formation.CIRCLE, index, count, bot.orderFacing)), 10);
            case PATROL -> {
                Vec3 a = bot.patrolFrom != null ? bot.patrolFrom : bot.orderPos;
                Vec3 target = (patrolOut ? bot.orderPos : a).add(Squad.slot(Squad.Formation.COLUMN, index, count, bot.orderFacing).scale(0.6));
                go(target, 30);
                if (bot.position().distanceTo(target) < 2.5) patrolOut = !patrolOut;
            }
            case RETREAT -> {
                go(bot.orderPos, 0);
                if (bot.position().distanceTo(bot.orderPos) < 3) bot.giveOrder(bot.squadLeader, Squad.Order.HOLD, bot.position(), -1, index, count);
            }
            case PROTECT -> {
                Entity e = bot.level().getEntity(bot.orderEntity);
                if (e == null || !e.isAlive()) {
                    bot.giveOrder(bot.squadLeader, Squad.Order.FOLLOW, bot.position(), -1, index, count);
                    return;
                }
                Squad.Formation f = e instanceof com.gunsmod.entity.VehicleEntity || e instanceof com.gunsmod.entity.JeepEntity ? Squad.Formation.ESCORT : Squad.Formation.CIRCLE;
                go(e.position().add(Squad.slot(f, index, count, e.getYRot())), 10);
            }
            default -> { }
        }
    }
}
