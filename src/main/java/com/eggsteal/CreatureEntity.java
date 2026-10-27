package com.eggsteal;

import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import org.joml.Vector3f;

/** A hatched creature standing on a slot of a base; earns money for its owner. */
public class CreatureEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HATCH = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MUT = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
    public static final int HATCH_TIME = 60;
    private UUID owner, stealer;
    private int stealEnd;
    private boolean wasHatching;
    /** Client only: counts down from 10 right after hatching, for the pop-in animation. */
    public int clientPop;

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
        b.define(MUT, 0);
    }

    public int variant() { return entityData.get(VARIANT); }
    public Creature creature() { return Creature.VALUES[Math.floorMod(variant(), Creature.VALUES.length)]; }
    public void setVariant(int v) { entityData.set(VARIANT, v); refreshName(); }
    public int mutation() { return entityData.get(MUT); }
    public void setMutation(int m) { entityData.set(MUT, m); refreshName(); }
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
            if (st != null) inc = Economy.rate(st, c, mutation());
        }
        setCustomName(Component.literal(hatching() ? "§7Hatching..." : Mutation.of(mutation()).prefix() + c.tag() + " §6+$" + Economy.fmt(inc) + "/s"));
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
        if (level().isClientSide) { clientTick(); return; }
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
                st.money += Economy.rate(st, creature(), mutation());
                eco.setDirty();
            }
        }
        if (tickCount % 100 == 0) refreshName();
    }

    /** Hatching "poof" and the sparkles of mutated creatures (client only). */
    private void clientTick() {
        boolean h = hatching();
        if (clientPop > 0) clientPop--;
        if (wasHatching && !h) {
            clientPop = 10;
            for (int i = 0; i < 14; i++)
                level().addParticle(i % 2 == 0 ? ParticleTypes.POOF : ParticleTypes.HAPPY_VILLAGER, getX() + (random.nextDouble() - 0.5) * 0.8, getY() + random.nextDouble() * 0.8, getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0.05, 0);
        }
        wasHatching = h;
        if (h || tickCount % 6 != 0) return;
        double x = getX() + (random.nextDouble() - 0.5) * 0.9, y = getY() + 0.2 + random.nextDouble() * 0.9, z = getZ() + (random.nextDouble() - 0.5) * 0.9;
        switch (Mutation.of(mutation())) {
            case RAINBOW -> {
                float hue = (tickCount % 60) / 60f;
                int rgb = Mutation.hsv(hue, 0.8f, 1f);
                level().addParticle(new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), 1f), x, y, z, 0, 0, 0);
            }
            case GLITCH -> level().addParticle(random.nextBoolean() ? ParticleTypes.ENCHANT : new DustParticleOptions(new Vector3f(0.1f, 1f, 0.3f), 0.8f), x, y, z, 0, 0.02, 0);
            case MOLTEN -> level().addParticle(random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME, x, y, z, 0, 0.01, 0);
            case GALAXY -> level().addParticle(random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.PORTAL, x, y, z, 0, 0.01, 0);
            default -> { }
        }
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
        t.putInt("mut", mutation());
        if (owner != null) t.putUUID("owner", owner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(VARIANT, t.getInt("variant"));
        entityData.set(HATCH, t.getInt("hatch"));
        entityData.set(MUT, t.getInt("mut"));
        if (t.hasUUID("owner")) owner = t.getUUID("owner");
    }
}
