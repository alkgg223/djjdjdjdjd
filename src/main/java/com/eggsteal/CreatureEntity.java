package com.eggsteal;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A hatched creature standing on a slot of a base; earns money for its owner. */
public class CreatureEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HATCH = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
    public static final int HATCH_TIME = 60;
    private UUID owner, stealer;
    private int stealEnd;

    public CreatureEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attrs() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(VARIANT, 0);
        b.define(HATCH, 0);
    }

    public int variant() { return entityData.get(VARIANT); }
    public Creature creature() { return Creature.VALUES[Math.floorMod(variant(), Creature.VALUES.length)]; }
    public void setVariant(int v) { entityData.set(VARIANT, v); refreshName(); }
    public UUID owner() { return owner; }
    public void setOwner(UUID o) { owner = o; refreshName(); }
    public boolean hatching() { return entityData.get(HATCH) > 0; }
    public void startHatch(int t) { entityData.set(HATCH, t); refreshName(); }
    public float hatchProgress() { return 1f - Math.min(1f, entityData.get(HATCH) / (float) HATCH_TIME); }

    void refreshName() {
        if (level().isClientSide) return;
        Creature c = creature();
        double inc = c.income;
        if (owner != null && level().getServer() != null) {
            Economy.Stats st = Economy.get(level().getServer()).map.get(owner);
            if (st != null) inc = Economy.rate(st, c);
        }
        setCustomName(Component.literal(hatching() ? "§7Hatching..." : c.tag() + " §6+$" + Economy.fmt(inc) + "/s"));
        setCustomNameVisible(true);
    }

    public UUID stealer() { return stealer; }
    public boolean stealing() { return stealer != null; }

    /** Normal creatures are stolen at once; pack creatures need the thief to stay close for 8 seconds. */
    public void beginSteal(LivingEntity thief) {
        int t = creature().stealTicks();
        if (t <= 0) { Steal.finish(this, thief); return; }
        if (stealer != null) return;
        stealer = thief.getUUID();
        stealEnd = tickCount + t;
        Steal.notifyAttempt((ServerLevel) level(), owner, thief, variant());
    }

    public void cancelSteal() {
        if (stealer == null) return;
        stealer = null;
        refreshName();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (stealer != null) {
            Entity e = ((ServerLevel) level()).getEntity(stealer);
            if (!(e instanceof LivingEntity th) || !th.isAlive() || distanceTo(th) > 4.5) cancelSteal();
            else if (tickCount >= stealEnd) { stealer = null; Steal.finish(this, th); return; }
            else if (tickCount % 10 == 0) {
                int left = (stealEnd - tickCount) / 20 + 1;
                setCustomName(Component.literal("§c⚠ Being stolen... " + left + "s"));
                if (th instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("§eStealing... stay next to it! §7" + left + "s"), true);
            }
        }
        int h = entityData.get(HATCH);
        if (h > 0) {
            entityData.set(HATCH, h - 1);
            if (h == 1) refreshName();
            return;
        }
        if (tickCount % 20 == 0 && owner != null) {
            Economy eco = Economy.get(level().getServer());
            Economy.Stats st = eco.map.get(owner);
            if (st != null) {
                st.money += Economy.rate(st, creature());
                eco.setDirty();
            }
        }
        if (tickCount % 100 == 0) refreshName();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && owner != null && !owner.equals(sp.getUUID())) Steal.playerSteal(this, sp);
        return InteractionResult.SUCCESS;
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("variant", variant());
        t.putInt("hatch", entityData.get(HATCH));
        if (owner != null) t.putUUID("owner", owner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(VARIANT, t.getInt("variant"));
        entityData.set(HATCH, t.getInt("hatch"));
        if (t.hasUUID("owner")) owner = t.getUUID("owner");
    }
}
