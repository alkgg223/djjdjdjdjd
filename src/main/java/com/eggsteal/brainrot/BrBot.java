package com.eggsteal.brainrot;

import com.eggsteal.EggSteal;
import com.eggsteal.Economy;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Brainrot Heist bot that plays like a player: it buys the best brainrot it can afford on the carpet (and fights
 * everybody near a good one), buys brainrots away from others for double, sells weak ones when full, steals from
 * other bases, carries the loot home on its head, locks its base when somebody comes close, chases thieves, and rebirths.
 */
public class BrBot extends PathfinderMob {
    /** Path speed: strolling, going somewhere on purpose, chasing a thief. Mob speed grows with the square of these. */
    static final double WALK = 1.0, RUN = 1.1, CHASE = 1.2;
    /** 0 idle, 1 go for a brainrot to buy, 2 carry loot home, 3 go to a victim, 4 press the lock, 5 stealing */
    private int phase, phaseTicks, chase, waitOff = -99, attackCool, still, wantTick = -999;
    private UUID goal;
    private double wantX, wantY, wantZ, lastX, lastZ;
    private boolean owesMe;
    private final long[] lockSeen = new long[BrMap.BASES];

    public BrBot(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attrs() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 1.0).add(Attributes.FOLLOW_RANGE, 128);
    }

    @Override
    protected void registerGoals() { goalSelector.addGoal(0, new FloatGoal(this)); }

    @Override public boolean removeWhenFarAway(double d) { return false; }

    public void aggro(LivingEntity t) {
        if (t == this) return;
        setTarget(t);
        chase = 0;
        owesMe = level() instanceof ServerLevel sl && owes(sl, t);
    }

    public BrainrotEntity carrying() {
        for (Entity e : getPassengers()) if (e instanceof BrainrotEntity b) return b;
        return null;
    }

    private void equip(Item item) {
        if (getMainHandItem().getItem() != item) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
            setDropChance(EquipmentSlot.MAINHAND, 0f);
        }
    }

    // ------------------------------------------------------------------ movement

    /** Walks toward a point, leaving and entering bases through their doors instead of walking into walls. */
    private void go(double x, double y, double z, double speed) {
        wantX = x; wantY = y; wantZ = z; wantTick = tickCount;
        int from = BrMap.baseAt(getX(), getZ()), to = BrMap.baseAt(x, z);
        Vec3 w = null;
        if (from >= 0 && from != to) w = BrMap.gateOutside(from);
        else if (to >= 0 && from != to) w = BrMap.gateOutside(to);
        if (w != null && position().distanceToSqr(w.x, getY(), w.z) > 2.5) getNavigation().moveTo(w.x, w.y, w.z, speed);
        else getNavigation().moveTo(x, y, z, speed);
    }

    /** Wants to move but does not: jump, plan again, and as a last resort go home (never while carrying loot - it can be caught). */
    private void watchStuck(BrData.Stats st) {
        double dx = getX() - lastX, dz = getZ() - lastZ;
        lastX = getX(); lastZ = getZ();
        boolean wants = tickCount - wantTick <= 30 && distanceToSqr(wantX, wantY, wantZ) > 4;
        if (!wants || dx * dx + dz * dz > 0.09) { still = 0; return; }
        still += 10;
        if (still == 30 || still == 90) getJumpControl().jump();
        if (still == 60 || still == 120) { getNavigation().stop(); go(wantX, wantY, wantZ, RUN); }
        int inside = BrMap.baseAt(getX(), getZ());
        boolean caught = carrying() != null || (inside >= 0 && inside != st.base && BrMap.Lock.active(inside));
        if (still >= 160 && caught) { still = 100; return; }
        if (still >= 160 && st.base >= 0) {
            Vec3 h = BrMap.home(st.base);
            getNavigation().stop();
            teleportTo(h.x, h.y, h.z);
            setTarget(null);
            phase = 0;
            phaseTicks = 0;
            still = 0;
        }
    }

    // ------------------------------------------------------------------ fighting

    /** Has this entity got something of mine: is it stealing my brainrot, or carrying one of mine? */
    private boolean owes(ServerLevel sl, LivingEntity t) {
        UUID me = getUUID(), tid = t.getUUID();
        for (BrainrotEntity b : BrainrotEntity.all(sl)) {
            if (me.equals(b.owner) && (tid.equals(b.stealer) || (b.state() == BrainrotEntity.CARRIED && tid.equals(b.thief)))) return true;
        }
        return false;
    }

    /** Only rich bots can boost: up to twice as fast as a normal bot when it really matters. */
    private void boost(BrData.Stats st, int ticks) {
        if (BrData.guard(st) > 0) addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, false, false));
    }

    private void fight(LivingEntity t, BrData.Stats st) {
        getLookControl().setLookAt(t, 30f, 30f);
        double d = distanceTo(t);
        if (owesMe) {
            boost(st, 40);
            addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1, false, false));
        }
        if ((st.coil || owesMe) && d > 5) {
            equip(EggSteal.SPEED_COIL.get());
            boost(st, 40);
        } else equip(EggSteal.BAT.get());
        if (attackCool > 0) attackCool--;
        if (d < 2.6 && attackCool <= 0 && hasLineOfSight(t)) {
            swing(InteractionHand.MAIN_HAND);
            doHurtTarget(t);
            attackCool = owesMe ? 10 : 14;
        }
        if (tickCount % 5 == 0 && d > 1.6) go(t.getX(), t.getY(), t.getZ(), owesMe ? CHASE : RUN);
    }

    // ------------------------------------------------------------------ main loop

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        BrData data = BrData.get(sl.getServer());
        BrData.Stats st = data.map.get(getUUID());
        if (st == null || st.base < 0) { // left the game: never stay behind as a frozen ghost
            BrainrotEntity c = carrying();
            if (c != null) c.returnHome();
            discard();
            return;
        }
        st.bot = true;
        if (tickCount % 20 == 0) heal(5);
        if (waitOff == -99) waitOff = random.nextInt(9) - 4;
        if (tickCount % 40 == 0) {
            var a = getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null) a.setBaseValue(0.27 * (BrData.guard(st) > 0 ? 1 + 0.02 * st.speed : 1)); // speed levels only help rich bots
            setCustomName(Component.literal((st.rebirths > 0 ? "§d✦R" + st.rebirths + " " : "") + "§b" + st.name + " §a$" + Economy.fmt(st.money)));
            setCustomNameVisible(true);
        }
        // buy the brainrot I am after the moment it is in reach, even in a fight
        if (phase == 1 && tickCount % 5 == 0 && goal != null && sl.getEntity(goal) instanceof BrainrotEntity b && distanceTo(b) < 3.6) tryBuy(sl, data, st, b);
        LivingEntity t = getTarget();
        if (t != null && tickCount % 10 == 0) owesMe = owes(sl, t);
        if (t != null) {
            int limit = owesMe ? 900 + 300 * BrData.guard(st) : phase == 1 ? 70 : 240;
            boolean drop = ++chase > limit || !t.isAlive() || t.isRemoved() || (t instanceof Player p && (p.isCreative() || p.isSpectator()));
            if (drop) { setTarget(null); chase = 0; }
            else {
                fight(t, st);
                if (tickCount % 10 == 0) watchStuck(st);
                return;
            }
        }
        equip(EggSteal.BAT.get());
        if (tickCount % 10 != 0) return;
        watchStuck(st);
        phaseTicks += 10;
        if (phaseTicks > 900) { phase = carrying() != null ? 2 : 0; phaseTicks = 0; }
        if (carrying() != null) phase = 2;
        else if (phase != 4 && phase != 5 && phase != 1 && shouldLock(sl, data, st)) { phase = 4; phaseTicks = 0; }
        switch (phase) {
            case 0 -> idle(sl, data, st);
            case 1 -> toBuy(sl, data, st);
            case 2 -> carryHome(st);
            case 3 -> toVictim(sl);
            case 4 -> toLock(sl, st);
            case 5 -> stealWait(sl);
        }
    }

    private boolean tryBuy(ServerLevel sl, BrData data, BrData.Stats st, BrainrotEntity b) {
        if (!BrainrotEntity.hasRoom(sl, getUUID())) sellWeakest(sl, data, b.value());
        if (b.buy(getUUID()) == null) { phase = 0; goal = null; setTarget(null); return true; }
        return false;
    }

    /** Sells my weakest brainrot to make room for a better one (only if it is really worse). */
    private void sellWeakest(ServerLevel sl, BrData data, double betterValue) {
        BrainrotEntity weakest = null;
        for (BrainrotEntity b : BrainrotEntity.all(sl))
            if (b.state() == BrainrotEntity.PODIUM && getUUID().equals(b.owner) && !b.beingStolen() && (weakest == null || b.value() < weakest.value())) weakest = b;
        if (weakest != null && weakest.value() * 1.3 < betterValue) weakest.sell();
    }

    /** Somebody else is inside (or right in front of) my base and my lock is ready: go and press it. */
    private boolean shouldLock(ServerLevel sl, BrData data, BrData.Stats st) {
        if (!BrMap.Lock.ready(sl.getServer(), st.base) || random.nextFloat() > (BrData.guard(st) > 0 ? 1f : 0.45f)) return false;
        int cx = BrMap.cx(st.base), cz = BrMap.cz(st.base);
        AABB box = new AABB(cx - 16, 60, cz - 13, cx + 17, 80, cz + 14); // inside my walls
        for (ServerPlayer p : sl.players()) if (!p.isCreative() && !p.isSpectator() && box.contains(p.position())) return true;
        for (BrBot b : sl.getEntitiesOfClass(BrBot.class, box)) if (b != this) return true;
        return false;
    }

    private void toLock(ServerLevel sl, BrData.Stats st) {
        if (!BrMap.Lock.ready(sl.getServer(), st.base)) { phase = 0; return; }
        Vec3 b = BrMap.lockStand(st.base);
        if (position().distanceToSqr(b.x, b.y, b.z) < 9) {
            BrMap.Lock.press(sl, st.base);
            phase = 0;
            return;
        }
        go(b.x, b.y, b.z, RUN);
    }

    private void idle(ServerLevel sl, BrData data, BrData.Stats st) {
        long now = sl.getServer().getTickCount();
        if (st.money >= BrData.rebirthCost(st) * 1.15 && random.nextFloat() < 0.15f) {
            BrShop.rebirth(sl, data, getUUID(), st);
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1, getZ(), 40, 0.5, 1, 0.5, 0.4);
            return;
        }
        if (st.speed < BrData.MAX_SPEED && st.money >= BrData.speedCost(st) * 4 && random.nextFloat() < 0.2f) {
            st.money -= BrData.speedCost(st); st.speed++; data.setDirty(); return;
        }
        if (!st.coil && st.money >= BrShop.SPEED_COIL * 3 && random.nextFloat() < 0.2f) {
            st.money -= BrShop.SPEED_COIL; st.coil = true; data.setDirty(); return;
        }
        // rich bots dress up their base now and then: the best skin they can easily afford
        if (BrData.guard(st) > 0 && random.nextFloat() < 0.01f) {
            int best = -1;
            for (int k = 1; k < BrSkin.ALL.length; k++) if (!BrSkin.owns(st, k) && BrSkin.ALL[k].price * 4 <= st.money) best = k;
            if (best > st.skin && BrShop.skin(sl, data, st, best)) {
                sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§e" + st.name + " §fgot the "
                        + BrSkin.ALL[best].color + "§l" + BrSkin.ALL[best].name + " §fbase skin!"), false);
                return;
            }
        }
        // the best brainrot I can afford on the carpet (or walking to somebody else's base)
        boolean full = !BrainrotEntity.hasRoom(sl, getUUID());
        double weakest = Double.MAX_VALUE;
        if (full) for (BrainrotEntity b : BrainrotEntity.all(sl))
            if (b.state() == BrainrotEntity.PODIUM && getUUID().equals(b.owner)) weakest = Math.min(weakest, b.value());
        BrainrotEntity pick = null;
        double best = 0;
        for (BrainrotEntity b : BrainrotEntity.all(sl)) {
            int s = b.state();
            if ((s != BrainrotEntity.ROAD && s != BrainrotEntity.WALK) || getUUID().equals(b.buyer) || b.price > st.money) continue;
            if (s == BrainrotEntity.ROAD && b.getX() > BrMap.ROAD_END - 8) continue;
            double v = b.value();
            if (full && v < weakest * 1.3) continue;
            boolean great = b.special || b.brainrot().rarity >= 3 || b.mutation() >= 10;
            double score = v * (great ? 3 : 1) / (1 + distanceTo(b) * 0.02);
            if (s == BrainrotEntity.WALK && b.price > st.money * 0.6 && !great) continue; // re-buying for double is only worth it for good ones
            if (score > best) { best = score; pick = b; }
        }
        if (pick != null && (pick.special || random.nextFloat() < 0.6f)) {
            goal = pick.getUUID();
            phase = 1;
            phaseTicks = 0;
            return;
        }
        // after a lock ends somewhere, ~12% of bots go for the best brainrot there; and very rarely steal on their own
        for (int b = 0; b < BrMap.BASES; b++) {
            if (b == st.base) continue;
            boolean lockedNow = BrMap.Lock.active(b);
            if (lockedNow) lockSeen[b] = now;
            else if (lockSeen[b] > 0 && now - lockSeen[b] < 60) {
                lockSeen[b] = 0;
                if (random.nextFloat() < 0.12f && pickVictim(sl, data, b)) return;
            }
        }
        if (random.nextFloat() < (st.malicious ? 0.012f : 0.004f) && pickVictim(sl, data, -1)) return;
        // otherwise wait by my door, watching the carpet
        Vec3 g = BrMap.gateOutside(st.base);
        double wx = g.x + waitOff, wz = g.z + BrMap.dir(st.base);
        if (position().distanceToSqr(wx, 64, wz) > 6) go(wx, 64, wz, WALK);
    }

    /** The most valuable brainrot in an unlocked base (my own excluded). */
    private boolean pickVictim(ServerLevel sl, BrData data, int onlyBase) {
        BrainrotEntity best = null;
        for (BrainrotEntity b : BrainrotEntity.all(sl)) {
            if (b.state() != BrainrotEntity.PODIUM || b.owner == null || b.owner.equals(getUUID()) || b.beingStolen()) continue;
            BrData.Stats os = data.map.get(b.owner);
            if (os == null || os.base < 0 || BrMap.Lock.active(os.base) || (onlyBase >= 0 && os.base != onlyBase)) continue;
            if (best == null || b.value() > best.value()) best = b;
        }
        if (best == null || !BrainrotEntity.hasRoom(sl, getUUID())) return false;
        goal = best.getUUID();
        phase = 3;
        phaseTicks = 0;
        return true;
    }

    private void toBuy(ServerLevel sl, BrData data, BrData.Stats st) {
        Entity e = goal == null ? null : sl.getEntity(goal);
        if (!(e instanceof BrainrotEntity b) || !b.isAlive() || (b.state() != BrainrotEntity.ROAD && b.state() != BrainrotEntity.WALK)
                || getUUID().equals(b.buyer) || b.price > st.money) { phase = 0; return; }
        boolean great = b.special || b.brainrot().rarity >= 3 || b.mutation() >= 10;
        if (great) boost(st, 30);
        if (distanceTo(b) < 3.6 && tryBuy(sl, data, st, b)) return;
        go(b.getX(), 64, b.getZ(), RUN);
        // a good one: fight whoever else is next to it
        float chance = (great ? 0.18f : 0.05f) * (st.malicious ? 2f : 1f);
        if (distanceTo(b) < 8 && random.nextFloat() < chance) {
            List<LivingEntity> rivals = sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4),
                    x -> x != this && ((x instanceof Player p && !p.isCreative() && !p.isSpectator()) || x instanceof BrBot));
            if (!rivals.isEmpty()) aggro(rivals.get(random.nextInt(rivals.size())));
        }
    }

    private void carryHome(BrData.Stats st) {
        if (carrying() == null) { phase = 0; return; }
        Vec3 h = BrMap.home(st.base);
        go(h.x, h.y, h.z, RUN);
    }

    private void toVictim(ServerLevel sl) {
        Entity e = goal == null ? null : sl.getEntity(goal);
        if (!(e instanceof BrainrotEntity b) || b.state() != BrainrotEntity.PODIUM || getUUID().equals(b.owner)) { phase = 0; return; }
        BrData.Stats os = BrData.get(sl.getServer()).map.get(b.owner);
        if (os == null || BrMap.Lock.active(os.base)) { phase = 0; return; } // locked: give up
        if (distanceTo(b) < 2.6) {
            if (b.beginSteal(this) == null) { phase = 5; phaseTicks = 0; } else phase = 0;
            return;
        }
        go(b.getX(), 64, b.getZ(), WALK);
    }

    private void stealWait(ServerLevel sl) {
        if (carrying() != null) { phase = 2; return; }
        Entity e = goal == null ? null : sl.getEntity(goal);
        if (!(e instanceof BrainrotEntity b) || !getUUID().equals(b.stealer)) { phase = 0; return; }
        if (distanceTo(b) > 2.2) go(b.getX(), 64, b.getZ(), WALK);
    }
}
