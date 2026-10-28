package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.block.VehicleSpawnerBlockEntity;
import com.gunsmod.entity.ai.DriveAI;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.gun.GunType;
import com.gunsmod.net.Net;
import com.gunsmod.world.Blast;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * F-16C Fighting Falcon. The jet flies where the pilot looks; W/S move the throttle (full throttle and W again lights
 * the afterburner), A/D are the rudder (steering on the runway). It needs a run along the ground: at 70 km/h look up to
 * rotate and lift off; the gear comes up by itself after take-off and goes down again for landing. Below 40 km/h it
 * stalls. Attack fires the selected missile (R: Maverick against vehicles / Sidewinder against aircraft, both lock on
 * like the Apache's Hellfire), use fires the 20 mm cannon, X drops flares.
 * Shot down in the air it does not just vanish: it burns black, dives on and blows up where it hits the ground.
 * Soldiers take off for real, patrol slowly, hunt only vehicles (never soldiers) with fast attack runs, dodge missiles.
 */
public class JetEntity extends VehicleEntity implements Aircraft {
    public static final double JET_UNIT = 0.375;
    public static final double STALL = 40 / 3.6 / 20, ROTATE = 70 / 3.6 / 20, MAX_SPEED = 180 / 3.6 / 20, PATROL = 50 / 3.6 / 20;
    public static final int LOCK_TICKS = 20, GUN_ROUNDS = 510;
    /** stores: bit 0..3 Mavericks on the pylons, bit 4 / 5 the Sidewinders on the wingtips */
    public static final int MAV_MASK = 0b001111, AIM_MASK = 0b110000, ALL_STORES = 0b111111;

    private static final EntityDataAccessor<Float> DATA_THROTTLE = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_AB = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_GEAR_UP = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CRASH = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_STORES = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GUN = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FLARES = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_WEAPON = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_LOCK = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_LOCK_PROGRESS = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_WARN = SynchedEntityData.defineId(JetEntity.class, EntityDataSerializers.BYTE);

    public final VehicleGeo.Jet geo = VehicleGeo.F16;

    // flight state (simulated where the jet is controlled: the pilot's client, or the server for soldiers and wrecks)
    public double speed;
    public float throttle, spool;
    /** nose up positive; kept here because the mob look control zeroes the entity pitch on the server every tick */
    private float jetPitch;
    private float rudderIn, throttleIn;
    private boolean wasGround = true, endsWere;
    // soldier pilot's wishes: heading, pitch, throttle, reheat
    private float botYaw, botPitch, botThrottle;
    private boolean botAb, botControl;
    // weapons
    private boolean fireStores, fireGun;
    private int storeCooldown, gunCooldown, rearm, flareCooldown, flareQueue, flareSalvo, lockCandidate = -1, lockCount, gunBurst, gunPause;
    private int warnMissile, warnRadar, flaredFor = -1;
    // soldier pilot
    private enum Phase { GROUND, CLIMB, PATROL, ATTACK, EXTEND, EVADE }
    private Phase phase = Phase.GROUND;
    private int phaseTicks, scanTimer, passShots;
    private float taxiYaw;
    @Nullable private Vec3 waypoint;
    private float evadeYaw, evadePitch;
    private Phase resume = Phase.PATROL;
    // crash
    private int crashTicks;
    // shared visuals
    public float gear = 0f, prevGear = 0f;
    public float roll, prevRoll, prevPitch, visPitch;
    private float lastYaw;
    private Vec3 lastPos = Vec3.ZERO;
    public Vec3 velocity = Vec3.ZERO;

    public JetEntity(EntityType<? extends JetEntity> type, Level level) {
        super(type, level);
        this.entityData.set(DATA_STORES, ALL_STORES);
        this.entityData.set(DATA_GUN, GUN_ROUNDS);
        this.entityData.set(DATA_FLARES, 60);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_THROTTLE, 0f);
        b.define(DATA_SPEED, 0f);
        b.define(DATA_AB, false);
        b.define(DATA_GEAR_UP, false);
        b.define(DATA_CRASH, false);
        b.define(DATA_STORES, ALL_STORES);
        b.define(DATA_GUN, GUN_ROUNDS);
        b.define(DATA_FLARES, 60);
        b.define(DATA_WEAPON, (byte) 0);
        b.define(DATA_LOCK, -1);
        b.define(DATA_LOCK_PROGRESS, (byte) 0);
        b.define(DATA_WARN, (byte) 0);
    }

    @Override
    public double unit() {
        return JET_UNIT;
    }

    @Override
    public ItemStack pickItem() {
        return new ItemStack(Reg.vehicleItem(VehicleKind.F16, getBotTeam()));
    }

    @Override
    public int seatCount() {
        return 1;
    }

    /** attitude pivot in model units: the renderer pitches and rolls the jet about this point */
    public static final double PIVOT_Z = 4.0;

    public double pivotYModel() {
        return geo.pivotY() / JET_UNIT;
    }

    @Override
    protected Vec3 seatModel(int seat) {
        // the seat turns with the jet's pitch and bank so the pilot stays in the cockpit
        double py = pivotYModel();
        double x = geo.seat()[0] - 8, y = geo.seat()[1] - py, z = geo.seat()[2] - PIVOT_Z;
        double r = Math.toRadians(-roll), p = Math.toRadians(pitch());
        double x1 = x * Math.cos(r) - y * Math.sin(r), y1 = x * Math.sin(r) + y * Math.cos(r);
        double y2 = y1 * Math.cos(p) - z * Math.sin(p), z2 = y1 * Math.sin(p) + z * Math.cos(p);
        return new Vec3(8 + x1, py + y2, PIVOT_Z + z2);
    }

    @Override
    public boolean isArmored() {
        return false;
    }

    @Override
    public float bulletFactor() {
        return 0.2f;
    }

    @Override
    protected String seatName0() {
        return "gunsmod.seat.pilot";
    }

    @Override
    public int seatForBot(BotEntity bot) {
        return this.getPassengers().isEmpty() && this.onGround() && !isCrashing() ? 0 : -1;
    }

    // ------------------------------------------------------------------------------------ state
    public float getThrottle() {
        return this.isControlledByLocalInstance() ? throttle : this.entityData.get(DATA_THROTTLE);
    }

    public double getAirSpeed() {
        return this.isControlledByLocalInstance() ? speed : this.entityData.get(DATA_SPEED);
    }

    public boolean isAfterburner() {
        return this.entityData.get(DATA_AB);
    }

    public boolean isGearUp() {
        return this.entityData.get(DATA_GEAR_UP);
    }

    public boolean isCrashing() {
        return this.entityData.get(DATA_CRASH);
    }

    public int getStores() {
        return this.entityData.get(DATA_STORES);
    }

    public int getGunRounds() {
        return this.entityData.get(DATA_GUN);
    }

    public int getFlares() {
        return this.entityData.get(DATA_FLARES);
    }

    /** 0 Maverick (ground), 1 Sidewinder (air) */
    public int getWeapon() {
        return this.entityData.get(DATA_WEAPON);
    }

    public int missilesLeft(int weapon) {
        return Integer.bitCount(getStores() & (weapon == 0 ? MAV_MASK : AIM_MASK));
    }

    public int getRearm() {
        return rearm;
    }

    public int getLockTarget() {
        return this.entityData.get(DATA_LOCK);
    }

    public int getLockProgress() {
        return this.entityData.get(DATA_LOCK_PROGRESS);
    }

    public int getWarning() {
        return this.entityData.get(DATA_WARN);
    }

    /** nose up positive, degrees */
    public float pitch() {
        return this.level().isClientSide && !this.isControlledByLocalInstance() ? -this.getXRot() : jetPitch;
    }

    @Override
    public boolean isAirborne() {
        return !this.onGround() && heightAboveGround() > 2.0;
    }

    public double heightAboveGround() {
        BlockPos p = this.blockPosition();
        for (int i = 0; i < 96; i++) {
            BlockPos q = p.below(i);
            if (!this.level().getBlockState(q).getCollisionShape(this.level(), q).isEmpty() || !this.level().getFluidState(q).isEmpty()) {
                return this.getY() - (q.getY() + 1);
            }
        }
        return 96;
    }

    public Vec3 noseDir() {
        return GunLogic.dir(-pitch(), this.getYRot());
    }

    // ------------------------------------------------------------------------------------ input
    @Override
    public void trigger(Entity who, int weapon, boolean down) {
        if (who != getControllingPassenger()) return;
        switch (weapon) {
            case 0 -> fireStores = down;
            case 1 -> fireGun = down;
            case 2 -> {
                if (down) dropFlares();
            }
            case 3 -> {
                if (down) {
                    this.entityData.set(DATA_WEAPON, (byte) (1 - getWeapon()));
                    lockCandidate = -1;
                    lockCount = 0;
                    this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("arm_switch"), SoundSource.NEUTRAL, 1f, 1.1f);
                }
            }
            default -> { }
        }
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        throttleIn = Mth.clamp(player.zza / 0.98f, -1f, 1f);
        rudderIn = Mth.clamp(-player.xxa / 0.98f, -1f, 1f);
        throttle = Mth.clamp(throttle + throttleIn * 0.012f, 0f, 1f);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return new Vec3(0, 0, 1);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return 0.3f;
    }

    private boolean afterburnerWanted() {
        LivingEntity p = getControllingPassenger();
        if (p instanceof Player) return throttle >= 0.99f && throttleIn > 0.1f;
        return p instanceof BotEntity && botAb;
    }

    // ------------------------------------------------------------------------------------ flight
    @Override
    public void travel(Vec3 input) {
        if (this.isControlledByLocalInstance()) physics();
        this.calculateEntityAnimation(false);
    }

    private void physics() {
        LivingEntity pilot = getControllingPassenger();
        boolean crashing = isCrashing();
        boolean ground = this.onGround();
        float yaw = this.getYRot(), pitch = pitch();
        float wantYaw = yaw, wantPitch = pitch;
        if (pilot instanceof Player p) {
            wantYaw = p.getYRot();
            wantPitch = -p.getXRot();
        } else if (pilot instanceof BotEntity && botControl) {
            wantYaw = botYaw;
            wantPitch = botPitch;
            throttle = botThrottle;
        } else if (!crashing) {
            throttle = Math.max(0f, throttle - 0.01f);
            wantPitch = ground ? 0 : Math.max(pitch - 1.5f, -20f);
        }
        boolean ab = !crashing && afterburnerWanted() && spool > 0.95f;
        double thrust = crashing ? 0 : (throttle * 0.0095 + (ab ? 0.0065 : 0)) * spool;
        double drag = 0.00256 * speed * speed + (gear > 0.5f ? 0 : 0.0012 * speed);
        // control authority builds up above the stall speed
        double auth = Mth.clamp((speed - STALL * 0.8) / (STALL * 0.35), 0, 1);
        float rate = (float) (auth * Math.min(1.0, 0.45 + speed / 1.4));
        float dYaw = Mth.wrapDegrees(wantYaw - yaw), dPitch = wantPitch - pitch;
        double vy;
        if (crashing) {
            // out of control: nose falls, it rolls, it dives on
            pitch = Math.max(pitch - 1.1f, -60f);
            yaw += 0.6f;
            speed = Math.max(speed, 1.3) + 0.04 * Math.sin(Math.toRadians(-pitch)) - drag * 0.3;
            vy = 0;
        } else if (ground) {
            // nose wheel steering, take-off rotation once fast enough
            float steer = Mth.clamp(dYaw, -2.0f, 2.0f) * (speed < 0.4 ? 1f : 0.5f) + rudderIn * (speed < 0.4 ? 2.2f : 0.8f);
            if (speed > 0.02 || pilot instanceof Player && Math.abs(rudderIn) > 0.1f) yaw += steer * (float) Math.min(1.0, 0.2 + speed * 3);
            float maxNose = speed > ROTATE * 0.95 ? 12f : 0f;
            pitch += Mth.clamp(Mth.clamp(wantPitch, 0f, maxNose) - pitch, -1.2f, 1.0f);
            speed += thrust - drag - 0.0015 * Math.signum(speed);
            if (pilot instanceof Player && throttle < 0.02f && throttleIn < -0.1f) speed -= 0.02;     // wheel brakes
            if (pilot == null) speed *= 0.96;
            speed = Math.max(0, speed);
            vy = pitch > 4 && speed > ROTATE ? speed * Math.sin(Math.toRadians(pitch)) * 0.6 : -0.08;
        } else {
            pitch += Mth.clamp(dPitch, -3.2f * rate, 3.2f * rate);
            yaw += Mth.clamp(dYaw, -2.8f * rate, 2.8f * rate) + rudderIn * 0.6f * rate;
            // below the stall speed the nose drops whatever the pilot does
            if (auth < 1) pitch -= (float) ((1 - auth) * 1.8);
            pitch = Mth.clamp(pitch, -80f, 80f);
            speed += thrust - drag - 0.04 * Math.sin(Math.toRadians(pitch));
            speed = Math.max(0, speed);
            vy = -(1 - auth) * 0.3;
        }
        speed = Math.min(speed, MAX_SPEED * 1.08);
        this.setYRot(yaw);
        jetPitch = pitch;
        this.setXRot(-pitch);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        Vec3 fwd = forward();
        Vec3 motion = ground && !crashing ? new Vec3(fwd.x * speed, vy, fwd.z * speed) : GunLogic.dir(-pitch, yaw).scale(speed).add(0, vy, 0);
        velocity = motion;
        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);
        this.fallDistance = 0;
        if (this.horizontalCollision) speed *= 0.2;
    }

    /**
     * Server side, whoever flies it (a player's jet is simulated on his client and only its moves reach the server):
     * flying into terrain, a hard or wheels-up landing; a falling wreck explodes where it hits.
     */
    private void impactCheck(Vec3 moved) {
        boolean ground = this.onGround();
        boolean ends = endsBlocked(Vec3.ZERO);
        double hs = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
        if (isCrashing()) {
            if (ground || ends || this.horizontalCollision || ++crashTicks > 400) crashExplode();
        } else if (ends && !endsWere && (hs > 0.3 || moved.y < -0.3) || this.horizontalCollision && hs > 0.45) {
            this.hurt(this.damageSources().flyIntoWall(), (float) (Math.max(hs, -moved.y) * 120));
        } else if (ground && !wasGround) {
            // touchdown: gear down, gentle sink rate, wings level, nose not down
            double sink = -moved.y;
            boolean good = !isGearUp() && sink < 0.45 && pitch() > -4 && Math.abs(roll) < 30 && hs < MAX_SPEED * 0.8;
            if (!good) this.hurt(this.damageSources().fall(), (float) (sink * 160 + hs * (isGearUp() ? 70 : 30)));
        }
        wasGround = ground;
        endsWere = ends;
    }

    private boolean endsBlocked(Vec3 offset) {
        for (double[] p : geo.ends()) {
            if (p[1] < 3 && !isGearUp()) continue;    // the belly point only counts with the wheels up
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
        prevGear = gear;
        prevRoll = roll;
        prevPitch = visPitch;
        boolean engine = getControllingPassenger() != null && this.isAlive() && !isCrashing();
        spool = Mth.clamp(spool + (engine ? 0.0065f : -0.004f), 0f, 1f);
        // a player's jet: the pitch arrived with his last move packet
        if (!this.level().isClientSide && !this.isControlledByLocalInstance()) jetPitch = -this.getXRot();
        if (!this.level().isClientSide) {
            LivingEntity d = getControllingPassenger();
            if (d instanceof BotEntity bot) botPilot(bot);
            else {
                botControl = false;
                if (d == null) throttleIn = rudderIn = 0;
            }
            if (isAirborne()) {
                for (Entity p : this.getPassengers()) if (p instanceof Player pl) pl.setShiftKeyDown(false);
            }
        }
        super.tick();
        if (!this.level().isClientSide) this.setXRot(-jetPitch);
        // what everyone sees: gear travel, bank into turns, burning wreck
        float dYaw = Mth.wrapDegrees(this.getYRot() - lastYaw);
        if (Math.abs(dYaw) > 30) dYaw = 0;
        lastYaw = this.getYRot();
        Vec3 moved = this.position().subtract(lastPos);
        if (moved.lengthSqr() > 100) moved = Vec3.ZERO;
        lastPos = this.position();
        if (!this.isControlledByLocalInstance()) {
            velocity = moved;
            if (!this.level().isClientSide) speed = moved.length();
        }
        if (isCrashing()) roll += 9f;
        else {
            float want = this.onGround() ? 0f : Mth.clamp(dYaw * 16f, -80f, 80f);
            roll += (want - roll) * 0.15f;
        }
        visPitch = pitch();
        boolean up = isGearUp();
        gear = Mth.clamp(gear + (up ? 0.025f : -0.025f), 0f, 1f);
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_SPEED, (float) speed);
            this.entityData.set(DATA_THROTTLE, throttle);
            this.entityData.set(DATA_AB, !isCrashing() && afterburnerWanted() && spool > 0.95f);
            gearLogic();
            weaponsTick();
            flaresTick();
            int warn = warnMissile > 0 ? 2 : warnRadar > 0 ? 1 : 0;
            if (warnMissile > 0) warnMissile--;
            if (warnRadar > 0) warnRadar--;
            if (getWarning() != warn) this.entityData.set(DATA_WARN, (byte) warn);
            impactCheck(moved);
        } else if (isCrashing()) {
            crashFx();
        }
    }

    /** wheels up after take-off, down again for landing */
    private void gearLogic() {
        boolean up = isGearUp();
        double agl = heightAboveGround();
        if (!up && !this.onGround() && agl > 6 && velocity.y > -0.05 && speed > ROTATE) up = true;
        else if (up && (this.onGround() || agl < 14 && speed < 1.35 && velocity.y < 0 && !isCrashing())) up = false;
        if (up != isGearUp()) {
            this.entityData.set(DATA_GEAR_UP, up);
            this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("cover_open"), SoundSource.NEUTRAL, 1.2f, 0.6f);
        }
    }

    // ------------------------------------------------------------------------------------ damage and the crash
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isCrashing()) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide || this.isRemoved()) return;
        if (!isCrashing() && isAirborne()) {
            // shot down: the pilot ejects, the burning jet dives on until it hits the ground
            this.setHealth(1f);
            this.entityData.set(DATA_CRASH, true);
            this.entityData.set(DATA_AB, false);
            crashTicks = 0;
            speed = Math.max(speed, 1.2);
            for (Entity p : new ArrayList<>(this.getPassengers())) {
                p.stopRiding();
                p.setPos(p.getX(), p.getY() + 2.5, p.getZ());
                p.setDeltaMovement(this.getDeltaMovement().scale(0.4).add(0, 1.1, 0));
                p.hurtMarked = true;
                if (p instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0));
            }
            this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("explosion_far"), SoundSource.NEUTRAL, 10f, 1.1f);
            Blast.fx(this.level(), this.position().add(0, 1, 0), Blast.FX_SMALL, 1.0f);
            return;
        }
        if (isCrashing()) return;
        super.die(source);
    }

    private void crashExplode() {
        if (this.isRemoved()) return;
        Vec3 c = this.position().add(0, 1, 0);
        // the whole fuel load goes up: a big fireball, the terrain stays as it is
        Blast.explode(this.level(), this, c, 5.5f, false, Level.ExplosionInteraction.NONE, Blast.FX_MISSILE, 3.2f);
        Blast.fx(this.level(), c.add(this.noseDir().scale(4)), Blast.FX_FIRE, 2.0f);
        Blast.shockwave(this.level(), c, 9, 18f, 4, this, null);
        this.level().playSound(null, c.x, c.y, c.z, Reg.sound("explosion_big"), SoundSource.NEUTRAL, 14f, 0.8f);
        VehicleSpawnerBlockEntity.vehicleGone(this.level(), spawnerPos, this.getUUID());
        this.discard();
    }

    private void crashFx() {
        Vec3 n = toWorld(geo.nozzle()[0], geo.nozzle()[1], geo.nozzle()[2] - 2);
        for (int i = 0; i < 3; i++) {
            this.level().addParticle(Reg.FLAME.get(), n.x + this.random.nextGaussian() * 0.4, n.y, n.z + this.random.nextGaussian() * 0.4, 0, 0.02, 0);
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, n.x, n.y + 0.3, n.z,
                    this.random.nextGaussian() * 0.03, 0.06, this.random.nextGaussian() * 0.03);
        }
        for (double[] tip : geo.tips()) {
            Vec3 w = toWorld(tip[0] * 0.6 + 8 * 0.4, tip[1], tip[2]);
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, w.x, w.y, w.z, 0, 0.05, 0);
        }
        FxHooks.light.accept(this, 15);
    }

    // ------------------------------------------------------------------------------------ warnings and flares
    @Override
    public void missileWarning(GuidedMissileEntity m) {
        warnMissile = 8;
        if (getControllingPassenger() instanceof BotEntity && flaredFor != m.getId()) {
            flaredFor = m.getId();
            if (this.random.nextFloat() < 0.85f) dropFlares();
            // break turn across the missile's path, dive if there is height for it
            Vec3 from = m.position().subtract(this.position());
            float bearing = (float) (Mth.atan2(from.z, from.x) * Mth.RAD_TO_DEG) - 90f;
            evadeYaw = bearing + (this.random.nextBoolean() ? 90f : -90f);
            evadePitch = heightAboveGround() > 45 ? -14f : 12f;
            if (phase != Phase.EVADE) resume = phase == Phase.GROUND || phase == Phase.CLIMB ? Phase.PATROL : phase;
            phase = Phase.EVADE;
            phaseTicks = 0;
        }
    }

    @Override
    public void radarWarning() {
        warnRadar = 8;
        if (getControllingPassenger() instanceof BotEntity && this.random.nextInt(60) == 0) dropFlares();
    }

    private void dropFlares() {
        if (flareCooldown > 0 || getFlares() <= 0 || !this.isAlive()) return;
        flareCooldown = 30;
        flareQueue = 4;
        flareSalvo = this.random.nextInt();
    }

    private void flaresTick() {
        if (flareCooldown > 0) flareCooldown--;
        else if (getFlares() < 60 && this.tickCount % 50 == 0) this.entityData.set(DATA_FLARES, getFlares() + 1);
        if (flareQueue <= 0 || flareCooldown % 3 != 1) return;
        flareQueue--;
        Vec3 right = new Vec3(-noseDir().z, 0, noseDir().x).normalize();
        for (double[] p : geo.flares()) {
            if (getFlares() <= 0) break;
            Vec3 at = toWorld(p[0], p[1], p[2]);
            double s = p[0] < 8 ? -1 : 1;
            Vec3 out = right.scale(s * (0.3 + this.random.nextDouble() * 0.2)).add(0, -0.25, 0);
            this.level().addFreshEntity(new FlareEntity(this.level(), at, velocity.scale(0.7).add(out), flareSalvo));
            this.entityData.set(DATA_FLARES, getFlares() - 1);
        }
        this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("flare"), SoundSource.NEUTRAL, 3f, 1.0f + this.random.nextFloat() * 0.2f);
    }

    // ------------------------------------------------------------------------------------ weapons
    private void weaponsTick() {
        if (storeCooldown > 0) storeCooldown--;
        if (gunCooldown > 0) gunCooldown--;
        // re-armed after a while (faster parked on the ground)
        if (getStores() != ALL_STORES || getGunRounds() < GUN_ROUNDS) {
            rearm += this.onGround() && speed < 0.1 ? 4 : 1;
            if (rearm >= 900) {
                rearm = 0;
                this.entityData.set(DATA_STORES, ALL_STORES);
                this.entityData.set(DATA_GUN, GUN_ROUNDS);
                this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("tank_reload"), SoundSource.NEUTRAL, 1f, 1.2f);
            }
        }
        LivingEntity pilot = getControllingPassenger();
        if (pilot instanceof Player p) {
            lockTick(p.getEyePosition(), p.getLookAngle(), p, 4.0);
            if (fireStores) fireMissile(p, lockedTarget());
            if (fireGun && gunCooldown <= 0) fireGun(p);
        } else if (!(pilot instanceof BotEntity)) {
            fireStores = fireGun = false;
            setLock(-1, 0);
        }
    }

    private boolean wantsTarget(Entity e, int weapon) {
        if (weapon == 1) return e instanceof Aircraft a && a.isAirborne();
        return (e instanceof VehicleEntity || e instanceof JeepEntity) && !(e instanceof Aircraft a && a.isAirborne());
    }

    @Nullable
    private Entity lockedTarget() {
        if (getLockProgress() < LOCK_TICKS) return null;
        return this.level().getEntity(getLockTarget());
    }

    private void setLock(int id, int progress) {
        if (getLockTarget() != id) this.entityData.set(DATA_LOCK, id);
        if (getLockProgress() != progress) this.entityData.set(DATA_LOCK_PROGRESS, (byte) progress);
    }

    /** the seeker looks along the sight line: vehicles for the Maverick, aircraft for the Sidewinder */
    private void lockTick(Vec3 eye, Vec3 look, LivingEntity pilot, double cone) {
        int w = getWeapon();
        if (missilesLeft(w) == 0) {
            setLock(-1, 0);
            return;
        }
        double range = w == 1 ? 260 : 240;
        LivingEntity best = null;
        double bestAng = cone;
        AABB box = new AABB(eye, eye.add(look.scale(range))).inflate(20);
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != this && wantsTarget(e, w)
                && !e.getPassengers().isEmpty() && !BotTeam.friendly(pilot, e.getControllingPassenger()))) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Vec3 to = c.subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 15) continue;
            double ang = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1, 1)));
            if (ang >= bestAng) continue;
            if (this.level().clip(new ClipContext(eye.add(look.scale(4)), c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
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
        if (best instanceof Aircraft a && lockCount > 0) a.radarWarning();
        setLock(id, id < 0 ? 0 : lockCount);
    }

    private void fireMissile(LivingEntity shooter, @Nullable Entity target) {
        int w = getWeapon();
        int stores = getStores();
        if (storeCooldown > 0 || missilesLeft(w) == 0 || isCrashing()) return;
        int mask = w == 0 ? MAV_MASK : AIM_MASK;
        int bit = Integer.numberOfTrailingZeros(stores & mask);
        storeCooldown = w == 0 ? 30 : 40;
        this.entityData.set(DATA_STORES, stores & ~(1 << bit));
        double[] pt = bit < 4 ? geo.pylons()[bit] : geo.tips()[bit - 4];
        Vec3 at = toWorld(pt[0], pt[1] - 0.4, pt[2] + 3.0);
        int kind = w == 0 ? GuidedMissileEntity.MAVERICK : GuidedMissileEntity.AIM9;
        GuidedMissileEntity m = new GuidedMissileEntity(this.level(), shooter, kind).target(target);
        if (w == 0) m.power(85f, 60f, 3.8f);
        else m.power(95f, 0f, 0f);
        m.setPos(at.x, at.y, at.z);
        m.setDeltaMovement(velocity.add(noseDir().scale(0.4)).add(0, -0.15, 0));
        m.alignToMotion();
        this.level().addFreshEntity(m);
        this.level().playSound(null, at.x, at.y, at.z, Reg.sound("jet_missile"), SoundSource.NEUTRAL, 9f, 0.95f + this.random.nextFloat() * 0.1f);
        if (this.level() instanceof ServerLevel sl) {
            PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 160, new Net.MuzzleFx(at, noseDir(), 0.6f, this.getId()));
            BotEntity.alertGunfire(sl, shooter, 96);
        }
        if (w == 0) fireStores = false;
    }

    private void fireGun(LivingEntity shooter) {
        if (getGunRounds() <= 0 || isCrashing()) return;
        gunCooldown = 1;
        this.entityData.set(DATA_GUN, getGunRounds() - 1);
        Vec3 muzzle = toWorld(geo.cannon()[0], geo.cannon()[1], geo.cannon()[2]).add(velocity);
        float yaw = this.getYRot(), pitch = -pitch();
        Vec3 dir = GunLogic.dir(pitch + (float) this.random.nextGaussian() * 0.5f, yaw + (float) this.random.nextGaussian() * 0.5f);
        GunLogic.Trace tr = GunLogic.trace(this.level(), shooter, muzzle, dir, 220);
        if (tr.entity() != null && tr.entity() != this) GunLogic.damage(shooter, tr.entity(), 8f, false, GunType.M249);
        if (tr.kind() != 0 && this.random.nextInt(3) == 0) {
            Vec3 e = tr.end();
            this.level().explode(this, e.x, e.y, e.z, 1.0f, Level.ExplosionInteraction.NONE);
        }
        List<Vec3> ends = new ArrayList<>();
        ends.add(tr.end().subtract(muzzle));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this, new Net.ShotFx(this.getId(), -7, muzzle, ends, new byte[]{tr.kind()}, false));
        if (this.level() instanceof ServerLevel sl && this.tickCount % 10 == 0) BotEntity.alertGunfire(sl, shooter, 96);
    }

    // ------------------------------------------------------------------------------------ soldier pilot
    private void botPilot(BotEntity bot) {
        botControl = true;
        phaseTicks++;
        double agl = heightAboveGround();
        float yaw = this.getYRot();
        botYaw = yaw;
        botPitch = pitch();
        botAb = false;
        if (spool < 0.95f) {
            // engine start: wait for the turbine to spool up
            botThrottle = 0;
            return;
        }
        if (this.onGround() && phase != Phase.GROUND) phase = Phase.GROUND;
        if (--scanTimer <= 0 && phase != Phase.GROUND && phase != Phase.CLIMB) {
            scanTimer = 10;
            LivingEntity t = scanVehicles(bot);
            if (t != null && t != bot.getTarget()) bot.setTarget(t);
            if (t != null && phase == Phase.PATROL) {
                phase = Phase.ATTACK;
                phaseTicks = 0;
                passShots = 0;
            }
        }
        LivingEntity t = bot.getTarget();
        if (t != null && (!t.isAlive() || t.distanceToSqr(this) > 330 * 330 || !wantsTarget(t, 0) && !wantsTarget(t, 1))) {
            t = null;
            bot.setTarget(null);
        }
        switch (phase) {
            case GROUND -> takeOff(agl);
            case CLIMB -> {
                botPitch = 14;
                botThrottle = 1;
                botAb = agl < 20;
                if (agl > 32) phase = Phase.PATROL;
            }
            case PATROL -> patrol(bot);
            case ATTACK -> {
                if (t == null) {
                    phase = Phase.PATROL;
                    setLock(-1, 0);
                } else attack(bot, t);
            }
            case EXTEND -> {
                botYaw = evadeYaw;
                botPitch = agl < 45 ? 14 : 2;
                botThrottle = 1;
                botAb = true;
                // jink: small turns to spoil the enemy's aim
                botYaw += Mth.sin(phaseTicks * 0.25f) * 25f;
                if (t == null) phase = Phase.PATROL;
                else if (phaseTicks > 70 && horiz(t.position()) > 160) {
                    phase = Phase.ATTACK;
                    phaseTicks = 0;
                    passShots = 0;
                }
            }
            case EVADE -> {
                botYaw = evadeYaw + Mth.sin(phaseTicks * 0.4f) * 10f;
                botPitch = evadePitch;
                botThrottle = 1;
                botAb = true;
                if (phaseTicks > 50) {
                    phase = resume;
                    phaseTicks = 0;
                }
            }
        }
        if (phase != Phase.GROUND) terrainGuard(agl);
    }

    /** take-off run along a clear stretch: full power, rotate at 70 km/h, climb out */
    private void takeOff(double agl) {
        if (!this.onGround()) {
            botPitch = 12;
            botThrottle = 1;
            botAb = true;
            if (agl > 8) {
                phase = Phase.CLIMB;
                phaseTicks = 0;
            }
            return;
        }
        if (speed < 0.3 && clearAhead(this.getYRot(), 70) < 65) {
            // the runway ahead is blocked: taxi round to the clearest direction
            if (phaseTicks % 20 == 1) {
                float best = this.getYRot();
                double bestLen = -1;
                for (int i = 0; i < 12; i++) {
                    float a = this.getYRot() + i * 30f;
                    double len = clearAhead(a, 90);
                    if (len > bestLen) {
                        bestLen = len;
                        best = a;
                    }
                }
                taxiYaw = best;
            }
            botYaw = taxiYaw;
            botThrottle = speed < 0.15 ? 0.35f : 0f;
            botPitch = 0;
            return;
        }
        botYaw = this.getYRot();
        botThrottle = 1;
        botAb = true;
        botPitch = speed >= ROTATE ? 11 : 0;
    }

    private double clearAhead(float yawDeg, double len) {
        Vec3 dir = GunLogic.dir(0, yawDeg);
        double best = len;
        for (double h : new double[]{1.0, 2.5, 4.5}) {
            Vec3 from = this.position().add(0, h, 0).add(dir.scale(6));
            HitResult hr = this.level().clip(new ClipContext(from, from.add(dir.scale(len)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (hr.getType() != HitResult.Type.MISS) best = Math.min(best, hr.getLocation().distanceTo(from));
        }
        // the ground must carry on (no cliff or water on the run)
        for (double d = 8; d < best; d += 8) {
            Vec3 p = this.position().add(dir.scale(d));
            int top = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
            BlockPos below = BlockPos.containing(p.x, top - 1, p.z);
            if (Math.abs(top - this.getY()) > 1.5 || !this.level().getFluidState(below).isEmpty()) return d;
        }
        return best;
    }

    /** slow patrol (50 km/h) between waypoints round home, 40 blocks above the ground */
    private void patrol(BotEntity bot) {
        BlockPos h = home != null ? home : this.blockPosition();
        // on an orchard battlefield the pilot knows where the enemy is: patrol over the enemy half
        boolean field = bot.battle != null && bot.battle.contains(this.position());
        Vec3 hv = field ? bot.battle.front(bot.getBotTeam()) : Vec3.atBottomCenterOf(h);
        if (waypoint == null || horiz(waypoint) < 35 || phaseTicks > 1400) {
            double a = this.random.nextDouble() * Math.PI * 2, r = field ? 30 + this.random.nextDouble() * 100 : 60 + this.random.nextDouble() * 160;
            waypoint = new Vec3(hv.x + Math.cos(a) * r, hv.y, hv.z + Math.sin(a) * r);
            phaseTicks = 0;
        }
        Vec3 to = waypoint.subtract(this.position());
        botYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        holdHeight(40);
        holdSpeed(PATROL);
    }

    private void holdHeight(double agl) {
        double ground = DriveAI.maxGround(this.level(), this.position(), new Vec3(velocity.x, 0, velocity.z).normalize(), 40 + speed * 30);
        double want = Math.max(ground, groundY()) + agl;
        botPitch = (float) Mth.clamp((want - this.getY()) * 0.5, -10, 14);
    }

    private void holdSpeed(double target) {
        botThrottle = (float) Mth.clamp(0.2 + (target - speed) * 4.0 + Math.max(0, botPitch) * 0.03, 0.08, 1.0);
        botAb = speed < target * 0.6;
    }

    /** attack run: line up, Maverick at range, cannon closer in, pull out low and extend away, come round again */
    private void attack(BotEntity bot, LivingEntity t) {
        boolean air = t instanceof Aircraft a && a.isAirborne();
        int weapon = air ? 1 : 0;
        if (getWeapon() != weapon) this.entityData.set(DATA_WEAPON, (byte) weapon);
        Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
        double dist = c.distanceTo(this.position());
        // lead: where it will be when the shots get there
        Vec3 tv = t.position().subtract(t.xo, t.yo, t.zo);
        Vec3 aim = c.add(tv.scale(Math.min(40, dist / Math.max(1.5, speed + 2.5))));
        Vec3 to = aim.subtract(this.position());
        botYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        botPitch = (float) Mth.clamp(Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))), -35, 30);
        botThrottle = 1;
        botAb = dist > 110;
        double off = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(noseDir()), -1, 1)));
        // missiles: lock as soon as the seeker sees it, at most two per pass
        if (missilesLeft(weapon) > 0 && off < (air ? 14 : 9) && dist > (air ? 30 : 55) && dist < (air ? 230 : 210)
                && storeCooldown <= 0 && passShots < 2 && hasSight(c)) {
            setLock(t.getId(), LOCK_TICKS);
            fireStores = true;
            fireMissile(bot, t);
            fireStores = false;
            passShots++;
        }
        // cannon: short bursts when the pipper is on it
        if (off < 3.5 && dist < (air ? 75 : 95) && hasSight(c)) {
            if (gunPause > 0) gunPause--;
            else if (gunCooldown <= 0) {
                fireGun(bot);
                if (++gunBurst > 18 + this.random.nextInt(10)) {
                    gunBurst = 0;
                    gunPause = 12;
                }
            }
        }
        // break off: too close or too low
        if (dist < (air ? 28 : 42) || !air && heightAboveGround() < 15 && botPitch < -3) {
            phase = Phase.EXTEND;
            phaseTicks = 0;
            Vec3 away = this.position().subtract(t.position());
            evadeYaw = (float) (Mth.atan2(away.z, away.x) * Mth.RAD_TO_DEG) - 90f + (this.random.nextBoolean() ? 30f : -30f);
        }
    }

    private boolean hasSight(Vec3 c) {
        Vec3 from = toWorld(8, 5, -16);
        return this.level().clip(new ClipContext(from, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** never fly into the ground: look ahead along the flight path and pull up */
    private void terrainGuard(double agl) {
        Vec3 flat = new Vec3(velocity.x, 0, velocity.z);
        if (flat.lengthSqr() < 1e-4) flat = noseDir().multiply(1, 0, 1);
        double ahead = DriveAI.maxGround(this.level(), this.position(), flat.normalize(), 25 + speed * 50);
        double clearance = this.getY() - Math.max(ahead, groundY());
        if (clearance < 14 || agl < 9) {
            botPitch = Math.max(botPitch, clearance < 6 ? 30 : 18);
            botThrottle = 1;
        }
        botPitch = Mth.clamp(botPitch, -40, 35);
    }

    /** enemy vehicles only, never soldiers on foot; aircraft count as well */
    @Nullable
    private LivingEntity scanVehicles(BotEntity bot) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 eye = toWorld(8, 5, -14);
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(280, 200, 280),
                e -> e != this && e.isAlive() && (e instanceof VehicleEntity || e instanceof JeepEntity) && !e.getPassengers().isEmpty())) {
            if (!bot.isEnemy(e)) continue;
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double d = c.distanceTo(eye);
            if (d > 280) continue;
            boolean air = e instanceof Aircraft a && a.isAirborne();
            if (!air && this.level().clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
            double score = d - (air ? 60 : 0) - (e == bot.getTarget() ? 40 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private double horiz(Vec3 p) {
        double dx = p.x - this.getX(), dz = p.z - this.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double groundY() {
        return this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
    }

    // ------------------------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Stores", getStores());
        tag.putInt("GunRounds", getGunRounds());
        tag.putInt("Flares", getFlares());
        tag.putBoolean("Crashing", isCrashing());
        tag.putFloat("Pitch", jetPitch);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Stores")) this.entityData.set(DATA_STORES, tag.getInt("Stores") & ALL_STORES);
        if (tag.contains("GunRounds")) this.entityData.set(DATA_GUN, Mth.clamp(tag.getInt("GunRounds"), 0, GUN_ROUNDS));
        if (tag.contains("Flares")) this.entityData.set(DATA_FLARES, Mth.clamp(tag.getInt("Flares"), 0, 60));
        this.entityData.set(DATA_CRASH, tag.getBoolean("Crashing"));
        jetPitch = tag.getFloat("Pitch");
    }
}
