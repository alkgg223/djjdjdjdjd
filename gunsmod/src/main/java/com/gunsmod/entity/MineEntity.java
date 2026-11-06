package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.world.Blast;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Anti-vehicle mine laid by a soldier. It arms a few seconds after it is laid and only goes off under an enemy
 * vehicle: soldiers and the team's own vehicles roll over it safely. A heavy blast that cripples a tank and wrecks a
 * jeep; it does not break blocks. A player can lift (defuse) a mine by hitting it.
 */
public class MineEntity extends Entity {
    private static final EntityDataAccessor<Byte> DATA_TEAM = SynchedEntityData.defineId(MineEntity.class, EntityDataSerializers.BYTE);
    public static final int ARM_TICKS = 60, LIFE = 24000;

    @Nullable private UUID owner;
    private int age;

    public MineEntity(EntityType<? extends MineEntity> type, Level level) {
        super(type, level);
    }

    public MineEntity(Level level, BotTeam team, @Nullable Entity owner, Vec3 at, float yaw) {
        this(Reg.MINE.get(), level);
        this.entityData.set(DATA_TEAM, (byte) team.ordinal());
        this.owner = owner != null ? owner.getUUID() : null;
        this.moveTo(at.x, at.y, at.z, yaw, 0f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_TEAM, (byte) 0);
    }

    public BotTeam getMineTeam() {
        return BotTeam.byId(this.entityData.get(DATA_TEAM));
    }

    public boolean isArmed() {
        return age >= ARM_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        age++;
        if (age == ARM_TICKS) this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("ui_beep"), SoundSource.NEUTRAL, 0.4f, 0.6f);
        if (age > LIFE) {
            this.discard();
            return;
        }
        if (!isArmed() || age % 2 != 0) return;
        BotTeam team = getMineTeam();
        List<Entity> over = this.level().getEntities(this, this.getBoundingBox().inflate(0.2, 1.0, 0.2), e -> e.isAlive() && isEnemyVehicle(e, team));
        if (!over.isEmpty()) detonate(over.get(0));
    }

    /** only an enemy vehicle on the ground sets it off */
    private static boolean isEnemyVehicle(Entity e, BotTeam team) {
        if (e instanceof Aircraft a && a.isAirborne()) return false;
        if (e instanceof VehicleEntity v) return v.getBotTeam() != team;
        if (e instanceof JeepEntity j) return j.getBotTeam() != team;
        return false;
    }

    private void detonate(@Nullable Entity victim) {
        if (this.isRemoved()) return;
        this.discard();
        Entity layer = owner != null && this.level() instanceof ServerLevel sl ? sl.getEntity(owner) : null;
        Vec3 at = this.position().add(0, 0.3, 0);
        Blast.explode(this.level(), this, at, 3.0f, false, Level.ExplosionInteraction.NONE, Blast.FX_HEAVY, 1.4f);
        if (victim instanceof LivingEntity v) {
            // the blast under the hull: about half the vehicle gone, more for a jeep
            boolean jeep = v instanceof JeepEntity;
            float share = jeep ? 0.6f : 0.45f;
            float taken = jeep ? 1.5f : v instanceof VehicleEntity ve && ve.isArmored() ? 0.8f : 1.3f;
            DamageSource src = this.damageSources().explosion(this, layer);
            v.hurt(src, v.getMaxHealth() * share / taken);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) return false;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            if (isArmed()) detonate(null);       // set off by a blast close by
            return true;
        }
        if (source.getEntity() instanceof Player) {
            // lifted out of the ground and made safe
            this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("cover_open"), SoundSource.PLAYERS, 1f, 0.7f);
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_TEAM, tag.getByte("Team"));
        age = tag.getInt("Age");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("Team", this.entityData.get(DATA_TEAM));
        tag.putInt("Age", age);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 64 * 64;
    }
}
