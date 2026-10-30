package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.entity.HeliType.Weapon;
import com.gunsmod.entity.ai.DriveAI;
import com.gunsmod.entity.ai.Sight;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.gun.GunType;
import com.gunsmod.net.Net;
import com.gunsmod.world.Blast;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
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
 * Attack helicopter. W/S tilt the nose to fly forward / back, A/D turn, jump climbs, sneak descends (sneak only gets
 * you out once landed). Attack fires the selected wing weapon (R switches), use fires the gun, X drops flares. A player
 * in the front seat works the gun himself. Missiles that lock on need the target held in the sight for a second;
 * beam riding missiles fly where the pilot keeps looking. The rotor needs a few seconds to spool up.
 * Soldiers fly patrols with hover stops, stop at a stand-off distance to fire, move to a new firing position now and
 * then, bomb from above (Hind) and throw flares when a missile comes for them.
 */
public class HelicopterEntity extends VehicleEntity implements MissileGuider, Aircraft {
    public static final int LOCK_TICKS = 20;
    private static final double LOCK_RANGE = 220, LOCK_CONE = 3.5;

    private static final EntityDataAccessor<Byte> DATA_WEAPON = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_AMMO = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RELOAD = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FLARES = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LOCK = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_LOCK_PROGRESS = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_WARN = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_CHIN_YAW = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CHIN_PITCH = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.FLOAT);

    /** climb input of the local pilot (jump +1, sneak -1), supplied by the client */
    public static IntSupplier liftInput = () -> 0;

    public final HeliType kind;
    public final VehicleGeo.Heli geo;
    private final double[][] ends;

    private float fwdIn, turnIn, liftIn;
    private float yawRate;
    private boolean fireStores, fireGun, gunnerFire;
    private final int[] ammo, reload;
    private int storeCooldown, gunCooldown, mountIndex, flareCooldown, flareQueue, flareSalvo, lockCandidate = -1, lockCount;
    private int warnMissile, warnRadar, lastBeamShot = -1000;
    @Nullable private Vec3 beamDir;
    // soldier pilot
    private enum Phase { PATROL, RELOCATE, HOLD, BOMB_RUN }
    private Phase phase = Phase.PATROL;
    private int scanTimer, phaseTicks, hidden, hoverLeft, gunBurst, gunPause, salvoLeft;
    private double standAngle;
    private boolean bombed;
    @Nullable private Vec3 patrolPos, aimError;
    private int patrolSince, flaredFor = -1;
    // shared visuals (computed on every side from the motion)
    public float rotor, prevRotor, rotorAngle, prevRotorAngle, tailAngle, prevTailAngle;
    public float visPitch, visRoll, prevVisPitch, prevVisRoll;
    public float prevChinYaw, prevChinPitch;
    private Vec3 lastPos = Vec3.ZERO;
    private double lastAlong;
    private float lastYaw;
    public double airSpeed, climbRate;

    public HelicopterEntity(EntityType<? extends HelicopterEntity> type, Level level) {
        this(type, level, HeliType.COBRA);
    }

    public HelicopterEntity(EntityType<? extends HelicopterEntity> type, Level level, HeliType kind) {
        super(type, level);
        this.kind = kind;
        this.geo = kind.geo;
        double[] h = geo.hub();
        double r = geo.rotorR();
        double[][] base = geo.ends();
        ends = new double[base.length + 4][];
        System.arraycopy(base, 0, ends, 0, base.length);
        ends[base.length] = new double[]{h[0] + r, h[1], h[2]};
        ends[base.length + 1] = new double[]{h[0] - r, h[1], h[2]};
        ends[base.length + 2] = new double[]{h[0], h[1], h[2] + r};
        ends[base.length + 3] = new double[]{h[0], h[1], h[2] - r};
        ammo = new int[kind.weapons.length];
        reload = new int[kind.weapons.length];
        for (int i = 0; i < ammo.length; i++) ammo[i] = kind.weapons[i].ammo;
        this.entityData.set(DATA_FLARES, kind.flares);
        this.entityData.set(DATA_AMMO, ammo[0]);
    }

    public static AttributeSupplier.Builder createAttributes(HeliType kind) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, kind.health)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_WEAPON, (byte) 0);
        b.define(DATA_AMMO, 0);
        b.define(DATA_RELOAD, 0);
        b.define(DATA_FLARES, 0);
        b.define(DATA_LOCK, -1);
        b.define(DATA_LOCK_PROGRESS, (byte) 0);
        b.define(DATA_WARN, (byte) 0);
        b.define(DATA_CHIN_YAW, 0f);
        b.define(DATA_CHIN_PITCH, 0f);
    }

    @Override
    public ItemStack pickItem() {
        return new ItemStack(kind.item(getBotTeam()));
    }

    @Override
    public int seatCount() {
        return kind.seats();
    }

    @Override
    protected Vec3 seatModel(int seat) {
        double[] s = geo.seats()[Math.min(seat, geo.seats().length - 1)];
        return new Vec3(s[0], s[1], s[2]);
    }

    @Override
    public boolean isArmored() {
        return false;
    }

    @Override
    public float bulletFactor() {
        return kind.bulletFactor;
    }

    @Override
    protected String seatName0() {
        return "gunsmod.seat.pilot";
    }

    @Override
    protected String seatName(int seat) {
        return seat == 1 ? "gunsmod.seat.heli_gunner" : "gunsmod.seat.passenger";
    }

    @Override
    public int seatForBot(BotEntity bot) {
        // soldiers only take the controls of a helicopter standing on the ground
        return this.getPassengers().isEmpty() && this.onGround() ? 0 : -1;
    }

    // ------------------------------------------------------------------------------------ state for HUD / renderer
    public int getWeaponIndex() {
        return Mth.clamp(this.entityData.get(DATA_WEAPON), 0, kind.weapons.length - 1);
    }

    public Weapon getWeapon() {
        return kind.weapons[getWeaponIndex()];
    }

    public int getAmmo() {
        return this.entityData.get(DATA_AMMO);
    }

    public int getReload() {
        return this.entityData.get(DATA_RELOAD);
    }

    public int getFlares() {
        return this.entityData.get(DATA_FLARES);
    }

    public int getLockTarget() {
        return this.entityData.get(DATA_LOCK);
    }

    public int getLockProgress() {
        return this.entityData.get(DATA_LOCK_PROGRESS);
    }

    /** 0 nothing, 1 tracked by a radar, 2 missile on the way */
    public int getWarning() {
        return this.entityData.get(DATA_WARN);
    }

    public float getChinYaw() {
        return this.entityData.get(DATA_CHIN_YAW);
    }

    public float getChinPitch() {
        return this.entityData.get(DATA_CHIN_PITCH);
    }

    @Override
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
    @Override
    public void trigger(Entity who, int weapon, boolean down) {
        int seat = seatOf(who);
        if (seat == 1 && who instanceof Player) {
            if (weapon <= 1) gunnerFire = down;
            return;
        }
        if (seat != 0) return;
        switch (weapon) {
            case 0 -> fireStores = down;
            case 1 -> fireGun = down;
            case 2 -> {
                if (down) dropFlares();
            }
            case 3 -> {
                if (down) switchWeapon((getWeaponIndex() + 1) % kind.weapons.length);
            }
            default -> { }
        }
    }

    @Override
    public boolean firesFor(Entity e) {
        int s = seatOf(e);
        return s == 0 || s == 1;
    }

    private void switchWeapon(int i) {
        if (i == getWeaponIndex()) return;
        this.entityData.set(DATA_WEAPON, (byte) i);
        lockCandidate = -1;
        lockCount = 0;
        this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("arm_switch"), SoundSource.NEUTRAL, 1.0f, 1.1f);
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
        // yaw: the tail rotor (or the coaxial rotors) turn it, on the ground only with some lift
        yawRate += (tr * kind.yawRate - yawRate) * 0.18f;
        if (!ground || li > 0) this.setYRot(this.getYRot() + yawRate * power);
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        Vec3 fwd = forward();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        // tilting the rotor disc drives it along the nose; the fuselage resists sliding sideways
        double along = v.x * fwd.x + v.z * fwd.z, side = v.x * right.x + v.z * right.z;
        if (!ground || li > 0) along += f * kind.accel * power;
        along *= 0.976;
        side *= 0.86;
        along = Mth.clamp(along, -kind.maxSpeed * 0.4, kind.maxSpeed);
        double vy = v.y;
        if (power > 0.55 && pilot) {
            double want = li > 0 ? kind.climb * li : li < 0 ? 0.28 * li : 0.0;
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
        int n = rotorToo ? ends.length : ends.length - 4;
        for (int i = 0; i < n; i++) {
            double[] p = ends[i];
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
        if (!this.level().isClientSide) {
            weaponsTick();
            flaresTick();
            int warn = warnMissile > 0 ? 2 : warnRadar > 0 ? 1 : 0;
            if (warnMissile > 0) warnMissile--;
            if (warnRadar > 0) warnRadar--;
            if (getWarning() != warn) this.entityData.set(DATA_WARN, (byte) warn);
        }
    }

    // ------------------------------------------------------------------------------------ warnings and flares
    /** a homing missile is coming for us */
    @Override
    public void missileWarning(GuidedMissileEntity m) {
        warnMissile = 8;
        if (getControllingPassenger() instanceof BotEntity && flaredFor != m.getId()) {
            // soldiers see the warning and dump flares (mostly in time)
            flaredFor = m.getId();
            if (this.random.nextFloat() < 0.85f) dropFlares();
        }
    }

    /** an air defence radar is tracking us */
    @Override
    public void radarWarning() {
        warnRadar = 8;
    }

    private void dropFlares() {
        if (flareCooldown > 0 || getFlares() <= 0 || !this.isAlive()) return;
        flareCooldown = 40;
        flareQueue = 4;
        flareSalvo = this.random.nextInt();
    }

    private void flaresTick() {
        if (flareCooldown > 0) flareCooldown--;
        // the dispensers are slowly refilled from the reserve between salvos
        else if (getFlares() < kind.flares && this.tickCount % 60 == 0) this.entityData.set(DATA_FLARES, getFlares() + 1);
        if (flareQueue <= 0 || flareCooldown % 3 != 1) return;
        flareQueue--;
        Vec3 fwd = forward();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        double[][] pts = geo.flares();
        for (int i = 0; i < pts.length && getFlares() > 0; i++) {
            double[] p = pts[i];
            Vec3 at = toWorld(p[0], p[1], p[2]);
            double s = p[0] < 8 ? -1 : 1;
            Vec3 out = right.scale(s * (0.35 + this.random.nextDouble() * 0.2)).add(fwd.scale(-0.15)).add(0, -0.12 + this.random.nextDouble() * 0.1, 0);
            this.level().addFreshEntity(new FlareEntity(this.level(), at, this.getDeltaMovement().add(out), flareSalvo));
            this.entityData.set(DATA_FLARES, getFlares() - 1);
        }
        this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("flare"), SoundSource.NEUTRAL, 3f, 0.9f + this.random.nextFloat() * 0.2f);
    }

    // ------------------------------------------------------------------------------------ weapons
    private Vec3 mountWorld(Weapon w, int i) {
        double[][] pts = switch (w.mount) {
            case PODS -> geo.pods();
            case RAILS -> geo.rails().length > 0 ? geo.rails() : geo.pods();
            case BOMBS -> geo.bombs().length > 0 ? geo.bombs() : geo.pods();
        };
        double[] p = pts[i % pts.length];
        return toWorld(p[0], p[1] - (w.mount == HeliType.Mount.BOMBS ? 1.2 : 0), p[2] - 0.6);
    }

    /** gun turret pivot (or the fixed cannon) in world space */
    public Vec3 gunWorld() {
        double[] c = geo.chin() != null ? geo.chin() : geo.cannon();
        return toWorld(c[0], c[1], c[2]);
    }

    public Vec3 gunMuzzle() {
        Vec3 d = GunLogic.dir(getChinPitch(), this.getYRot() + getChinYaw());
        if (geo.chin() != null) return gunWorld().add(0, -0.05, 0).add(d.scale((geo.chinLen() + 0.7) * UNIT));
        return gunWorld().add(d.scale(0.2));
    }

    /** where guided missiles are steered from: the sight in the nose */
    public Vec3 sightWorld() {
        double[] n = geo.ends()[0];
        return toWorld(n[0], n[1] - 0.6, n[2] + 0.5);
    }

    @Override
    @Nullable
    public Vec3[] beam() {
        if (beamDir == null || getControllingPassenger() == null || this.tickCount - lastBeamShot > 240) return null;
        return new Vec3[]{sightWorld(), beamDir};
    }

    private void weaponsTick() {
        if (storeCooldown > 0) storeCooldown--;
        if (gunCooldown > 0) gunCooldown--;
        for (int i = 0; i < ammo.length; i++) {
            Weapon w = kind.weapons[i];
            if (ammo[i] <= 0 && reload[i] == 0) reload[i] = w.reload;
            else if (reload[i] > 0 && --reload[i] == 0) {
                ammo[i] = w.ammo;
                if (i == getWeaponIndex()) this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("tank_reload"), SoundSource.NEUTRAL, 1.0f, 1.3f);
            }
        }
        int cur = getWeaponIndex();
        if (this.entityData.get(DATA_AMMO) != ammo[cur]) this.entityData.set(DATA_AMMO, ammo[cur]);
        if (this.entityData.get(DATA_RELOAD) != reload[cur]) this.entityData.set(DATA_RELOAD, reload[cur]);
        LivingEntity pilot = getControllingPassenger();
        Entity front = seatEntity(1);
        if (pilot instanceof Player p) {
            beamDir = sightLine(p);
            lockTick(p);
            Player gunner = front instanceof Player g ? g : p;
            aimGun(Mth.wrapDegrees(gunner.getYRot() - this.getYRot()), gunner.getXRot());
            if (fireStores) fireStores(p, beamDir, lockedTarget());
            boolean gun = front instanceof Player ? gunnerFire : fireGun;
            if (gun && gunCooldown <= 0) fireGun(gunner, null);
        } else if (!(pilot instanceof BotEntity)) {
            fireStores = fireGun = gunnerFire = false;
            beamDir = null;
            setLock(-1, 0);
            aimGun(0, 0);
        }
    }

    /** the pilot's line of sight from the nose sight to what he looks at */
    private Vec3 sightLine(Player p) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 far = eye.add(look.scale(320));
        HitResult hr = this.level().clip(new ClipContext(eye.add(look.scale(3)), far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 at = hr.getType() == HitResult.Type.MISS ? far : hr.getLocation();
        Vec3 d = at.subtract(sightWorld());
        return d.lengthSqr() < 1e-4 ? look : d.normalize();
    }

    @Nullable
    private Entity lockedTarget() {
        if (getWeapon().guide != HeliType.Guide.LOCK || getLockProgress() < LOCK_TICKS) return null;
        return this.level().getEntity(getLockTarget());
    }

    private void setLock(int id, int progress) {
        if (getLockTarget() != id) this.entityData.set(DATA_LOCK, id);
        if (getLockProgress() != progress) this.entityData.set(DATA_LOCK_PROGRESS, (byte) progress);
    }

    /** missiles that lock on: the pilot holds a target in the sight until the seeker has it */
    private void lockTick(Player p) {
        if (getWeapon().guide != HeliType.Guide.LOCK || ammo[getWeaponIndex()] <= 0) {
            setLock(-1, 0);
            return;
        }
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bestAng = LOCK_CONE;
        AABB box = new AABB(eye, eye.add(look.scale(LOCK_RANGE))).inflate(14);
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != this && e.getVehicle() != this
                && !(e instanceof Player pl && pl.isCreative()) && VehicleEntity.shieldOf(e) == null && !BotTeam.friendly(p, e))) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Vec3 to = c.subtract(eye);
            double dist = to.length();
            if (dist > LOCK_RANGE || dist < 6) continue;
            double ang = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1, 1)));
            // vehicles are what the seeker looks for
            if (e instanceof VehicleEntity || e instanceof JeepEntity) ang -= 1.0;
            if (ang >= bestAng) continue;
            if (this.level().clip(new ClipContext(eye.add(look.scale(3)), c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            bestAng = ang;
            best = e;
        }
        int id = best == null ? -1 : best.getId();
        if (id != lockCandidate) {
            lockCandidate = id;
            lockCount = 0;
        } else if (id >= 0 && lockCount < LOCK_TICKS) {
            lockCount++;
            if (lockCount == LOCK_TICKS) this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("lock_on"), SoundSource.NEUTRAL, 0.8f, 1f);
        }
        setLock(id, id < 0 ? 0 : lockCount);
    }

    private void aimGun(float yaw, float pitch) {
        HeliType.Gun g = kind.gun;
        float cy = getChinYaw(), cp = getChinPitch();
        float wy = Mth.clamp(yaw, -g.yawLimit, g.yawLimit), wp = Mth.clamp(pitch, g.pitchMin, g.pitchMax);
        this.entityData.set(DATA_CHIN_YAW, cy + Mth.clamp(Mth.wrapDegrees(wy - cy), -9f, 9f));
        this.entityData.set(DATA_CHIN_PITCH, cp + Mth.clamp(wp - cp, -6f, 6f));
    }

    private void fireStores(LivingEntity shooter, Vec3 dir, @Nullable Entity lockOn) {
        int wi = getWeaponIndex();
        Weapon w = kind.weapons[wi];
        if (storeCooldown > 0 || ammo[wi] <= 0 || rotor < 0.6f) return;
        storeCooldown = w.cooldown;
        mountIndex++;
        Vec3 at = mountWorld(w, mountIndex);
        ammo[wi]--;
        switch (w.mount) {
            case PODS -> {
                RocketEntity r = new RocketEntity(this.level(), shooter, RocketEntity.HELI_ROCKET);
                r.setPos(at.x, at.y, at.z);
                Vec3 d = dir.normalize();
                if (w == Weapon.S8) d = GunLogic.dir((float) -Math.toDegrees(Math.asin(Mth.clamp(d.y, -1, 1))) + (float) this.random.nextGaussian() * 0.6f,
                        (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f + (float) this.random.nextGaussian() * 0.6f);
                r.setDeltaMovement(d.scale(2.2).add(this.getDeltaMovement()));
                r.setPower(w.hitArmour, w.hitSoft, w.blast);
                r.alignToMotion();
                this.level().addFreshEntity(r);
                this.level().playSound(null, at.x, at.y, at.z, Reg.sound("heli_rocket"), SoundSource.NEUTRAL, 5f, (w == Weapon.S8 ? 0.8f : 0.9f) + this.random.nextFloat() * 0.2f);
                muzzleFx(at, d, 0.45f);
            }
            case RAILS -> {
                int k = w == Weapon.HELLFIRE ? GuidedMissileEntity.HELLFIRE : w == Weapon.VIKHR ? GuidedMissileEntity.VIKHR : GuidedMissileEntity.SHTURM;
                GuidedMissileEntity m = new GuidedMissileEntity(this.level(), shooter, k).power(w.hitArmour, w.hitSoft, w.blast);
                m.setPos(at.x, at.y, at.z);
                m.setDeltaMovement(dir.normalize().scale(1.3).add(this.getDeltaMovement()));
                if (w.guide == HeliType.Guide.LOCK) m.target(lockOn);
                else {
                    m.guider(this);
                    lastBeamShot = this.tickCount;
                }
                m.alignToMotion();
                this.level().addFreshEntity(m);
                this.level().playSound(null, at.x, at.y, at.z, Reg.sound("atgm_launch"), SoundSource.NEUTRAL, 6f, 0.95f + this.random.nextFloat() * 0.1f);
                muzzleFx(at, dir.normalize(), 0.6f);
            }
            case BOMBS -> {
                RocketEntity b = new RocketEntity(this.level(), shooter, RocketEntity.BOMB);
                b.setPos(at.x, at.y, at.z);
                b.setDeltaMovement(this.getDeltaMovement().add(0, -0.1, 0));
                b.setPower(w.hitArmour, w.hitSoft, w.blast);
                b.alignToMotion();
                this.level().addFreshEntity(b);
                this.level().playSound(null, at.x, at.y, at.z, Reg.sound("bomb_drop"), SoundSource.NEUTRAL, 4f, 1f);
            }
        }
        if (this.level() instanceof ServerLevel sl) BotEntity.alertGunfire(sl, shooter, 96);
    }

    private void muzzleFx(Vec3 at, Vec3 dir, float size) {
        if (this.level() instanceof ServerLevel sl) PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 128, new Net.MuzzleFx(at, dir, size, this.getId()));
    }

    private void fireGun(LivingEntity shooter, @Nullable Vec3 aimAt) {
        HeliType.Gun g = kind.gun;
        gunCooldown = g.cooldown;
        Vec3 muzzle = gunMuzzle();
        Vec3 d = aimAt != null ? aimAt.subtract(muzzle).normalize() : GunLogic.dir(getChinPitch(), this.getYRot() + getChinYaw());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        Vec3 dir = GunLogic.dir(pitch + (float) this.random.nextGaussian() * g.spread, yaw + (float) this.random.nextGaussian() * g.spread);
        GunLogic.Trace tr = GunLogic.trace(this.level(), shooter, muzzle, dir, 180);
        if (tr.entity() != null && tr.entity() != this) GunLogic.damage(shooter, tr.entity(), g.damage, false, GunType.M249);
        if (g.splash > 0 && tr.kind() != 0) {
            // 30 mm high explosive: a small burst where it hits
            Vec3 e = tr.end();
            this.level().explode(this, e.x, e.y, e.z, g.splash, Level.ExplosionInteraction.NONE);
            if (this.random.nextInt(3) == 0) Blast.fx(this.level(), e, Blast.FX_SMALL, 0.25f);
        }
        List<Vec3> ends = new ArrayList<>();
        ends.add(tr.end().subtract(muzzle));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this, new Net.ShotFx(this.getId(), g.fx, muzzle, ends, new byte[]{tr.kind()}, false));
        if (this.level() instanceof ServerLevel sl) BotEntity.alertGunfire(sl, shooter, 80);
    }

    // ------------------------------------------------------------------------------------ soldier pilot
    private void botPilot(BotEntity bot) {
        if (--scanTimer <= 0) {
            scanTimer = 10;
            // the crew sees what is ahead of the cockpit (and straight below)
            LivingEntity t = TankEntity.scan(this, bot, 140, sightWorld(), bot.getTarget(), this.getYRot(), Sight.HELI, 16);
            if (t != null && t != bot.getTarget()) {
                bot.setTarget(t);
                aimError = null;
                if (phase == Phase.PATROL) startRelocate(t, true);
            }
        }
        LivingEntity t = bot.getTarget();
        if (t != null && (!t.isAlive() || t.distanceToSqr(this) > 170 * 170)) t = null;
        // spool up on the ground first
        if (rotor < 0.92f) {
            fwdIn = turnIn = 0;
            liftIn = rotor > 0.8f ? 0.3f : 0f;
            return;
        }
        Vec3 goal, face;
        double clearance;
        if (t == null) {
            beamDir = null;
            setLock(-1, 0);
            phase = Phase.PATROL;
            BlockPos h = home != null ? home : this.blockPosition();
            Entity esc = com.gunsmod.entity.ai.Convoy.ahead(this, escort);
            if (esc == null && escort != null) {
                escort = null;
                home = this.blockPosition();
            }
            if (esc != null) {
                // convoy escort: over the column a little behind its head, looking the way it drives
                Vec3 f = com.gunsmod.entity.ai.Convoy.forward(esc);
                goal = esc.position().subtract(f.scale(14));
                face = esc.position().add(f.scale(40));
                if (horiz(goal) < 6) goal = null;
                com.gunsmod.entity.ai.Waypoints.keepLoaded(this);
            } else if (hoverLeft > 0) {
                // a stop: hang in the air and look around
                hoverLeft--;
                goal = null;
                face = this.position().add(forward());
            } else {
                if (patrolPos == null || this.tickCount - patrolSince > 1200) {
                    // on an orchard battlefield the pilot knows where the enemy is: patrol over the enemy half
                    boolean field = bot.battle != null && bot.battle.contains(this.position());
                    patrolPos = field ? patrolPoint(bot.battle.front(bot.getBotTeam()), 110) : patrolPoint(Vec3.atBottomCenterOf(h), 170);
                    patrolSince = this.tickCount;
                }
                if (horiz(patrolPos) < 14) {
                    patrolPos = null;
                    hoverLeft = 60 + this.random.nextInt(80);
                    goal = null;
                    face = this.position().add(forward());
                } else {
                    goal = patrolPos;
                    face = goal;
                }
            }
            clearance = 24;
            aimGun(0, 10);
        } else {
            bot.setAiming(true);
            phaseTicks++;
            double dist = horiz(t.position());
            boolean see = sees(t);
            hidden = see ? 0 : hidden + 1;
            Weapon w = chooseWeapon(t);
            double stand = w == null ? 40 : w.guide != HeliType.Guide.NONE ? 75 : w.mount == HeliType.Mount.BOMBS ? 40 : 52;
            clearance = Math.max(12, t.getY() + 12 - groundY());
            switch (phase) {
                case RELOCATE -> {
                    goal = standPoint(t, stand);
                    face = goal;
                    if (horiz(goal) < 10 || phaseTicks > 260) {
                        phase = Phase.HOLD;
                        phaseTicks = 0;
                    }
                }
                case BOMB_RUN -> {
                    // straight over the target at bombing height, release when the bombs will land on it
                    Vec3 away = t.position().subtract(this.position()).multiply(1, 0, 1);
                    goal = t.position().add(away.lengthSqr() < 0.01 ? forward() : away.normalize().scale(45));
                    face = bombed ? goal : t.position();
                    clearance = Math.max(18, t.getY() + 20 - groundY());
                    if (!bombed) bombRelease(bot, t, dist);
                    if (bombed && dist > 40 || phaseTicks > 400) startRelocate(t, false);
                }
                default -> {
                    // hold: hover facing the target at the stand-off distance and fire
                    face = t.position();
                    Vec3 away = this.position().subtract(t.position()).multiply(1, 0, 1);
                    goal = Math.abs(dist - stand) > 12 && away.lengthSqr() > 0.01 ? t.position().add(away.normalize().scale(stand)) : null;
                    if (see) botWeapons(bot, t, dist, w);
                    if (hidden > 50 || phaseTicks > 180 + this.random.nextInt(120)) startRelocate(t, false);
                    else if (kind == HeliType.HIND && phaseTicks > 60 && ammo[weaponOf(Weapon.BOMB)] > 0 && !(t instanceof HelicopterEntity)
                            && this.random.nextInt(200) == 0) {
                        phase = Phase.BOMB_RUN;
                        phaseTicks = 0;
                        bombed = false;
                    }
                }
            }
        }
        fly(goal, face, clearance);
    }

    private boolean sees(LivingEntity t) {
        Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
        return this.level().clip(new ClipContext(sightWorld(), c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private void startRelocate(LivingEntity t, boolean first) {
        Vec3 away = this.position().subtract(t.position());
        double here = Math.atan2(away.z, away.x);
        standAngle = first ? here : here + Math.toRadians((this.random.nextBoolean() ? 1 : -1) * (50 + this.random.nextInt(50)));
        phase = Phase.RELOCATE;
        phaseTicks = 0;
        hidden = 0;
    }

    private Vec3 standPoint(LivingEntity t, double dist) {
        return t.position().add(Math.cos(standAngle) * dist, 0, Math.sin(standAngle) * dist);
    }

    /** turn to {@code face}, fly to {@code goal} (or hover when null) and hold the height above the ground ahead */
    private void fly(@Nullable Vec3 goal, Vec3 face, double clearance) {
        Vec3 to = face.subtract(this.position());
        float desired = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float diff = Mth.wrapDegrees(desired - this.getYRot());
        turnIn = Mth.clamp(diff / 22f, -1f, 1f);
        Vec3 fwd = forward();
        Vec3 v = this.getDeltaMovement();
        double along = v.x * fwd.x + v.z * fwd.z;
        if (goal == null) {
            fwdIn = (float) Mth.clamp(-along * 3.0, -1.0, 1.0);
        } else {
            Vec3 g = goal.subtract(this.position());
            double ahead = g.x * fwd.x + g.z * fwd.z;
            double hd = Math.sqrt(g.x * g.x + g.z * g.z);
            if (Math.abs(diff) > 70 && hd > 20) fwdIn = 0.1f;
            else {
                double want = Mth.clamp(ahead / 30.0, -0.6, 1.0) * kind.maxSpeed;
                fwdIn = (float) Mth.clamp((want - along) * 4.0, -1.0, 1.0);
            }
        }
        double ground = Math.max(groundY(), DriveAI.maxGround(this.level(), this.position(), forward(), 36 + airSpeed * 20));
        double wantY = ground + clearance;
        liftIn = (float) Mth.clamp((wantY - this.getY()) / 5.0, -1.0, 1.0);
    }

    private int weaponOf(Weapon w) {
        for (int i = 0; i < kind.weapons.length; i++) if (kind.weapons[i] == w) return i;
        return 0;
    }

    /** guided missiles for vehicles, rockets for groups on the ground, the gun for the rest; selects it */
    @Nullable
    private Weapon chooseWeapon(LivingEntity t) {
        boolean vehicle = t instanceof VehicleEntity || t instanceof JeepEntity;
        boolean air = t instanceof HelicopterEntity;
        int pick = -1;
        for (int i = 0; i < kind.weapons.length; i++) {
            Weapon w = kind.weapons[i];
            if (ammo[i] <= 0 || w.mount == HeliType.Mount.BOMBS) continue;
            boolean guided = w.guide != HeliType.Guide.NONE;
            if (air && !guided) continue;
            if (pick < 0 || vehicle && guided && kind.weapons[pick].guide == HeliType.Guide.NONE
                    || !vehicle && !guided && kind.weapons[pick].guide != HeliType.Guide.NONE) pick = i;
        }
        if (pick < 0) return null;
        if (pick != getWeaponIndex()) this.entityData.set(DATA_WEAPON, (byte) pick);
        return kind.weapons[pick];
    }

    private void botWeapons(BotEntity bot, LivingEntity t, double dist, @Nullable Weapon w) {
        if (aimError == null) aimError = TankEntity.rollAimError(this.random);
        Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
        Vec3 to = c.subtract(this.position());
        float bearing = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - this.getYRot());
        // the gun follows the target
        Vec3 gunTo = c.subtract(gunWorld());
        float gp = (float) -(Mth.atan2(gunTo.y, Math.sqrt(gunTo.x * gunTo.x + gunTo.z * gunTo.z)) * Mth.RAD_TO_DEG);
        aimGun(bearing, gp);
        beamDir = c.subtract(sightWorld()).normalize();
        if (w != null && storeCooldown <= 0 && Math.abs(bearing) < (w.guide == HeliType.Guide.NONE ? 6f : 15f) && dist > 16 && dist < 130) {
            int wi = getWeaponIndex();
            if (w.guide == HeliType.Guide.LOCK) {
                setLock(t.getId(), LOCK_TICKS);
                if (this.tickCount - lastBeamShot > 50) {
                    fireStores(bot, beamDir, t);
                    lastBeamShot = this.tickCount;
                }
            } else if (w.guide == HeliType.Guide.BEAM) {
                // one missile at a time: keep the sight on the target until it hits
                if (this.tickCount - lastBeamShot > 70) fireStores(bot, beamDir, null);
            } else if (dist < 95) {
                if (salvoLeft <= 0 && this.random.nextInt(25) == 0) salvoLeft = w == Weapon.S8 ? 8 : 4;
                if (salvoLeft > 0) {
                    Vec3 pod = mountWorld(w, mountIndex + 1);
                    Vec3 aim = TankEntity.lead(pod, t, 3.6, 0.004).add(aimError);
                    fireStores(bot, aim.subtract(pod), null);
                    salvoLeft--;
                    aimError = TankEntity.rollAimError(this.random);
                }
            }
            if (ammo[wi] <= 0) salvoLeft = 0;
        }
        boolean soft = !(t instanceof VehicleEntity v) || !v.isArmored();
        float gunErr = Math.abs(Mth.wrapDegrees(bearing - getChinYaw()));
        if (soft && dist < 90 && gunErr < (kind.gun.turret() ? 6f : 3f)) {
            if (gunPause > 0) gunPause--;
            else if (gunCooldown <= 0) {
                Vec3 at = c;
                if (this.random.nextFloat() >= 0.7f) at = at.add((this.random.nextDouble() - 0.5) * 4, 0, (this.random.nextDouble() - 0.5) * 4);
                fireGun(bot, at);
                if (++gunBurst >= (kind.gun == HeliType.Gun.YAKB ? 14 : 6) + this.random.nextInt(4)) {
                    gunBurst = 0;
                    gunPause = 20 + this.random.nextInt(20);
                }
            }
        }
    }

    private void bombRelease(BotEntity bot, LivingEntity t, double dist) {
        int wi = weaponOf(Weapon.BOMB);
        if (ammo[wi] <= 0) {
            bombed = true;
            return;
        }
        double h = Math.max(1, this.getY() - t.getY());
        double fall = Math.sqrt(2 * h / 0.04);
        double throwDist = airSpeed * fall * 0.9;
        Vec3 to = t.position().subtract(this.position());
        float bearing = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - this.getYRot());
        if (Math.abs(bearing) < 12f && dist < throwDist + 3) {
            int keep = getWeaponIndex();
            this.entityData.set(DATA_WEAPON, (byte) wi);
            storeCooldown = 0;
            fireStores(bot, forward(), null);
            if (ammo[wi] > 0) {
                storeCooldown = 0;
                fireStores(bot, forward(), null);
            }
            this.entityData.set(DATA_WEAPON, (byte) keep);
            bombed = true;
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
    private Vec3 patrolPoint(Vec3 home, double leash) {
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

    // ------------------------------------------------------------------------------------ save
    /** convoy: the vehicle at the head of the column this helicopter flies cover for (null = none) */
    @Nullable public java.util.UUID escort;

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (escort != null) tag.putUUID("Escort", escort);
        tag.putIntArray("Ammo", ammo.clone());
        tag.putIntArray("Reloads", reload.clone());
        tag.putInt("Flares", getFlares());
        tag.putByte("Weapon", (byte) getWeaponIndex());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        escort = tag.hasUUID("Escort") ? tag.getUUID("Escort") : null;
        int[] a = tag.getIntArray("Ammo"), r = tag.getIntArray("Reloads");
        for (int i = 0; i < ammo.length; i++) {
            if (i < a.length) ammo[i] = Mth.clamp(a[i], 0, kind.weapons[i].ammo);
            if (i < r.length) reload[i] = Math.max(0, r[i]);
        }
        if (tag.contains("Flares")) this.entityData.set(DATA_FLARES, Mth.clamp(tag.getInt("Flares"), 0, kind.flares));
        this.entityData.set(DATA_WEAPON, (byte) Mth.clamp(tag.getByte("Weapon"), 0, kind.weapons.length - 1));
    }
}
