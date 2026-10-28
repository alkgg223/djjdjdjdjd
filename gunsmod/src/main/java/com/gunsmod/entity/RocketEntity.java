package com.gunsmod.entity;

import com.gunsmod.Reg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** RPG rocket (kind 0) or 40 mm grenade (kind 1). */
public class RocketEntity extends Projectile {
    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BYTE);
    private int life;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    public RocketEntity(Level level, LivingEntity owner, int kind) {
        this(Reg.ROCKET.get(), level);
        this.setOwner(owner);
        this.entityData.set(DATA_KIND, (byte) kind);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_KIND, (byte) 0);
    }

    public int getKind() {
        return this.entityData.get(DATA_KIND);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        Entity owner = this.getOwner();
        if (owner != null && (e == owner.getVehicle() || e.getVehicle() == owner.getVehicle() && owner.getVehicle() != null)) return false;
        return super.canHitEntity(e) && !BotTeam.friendly(owner, e);
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        Vec3 v = this.getDeltaMovement();
        if (life > 2) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                if (!this.level().isClientSide) detonate(hit.getLocation());
                return;
            }
        }
        if (getKind() == 0) {
            if (life > 4 && v.length() < 4.0) v = v.scale(1.035);
            v = v.add(0, -0.006, 0);
        } else {
            v = v.scale(0.99).add(0, -0.05, 0);
        }
        this.setDeltaMovement(v);
        this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        this.updateRotation();
        if (this.level().isClientSide) {
            if (getKind() == 0) {
                for (int i = 0; i < 3; i++) {
                    double f = i / 3.0;
                    this.level().addParticle(Reg.SMOKE.get(), getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f, 0, 0.01, 0);
                }
                this.level().addParticle(Reg.FLAME.get(), getX() - v.x * 0.2, getY() - v.y * 0.2, getZ() - v.z * 0.2, -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
            } else if (life % 2 == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
            }
        } else if (life > 240 || this.isInWater() && getKind() == 1) {
            detonate(this.position());
        }
    }

    private void detonate(Vec3 at) {
        Entity owner = this.getOwner();
        Level.ExplosionInteraction mode = owner instanceof Player ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.MOB;
        this.level().explode(this, at.x, at.y, at.z, getKind() == 0 ? 3.6f : 2.4f, false, mode);
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Kind", (byte) getKind());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_KIND, tag.getByte("Kind"));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }
}
