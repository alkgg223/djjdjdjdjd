package com.vehicleworks.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * A simple person on board: a passenger that only sits in its seat, or the AI first officer that
 * helps the pilot (gear, terrain, stall, lights). They exist only while seated in a vehicle.
 */
public class NpcEntity extends PathfinderMob {
    public static final int PASSENGER = 0, FIRST_OFFICER = 1;
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private int unseated;

    public NpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ROLE, PASSENGER);
    }

    public int role() {
        return entityData.get(ROLE);
    }

    public void setRole(int r) {
        entityData.set(ROLE, r);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        // a person without a seat leaves the aircraft (deplaned, seat removed, vehicle gone)
        boolean seated = getVehicle() instanceof VehicleEntity v && v.seatOf(this) != null;
        if (!seated && ++unseated > 40) discard();
        if (seated) unseated = 0;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) && !isPassenger() && super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Role", role());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        setRole(t.getInt("Role"));
    }
}
