package com.vehicleworks.weapon;

import com.vehicleworks.Config;
import com.vehicleworks.registry.ModEntities;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleRaycast;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;

/**
 * Fictional projectiles fired by vehicle weapons: tracer bullets, explosive shells, guided
 * missiles and free-fall bombs. Moves with its own ballistic integration and ray casts
 * blocks, entities and vehicle structures every tick.
 */
public class ProjectileEntity extends Projectile {
    public enum Kind { BULLET, SHELL, MISSILE, BOMB }

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(ProjectileEntity.class, EntityDataSerializers.BYTE);
    public float damage = 5;
    public float explosion;
    private int life;
    private int targetId = -1;
    private int sourceVehicleId = -1;
    private int boostTicks = 120;

    public ProjectileEntity(EntityType<? extends ProjectileEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public ProjectileEntity(Level level, Kind kind, VehicleEntity source, Vector3d pos, Vector3d velocityMs) {
        this(ModEntities.PROJECTILE.get(), level);
        entityData.set(KIND, (byte) kind.ordinal());
        setPos(pos.x, pos.y, pos.z);
        setDeltaMovement(velocityMs.x / 20, velocityMs.y / 20, velocityMs.z / 20);
        sourceVehicleId = source.getId();
        xo = pos.x; yo = pos.y; zo = pos.z;
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(entityData.get(KIND), Kind.values().length)];
    }

    public void setTarget(@Nullable Entity e) {
        targetId = e == null ? -1 : e.getId();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, (byte) 0);
    }

    @Override
    public void tick() {
        super.tick();
        Kind kind = kind();
        Vec3 vel = getDeltaMovement();
        if (!level().isClientSide) {
            if (++life > (kind == Kind.BULLET ? 60 : 400)) {
                if (kind != Kind.BULLET) detonate(position());
                discard();
                return;
            }
        }
        // forces (per tick, blocks/tick units)
        double g = 9.81 / 400.0;
        switch (kind) {
            case BULLET -> vel = vel.add(0, -g * 0.5, 0).scale(0.995);
            case SHELL -> vel = vel.add(0, -g, 0).scale(0.997);
            case BOMB -> vel = vel.add(0, -g, 0).scale(0.992);
            case MISSILE -> vel = guideMissile(vel, g);
        }
        Vec3 from = position();
        Vec3 to = from.add(vel);
        if (!level().isClientSide) {
            HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 end = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : to;
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(level(), this, from, end, getBoundingBox().expandTowards(vel).inflate(1.0),
                    e -> e.isPickable() && !e.isSpectator() && e != getOwner() && !(e instanceof ProjectileEntity) && (life > 4 || e.getRootVehicle().getId() != sourceVehicleId));
            if (eh != null) { hit = eh; end = eh.getLocation(); }
            VehicleEntity exclude = life < 6 ? (level().getEntity(sourceVehicleId) instanceof VehicleEntity v ? v : null) : null;
            VehicleRaycast.Hit vh = VehicleRaycast.raycast(level(), from, end, exclude);
            if (vh != null) {
                onVehicleHit(vh);
                return;
            }
            if (hit.getType() == HitResult.Type.ENTITY) {
                onEntityHit(((EntityHitResult) hit).getEntity(), hit.getLocation());
                return;
            }
            if (hit.getType() == HitResult.Type.BLOCK) {
                onTerrainHit((BlockHitResult) hit);
                return;
            }
        } else {
            if (kind == Kind.MISSILE) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 0, 0.02, 0);
                level().addParticle(ParticleTypes.FLAME, from.x, from.y, from.z, 0, 0, 0);
            } else if (kind == Kind.SHELL && tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.SMOKE, from.x, from.y, from.z, 0, 0, 0);
            }
        }
        setDeltaMovement(vel);
        setPos(to.x, to.y, to.z);
        // orient for rendering
        double h = vel.horizontalDistance();
        setYRot((float) (Math.atan2(vel.x, vel.z) * 180 / Math.PI));
        setXRot((float) (Math.atan2(vel.y, h) * 180 / Math.PI));
    }

    private Vec3 guideMissile(Vec3 vel, double g) {
        double speed = vel.length();
        Vec3 dir = speed > 1e-4 ? vel.scale(1 / speed) : new Vec3(0, 0, 1);
        Entity target = targetId >= 0 ? level().getEntity(targetId) : null;
        if (target != null && target.isAlive() && life > 3) {
            Vec3 tp = target.position().add(0, target.getBbHeight() * 0.5, 0);
            Vec3 tv = target instanceof VehicleEntity v ? new Vec3(v.linearVelocity.x / 20, v.linearVelocity.y / 20, v.linearVelocity.z / 20) : target.getDeltaMovement();
            double dist = tp.distanceTo(position());
            double tGo = dist / Math.max(1, speed);
            Vec3 lead = tp.add(tv.scale(Math.min(tGo, 40))).subtract(position()).normalize();
            double turn = 0.12; // rad per tick max
            double cos = Math.max(-1, Math.min(1, dir.dot(lead)));
            double ang = Math.acos(cos);
            if (ang > 1e-4) {
                double f = Math.min(1, turn / ang);
                dir = dir.scale(1 - f).add(lead.scale(f)).normalize();
            }
        }
        double accel = boostTicks-- > 0 ? 0.9 : 0.0; // blocks/tick² (≈18 m/s per tick)
        double newSpeed = Math.min(14.0, speed + accel * 0.1);
        if (boostTicks < 0) newSpeed *= 0.99;
        return dir.scale(newSpeed).add(0, boostTicks < 0 ? -g : 0, 0);
    }

    private void onVehicleHit(VehicleRaycast.Hit h) {
        Kind kind = kind();
        if (kind == Kind.BULLET) {
            h.vehicle().damageBlock(h.block(), damage);
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.CRIT, h.location().x, h.location().y, h.location().z, 4, 0.1, 0.1, 0.1, 0.2);
            discard();
        } else {
            h.vehicle().damageBlock(h.block(), damage * 4);
            detonate(h.location());
        }
    }

    private void onEntityHit(Entity e, Vec3 at) {
        Kind kind = kind();
        Entity owner = getOwner();
        e.hurt(damageSources().mobProjectile(this, owner instanceof net.minecraft.world.entity.LivingEntity le ? le : null), damage);
        if (kind == Kind.BULLET) discard();
        else detonate(at);
    }

    private void onTerrainHit(BlockHitResult hit) {
        Kind kind = kind();
        Vec3 at = hit.getLocation();
        if (kind == Kind.BULLET) {
            if (level() instanceof ServerLevel sl) {
                var st = level().getBlockState(hit.getBlockPos());
                sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st), at.x, at.y, at.z, 6, 0.05, 0.05, 0.05, 0.1);
            }
            discard();
        } else {
            detonate(at);
        }
    }

    private void detonate(Vec3 at) {
        if (level() instanceof ServerLevel sl && explosion > 0) {
            Level.ExplosionInteraction mode = Config.SERVER.weaponsBreakBlocks.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
            sl.explode(this, at.x, at.y, at.z, explosion, false, mode);
        }
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getByte("Kind"));
        damage = tag.getFloat("Damage");
        explosion = tag.getFloat("Explosion");
        life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Kind", entityData.get(KIND));
        tag.putFloat("Damage", damage);
        tag.putFloat("Explosion", explosion);
        tag.putInt("Life", life);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }
}
