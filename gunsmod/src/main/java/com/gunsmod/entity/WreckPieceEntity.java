package com.gunsmod.entity;

import com.gunsmod.Reg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A burning piece of a destroyed aircraft (wing, fin, fuselage section, engine, skin panel): thrown off by the blast,
 * tumbling as it falls, it lands with a thud and lies smouldering on the ground for a minute before it is gone.
 */
public class WreckPieceEntity extends Entity {
    public static final String[] KINDS = {"wing", "fin", "fuselage", "engine", "panel"};
    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(WreckPieceEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(WreckPieceEntity.class, EntityDataSerializers.FLOAT);
    /** a vehicle's burnt-out hulk: the model it shows (empty for an aircraft piece) */
    private static final EntityDataAccessor<String> DATA_MODEL = SynchedEntityData.defineId(WreckPieceEntity.class, EntityDataSerializers.STRING);
    public static final int LIFE = 1400, BURN = 500, HULK_LIFE = 3600, HULK_BURN = 1400;
    private int life;
    private boolean landed;
    // client: tumbling
    public float spin, spinO, spinRate, tiltX, tiltZ;

    public WreckPieceEntity(EntityType<? extends WreckPieceEntity> type, Level level) {
        super(type, level);
        spinRate = 8f + level.random.nextFloat() * 16f;
        tiltX = level.random.nextFloat() * 360f;
        tiltZ = level.random.nextFloat() * 360f;
    }

    public static WreckPieceEntity of(Level level, int kind, float scale, Vec3 at, Vec3 vel) {
        WreckPieceEntity w = new WreckPieceEntity(Reg.WRECK_PIECE.get(), level);
        w.entityData.set(DATA_KIND, (byte) kind);
        w.entityData.set(DATA_SCALE, scale);
        w.setPos(at.x, at.y, at.z);
        w.setDeltaMovement(vel);
        w.setYRot(level.random.nextFloat() * 360f);
        return w;
    }

    /** a destroyed vehicle's hulk (or its turret blown off): the vehicle's own model, charred, smoking and burning a while */
    public static WreckPieceEntity hulk(Level level, String model, float scale, Vec3 at, float yaw, Vec3 vel) {
        WreckPieceEntity w = new WreckPieceEntity(Reg.WRECK_PIECE.get(), level);
        w.entityData.set(DATA_MODEL, model);
        w.entityData.set(DATA_SCALE, scale);
        w.setPos(at.x, at.y, at.z);
        w.setDeltaMovement(vel);
        w.setYRot(yaw);
        w.spinRate = vel.lengthSqr() > 0.05 ? 6f + level.random.nextFloat() * 8f : 0f;
        return w;
    }

    public String model() {
        return this.entityData.get(DATA_MODEL);
    }

    public boolean isHulk() {
        return !model().isEmpty();
    }

    /** throw pieces of an aircraft about: n of them from a point with the aircraft's own motion */
    public static void scatter(Level level, Vec3 at, Vec3 motion, float scale, int n, double force) {
        for (int i = 0; i < n; i++) {
            int kind = i < 2 ? i : level.random.nextInt(KINDS.length);
            double a = level.random.nextDouble() * Math.PI * 2;
            double out = force * (0.4 + level.random.nextDouble() * 0.8);
            Vec3 v = motion.scale(0.5).add(Math.cos(a) * out, force * (0.5 + level.random.nextDouble() * 0.7), Math.sin(a) * out);
            float s = scale * (0.75f + level.random.nextFloat() * 0.4f);
            level.addFreshEntity(of(level, kind, s, at.add(Math.cos(a) * 1.5, 0.8, Math.sin(a) * 1.5), v));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_KIND, (byte) 0);
        b.define(DATA_SCALE, 1f);
        b.define(DATA_MODEL, "");
    }

    public String kind() {
        int k = this.entityData.get(DATA_KIND);
        return KINDS[Math.floorMod(k, KINDS.length)];
    }

    public float scale() {
        return this.entityData.get(DATA_SCALE);
    }

    public boolean burning() {
        return this.tickCount < (isHulk() ? HULK_BURN : BURN);
    }

    @Override
    public void tick() {
        super.tick();
        spinO = spin;
        Vec3 v = this.getDeltaMovement();
        if (!this.onGround()) v = v.scale(0.985).add(0, -0.05, 0);
        this.setDeltaMovement(v);
        this.move(MoverType.SELF, v);
        if (this.onGround()) {
            if (!landed && !this.level().isClientSide && v.y < -0.3) {
                this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("wreck_impact"), SoundSource.NEUTRAL, 2.5f, 0.8f + this.random.nextFloat() * 0.3f);
            }
            landed = true;
            this.setDeltaMovement(v.multiply(0.5, 0, 0.5));
        }
        if (this.level().isClientSide) {
            if (!this.onGround()) spin += spinRate;
            if (burning() || this.random.nextInt(6) == 0) {
                float s = scale();
                if (this.random.nextInt(burning() ? 1 : 3) == 0) {
                    this.level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + this.random.nextGaussian() * 0.4 * s, getY() + 0.3, getZ() + this.random.nextGaussian() * 0.4 * s,
                            0, 0.05, 0);
                }
                if (burning() && this.random.nextInt(2) == 0) {
                    this.level().addParticle(Reg.FLAME.get(), getX() + this.random.nextGaussian() * 0.3 * s, getY() + 0.2, getZ() + this.random.nextGaussian() * 0.3 * s,
                            0, 0.03, 0);
                }
            }
            if (burning()) FxHooks.light.accept(this, 11);
            return;
        }
        if (++life > (isHulk() ? HULK_LIFE : LIFE) || this.getY() < this.level().getMinBuildHeight() - 16) this.discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 192 * 192;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_KIND, tag.getByte("Kind"));
        this.entityData.set(DATA_SCALE, tag.contains("Scale") ? tag.getFloat("Scale") : 1f);
        this.entityData.set(DATA_MODEL, tag.getString("Model"));
        life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("Kind", this.entityData.get(DATA_KIND));
        tag.putFloat("Scale", scale());
        tag.putString("Model", model());
        tag.putInt("Life", life);
    }
}
