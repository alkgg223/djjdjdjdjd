package com.gunsmod.entity;

import com.gunsmod.Reg;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Thrown frag or smoke grenade. Bounces, then explodes (frag) or releases a smoke screen that blocks soldier vision. */
public class GrenadeEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> DATA_SMOKE = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);

    public record SmokeCloud(ResourceKey<Level> dim, Vec3 center, double radius, long until) {}

    private static final List<SmokeCloud> CLOUDS = new CopyOnWriteArrayList<>();

    public float spin, prevSpin;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
        super(type, level);
    }

    public GrenadeEntity(Level level, LivingEntity owner, boolean smoke) {
        this(Reg.GRENADE.get(), level);
        this.setOwner(owner);
        this.entityData.set(DATA_SMOKE, smoke);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_SMOKE, false);
        b.define(DATA_AGE, 0);
    }

    public boolean isSmoke() {
        return this.entityData.get(DATA_SMOKE);
    }

    public static boolean smokeBlocks(Level level, Vec3 a, Vec3 b) {
        if (CLOUDS.isEmpty()) return false;
        long now = level.getGameTime();
        CLOUDS.removeIf(c -> c.until < now - 20 || c.until > now + 2000);
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        for (SmokeCloud c : CLOUDS) {
            if (c.dim != level.dimension() || c.until < now) continue;
            double t = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, c.center.subtract(a).dot(ab) / len2));
            if (a.add(ab.scale(t)).distanceToSqr(c.center) < c.radius * c.radius) return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        int age = this.entityData.get(DATA_AGE) + 1;
        if (!this.level().isClientSide) this.entityData.set(DATA_AGE, age);
        prevSpin = spin;
        Vec3 v = this.getDeltaMovement().add(0, -0.05, 0);
        this.move(MoverType.SELF, v);
        Vec3 after = this.getDeltaMovement();
        if (this.horizontalCollision) v = new Vec3(-v.x * 0.4, v.y, -v.z * 0.4);
        if (this.verticalCollision) {
            if (v.y < -0.12 && !this.level().isClientSide) {
                this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("grenade_bounce"), SoundSource.NEUTRAL, 0.6f, 1.0f + random.nextFloat() * 0.3f);
            }
            v = new Vec3(v.x * 0.6, v.y < 0 ? -v.y * 0.3 : v.y, v.z * 0.6);
            if (Math.abs(v.y) < 0.06) v = new Vec3(v.x * 0.7, 0, v.z * 0.7);
        }
        v = v.scale(0.985);
        this.setDeltaMovement(v);
        if (after.lengthSqr() > 0.001) spin += 25f;
        boolean smoke = isSmoke();
        if (!smoke) {
            if (age >= 60 && !this.level().isClientSide) {
                Entity owner = this.getOwner();
                this.level().explode(this, getX(), getY() + 0.2, getZ(), 2.8f, false,
                        owner instanceof Player ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.MOB);
                this.discard();
            } else if (this.level().isClientSide && age % 3 == 0) {
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 0, 0.02, 0);
            }
        } else {
            if (age == 25 && !this.level().isClientSide) {
                CLOUDS.add(new SmokeCloud(this.level().dimension(), this.position().add(0, 1.5, 0), 4.5, this.level().getGameTime() + 320));
                this.level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1f, 0.6f);
            }
            if (age >= 25 && this.level().isClientSide) {
                int n = age < 60 ? 6 : 3;
                for (int i = 0; i < n; i++) {
                    this.level().addParticle(Reg.SMOKE.get(), getX(), getY() + 0.3, getZ(),
                            (random.nextDouble() - 0.5) * 0.25, 0.03 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.25);
                }
            }
            if (age > 340 && !this.level().isClientSide) this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Smoke", isSmoke());
        tag.putInt("Age", this.entityData.get(DATA_AGE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_SMOKE, tag.getBoolean("Smoke"));
        this.entityData.set(DATA_AGE, tag.getInt("Age"));
    }
}
