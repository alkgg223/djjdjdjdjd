package com.gunsmod.entity;

import com.gunsmod.Reg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Infrared decoy flare: a burning magnesium pellet thrown out of the helicopter's dispenser. It arcs down while it
 * burns for a few seconds, lighting up everything around it with a white-hot glare, sparks and a smoke trail.
 * Homing missiles may turn after it instead of the helicopter.
 */
public class FlareEntity extends Entity {
    public static final int BURN = 70;
    private int age;
    private int salvo;

    public FlareEntity(EntityType<? extends FlareEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public FlareEntity(Level level, Vec3 at, Vec3 motion, int salvo) {
        this(Reg.FLARE_ENTITY.get(), level);
        this.setPos(at.x, at.y, at.z);
        this.setDeltaMovement(motion);
        this.salvo = salvo;
    }

    public int age() {
        return age;
    }

    /** flares dropped together count as one try against a missile */
    public int salvo() {
        return salvo;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        Vec3 v = this.getDeltaMovement();
        v = new Vec3(v.x * 0.95, v.y * 0.95 - 0.025, v.z * 0.95);
        Vec3 next = this.position().add(v);
        if (!this.level().noCollision(this, this.getBoundingBox().move(v))) {
            v = new Vec3(v.x * 0.3, -v.y * 0.2, v.z * 0.3);
            next = this.position().add(v.x, Math.max(0, v.y), v.z);
        }
        this.setDeltaMovement(v);
        this.setPos(next.x, next.y, next.z);
        if (this.level().isClientSide) {
            float fade = age > BURN - 15 ? (BURN - age) / 15f : 1f;
            this.level().addParticle(Reg.FLARE.get(), getX(), getY(), getZ(), v.x * 0.2, v.y * 0.2, v.z * 0.2);
            if (this.random.nextFloat() < 0.6f * fade) {
                this.level().addParticle(Reg.SPARK.get(), getX(), getY(), getZ(), (this.random.nextDouble() - 0.5) * 0.25,
                        (this.random.nextDouble() - 0.3) * 0.2, (this.random.nextDouble() - 0.5) * 0.25);
            }
            if (age % 2 == 0) this.level().addParticle(Reg.MISSILE_SMOKE.get(), getX() - v.x, getY() - v.y + 0.1, getZ() - v.z, 0, 0.01, 0);
            FxHooks.light.accept(this, fade > 0.5f ? 15 : 11);
        } else if (age > BURN || this.isInWater()) {
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = BURN;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }
}
