package com.gunsmod.entity.ai;

import com.gunsmod.entity.Aircraft;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.DroneEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.VehicleEntity;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A drone operator. With FPV drones in his pack he looks for an enemy vehicle (best), a bunch of enemies, or the
 * enemy he is fighting at a distance; he crouches where he is, sends the quad up and flies it in on his goggles,
 * keeping still until it hits (or is shot down). With a Shahed he picks a vehicle or a group far off, launches it and
 * goes back to the fight; it finds its own way.
 */
public class BotDroneGoal extends Goal {
    public static final double FPV_MIN = 28, FPV_MAX = 180, SHAHED_MIN = 90, SHAHED_MAX = 420;
    private final BotEntity bot;
    private int cooldown = 200, flying;
    @Nullable private DroneEntity drone;

    public BotDroneGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private boolean threatened() {
        LivingEntity t = bot.getTarget();
        if (t != null && t.isAlive() && bot.distanceTo(t) < 22 && bot.canSeeTarget(t)) return true;
        return bot.level().getGameTime() - bot.lastHurtTime < 40;
    }

    private boolean enemyVehicle(Entity e) {
        if (!e.isAlive() || BotTeam.of(e) == null || BotTeam.of(e) == bot.getBotTeam()) return false;
        if (e instanceof JeepEntity j) return !j.getPassengers().isEmpty();
        return e instanceof VehicleEntity v && !(v instanceof DroneEntity) && v.isManned() && !(v instanceof Aircraft a && a.isAirborne());
    }

    @Nullable
    private Entity vehicleTarget(double min, double max) {
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        AABB box = bot.getBoundingBox().inflate(max, 60, max);
        for (LivingEntity e : bot.level().getEntitiesOfClass(LivingEntity.class, box, this::enemyVehicle)) {
            double d = e.distanceTo(bot);
            if (d < min || d > max) continue;
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = 40 + bot.getRandom().nextInt(20);
        if ((bot.drones <= 0 && bot.shaheds <= 0) || bot.isPassenger() || bot.operator || threatened()) return false;
        if (bot.shaheds > 0 && bot.getRandom().nextInt(3) == 0) {
            Entity v = vehicleTarget(SHAHED_MIN, SHAHED_MAX);
            Vec3 at = v != null ? v.position() : GroupFinder.find(bot.level(), bot, bot.getBotTeam(), bot.position(), SHAHED_MAX);
            if (at != null && at.distanceTo(bot.position()) > SHAHED_MIN) {
                bot.shaheds--;
                Vec3 look = at.subtract(bot.position()).multiply(1, 0, 1).normalize();
                DroneEntity.launch(bot.level(), DroneEntity.Kind.SHAHED, bot.getBotTeam(), bot.position().add(look.scale(1.8)).add(0, 1.4, 0), null, at, null);
                cooldown = 1200 + bot.getRandom().nextInt(1200);
                return false;
            }
        }
        if (bot.drones <= 0) return false;
        Entity chase = vehicleTarget(FPV_MIN, FPV_MAX);
        Vec3 point = null;
        if (chase == null) {
            point = GroupFinder.find(bot.level(), bot, bot.getBotTeam(), bot.position(), FPV_MAX * 0.8);
            if (point != null && point.distanceTo(bot.position()) < FPV_MIN) point = null;
        }
        if (chase == null && point == null) {
            LivingEntity t = bot.getTarget();
            if (t != null && t.isAlive() && bot.isEnemy(t) && bot.distanceTo(t) > FPV_MIN && bot.distanceTo(t) < FPV_MAX) chase = t;
        }
        if (chase == null && point == null) return false;
        bot.drones--;
        Vec3 eye = bot.getEyePosition();
        Vec3 to = (chase != null ? chase.position() : point).subtract(bot.position()).multiply(1, 0, 1).normalize();
        drone = DroneEntity.launch(bot.level(), DroneEntity.Kind.FPV, bot.getBotTeam(), eye.add(to.scale(1.0)), chase, point, bot.getUUID());
        flying = 0;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return drone != null && drone.isAlive() && !drone.isRemoved() && flying < 1500 && !bot.isPassenger() && !threatened();
    }

    @Override
    public void start() {
        bot.getNavigation().stop();
        bot.setAiming(false);
    }

    @Override
    public void tick() {
        flying++;
        bot.getNavigation().stop();
        bot.setCrouch(true);
        if (drone != null) bot.getLookControl().setLookAt(drone.getX(), drone.getY(), drone.getZ(), 10f, 10f);
    }

    @Override
    public void stop() {
        bot.setCrouch(false);
        drone = null;
        cooldown = 500 + bot.getRandom().nextInt(500);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
