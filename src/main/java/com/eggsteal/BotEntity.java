package com.eggsteal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A bot that plays the game like a player: buys eggs from its own belt (saving up for the next tier),
 * rushes the rare center egg when it can afford it, sells weak creatures when full, upgrades itself and its base,
 * uses the laser door, and steals the most expensive creature it can reach.
 */
public class BotEntity extends PathfinderMob {
    public int carried = -1, carriedMut;
    public UUID carriedFrom;
    /** 0 idle, 1 to belt egg, 2 go home, 3 to victim, 4 to laser button, 5 chase rare egg, 6 stealing a pack creature */
    private int phase, phaseTicks, chase;
    private UUID goal;
    private final long[] seenEnd = new long[Arena.BASES];
    private int waitOff = -99, threatBase = -1, rushBase = -1;
    private boolean stealRush;
    /** Stuck detection: where I wanted to go, and for how long I have not moved. */
    private double wantX, wantY, wantZ, lastX, lastZ;
    private int wantTick = -999, still, attackCool;
    /** The current target has something of mine (my egg, my creature, or is stealing one right now): chase hard. */
    private boolean owesMe;

    public BotEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attrs() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 1.0).add(Attributes.FOLLOW_RANGE, 128); // the arena is ~140 blocks wide: a short range left far bots without a path
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this)); // fighting is driven by tick(): the melee goal gave up silently on long paths
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }

    public void aggro(LivingEntity thief) {
        setTarget(thief);
        chase = 0;
        owesMe = level() instanceof ServerLevel sl && owes(sl, thief);
    }

    /** Called when a theft succeeded: run home with the creature. */
    public void stolen() {
        phase = 2;
        phaseTicks = 0;
    }

    /** A recovered creature goes straight back into this bot's base. */
    public void receive(int variant, int mut) {
        if (!(level() instanceof ServerLevel sl)) return;
        Economy eco = Economy.get(sl.getServer());
        Economy.Stats st = eco.stats(getUUID());
        if (!Arena.place(sl, getUUID(), variant, mut, st, 0)) { st.money += Creature.VALUES[variant].price * 0.5; eco.setDirty(); }
    }

    private Item weaponItem(Economy.Stats st) {
        return st.weapon >= 2 ? Items.IRON_SWORD : st.weapon == 1 ? Items.STONE_SWORD : EggSteal.BAT.get();
    }

    private void equip(Item item) {
        if (getMainHandItem().getItem() != item) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
            setDropChance(EquipmentSlot.MAINHAND, 0f);
        }
    }

    /**
     * Walks toward a point. Bases are walled with a single entrance, so the bot first leaves its current base through
     * the door and enters a target base through its door, instead of pushing into a wall.
     */
    private void go(double x, double y, double z, double speed) {
        wantX = x; wantY = y; wantZ = z; wantTick = tickCount;
        int from = Arena.baseAt(getX(), getZ()), to = Arena.baseAt(x, z);
        Vec3 w = null;
        if (from >= 0 && from != to) w = Arena.gateOutside(from);
        else if (to >= 0 && from != to) w = Arena.gateOutside(to);
        if (w != null && position().distanceToSqr(w.x, getY(), w.z) > 2.5) getNavigation().moveTo(w.x, w.y, w.z, speed);
        else getNavigation().moveTo(x, y, z, speed);
    }

    /** If I want to move but have not moved for a while: jump, re-plan, and as a last resort go back home. */
    private void watchStuck(Economy.Stats st) {
        double dx = getX() - lastX, dz = getZ() - lastZ;
        lastX = getX(); lastZ = getZ();
        boolean wants = tickCount - wantTick <= 30 && distanceToSqr(wantX, wantY, wantZ) > 4;
        if (!wants || dx * dx + dz * dz > 0.09) { still = 0; return; }
        still += 10;
        if (still == 30 || still == 90) getJumpControl().jump();
        if (still == 60 || still == 120) { getNavigation().stop(); go(wantX, wantY, wantZ, 1.2); }
        boolean caught = carried >= 0 && carriedFrom != null; // a thief trapped by a laser must stay catchable
        int inside = Arena.baseAt(getX(), getZ());
        if (inside >= 0 && inside != st.base && Laser.active(inside)) caught = true;
        if (still >= 160 && caught) { still = 100; return; }
        if (still >= 160 && st.base >= 0) { // 8 s without moving: teleport home and start over
            Vec3 h = Arena.home(st.base);
            getNavigation().stop();
            teleportTo(h.x, h.y, h.z);
            setTarget(null);
            phase = carried >= 0 ? 2 : 0;
            phaseTicks = 0;
            still = 0;
        }
    }

    /** Does this entity have something of mine: my egg, my creature on its back or freshly placed, or is it stealing one? */
    private boolean owes(ServerLevel sl, LivingEntity t) {
        UUID me = getUUID(), tid = t.getUUID();
        if (t instanceof Player p) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (s.getItem() instanceof EggItem && me.equals(Steal.fromOf(s))) return true;
            }
        }
        if (t instanceof BotEntity b && me.equals(b.carriedFrom)) return true;
        long now = sl.getGameTime();
        return !sl.getEntities(EntityTypeTest.forClass(CreatureEntity.class), c ->
                (me.equals(c.owner()) && tid.equals(c.stealer())) || (tid.equals(c.owner()) && me.equals(c.stolenFrom()) && c.stolenUntil() > now)).isEmpty();
    }

    private void fight(LivingEntity t, Economy.Stats st) {
        getLookControl().setLookAt(t, 30f, 30f);
        double d = distanceTo(t);
        int g = Economy.guard(st);
        if (owesMe) { // a thief: run after it much faster (rich bots even more), and jump over things
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, Math.min(4, 2 + g), false, false));
            addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1, false, false));
        }
        if ((st.coilSpeed || owesMe) && d > 5) {
            equip(EggSteal.SPEED_COIL.get());
            if (!owesMe) addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 2, false, false));
        } else equip(weaponItem(st));
        if (attackCool > 0) attackCool--;
        if (d < 2.6 && attackCool <= 0 && hasLineOfSight(t)) {
            swing(InteractionHand.MAIN_HAND);
            doHurtTarget(t);
            attackCool = owesMe ? 10 : 14;
        }
        if (tickCount % 5 == 0 && d > 1.6) go(t.getX(), t.getY(), t.getZ(), 1.3);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        if (tickCount % 20 == 0) heal(5);
        Economy eco = Economy.get(sl.getServer());
        Economy.Stats st = eco.map.get(getUUID());
        // a bot whose player data is gone (it left, or was kicked for a real player) must not stay behind as a frozen "?" ghost
        if (st == null || st.base < 0) { discard(); return; }
        st.bot = true;
        if (waitOff == -99) waitOff = random.nextInt(9) - 4;
        if (tickCount % 40 == 0) {
            var a = getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null) a.setBaseValue(0.25 * (1 + 0.05 * st.speed));
            setCustomName(Component.literal(Economy.rebirthTag(st) + "§b" + st.name + " §a$" + Economy.fmt(st.money)));
            setCustomNameVisible(true);
        }
        // the rare egg comes first: buy it as soon as it is in reach, even in the middle of a fight
        if (phase == 5 && tickCount % 5 == 0) {
            AuctionEggEntity egg = Auction.active(sl);
            if (egg != null && distanceTo(egg) < 3.4 && Auction.buy(egg, getUUID(), sl.getServer())) { phase = 0; setTarget(null); }
        }
        LivingEntity t = getTarget();
        if (t != null && tickCount % 10 == 0) owesMe = owes(sl, t);
        if (t != null) {
            int limit = owesMe ? 900 + 300 * Economy.guard(st) : 300; // never give up quickly on a thief
            boolean drop = ++chase > limit || !t.isAlive() || t.isRemoved()
                    || (t instanceof Player p && (p.isCreative() || p.isSpectator()))
                    || (phase == 5 && chase > 60); // never miss the rare egg because of a long fight
            if (drop) { setTarget(null); chase = 0; equip(weaponItem(st)); }
            else {
                fight(t, st);
                if (tickCount % 10 == 0) watchStuck(st);
                return;
            }
        }
        equip(weaponItem(st));
        if (tickCount % 10 != 0 || st.base < 0) return;
        watchStuck(st);
        phaseTicks += 10;
        if (phaseTicks > 900) { phase = carried >= 0 ? 2 : 0; phaseTicks = 0; }
        if (carried < 0 && phase != 4 && phase != 5 && phase != 6) {
            if (wantsAuction(sl, st)) { phase = 5; phaseTicks = 0; }
            else if (shouldDefend(sl, eco, st)) { phase = 4; phaseTicks = 0; }
        }
        switch (phase) {
            case 0 -> idle(sl, eco, st);
            case 1 -> toBelt(sl, eco, st);
            case 2 -> goHome(sl, eco, st);
            case 3 -> toVictim(sl, eco);
            case 4 -> toButton(sl, st);
            case 5 -> chaseAuction(sl, st);
            case 6 -> stealWait(sl);
        }
    }

    private boolean wantsAuction(ServerLevel sl, Economy.Stats st) {
        AuctionEggEntity egg = Auction.active(sl);
        return egg != null && !getUUID().equals(egg.lastBuyer) && st.money >= egg.price();
    }

    /** Rush toward the rare egg (even if somebody else already bought it) and buy it, fighting rivals on the way. */
    private void chaseAuction(ServerLevel sl, Economy.Stats st) {
        AuctionEggEntity egg = Auction.active(sl);
        if (egg == null || getUUID().equals(egg.lastBuyer) || st.money < egg.price()) { phase = 0; return; }
        addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 1, false, false));
        if (distanceTo(egg) < 3.4) {
            if (Auction.buy(egg, getUUID(), sl.getServer())) { phase = 0; return; }
        }
        go(egg.getX(), 64, egg.getZ(), 1.5);
        if (distanceTo(egg) > 5 && random.nextFloat() < 0.05f) { // only scuffle on the way, never next to the egg
            List<LivingEntity> rivals = sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3),
                    x -> x != this && ((x instanceof Player p && !p.isCreative() && !p.isSpectator()) || x instanceof BotEntity));
            if (!rivals.isEmpty()) aggro(rivals.get(random.nextInt(rivals.size())));
        }
    }

    /** Someone who is not me is close to my base and my laser is ready: go back and press the button. */
    private boolean shouldDefend(ServerLevel sl, Economy eco, Economy.Stats st) {
        if (!Laser.ready(sl.getServer(), st.base) || random.nextFloat() > (Economy.guard(st) > 0 ? 1f : 0.4f)) return false;
        if (Arena.owned(sl, getUUID()) == 0) return false;
        int cx = Arena.cx(st.base), cz = Arena.cz(st.base);
        AABB box = new AABB(cx - 22, 60, cz - 22, cx + 22, 80, cz + 22);
        for (ServerPlayer p : sl.players()) {
            if (!p.isCreative() && !p.isSpectator() && box.contains(p.position())) {
                Economy.Stats ps = eco.map.get(p.getUUID());
                threatBase = ps == null ? -1 : ps.base;
                return true;
            }
        }
        for (BotEntity b : sl.getEntitiesOfClass(BotEntity.class, box)) {
            if (b != this) {
                Economy.Stats bs = eco.map.get(b.getUUID());
                threatBase = bs == null ? -1 : bs.base;
                return true;
            }
        }
        return false;
    }

    private void toButton(ServerLevel sl, Economy.Stats st) {
        if (!Laser.ready(sl.getServer(), st.base)) { phase = 0; return; }
        Vec3 b = Arena.buttonStand(st.base);
        if (position().distanceToSqr(b.x, b.y, b.z) < 9) {
            Laser.press(sl, st.base);
            stealRush = true;           // the door is shut: slip out and counter-steal while it protects my base
            rushBase = threatBase;
            phase = 0;
            phaseTicks = 0;
            return;
        }
        go(b.x, b.y, b.z, 1.3);
    }

    private void idle(ServerLevel sl, Economy eco, Economy.Stats st) {
        if (carried >= 0) { phase = 2; phaseTicks = 0; return; }
        long now = sl.getServer().getTickCount();

        if (stealRush) {
            stealRush = false;
            if (Laser.active(st.base) && random.nextFloat() < 0.7f) {
                CreatureEntity v = rushBase >= 0 ? pickVictim(sl, eco, rushBase) : null;
                if (v == null) v = pickVictim(sl, eco, -1);
                if (v != null) { goal = v.getUUID(); phase = 3; phaseTicks = 0; return; }
            }
        }

        List<? extends CreatureEntity> mine = sl.getEntities(EntityTypeTest.forClass(CreatureEntity.class), c -> getUUID().equals(c.owner()));
        int avail = Arena.available(st);
        boolean full = mine.size() >= avail;

        if (st.money >= Economy.rebirthCost(st) * 1.1 && random.nextFloat() < 0.15f) {
            eco.rebirth(st);
            sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§d§l✦ " + st.name + " §dREBIRTHED! §7(Rebirth " + st.rebirths + " · income x" + Economy.rebirthMult(st) + ")"), false);
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1, getZ(), 40, 0.5, 1, 0.5, 0.4);
            return;
        }
        // a rare egg is on the center belt: keep the money for it instead of spending it on extras
        AuctionEggEntity rare = Auction.active(sl);
        boolean saving = rare != null && !getUUID().equals(rare.lastBuyer) && st.money < rare.price() && st.money * 3 >= rare.price();

        // gadgets and cosmetics
        if (!saving && !st.coilSpeed && st.money >= Shop.SPEED_COIL * 2 && random.nextFloat() < 0.3f) {
            st.money -= Shop.SPEED_COIL; st.coilSpeed = true; st.speed += 3; eco.setDirty(); return;
        }
        if (!saving && !st.floor2 && st.slots >= 2 && st.money >= Shop.FLOOR2 * 1.5 && random.nextFloat() < 0.3f) {
            st.money -= Shop.FLOOR2; st.floor2 = true; Arena.setFloor2(sl, st.base, true); eco.setDirty(); return;
        }
        if (!saving && st.money > 3_000_000 && random.nextFloat() < 0.05f) {
            List<Integer> opts = new ArrayList<>();
            for (int k = 1; k < Skin.ALL.length; k++) if (((st.skinsOwned >> k) & 1) == 0 && Skin.ALL[k].price <= st.money * 0.5) opts.add(k);
            if (!opts.isEmpty()) {
                int k = opts.get(random.nextInt(opts.size()));
                st.money -= Skin.ALL[k].price;
                st.skinsOwned |= 1L << k;
                st.skin = k;
                Arena.paintBase(sl, st.base, k);
                eco.setDirty();
                return;
            }
        }

        // which egg do I want? the first normal creature type I own fewer than 2 of
        int[] counts = new int[Creature.VALUES.length];
        CreatureEntity weakest = null;
        for (CreatureEntity c : mine) {
            counts[c.variant()]++;
            if (weakest == null || value(c) < value(weakest)) weakest = c;
        }
        int target = 0;
        while (target < 15 && counts[target] >= 2) target++;

        ItemEntity pick = null;
        double bestPrice = -1;
        for (ItemEntity e : sl.getEntitiesOfClass(ItemEntity.class, Belt.box(st.base), it -> it.getPersistentData().contains("es_belt"))) {
            Creature c = Creature.VALUES[e.getPersistentData().getInt("es_belt")];
            int mut = e.getPersistentData().getInt("es_mut");
            double val = c.price * Mutation.of(mut).mult;
            boolean ok;
            if (full) ok = weakest != null && val > value(weakest) && c.price <= st.money + weakest.creature().price * 0.5;
            else ok = (c.ordinal() >= target || mut > 0) && c.price <= st.money;
            if (saving && c.price > st.money * 0.1) ok = false;
            if (ok && val > bestPrice) { bestPrice = val; pick = e; }
        }
        if (pick != null) {
            if (full) { // sell the weakest creature to make room for a stronger one
                st.money += weakest.creature().price * 0.5;
                weakest.discard();
                eco.setDirty();
            }
            goal = pick.getUUID();
            phase = 1;
            phaseTicks = 0;
            return;
        }

        // upgrades (only if I can still afford the egg I am saving for)
        double reserve = full ? 0 : Creature.VALUES[target].price;
        Economy.Upgrade best = null;
        double bc = Double.MAX_VALUE;
        for (Economy.Upgrade u : Economy.Upgrade.values()) {
            int lv = u.level(st);
            if (lv >= u.max) continue;
            if (u == Economy.Upgrade.SLOTS && !full) continue;
            double c = u.cost(lv);
            if (c < bc) { bc = c; best = u; }
        }
        if (!saving && best != null && st.money >= bc + reserve && random.nextFloat() < 0.4f) {
            st.money -= bc;
            best.set(st, best.level(st) + 1);
            eco.setDirty();
            return;
        }

        // after somebody's laser ends, ~10% of bots go for a creature there
        for (int b = 0; b < Arena.BASES; b++) {
            if (b == st.base) continue;
            long end = Laser.endedAt(b);
            if (end > seenEnd[b]) {
                seenEnd[b] = end;
                if (now - end < 300 && random.nextFloat() < 0.10f) {
                    CreatureEntity v = pickVictim(sl, eco, b);
                    if (v != null) { goal = v.getUUID(); phase = 3; phaseTicks = 0; return; }
                }
            }
        }
        // very rarely steal on my own
        if (random.nextFloat() < 0.004f) {
            CreatureEntity v = pickVictim(sl, eco, -1);
            if (v != null) { goal = v.getUUID(); phase = 3; phaseTicks = 0; return; }
        }
        // otherwise wait next to my belt
        int d = Arena.dir(st.base);
        double wx = Arena.cx(st.base) + waitOff + 0.5, wz = Arena.beltZ(st.base) - d * 2.5;
        if (position().distanceToSqr(wx, 64, wz) > 6) go(wx, 64, wz, 1.0);
    }

    private static double value(CreatureEntity c) { return c.creature().price * Mutation.of(c.mutation()).mult; }

    /** The smartest target: always the most expensive creature that is reachable (its base laser is off). */
    private CreatureEntity pickVictim(ServerLevel sl, Economy eco, int onlyBase) {
        List<? extends CreatureEntity> all = sl.getEntities(EntityTypeTest.forClass(CreatureEntity.class), c -> {
            if (c.owner() == null || c.owner().equals(getUUID()) || c.hatching() || c.stealing()) return false;
            Economy.Stats os = eco.map.get(c.owner());
            if (os == null || os.base < 0 || Laser.active(os.base)) return false;
            return onlyBase < 0 || os.base == onlyBase;
        });
        CreatureEntity best = null;
        for (CreatureEntity c : all) if (best == null || c.creature().price > best.creature().price) best = c;
        return best;
    }

    private void toBelt(ServerLevel sl, Economy eco, Economy.Stats st) {
        Entity e = goal == null ? null : sl.getEntity(goal);
        if (!(e instanceof ItemEntity ie) || !ie.isAlive()) { phase = 0; return; }
        if (distanceToSqr(ie) < 5) {
            Creature c = Creature.VALUES[ie.getPersistentData().getInt("es_belt")];
            if (st.money >= c.price) {
                st.money -= c.price;
                carried = c.ordinal();
                carriedMut = ie.getPersistentData().getInt("es_mut");
                carriedFrom = null;
                ie.discard();
                eco.setDirty();
                phase = 2;
                phaseTicks = 0;
            } else phase = 0;
            return;
        }
        go(ie.getX(), 64, ie.getZ(), 1.0);
    }

    private void goHome(ServerLevel sl, Economy eco, Economy.Stats st) {
        if (carried < 0) { phase = 0; return; }
        int idx = Arena.freeSlot(sl, st.base, st);
        if (idx < 0) { // base full: sell my weakest creature to make room
            CreatureEntity weakest = null;
            for (CreatureEntity c : sl.getEntities(EntityTypeTest.forClass(CreatureEntity.class), x -> getUUID().equals(x.owner())))
                if (Arena.slotOf(st.base, c) >= 0 && (weakest == null || value(c) < value(weakest))) weakest = c;
            if (weakest != null && (carriedFrom != null || value(weakest) < Creature.VALUES[carried].price * Mutation.of(carriedMut).mult)) {
                idx = Arena.slotOf(st.base, weakest);
                st.money += weakest.creature().price * 0.5;
                weakest.discard();
                eco.setDirty();
            }
        }
        if (idx < 0) {
            st.money += Creature.VALUES[carried].price * 0.5;
            carried = -1;
            carriedMut = 0;
            carriedFrom = null;
            eco.setDirty();
            phase = 0;
            return;
        }
        Vec3 p = Arena.slot(st.base, idx);
        if (position().distanceToSqr(p.x, p.y, p.z) < 9) {
            CreatureEntity placed = Arena.spawnCreature(sl, getUUID(), carried, carriedMut, st.base, idx);
            if (placed != null && carriedFrom != null) placed.markStolen(carriedFrom, sl.getGameTime() + Steal.recoverWindow(eco.map.get(carriedFrom)));
            carried = -1;
            carriedMut = 0;
            carriedFrom = null;
            phase = 0;
            return;
        }
        go(p.x, p.y, p.z, 1.15);
    }

    private void toVictim(ServerLevel sl, Economy eco) {
        Entity e = goal == null ? null : sl.getEntity(goal);
        if (!(e instanceof CreatureEntity c) || !c.isAlive() || c.hatching() || getUUID().equals(c.owner())) { phase = 0; return; }
        Economy.Stats os = c.owner() == null ? null : eco.map.get(c.owner());
        if (os == null || os.base < 0 || Laser.active(os.base)) { phase = 0; return; } // the laser is on: they know and give up
        if (distanceToSqr(c) < 5) {
            c.beginSteal(this);
            if (!c.isRemoved() && c.stealing()) { phase = 6; phaseTicks = 0; }
            return;
        }
        go(c.getX(), c.getY(), c.getZ(), 1.1);
    }

    /** Pack creatures take 8 seconds to steal: stand next to it until it is done (or the owner interrupts). */
    private void stealWait(ServerLevel sl) {
        if (carried >= 0) { phase = 2; return; }
        Entity e = goal == null ? null : sl.getEntity(goal);
        if (!(e instanceof CreatureEntity c) || !c.isAlive() || !getUUID().equals(c.stealer())) { phase = 0; return; }
        if (distanceTo(c) > 2.5) go(c.getX(), c.getY(), c.getZ(), 1.0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("carried", carried);
        t.putInt("carriedMut", carriedMut);
        if (carriedFrom != null) t.putUUID("from", carriedFrom);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        carried = t.contains("carried") ? t.getInt("carried") : -1;
        carriedMut = t.getInt("carriedMut");
        if (t.hasUUID("from")) carriedFrom = t.getUUID("from");
    }
}
