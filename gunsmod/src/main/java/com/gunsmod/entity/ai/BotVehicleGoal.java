package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotRideable;
import java.util.EnumSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Soldiers who see a free vehicle of their own team (jeep, tank, helicopter) get in and keep patrolling with it.
 * They take the wheel first, and always when a friendly player is waiting in the gunner seat of a jeep.
 */
public class BotVehicleGoal extends Goal {
    private final BotEntity bot;
    private Entity vehicle;
    private int seat;
    private int ticks;

    public BotVehicleGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (bot.isPassenger() || bot.vehicleCooldown > 0 || bot.operator) return false;
        bot.vehicleCooldown = 40 + bot.getRandom().nextInt(40);
        if (bot.getTarget() != null && bot.distanceTo(bot.getTarget()) < 14) return false;
        Entity best = null;
        int bestSeat = -1;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : bot.level().getEntities(bot, bot.getBoundingBox().inflate(28), e -> e instanceof BotRideable && e.isAlive())) {
            BotRideable v = (BotRideable) e;
            if (v.getBotTeam() != bot.getBotTeam() || !v.fitForBots()) continue;
            int s = v.seatForBot(bot);
            if (s < 0) continue;
            double score = bot.distanceToSqr(e);
            if (s == 0 && e.getPassengers().stream().anyMatch(p -> p instanceof Player)) score -= 5000;
            if (score < bestScore) {
                bestScore = score;
                best = e;
                bestSeat = s;
            }
        }
        if (best == null) return false;
        vehicle = best;
        seat = bestSeat;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return vehicle != null && vehicle.isAlive() && !bot.isPassenger() && ticks < 200 && ((BotRideable) vehicle).seatFree(seat);
    }

    @Override
    public void start() {
        ticks = 0;
        bot.setCrouch(false);
    }

    @Override
    public void stop() {
        vehicle = null;
        bot.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % 10 == 1) bot.getNavigation().moveTo(vehicle, BotEntity.SPEED_JOG);
        bot.getLookControl().setLookAt(vehicle, 30, 30);
        double reach = 2.2 + vehicle.getBbWidth() * 0.5;
        if (bot.distanceToSqr(vehicle) < reach * reach) {
            ((BotRideable) vehicle).board(bot, seat);
            vehicle = null;
        }
    }
}
