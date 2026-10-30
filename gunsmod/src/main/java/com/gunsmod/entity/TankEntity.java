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
 * Main battle tank for one soldier: the commander drives, aims the turret with the view and fires the main gun
 * (attack) and the coaxial machine gun (use). Tracked steering (turns on the spot), slow heavy acceleration, the
 * turret traverses on its own so a soldier inside keeps driving its route and only swings the gun at enemies.
 * The model, armour, gun and mobility come from the {@link TankType}.
 */
public class TankEntity extends VehicleEntity {
    public static final double MAX_REVERSE = 12.0 / 3.6 / 20.0;
    public static final double SHELL_GRAVITY = 0.03;

    public final TankType kind;
    private final VehicleGeo.Tank geo;

    private static final EntityDataAccessor<Float> DATA_TURRET = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GUN = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_RELOAD = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);

    public double speed;
    public float steer;
    private float throttleIn, steerIn;
    private boolean fireMain, fireCoax;
    private int coaxCooldown, coaxBurst, coaxPause, scanTimer, searchTicks;
    @Nullable private Vec3 aimError;
    private final GroundBotDriver patrol = new GroundBotDriver();
    private int stuckTicks, reverseTicks;
    private Vec3 lastPos = Vec3.ZERO;
    private float lastYaw;
    // client visuals
    public float prevTurret, prevGun;
    public float trackL, trackR, prevTrackL, prevTrackR;
    public float tiltPitch, tiltRoll, prevTiltPitch, prevTiltRoll;
    public float rock, prevRock;
    public boolean engineOn;

    public TankEntity(EntityType<? extends TankEntity> type, Level level) {
        this(type, level, TankType.ABRAMS);
    }

    public TankEntity(EntityType<? extends TankEntity> type, Level level, TankType kind) {
        super(type, level);
        this.kind = kind;
        this.geo = kind.geo;
    }

    public static AttributeSupplier.Builder createAttributes(TankType kind) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, kind.health)
                .add(Attributes.ARMOR, 20.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_TURRET, 0f);
        b.define(DATA_GUN, 0f);
        b.define(DATA_RELOAD, 0);
        b.define(DATA_SPEED, 0f);
    }

    @Override
    public ItemStack pickItem() {
        return new ItemStack(kind.item(getBotTeam()));
    }

    @Override
    public Vec3 sightEye() {
        // the gunner's primary sight, on the turret roof left of the gun
        Vec3 m = turretModel(geo.turret()[0] - 1.6, geo.gun()[0] + 1.4, geo.gun()[1] + 0.8, getTurretYaw());
        return toWorld(m.x, m.y, m.z);
    }

    /** the super-heavy tanks are built on a bigger scale */
    @Override
    public double unit() {
        return UNIT * kind.scale;
    }

    @Override
    public int seatCount() {
        return 1;
    }

    @Override
    public boolean isArmored() {
        return true;
    }

    @Override
    public float bulletFactor() {
        return kind.bulletFactor;
    }

    @Override
    protected String seatName0() {
        return "gunsmod.seat.commander";
    }

    public float getTurretYaw() {
        return this.entityData.get(DATA_TURRET);
    }

    public float getGunPitch() {
        return this.entityData.get(DATA_GUN);
    }

    public int getReload() {
        return this.entityData.get(DATA_RELOAD);
    }

    /** speed for the HUD and the tracks in blocks per tick (signed) */
    public double getDisplaySpeed() {
        return this.isControlledByLocalInstance() ? speed : this.entityData.get(DATA_SPEED);
    }

    // ------------------------------------------------------------------------------------ geometry
    /** a point of the turret (model units, turret not rotated) -> model units with the turret turned */
    public Vec3 turretModel(double mx, double my, double mz, float turretYaw) {
        float a = turretYaw * Mth.DEG_TO_RAD;
        double tx = geo.turret()[0], tz = geo.turret()[1];
        double dx = mx - tx, dz = mz - tz;
        return new Vec3(tx + Mth.cos(a) * dx - Mth.sin(a) * dz, my, tz + Mth.sin(a) * dx + Mth.cos(a) * dz);
    }

    /** a point of the gun (model units, gun level) -> model units with the gun elevated and the turret turned */
    public Vec3 gunModel(double mx, double my, double mz, float turretYaw, float pitch) {
        float p = pitch * Mth.DEG_TO_RAD;
        double gy = geo.gun()[0], gz = geo.gun()[1];
        double dy = my - gy, dz = mz - gz;
        return turretModel(mx, gy + Mth.cos(p) * dy + Mth.sin(p) * dz, gz - Mth.sin(p) * dy + Mth.cos(p) * dz, turretYaw);
    }

    @Override
    protected Vec3 seatModel(int seat) {
        double[] s = geo.seat();
        return turretModel(s[0], s[1], s[2], getTurretYaw());
    }

    public Vec3 muzzleWorld() {
        Vec3 m = gunModel(geo.turret()[0], geo.gun()[0], geo.muzzle(), getTurretYaw(), getGunPitch());
        return toWorld(m.x, m.y, m.z);
    }

    public Vec3 coaxWorld() {
        double[] c = geo.coax();
        Vec3 m = gunModel(c[0], c[1], c[2] - 0.2, getTurretYaw(), getGunPitch());
        return toWorld(m.x, m.y, m.z);
    }

    public Vec3 gunDir() {
        return GunLogic.dir(getGunPitch(), this.getYRot() + getTurretYaw());
    }

    // ------------------------------------------------------------------------------------ input
    @Override
    public void trigger(Entity who, int weapon, boolean down) {
        if (who != getControllingPassenger()) return;
        if (weapon == 0) fireMain = down;
        else if (weapon == 1) fireCoax = down;
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
        double max = water ? 0.08 : kind.maxSpeed;
        if (thr > 0.05f) {
            if (speed < -0.01) speed = Math.min(0, speed + 0.025);
            else {
                double k = speed / max;
                speed += 0.0055 * kind.accel * thr * (1.0 - 0.85 * k * k);
            }
        } else if (thr < -0.05f) {
            if (speed > 0.01) speed = Math.max(0, speed - 0.025);
            else speed = Math.max(-MAX_REVERSE, speed - 0.004 * -thr);
        } else {
            speed *= 0.96;
            speed -= Math.signum(speed) * 0.002;
            if (Math.abs(speed) < 0.003) speed = 0;
        }
        speed = Mth.clamp(speed, -MAX_REVERSE, max);
        steer += (st - steer) * 0.25f;
        float yawBefore = this.getYRot();
        double[][] front = geo.front(), back = geo.back();
        boolean wasFront = blockedAfter(Vec3.ZERO, front), wasBack = blockedAfter(Vec3.ZERO, back);
        if (this.onGround() || water) {
            // tracks turn the hull on the spot; a little slower at speed
            float rate = kind.turnRate - 1.0f * (float) Math.min(1, Math.abs(speed) / kind.maxSpeed);
            this.setYRot(this.getYRot() + steer * rate * (speed < -0.01 ? -1 : 1));
        }
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        Vec3 fwd = forward();
        double vy = this.getDeltaMovement().y;
        vy = this.onGround() ? -0.08 : (vy - 0.08) * 0.98;
        if (water) vy = Math.max(vy, -0.06);
        Vec3 motion = fwd.scale(speed).add(0, vy, 0);
        // the long hull: the front or rear corners hitting a wall stop it and turning into a wall is undone
        // (a hull already touching something may always move, so it can never get stuck)
        if (Math.abs(this.getYRot() - yawBefore) > 0.001f && (blockedAfter(Vec3.ZERO, front) && !wasFront || blockedAfter(Vec3.ZERO, back) && !wasBack)) {
            this.setYRot(yawBefore);
        }
        Vec3 h = new Vec3(motion.x, 0, motion.z);
        double[][] lead = speed >= 0 ? front : back;
        if (blockedAfter(h, lead) && !(speed >= 0 ? wasFront : wasBack)) {
            if (Math.abs(speed) > 0.3 && !this.level().isClientSide) this.hurt(this.damageSources().flyIntoWall(), (float) (Math.abs(speed) * 10));
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
        prevTrackL = trackL;
        prevTrackR = trackR;
        prevTiltPitch = tiltPitch;
        prevTiltRoll = tiltRoll;
        prevRock = rock;
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
        float dYaw = Mth.wrapDegrees(this.getYRot() - lastYaw);
        if (Math.abs(dYaw) > 30) dYaw = 0;
        if (!this.isControlledByLocalInstance() && !this.level().isClientSide) speed = signed;
        if (!this.level().isClientSide) this.entityData.set(DATA_SPEED, (float) (this.isControlledByLocalInstance() ? speed : signed));
        // tracks: forward travel plus the turn (half the track gauge is 1.375 blocks)
        double turn = dYaw * Mth.DEG_TO_RAD * 1.375;
        trackL += (float) (signed + turn);
        trackR += (float) (signed - turn);
        lastPos = this.position();
        lastYaw = this.getYRot();
        engineOn = getControllingPassenger() != null && this.isAlive();
        if (this.level().isClientSide) clientVisuals();
        else {
            turretTick();
            crushTick();
        }
    }

    private void clientVisuals() {
        // hull follows the ground under its ends; rocks back when the gun fires
        double hf = groundAt(8, -4.5), hb = groundAt(8, 20.5), hl = groundAt(1.5, 8), hr = groundAt(14.5, 8);
        float wantPitch = (float) Math.toDegrees(Math.atan2(hf - hb, 6.25));
        float wantRoll = (float) Math.toDegrees(Math.atan2(hr - hl, 3.25));
        tiltPitch += (Mth.clamp(wantPitch, -22f, 22f) - tiltPitch) * 0.3f;
        tiltRoll += (Mth.clamp(wantRoll, -18f, 18f) - tiltRoll) * 0.3f;
        int sinceShot = kind.reload - getReload();
        rock = getReload() > 0 && sinceShot < 14 ? Mth.sin(sinceShot * 0.45f) * (1f - sinceShot / 14f) * 2.2f : 0f;
    }

    /** height of the ground under a model point, relative to the tank (blocks) */
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

    private void crushTick() {
        double v = Math.abs(speed);
        if (v < 0.12) return;
        AABB box = this.getBoundingBox().move(forward().scale(Math.signum(speed) * 2.6)).inflate(0.2, 0, 0.2);
        Entity driver = getControllingPassenger();
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.getVehicle() != this && !(e instanceof VehicleEntity) && !(e instanceof JeepEntity))) {
            if (driver != null && BotTeam.friendly(driver, e)) continue;
            e.hurt(GunLogic.source(this.level(), GunLogic.ROADKILL, this, driver != null ? driver : this), (float) (v * 60));
            Vec3 f = forward().scale(Math.signum(speed));
            e.push(f.x * v * 3, 0.3, f.z * v * 3);
        }
    }

    // ------------------------------------------------------------------------------------ turret + guns
    private void turretTick() {
        int reload = getReload();
        if (reload > 0) {
            reload--;
            this.entityData.set(DATA_RELOAD, reload);
            if (reload == 30) this.level().playSound(null, getX(), getY() + 2, getZ(), Reg.sound("tank_reload"), SoundSource.NEUTRAL, 1.4f, 1f);
        }
        if (coaxCooldown > 0) coaxCooldown--;
        LivingEntity crew = getControllingPassenger();
        if (crew instanceof Player p) {
            float want = Mth.wrapDegrees(p.getYRot() - this.getYRot());
            traverse(want, Mth.clamp(p.getXRot(), kind.gunMin, kind.gunMax), kind.traverse, 2.0f);
            if (fireMain && reload == 0) fireMain(p);
            if (fireCoax && coaxCooldown <= 0) fireCoax(p, null);
        } else if (crew instanceof BotEntity bot) {
            botGunner(bot);
        } else {
            fireMain = fireCoax = false;
        }
    }

    private void traverse(float wantYaw, float wantPitch, float yawRate, float pitchRate) {
        float cy = getTurretYaw(), cp = getGunPitch();
        float dy = Mth.clamp(Mth.wrapDegrees(wantYaw - cy), -yawRate, yawRate);
        float dp = Mth.clamp(wantPitch - cp, -pitchRate, pitchRate);
        this.entityData.set(DATA_TURRET, Mth.wrapDegrees(cy + dy));
        this.entityData.set(DATA_GUN, Mth.clamp(cp + dp, kind.gunMin, kind.gunMax));
    }

    private void fireMain(@Nullable LivingEntity gunner) {
        fireMain = false;
        this.entityData.set(DATA_RELOAD, kind.reload);
        Vec3 muzzle = muzzleWorld();
        Vec3 dir = gunDir();
        RocketEntity shell = new RocketEntity(this.level(), gunner, RocketEntity.TANK_SHELL);
        shell.setPos(muzzle.x, muzzle.y, muzzle.z);
        shell.setDeltaMovement(dir.scale(kind.shellSpeed));
        shell.setPower(kind.shellDamageArmour, kind.shellDamageSoft, kind.splash);
        shell.alignToMotion();
        this.level().addFreshEntity(shell);
        this.level().playSound(null, muzzle.x, muzzle.y, muzzle.z, Reg.sound("tank_fire"), SoundSource.NEUTRAL, 9f, 0.95f + this.random.nextFloat() * 0.1f);
        if (this.level() instanceof ServerLevel sl) {
            PacketDistributor.sendToPlayersNear(sl, null, muzzle.x, muzzle.y, muzzle.z, 192, new Net.MuzzleFx(muzzle, dir, 1.6f, this.getId()));
            if (gunner != null) BotEntity.alertGunfire(sl, gunner, 96);
        }
    }

    private void fireCoax(@Nullable LivingEntity gunner, @Nullable Vec3 aimAt) {
        coaxCooldown = 3;
        Vec3 muzzle = coaxWorld();
        Vec3 d = aimAt != null ? aimAt.subtract(muzzle).normalize() : gunDir();
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        Vec3 dir = GunLogic.dir(pitch + (float) this.random.nextGaussian() * 0.7f, yaw + (float) this.random.nextGaussian() * 0.7f);
        Entity shooter = gunner != null ? gunner : this;
        GunLogic.Trace tr = GunLogic.trace(this.level(), shooter, muzzle, dir, 120);
        if (tr.entity() != null && tr.entity() != this && gunner != null) GunLogic.damage(gunner, tr.entity(), 7f, tr.headshot(), GunType.M249);
        List<Vec3> ends = new ArrayList<>();
        ends.add(tr.end().subtract(muzzle));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this, new Net.ShotFx(this.getId(), -2, muzzle, ends, new byte[]{tr.kind()}, false));
        if (gunner != null && this.level() instanceof ServerLevel sl) BotEntity.alertGunfire(sl, gunner, 64);
    }

    // ------------------------------------------------------------------------------------ soldier crew
    /**
     * The closest visible enemy, vehicles first, seen from {@code eye}: only within {@code half} degrees either side of
     * the heading {@code viewYaw} (where the gun or the cockpit points), or nearer than {@code near}.
     */
    @Nullable
    public static LivingEntity scan(Entity vehicle, BotEntity bot, double range, Vec3 eye, @Nullable LivingEntity current, float viewYaw, float half, double near) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : vehicle.level().getEntitiesOfClass(LivingEntity.class, vehicle.getBoundingBox().inflate(range, range * 0.6, range), bot::isEnemy)) {
            if (VehicleEntity.shieldOf(e) != null) continue;    // shoot the vehicle, not the crew
            if (e instanceof JetEntity jet && jet.isAirborne()) continue;    // far too fast: that is the air defence's job
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            if (c.distanceTo(eye) > range || !Sight.inView(eye, viewYaw, half, near, c)) continue;
            if (vehicle.level().clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, vehicle)).getType() != HitResult.Type.MISS) continue;
            double score = c.distanceTo(eye) - (e instanceof VehicleEntity || e instanceof JeepEntity ? 35 : 0) - (e == current ? 12 : 0)
                    - (e instanceof Player ? 10 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    /** where to aim to hit a target with a projectile of the given speed and gravity: lead and drop */
    public static Vec3 lead(Vec3 from, LivingEntity t, double speed, double gravity) {
        Vec3 vel = t.position().subtract(t.xo, t.yo, t.zo);
        Vec3 at = t.position().add(0, t.getBbHeight() * 0.5, 0);
        for (int i = 0; i < 2; i++) {
            double time = at.distanceTo(from) / speed;
            at = t.position().add(0, t.getBbHeight() * 0.5, 0).add(vel.scale(time)).add(0, 0.5 * gravity * time * time, 0);
        }
        return at;
    }

    /** 70 % of the shots are aimed true, the rest miss by a few blocks */
    public static Vec3 rollAimError(net.minecraft.util.RandomSource r) {
        if (r.nextFloat() < 0.7f) return Vec3.ZERO;
        double a = r.nextDouble() * Math.PI * 2, d = 2.5 + r.nextDouble() * 4.0;
        return new Vec3(Math.cos(a) * d, (r.nextDouble() - 0.3) * 2.0, Math.sin(a) * d);
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
            traverse(Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - this.getYRot()), 0f, 1.2f, 1f);
            return;
        }
        traverse(Mth.sin(this.tickCount * 0.015f) * 75f, 0f, 1.2f, 1f);
    }

    private void botGunner(BotEntity bot) {
        if (--scanTimer <= 0) {
            scanTimer = 10;
            // the gunner sees what is in his sights: near where the gun points
            LivingEntity t = scan(this, bot, 96, toWorld(geo.turret()[0], geo.seat()[1] + 3, geo.turret()[1]), bot.getTarget(),
                    this.getYRot() + getTurretYaw(), Sight.TURRET, 6);
            if (t != null && t != bot.getTarget()) {
                bot.setTarget(t);
                aimError = null;
                searchTicks = 0;
            }
        }
        LivingEntity t = bot.getTarget();
        if (t == null || !t.isAlive() || t.distanceToSqr(this) > 110 * 110) {
            search(bot);
            coaxBurst = 0;
            return;
        }
        bot.setAiming(true);
        if (aimError == null) aimError = rollAimError(this.random);
        Vec3 muzzle = muzzleWorld();
        Vec3 aim = lead(muzzle, t, kind.shellSpeed, SHELL_GRAVITY).add(aimError);
        Vec3 to = aim.subtract(toWorld(geo.turret()[0], geo.gun()[0], geo.turret()[1]));
        float wantYaw = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - this.getYRot());
        float wantPitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        traverse(wantYaw, Mth.clamp(wantPitch, kind.gunMin, kind.gunMax), kind.traverse * 0.8f, 1.6f);
        float errYaw = Math.abs(Mth.wrapDegrees(wantYaw - getTurretYaw())), errPitch = Math.abs(wantPitch - getGunPitch());
        boolean los = this.level().clip(new ClipContext(muzzle, t.position().add(0, t.getBbHeight() * 0.6, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
        double dist = t.distanceTo(this);
        if (getReload() == 0 && errYaw < 1.2f && errPitch < 1.2f && los && dist > 6) {
            fireMain(bot);
            aimError = rollAimError(this.random);
        }
        // coaxial machine gun against soldiers: bursts
        boolean soft = !(t instanceof VehicleEntity);
        if (soft && dist < 70 && errYaw < 4f && los) {
            if (coaxPause > 0) coaxPause--;
            else if (coaxCooldown <= 0) {
                Vec3 mg = t.position().add(0, t.getBbHeight() * 0.6, 0);
                if (this.random.nextFloat() >= 0.7f) mg = mg.add((this.random.nextDouble() - 0.5) * 3, 0, (this.random.nextDouble() - 0.5) * 3);
                fireCoax(bot, mg);
                if (++coaxBurst >= 6 + this.random.nextInt(5)) {
                    coaxBurst = 0;
                    coaxPause = 18 + this.random.nextInt(18);
                }
            }
        }
    }

    /** soldiers drive it like a real crew: patrol with halts, stop to fire when the enemy is in sight */
    private void botDrive(BotEntity driver) {
        LivingEntity enemy = driver.getTarget();
        BlockPos h = home != null ? home : this.blockPosition();
        Vec3 goal = GroundBotDriver.onField(this, driver) ? patrol.road(this, driver, enemy, true, 95, 1)
                : patrol.goal(this, enemy, true, Vec3.atBottomCenterOf(h), 110, 95, 10);
        if (goal == null) {
            throttleIn = GroundBotDriver.brake(speed);
            steerIn = 0;
            return;
        }
        Vec3 to = goal.subtract(this.position());
        float desired = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float diff = Mth.wrapDegrees(desired - this.getYRot());
        float st = Mth.clamp(diff / 28f, -1f, 1f);
        float thr = Math.abs(diff) > 75 ? 0.1f : Math.abs(diff) > 35 ? 0.55f : 1f;
        // obstacles: walls, cliffs and water ahead
        double look = 5 + Math.abs(speed) * 16;
        double c = probe(0, look), l = probe(-28, look), r = probe(28, look);
        if (c < look) {
            thr = Math.min(thr, 0.35f);
            st = l > r ? -1f : 1f;
            if (c < 3) thr = -0.8f;
        }
        if (patrol.slow && thr > 0) thr = GroundBotDriver.creep(thr, speed, kind.maxSpeed);
        if (thr > 0.3f && Math.abs(speed) < 0.02) stuckTicks++;
        else stuckTicks = Math.max(0, stuckTicks - 1);
        if (stuckTicks > 40) {
            reverseTicks = 35;
            stuckTicks = 0;
        }
        if (reverseTicks > 0) {
            reverseTicks--;
            thr = -1f;
            st = st == 0 ? 1f : st;
        }
        throttleIn = thr;
        steerIn = st;
    }

    /** free distance in a direction relative to the heading; deep drops and water count as blocked */
    private double probe(float relDeg, double len) {
        float yr = (this.getYRot() + relDeg) * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
        double best = len;
        for (double h : new double[]{1.4, 2.2}) {
            Vec3 from = this.position().add(0, h, 0).add(dir.scale(3.4));
            Vec3 to = from.add(dir.scale(len));
            HitResult hr = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (hr.getType() != HitResult.Type.MISS) best = Math.min(best, hr.getLocation().distanceTo(from));
        }
        Vec3 ahead = this.position().add(dir.scale(5.5));
        BlockPos bp = BlockPos.containing(ahead);
        boolean ground = false;
        for (int i = 0; i < 3 && !ground; i++) {
            BlockPos q = bp.below(i);
            if (!this.level().getFluidState(q).isEmpty()) break;
            ground = !this.level().getBlockState(q).getCollisionShape(this.level(), q).isEmpty();
        }
        if (!ground) best = Math.min(best, 2.5);
        return best;
    }
}
