package com.gunsmod.entity.ai;

import com.gunsmod.entity.BotEntity;
import com.gunsmod.gun.GunType;
import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Soldier combat behaviour: keeps a weapon-appropriate distance, strafes, fires bursts, falls back to cover to
 * reload or when hurt, peeks out of cover, flanks a hidden enemy and throws frag / smoke grenades.
 * On an orchard battlefield every move stays on a road or by the roadside.
 */
public class BotCombatGoal extends Goal {
    private enum State { ENGAGE, MOVE_COVER, IN_COVER, PEEK, FLANK }
    /** how far from the middle of a road soldiers of an orchard battlefield go in a fight */
    private static final double ROADSIDE = 10;

    private final BotEntity bot;
    private State state = State.ENGAGE;
    private int stateTicks;
    private int lostSight;
    private int strafeDir = 1;
    private int strafeTicks;
    private int coverCooldown;
    private int repath;
    private int engageLimit;
    private Vec3 coverPos;
    private Vec3 movePos;
    private Vec3 lastSeen;

    public BotCombatGoal(BotEntity bot) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = bot.getTarget();
        return t != null && t.isAlive() && !bot.isPassenger();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        state = State.ENGAGE;
        stateTicks = 0;
        lostSight = 0;
        engageLimit = 0;
        lastSeen = bot.getTarget() != null ? bot.getTarget().position() : null;
    }

    @Override
    public void stop() {
        bot.setCrouch(false);
        bot.setAiming(false);
        bot.getNavigation().stop();
        bot.getMoveControl().strafe(0, 0);
    }

    private void go(State s) {
        state = s;
        stateTicks = 0;
        repath = 0;
    }

    private boolean onField() {
        return bot.battle != null && bot.battle.contains(bot.position());
    }

    /** the spot, or the nearest one by the roadside on an orchard battlefield */
    private Vec3 roadside(Vec3 p) {
        return p != null && onField() ? bot.battle.keepNear(p, ROADSIDE) : p;
    }

    /** a cover or peek spot is only any use on the battlefield when it is by a road */
    private Vec3 byRoad(Vec3 p) {
        return p != null && onField() && bot.battle.keepNear(p, ROADSIDE) != p ? null : p;
    }

    private void moveTo(Vec3 p, double speed) {
        if (repath-- <= 0) {
            p = roadside(p);
            bot.getNavigation().moveTo(p.x, p.y, p.z, speed);
            repath = 10;
        }
    }

    @Override
    public void tick() {
        LivingEntity target = bot.getTarget();
        if (target == null) return;
        stateTicks++;
        if (coverCooldown > 0) coverCooldown--;
        GunType gun = bot.gunType();
        double dist = bot.distanceTo(target);
        boolean see = bot.canSeeTarget(target);
        if (see) {
            lostSight = 0;
            lastSeen = target.position();
            bot.lastKnownEnemyPos = lastSeen;
        } else {
            lostSight++;
        }
        Vec3 threatEye = see ? target.getEyePosition() : (lastSeen != null ? lastSeen.add(0, 1.6, 0) : target.getEyePosition());
        if (see) bot.getLookControl().setLookAt(target, 40f, 40f);
        else if (lastSeen != null) bot.getLookControl().setLookAt(lastSeen.x, lastSeen.y + 1.5, lastSeen.z, 30f, 30f);

        // no gun: melee rush
        if (gun == null) {
            bot.setCrouch(false);
            moveTo(target.position(), BotEntity.SPEED_RUN);
            if (dist < 2.2 && bot.getRandom().nextInt(10) == 0) {
                bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                bot.doHurtTarget(target);
            }
            return;
        }

        float minD = gun.preferredMin(), maxD = gun.preferredMax();
        boolean lowHealth = bot.getHealth() < bot.getMaxHealth() * 0.45f;
        boolean wantsCover = (bot.isReloadingGun() || bot.needsReload() || lowHealth || (bot.recentlyHurt(20) && bot.getRandom().nextInt(3) == 0))
                && coverCooldown <= 0 && dist > 4;

        switch (state) {
            case ENGAGE -> {
                bot.setCrouch(bot.isReloadingGun() || gun.category == GunType.Category.SNIPER && see && dist > 20);
                if (wantsCover) {
                    Vec3 c = byRoad(CoverFinder.findCover(bot, threatEye, 10));
                    coverCooldown = 40;
                    if (c != null) {
                        coverPos = c;
                        // pop smoke when badly hurt
                        if (lowHealth && bot.smokes > 0 && dist < 30 && bot.getRandom().nextInt(2) == 0) {
                            bot.throwGrenade(bot.position().add(target.position().subtract(bot.position()).normalize().scale(4)), true);
                        }
                        go(State.MOVE_COVER);
                        return;
                    }
                }
                if (bot.needsReload() && !bot.isReloadingGun()) bot.startBotReload();
                if (see) {
                    if (dist > maxD) {
                        moveTo(target.position(), dist > maxD * 1.6 ? BotEntity.SPEED_RUN : BotEntity.SPEED_WALK);
                        bot.getMoveControl().strafe(0, 0);
                    } else if (dist < minD) {
                        Vec3 away = bot.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(6);
                        moveTo(bot.position().add(away), BotEntity.SPEED_JOG);
                    } else {
                        bot.getNavigation().stop();
                        if (--strafeTicks <= 0) {
                            strafeTicks = 15 + bot.getRandom().nextInt(30);
                            strafeDir = bot.getRandom().nextBoolean() ? 1 : -1;
                            if (bot.getRandom().nextInt(4) == 0) strafeDir = 0;
                        }
                        float speed = gun.category == GunType.Category.SNIPER ? 0f : 0.45f;
                        bot.getMoveControl().strafe(0f, strafeDir * speed);
                    }
                    bot.tryShoot(target, true);
                    if (engageLimit > 0 && --engageLimit == 0 && coverPos != null && (lowHealth || bot.recentlyHurt(60))) {
                        go(State.MOVE_COVER);
                    }
                } else {
                    bot.getMoveControl().strafe(0, 0);
                    // enemy hid: nade it, then flank
                    if (lostSight == 30 && bot.grenades > 0 && lastSeen != null && dist > 6 && dist < 26 && bot.getRandom().nextInt(2) == 0) {
                        bot.throwGrenade(lastSeen, false);
                    }
                    if (lostSight > 50) {
                        Vec3 f = lastSeen == null ? null : CoverFinder.findFlank(bot, lastSeen, Mth.clamp(dist, 6, 18));
                        movePos = roadside(f != null ? f : lastSeen);
                        if (movePos != null) go(State.FLANK);
                    } else if (lastSeen != null && lostSight > 15) {
                        moveTo(lastSeen, BotEntity.SPEED_WALK);
                    }
                }
            }
            case MOVE_COVER -> {
                bot.setCrouch(false);
                moveTo(coverPos, BotEntity.SPEED_RUN);
                if (see && bot.getRandom().nextInt(3) == 0) bot.tryShoot(target, true); // suppressive fire
                if (bot.position().distanceToSqr(coverPos) < 1.4) {
                    bot.getNavigation().stop();
                    go(State.IN_COVER);
                } else if (stateTicks > 100 || (stateTicks > 20 && bot.getNavigation().isDone())) {
                    go(State.ENGAGE);
                }
            }
            case IN_COVER -> {
                bot.setCrouch(true);
                bot.getMoveControl().strafe(0, 0);
                if (bot.needsReload() && !bot.isReloadingGun()) bot.startBotReload();
                if (dist < 4 && see) {
                    go(State.ENGAGE);
                    return;
                }
                // if the enemy still sees us here the cover is bad
                if (see && stateTicks > 15 && CoverFinder.coverQuality(bot.level(), bot.blockPosition(), target.getEyePosition()) == 0) {
                    go(State.ENGAGE);
                    return;
                }
                int wait = 30 + (lowHealth ? 50 : 0);
                if (stateTicks > wait && !bot.isReloadingGun()) {
                    Vec3 p = lastSeen != null ? byRoad(CoverFinder.findPeek(bot, coverPos, threatEye)) : null;
                    if (p != null) {
                        movePos = p;
                        go(State.PEEK);
                    } else {
                        bot.setCrouch(false);
                        engageLimit = 50 + bot.getRandom().nextInt(50);
                        go(State.ENGAGE);
                    }
                }
            }
            case PEEK -> {
                bot.setCrouch(false);
                moveTo(movePos, BotEntity.SPEED_JOG);
                if (see || bot.position().distanceToSqr(movePos) < 1.2 || stateTicks > 60) {
                    bot.getNavigation().stop();
                    engageLimit = 40 + bot.getRandom().nextInt(60);
                    go(State.ENGAGE);
                }
            }
            case FLANK -> {
                bot.setCrouch(false);
                if (see) {
                    go(State.ENGAGE);
                    return;
                }
                moveTo(movePos, BotEntity.SPEED_RUN);
                if (bot.position().distanceToSqr(movePos) < 2.5 || stateTicks > 140) {
                    lostSight = 0;
                    go(State.ENGAGE);
                }
            }
        }
    }
}
