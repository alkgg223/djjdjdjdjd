package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.world.Blast;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A heavyweight torpedo: it runs straight at its depth under the water, turns gently onto an enemy ship that comes
 * into its seeker's cone ahead, and its warhead breaks a ship's back. Out of the water it drops and goes off.
 */
public class TorpedoEntity extends Projectile {
    private BotTeam team = BotTeam.GREEN;
    private int life;
    public float spin, prevSpin;

    public TorpedoEntity(EntityType<? extends TorpedoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public TorpedoEntity(Level level, @Nullable LivingEntity owner, BotTeam team, Vec3 at, float yaw) {
        this(Reg.TORPEDO.get(), level);
        this.setOwner(owner);
        this.team = team;
        this.moveTo(at.x, at.y, at.z, yaw, 0);
        float r = yaw * Mth.DEG_TO_RAD;
        this.setDeltaMovement(new Vec3(-Mth.sin(r), 0, Mth.cos(r)).scale(0.9));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
    }

    @Override
    public void tick() {
        super.tick();
        prevSpin = spin;
        spin += 30f;
        Vec3 v = this.getDeltaMovement();
        boolean wet = this.level().getFluidState(this.blockPosition()).is(FluidTags.WATER);
        if (this.level().isClientSide) {
            if (wet && this.tickCount % 2 == 0) this.level().addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE, getX(), getY(), getZ(), 0, 0.05, 0);
            return;
        }
        if (++life > 400) {
            this.discard();
            return;
        }
        if (!wet) {
            v = v.add(0, -0.06, 0);
        } else if (life > 10) {
            // the seeker: a ship of the other side in a 40 degree cone ahead within 40 blocks
            ShipEntity best = null;
            double bd = 40 * 40;
            Vec3 dir = v.normalize();
            for (ShipEntity s : this.level().getEntitiesOfClass(ShipEntity.class, this.getBoundingBox().inflate(40, 12, 40), s -> s.isAlive() && s.getBotTeam() != team)) {
                Vec3 to = s.position().subtract(this.position());
                double d = to.lengthSqr();
                if (d < bd && to.normalize().dot(dir) > 0.75) {
                    bd = d;
                    best = s;
                }
            }
            if (best != null) {
                Vec3 to = best.position().add(0, best.getBbHeight() * 0.3, 0).subtract(this.position()).normalize().scale(0.9);
                v = v.add(to.subtract(v).scale(0.06));
            } else {
                v = new Vec3(v.x, v.y * 0.8, v.z);
            }
        }
        Vec3 next = this.position().add(v);
        HitResult hr = this.level().clip(new ClipContext(this.position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hr.getType() != HitResult.Type.MISS) {
            explode(hr.getLocation());
            return;
        }
        for (Entity e : this.level().getEntities(this, this.getBoundingBox().expandTowards(v).inflate(1.0),
                e -> e instanceof LivingEntity le && le.isAlive() && BotTeam.of(e) != team && e != this.getOwner())) {
            explode(e.position());
            return;
        }
        this.setDeltaMovement(v);
        this.setPos(next.x, next.y, next.z);
        float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90f;
        this.setYRot(yaw);
    }

    private void explode(Vec3 at) {
        Blast.explode(this.level(), this, at, 5.0f, false, Level.ExplosionInteraction.NONE, Blast.FX_HEAVY, 2.2f);
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(6), e -> e instanceof VehicleEntity)) {
            double d = e.position().distanceTo(at);
            e.invulnerableTime = 0;
            e.hurt(this.damageSources().explosion(this, this.getOwner()), (float) (260 * Math.max(0.2, 1 - d / 8)));
            e.invulnerableTime = 0;
        }
        for (int i = 0; i < 30; i++) {
            ((net.minecraft.server.level.ServerLevel) this.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, at.x, at.y + 1, at.z, 20, 2, 3, 2, 0.4);
        }
        this.discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160 * 160;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Team", team.ordinal());
        tag.putInt("Life", life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        team = BotTeam.byId(tag.getInt("Team"));
        life = tag.getInt("Life");
    }
}
