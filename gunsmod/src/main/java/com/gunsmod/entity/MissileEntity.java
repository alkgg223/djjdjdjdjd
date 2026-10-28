package com.gunsmod.entity;

import com.gunsmod.Reg;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Guided missile: boosts straight up, cruises (or re-enters above far targets) and dives onto the target block. */
public class MissileEntity extends Projectile {
    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BYTE);
    public static final TicketType<ChunkPos> TICKET = TicketType.create("gunsmod_missile", Comparator.comparingLong(ChunkPos::toLong), 60);

    private BlockPos target = BlockPos.ZERO;
    private int phase;
    private int t;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public MissileEntity(Level level, LivingEntity owner, Vec3 start, BlockPos target, int kind) {
        this(Reg.MISSILE.get(), level);
        if (owner != null) this.setOwner(owner);
        this.target = target;
        this.entityData.set(DATA_KIND, (byte) kind);
        this.setPos(start.x, start.y, start.z);
        this.setDeltaMovement(0, 0.25, 0);
        this.setXRot(-90f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_KIND, (byte) 0);
    }

    public int getKind() {
        return this.entityData.get(DATA_KIND);
    }

    @Override
    public void tick() {
        super.tick();
        t++;
        Vec3 v = this.getDeltaMovement();
        Vec3 tgt = Vec3.atCenterOf(target);
        if (!this.level().isClientSide) {
            ServerLevel sl = (ServerLevel) this.level();
            sl.getChunkSource().addRegionTicket(TICKET, new ChunkPos(this.blockPosition()), 2, new ChunkPos(this.blockPosition()));
            switch (phase) {
                case 0 -> {
                    v = new Vec3(v.x * 0.9, Math.min(1.6, v.y + 0.07), v.z * 0.9);
                    if (t > 50 || this.getY() > tgt.y + 90 || this.getY() > this.level().getMaxBuildHeight() + 20) {
                        double hd = Math.sqrt(this.distanceToSqr(tgt.x, this.getY(), tgt.z));
                        if (hd > 220) {
                            // long range: re-enter the atmosphere above the target
                            ChunkPos cp = new ChunkPos(target);
                            sl.getChunkSource().addRegionTicket(TICKET, cp, 2, cp);
                            sl.getChunk(cp.x, cp.z);
                            double y = Math.max(this.getY(), tgt.y + 100);
                            this.teleportTo(tgt.x + 0.5, y, tgt.z + 0.5);
                            v = new Vec3(0, -1.5, 0);
                            phase = 2;
                        } else {
                            phase = 1;
                        }
                    }
                }
                case 1 -> {
                    Vec3 aim = new Vec3(tgt.x, Math.max(tgt.y + 30, this.getY() - 2), tgt.z);
                    Vec3 desired = aim.subtract(this.position()).normalize().scale(2.2);
                    v = v.lerp(desired, 0.07);
                    double hd = Math.sqrt(this.distanceToSqr(tgt.x, this.getY(), tgt.z));
                    if (hd < 30 + (this.getY() - tgt.y) * 0.4) phase = 2;
                }
                default -> {
                    Vec3 desired = tgt.subtract(this.position()).normalize().scale(2.8);
                    v = v.lerp(desired, 0.25);
                }
            }
            this.setDeltaMovement(v);
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (t > 20 && (hit.getType() != HitResult.Type.MISS || this.position().distanceToSqr(tgt) < 4.0)) {
                impact(hit.getType() != HitResult.Type.MISS ? hit.getLocation() : tgt);
                return;
            }
            if (t > 1600 || this.getY() < this.level().getMinBuildHeight() - 10) {
                this.discard();
                return;
            }
        }
        this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        // orientation from velocity
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        this.setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (Mth.atan2(v.y, h) * Mth.RAD_TO_DEG));
        if (this.level().isClientSide) {
            Vec3 back = v.lengthSqr() > 1e-4 ? v.normalize().scale(-1.6) : new Vec3(0, -1.6, 0);
            for (int i = 0; i < 4; i++) {
                double f = i / 4.0;
                this.level().addParticle(Reg.MISSILE_SMOKE.get(), getX() + back.x - v.x * f, getY() + back.y - v.y * f, getZ() + back.z - v.z * f,
                        (random.nextDouble() - 0.5) * 0.05, (random.nextDouble() - 0.5) * 0.05, (random.nextDouble() - 0.5) * 0.05);
            }
            this.level().addParticle(Reg.FLAME.get(), getX() + back.x * 0.8, getY() + back.y * 0.8, getZ() + back.z * 0.8, back.x * 0.1, back.y * 0.1, back.z * 0.1);
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return t > 30 && super.canHitEntity(e);
    }

    private void impact(Vec3 at) {
        Level level = this.level();
        int kind = getKind();
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 8f, 0.6f);
        switch (kind) {
            case 1 -> {
                level.explode(this, at.x, at.y, at.z, 3.5f, false, Level.ExplosionInteraction.TNT);
                for (int i = 0; i < 10; i++) {
                    double a = random.nextDouble() * Mth.TWO_PI, r = 3 + random.nextDouble() * 9;
                    int x = Mth.floor(at.x + Math.cos(a) * r), z = Mth.floor(at.z + Math.sin(a) * r);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    level.explode(this, x + 0.5, y + 0.5, z + 0.5, 2.6f, false, Level.ExplosionInteraction.TNT);
                }
            }
            case 2 -> {
                level.explode(this, at.x, at.y, at.z, 4.0f, true, Level.ExplosionInteraction.TNT);
                for (int i = 0; i < 60; i++) {
                    double a = random.nextDouble() * Mth.TWO_PI, r = random.nextDouble() * 8;
                    int x = Mth.floor(at.x + Math.cos(a) * r), z = Mth.floor(at.z + Math.sin(a) * r);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).isAir() && BaseFireBlock.canBePlacedAt(level, p, net.minecraft.core.Direction.UP)) {
                        level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
                    }
                }
            }
            default -> level.explode(this, at.x, at.y, at.z, 7.0f, false, Level.ExplosionInteraction.TNT);
        }
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TX", target.getX());
        tag.putInt("TY", target.getY());
        tag.putInt("TZ", target.getZ());
        tag.putInt("Phase", phase);
        tag.putInt("T", t);
        tag.putByte("Kind", (byte) getKind());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        target = new BlockPos(tag.getInt("TX"), tag.getInt("TY"), tag.getInt("TZ"));
        phase = tag.getInt("Phase");
        t = tag.getInt("T");
        this.entityData.set(DATA_KIND, tag.getByte("Kind"));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 512 * 512;
    }
}
