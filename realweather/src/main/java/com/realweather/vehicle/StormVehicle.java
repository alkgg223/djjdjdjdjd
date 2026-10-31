package com.realweather.vehicle;

import com.realweather.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidType;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * A drivable car, pickup or intercept vehicle. The driver's client runs the physics: engine and brakes limited by
 * the grip of the road (ice and snow are slippery, wet roads less grippy), real steering geometry with slides when
 * the tyres let go, sand that bogs the wheels down in a sandstorm, flood water that slows it, floats it and in the
 * end breaks the windows and forces the doors, and tornado winds that push it and can lift it away (the intercept
 * vehicle drives hydraulic spikes into the ground to hold on). Model space: metres, front toward -Z, +X right.
 */
public class StormVehicle extends Mob {
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(StormVehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(StormVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STEER = SynchedEntityData.defineId(StormVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_THROTTLE = SynchedEntityData.defineId(StormVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(StormVehicle.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_WIPERS = SynchedEntityData.defineId(StormVehicle.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_HORN = SynchedEntityData.defineId(StormVehicle.class, EntityDataSerializers.INT);

    public static final int FLAG_LIGHTS = 1, FLAG_HANDBRAKE = 2, FLAG_GLASS_BROKEN = 4, FLAG_DOORS_OPEN = 8, FLAG_SPIKES = 16, FLAG_BRAKE = 32;

    /** Client hooks (sounds, windshield, controls) installed by the client setup. */
    public static Consumer<StormVehicle> clientTick = v -> { };

    public final VehicleType kind;

    // Driving state (the side that runs the physics owns it).
    public double speed;
    public double vx, vz;
    public float steer;
    public float throttleIn, steerIn;
    public boolean handbrakeIn;
    public boolean sliding;
    public float bog;
    public boolean airborne;
    private int floodTicks;
    private float lastYaw;
    private Vec3 lastPos = Vec3.ZERO;

    // Animation (both sides).
    public float wheelSpin, prevWheelSpin;
    public float spikeDeploy, prevSpikeDeploy;
    public float pitch, prevPitch, roll, prevRoll, lift, prevLift;
    public float wiperPhase, prevWiperPhase;
    public float anemometer, prevAnemometer;
    public float turretYaw, prevTurretYaw;
    public float dishSpin, prevDishSpin;
    public float doorOpen, prevDoorOpen;
    public float rpm = 800F;
    /** Client: per-cell snow and sand on the windshield (see the client windshield logic). */
    public float[] glassSnow, glassSand;
    /** Client: snow lying on the roof and hood, 0..1. */
    public float bodySnow;
    /** Client: last wind measured by the instruments, m/s, and the tornado it belongs to. */
    public float measuredWind;
    /** Client: the strongest wind measured in this encounter, m/s. */
    public float measuredPeak;
    public int measuredTornado = -1;

    public StormVehicle(EntityType<? extends StormVehicle> type, Level level, VehicleType kind) {
        super(type, level);
        this.kind = kind;
        this.setPersistenceRequired();
        this.noCulling = true;
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes(VehicleType kind) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, kind.health)
                .add(Attributes.ARMOR, kind.spikes ? 16.0 : 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.05);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_COLOR, 0);
        b.define(DATA_SPEED, 0F);
        b.define(DATA_STEER, 0F);
        b.define(DATA_THROTTLE, 0F);
        b.define(DATA_FLAGS, (byte) FLAG_LIGHTS);
        b.define(DATA_WIPERS, (byte) 0);
        b.define(DATA_HORN, 0);
    }

    // ------------------------------------------------------------------------------------ synced state
    public int getColor() {
        return entityData.get(DATA_COLOR);
    }

    public void setColor(int rgb) {
        entityData.set(DATA_COLOR, rgb);
    }

    public boolean flag(int f) {
        return (entityData.get(DATA_FLAGS) & f) != 0;
    }

    public void setFlag(int f, boolean on) {
        byte b = entityData.get(DATA_FLAGS);
        entityData.set(DATA_FLAGS, (byte) (on ? b | f : b & ~f));
    }

    public int wipers() {
        return entityData.get(DATA_WIPERS);
    }

    public int hornTicks() {
        return entityData.get(DATA_HORN);
    }

    /** Signed speed along the heading, blocks per tick (the driver's own value, or the synced one). */
    public double displaySpeed() {
        return isControlledByLocalInstance() ? speed : entityData.get(DATA_SPEED);
    }

    public float displaySteer() {
        return isControlledByLocalInstance() ? steer : entityData.get(DATA_STEER);
    }

    public float displayThrottle() {
        return isControlledByLocalInstance() ? throttleIn : entityData.get(DATA_THROTTLE);
    }

    public boolean displayBrake() {
        if (isControlledByLocalInstance()) {
            return handbrakeIn || (throttleIn < -0.05F && speed > 0.02) || (throttleIn > 0.05F && speed < -0.02);
        }
        return flag(FLAG_BRAKE) || flag(FLAG_HANDBRAKE);
    }

    /** 'P', 'R', 'N' or 'D' for the lever and the dash. */
    public char gear() {
        double v = displaySpeed();
        float t = displayThrottle();
        if (Math.abs(v) < 0.01 && Math.abs(t) < 0.05) {
            return flag(FLAG_HANDBRAKE) || getControllingPassenger() == null ? 'P' : 'N';
        }
        return v < -0.005 || (Math.abs(v) < 0.01 && t < -0.05) ? 'R' : 'D';
    }

    public boolean spikesAnchored() {
        return kind.spikes && spikeDeploy > 0.9F;
    }

    // ------------------------------------------------------------------------------------ controls (from the driver's client)
    public void applyInput(Player from, float throttle, float steer, boolean handbrake, int actions) {
        if (from != getControllingPassenger()) {
            return;
        }
        throttleIn = Mth.clamp(throttle, -1F, 1F);
        steerIn = Mth.clamp(steer, -1F, 1F);
        handbrakeIn = handbrake;
        entityData.set(DATA_STEER, steerIn);
        entityData.set(DATA_THROTTLE, throttleIn);
        setFlag(FLAG_HANDBRAKE, handbrake);
        setFlag(FLAG_BRAKE, throttle < -0.05F && entityData.get(DATA_SPEED) > 0.02F);
        if ((actions & 1) != 0) {
            int w = (wipers() + 1) % 3;
            entityData.set(DATA_WIPERS, (byte) w);
            from.displayClientMessage(Component.translatable("realweather.vehicle.wipers." + w), true);
        }
        if ((actions & 2) != 0) {
            entityData.set(DATA_HORN, 12);
        }
        if ((actions & 4) != 0 && kind.spikes) {
            if (Math.abs(entityData.get(DATA_SPEED)) > 0.08F && !flag(FLAG_SPIKES)) {
                from.displayClientMessage(Component.translatable("realweather.vehicle.spikes.stop_first"), true);
            } else {
                setFlag(FLAG_SPIKES, !flag(FLAG_SPIKES));
                from.displayClientMessage(Component.translatable(flag(FLAG_SPIKES) ? "realweather.vehicle.spikes.down" : "realweather.vehicle.spikes.up"), true);
                level().playSound(null, getX(), getY(), getZ(), ModContent.VEHICLE_HYDRAULIC.get(), SoundSource.NEUTRAL, 1F, 1F);
            }
        }
        if ((actions & 8) != 0) {
            setFlag(FLAG_LIGHTS, !flag(FLAG_LIGHTS));
        }
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        if (level().isClientSide) {
            throttleIn = Mth.clamp(player.zza / 0.98F, -1F, 1F);
            steerIn = Mth.clamp(player.xxa / 0.98F, -1F, 1F);
        }
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return new Vec3(0, 0, 1);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return 0.3F;
    }

    // ------------------------------------------------------------------------------------ seats
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < kind.seats;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity d = getFirstPassenger();
        return d instanceof Player p ? p : null;
    }

    /** Model point (metres) to world, with the current heading. */
    public Vec3 toWorld(double mx, double my, double mz) {
        float yr = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yr), fz = Mth.cos(yr);
        double rx = -fz, rz = fx;
        double fwd = -mz;
        return new Vec3(getX() + rx * mx + fx * fwd, getY() + my + lift, getZ() + rz * mx + fz * fwd);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        int seat = Math.max(0, getPassengers().indexOf(passenger));
        float[] s = kind.seatPoints[Math.min(seat, kind.seatPoints.length - 1)];
        Vec3 p = toWorld(s[0], s[1], s[2]);
        Vec3 att = passenger.getVehicleAttachmentPoint(this);
        callback.accept(passenger, p.x - att.x, p.y - att.y, p.z - att.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        int seat = Math.max(0, getPassengers().indexOf(passenger));
        float[] s = kind.seatPoints[Math.min(seat, kind.seatPoints.length - 1)];
        double side = (s[0] < 0 ? -1 : 1) * (kind.size[0] / 2 + 0.7);
        double[][] tries = {{side, s[2]}, {-side, s[2]}, {0, -kind.size[2] / 2 - 1.2}, {0, kind.size[2] / 2 + 1.2}};
        for (double[] t : tries) {
            Vec3 w = toWorld(t[0], 0, t[1]);
            BlockPos bp = BlockPos.containing(w);
            for (int dy = 1; dy >= -2; dy--) {
                Vec3 c = new Vec3(w.x, bp.getY() + dy, w.z);
                BlockPos under = BlockPos.containing(c).below();
                if (level().noCollision(passenger, passenger.getBoundingBox().move(c.subtract(passenger.position())))
                        && !level().getBlockState(under).getCollisionShape(level(), under).isEmpty()) {
                    return c;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.IRON_INGOT) && (getHealth() < getMaxHealth() || flag(FLAG_GLASS_BROKEN) || flag(FLAG_DOORS_OPEN))) {
            if (!level().isClientSide) {
                heal(kind.health * 0.25F);
                setFlag(FLAG_GLASS_BROKEN, false);
                setFlag(FLAG_DOORS_OPEN, false);
                held.consume(1, player);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.3F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive() && getPassengers().isEmpty()) {
            if (!level().isClientSide) {
                ItemStack item = new ItemStack(VehicleRegistry.item(kind));
                item.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(getColor(), false));
                if (!player.addItem(item)) {
                    spawnAtLocation(item);
                }
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isPassenger()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide && getPassengers().size() < kind.seats) {
            player.startRiding(this);
            level().playSound(null, getX(), getY(), getZ(), ModContent.VEHICLE_DOOR.get(), SoundSource.NEUTRAL, 0.8F, 1F);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ------------------------------------------------------------------------------------ damage
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isInvulnerableTo(source)) {
            return false;
        }
        Entity att = source.getEntity();
        if (att != null && att.getVehicle() == this) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FREEZING)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)) {
            return false;
        }
        if (source.getDirectEntity() instanceof Player && !source.is(DamageTypeTags.IS_EXPLOSION)) {
            amount *= 0.3F;
        }
        invulnerableTime = 0;
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float dist, float mul, DamageSource src) {
        if (dist > 4F && !level().isClientSide) {
            float dmg = (dist - 4F) * 3F;
            super.hurt(damageSources().fall(), dmg);
            for (Entity p : getPassengers()) {
                p.hurt(damageSources().fall(), dmg * 0.25F);
            }
            level().playSound(null, getX(), getY(), getZ(), ModContent.VEHICLE_CRASH.get(), SoundSource.NEUTRAL, 1F, 0.8F);
        }
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED || Boolean.getBoolean("realweather.selftest"))) {
            com.realweather.RealWeather.LOG.info("{} removed ({}) at {} health {}", kind.id, reason, position(), getHealth());
        }
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide || isRemoved()) {
            return;
        }
        com.realweather.RealWeather.LOG.info("{} destroyed by {}", kind.id, source.getMsgId());
        super.die(source);
        ejectPassengers();
        level().playSound(null, getX(), getY(), getZ(), ModContent.VEHICLE_CRASH.get(), SoundSource.NEUTRAL, 1.4F, 0.7F);
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 1.2, 0.6, 1.2, 0.02);
        }
        spawnAtLocation(new ItemStack(Items.IRON_INGOT, 4 + random.nextInt(4)));
        discard();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    @Override
    protected void doPush(Entity e) {
        // Too heavy to be pushed around by mobs.
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    // ------------------------------------------------------------------------------------ driving
    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance()) {
            physics();
        }
        calculateEntityAnimation(false);
    }

    private void physics() {
        LivingEntity driver = getControllingPassenger();
        float thr = driver != null ? throttleIn : 0F;
        float st = driver != null ? steerIn : 0F;
        boolean hb = driver != null && handbrakeIn;
        VehicleEnv env = VehicleEnv.sample(this);
        double g = 0.0245;
        double mu = env.grip;
        double vmax = kind.maxSpeed() * (1.0 - 0.7 * bog) * (env.flood > 0.5F ? 0.35 : 1.0);
        float yaw0 = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw0), fz = Mth.cos(yaw0);
        double rx = -fz, rz = fx;
        double vLong = vx * fx + vz * fz;
        double vLat = vx * rx + vz * rz;
        boolean ground = onGround() || env.flood > 0.3F;
        boolean anchored = spikeDeploy > 0.5F;
        sliding = false;
        steer += (st - steer) * 0.25F;
        if (ground && !airborne) {
            double aEng = 0;
            double braking = 0;
            if (thr > 0.05F) {
                if (vLong < -0.02) {
                    braking = thr;
                } else {
                    double k = Math.max(0, vLong) / vmax;
                    aEng = kind.accel * thr * (1.0 - k * k);
                }
            } else if (thr < -0.05F) {
                if (vLong > 0.02) {
                    braking = -thr;
                } else {
                    double rev = kind.maxSpeed() * 0.25;
                    double k = Math.max(0, -vLong) / rev;
                    aEng = -kind.accel * 0.6 * -thr * (1.0 - k * k);
                }
            }
            if (anchored) {
                aEng = 0;
                braking = 1;
            }
            if (env.flood > 1.1F) {
                // Water in the intake: the engine dies.
                aEng = 0;
            }
            if (env.sandy > 0 && bog > 0.75F && Math.abs(vLong) < 0.06) {
                // Bogged down: the wheels just dig in.
                aEng *= 0.18;
            }
            double traction = mu * g;
            if (Math.abs(aEng) > traction) {
                aEng = Math.copySign(traction, aEng);
                sliding = true;
            }
            vLong += aEng;
            double aBrake = braking * kind.brake;
            if (hb) {
                aBrake = Math.max(aBrake, kind.brake * 0.6);
            }
            aBrake = Math.min(aBrake, traction * 1.05);
            vLong -= Math.signum(vLong) * Math.min(Math.abs(vLong), aBrake);
            double rolling = 0.00045 + env.sandy * (0.0035 + 0.012 * bog) + env.snowy * 0.0012 + Math.min(1.5F, env.flood) * 0.012;
            vLong -= Math.signum(vLong) * Math.min(Math.abs(vLong), rolling);
            vLong *= 1.0 - 0.0028 * Math.abs(vLong);

            double maxSteer = Math.toRadians(kind.maxSteerDeg) * (1.0 - 0.6 * Math.min(1.0, Math.abs(vLong) / kind.maxSpeed()));
            double yawWanted = vLong * Math.tan(steer * maxSteer) / kind.wheelbase;
            double demand = Math.abs(vLong * yawWanted);
            double limit = mu * g * 1.3;
            double follow = demand < 1.0E-6 ? 1.0 : Math.min(1.0, limit / demand);
            if (hb && Math.abs(vLong) > 0.15) {
                follow *= 0.35;
                yawWanted *= 1.35;
            }
            if (follow < 0.95) {
                sliding = true;
            }
            double yawRate = yawWanted * (0.35 + 0.65 * follow);
            setYRot(getYRot() + (float) Math.toDegrees(yawRate));
            float yaw1 = getYRot() * Mth.DEG_TO_RAD;
            double fx1 = -Mth.sin(yaw1), fz1 = Mth.cos(yaw1);
            double rx1 = -fz1, rz1 = fx1;
            double oldX = fx * vLong + rx * vLat;
            double oldZ = fz * vLong + rz * vLat;
            double carvedX = fx1 * vLong + rx1 * vLat;
            double carvedZ = fz1 * vLong + rz1 * vLat;
            vx = oldX + (carvedX - oldX) * follow;
            vz = oldZ + (carvedZ - oldZ) * follow;
            // Sideways grip.
            double lat = vx * rx1 + vz * rz1;
            double latGrip = mu * g * (hb ? 0.4 : 1.15);
            if (Math.abs(lat) > latGrip * 1.5) {
                sliding = true;
            }
            double cut = Math.signum(lat) * Math.min(Math.abs(lat), latGrip);
            vx -= rx1 * cut;
            vz -= rz1 * cut;
            speed = vx * fx1 + vz * fz1;
        } else {
            vx *= 0.995;
            vz *= 0.995;
            speed = vx * fx + vz * fz;
        }
        // Flood water carries it along.
        if (env.flood > 0.5F) {
            vx += env.floodFlowX * 0.004;
            vz += env.floodFlowZ * 0.004;
        }
        // Tornado wind.
        double kv = kind.windGrip * (anchored ? 0.12 : 1.0) * (ground ? 1.0 : 2.5);
        vx += (env.windX - vx) * kv * Math.min(1.0, Math.abs(env.windX) * 4 + 0.0);
        vz += (env.windZ - vz) * kv * Math.min(1.0, Math.abs(env.windZ) * 4 + 0.0);
        double vy = getDeltaMovement().y;
        if (env.lifting && env.windY > 0) {
            airborne = true;
            vy += (env.windY - Math.max(0, vy)) * kind.windGrip * 2.0;
            setYRot(getYRot() + (float) (12.0 * kind.windGrip / 0.05));
        } else if (onGround() && vy <= 0) {
            airborne = false;
            vy = -0.08;
        } else {
            vy = (vy - 0.08) * 0.98;
        }
        if (env.flood > 0.8F) {
            vy = Math.max(vy, -0.02) + Math.min(0.03, (env.flood - 0.8F) * 0.04);
        }
        if (!Double.isFinite(vx) || !Double.isFinite(vz) || !Double.isFinite(vy)) {
            vx = 0;
            vz = 0;
            vy = 0;
            speed = 0;
        }
        double cap = kind.maxSpeed() * 1.6 + 0.5;
        double hs = Math.sqrt(vx * vx + vz * vz);
        if (hs > cap) {
            vx *= cap / hs;
            vz *= cap / hs;
        }
        vy = Mth.clamp(vy, -3.0, 2.0);
        Vec3 motion = new Vec3(vx, vy, vz);
        double before = Math.sqrt(vx * vx + vz * vz);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (horizontalCollision && before > 0.08) {
            if (before > 0.42 && !level().isClientSide) {
                hurt(damageSources().flyIntoWall(), (float) (before * 14));
            }
            if (before > 0.3) {
                playSound(ModContent.VEHICLE_CRASH.get(), (float) Math.min(1.5, before), 1F);
            }
            Vec3 after = getDeltaMovement();
            vx = after.x * 0.3;
            vz = after.z * 0.3;
            speed *= 0.3;
        }
        // Sand: digging in during a sandstorm, rocking back and forth gets it out.
        if (env.sandy > 0 && env.sandstorm > 0.15F) {
            float dig = 0.0009F * env.sandstorm * (1F + (Math.abs(speed) < 0.08 && Math.abs(thr) > 0.3F ? 2.5F : 0F));
            bog = Math.min(1F, bog + dig);
            if (thr * lastThrottle < -0.1F) {
                bog = Math.max(0F, bog - 0.08F);
            }
        } else {
            bog = Math.max(0F, bog - 0.003F);
        }
        if (Math.abs(thr) > 0.3F) {
            lastThrottle = thr;
        }
        fallDistance = airborne ? fallDistance : 0F;
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    private float lastThrottle;

    @Override
    public void tick() {
        prevWheelSpin = wheelSpin;
        prevSpikeDeploy = spikeDeploy;
        prevPitch = pitch;
        prevRoll = roll;
        prevLift = lift;
        prevWiperPhase = wiperPhase;
        prevAnemometer = anemometer;
        prevTurretYaw = turretYaw;
        prevDishSpin = dishSpin;
        prevDoorOpen = doorOpen;
        if (!level().isClientSide && getControllingPassenger() == null) {
            throttleIn = 0F;
            steerIn = 0F;
            handbrakeIn = false;
        }
        super.tick();
        Vec3 moved = position().subtract(lastPos);
        double horiz = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
        if (horiz > 4) {
            horiz = 0;
        }
        float yr = getYRot() * Mth.DEG_TO_RAD;
        double signed = moved.x * -Mth.sin(yr) + moved.z * Mth.cos(yr) < 0 ? -horiz : horiz;
        if (!level().isClientSide) {
            entityData.set(DATA_SPEED, (float) (isControlledByLocalInstance() ? speed : signed));
            if (hornTicks() > 0) {
                entityData.set(DATA_HORN, hornTicks() - 1);
            }
            floodCheck();
        }
        // Passengers turn with the vehicle.
        float dy = Mth.wrapDegrees(getYRot() - lastYaw);
        if (Math.abs(dy) > 0.001F && Math.abs(dy) < 40F) {
            for (Entity p : getPassengers()) {
                p.setYRot(p.getYRot() + dy);
                p.setYHeadRot(p.getYHeadRot() + dy);
            }
        }
        lastYaw = getYRot();
        lastPos = position();
        double v = displaySpeed();
        wheelSpin += (float) (v / kind.wheelRadius);
        float target = kind.spikes && flag(FLAG_SPIKES) ? 1F : 0F;
        spikeDeploy += Mth.clamp(target - spikeDeploy, -0.025F, 0.025F);
        int w = wipers();
        if (w > 0 || Mth.sin(wiperPhase) > 0.02F || Mth.cos(wiperPhase) < 0.99F) {
            float rate = w == 2 ? 0.19F : w == 1 ? 0.1F : 0.12F;
            float before = wiperPhase;
            wiperPhase += rate;
            if (w == 0 && Math.floor(before / Mth.TWO_PI) != Math.floor(wiperPhase / Mth.TWO_PI)) {
                wiperPhase = 0F;
                prevWiperPhase = 0F;
            }
        }
        float doorTarget = flag(FLAG_DOORS_OPEN) ? 0.85F + 0.15F * Mth.sin(tickCount * 0.07F) : 0F;
        doorOpen += Mth.clamp(doorTarget - doorOpen, -0.06F, 0.06F);
        tilt();
        if (level().isClientSide) {
            clientTick.accept(this);
        }
    }

    private void floodCheck() {
        BlockPos p = blockPosition().above();
        if (!level().getFluidState(p).isEmpty()) {
            floodTicks++;
            if (floodTicks == 80 && !flag(FLAG_GLASS_BROKEN)) {
                // The water pressure breaks the windows and forces the doors open.
                setFlag(FLAG_GLASS_BROKEN, true);
                setFlag(FLAG_DOORS_OPEN, true);
                level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.2F, 0.9F);
                if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), getX(), getY() + 1.2, getZ(),
                            40, kind.size[0] / 2, 0.4, kind.size[2] / 3, 0.1);
                }
            }
        } else {
            floodTicks = Math.max(0, floodTicks - 2);
        }
    }

    /** The body follows the ground under the four wheels: nose up on a slope, leaning when one side is on a block. */
    private void tilt() {
        float[] h = new float[4];
        float base = (float) getY();
        for (int i = 0; i < 4; i++) {
            float[] wp = kind.wheels[i];
            Vec3 p = toWorld(wp[0], 0, wp[2]).add(0, -lift, 0);
            h[i] = groundAt(p.x, base + 1.1, p.z, base - 1.6);
        }
        float front = (h[0] + h[1]) / 2F;
        float rear = (h[2] + h[3]) / 2F;
        float left = (h[0] + h[2]) / 2F;
        float right = (h[1] + h[3]) / 2F;
        float wheelbase = Math.abs(kind.wheels[2][2] - kind.wheels[0][2]);
        float track = Math.abs(kind.wheels[1][0] - kind.wheels[0][0]);
        float tp = (float) Math.toDegrees(Math.atan2(front - rear, wheelbase));
        float tr = (float) Math.toDegrees(Math.atan2(left - right, track));
        if (airborne) {
            tp = pitch * 0.98F + Mth.sin(tickCount * 0.3F) * 2F;
            tr = roll * 0.98F + Mth.cos(tickCount * 0.23F) * 3F;
        }
        tp = Mth.clamp(tp, -35F, 35F);
        tr = Mth.clamp(tr, -30F, 30F);
        pitch += (tp - pitch) * 0.35F;
        roll += (tr - roll) * 0.35F;
        // Sit at the average of the wheel heights (the collision box rests on the highest point).
        float avg = (front + rear) / 2F;
        float target = Mth.clamp(avg - base, -0.6F, 0F);
        if (airborne) {
            target = 0F;
        }
        lift += (target - lift) * 0.4F;
    }

    private float groundAt(double x, double top, double z, double bottom) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (int y = Mth.floor(top); y >= Mth.floor(bottom); y--) {
            m.set(bx, y, bz);
            BlockState s = level().getBlockState(m);
            VoxelShape shape = s.getCollisionShape(level(), m);
            if (!shape.isEmpty()) {
                double t = y + shape.max(net.minecraft.core.Direction.Axis.Y);
                if (t <= top) {
                    return (float) t;
                }
            }
        }
        return (float) bottom;
    }

    // ------------------------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Color", getColor());
        tag.putByte("Flags", entityData.get(DATA_FLAGS));
        tag.putFloat("Bog", bog);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setColor(tag.getInt("Color"));
        entityData.set(DATA_FLAGS, tag.getByte("Flags"));
        bog = tag.getFloat("Bog");
    }
}
