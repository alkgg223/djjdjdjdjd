package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.entity.ai.DriveAI;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.gun.GunType;
import com.gunsmod.net.Net;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Attack helicopter: pilot (rear) + front seat. W/S tilt the nose to fly forward / back, A/D turn, jump climbs, sneak
 * descends (sneak only gets you out once landed). Attack fires rockets from the wing pods, use fires the chin gun; both
 * go where the pilot looks. The rotor needs a few seconds to spool up. Soldiers fly patrols and attack runs.
 */
public class HelicopterEntity extends VehicleEntity {
    public static final double HUB_X = 8.0, HUB_Y = 10.7, HUB_Z = 3.0;
    public static final double TAIL_X = 8.95, TAIL_Y = 8.4, TAIL_Z = 28.3;
    public static final double CHIN_X = 8.0, CHIN_Y = 1.6, CHIN_Z = -9.6;
    public static final double ROTOR_R = 18.4;
    private static final double[][] SEATS = {{8.0, 2.72, -1.8}, {8.0, 2.12, -6.6}};
    private static final double[][] PODS = {{3.5, 2.95, -0.6}, {12.5, 2.95, -0.6}};
    /** nose, tail and rotor tips that must not go into blocks */
    private static final double[][] ENDS = {{8, 4, -12.6}, {8, 7, 29.5}, {8 + ROTOR_R, 10.7, 3}, {8 - ROTOR_R, 10.7, 3}, {8, 10.7, 3 + ROTOR_R}, {8, 10.7, 3 - ROTOR_R}};
    public static final double MAX_SPEED = 1.35;
    public static final int ROCKETS = 16, ROCKET_RELOAD = 160;
    public static final double ROCKET_SPEED = 3.6;

    private static final EntityDataAccessor<Integer> DATA_ROCKETS = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RELOAD = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_CHIN_YAW = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CHIN_PITCH = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.FLOAT);

    /** climb input of the local pilot (jump +1, sneak -1), supplied by the client */
    public static IntSupplier liftInput = () -> 0;

    private float fwdIn, turnIn, liftIn;
    private float yawRate;
    private boolean fireRockets, fireGun;
    private int rocketCooldown, gunCooldown, pod, scanTimer, salvoLeft, phaseTicks, gunBurst, gunPause;
    private boolean attackRun;
    @Nullable private Vec3 aimError;
    @Nullable private Vec3 patrolPos;
    private int patrolSince;
    // shared visuals (computed on every side from the motion)
    public float rotor, prevRotor, rotorAngle, prevRotorAngle, tailAngle, prevTailAngle;
    public float visPitch, visRoll, prevVisPitch, prevVisRoll;
    public float prevChinYaw, prevChinPitch;
    private Vec3 lastPos = Vec3.ZERO;
    private double lastAlong;
    private float lastYaw;
    public double airSpeed, climbRate;

    public HelicopterEntity(EntityType<? extends HelicopterEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 180.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_ROCKETS, ROCKETS);
        b.define(DATA_RELOAD, 0);
        b.define(DATA_CHIN_YAW, 0f);
        b.define(DATA_CHIN_PITCH, 0f);
    }

    @Override
    public ItemStack pickItem() {
        return new ItemStack(getBotTeam() == BotTeam.RED ? Reg.RED_HELICOPTER.get() : Reg.GREEN_HELICOPTER.get());
    }

    @Override
    public int seatCount() {
        return 2;
    }

    @Override
    protected Vec3 seatModel(int seat) {
        double[] s = SEATS[Math.min(seat, 1)];
        return new Vec3(s[0], s[1], s[2]);
    }

    @Override
    public boolean isArmored() {
        return false;
    }

    @Override
    public float bulletFactor() {
        return 0.3f;
    }

    @Override
    protected String seatName0() {
        return "gunsmod.seat.pilot";
    }

    @Override
    public int seatForBot(BotEntity bot) {
        // soldiers only take the controls of a helicopter standing on the ground
        return this.getPassengers().isEmpty() && this.onGround() ? 0 : -1;
    }

    public int getRockets() {
        return this.entityData.get(DATA_ROCKETS);
    }

    public int getRocketReload() {
        return this.entityData.get(DATA_RELOAD);
    }

    public float getChinYaw() {
        return this.entityData.get(DATA_CHIN_YAW);
    }

    public float getChinPitch() {
        return this.entityData.get(DATA_CHIN_PITCH);
    }

    public boolean isAirborne() {
        return !this.onGround() && heightAboveGround() > 1.2;
    }

    public double heightAboveGround() {
        BlockPos p = this.blockPosition();
        for (int i = 0; i < 64; i++) {
            BlockPos q = p.below(i);
            if (!this.level().getBlockState(q).getCollisionShape(this.level(), q).isEmpty() || !this.level().getFluidState(q).isEmpty()) {
                return this.getY() - (q.getY() + 1);
            }
        }
        return 64;
    }

    // ------------------------------------------------------------------------------------ input
    public void setTrigger(int weapon, boolean down) {
        if (weapon == 0) fireRockets = down;
        else fireGun = down;
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        fwdIn = Mth.clamp(player.zza / 0.98f, -1f, 1f);
        turnIn = Mth.clamp(-player.xxa / 0.98f, -1f, 1f);
        liftIn = this.level().isClientSide ? liftInput.getAsInt() : 0;
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return new Vec3(0, 0, 1);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return 0.3f;
    }

    // ------------------------------------------------------------------------------------ flight
    @Override
    public void travel(Vec3 input) {
        if (this.isControlledByLocalInstance()) physics();
        this.calculateEntityAnimation(false);
    }

    private void physics() {
        boolean pilot = getControllingPassenger() != null;
        float f = pilot ? fwdIn : 0, tr = pilot ? turnIn : 0, li = pilot ? liftIn : 0;
        float power = rotor;
        Vec3 v = this.getDeltaMovement();
        boolean ground = this.onGround();
        // yaw: the tail rotor turns it, on the ground only with some lift
        yawRate += (tr * 3.4f - yawRate) * 0.18f;
        if (!ground || li > 0) this.setYRot(this.getYRot() + yawRate * power);
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        Vec3 fwd = forward();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        // tilting the rotor disc drives it along the nose; the fuselage resists sliding sideways
        double along = v.x * fwd.x + v.z * fwd.z, side = v.x * right.x + v.z * right.z;
        if (!ground || li > 0) along += f * 0.034 * power;
        along *= 0.976;
        side *= 0.86;
        along = Mth.clamp(along, -MAX_SPEED * 0.4, MAX_SPEED);
        double vy = v.y;
        if (power > 0.55 && pilot) {
            double want = li > 0 ? 0.32 * li : li < 0 ? 0.28 * li : 0.0;
            vy += (want - vy) * 0.12 * power;
        } else {
            // no lift: it drops, a turning rotor (autorotation) slows the fall a little
            vy = (vy - 0.05 * (1.0 - power * 0.7)) * 0.985;
        }
        if (ground && li <= 0 && vy <= 0.01) {
            along *= 0.5;
            side *= 0.5;
            vy = -0.04;
        }
        vy = Mth.clamp(vy, -1.5, 0.5);
        Vec3 motion = fwd.scale(along).add(right.scale(side)).add(0, vy, 0);
        // the nose, tail and rotor tips hitting terrain
        if (endsBlocked(motion, power > 0.25) && !endsBlocked(Vec3.ZERO, power > 0.25)) {
            double sp = motion.length();
            if (sp > 0.35 && !this.level().isClientSide) this.hurt(this.damageSources().flyIntoWall(), (float) (sp * 30 + (power > 0.25 ? 10 : 0)));
            motion = new Vec3(-motion.x * 0.3, Math.min(vy, 0), -motion.z * 0.3);
        }
        this.setDeltaMovement(motion);
        double fall = vy;
        double hs = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        this.move(MoverType.SELF, motion);
        if (this.horizontalCollision && hs > 0.45 && !this.level().isClientSide) this.hurt(this.damageSources().flyIntoWall(), (float) (hs * 25));
        if (this.onGround() && !ground && fall < -0.55 && !this.level().isClientSide) this.hurt(this.damageSources().fall(), (float) ((-fall - 0.45) * 70));
        this.fallDistance = 0;
    }

    private boolean endsBlocked(Vec3 offset, boolean rotorToo) {
        int n = rotorToo ? ENDS.length : 2;
        for (int i = 0; i < n; i++) {
            double[] p = ENDS[i];
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
        prevRotor = rotor;
        prevRotorAngle = rotorAngle;
        prevTailAngle = tailAngle;
        prevVisPitch = visPitch;
        prevVisRoll = visRoll;
        prevChinYaw = getChinYaw();
        prevChinPitch = getChinPitch();
        boolean engine = getControllingPassenger() != null && this.isAlive();
        rotor = Mth.clamp(rotor + (engine ? 0.011f : -0.005f), 0f, 1f);
        rotorAngle += rotor * 38f;
        tailAngle += rotor * 95f;
        if (rotorAngle > 3600f) {
            rotorAngle -= 3600f;
            prevRotorAngle -= 3600f;
        }
        if (tailAngle > 3600f) {
            tailAngle -= 3600f;
            prevTailAngle -= 3600f;
        }
        if (!this.level().isClientSide) {
            LivingEntity d = getControllingPassenger();
            if (d instanceof BotEntity bot) botPilot(bot);
            else if (d == null) {
                fwdIn = turnIn = liftIn = 0;
            }
            // sneaking only gets the crew out once it has landed
            if (isAirborne()) {
                for (Entity p : this.getPassengers()) if (p instanceof Player pl) pl.setShiftKeyDown(false);
            }
        }
        super.tick();
        // passengers turn with the helicopter
        float dy = Mth.wrapDegrees(this.getYRot() - lastYaw);
        if (Math.abs(dy) > 0.001f && Math.abs(dy) < 30f) {
            for (Entity p : this.getPassengers()) {
                p.setYRot(p.getYRot() + dy);
                p.setYHeadRot(p.getYHeadRot() + dy);
            }
        }
        // attitude from the motion: nose down when speeding up / flying fast, banking into turns
        Vec3 moved = this.position().subtract(lastPos);
        if (moved.lengthSqr() > 9) moved = Vec3.ZERO;
        Vec3 fwd = forward();
        double along = moved.x * fwd.x + moved.z * fwd.z, side = moved.x * -fwd.z + moved.z * fwd.x;
        airSpeed = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
        climbRate = moved.y;
        float wantPitch = Mth.clamp((float) (along * 9.0 + (along - lastAlong) * 140.0), -16f, 22f);
        float wantRoll = Mth.clamp(dy * 5.5f + (float) side * 12f, -32f, 32f);
        if (this.onGround()) wantPitch = wantRoll = 0;
        visPitch += (wantPitch - visPitch) * 0.14f;
        visRoll += (wantRoll - visRoll) * 0.14f;
        lastAlong = along;
        lastPos = this.position();
        lastYaw = this.getYRot();
        if (!this.level().isClientSide) weaponsTick();
    }

    // ------------------------------------------------------------------------------------ weapons
    private Vec3 podWorld(int i) {
        double[] p = PODS[i];
        return toWorld(p[0], p[1], p[2] - 0.6);
    }

    public Vec3 chinMuzzle() {
        Vec3 d = GunLogic.dir(getChinPitch(), this.getYRot() + getChinYaw());
        return toWorld(CHIN_X, CHIN_Y - 0.15, CHIN_Z).add(d.scale(0.9));
    }

    private void weaponsTick() {
        if (rocketCooldown > 0) rocketCooldown--;
        if (gunCooldown > 0) gunCooldown--;
        int reload = getRocketReload();
        if (getRockets() <= 0 && reload == 0) {
            this.entityData.set(DATA_RELOAD, ROCKET_RELOAD);
        } else if (reload > 0) {
            this.entityData.set(DATA_RELOAD, --reload);
            if (reload == 0) {
                this.entityData.set(DATA_ROCKETS, ROCKETS);
                this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("tank_reload"), SoundSource.NEUTRAL, 1.0f, 1.3f);
            }
        }
        LivingEntity pilot = getControllingPassenger();
        if (pilot instanceof Player p) {
            Vec3 look = p.getLookAngle();
            aimChin(Mth.wrapDegrees(p.getYRot() - this.getYRot()), p.getXRot());
            if (fireRockets) fireRocket(p, look);
            if (fireGun && gunCooldown <= 0) fireGun(p, null);
        } else if (!(pilot instanceof BotEntity)) {
            fireRockets = fireGun = false;
            aimChin(0, 0);
        }
    }

    private void aimChin(float yaw, float pitch) {
        float cy = getChinYaw(), cp = getChinPitch();
        float wy = Mth.clamp(yaw, -110f, 110f), wp = Mth.clamp(pitch, -15f, 60f);
        this.entityData.set(DATA_CHIN_YAW, cy + Mth.clamp(Mth.wrapDegrees(wy - cy), -9f, 9f));
        this.entityData.set(DATA_CHIN_PITCH, cp + Mth.clamp(wp - cp, -6f, 6f));
    }

    private void fireRocket(LivingEntity shooter, Vec3 dir) {
        if (rocketCooldown > 0 || getRockets() <= 0 || rotor < 0.6f) return;
        rocketCooldown = 4;
        pod = 1 - pod;
        Vec3 at = podWorld(pod);
        RocketEntity r = new RocketEntity(this.level(), shooter, RocketEntity.HELI_ROCKET);
        r.setPos(at.x, at.y, at.z);
        r.setDeltaMovement(dir.normalize().scale(2.2).add(this.getDeltaMovement()));
        r.alignToMotion();
        this.level().addFreshEntity(r);
        this.entityData.set(DATA_ROCKETS, getRockets() - 1);
        this.level().playSound(null, at.x, at.y, at.z, Reg.sound("heli_rocket"), SoundSource.NEUTRAL, 5f, 0.9f + this.random.nextFloat() * 0.2f);
        if (this.level() instanceof ServerLevel sl) {
            PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 128, new Net.MuzzleFx(at, dir.normalize(), 0.45f, this.getId()));
            BotEntity.alertGunfire(sl, shooter, 96);
        }
    }

    private void fireGun(LivingEntity shooter, @Nullable Vec3 aimAt) {
        gunCooldown = 3;
        Vec3 muzzle = chinMuzzle();
        Vec3 d = aimAt != null ? aimAt.subtract(muzzle).normalize() : GunLogic.dir(getChinPitch(), this.getYRot() + getChinYaw());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        Vec3 dir = GunLogic.dir(pitch + (float) this.random.nextGaussian() * 0.9f, yaw + (float) this.random.nextGaussian() * 0.9f);
        GunLogic.Trace tr = GunLogic.trace(this.level(), shooter, muzzle, dir, 160);
        if (tr.entity() != null && tr.entity() != this) GunLogic.damage(shooter, tr.entity(), 11f, false, GunType.M249);
        List<Vec3> ends = new ArrayList<>();
        ends.add(tr.end().subtract(muzzle));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this, new Net.ShotFx(this.getId(), -3, muzzle, ends, new byte[]{tr.kind()}, false));
        if (this.level() instanceof ServerLevel sl) BotEntity.alertGunfire(sl, shooter, 80);
    }

    // ------------------------------------------------------------------------------------ soldier pilot
    private void botPilot(BotEntity bot) {
        if (--scanTimer <= 0) {
            scanTimer = 10;
            LivingEntity t = TankEntity.scan(this, bot, 120, toWorld(8, 2, -9), bot.getTarget());
            if (t != null && t != bot.getTarget()) {
                bot.setTarget(t);
                aimError = null;
            }
        }
        LivingEntity t = bot.getTarget();
        if (t != null && (!t.isAlive() || t.distanceToSqr(this) > 150 * 150)) t = null;
        // spool up on the ground first
        if (rotor < 0.92f) {
            fwdIn = turnIn = 0;
            liftIn = rotor > 0.8f ? 0.3f : 0f;
            return;
        }
        Vec3 goal;
        double clearance;
        Vec3 face;
        if (t == null) {
            attackRun = false;
            BlockPos h = home != null ? home : this.blockPosition();
            if (patrolPos == null || horiz(patrolPos) < 16 || this.tickCount - patrolSince > 1200) {
                patrolPos = patrolPoint(Vec3.atBottomCenterOf(h));
                patrolSince = this.tickCount;
            }
            goal = patrolPos;
            face = goal;
            clearance = 24;
            aimChin(0, 10);
        } else {
            bot.setAiming(true);
            double dist = horiz(t.position());
            phaseTicks++;
            Vec3 away = this.position().subtract(t.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
            away = away.normalize();
            if (attackRun) {
                // nose on the target, close to rocket range, fire a salvo, then break away
                goal = t.position().add(away.scale(42));
                face = t.position();
                if (phaseTicks > 200 || salvoLeft == 0 && phaseTicks > 30 && dist < 50) {
                    attackRun = false;
                    phaseTicks = 0;
                }
            } else {
                // circle out to the side and come round again
                double a = Math.toRadians(80);
                Vec3 side = new Vec3(away.x * Math.cos(a) - away.z * Math.sin(a), 0, away.x * Math.sin(a) + away.z * Math.cos(a));
                goal = t.position().add(side.scale(60));
                face = goal;
                if (phaseTicks > 90 || horiz(goal) < 15) {
                    attackRun = true;
                    phaseTicks = 0;
                    salvoLeft = 4;
                }
            }
            clearance = Math.max(12, t.getY() + 14 - groundY());
            botWeapons(bot, t, dist);
        }
        // fly: turn to the goal, keep the height above the terrain ahead
        Vec3 to = face.subtract(this.position());
        float desired = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float diff = Mth.wrapDegrees(desired - this.getYRot());
        turnIn = Mth.clamp(diff / 22f, -1f, 1f);
        double hd = horiz(goal);
        if (attackRun && t != null) {
            double dist = horiz(t.position());
            fwdIn = Math.abs(diff) > 45 ? 0f : dist < 34 ? -0.5f : dist > 52 ? 0.8f : 0.15f;
        } else {
            fwdIn = Math.abs(diff) > 70 ? 0.1f : (float) Mth.clamp(hd / 30.0, 0.25, 1.0);
        }
        double ground = Math.max(groundY(), DriveAI.maxGround(this.level(), this.position(), forward(), 36 + airSpeed * 20));
        double wantY = ground + clearance;
        liftIn = (float) Mth.clamp((wantY - this.getY()) / 5.0, -1.0, 1.0);
    }

    private void botWeapons(BotEntity bot, LivingEntity t, double dist) {
        if (aimError == null) aimError = TankEntity.rollAimError(this.random);
        Vec3 podPos = podWorld(pod);
        boolean los = this.level().clip(new ClipContext(podPos, t.position().add(0, t.getBbHeight() * 0.5, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
        Vec3 to = t.position().subtract(this.position());
        float bearing = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - this.getYRot());
        // chin gun follows the target
        Vec3 chinTo = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(toWorld(CHIN_X, CHIN_Y, CHIN_Z));
        float cp = (float) -(Mth.atan2(chinTo.y, Math.sqrt(chinTo.x * chinTo.x + chinTo.z * chinTo.z)) * Mth.RAD_TO_DEG);
        aimChin(bearing, cp);
        if (attackRun && salvoLeft > 0 && los && Math.abs(bearing) < 8f && dist > 14 && dist < 90 && rocketCooldown <= 0) {
            Vec3 aim = TankEntity.lead(podPos, t, ROCKET_SPEED, 0.004).add(aimError);
            fireRocket(bot, aim.subtract(podPos));
            rocketCooldown = 6;
            salvoLeft--;
            aimError = TankEntity.rollAimError(this.random);
        }
        boolean soft = !(t instanceof VehicleEntity) || !((VehicleEntity) t).isArmored();
        if (soft && los && dist < 80 && Math.abs(Mth.wrapDegrees(bearing - getChinYaw())) < 6f) {
            if (gunPause > 0) gunPause--;
            else if (gunCooldown <= 0) {
                Vec3 at = t.position().add(0, t.getBbHeight() * 0.5, 0);
                if (this.random.nextFloat() >= 0.7f) at = at.add((this.random.nextDouble() - 0.5) * 4, 0, (this.random.nextDouble() - 0.5) * 4);
                fireGun(bot, at);
                if (++gunBurst >= 5 + this.random.nextInt(4)) {
                    gunBurst = 0;
                    gunPause = 20 + this.random.nextInt(20);
                }
            }
        }
    }

    private double horiz(Vec3 p) {
        double dx = p.x - this.getX(), dz = p.z - this.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double groundY() {
        return this.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
    }

    /** a patrol point 60-120 blocks away, ahead with some wandering, back towards home near the edge of the area */
    private Vec3 patrolPoint(Vec3 home) {
        double leash = 170;
        Vec3 here = this.position();
        double fromHome = Math.sqrt(here.distanceToSqr(home.x, here.y, home.z));
        float dir;
        if (fromHome > leash * 0.7) {
            Vec3 back = home.subtract(here);
            dir = (float) (Mth.atan2(back.z, back.x) * Mth.RAD_TO_DEG) - 90f + (this.random.nextFloat() - 0.5f) * 80f;
        } else {
            dir = this.getYRot() + (this.random.nextFloat() - 0.5f) * 160f;
        }
        double d = 60 + this.random.nextDouble() * 60;
        double a = dir * Mth.DEG_TO_RAD;
        return new Vec3(here.x - Math.sin(a) * d, here.y, here.z + Math.cos(a) * d);
    }
}
