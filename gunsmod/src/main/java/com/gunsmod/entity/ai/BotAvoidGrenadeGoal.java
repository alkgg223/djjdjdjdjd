package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.GrenadeEntity;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Run away from live frag grenades. */
public class BotAvoidGrenadeGoal extends Goal {
    private final BotEntity bot;
    private Vec3 dest;
    private int check;

    public BotAvoidGrenadeGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (bot.isPassenger() || --check > 0) return false;
        check = 4;
        List<GrenadeEntity> nades = bot.level().getEntitiesOfClass(GrenadeEntity.class, bot.getBoundingBox().inflate(5.5), g -> !g.isSmoke());
        if (nades.isEmpty()) return false;
        GrenadeEntity g = nades.get(0);
        Vec3 away = bot.position().subtract(g.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) away = new Vec3(bot.getRandom().nextDouble() - 0.5, 0, bot.getRandom().nextDouble() - 0.5);
        Vec3 target = bot.position().add(away.normalize().scale(8));
        BlockPos p = CoverFinder.ground(bot.level(), BlockPos.containing(target));
        if (p == null) return false;
        dest = Vec3.atBottomCenterOf(p);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !bot.getNavigation().isDone();
    }

    @Override
    public void start() {
        bot.setCrouch(false);
        bot.setSprinting(true);
        bot.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.5);
    }

    @Override
    public void stop() {
        bot.setSprinting(false);
    }
}
