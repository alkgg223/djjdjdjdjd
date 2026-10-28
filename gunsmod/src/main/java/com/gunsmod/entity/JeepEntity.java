package com.gunsmod.entity;

import com.gunsmod.Reg;
import com.gunsmod.block.VehicleSpawnerBlockEntity;
import com.gunsmod.entity.ai.GroundBotDriver;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.net.Net;
import com.gunsmod.world.Blast;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Military jeep with four seats (driver, turret gunner, passenger, rear). Speed builds up gradually to 40 km/h.
 * Model space: 1 unit = 0.1 block, front at -Z, +X = right side, (8, 0, 8) = entity origin.
 */
public class JeepEntity extends Mob implements BotRideable, MissileGuider {
    public static final int SEAT_DRIVER = 0, SEAT_GUNNER = 1, SEAT_PASSENGER = 2, SEAT_REAR = 3;
    /** seat positions in model units */
    public static final double[][] SEATS = {{2.6, 5.6, 4.6}, {8.0, 16.6, 17.3}, {13.4, 5.6, 4.6}, {13.4, 5.6, 12.6}};
    public static final double TURRET_X = 8.0, TURRET_Y = 19.3, TURRET_Z = 13.0, GUN_PIVOT_Y = 23.6;
    /** the gun mount sits ahead of the ring centre so the gunner can stand behind the grips and see over the shield */
    public static final double GUN_PIVOT_Z = 10.5;
    /** gunner position: behind the ring centre (turns with the turret), eye just above the gun shield */
    public static final double GUNNER_BACK = 4.3, GUNNER_Y = 16.6;
    public static final double MAX_SPEED = 40.0 / 3.6 / 20.0;   // 40 km/h in blocks per tick
    public static final double MAX_REVERSE = 12.0 / 3.6 / 20.0;
    private static final double WHEELBASE = 2.9;

    private static final EntityDataAccessor<Byte> DATA_TEAM = SynchedEntityData.defineId(JeepEntity.class, EntityDataSerializers.BYTE);
    private static final List<EntityDataAccessor<Integer>> DATA_SEATS = new ArrayList<>();
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(JeepEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STEER = SynchedEntityData.defineId(JeepEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TURRET_YAW = SynchedEntityData.defineId(JeepEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TURRET_PITCH = SynchedEntityData.defineId(JeepEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEAT = SynchedEntityData.defineId(JeepEntity.class, EntityDataSerializers.FLOAT);

    static {
        for (int i = 0; i < 4; i++) DATA_SEATS.add(SynchedEntityData.defineId(JeepEntity.class, EntityDataSerializers.INT));
    }

    /** client hook (engine sound, smoke) installed by the client setup */
    public static Consumer<JeepEntity> clientTick = j -> { };

    public double speed;          // signed, blocks per tick along the heading
    public float steer;           // -1..1 smoothed
    public float wheelSpin, prevWheelSpin;
    public float prevTurretYaw, prevTurretPitch;
    private float throttleIn, steerIn;
    private int pendingSeat = -1;
    private boolean gunnerFiring;
    private boolean overheated;
    private int turretCooldown;
    private int lastPassengerFire;
    private final GroundBotDriver patrol = new GroundBotDriver();
    /** patrol centre (where it was placed) and the vehicle spawner that owns it */
    @Nullable public BlockPos home;
    @Nullable public BlockPos spawnerPos;
    private int stuckTicks, reverseTicks;
    private Vec3 lastPos = Vec3.ZERO;
    private float lastYaw;
    public boolean engineStarted;

    /** TOW variant: the turret carries an anti-tank missile launcher instead of the machine gun */
    public final boolean tow;
    public static final int TOW_RELOAD = 100;
    private int towReload, lastTowShot = -1000, towScan;

    public JeepEntity(EntityType<? extends JeepEntity> type, Level level) {
        this(type, level, false);
    }

    public JeepEntity(EntityType<? extends JeepEntity> type, Level level, boolean tow) {
        super(type, level);
        this.tow = tow;
        this.setPersistenceRequired();
        this.noCulling = true;
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_TEAM, (byte) 0);
        for (EntityDataAccessor<Integer> a : DATA_SEATS) b.define(a, -1);
        b.define(DATA_SPEED, 0f);
        b.define(DATA_STEER, 0f);
        b.define(DATA_TURRET_YAW, 0f);
        b.define(DATA_TURRET_PITCH, 0f);
        b.define(DATA_HEAT, 0f);
    }

    public BotTeam getBotTeam() {
        return BotTeam.byId(this.entityData.get(DATA_TEAM));
    }

    public void setTeam(BotTeam t) {
        this.entityData.set(DATA_TEAM, (byte) t.ordinal());
    }

    public float getHeat() {
        return this.entityData.get(DATA_HEAT);
    }

    public boolean isOverheated() {
        return overheated || getHeat() >= 99f;
    }

    public float getTurretYaw() {
        return this.entityData.get(DATA_TURRET_YAW);
    }

    public float getTurretPitch() {
        return this.entityData.get(DATA_TURRET_PITCH);
    }

    public float getSteerVisual() {
        return this.entityData.get(DATA_STEER);
    }

    /** speed for HUD / wheels in blocks per tick (signed) */
    public double getDisplaySpeed() {
        if (this.isControlledByLocalInstance()) return speed;
        return this.entityData.get(DATA_SPEED);
    }

    // ------------------------------------------------------------------------------------ seats
    public int seatOf(Entity e) {
        for (int i = 0; i < 4; i++) if (this.entityData.get(DATA_SEATS.get(i)) == e.getId()) return i;
        return -1;
    }

    @Nullable
    public Entity seatEntity(int seat) {
        int id = this.entityData.get(DATA_SEATS.get(seat));
        if (id < 0) return null;
        for (Entity p : this.getPassengers()) if (p.getId() == id) return p;
        return null;
    }

    @Override
    public boolean seatFree(int seat) {
        return seatEntity(seat) == null;
    }

    public boolean isDriver(Entity e) {
        return seatOf(e) == SEAT_DRIVER;
    }

    public boolean isGunner(Entity e) {
        return seatOf(e) == SEAT_GUNNER;
    }

    @Override
    public boolean fitForBots() {
        return this.getHealth() > this.getMaxHealth() * 0.3f;
    }

    @Override
    public int seatForBot(BotEntity bot) {
        if (seatFree(SEAT_DRIVER)) return SEAT_DRIVER;
        if (seatFree(SEAT_GUNNER)) return SEAT_GUNNER;
        return -1;
    }

    @Override
    public void board(Entity e, int seat) {
        if (this.level().isClientSide || !seatFree(seat) || e.isPassenger()) return;
        pendingSeat = seat;
        e.startRiding(this, true);
        pendingSeat = -1;
        if (seat == SEAT_DRIVER && !engineStarted) {
            engineStarted = true;
            this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("jeep_start"), SoundSource.NEUTRAL, 1.2f, 1f);
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 4;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (this.level().isClientSide) return;
        int seat = pendingSeat >= 0 && seatFree(pendingSeat) ? pendingSeat : -1;
        if (seat < 0) {
            for (int i = 0; i < 4; i++) {
                if (this.entityData.get(DATA_SEATS.get(i)) < 0) {
                    seat = i;
                    break;
                }
            }
        }
        if (seat >= 0) this.entityData.set(DATA_SEATS.get(seat), passenger.getId());
        if (passenger instanceof Player && seat == SEAT_DRIVER) setTeam(BotTeam.GREEN);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (this.level().isClientSide) return;
        for (int i = 0; i < 4; i++) {
            if (this.entityData.get(DATA_SEATS.get(i)) == passenger.getId()) this.entityData.set(DATA_SEATS.get(i), -1);
        }
        if (seatOf(passenger) < 0 && passenger instanceof Player) gunnerFiring = false;
    }

    public void switchSeat(Player player) {
        int cur = seatOf(player);
        for (int k = 1; k <= 4; k++) {
            int s = (cur + k) % 4;
            if (seatFree(s)) {
                if (cur >= 0) this.entityData.set(DATA_SEATS.get(cur), -1);
                this.entityData.set(DATA_SEATS.get(s), player.getId());
                player.displayClientMessage(Component.translatable("gunsmod.seat." + s), true);
                gunnerFiring = false;
                return;
            }
        }
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity d = seatEntity(SEAT_DRIVER);
        return d instanceof LivingEntity le ? le : null;
    }

    public Vec3 seatWorld(int seat) {
        if (seat == SEAT_GUNNER) return turretPoint(getTurretYaw(), -GUNNER_BACK, GUNNER_Y);
        double[] s = SEATS[seat];
        return toWorld(s[0], s[1], s[2]);
    }

    /** turret yaw (relative to the hull) the gunner is aiming with; players drive it with their own view */
    public float turretYawFor(Entity gunner) {
        return gunner instanceof Player p ? Mth.wrapDegrees(p.getYRot() - this.getYRot()) : getTurretYaw();
    }

    /** world position of a point on the turret centre line, {@code ahead} model units in front of the ring centre */
    public Vec3 turretPoint(float turretYaw, double ahead, double y) {
        float a = turretYaw * Mth.DEG_TO_RAD;
        return toWorld(TURRET_X + Mth.sin(a) * ahead, y, TURRET_Z - Mth.cos(a) * ahead);
    }

    public Vec3 gunPivotWorld() {
        return turretPoint(getTurretYaw(), TURRET_Z - GUN_PIVOT_Z, GUN_PIVOT_Y);
    }

    /** model units -> world position using the current yaw */
    public Vec3 toWorld(double mx, double my, double mz) {
        double right = (mx - 8) * 0.1, up = my * 0.1, fwd = -(mz - 8) * 0.1;
        float yr = this.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yr), fz = Mth.cos(yr);
        double rx = -Mth.cos(yr), rz = -Mth.sin(yr);
        return new Vec3(getX() + rx * right + fx * fwd, getY() + up, getZ() + rz * right + fz * fwd);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        int seat = seatOf(passenger);
        if (seat < 0) seat = SEAT_REAR;
        Vec3 p = seat == SEAT_GUNNER ? turretPoint(turretYawFor(passenger), -GUNNER_BACK, GUNNER_Y) : seatWorld(seat);
        // players hang 0.6 below their seat point; soldiers get the same so both sit at the same height
        Vec3 att = passenger instanceof Player ? passenger.getVehicleAttachmentPoint(this) : new Vec3(0, 0.6, 0);
        callback.accept(passenger, p.x - att.x, p.y - att.y, p.z - att.z);
        if (passenger instanceof LivingEntity le && !(passenger instanceof Player)) {
            le.yBodyRot = this.getYRot();
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        int seat = seatOf(passenger);
        double side = seat == SEAT_DRIVER ? -14 : 30;
        double[][] tries = {{side, 0, seat == SEAT_REAR ? 12.6 : 4.6}, {8, 0, -20}, {8, 0, 36}, {-14 - side + 16, 0, 8}};
        for (double[] t : tries) {
            Vec3 w = toWorld(t[0], 0, t[2]);
            BlockPos bp = BlockPos.containing(w);
            for (int dy = 1; dy >= -1; dy--) {
                Vec3 c = new Vec3(w.x, bp.getY() + dy, w.z);
                if (this.level().noCollision(passenger, passenger.getBoundingBox().move(c.subtract(passenger.position())))
                        && !this.level().getBlockState(BlockPos.containing(c).below()).getCollisionShape(this.level(), BlockPos.containing(c).below()).isEmpty()) {
                    return c;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ------------------------------------------------------------------------------------ interaction
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.IRON_INGOT) && this.getHealth() < this.getMaxHealth()) {
            if (!this.level().isClientSide) {
                this.heal(20f);
                held.consume(1, player);
                this.level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6f, 1.4f);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (player.isPassenger()) return InteractionResult.PASS;
        if (!this.level().isClientSide) {
            int seat;
            if (player.isSecondaryUseActive()) seat = seatFree(SEAT_GUNNER) ? SEAT_GUNNER : -1;
            else seat = seatFree(SEAT_DRIVER) ? SEAT_DRIVER : seatFree(SEAT_GUNNER) ? SEAT_GUNNER : seatFree(SEAT_PASSENGER) ? SEAT_PASSENGER : seatFree(SEAT_REAR) ? SEAT_REAR : -1;
            if (seat < 0) {
                for (int i = 0; i < 4; i++) if (seatFree(i)) seat = i;
            }
            if (seat >= 0) {
                board(player, seat);
                player.displayClientMessage(Component.translatable("gunsmod.seat." + seat), true);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ------------------------------------------------------------------------------------ damage
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isInvulnerableTo(source)) return false;
        Entity att = source.getEntity();
        if (att != null && att.getVehicle() == this) return false;
        if (att instanceof Player p && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            this.spawnAtLocation(new ItemStack(tow ? Reg.vehicleItem(VehicleKind.TOW_HUMVEE, getBotTeam())
                    : getBotTeam() == BotTeam.RED ? Reg.RED_JEEP.get() : Reg.GREEN_JEEP.get()));
            VehicleSpawnerBlockEntity.vehicleGone(this.level(), spawnerPos, this.getUUID());
            this.discard();
            return true;
        }
        if (source.is(DamageTypeTags.IS_FALL)) return false;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) amount *= 1.5f;
        else if (source.is(DamageTypeTags.IS_FIRE)) amount *= 0.3f;
        else if (source.getDirectEntity() instanceof Player && !source.is(GunLogic.BULLET)) amount *= 0.4f; // fists and swords
        this.invulnerableTime = 0;
        return super.hurt(source, amount);
    }

    public void hurtByBullet(DamageSource src, float amount) {
        this.invulnerableTime = 0;
        super.hurt(src, amount);
        this.invulnerableTime = 0;
    }

    @Override
    public boolean causeFallDamage(float dist, float mul, DamageSource src) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide || this.isRemoved()) return;
        super.die(source);
        this.ejectPassengers();
        VehicleSpawnerBlockEntity.vehicleGone(this.level(), spawnerPos, this.getUUID());
        Blast.explode(this.level(), this, this.position().add(0, 1.0, 0), 3.2f, true, Level.ExplosionInteraction.MOB, Blast.FX_FIRE, 1.2f);
        this.discard();
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
        // the jeep is too heavy to be pushed around
    }

    // ------------------------------------------------------------------------------------ driving
    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        throttleIn = (float) Mth.clamp(player.zza / 0.98f, -1f, 1f);
        steerIn = (float) Mth.clamp(-player.xxa / 0.98f, -1f, 1f);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return new Vec3(0, 0, 1);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return 0.3f;
    }

    @Override
    public void travel(Vec3 input) {
        if (this.isControlledByLocalInstance()) {
            physics();
        }
        this.calculateEntityAnimation(false);
    }

    private void physics() {
        Entity driver = getControllingPassenger();
        float thr = driver != null ? throttleIn : 0f;
        float st = driver != null ? steerIn : 0f;
        boolean water = this.isInWater();
        double max = water ? 0.07 : MAX_SPEED;
        if (thr > 0.05f) {
            if (speed < -0.01) speed = Math.min(0, speed + 0.03);
            else {
                double k = speed / max;
                speed += 0.0085 * thr * (1.0 - 0.8 * k * k);
            }
        } else if (thr < -0.05f) {
            if (speed > 0.01) speed = Math.max(0, speed - 0.03);
            else speed = Math.max(-MAX_REVERSE, speed - 0.0055 * -thr);
        } else {
            speed *= 0.985;
            speed -= Math.signum(speed) * 0.0012;
            if (Math.abs(speed) < 0.002) speed = 0;
        }
        speed = Mth.clamp(speed, -MAX_REVERSE, max);
        steer += (st - steer) * 0.22f;
        if (this.onGround() || water) {
            double angle = steer * Math.toRadians(32) * (1.0 - 0.45 * Math.min(1, Math.abs(speed) / MAX_SPEED));
            double yawRate = speed * Math.tan(angle) / WHEELBASE;
            this.setYRot(this.getYRot() + (float) Math.toDegrees(yawRate));
        }
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        float yr = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
        double vy = this.getDeltaMovement().y;
        vy = this.onGround() ? -0.08 : (vy - 0.08) * 0.98;
        if (water) vy = Math.max(vy, -0.05);
        Vec3 motion = fwd.scale(speed).add(0, vy, 0);
        this.setDeltaMovement(motion);
        double before = Math.abs(speed);
        this.move(MoverType.SELF, motion);
        if (this.horizontalCollision && before > 0.05) {
            if (before > 0.38 && !this.level().isClientSide) this.hurt(this.damageSources().flyIntoWall(), (float) (before * 12));
            speed *= 0.25;
        }
        this.fallDistance = 0;
    }

    @Override
    public void tick() {
        prevWheelSpin = wheelSpin;
        prevTurretYaw = getTurretYaw();
        prevTurretPitch = getTurretPitch();
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
        float yr = this.getYRot() * Mth.DEG_TO_RAD;
        double signed = moved.x * -Mth.sin(yr) + moved.z * Mth.cos(yr) < 0 ? -horiz : horiz;
        if (!this.isControlledByLocalInstance()) {
            if (!this.level().isClientSide) {
                speed = signed;
                this.entityData.set(DATA_SPEED, (float) signed);
                this.entityData.set(DATA_STEER, steer);
            }
        } else if (!this.level().isClientSide) {
            this.entityData.set(DATA_SPEED, (float) speed);
            this.entityData.set(DATA_STEER, steer);
        }
        // passengers turn with the jeep
        float dy = Mth.wrapDegrees(this.getYRot() - lastYaw);
        if (Math.abs(dy) > 0.001f && Math.abs(dy) < 30f) {
            for (Entity p : this.getPassengers()) {
                if (seatOf(p) == SEAT_GUNNER) continue;
                p.setYRot(p.getYRot() + dy);
                p.setYHeadRot(p.getYHeadRot() + dy);
            }
        }
        lastYaw = this.getYRot();
        lastPos = this.position();
        wheelSpin += (float) (getDisplaySpeed() / 0.43);   // wheel radius 0.43 blocks
        if (!this.level().isClientSide) {
            turretTick();
            ramTick();
        } else {
            clientTick.accept(this);
        }
    }

    private void ramTick() {
        double v = Math.abs(speed);
        if (v < 0.2) return;
        float yr = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr)).scale(Math.signum(speed));
        AABB box = this.getBoundingBox().move(fwd.scale(1.2)).inflate(0.2, 0, 0.2);
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.getVehicle() != this && !(e instanceof JeepEntity))) {
            Entity driver = getControllingPassenger();
            if (driver != null && BotTeam.friendly(driver, e)) continue;
            e.hurt(GunLogic.source(this.level(), GunLogic.ROADKILL, this, driver != null ? driver : this), (float) (v * 30));
            e.push(fwd.x * v * 2.5, 0.35, fwd.z * v * 2.5);
        }
    }

    // ------------------------------------------------------------------------------------ bot driving
    private void botDrive(BotEntity driver) {
        LivingEntity enemy = driver.getTarget();
        Entity gunner = seatEntity(SEAT_GUNNER);
        if (enemy == null && gunner instanceof BotEntity gb) enemy = gb.getTarget();
        if (home == null) home = this.blockPosition();
        // a gunner on the turret: halt when the enemy is in sight so the shots land, otherwise patrol with stops
        Vec3 goal = patrol.goal(this, enemy, gunner != null, Vec3.atBottomCenterOf(home), 96, tow ? 120 : 55, tow ? 14 : 7);
        double stopDist = 0;
        if (goal == null) {
            throttleIn = GroundBotDriver.brake(speed);
            steerIn = 0;
            return;
        }
        Vec3 to = goal.subtract(this.position());
        double dist = Math.sqrt(to.x * to.x + to.z * to.z);
        float desired = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float diff = Mth.wrapDegrees(desired - this.getYRot());
        float thr = dist > stopDist ? 1f : 0f;
        if (Math.abs(diff) > 60) thr *= 0.55f;
        float st = Mth.clamp(diff / 25f, -1f, 1f);
        // obstacle probing
        double look = 3.5 + Math.abs(speed) * 14;
        double c = probe(0, look), l = probe(-30, look), r = probe(30, look);
        if (c < look) {
            thr = Math.min(thr, 0.5f);
            st = l > r ? -1f : 1f;
            if (c < 2.2) thr = -1f;
        }
        if (Math.abs(diff) > 120 && dist < 30 && reverseTicks == 0) {
            thr = -0.8f;
            st = -Math.signum(diff);
        }
        // stuck detection
        if (thr > 0.3f && Math.abs(speed) < 0.03) stuckTicks++;
        else stuckTicks = Math.max(0, stuckTicks - 1);
        if (stuckTicks > 35) {
            reverseTicks = 30;
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

    /** free distance in a direction relative to the heading */
    private double probe(float relDeg, double len) {
        float yr = (this.getYRot() + relDeg) * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
        double best = len;
        for (double h : new double[]{1.2, 2.0}) {
            Vec3 from = this.position().add(0, h, 0).add(dir.scale(2.2));
            Vec3 to = from.add(dir.scale(len));
            HitResult hr = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (hr.getType() != HitResult.Type.MISS) best = Math.min(best, hr.getLocation().distanceTo(from));
        }
        // cliff check
        Vec3 ahead = this.position().add(dir.scale(4.0));
        BlockPos bp = BlockPos.containing(ahead);
        boolean ground = false;
        for (int i = 0; i < 4 && !ground; i++) {
            BlockPos q = bp.below(i);
            ground = !this.level().getBlockState(q).getCollisionShape(this.level(), q).isEmpty() || !this.level().getFluidState(q).isEmpty();
        }
        if (!ground) best = Math.min(best, 3.0);
        return best;
    }

    // ------------------------------------------------------------------------------------ turret
    public void setGunnerFiring(boolean f) {
        gunnerFiring = f;
    }

    public Vec3 turretDir() {
        float yaw = this.getYRot() + getTurretYaw();
        return GunLogic.dir(getTurretPitch(), yaw);
    }

    public Vec3 muzzleWorld() {
        if (tow) return gunPivotWorld().add(0, 0.12, 0).add(turretDir().scale(1.2));
        return gunPivotWorld().add(0, 0.03, 0).add(turretDir().scale(1.55));
    }

    /** TOW reload progress for the HUD: 0 = ready, 1 = just fired */
    public float towReloadFraction() {
        return getHeat() / 100f;
    }

    @Override
    @Nullable
    public Vec3[] beam() {
        if (!tow || seatEntity(SEAT_GUNNER) == null || this.tickCount - lastTowShot > 260) return null;
        return new Vec3[]{gunPivotWorld().add(0, 0.12, 0), turretDir()};
    }

    /** TOW: the gunner fires and then keeps the cross hairs on the target until the missile arrives */
    private void towTick() {
        if (towReload > 0) towReload--;
        this.entityData.set(DATA_HEAT, towReload * 100f / TOW_RELOAD);
        Entity gunner = seatEntity(SEAT_GUNNER);
        if (gunner instanceof Player p) {
            this.entityData.set(DATA_TURRET_YAW, Mth.wrapDegrees(p.getYRot() - this.getYRot()));
            this.entityData.set(DATA_TURRET_PITCH, Mth.clamp(p.getXRot(), -35f, 20f));
            if (gunnerFiring && towReload == 0) fireTow(p);
        } else if (gunner instanceof BotEntity bot) {
            if (--towScan <= 0) {
                towScan = 10;
                LivingEntity t = TankEntity.scan(this, bot, 150, gunPivotWorld(), bot.getTarget());
                if (t != null) bot.setTarget(t);
            }
            LivingEntity t = bot.getTarget();
            if (t != null && t.isAlive() && t.distanceToSqr(this) < 160 * 160) {
                Vec3 pivot = gunPivotWorld();
                Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(pivot);
                float wantYaw = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - this.getYRot());
                float wantPitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
                float cy = getTurretYaw(), cp = getTurretPitch();
                this.entityData.set(DATA_TURRET_YAW, Mth.wrapDegrees(cy + Mth.clamp(Mth.wrapDegrees(wantYaw - cy), -4f, 4f)));
                this.entityData.set(DATA_TURRET_PITCH, Mth.clamp(cp + Mth.clamp(wantPitch - cp, -3f, 3f), -35f, 20f));
                boolean aligned = Math.abs(Mth.wrapDegrees(wantYaw - getTurretYaw())) < 1.5f && Math.abs(wantPitch - getTurretPitch()) < 1.5f;
                boolean los = this.level().clip(new ClipContext(muzzleWorld(), t.position().add(0, t.getBbHeight() * 0.5, 0), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
                double d = Math.sqrt(t.distanceToSqr(this));
                if (aligned && los && towReload == 0 && d > 12 && bot.isEnemy(t)) fireTow(bot);
                bot.setAiming(true);
            }
        } else {
            gunnerFiring = false;
        }
    }

    private void fireTow(LivingEntity gunner) {
        towReload = TOW_RELOAD;
        lastTowShot = this.tickCount;
        Vec3 at = muzzleWorld();
        GuidedMissileEntity m = new GuidedMissileEntity(this.level(), gunner, GuidedMissileEntity.TOW).power(62f, 45f, 3.0f).guider(this);
        m.setPos(at.x, at.y, at.z);
        m.setDeltaMovement(turretDir().scale(1.0));
        m.alignToMotion();
        this.level().addFreshEntity(m);
        this.level().playSound(null, at.x, at.y, at.z, Reg.sound("atgm_launch"), SoundSource.NEUTRAL, 6f, 0.9f + this.random.nextFloat() * 0.1f);
        if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 128, new Net.MuzzleFx(at, turretDir(), 0.7f, this.getId()));
            // back blast behind the launcher
            Vec3 back = at.subtract(turretDir().scale(2.6));
            PacketDistributor.sendToPlayersNear(sl, null, back.x, back.y, back.z, 96, new Net.MuzzleFx(back, turretDir().scale(-1), 0.6f, this.getId()));
            BotEntity.alertGunfire(sl, gunner, 96);
        }
    }

    private void turretTick() {
        if (tow) {
            towTick();
            return;
        }
        float heat = getHeat();
        if (turretCooldown > 0) turretCooldown--;
        Entity gunner = seatEntity(SEAT_GUNNER);
        boolean fire = false;
        if (gunner instanceof Player p) {
            float rel = Mth.wrapDegrees(p.getYRot() - this.getYRot());
            this.entityData.set(DATA_TURRET_YAW, rel);
            this.entityData.set(DATA_TURRET_PITCH, Mth.clamp(p.getXRot(), -35f, 20f));
            fire = gunnerFiring;
        } else if (gunner instanceof BotEntity bot) {
            LivingEntity t = bot.getTarget();
            if (t == null) {
                Entity d = getControllingPassenger();
                if (d instanceof BotEntity db) t = db.getTarget();
            }
            if (t != null && t.isAlive()) {
                Vec3 pivot = gunPivotWorld();
                Vec3 to = t.position().add(0, t.getBbHeight() * 0.6, 0).subtract(pivot);
                float wantYaw = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - this.getYRot());
                float wantPitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
                float cy = getTurretYaw(), cp = getTurretPitch();
                float dyaw = Mth.clamp(Mth.wrapDegrees(wantYaw - cy), -7f, 7f);
                float dp = Mth.clamp(wantPitch - cp, -5f, 5f);
                this.entityData.set(DATA_TURRET_YAW, Mth.wrapDegrees(cy + dyaw));
                this.entityData.set(DATA_TURRET_PITCH, Mth.clamp(cp + dp, -35f, 20f));
                boolean aligned = Math.abs(Mth.wrapDegrees(wantYaw - cy)) < 5f && Math.abs(wantPitch - cp) < 5f;
                boolean los = this.level().clip(new ClipContext(muzzleWorld(), t.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
                if (aligned && los && bot.isEnemy(t) && t.distanceToSqr(this) < 70 * 70) {
                    fire = (this.tickCount / 25) % 3 != 2; // bursts
                }
                bot.setAiming(true);
            }
        } else {
            gunnerFiring = false;
        }
        if (fire && !overheated && turretCooldown <= 0) {
            fireTurret(gunner instanceof LivingEntity le ? le : null);
            heat += 4.5f;
            turretCooldown = 3;
        }
        heat = Math.max(0f, heat - 1.1f);
        if (heat >= 100f && !overheated) {
            overheated = true;
            this.level().playSound(null, getX(), getY() + 2, getZ(), Reg.sound("turret_overheat"), SoundSource.NEUTRAL, 1f, 1f);
        }
        if (overheated && heat < 35f) overheated = false;
        this.entityData.set(DATA_HEAT, Math.min(100f, heat));
    }

    private void fireTurret(@Nullable LivingEntity gunner) {
        Vec3 muzzle = muzzleWorld();
        Vec3 d = turretDir();
        float yaw = this.getYRot() + getTurretYaw();
        float pitch = getTurretPitch();
        Entity shooterRef = gunner != null ? gunner : this;
        var r = this.random;
        Vec3 dir = GunLogic.dir(pitch + (float) r.nextGaussian() * 0.8f, yaw + (float) r.nextGaussian() * 0.8f);
        GunLogic.Trace tr = GunLogic.trace(this.level(), shooterRef, muzzle, dir, 140);
        if (tr.entity() != null && tr.entity() != this && gunner != null) {
            GunLogic.damage(gunner, tr.entity(), 9f, tr.headshot(), com.gunsmod.gun.GunType.M249);
        }
        List<Vec3> ends = new ArrayList<>();
        ends.add(tr.end().subtract(muzzle));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this, new Net.ShotFx(this.getId(), -1, muzzle, ends, new byte[]{tr.kind()}, false));
        if (gunner != null && this.level() instanceof net.minecraft.server.level.ServerLevel sl) BotEntity.alertGunfire(sl, gunner, 64);
    }

    public void honk() {
        this.level().playSound(null, getX(), getY(), getZ(), Reg.sound("jeep_horn"), SoundSource.NEUTRAL, 2.0f, 1f);
    }

    // ------------------------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Team", getBotTeam().ordinal());
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        if (spawnerPos != null) tag.put("Spawner", NbtUtils.writeBlockPos(spawnerPos));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTeam(BotTeam.byId(tag.getInt("Team")));
        home = NbtUtils.readBlockPos(tag, "Home").orElse(null);
        spawnerPos = NbtUtils.readBlockPos(tag, "Spawner").orElse(null);
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }
}
