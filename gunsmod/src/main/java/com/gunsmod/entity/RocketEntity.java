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

/**
 * RPG rocket, 40 mm grenade, tank shell, helicopter rocket, aircraft bomb or the RPG's thermobaric round. Vehicle rounds
 * hurt but never break blocks.
 */
public class RocketEntity extends Projectile {
    public static final int RPG = 0, GRENADE = 1, TANK_SHELL = 2, HELI_ROCKET = 3, BOMB = 4, ARTY = 5, THERMOBARIC = 6, B2_BOMB = 7;
    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BYTE);
    private int life;
    /** vehicle rounds: direct hit damage against armour / everything else and the blast power (set by the vehicle) */
    private float hitArmour = -1, hitSoft = -1, blast = -1;
    /** the B-2's guided bomb: the point on the ground it steers to (null = falls free) */
    @javax.annotation.Nullable private Vec3 guide;
    /** RPG rounds: the bombers this rocket already passed (one roll of the dice each) */
    private java.util.Set<Integer> passed;

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
        return getKind() >= TANK_SHELL && getKind() <= ARTY;
    }

    /** a shoulder fired rocket (the normal or the thermobaric round) */
    public boolean isRpg() {
        return getKind() == RPG || getKind() == THERMOBARIC;
    }

    public void guideTo(Vec3 target) {
        guide = target;
    }

    public void setPower(float armour, float soft, float blastPower) {
        hitArmour = armour;
        hitSoft = soft;
        blast = blastPower;
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
        if (VehicleEntity.shieldOf(e) != null) return false;    // the round hits the vehicle
        if (e instanceof JetEntity j && j.type.rpgOnly()) return false;   // only the dice decide (see bomberPass)
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
        if (!this.level().isClientSide && isRpg() && bomberPass()) return;
        switch (getKind()) {
            case B2_BOMB -> v = steerBomb(v);
            case RPG, THERMOBARIC -> {
                if (life > 4 && v.length() < 4.0) v = v.scale(1.035);
                v = v.add(0, -0.006, 0);
            }
            case TANK_SHELL -> v = v.add(0, -0.03, 0);
            case ARTY -> v = v.add(0, -ArmoredCarEntity.ARTY_GRAVITY, 0);
            case BOMB -> v = guide != null ? steerBomb(v) : v.scale(0.995).add(0, -0.04, 0);
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
            } else if (getKind() == ARTY) {
                // a long white smoke trail behind a bright motor flame
                for (int i = 0; i < 6; i++) {
                    double f = i / 6.0;
                    this.level().addParticle(Reg.MISSILE_SMOKE.get(), getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f, 0, 0.01, 0);
                }
                this.level().addParticle(Reg.FLAME.get(), getX() - v.x * 0.1, getY() - v.y * 0.1, getZ() - v.z * 0.1, -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
                FxHooks.light.accept(this, 14);
            } else if (getKind() == HELI_ROCKET) {
                for (int i = 0; i < 4; i++) {
                    double f = i / 4.0;
                    this.level().addParticle(Reg.SMOKE.get(), getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f, 0, 0.01, 0);
                }
                this.level().addParticle(Reg.FLAME.get(), getX() - v.x * 0.15, getY() - v.y * 0.15, getZ() - v.z * 0.15, -v.x * 0.04, -v.y * 0.04, -v.z * 0.04);
                FxHooks.light.accept(this, 12);
            } else if (getKind() == BOMB) {
                // falls silently, no trail
            } else if (isRpg()) {
                FxHooks.light.accept(this, 11);
                for (int i = 0; i < 3; i++) {
                    double f = i / 3.0;
                    this.level().addParticle(Reg.SMOKE.get(), getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f, 0, 0.01, 0);
                }
                this.level().addParticle(Reg.FLAME.get(), getX() - v.x * 0.2, getY() - v.y * 0.2, getZ() - v.z * 0.2, -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
            } else if (life % 2 == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
            }
        } else if (getKind() == B2_BOMB) {
            if (life > 900) detonate(this.position());
        } else if (isVehicleRound() && life > (getKind() == BOMB || getKind() == ARTY ? 400 : 110)) {
            this.discard();
        } else if (life > 240 || this.isInWater() && getKind() == 1) {
            detonate(this.position());
        }
    }

    /** an RPG passing a stealth bomber: one roll of the dice; true when it hit (and burst) */
    private boolean bomberPass() {
        for (JetEntity b : JetEntity.rpgOnlyActive(this.level())) {
            if (passed != null && passed.contains(b.getId())) continue;
            Vec3 c = b.position().add(0, b.geo.pivotY(), 0);
            double dx = getX() - c.x, dz = getZ() - c.z, dy = getY() - c.y, hs = b.halfSpan();
            if (dx * dx + dz * dz > hs * hs || Math.abs(dy) > 4) continue;
            if (BotTeam.friendly(this.getOwner(), b)) continue;
            if (passed == null) passed = new java.util.HashSet<>();
            passed.add(b.getId());
            if (b.rpgPass(this, this.getOwner())) {
                detonate(this.position());
                return true;
            }
        }
        return false;
    }

    /** the guided bomb: falls under gravity and trims its glide so it comes down on the target */
    private Vec3 steerBomb(Vec3 v) {
        v = v.scale(0.995).add(0, -0.04, 0);
        if (guide == null) return v;
        double h = this.getY() - guide.y;
        if (h <= 0.5) return v;
        // time left to fall from here: h = -vy t + 0.02 t^2
        double vy = v.y;
        double tFall = Math.max(1, (vy + Math.sqrt(vy * vy + 0.08 * h)) / 0.04);
        double ax = Mth.clamp((guide.x - getX()) / tFall - v.x, -0.04, 0.04), az = Mth.clamp((guide.z - getZ()) / tFall - v.z, -0.04, 0.04);
        return new Vec3(v.x + ax, v.y, v.z + az);
    }

    /** the B-2 bomb: a blinding flash and a fireball that rises into a mushroom cloud; the land is left as it was */
    private void nuke(Vec3 at) {
        Entity owner = this.getOwner();
        Level level = this.level();
        Blast.explode(level, this, at, 6.0f, false, Level.ExplosionInteraction.NONE, Blast.FX_NUKE, 4.0f);
        Blast.shockwave(level, at, 30, 80f, 10, this, owner);
        // armour inside the fireball does not survive either
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(24),
                e -> e instanceof VehicleEntity && !(e instanceof Aircraft a && a.isAirborne()) || e instanceof JeepEntity)) {
            double d = e.position().distanceTo(at);
            if (d > 24) continue;
            e.invulnerableTime = 0;
            e.hurt(this.damageSources().explosion(this, owner), (float) (600 * (1 - d / 24)));
            e.invulnerableTime = 0;
        }
        this.discard();
    }

    /** shot down in the air (C-RAM): a small burst, no damage on the ground */
    public void intercept() {
        if (this.isRemoved()) return;
        Blast.fx(this.level(), this.position(), Blast.FX_SMALL, getKind() == ARTY ? 1.0f : 0.6f);
        this.discard();
    }

    /** the round itself hits hard before it bursts: tanks need a few hits, soldiers do not survive one */
    private void directHit(Entity e) {
        if (!isVehicleRound()) return;
        Entity owner = this.getOwner();
        boolean armour = e instanceof VehicleEntity || e instanceof JeepEntity;
        float dmg = armour && hitArmour >= 0 ? hitArmour : !armour && hitSoft >= 0 ? hitSoft
                : getKind() == TANK_SHELL ? (armour ? 55f : 40f) : (armour ? 16f : 14f);
        e.invulnerableTime = 0;
        e.hurt(this.damageSources().explosion(this, owner), dmg);
        e.invulnerableTime = 0;
    }

    private void detonate(Vec3 at) {
        Entity owner = this.getOwner();
        if (getKind() == B2_BOMB) {
            nuke(at);
            return;
        }
        if (isVehicleRound()) {
            // same blast as the launch station missiles, but the terrain stays as it is
            boolean shell = getKind() == TANK_SHELL;
            float power = blast > 0 ? blast : shell ? 3.2f : 2.6f;
            Blast.explode(this.level(), this, at, power, false, Level.ExplosionInteraction.NONE, Blast.FX_MISSILE,
                    getKind() == BOMB ? 1.9f : power * 0.36f);
            this.discard();
            return;
        }
        // soldiers' rockets and 40 mm grenades leave the terrain as it is
        boolean player = owner instanceof Player;
        Level.ExplosionInteraction mode = player ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        if (getKind() == THERMOBARIC) {
            // fuel-air charge: a fireball, then the pressure wave fills the space around it (and goes round corners)
            Blast.explode(this.level(), this, at, 3.0f, player, mode, Blast.FX_FIRE, 1.6f);
            Blast.shockwave(this.level(), at, 9.0, 16f, 8, this, owner);
            if (player) Blast.firestorm(this.level(), at, 4.5f, 12);
            this.level().playSound(null, at.x, at.y, at.z, Reg.sound("explosion_far"), net.minecraft.sounds.SoundSource.NEUTRAL, 10f, 0.75f);
            this.discard();
            return;
        }
        Blast.explode(this.level(), this, at, getKind() == 0 ? 3.6f : 2.4f, false, mode, Blast.FX_SMALL, getKind() == 0 ? 1.0f : 0.7f);
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Kind", (byte) getKind());
        if (guide != null) {
            tag.putDouble("GX", guide.x);
            tag.putDouble("GY", guide.y);
            tag.putDouble("GZ", guide.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_KIND, tag.getByte("Kind"));
        if (tag.contains("GX")) guide = new Vec3(tag.getDouble("GX"), tag.getDouble("GY"), tag.getDouble("GZ"));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }
}
