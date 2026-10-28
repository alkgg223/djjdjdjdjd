package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.JeepEntity;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Soldiers who see a free jeep of their own team get in: they take the wheel first (always when a friendly player is
 * waiting in the gunner seat), otherwise the turret.
 */
public class BotVehicleGoal extends Goal {
    private final BotEntity bot;
    private JeepEntity jeep;
    private int seat;
    private int ticks;

    public BotVehicleGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (bot.isPassenger() || bot.vehicleCooldown > 0 || bot.getOrder() == BotEntity.ORDER_HOLD) return false;
        bot.vehicleCooldown = 40 + bot.getRandom().nextInt(40);
        if (bot.getTarget() != null && bot.distanceTo(bot.getTarget()) < 14) return false;
        List<JeepEntity> jeeps = bot.level().getEntitiesOfClass(JeepEntity.class, bot.getBoundingBox().inflate(28),
                j -> j.isAlive() && j.getBotTeam() == bot.getBotTeam() && j.getHealth() > j.getMaxHealth() * 0.3f);
        JeepEntity best = null;
        int bestSeat = -1;
        double bestScore = Double.MAX_VALUE;
        for (JeepEntity j : jeeps) {
            int s = j.seatForBot(bot);
            if (s < 0) continue;
            double score = bot.distanceToSqr(j);
            boolean playerInside = j.getPassengers().stream().anyMatch(p -> p instanceof Player);
            if (playerInside && s == JeepEntity.SEAT_DRIVER) score -= 5000;
            // following soldiers join their owner's ride
            Player owner = bot.getOwnerPlayer();
            if (owner != null && owner.getVehicle() == j) score -= 3000;
            else if (bot.getOrder() == BotEntity.ORDER_FOLLOW && !playerInside) continue;
            if (score < bestScore) {
                bestScore = score;
                best = j;
                bestSeat = s;
            }
        }
        if (best == null) return false;
        jeep = best;
        seat = bestSeat;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return jeep != null && jeep.isAlive() && !bot.isPassenger() && ticks < 200 && jeep.seatFree(seat);
    }

    @Override
    public void start() {
        ticks = 0;
        bot.setCrouch(false);
    }

    @Override
    public void stop() {
        jeep = null;
        bot.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % 10 == 1) {
            bot.getNavigation().moveTo(jeep, BotEntity.SPEED_JOG);
        }
        bot.getLookControl().setLookAt(jeep, 30, 30);
        if (bot.distanceToSqr(jeep) < 3.2 * 3.2) {
            jeep.board(bot, seat);
            jeep = null;
        }
    }
}
