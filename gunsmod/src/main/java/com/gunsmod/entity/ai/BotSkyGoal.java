package com.gunsmod.entity.ai;

import com.gunsmod.entity.BomberEntity;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An enemy stealth bomber overhead: nothing finds it but the eye, nothing but an RPG can touch it. A soldier with no
 * close fight on his hands pulls out an RPG, leads the bomber and fires, again and again, trying his luck. His own gun
 * comes back when the bomber is gone or an enemy comes close.
 */
public class BotSkyGoal extends Goal {
    /** how far off (horizontally) and how high a bomber can be and still be worth a rocket */
    public static final double RANGE = 160, MAX_UP = 150;
    private final BotEntity bot;
    private int cooldown, lost, ticks;
    @Nullable private BomberEntity bomber;

    public BotSkyGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean busy() {
        LivingEntity t = bot.getTarget();
        return t != null && t.isAlive() && bot.distanceTo(t) < 35 && bot.canSeeTarget(t);
    }

    private boolean inReach(BomberEntity b) {
        if (!b.isAlive() || b.isCrashing() || BotTeam.friendly(bot, b)) return false;
        double dx = b.getX() - bot.getX(), dz = b.getZ() - bot.getZ(), dy = b.getY() - bot.getY();
        return dx * dx + dz * dz < RANGE * RANGE && dy > 8 && dy < MAX_UP;
    }

    private boolean clearSky(BomberEntity b) {
        Vec3 eye = bot.getEyePosition();
        Vec3 c = b.position().add(0, BomberEntity.MID_Y, 0);
        return bot.level().clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bot)).getType() == HitResult.Type.MISS;
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = 20 + bot.getRandom().nextInt(10);
        if (bot.isPassenger() || bot.operator || busy()) return false;
        BomberEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (BomberEntity b : BomberEntity.active(bot.level())) {
            if (!inReach(b)) continue;
            double d = b.distanceToSqr(bot);
            if (d < bestD && clearSky(b)) {
                bestD = d;
                best = b;
            }
        }
        bomber = best;
        return best != null;
    }

    @Override
    public boolean canContinueToUse() {
        return bomber != null && inReach(bomber) && lost < 60 && ticks < 1200 && !busy() && !bot.isPassenger();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        lost = 0;
        ticks = 0;
        bot.getNavigation().stop();
        bot.setCrouch(false);
        bot.armLauncher();
    }

    @Override
    public void stop() {
        bot.setAiming(false);
        bot.restoreGun();
        bomber = null;
    }

    /** rocket flight time to a distance: it leaves at 2.6 blocks a tick and speeds up to 4 */
    private static int flightTicks(double dist) {
        double s = 0, v = 2.6;
        int n = 0;
        while (s < dist && n < 200) {
            n++;
            if (n > 4 && v < 4.0) v *= 1.035;
            s += v;
        }
        return n;
    }

    @Override
    public void tick() {
        if (bomber == null) return;
        ticks++;
        bot.getNavigation().stop();
        bot.getMoveControl().strafe(0, 0);
        if (ticks % 10 == 0) lost = clearSky(bomber) ? 0 : lost + 10;
        // lead the bomber: where it will be when the rocket gets there, a little high for the drop
        Vec3 eye = bot.getEyePosition();
        Vec3 c = bomber.position().add(0, BomberEntity.MID_Y, 0);
        Vec3 aim = c;
        int t = 0;
        for (int i = 0; i < 3; i++) {
            t = flightTicks(aim.distanceTo(eye));
            aim = c.add(bomber.velocity.scale(t));
        }
        aim = aim.add(0, 0.5 * 0.006 * t * t, 0);
        // the head comes round at a human speed; looking up is quick
        bot.getLookControl().setLookAt(aim.x, aim.y, aim.z, 14f, 90f);
        if (lost == 0) bot.tryShootAt(aim, bomber);
    }
}
