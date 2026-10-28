package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.item.MissileItem;
import com.gunsmod.world.Blast;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Guided missile: boosts straight up, cruises (or re-enters above far targets), dives onto the target block and then
 * runs its warhead's detonation sequence over a few ticks (cluster bomblets, bunker buster drilling, Titan blast rings).
 */
public class MissileEntity extends Projectile {
    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_DETONATING = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BOOLEAN);
    public static final TicketType<ChunkPos> TICKET = TicketType.create("gunsmod_missile", Comparator.comparingLong(ChunkPos::toLong), 60);

    private BlockPos target = BlockPos.ZERO;
    private int phase;
    private int t;
    private boolean whistled;
    /** detonation sequence clock and impact point */
    private int boom = -1;
    private Vec3 blast = Vec3.ZERO;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public MissileEntity(Level level, Entity owner, Vec3 start, BlockPos target, int kind) {
        this(Reg.MISSILE.get(), level);
        if (owner != null) this.setOwner(owner);
        this.target = target;
        this.entityData.set(DATA_KIND, (byte) kind);
        this.setPos(start.x, start.y, start.z);
        this.setDeltaMovement(0, isHeavy() ? 0.12 : 0.25, 0);
        this.setXRot(-90f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_KIND, (byte) 0);
        b.define(DATA_DETONATING, false);
    }

    public int getKind() {
        return this.entityData.get(DATA_KIND);
    }

    public boolean isHeavy() {
        return MissileItem.isHeavy(getKind());
    }

    public boolean isDetonating() {
        return this.entityData.get(DATA_DETONATING);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && boom >= 0) {
            detonationTick((ServerLevel) this.level());
            return;
        }
        if (isDetonating()) return;
        t++;
        boolean heavy = isHeavy();
        Vec3 v = this.getDeltaMovement();
        Vec3 tgt = Vec3.atCenterOf(target);
        if (!this.level().isClientSide) {
            ServerLevel sl = (ServerLevel) this.level();
            sl.getChunkSource().addRegionTicket(TICKET, new ChunkPos(this.blockPosition()), 2, new ChunkPos(this.blockPosition()));
            double cruise = heavy ? 1.9 : 2.2, dive = heavy ? 2.6 : 2.8;
            switch (phase) {
                case 0 -> {
                    v = new Vec3(v.x * 0.9, Math.min(heavy ? 1.3 : 1.6, v.y + (heavy ? 0.045 : 0.07)), v.z * 0.9);
                    if (t > (heavy ? 70 : 50) || this.getY() > tgt.y + 90 || this.getY() > this.level().getMaxBuildHeight() + 20) {
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
                    Vec3 desired = aim.subtract(this.position()).normalize().scale(cruise);
                    v = v.lerp(desired, 0.07);
                    double hd = Math.sqrt(this.distanceToSqr(tgt.x, this.getY(), tgt.z));
                    if (hd < 30 + (this.getY() - tgt.y) * 0.4) phase = 2;
                }
                default -> {
                    Vec3 desired = tgt.subtract(this.position()).normalize().scale(dive);
                    v = v.lerp(desired, 0.25);
                    if (!whistled && this.position().distanceToSqr(tgt) < 70 * 70) {
                        whistled = true;
                        this.level().playSound(null, tgt.x, tgt.y + 4, tgt.z, Reg.sound("missile_incoming"), SoundSource.HOSTILE, heavy ? 8f : 5f, heavy ? 0.8f : 1f);
                    }
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
        if (this.level().isClientSide) trail(v, heavy);
    }

    private void trail(Vec3 v, boolean heavy) {
        double len = heavy ? 2.6 : 1.6;
        Vec3 back = v.lengthSqr() > 1e-4 ? v.normalize().scale(-len) : new Vec3(0, -len, 0);
        int n = heavy ? 7 : 4;
        for (int i = 0; i < n; i++) {
            double f = i / (double) n;
            double j = heavy ? 0.15 : 0.05;
            this.level().addParticle(Reg.MISSILE_SMOKE.get(), getX() + back.x - v.x * f, getY() + back.y - v.y * f, getZ() + back.z - v.z * f,
                    (random.nextDouble() - 0.5) * j, (random.nextDouble() - 0.5) * j, (random.nextDouble() - 0.5) * j);
        }
        this.level().addParticle(Reg.FLAME.get(), getX() + back.x * 0.8, getY() + back.y * 0.8, getZ() + back.z * 0.8, back.x * 0.1, back.y * 0.1, back.z * 0.1);
        if (heavy) {
            this.level().addParticle(Reg.FIREBALL.get(), getX() + back.x * 0.85, getY() + back.y * 0.85, getZ() + back.z * 0.85,
                    back.x * 0.04, back.y * 0.04, back.z * 0.04);
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return t > 30 && super.canHitEntity(e);
    }

    // ------------------------------------------------------------------------------------ warheads
    private void impact(Vec3 at) {
        blast = at;
        boom = 0;
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(at.x, at.y, at.z);
        this.noPhysics = true;
        this.entityData.set(DATA_DETONATING, true);
        detonationTick((ServerLevel) this.level());
    }

    /** one step of the warhead's detonation; discards the missile when the sequence is over */
    private void detonationTick(ServerLevel level) {
        int step = boom++;
        Entity owner = this.getOwner();
        Vec3 at = blast;
        Level.ExplosionInteraction tnt = Level.ExplosionInteraction.TNT;
        boolean done;
        switch (getKind()) {
            case MissileItem.CLUSTER -> {
                if (step == 0) Blast.explode(level, this, at, 3.5f, false, tnt, Blast.FX_MISSILE, 1.4f);
                else bomblet(level, at, 3 + random.nextDouble() * 10, 2.6f, false);
                done = step >= 10;
            }
            case MissileItem.INCENDIARY -> {
                Blast.explode(level, this, at, 4.0f, true, tnt, Blast.FX_FIRE, 1.8f);
                Blast.firestorm(level, at, 8, 60);
                Blast.scorch(level, at, 8, true, false);
                done = true;
            }
            case MissileItem.THERMOBARIC -> {
                if (step == 0) {
                    Blast.explode(level, this, at, 9.0f, true, tnt, Blast.FX_FIRE, 3.2f);
                    Blast.shockwave(level, at, 28, 26f, 8, this, owner);
                } else if (step <= 3) {
                    for (int i = 0; i < 2; i++) ring(level, at, 8, (step - 1) * 2 + i, 6, 5.5f, true);
                } else if (step == 4) {
                    Blast.firestorm(level, at, 16, 130);
                    Blast.scorch(level, at, 16, true, true);
                }
                done = step >= 4;
            }
            case MissileItem.BUNKER_BUSTER -> {
                // drills down through 15 blocks, then the main charge erupts from below
                if (step == 0) {
                    Blast.explode(level, this, at, 3.0f, false, tnt, Blast.FX_ERUPTION, 1.2f);
                } else if (step % 3 == 0 && step <= 15) {
                    Vec3 p = at.add(0, -step, 0);
                    level.explode(this, p.x, p.y, p.z, 4.5f, false, tnt);
                } else if (step == 18) {
                    Vec3 p = at.add(0, -15, 0);
                    level.explode(this, p.x, p.y, p.z, 11.0f, false, tnt);
                    Blast.fx(level, at, Blast.FX_ERUPTION, 3.0f);
                    Blast.shockwave(level, p, 20, 30f, 0, this, owner);
                }
                done = step >= 18;
            }
            case MissileItem.TITAN -> {
                if (step == 0) {
                    Blast.explode(level, this, at, 14.0f, true, tnt, Blast.FX_TITAN, 4.0f);
                    Blast.shockwave(level, at, 64, 44f, 10, this, owner);
                } else if (step <= 4) {
                    for (int i = 0; i < 2; i++) ring(level, at, 8, (step - 1) * 2 + i, 10, 8.0f, true);
                } else if (step <= 8) {
                    for (int i = 0; i < 2; i++) ring(level, at, 8, (step - 5) * 2 + i, 18, 6.0f, true);
                } else if (step == 9) {
                    Blast.firestorm(level, at, 28, 220);
                    Blast.scorch(level, at, 26, true, true);
                }
                done = step >= 9;
            }
            default -> {
                Blast.explode(level, this, at, 7.0f, false, tnt, Blast.FX_MISSILE, 2.0f);
                Blast.scorch(level, at, 6, false, false);
                done = true;
            }
        }
        if (done) this.discard();
    }

    /** blast number {@code i} of {@code n} evenly around a ring, dropped onto the surface */
    private void ring(ServerLevel level, Vec3 at, int n, int i, double radius, float power, boolean fire) {
        double a = Mth.TWO_PI * i / n + 0.3;
        int x = Mth.floor(at.x + Math.cos(a) * radius), z = Mth.floor(at.z + Math.sin(a) * radius);
        int y = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), Mth.floor(at.y) + 6);
        Blast.explode(level, this, new Vec3(x + 0.5, y + 0.5, z + 0.5), power, fire, Level.ExplosionInteraction.TNT, Blast.FX_SMALL, 1.3f);
    }

    private void bomblet(ServerLevel level, Vec3 at, double r, float power, boolean fire) {
        double a = random.nextDouble() * Mth.TWO_PI;
        int x = Mth.floor(at.x + Math.cos(a) * r), z = Mth.floor(at.z + Math.sin(a) * r);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        Blast.explode(level, this, new Vec3(x + 0.5, y + 0.5, z + 0.5), power, fire, Level.ExplosionInteraction.TNT, Blast.FX_SMALL, 0.8f);
    }

    // ------------------------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TX", target.getX());
        tag.putInt("TY", target.getY());
        tag.putInt("TZ", target.getZ());
        tag.putInt("Phase", phase);
        tag.putInt("T", t);
        tag.putByte("Kind", (byte) getKind());
        tag.putBoolean("Whistled", whistled);
        if (boom >= 0) {
            tag.putInt("Boom", boom);
            tag.putDouble("BX", blast.x);
            tag.putDouble("BY", blast.y);
            tag.putDouble("BZ", blast.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        target = new BlockPos(tag.getInt("TX"), tag.getInt("TY"), tag.getInt("TZ"));
        phase = tag.getInt("Phase");
        t = tag.getInt("T");
        whistled = tag.getBoolean("Whistled");
        this.entityData.set(DATA_KIND, tag.getByte("Kind"));
        if (tag.contains("Boom")) {
            boom = tag.getInt("Boom");
            blast = new Vec3(tag.getDouble("BX"), tag.getDouble("BY"), tag.getDouble("BZ"));
            this.noPhysics = true;
            this.entityData.set(DATA_DETONATING, true);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 512 * 512;
    }
}
