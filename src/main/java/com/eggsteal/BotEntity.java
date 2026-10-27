package com.eggsteal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
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
    public int carried = -1;
    public UUID carriedFrom;
    /** 0 idle, 1 to belt egg, 2 go home, 3 to victim, 4 to laser button, 5 chase rare egg, 6 stealing a pack creature */
    private int phase, phaseTicks, chase;
    private UUID goal;
    private final long[] seenEnd = new long[Arena.BASES];
    private int waitOff = -99, threatBase = -1, rushBase = -1;
    private boolean stealRush;

    public BotEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attrs() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 1.0).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }

    public void aggro(LivingEntity thief) {
        setTarget(thief);
        chase = 0;
    }

    /** Called when a theft succeeded: run home with the creature. */
    public void stolen() {
        phase = 2;
        phaseTicks = 0;
    }

    /** A recovered creature goes straight back into this bot's base. */
    public void receive(int variant) {
        if (!(level() instanceof ServerLevel sl)) return;
        Economy eco = Economy.get(sl.getServer());
        Economy.Stats st = eco.stats(getUUID());
        if (!Arena.place(sl, getUUID(), variant, st, 0)) { st.money += Creature.VALUES[variant].price * 0.5; eco.setDirty(); }
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

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        if (tickCount % 20 == 0) heal(5);
        Economy eco = Economy.get(sl.getServer());
        Economy.Stats st = eco.stats(getUUID());
        st.bot = true;
        if (waitOff == -99) waitOff = random.nextInt(9) - 4;
        if (tickCount % 40 == 0) {
            var a = getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null) a.setBaseValue(0.25 * (1 + 0.05 * st.speed));
            setCustomName(Component.literal("§b" + st.name + " §a$" + Economy.fmt(st.money)));
            setCustomNameVisible(true);
        }
        LivingEntity t = getTarget();
        if (t != null) {
            if (++chase > 300 || !t.isAlive()) { setTarget(null); chase = 0; equip(weaponItem(st)); return; }
            if (st.coilSpeed && distanceTo(t) > 5) {
                equip(EggSteal.SPEED_COIL.get());
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 2, false, false));
            } else equip(weaponItem(st));
            return;
        }
        equip(weaponItem(st));
        if (tickCount % 10 != 0 || st.base < 0) return;
        phaseTicks += 10;
        if (phaseTicks > 900) { phase = carried >= 0 ? 2 : 0; phaseTicks = 0; }
        if (carried < 0 && phase != 4 && phase != 5 && phase != 6) {
            if (shouldDefend(sl, eco, st)) { phase = 4; phaseTicks = 0; }
            else if (wantsAuction(sl, st)) { phase = 5; phaseTicks = 0; }
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
        return egg != null && !getUUID().equals(egg.lastBuyer) && st.money >= egg.price() && random.nextFloat() < 0.95f;
    }

    /** Rush toward the rare egg (even if somebody else already bought it) and buy it, fighting rivals on the way. */
    private void chaseAuction(ServerLevel sl, Economy.Stats st) {
        AuctionEggEntity egg = Auction.active(sl);
        if (egg == null || getUUID().equals(egg.lastBuyer) || st.money < egg.price()) { phase = 0; return; }
        addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 1, false, false));
        if (distanceTo(egg) < 3.2) {
            if (Auction.buy(egg, getUUID(), sl.getServer())) { phase = 0; return; }
        } else getNavigation().moveTo(egg.getX(), 64, egg.getZ(), 1.5);
        if (random.nextFloat() < 0.2f) {
            List<LivingEntity> rivals = sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4),
                    x -> x != this && ((x instanceof Player p && !p.isCreative() && !p.isSpectator()) || x instanceof BotEntity));
            if (!rivals.isEmpty()) aggro(rivals.get(random.nextInt(rivals.size())));
        }
    }

    /** Someone who is not me is close to my base and my laser is ready: go back and press the button. */
    private boolean shouldDefend(ServerLevel sl, Economy eco, Economy.Stats st) {
        if (!Laser.ready(sl.getServer(), st.base) || random.nextFloat() > 0.4f) return false;
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
        getNavigation().moveTo(b.x, b.y, b.z, 1.3);
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

        // gadgets and cosmetics
        if (!st.coilSpeed && st.money >= Shop.SPEED_COIL * 2 && random.nextFloat() < 0.3f) {
            st.money -= Shop.SPEED_COIL; st.coilSpeed = true; st.speed += 3; eco.setDirty(); return;
        }
        if (!st.floor2 && st.slots >= 2 && st.money >= Shop.FLOOR2 * 1.5 && random.nextFloat() < 0.3f) {
            st.money -= Shop.FLOOR2; st.floor2 = true; Arena.setFloor2(sl, st.base, true); eco.setDirty(); return;
        }
        if (st.money > 3_000_000 && random.nextFloat() < 0.05f) {
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
            if (weakest == null || c.creature().price < weakest.creature().price) weakest = c;
        }
        int target = 0;
        while (target < 15 && counts[target] >= 2) target++;

        ItemEntity pick = null;
        double bestPrice = -1;
        for (ItemEntity e : sl.getEntitiesOfClass(ItemEntity.class, Belt.box(st.base), it -> it.getPersistentData().contains("es_belt"))) {
            Creature c = Creature.VALUES[e.getPersistentData().getInt("es_belt")];
            boolean ok;
            if (full) ok = weakest != null && c.price > weakest.creature().price && c.price <= st.money + weakest.creature().price * 0.5;
            else ok = c.ordinal() >= target && c.price <= st.money;
            if (ok && c.price > bestPrice) { bestPrice = c.price; pick = e; }
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
        if (best != null && st.money >= bc + reserve && random.nextFloat() < 0.4f) {
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
        if (position().distanceToSqr(wx, 64, wz) > 6) getNavigation().moveTo(wx, 64, wz, 1.0);
    }

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
                carriedFrom = null;
                ie.discard();
                eco.setDirty();
                phase = 2;
                phaseTicks = 0;
            } else phase = 0;
            return;
        }
        getNavigation().moveTo(ie, 1.0);
    }

    private void goHome(ServerLevel sl, Economy eco, Economy.Stats st) {
        if (carried < 0) { phase = 0; return; }
        int idx = Arena.freeSlot(sl, st.base, st);
        if (idx < 0) {
            st.money += Creature.VALUES[carried].price * 0.5;
            carried = -1;
            carriedFrom = null;
            eco.setDirty();
            phase = 0;
            return;
        }
        Vec3 p = Arena.slot(st.base, idx);
        if (position().distanceToSqr(p.x, p.y, p.z) < 9) {
            Arena.spawnCreature(sl, getUUID(), carried, st.base, idx);
            carried = -1;
            carriedFrom = null;
            phase = 0;
            return;
        }
        getNavigation().moveTo(p.x, p.y, p.z, 1.15);
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
        getNavigation().moveTo(c, 1.1);
    }

    /** Pack creatures take 8 seconds to steal: stand next to it until it is done (or the owner interrupts). */
    private void stealWait(ServerLevel sl) {
        if (carried >= 0) { phase = 2; return; }
        Entity e = goal == null ? null : sl.getEntity(goal);
        if (!(e instanceof CreatureEntity c) || !c.isAlive() || !getUUID().equals(c.stealer())) { phase = 0; return; }
        if (distanceTo(c) > 2.5) getNavigation().moveTo(c, 1.0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("carried", carried);
        if (carriedFrom != null) t.putUUID("from", carriedFrom);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        carried = t.contains("carried") ? t.getInt("carried") : -1;
        if (t.hasUUID("from")) carriedFrom = t.getUUID("from");
    }
}
