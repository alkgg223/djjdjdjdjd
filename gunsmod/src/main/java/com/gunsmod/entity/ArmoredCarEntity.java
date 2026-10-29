package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.entity.ai.GroundBotDriver;
import com.gunsmod.entity.ai.Sight;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.gun.GunType;
import com.gunsmod.net.Net;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 8x8 wheeled fighting vehicle. The commander in the turret hatch drives, turns the turret with his view and fires
 * (attack: main weapon, use: second weapon); the other seats are passengers, all protected while it lasts.
 * BTR-82A: 30 mm cannon with high explosive rounds and a coaxial machine gun, against everything.
 * Pantsir-S1: its radar tracks helicopters; hold one in the sight until the tracking radar locks, then the missiles fly
 * to it on their own; the twin 30 mm guns are radar directed. It never engages anything on the ground.
 * Soldiers drive patrols with halts and stop to fight.
 */
public class ArmoredCarEntity extends VehicleEntity {
    public static final double MAX_REVERSE = 14.0 / 3.6 / 20.0;
    public static final double RADAR_RANGE = 240;
    public static final int LOCK_TICKS = 30;

    private static final EntityDataAccessor<Float> DATA_TURRET = SynchedEntityData.defineId(ArmoredCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GUN = SynchedEntityData.defineId(ArmoredCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(ArmoredCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STEER = SynchedEntityData.defineId(ArmoredCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_AMMO = SynchedEntityData.defineId(ArmoredCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RELOAD = SynchedEntityData.defineId(ArmoredCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TRACK = SynchedEntityData.defineId(ArmoredCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_LOCK = SynchedEntityData.defineId(ArmoredCarEntity.class, EntityDataSerializers.BYTE);

    public final CarType kind;
    public final VehicleGeo.Car geo;

    public double speed;
    public float steer;
    private float throttleIn, steerIn;
    private boolean fireMain, fireSecond;
    private int mainCooldown, secondCooldown, muzzleIndex, tubeIndex, scanTimer, searchTicks, burst, burstPause, trackCandidate = -1, trackCount;
    @Nullable private Vec3 aimError;
    private final GroundBotDriver patrol = new GroundBotDriver();
    private int stuckTicks, reverseTicks;
    private Vec3 lastPos = Vec3.ZERO;
    private float lastYaw;
    // client visuals
    public float prevTurret, prevGun, wheelSpin, prevWheelSpin, radarAngle, prevRadarAngle;
    public float tiltPitch, tiltRoll, prevTiltPitch, prevTiltRoll;

    public ArmoredCarEntity(EntityType<? extends ArmoredCarEntity> type, Level level) {
        this(type, level, CarType.BTR);
    }

    public ArmoredCarEntity(EntityType<? extends ArmoredCarEntity> type, Level level, CarType kind) {
        super(type, level);
        this.kind = kind;
        this.geo = kind.geo;
        this.entityData.set(DATA_AMMO, kind.ammo);
    }

    public static AttributeSupplier.Builder createAttributes(CarType kind) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, kind.health)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_TURRET, 0f);
        b.define(DATA_GUN, 0f);
        b.define(DATA_SPEED, 0f);
        b.define(DATA_STEER, 0f);
        b.define(DATA_AMMO, 0);
        b.define(DATA_RELOAD, 0);
        b.define(DATA_TRACK, -1);
        b.define(DATA_LOCK, (byte) 0);
    }

    @Override
    public ItemStack pickItem() {
        return new ItemStack(kind.item(getBotTeam()));
    }

    @Override
    public int seatCount() {
        return geo.seats().length;
    }

    @Override
    public boolean isArmored() {
        return kind.armored;
    }

    @Override
    public float bulletFactor() {
        return kind.bulletFactor;
    }

    @Override
    protected String seatName0() {
        return "gunsmod.seat.commander";
    }

    @Override
    protected String seatName(int seat) {
        return seat == 0 ? seatName0() : "gunsmod.seat.passenger";
    }

    public float getTurretYaw() {
        return this.entityData.get(DATA_TURRET);
    }

    public float getGunPitch() {
        return this.entityData.get(DATA_GUN);
    }

    public int getAmmo() {
        return this.entityData.get(DATA_AMMO);
    }

    public int getReload() {
        return this.entityData.get(DATA_RELOAD);
    }

    /** the helicopter the tracking radar follows (Pantsir), or -1 */
    public int getTrack() {
        return this.entityData.get(DATA_TRACK);
    }

    public int getLockProgress() {
        return this.entityData.get(DATA_LOCK);
    }

    public boolean isLocked() {
        return getTrack() >= 0 && getLockProgress() >= LOCK_TICKS;
    }

    public float getSteerVisual() {
        return this.isControlledByLocalInstance() ? steer : this.entityData.get(DATA_STEER);
    }

    public double getDisplaySpeed() {
        return this.isControlledByLocalInstance() ? speed : this.entityData.get(DATA_SPEED);
    }

    // ------------------------------------------------------------------------------------ geometry
    public Vec3 turretModel(double mx, double my, double mz, float turretYaw) {
        float a = turretYaw * Mth.DEG_TO_RAD;
        double tx = geo.turret()[0], tz = geo.turret()[1];
        double dx = mx - tx, dz = mz - tz;
        return new Vec3(tx + Mth.cos(a) * dx - Mth.sin(a) * dz, my, tz + Mth.sin(a) * dx + Mth.cos(a) * dz);
    }

    public Vec3 gunModel(double mx, double my, double mz, float turretYaw, float pitch) {
        float p = pitch * Mth.DEG_TO_RAD;
        double gy = geo.gun()[0], gz = geo.gun()[1];
        double dy = my - gy, dz = mz - gz;
        return turretModel(mx, gy + Mth.cos(p) * dy + Mth.sin(p) * dz, gz - Mth.sin(p) * dy + Mth.cos(p) * dz, turretYaw);
    }

    private Vec3 gunPoint(double[] p) {
        Vec3 m = gunModel(p[0], p[1], p[2], getTurretYaw(), getGunPitch());
        return toWorld(m.x, m.y, m.z);
    }

    @Override
    protected Vec3 seatModel(int seat) {
        double[] s = geo.seats()[Math.min(seat, geo.seats().length - 1)];
        return seat == 0 ? turretModel(s[0], s[1], s[2], getTurretYaw()) : new Vec3(s[0], s[1], s[2]);
    }

    public Vec3 gunDir() {
        return GunLogic.dir(getGunPitch(), this.getYRot() + getTurretYaw());
    }

    /** where the radar and the sights look from */
    public Vec3 sightWorld() {
        return toWorld(geo.turret()[0], geo.gun()[0] + 2, geo.turret()[1]);
    }

    // ------------------------------------------------------------------------------------ input
    @Override
    public void trigger(Entity who, int weapon, boolean down) {
        if (who != getControllingPassenger()) return;
        if (weapon == 0) fireMain = down;
        else if (weapon == 1) fireSecond = down;
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        throttleIn = Mth.clamp(player.zza / 0.98f, -1f, 1f);
        steerIn = Mth.clamp(-player.xxa / 0.98f, -1f, 1f);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return new Vec3(0, 0, 1);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return 0.3f;
    }

    // ------------------------------------------------------------------------------------ driving
    @Override
    public void travel(Vec3 input) {
        if (this.isControlledByLocalInstance()) physics();
        this.calculateEntityAnimation(false);
    }

    private void physics() {
        Entity driver = getControllingPassenger();
        float thr = driver != null ? throttleIn : 0f;
        float st = driver != null ? steerIn : 0f;
        boolean water = this.isInWater();
        double max = water ? (kind == CarType.BTR ? 0.18 : 0.06) : kind.maxSpeed;
        if (thr > 0.05f) {
            if (speed < -0.01) speed = Math.min(0, speed + 0.03);
            else {
                double k = speed / max;
                speed += 0.007 * kind.accel * thr * (1.0 - 0.8 * k * k);
            }
        } else if (thr < -0.05f) {
            if (speed > 0.01) speed = Math.max(0, speed - 0.03);
            else speed = Math.max(-MAX_REVERSE, speed - 0.005 * -thr);
        } else {
            speed *= 0.985;
            speed -= Math.signum(speed) * 0.0012;
            if (Math.abs(speed) < 0.002) speed = 0;
        }
        speed = Mth.clamp(speed, -MAX_REVERSE, max);
        steer += (st - steer) * 0.2f;
        float yawBefore = this.getYRot();
        double[][] front = geo.front(), back = geo.back();
        boolean wasFront = blockedAfter(Vec3.ZERO, front), wasBack = blockedAfter(Vec3.ZERO, back);
        if (this.onGround() || water) {
            // front axles steer: the yaw rate follows the speed (no turning on the spot)
            double angle = steer * Math.toRadians(30) * (1.0 - 0.45 * Math.min(1, Math.abs(speed) / kind.maxSpeed));
            this.setYRot(this.getYRot() + (float) Math.toDegrees(speed * Math.tan(angle) / kind.wheelbase));
        }
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        Vec3 fwd = forward();
        double vy = this.getDeltaMovement().y;
        vy = this.onGround() ? -0.08 : (vy - 0.08) * 0.98;
        // the BTR swims
        if (water) vy = kind == CarType.BTR ? Math.max(vy, 0.02) : Math.max(vy, -0.05);
        Vec3 motion = fwd.scale(speed).add(0, vy, 0);
        if (Math.abs(this.getYRot() - yawBefore) > 0.001f && (blockedAfter(Vec3.ZERO, front) && !wasFront || blockedAfter(Vec3.ZERO, back) && !wasBack)) {
            this.setYRot(yawBefore);
        }
        Vec3 h = new Vec3(motion.x, 0, motion.z);
        double[][] lead = speed >= 0 ? front : back;
        if (blockedAfter(h, lead) && !(speed >= 0 ? wasFront : wasBack)) {
            if (Math.abs(speed) > 0.35 && !this.level().isClientSide) this.hurt(this.damageSources().flyIntoWall(), (float) (Math.abs(speed) * 12));
            speed = 0;
            motion = new Vec3(0, vy, 0);
        }
        this.setDeltaMovement(motion);
        double before = Math.abs(speed);
        this.move(MoverType.SELF, motion);
        if (this.horizontalCollision && before > 0.05) speed *= 0.3;
        this.fallDistance = 0;
    }

    private boolean blockedAfter(Vec3 offset, double[][] pts) {
        for (double[] p : pts) {
            Vec3 w = toWorld(p[0], p[1], p[2]).add(offset);
            BlockPos bp = BlockPos.containing(w);
            var shape = this.level().getBlockState(bp).getCollisionShape(this.level(), bp);
            if (!shape.isEmpty() && w.y - bp.getY() < shape.max(net.minecraft.core.Direction.Axis.Y)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------ tick
    @Override
    public void tick() {
        prevTurret = getTurretYaw();
        prevGun = getGunPitch();
        prevWheelSpin = wheelSpin;
        prevRadarAngle = radarAngle;
        prevTiltPitch = tiltPitch;
        prevTiltRoll = tiltRoll;
        if (!this.level().isClientSide) {
            LivingEntity d = getControllingPassenger();
            if (d instanceof BotEntity bot) botDrive(bot);
            else if (d == null) {
                throttleIn = 0;
                steerIn = 0;
            }
        }
        super.tick();
        Vec3 moved = this.position().subtract(lastPos);
        double horiz = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
        if (horiz > 3) horiz = 0;
        Vec3 fwd = forward();
        double signed = moved.x * fwd.x + moved.z * fwd.z < 0 ? -horiz : horiz;
        if (!this.isControlledByLocalInstance() && !this.level().isClientSide) speed = signed;
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_SPEED, (float) (this.isControlledByLocalInstance() ? speed : signed));
            this.entityData.set(DATA_STEER, steer);
        }
        // passengers on the hull turn with it (the commander turns the turret with his own view)
        float dy = Mth.wrapDegrees(this.getYRot() - lastYaw);
        if (Math.abs(dy) > 0.001f && Math.abs(dy) < 30f) {
            for (Entity p : this.getPassengers()) {
                if (seatOf(p) == 0) continue;
                p.setYRot(p.getYRot() + dy);
                p.setYHeadRot(p.getYHeadRot() + dy);
            }
        }
        lastPos = this.position();
        lastYaw = this.getYRot();
        wheelSpin += (float) (getDisplaySpeed() / (geo.wheelR() * UNIT));
        if (this.level().isClientSide) {
            if (kind.antiAir() && getControllingPassenger() != null) radarAngle += 9f;
            clientTilt();
        } else {
            weaponsTick();
            ramTick();
        }
    }

    private void clientTilt() {
        double[][] w = geo.wheels();
        double zf = w[0][2], zb = w[w.length - 1][2];
        double hf = groundAt(8, zf), hb = groundAt(8, zb), hl = groundAt(w[0][0], (zf + zb) / 2), hr = groundAt(w[1][0], (zf + zb) / 2);
        float wantPitch = (float) Math.toDegrees(Math.atan2(hf - hb, (zb - zf) * UNIT));
        float wantRoll = (float) Math.toDegrees(Math.atan2(hr - hl, (w[1][0] - w[0][0]) * UNIT));
        tiltPitch += (Mth.clamp(wantPitch, -20f, 20f) - tiltPitch) * 0.3f;
        tiltRoll += (Mth.clamp(wantRoll, -15f, 15f) - tiltRoll) * 0.3f;
    }

    private double groundAt(double mx, double mz) {
        Vec3 w = toWorld(mx, 0, mz);
        int x = Mth.floor(w.x), z = Mth.floor(w.z);
        int y0 = Mth.floor(this.getY());
        for (int y = y0 + 1; y >= y0 - 3; y--) {
            BlockPos p = new BlockPos(x, y, z);
            var shape = this.level().getBlockState(p).getCollisionShape(this.level(), p);
            if (!shape.isEmpty()) return y + shape.max(net.minecraft.core.Direction.Axis.Y) - this.getY();
        }
        return -3;
    }

    private void ramTick() {
        double v = Math.abs(speed);
        if (v < 0.18) return;
        AABB box = this.getBoundingBox().move(forward().scale(Math.signum(speed) * 2.2)).inflate(0.2, 0, 0.2);
        Entity driver = getControllingPassenger();
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.getVehicle() != this && !(e instanceof VehicleEntity) && !(e instanceof JeepEntity))) {
            if (driver != null && BotTeam.friendly(driver, e)) continue;
            e.hurt(GunLogic.source(this.level(), GunLogic.ROADKILL, this, driver != null ? driver : this), (float) (v * 45));
            Vec3 f = forward().scale(Math.signum(speed));
            e.push(f.x * v * 3, 0.3, f.z * v * 3);
        }
    }

    // ------------------------------------------------------------------------------------ weapons
    private void weaponsTick() {
        if (mainCooldown > 0) mainCooldown--;
        if (secondCooldown > 0) secondCooldown--;
        int reload = getReload();
        if (getAmmo() <= 0 && reload == 0) this.entityData.set(DATA_RELOAD, kind.reload);
        else if (reload > 0) {
            this.entityData.set(DATA_RELOAD, --reload);
            if (reload == 0) {
                this.entityData.set(DATA_AMMO, kind.ammo);
                this.level().playSound(null, getX(), getY() + 2, getZ(), Reg.sound("tank_reload"), SoundSource.NEUTRAL, 1.2f, kind.antiAir() ? 0.8f : 1.2f);
            }
        }
        LivingEntity crew = getControllingPassenger();
        if (crew instanceof Player p) {
            traverse(Mth.wrapDegrees(p.getYRot() - this.getYRot()), p.getXRot(), kind.traverse, kind.elevate);
            if (kind.antiAir()) radarTrack(p, p.getEyePosition(), p.getLookAngle(), 1);
            if (fireMain) fireMain(p, null);
            if (fireSecond && secondCooldown <= 0) fireSecond(p, null);
        } else if (crew instanceof BotEntity bot) {
            if (kind.antiAir()) botAirDefence(bot);
            else botGunner(bot);
        } else {
            fireMain = fireSecond = false;
            setTrack(-1, 0);
        }
    }

    private void traverse(float wantYaw, float wantPitch, float yawRate, float pitchRate) {
        float cy = getTurretYaw(), cp = getGunPitch();
        float dy = Mth.clamp(Mth.wrapDegrees(wantYaw - cy), -yawRate, yawRate);
        float dp = Mth.clamp(Mth.clamp(wantPitch, (float) -geo.gunMax(), (float) -geo.gunMin()) - cp, -pitchRate, pitchRate);
        this.entityData.set(DATA_TURRET, Mth.wrapDegrees(cy + dy));
        this.entityData.set(DATA_GUN, Mth.clamp(cp + dp, (float) -geo.gunMax(), (float) -geo.gunMin()));
    }

    private void setTrack(int id, int progress) {
        if (getTrack() != id) this.entityData.set(DATA_TRACK, id);
        if (getLockProgress() != progress) this.entityData.set(DATA_LOCK, (byte) progress);
    }

    private boolean enemyAircraft(VehicleEntity h, @Nullable LivingEntity crew) {
        if (!(h instanceof Aircraft a) || !h.isAlive() || h.getPassengers().isEmpty() || !a.isAirborne()) return false;
        if (crew != null && BotTeam.friendly(crew, h.getControllingPassenger())) return false;
        return crew == null || h.getBotTeam() != getBotTeam() || crew instanceof Player;
    }

    /** Pantsir: the aircraft closest to the sight line inside the cone is tracked; held long enough, it is locked */
    private void radarTrack(LivingEntity crew, Vec3 eye, Vec3 look, int rate) {
        VehicleEntity best = null;
        double bestAng = rate > 1 ? RADAR_RANGE : 12;
        for (VehicleEntity h : this.level().getEntitiesOfClass(VehicleEntity.class, this.getBoundingBox().inflate(RADAR_RANGE, RADAR_RANGE * 0.6, RADAR_RANGE),
                h -> enemyAircraft(h, crew))) {
            Vec3 c = h.position().add(0, h.getBbHeight() * 0.5, 0);
            Vec3 to = c.subtract(eye);
            if (to.length() > RADAR_RANGE) continue;
            double ang = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1, 1)));
            if (rate > 1) ang = to.length();     // soldiers: the nearest one
            if (ang >= bestAng) continue;
            if (this.level().clip(new ClipContext(sightWorld(), c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
            bestAng = ang;
            best = h;
        }
        int id = best == null ? -1 : best.getId();
        if (id != trackCandidate) {
            trackCandidate = id;
            trackCount = 0;
        } else if (id >= 0 && trackCount < LOCK_TICKS) {
            trackCount = Math.min(LOCK_TICKS, trackCount + rate);
            if (trackCount >= LOCK_TICKS) this.level().playSound(null, getX(), getY() + 2, getZ(), Reg.sound("lock_on"), SoundSource.NEUTRAL, 1f, 0.9f);
        }
        if (best instanceof Aircraft a) a.radarWarning();
        setTrack(id, id < 0 ? 0 : trackCount);
    }

    @Nullable
    private VehicleEntity tracked() {
        return getTrack() >= 0 && this.level().getEntity(getTrack()) instanceof VehicleEntity h && h instanceof Aircraft ? h : null;
    }

    private void fireMain(@Nullable LivingEntity gunner, @Nullable Vec3 aimAt) {
        if (mainCooldown > 0 || getAmmo() <= 0 || getReload() > 0) return;
        if (kind.antiAir()) {
            VehicleEntity h = tracked();
            if (h == null || !isLocked()) {
                fireMain = false;
                return;
            }
            mainCooldown = 30;
            double[][] tubes = geo.tubes();
            Vec3 at = gunPoint(tubes[tubeIndex++ % tubes.length]);
            GuidedMissileEntity m = new GuidedMissileEntity(this.level(), gunner, GuidedMissileEntity.SAM).power(80f, 0f, 0f).target(h);
            m.setPos(at.x, at.y, at.z);
            m.setDeltaMovement(gunDir().scale(1.6).add(0, 0.3, 0));
            m.alignToMotion();
            this.level().addFreshEntity(m);
            this.entityData.set(DATA_AMMO, getAmmo() - 1);
            this.level().playSound(null, at.x, at.y, at.z, Reg.sound("sam_launch"), SoundSource.NEUTRAL, 9f, 0.95f + this.random.nextFloat() * 0.1f);
            muzzleFx(at, gunDir(), 1.0f);
            fireMain = false;
        } else {
            // 2A72: 30 mm high explosive, a small burst where it hits
            mainCooldown = 3;
            Vec3 muzzle = gunPoint(geo.muzzles()[0]);
            Vec3 dir = spread(aimAt == null ? gunDir() : aimAt.subtract(muzzle).normalize(), 0.5f);
            Entity shooter = gunner != null ? gunner : this;
            GunLogic.Trace tr = GunLogic.trace(this.level(), shooter, muzzle, dir, 170);
            if (tr.entity() != null && tr.entity() != this && gunner != null) GunLogic.damage(gunner, tr.entity(), 14f, false, GunType.M249);
            if (tr.kind() != 0) {
                Vec3 e = tr.end();
                this.level().explode(this, e.x, e.y, e.z, 1.2f, Level.ExplosionInteraction.NONE);
            }
            this.entityData.set(DATA_AMMO, getAmmo() - 1);
            shotFx(muzzle, tr, -4);
            if (gunner != null && this.level() instanceof ServerLevel sl) BotEntity.alertGunfire(sl, gunner, 96);
        }
    }

    private void fireSecond(@Nullable LivingEntity gunner, @Nullable Vec3 aimAt) {
        Entity shooter = gunner != null ? gunner : this;
        if (kind.antiAir()) {
            // 2A38M: four barrels firing in turn, radar directed onto the tracked helicopter; only aircraft are hit
            secondCooldown = 2;
            double[][] mz = geo.muzzles();
            Vec3 muzzle = gunPoint(mz[muzzleIndex++ % mz.length]);
            Vec3 dir = gunDir();
            VehicleEntity h = tracked();
            if (aimAt == null && h != null && isLocked()) {
                Vec3 to = h.position().add(0, h.getBbHeight() * 0.5, 0).subtract(muzzle).normalize();
                if (to.dot(dir) > Math.cos(Math.toRadians(10))) dir = to;
            } else if (aimAt != null) {
                dir = aimAt.subtract(muzzle).normalize();
            }
            dir = spread(dir, 0.7f);
            GunLogic.Trace tr = GunLogic.trace(this.level(), shooter, muzzle, dir, 260);
            VehicleEntity hit = tr.entity() instanceof VehicleEntity he && he instanceof Aircraft ? he : flak(muzzle, tr.end(), gunner);
            if (hit != null) {
                hit.invulnerableTime = 0;
                hit.hurt(this.damageSources().explosion(this, gunner), 5f);
                hit.invulnerableTime = 0;
            }
            shotFx(muzzle, tr, -6);
            if (gunner != null && this.level() instanceof ServerLevel sl) BotEntity.alertGunfire(sl, gunner, 96);
        } else {
            // PKTM coaxial machine gun
            secondCooldown = 3;
            Vec3 muzzle = gunPoint(geo.coax());
            Vec3 dir = spread(aimAt == null ? gunDir() : aimAt.subtract(muzzle).normalize(), 0.7f);
            GunLogic.Trace tr = GunLogic.trace(this.level(), shooter, muzzle, dir, 120);
            if (tr.entity() != null && tr.entity() != this && gunner != null) GunLogic.damage(gunner, tr.entity(), 7f, tr.headshot(), GunType.M249);
            shotFx(muzzle, tr, -2);
            if (gunner != null && this.level() instanceof ServerLevel sl) BotEntity.alertGunfire(sl, gunner, 64);
        }
    }

    /** proximity: a 30 mm round passing close to a helicopter still damages it */
    @Nullable
    private VehicleEntity flak(Vec3 from, Vec3 to, @Nullable LivingEntity gunner) {
        Vec3 ab = to.subtract(from);
        double len2 = ab.lengthSqr();
        if (len2 < 1) return null;
        for (VehicleEntity h : this.level().getEntitiesOfClass(VehicleEntity.class, new AABB(from, to).inflate(3), h -> enemyAircraft(h, gunner))) {
            Vec3 c = h.position().add(0, h.getBbHeight() * 0.5, 0);
            double t = Mth.clamp(c.subtract(from).dot(ab) / len2, 0, 1);
            if (from.add(ab.scale(t)).distanceTo(c) < h.getBbWidth() * 0.5 + 1.2) return h;
        }
        return null;
    }

    private Vec3 spread(Vec3 d, float deg) {
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        return GunLogic.dir(pitch + (float) this.random.nextGaussian() * deg, yaw + (float) this.random.nextGaussian() * deg);
    }

    private void shotFx(Vec3 muzzle, GunLogic.Trace tr, int fx) {
        List<Vec3> ends = new ArrayList<>();
        ends.add(tr.end().subtract(muzzle));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this, new Net.ShotFx(this.getId(), fx, muzzle, ends, new byte[]{tr.kind()}, false));
    }

    private void muzzleFx(Vec3 at, Vec3 dir, float size) {
        if (this.level() instanceof ServerLevel sl) PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 160, new Net.MuzzleFx(at, dir, size, this.getId()));
    }

    // ------------------------------------------------------------------------------------ soldier crew
    private void aimAt(Vec3 aim, float rate) {
        Vec3 to = aim.subtract(toWorld(geo.turret()[0], geo.gun()[0], geo.turret()[1]));
        float wantYaw = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - this.getYRot());
        float wantPitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        traverse(wantYaw, wantPitch, kind.traverse * rate, kind.elevate * rate);
    }

    private float offAim(Vec3 aim) {
        Vec3 to = aim.subtract(toWorld(geo.turret()[0], geo.gun()[0], geo.turret()[1])).normalize();
        return (float) Math.toDegrees(Math.acos(Mth.clamp(to.dot(gunDir()), -1, 1)));
    }

    /** nobody in the sights: turn the gun to where the enemy was last heard of, otherwise sweep the sector ahead */
    private void search(BotEntity bot) {
        Vec3 heard = bot.lastKnownEnemyPos;
        if (heard != null) {
            if (++searchTicks > 100) {
                bot.lastKnownEnemyPos = null;
                searchTicks = 0;
            }
            Vec3 to = heard.subtract(this.position());
            traverse(Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - this.getYRot()), 0f, 1.5f, 1f);
            return;
        }
        traverse(Mth.sin(this.tickCount * 0.015f) * 75f, 0f, 1.5f, 1f);
    }

    /** BTR: the gunner engages everything in range, cannon bursts at vehicles and aircraft, the machine gun at soldiers */
    private void botGunner(BotEntity bot) {
        if (--scanTimer <= 0) {
            scanTimer = 10;
            // the gunner sees what is in his sights: near where the gun points
            LivingEntity t = TankEntity.scan(this, bot, 100, sightWorld(), bot.getTarget(), this.getYRot() + getTurretYaw(), Sight.TURRET, 6);
            if (t != null && t != bot.getTarget()) {
                bot.setTarget(t);
                aimError = null;
                searchTicks = 0;
            }
        }
        LivingEntity t = bot.getTarget();
        if (t == null || !t.isAlive() || t.distanceToSqr(this) > 110 * 110) {
            search(bot);
            burst = 0;
            return;
        }
        bot.setAiming(true);
        if (aimError == null) aimError = TankEntity.rollAimError(this.random).scale(0.5);
        Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
        Vec3 aim = c.add(aimError);
        aimAt(aim, 0.8f);
        boolean los = this.level().clip(new ClipContext(sightWorld(), c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
        if (!los || offAim(aim) > 3f) return;
        boolean soft = !(t instanceof VehicleEntity) && !(t instanceof JeepEntity);
        if (burstPause > 0) {
            burstPause--;
            return;
        }
        if (soft && t.distanceTo(this) < 70) {
            if (secondCooldown <= 0) fireSecond(bot, aim);
        } else if (mainCooldown <= 0) {
            fireMain(bot, aim);
        } else {
            return;
        }
        if (++burst >= 5 + this.random.nextInt(5)) {
            burst = 0;
            burstPause = 20 + this.random.nextInt(25);
            aimError = TankEntity.rollAimError(this.random).scale(0.5);
        }
    }

    /** Pantsir: radar picks the nearest enemy helicopter, locks it, launches missiles and fires the guns when it is close */
    private void botAirDefence(BotEntity bot) {
        radarTrack(bot, sightWorld(), gunDir(), 2);
        VehicleEntity h = tracked();
        if (h == null) {
            if (bot.getTarget() instanceof Aircraft) bot.setTarget(null);
            // search: the module turns slowly while the radar spins
            traverse(getTurretYaw() + 3f, -15f, 1.5f, 1f);
            return;
        }
        bot.setTarget(h);
        bot.setAiming(true);
        Vec3 c = h.position().add(0, h.getBbHeight() * 0.5, 0);
        aimAt(c, 1f);
        double dist = c.distanceTo(sightWorld());
        if (isLocked() && dist > 30 && mainCooldown <= 0 && getAmmo() > 0 && this.random.nextInt(8) == 0) fireMain(bot, null);
        if (dist < 140 && offAim(c) < 4f) {
            if (burstPause > 0) burstPause--;
            else if (secondCooldown <= 0) {
                Vec3 at = this.random.nextFloat() < 0.6f ? c : c.add((this.random.nextDouble() - 0.5) * 5, (this.random.nextDouble() - 0.5) * 3, (this.random.nextDouble() - 0.5) * 5);
                fireSecond(bot, at);
                if (++burst >= 12 + this.random.nextInt(8)) {
                    burst = 0;
                    burstPause = 15 + this.random.nextInt(15);
                }
            }
        }
    }

    /** patrol with halts; stop to fight when the enemy is in sight */
    private void botDrive(BotEntity driver) {
        // the Pantsir only cares about aircraft: it halts for the helicopter its radar tracks, never for soldiers
        LivingEntity enemy = kind.antiAir() ? tracked() : driver.getTarget();
        BlockPos hp = home != null ? home : this.blockPosition();
        // on the battlefield the air defence covers its own half of the field from the road, the rest drive on the enemy
        Vec3 goal = GroundBotDriver.onField(this, driver)
                ? patrol.road(this, driver, enemy, true, kind.antiAir() ? RADAR_RANGE : 75, kind.antiAir() ? 0.3 : 1)
                : kind.antiAir()
                ? patrol.goal(this, enemy, true, Vec3.atBottomCenterOf(hp), 90, RADAR_RANGE, 0)
                : patrol.goal(this, enemy, true, Vec3.atBottomCenterOf(hp), 100, 75, 9);
        if (goal == null) {
            throttleIn = GroundBotDriver.brake(speed);
            steerIn = 0;
            return;
        }
        Vec3 to = goal.subtract(this.position());
        double dist = Math.sqrt(to.x * to.x + to.z * to.z);
        float desired = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float diff = Mth.wrapDegrees(desired - this.getYRot());
        float thr = Math.abs(diff) > 60 ? 0.5f : 1f;
        float st = Mth.clamp(diff / 25f, -1f, 1f);
        double look = 4.5 + Math.abs(speed) * 14;
        double c = probe(0, look), l = probe(-30, look), r = probe(30, look);
        if (c < look) {
            thr = Math.min(thr, 0.5f);
            st = l > r ? -1f : 1f;
            if (c < 2.5) thr = -1f;
        }
        if (Math.abs(diff) > 120 && dist < 30 && reverseTicks == 0) {
            thr = -0.8f;
            st = -Math.signum(diff);
        }
        if (patrol.slow && thr > 0) thr = GroundBotDriver.creep(thr, speed, kind.maxSpeed);
        if (thr > 0.3f && Math.abs(speed) < 0.03) stuckTicks++;
        else stuckTicks = Math.max(0, stuckTicks - 1);
        if (stuckTicks > 35) {
            reverseTicks = 35;
            stuckTicks = 0;
        }
        if (reverseTicks > 0) {
            reverseTicks--;
            thr = -1f;
            st = -st;
            if (st == 0) st = 1f;
        }
        throttleIn = thr;
        steerIn = st;
    }

    private double probe(float relDeg, double len) {
        float yr = (this.getYRot() + relDeg) * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
        double best = len;
        double nose = Math.abs(geo.front()[0][2] - 8) * UNIT;
        for (double h : new double[]{1.3, 2.2}) {
            Vec3 from = this.position().add(0, h, 0).add(dir.scale(nose));
            Vec3 to = from.add(dir.scale(len));
            HitResult hr = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (hr.getType() != HitResult.Type.MISS) best = Math.min(best, hr.getLocation().distanceTo(from));
        }
        Vec3 ahead = this.position().add(dir.scale(nose + 2));
        BlockPos bp = BlockPos.containing(ahead);
        boolean ground = false;
        for (int i = 0; i < 3 && !ground; i++) {
            BlockPos q = bp.below(i);
            if (!this.level().getFluidState(q).isEmpty()) {
                ground = kind == CarType.BTR;
                break;
            }
            ground = !this.level().getBlockState(q).getCollisionShape(this.level(), q).isEmpty();
        }
        if (!ground) best = Math.min(best, 2.5);
        return best;
    }

    // ------------------------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Ammo", getAmmo());
        tag.putInt("Reloading", getReload());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Ammo")) this.entityData.set(DATA_AMMO, Mth.clamp(tag.getInt("Ammo"), 0, kind.ammo));
        this.entityData.set(DATA_RELOAD, Math.max(0, tag.getInt("Reloading")));
    }
}
