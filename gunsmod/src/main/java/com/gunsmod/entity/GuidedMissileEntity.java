package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.world.Blast;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Guided missiles. Hellfire flies to the target it was locked on; Vikhr, Shturm and TOW ride the operator's line of
 * sight; the Pantsir's SAM and the F-16's Sidewinder chase an aircraft, burst next to it and only ever harm aircraft;
 * the F-16's Maverick flies to the vehicle it was locked on.
 * Homing missiles can be pulled away by flares. Like all vehicle weapons they never break blocks.
 */
public class GuidedMissileEntity extends Projectile {
    public static final int HELLFIRE = 0, VIKHR = 1, SHTURM = 2, TOW = 3, SAM = 4, MAVERICK = 5, AIM9 = 6;
    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.BYTE);

    private int life, targetId = -1, guiderId = -1;
    private float hitArmour = 60, hitSoft = 45, blast = 3.0f;
    /** flares already tried against this missile */
    private final java.util.Set<Integer> triedFlares = new java.util.HashSet<>();

    public GuidedMissileEntity(EntityType<? extends GuidedMissileEntity> type, Level level) {
        super(type, level);
    }

    public GuidedMissileEntity(Level level, @Nullable LivingEntity owner, int kind) {
        this(Reg.GUIDED_MISSILE.get(), level);
        if (owner != null) this.setOwner(owner);
        this.entityData.set(DATA_KIND, (byte) kind);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_KIND, (byte) 0);
    }

    public int getKind() {
        return this.entityData.get(DATA_KIND);
    }

    public GuidedMissileEntity power(float armour, float soft, float blastPower) {
        hitArmour = armour;
        hitSoft = soft;
        blast = blastPower;
        return this;
    }

    /** lock-on missile: chase this entity */
    public GuidedMissileEntity target(@Nullable Entity t) {
        targetId = t == null ? -1 : t.getId();
        return this;
    }

    /** beam rider: follow the line of sight of this launcher */
    public GuidedMissileEntity guider(Entity g) {
        guiderId = g.getId();
        return this;
    }

    private double topSpeed() {
        return switch (getKind()) {
            case HELLFIRE -> 3.2;
            case VIKHR -> 4.4;
            case SAM -> 5.2;
            case AIM9 -> 6.5;
            case MAVERICK -> 3.6;
            default -> 2.6;
        };
    }

    /** degrees the missile can turn in a tick */
    private double agility() {
        return switch (getKind()) {
            case SAM -> 9.0;
            case AIM9 -> 12.0;
            case MAVERICK -> 5.0;
            case HELLFIRE -> 6.0;
            case TOW -> 5.0;
            default -> 4.0;
        };
    }

    public void alignToMotion() {
        Vec3 v = this.getDeltaMovement();
        this.setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        Entity owner = this.getOwner();
        if (owner != null && (e == owner.getVehicle() || e.getVehicle() != null && e.getVehicle() == owner.getVehicle())) return false;
        if (e instanceof FlareEntity) return false;
        if (antiAir()) return e instanceof Aircraft && super.canHitEntity(e) && !BotTeam.friendly(owner, e.getControllingPassenger());
        if (VehicleEntity.shieldOf(e) != null) return false;
        return super.canHitEntity(e) && !BotTeam.friendly(owner, e);
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        Vec3 v = this.getDeltaMovement();
        if (!this.level().isClientSide) {
            if (life > 1) {
                HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
                if (hit.getType() != HitResult.Type.MISS) {
                    if (hit instanceof EntityHitResult eh) directHit(eh.getEntity());
                    detonate(hit.getLocation());
                    return;
                }
            }
            v = steer(v);
            if (antiAir() && proximity()) return;
            if (life > (antiAir() ? 170 : 220) || this.isInWater()) {
                detonate(this.position());
                return;
            }
        }
        this.setDeltaMovement(v);
        this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        this.updateRotation();
        if (this.level().isClientSide) trail(v);
    }

    private Vec3 steer(Vec3 v) {
        double speed = Math.min(topSpeed(), v.length() * (life < 10 ? 1.22 : 1.06) + 0.05);
        Vec3 dir = v.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : v.normalize();
        Vec3 want = null;
        if (targetId >= 0) {
            Entity t = this.level().getEntity(targetId);
            if (t == null || !t.isAlive()) targetId = -1;
            else {
                decoy(t);
                t = this.level().getEntity(targetId);
                if (t != null) {
                    Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0);
                    // lead the target a little
                    Vec3 tv = t.position().subtract(t.xo, t.yo, t.zo);
                    double time = aim.distanceTo(this.position()) / Math.max(1, speed);
                    want = aim.add(tv.scale(Math.min(time, 20) * 0.8)).subtract(this.position());
                    if (t instanceof Aircraft a) a.missileWarning(this);
                }
            }
        } else if (guiderId >= 0) {
            Entity g = this.level().getEntity(guiderId);
            Vec3[] beam = g instanceof MissileGuider mg && g.isAlive() ? mg.beam() : null;
            if (beam != null) {
                // stay on the sight line, a little ahead of where the missile is now
                double along = this.position().subtract(beam[0]).dot(beam[1]);
                Vec3 onBeam = beam[0].add(beam[1].scale(Math.max(along, 0) + 6 + speed * 2));
                want = onBeam.subtract(this.position());
            } else {
                guiderId = -1;
            }
        }
        if (want != null && want.lengthSqr() > 1e-6) {
            Vec3 w = want.normalize();
            double cos = Mth.clamp(dir.dot(w), -1, 1);
            double ang = Math.acos(cos);
            double max = Math.toRadians(agility());
            if (ang <= max) dir = w;
            else {
                // rotate towards the wanted direction by the turn limit
                Vec3 perp = w.subtract(dir.scale(cos));
                if (perp.lengthSqr() > 1e-9) dir = dir.scale(Math.cos(max)).add(perp.normalize().scale(Math.sin(max))).normalize();
            }
        } else if (!antiAir()) {
            dir = dir.add(0, -0.004, 0).normalize();
        }
        return dir.scale(speed);
    }

    /** flares pull homing missiles away: each flare salvo gets one chance */
    private void decoy(Entity target) {
        if (life % 3 != 0) return;
        List<FlareEntity> flares = this.level().getEntitiesOfClass(FlareEntity.class, new AABB(target.position(), target.position()).inflate(28),
                f -> f.isAlive() && f.age() < 40 && !triedFlares.contains(f.salvo()));
        for (FlareEntity f : flares) {
            triedFlares.add(f.salvo());
            float chance = getKind() == SAM ? 0.75f : getKind() == AIM9 ? 0.65f : 0.45f;
            if (this.random.nextFloat() < chance) {
                targetId = f.getId();
                return;
            }
        }
    }

    /** surface-to-air and air-to-air missiles: only aircraft are their targets */
    public boolean antiAir() {
        return getKind() == SAM || getKind() == AIM9;
    }

    /** SAM / Sidewinder: burst when it passes close to an aircraft (or the flare it was fooled by) */
    private boolean proximity() {
        Entity t = targetId >= 0 ? this.level().getEntity(targetId) : null;
        if (t instanceof FlareEntity f && f.distanceTo(this) < 3.0) {
            Blast.fx(this.level(), this.position(), Blast.FX_SMALL, 0.8f);
            this.discard();
            return true;
        }
        for (VehicleEntity h : this.level().getEntitiesOfClass(VehicleEntity.class, this.getBoundingBox().inflate(4.5), this::canHitEntity)) {
            if (h.position().add(0, h.getBbHeight() * 0.5, 0).distanceTo(this.position()) < 4.5) {
                detonate(this.position());
                return true;
            }
        }
        return false;
    }

    private void directHit(Entity e) {
        if (antiAir()) return;   // the burst does it
        boolean armour = e instanceof VehicleEntity || e instanceof JeepEntity;
        e.invulnerableTime = 0;
        e.hurt(this.damageSources().explosion(this, this.getOwner()), armour ? hitArmour : hitSoft);
        e.invulnerableTime = 0;
    }

    /** shot down in the air (C-RAM) */
    public void intercept() {
        if (this.isRemoved()) return;
        Blast.fx(this.level(), this.position(), Blast.FX_MISSILE, 0.8f);
        this.discard();
    }

    private void detonate(Vec3 at) {
        if (antiAir()) {
            // fragmentation burst: only aircraft are hurt
            for (VehicleEntity h : this.level().getEntitiesOfClass(VehicleEntity.class, new AABB(at, at).inflate(7))) {
                if (!canHitEntity(h)) continue;
                double d = h.position().add(0, h.getBbHeight() * 0.5, 0).distanceTo(at);
                if (d > 7) continue;
                h.invulnerableTime = 0;
                h.hurt(this.damageSources().explosion(this, this.getOwner()), (float) (hitArmour * (1.0 - d / 10.0)));
                h.invulnerableTime = 0;
            }
            Blast.fx(this.level(), at, Blast.FX_MISSILE, 1.0f);
            this.level().playSound(null, at.x, at.y, at.z, Reg.sound("explosion_far"), SoundSource.NEUTRAL, 8f, 1.2f);
        } else {
            Blast.explode(this.level(), this, at, blast, false, Level.ExplosionInteraction.NONE, Blast.FX_MISSILE, blast * 0.36f);
        }
        this.discard();
    }

    private void trail(Vec3 v) {
        int n = antiAir() ? 5 : 3;
        for (int i = 0; i < n; i++) {
            double f = i / (double) n;
            this.level().addParticle(Reg.MISSILE_SMOKE.get(), getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f, 0, 0.01, 0);
        }
        this.level().addParticle(Reg.FLAME.get(), getX() - v.x * 0.15, getY() - v.y * 0.15, getZ() - v.z * 0.15, -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
        FxHooks.light.accept(this, 13);
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
