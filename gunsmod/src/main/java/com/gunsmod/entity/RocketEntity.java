package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.world.Blast;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** RPG rocket, 40 mm grenade, 120 mm tank shell or helicopter rocket. Vehicle rounds hurt but never break blocks. */
public class RocketEntity extends Projectile {
    public static final int RPG = 0, GRENADE = 1, TANK_SHELL = 2, HELI_ROCKET = 3;
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

    /** vehicle rounds: no block damage */
    public boolean isVehicleRound() {
        return getKind() >= TANK_SHELL;
    }

    /** point the entity along its velocity (for the first rendered frame) */
    public void alignToMotion() {
        Vec3 v = this.getDeltaMovement();
        double h = v.horizontalDistance();
        this.setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (Mth.atan2(v.y, h) * Mth.RAD_TO_DEG));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        Entity owner = this.getOwner();
        if (owner != null && (e == owner.getVehicle() || e.getVehicle() == owner.getVehicle() && owner.getVehicle() != null)) return false;
        if (e.getVehicle() instanceof VehicleEntity v && v.protectsCrew()) return false;    // the round hits the vehicle
        return super.canHitEntity(e) && !BotTeam.friendly(owner, e);
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        Vec3 v = this.getDeltaMovement();
        if (life > (isVehicleRound() ? 0 : 2)) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                if (!this.level().isClientSide) {
                    if (hit instanceof EntityHitResult eh) directHit(eh.getEntity());
                    detonate(hit.getLocation());
                }
                return;
            }
        }
        switch (getKind()) {
            case RPG -> {
                if (life > 4 && v.length() < 4.0) v = v.scale(1.035);
                v = v.add(0, -0.006, 0);
            }
            case TANK_SHELL -> v = v.add(0, -0.03, 0);
            case HELI_ROCKET -> {
                if (v.length() < 4.2) v = v.scale(1.06);
                v = v.add(0, -0.004, 0);
            }
            default -> v = v.scale(0.99).add(0, -0.05, 0);
        }
        this.setDeltaMovement(v);
        this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        this.updateRotation();
        if (this.level().isClientSide) {
            if (getKind() == TANK_SHELL) {
                // glowing tracer base and a thin smoke line
                this.level().addParticle(Reg.TRACER.get(), getX(), getY(), getZ(), v.x, v.y, v.z);
                if (life % 2 == 0) this.level().addParticle(Reg.SMOKE.get(), getX() - v.x * 0.5, getY() - v.y * 0.5, getZ() - v.z * 0.5, 0, 0.005, 0);
            } else if (getKind() == HELI_ROCKET) {
                for (int i = 0; i < 4; i++) {
                    double f = i / 4.0;
                    this.level().addParticle(Reg.SMOKE.get(), getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f, 0, 0.01, 0);
                }
                this.level().addParticle(Reg.FLAME.get(), getX() - v.x * 0.15, getY() - v.y * 0.15, getZ() - v.z * 0.15, -v.x * 0.04, -v.y * 0.04, -v.z * 0.04);
            } else if (getKind() == 0) {
                for (int i = 0; i < 3; i++) {
                    double f = i / 3.0;
                    this.level().addParticle(Reg.SMOKE.get(), getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f, 0, 0.01, 0);
                }
                this.level().addParticle(Reg.FLAME.get(), getX() - v.x * 0.2, getY() - v.y * 0.2, getZ() - v.z * 0.2, -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
            } else if (life % 2 == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
            }
        } else if (isVehicleRound() && life > 110) {
            this.discard();
        } else if (life > 240 || this.isInWater() && getKind() == 1) {
            detonate(this.position());
        }
    }

    /** the round itself hits hard before it bursts: tanks need a few hits, soldiers do not survive one */
    private void directHit(Entity e) {
        if (!isVehicleRound()) return;
        Entity owner = this.getOwner();
        boolean armour = e instanceof VehicleEntity || e instanceof JeepEntity;
        float dmg = getKind() == TANK_SHELL ? (armour ? 55f : 40f) : (armour ? 16f : 14f);
        e.invulnerableTime = 0;
        e.hurt(this.damageSources().explosion(this, owner), dmg);
        e.invulnerableTime = 0;
    }

    private void detonate(Vec3 at) {
        Entity owner = this.getOwner();
        if (isVehicleRound()) {
            // same blast as the launch station missiles, but the terrain stays as it is
            boolean shell = getKind() == TANK_SHELL;
            Blast.explode(this.level(), this, at, shell ? 3.2f : 2.6f, false, Level.ExplosionInteraction.NONE, Blast.FX_MISSILE, shell ? 1.15f : 0.85f);
            this.discard();
            return;
        }
        Level.ExplosionInteraction mode = owner instanceof Player ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.MOB;
        Blast.explode(this.level(), this, at, getKind() == 0 ? 3.6f : 2.4f, false, mode, Blast.FX_SMALL, getKind() == 0 ? 1.0f : 0.7f);
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
