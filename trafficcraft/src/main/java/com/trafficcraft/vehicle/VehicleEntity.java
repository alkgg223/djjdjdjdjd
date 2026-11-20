package com.trafficcraft.vehicle;

import com.trafficcraft.TCConfig;
import com.trafficcraft.TrafficCraft;
import com.trafficcraft.registry.TCRegistry;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A drivable vehicle. One entity type serves all models; the model id is synced data.
 * Player-driven vehicles are simulated on the driver's client (like boats) and the position is sent with vanilla
 * vehicle-move packets; speed/steering/rpm/light state is sent with {@link com.trafficcraft.net.VehicleStatePayload}.
 * Driverless and AI vehicles are simulated on the server.
 */
public class VehicleEntity extends Entity {
    // ---------------------------------------------------------------- flags
    public static final int F_LOW = 1, F_HIGH = 1 << 1, F_IND_L = 1 << 2, F_IND_R = 1 << 3, F_HAZARD = 1 << 4, F_BRAKE = 1 << 5,
            F_REVERSE = 1 << 6, F_INTERIOR = 1 << 7, F_ENGINE = 1 << 8, F_BEACONS = 1 << 9, F_SIREN = 1 << 10, F_HANDBRAKE = 1 << 11,
            F_STARTING = 1 << 12, F_MANUAL = 1 << 13, F_HORN = 1 << 14;
    public static final int CLIENT_BITS = F_BRAKE | F_REVERSE | F_HANDBRAKE;
    public static final int MODE_FREE = 0, MODE_PLAYER = 1, MODE_AI = 2, MODE_PARKED = 3;

    // ---------------------------------------------------------------- actions (from the control payload)
    public static final int A_ENGINE = 0, A_LIGHTS = 1, A_HIGH = 2, A_IND_L = 3, A_IND_R = 4, A_HAZARD = 5, A_HORN = 6, A_INTERIOR = 7,
            A_SIREN = 8, A_GEARMODE = 9, A_GEAR_UP = 10, A_GEAR_DOWN = 11, A_BEACONS = 12;

    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR2 = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> STEER = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RPM = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LOAD = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FUEL = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> GEAR = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DOOR_L = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DOOR_R = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> PLATE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CONFIG = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DRIFT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    /** passenger entity id -> seat index */
    private static final EntityDataAccessor<net.minecraft.nbt.CompoundTag> SEATS = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private static final ResourceLocation SEATED_SCALE_ID = TrafficCraft.id("seated_scale");

    /** Supplies driver input on the client (installed by the client setup; returns NONE on a dedicated server). */
    public static Function<VehicleEntity, VehicleInput> CLIENT_INPUT = v -> VehicleInput.NONE;
    /** Called on the driving client after each physics step to send the state packet (installed by client setup). */
    public static java.util.function.Consumer<VehicleEntity> CLIENT_SEND = v -> {};
    public static java.util.function.BiConsumer<VehicleEntity, String> CLIENT_EVENT = (v, e) -> {};
    public static java.util.function.Consumer<VehicleEntity> OPEN_TUNING = v -> {};

    private VehicleSpec spec;
    /** Server-side AI driver (MODE_AI vehicles). */
    public com.trafficcraft.ai.TrafficDriver driver;

    // ---------------------------------------------------------------- physics state (authoritative where simulated)
    public float speed;           // m/s along heading (negative = reverse)
    public float steer;           // front wheel angle, degrees (positive = left)
    public float rpm = 800;
    public float throttle;        // smoothed 0..1
    public int gear = 1;          // -1 R, 0 N, 1..n
    public float bodyRoll, bodyPitch, bodyRollO, bodyPitchO;
    /** motorcycles: lean angle (degrees, + = right), rev blip level 0..1, last backfire (client tick) */
    public float lean, leanO, revLevel;
    float revTarget;
    int revHold, backfireCooldown;
    float rpmPrevS;
    public int backfireTick = -100, backfireKind;
    private float deltaYaw;
    private float vy;
    private int lastAutoGear = 1;

    // client visual state
    public float wheelSpin, wheelSpinO, steerVis, steerVisO;
    public int blinkTick;

    // lerp (remote clients)
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot;

    // server state
    private int startTimer;
    private int hornCooldown;
    private Vec3 lastFuelPos;
    private float indPeakSteer;
    public int aiBlockedTicks;

    public VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    // ================================================================= data
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        String def = VehicleSpecs.ids().isEmpty() ? "sedan" : VehicleSpecs.ids().get(0);
        b.define(MODEL, def);
        b.define(COLOR, 0xB0121B);
        b.define(COLOR2, 0xF2F2F2);
        b.define(FLAGS, 0);
        b.define(STEER, 0f);
        b.define(SPEED, 0f);
        b.define(RPM, 0f);
        b.define(LOAD, 0f);
        b.define(FUEL, 40f);
        b.define(GEAR, 1);
        b.define(DOOR_L, -1000);
        b.define(DOOR_R, -1000);
        b.define(PLATE, "TC 0000");
        b.define(CONFIG, "");
        b.define(DRIFT, 0f);
        b.define(MODE, MODE_FREE);
        b.define(SEATS, new net.minecraft.nbt.CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (MODEL.equals(key)) {
            spec = null;
            refreshDimensions();
        }
    }

    public VehicleSpec spec() {
        if (spec == null) spec = VehicleSpecs.get(entityData.get(MODEL));
        return spec;
    }

    public String getModel() { return entityData.get(MODEL); }
    public void setModel(String id) {
        entityData.set(MODEL, id);
        spec = null;
        VehicleSpec s = spec();
        entityData.set(COLOR2, s.paint2);
        refreshDimensions();
    }
    public int getColor() { return entityData.get(COLOR); }
    public void setColor(int c) { entityData.set(COLOR, c & 0xFFFFFF); }
    public int getColor2() { return entityData.get(COLOR2); }
    public int flags() { return entityData.get(FLAGS); }
    public boolean has(int f) { return (flags() & f) != 0; }
    public void setFlag(int f, boolean on) { entityData.set(FLAGS, on ? flags() | f : flags() & ~f); }
    public float getFuel() { return entityData.get(FUEL); }
    public void setFuel(float f) { entityData.set(FUEL, Mth.clamp(f, 0, spec().fuelCapacity)); }
    public String getPlate() { return entityData.get(PLATE); }

    // ------------------------------------------------------------------ customisation ("fb=1;rb=0;wh=3")
    private String cfgSrc = null;
    private final java.util.Map<String, Integer> cfgMap = new java.util.HashMap<>();
    /** client-side animation of pop-up headlamps (0 closed .. 1 open) */
    public float popupVis;

    public String getConfig() { return entityData.get(CONFIG); }
    /** visual drift (slip) angle in degrees, + = nose to the left */
    public float getDrift() { return entityData.get(DRIFT); }
    public void setDrift(float a) { if (Math.abs(a - entityData.get(DRIFT)) > 0.05f || a == 0) entityData.set(DRIFT, Math.abs(a) < 0.05f ? 0f : a); }
    public float driftVis, driftVisO, proBackfire;
    /** client-side tyre smoke puffs: x y z vx vy vz age life size [grey 0..1] */
    public final java.util.ArrayList<float[]> smoke = new java.util.ArrayList<>();
    /** client-side backfire events: start tick, kind (0 pop, 1 crackle, 2 bang), seed, n sub-pops, sub-pop times... */
    public final java.util.ArrayList<float[]> bfx = new java.util.ArrayList<>();
    /** client-side exhaust sparks: x y z vx vy vz age life */
    public final java.util.ArrayList<float[]> sparks = new java.util.ArrayList<>();

    private void backfireFx(int kind) {
        float seed = random.nextFloat();
        int n = kind == 1 ? 7 + random.nextInt(7) : 1;
        float[] ev = new float[4 + n];
        ev[0] = tickCount;
        ev[1] = kind;
        ev[2] = seed;
        ev[3] = n;
        float t = 0;
        for (int i = 0; i < n; i++) {
            ev[4 + i] = t;
            t += 0.6f + random.nextFloat() * 2.4f;          // crackle: irregular pops over about a second
        }
        if (bfx.size() < 6) bfx.add(ev);
    }

    private void sparkBurst(int count, float speed) {
        VehicleSpec s = spec();
        for (float[] e : s.exhaustsFor(cfg("ex"))) {
            Vec3 p = position().add(toWorldOffset(e[0], e[1], e[2], getYRot()));
            Vec3 d = toWorldOffset(e[3], e[4], e[5], getYRot()).normalize();
            Vec3 vel = getDeltaMovement();
            for (int i = 0; i < count && sparks.size() < 160; i++) {
                double sp = speed * (0.4 + random.nextFloat() * 0.9);
                double jx = (random.nextFloat() - 0.5) * 0.35, jy = random.nextFloat() * 0.2, jz = (random.nextFloat() - 0.5) * 0.35;
                sparks.add(new float[]{(float) p.x, (float) p.y, (float) p.z, (float) (vel.x + (d.x + jx) * sp), (float) (vel.y + (d.y + jy) * sp),
                        (float) (vel.z + (d.z + jz) * sp), 0, 3 + random.nextInt(6)});
            }
        }
    }

    private void exhaustPuff(float size, float grey) {
        VehicleSpec s = spec();
        for (float[] e : s.exhaustsFor(cfg("ex"))) {
            Vec3 p = position().add(toWorldOffset(e[0], e[1], e[2] - 0.15f, getYRot()));
            Vec3 d = toWorldOffset(e[3], e[4], e[5], getYRot()).normalize().scale(0.05);
            if (smoke.size() < 150) smoke.add(new float[]{(float) p.x, (float) p.y, (float) p.z, (float) d.x, 0.01f, (float) d.z, 0, 30 + random.nextInt(20), size, grey});
        }
    }

    private void tickBackfireFx() {
        for (int i = bfx.size() - 1; i >= 0; i--) {
            float[] ev = bfx.get(i);
            int age = tickCount - (int) ev[0] - 1;          // the event arrives just before this entity's tick
            int kind = (int) ev[1];
            if (kind == 1) {
                for (int k = 0; k < (int) ev[3]; k++) if ((int) ev[4 + k] == age) {
                    CLIENT_EVENT.accept(this, "bf_tick");        // each flicker of flame has its own pop, in sync
                    if (random.nextFloat() < 0.5f) sparkBurst(1, 0.2f);
                }
            }
            if (age == 0) sparkBurst(kind == 2 ? 4 : kind == 0 ? 2 : 0, kind == 2 ? 0.32f : 0.24f);
            if (age == (kind == 2 ? 5 : 3)) exhaustPuff(kind == 2 ? 0.55f : 0.3f, kind == 2 ? 0.25f : 0.4f);
            if (age > 32) bfx.remove(i);
        }
        for (int i = sparks.size() - 1; i >= 0; i--) {
            float[] p = sparks.get(i);
            p[0] += p[3];
            p[1] += p[4];
            p[2] += p[5];
            p[3] *= 0.9f;
            p[5] *= 0.9f;
            p[4] = p[4] * 0.9f - 0.025f;
            if (++p[6] > p[7]) sparks.remove(i);
        }
    }
    int skidTick;
    public void setConfig(String c) { entityData.set(CONFIG, c == null ? "" : c); }

    public int cfg(String slot) {
        String src = getConfig();
        if (!src.equals(cfgSrc)) {
            cfgSrc = src;
            cfgMap.clear();
            for (String kv : src.split(";")) {
                int i = kv.indexOf('=');
                if (i > 0) try { cfgMap.put(kv.substring(0, i), Integer.parseInt(kv.substring(i + 1))); } catch (NumberFormatException ignored) {}
            }
        }
        return cfgMap.getOrDefault(slot, 0);
    }

    /** Coherent random build for spawned traffic: most cars stay factory, the rest get a themed set of parts. */
    public void randomizeConfig(net.minecraft.util.RandomSource r) {
        VehicleSpec s = spec();
        if (s.catalog.isEmpty() || r.nextFloat() < 0.55f) {
            setConfig("");
            return;
        }
        boolean track = r.nextFloat() < 0.3f;
        StringBuilder b = new StringBuilder();
        for (var en : s.catalog.entrySet()) {
            int n = en.getValue();
            if (n <= 1 || en.getKey().equals("dc")) continue;                          // festive decor only by the player
            if ((en.getKey().equals("kt") || en.getKey().equals("lv")) && r.nextFloat() > 0.12f) continue;   // body kits and liveries are rare in traffic
            int v;
            if (r.nextFloat() < (track ? 0.25f : 0.45f)) v = 0;
            else if (track) v = Math.max(1, n - 1 - r.nextInt(Math.min(2, n - 1)));
            else v = 1 + r.nextInt(Math.max(1, Math.min(n - 1, 3)));
            if (v > 0) b.append(en.getKey()).append('=').append(Math.min(v, n - 1)).append(';');
        }
        setConfig(b.toString());
    }
    public void setPlate(String p) { entityData.set(PLATE, p); }
    public int getMode() { return entityData.get(MODE); }
    public void setMode(int m) { entityData.set(MODE, m); }
    public int doorTick(boolean left) { return entityData.get(left ? DOOR_L : DOOR_R); }

    /** Speed used for visuals/sound: local physics for the driver, synced value elsewhere. */
    public float visSpeed() { return isLocallySimulated() ? speed : entityData.get(SPEED); }
    public float visSteer() { return isLocallySimulated() ? steer : entityData.get(STEER); }
    public float visRpm() { return isLocallySimulated() ? rpm : entityData.get(RPM); }
    public float visLoad() { return isLocallySimulated() ? throttle : entityData.get(LOAD); }
    public int visGear() { return isLocallySimulated() ? gear : entityData.get(GEAR); }

    public boolean isLocallySimulated() {
        return isControlledByLocalInstance();
    }

    public static String randomPlate(RandomSource r) {
        String letters = "ABCDEFGHJKLMNPRSTUVWXYZ";
        return "" + letters.charAt(r.nextInt(letters.length())) + letters.charAt(r.nextInt(letters.length())) + " "
                + (1000 + r.nextInt(9000));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.contains("Model")) entityData.set(MODEL, VehicleSpecs.exists(t.getString("Model")) ? t.getString("Model") : getModel());
        spec = null;
        if (t.contains("Color")) setColor(t.getInt("Color"));
        if (t.contains("Color2")) entityData.set(COLOR2, t.getInt("Color2"));
        if (t.contains("Fuel")) entityData.set(FUEL, t.getFloat("Fuel"));
        entityData.set(FLAGS, t.getInt("Flags") & (F_LOW | F_HIGH | F_INTERIOR | F_HAZARD | F_MANUAL));
        if (t.contains("Plate")) setPlate(t.getString("Plate"));
        if (t.contains("Config")) setConfig(t.getString("Config"));
        setMode(t.getInt("Mode") == MODE_PARKED ? MODE_PARKED : MODE_FREE);
        HeadLighting.load(this, t);
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putString("Model", getModel());
        t.putInt("Color", getColor());
        t.putInt("Color2", getColor2());
        t.putFloat("Fuel", getFuel());
        t.putInt("Flags", flags());
        t.putString("Plate", getPlate());
        t.putString("Config", getConfig());
        t.putInt("Mode", getMode());
        HeadLighting.save(this, t);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        VehicleSpec s = spec();
        return EntityDimensions.fixed(Math.min(s.width * 0.96f, 2.6f), Math.min(s.height * 0.92f, 3.4f));
    }

    // ================================================================= entity behaviour
    @Override
    public boolean isPickable() { return !isRemoved(); }
    @Override
    public boolean canBeCollidedWith() { return true; }
    @Override
    public boolean isPushable() { return false; }
    @Override
    public float maxUpStep() { return (float) (double) TCConfig.STEP_HEIGHT.get(); }
    @Override
    public boolean shouldRiderSit() { return true; }
    @Override
    public boolean canCollideWith(Entity e) { return e instanceof VehicleEntity ? false : super.canCollideWith(e); }

    @Override
    public ItemStack getPickResult() {
        var item = TCRegistry.VEHICLE_ITEMS.get(getModel());
        return item != null ? new ItemStack(item.get()) : ItemStack.EMPTY;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (getMode() == MODE_AI) return null;
        for (Entity e : getPassengers()) if (e instanceof Player p && seatOf(p) == 0) return p;
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return freeSeat(e) >= 0;
    }

    /** True when an AI chauffeur sits in seat 0 (players ride in the other seats). */
    public boolean hasAiDriver() {
        return getMode() == MODE_AI;
    }

    /** Seat used by a passenger (synced map, falling back to boarding order). */
    public int seatOf(Entity p) {
        net.minecraft.nbt.CompoundTag m = entityData.get(SEATS);
        String k = Integer.toString(p.getId());
        if (m.contains(k)) return Math.min(m.getInt(k), spec().seats.size() - 1);
        int idx = Math.max(0, getPassengers().indexOf(p)) + (hasAiDriver() ? 1 : 0);
        return Math.min(idx, spec().seats.size() - 1);
    }

    /** Free seat for a boarding entity: the driver seat first (unless an AI drives), otherwise the free seat nearest to it. */
    int freeSeat(Entity e) {
        int n = spec().seats.size();
        boolean[] used = new boolean[n];
        if (hasAiDriver()) used[0] = true;
        for (Entity p : getPassengers()) if (p != e) used[seatOf(p)] = true;
        if (!used[0]) return 0;
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (used[i]) continue;
            Vec3 sp = spec().seats.get(i);
            Vec3 w = position().add(toWorldOffset(sp.x, sp.y, sp.z, getYRot()));
            double d = w.distanceToSqr(e.position());
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float partial) {
        Vec3 s = spec().seats.get(seatOf(passenger));
        return toWorldOffset(s.x, s.y, s.z, getYRot());
    }

    /** Model space (+X left, +Z forward) offset rotated into world space for the given yaw. */
    public static Vec3 toWorldOffset(double x, double y, double z, float yawDeg) {
        double r = Math.toRadians(yawDeg);
        double c = Math.cos(r), s = Math.sin(r);
        return new Vec3(x * c - z * s, y, x * s + z * c);
    }

    public Vec3 forward() {
        double r = Math.toRadians(getYRot());
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction fn) {
        super.positionRider(passenger, fn);
        passenger.setYRot(passenger.getYRot() + deltaYaw);
        passenger.setYHeadRot(passenger.getYHeadRot() + deltaYaw);
        clampRotation(passenger);
    }

    protected void clampRotation(Entity p) {
        p.setYBodyRot(getYRot());
        float f = Mth.wrapDegrees(p.getYRot() - getYRot());
        float f1 = Mth.clamp(f, -130.0F, 130.0F);
        p.yRotO += f1 - f;
        p.setYRot(p.getYRot() + f1 - f);
        p.setYHeadRot(p.getYRot());
    }

    @Override
    public void onPassengerTurned(Entity p) {
        clampRotation(p);
    }

    @Override
    protected void addPassenger(Entity p) {
        int seat = freeSeat(p);
        super.addPassenger(p);
        if (!level().isClientSide) {
            net.minecraft.nbt.CompoundTag m = entityData.get(SEATS).copy();
            m.putInt(Integer.toString(p.getId()), Math.max(0, seat));
            entityData.set(SEATS, m);
        }
        int idx = seatOf(p);
        boolean left = spec().seats.get(Math.min(Math.max(idx, 0), spec().seats.size() - 1)).x > 0;
        p.setYRot(getYRot());
        p.yRotO = getYRot();
        p.setYHeadRot(getYRot());
        if (p instanceof LivingEntity le) {
            le.setYBodyRot(getYRot());
            var inst = le.getAttribute(Attributes.SCALE);
            if (inst != null) inst.addOrUpdateTransientModifier(new AttributeModifier(SEATED_SCALE_ID, (spec().bike ? spec().riderScale : TCConfig.SEATED_SCALE.get()) - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (!level().isClientSide) {
            entityData.set(left ? DOOR_L : DOOR_R, tickCount);
            if (!spec().bike) level().playSound(null, getX(), getY(), getZ(), TCRegistry.snd("misc.door_open"), SoundSource.NEUTRAL, 0.8f, 1.0f);
            if (p instanceof Player && idx == 0 && getMode() != MODE_AI) setMode(MODE_PLAYER);
            if (p instanceof Player pl && getMode() == MODE_AI) pl.displayClientMessage(Component.translatable("msg.trafficcraft.passenger"), true);
        }
    }

    @Override
    protected void removePassenger(Entity p) {
        int idx = seatOf(p);
        super.removePassenger(p);
        if (!level().isClientSide) {
            net.minecraft.nbt.CompoundTag m = entityData.get(SEATS).copy();
            m.remove(Integer.toString(p.getId()));
            entityData.set(SEATS, m);
        }
        if (p instanceof LivingEntity le) {
            var inst = le.getAttribute(Attributes.SCALE);
            if (inst != null) inst.removeModifier(SEATED_SCALE_ID);
        }
        if (!level().isClientSide) {
            boolean left = spec().seats.get(Math.min(Math.max(idx, 0), spec().seats.size() - 1)).x > 0;
            entityData.set(left ? DOOR_L : DOOR_R, tickCount);
            if (!spec().bike) level().playSound(null, getX(), getY(), getZ(), TCRegistry.snd("misc.door_close"), SoundSource.NEUTRAL, 0.9f, 1.0f);
            if (getPassengers().isEmpty() && getMode() == MODE_PLAYER) {
                setMode(MODE_FREE);
                setFlag(F_BRAKE | F_REVERSE, false);
                entityData.set(SPEED, 0f);
            }
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 seat = spec().seats.get(seatOf(passenger));
        for (int attempt = 0; attempt < 2; attempt++) {
            double side = (seat.x > 0) == (attempt == 0) ? 1 : -1;
            Vec3 off = toWorldOffset(side * (spec().width / 2 + 0.55), 0, seat.z, getYRot());
            BlockPos bp = BlockPos.containing(getX() + off.x, getY() + 0.5, getZ() + off.z);
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos p = bp.above(dy);
                double floor = level().getBlockFloorHeight(p.below());
                Vec3 target = new Vec3(getX() + off.x, p.getY() - 1 + (DismountHelper.isBlockFloorValid(floor) ? floor : 1.0), getZ() + off.z);
                if (DismountHelper.canDismountTo(level(), target, passenger, Pose.STANDING)) return target;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof TuningToolItem) {
            if (level().isClientSide && !spec().catalog.isEmpty()) OPEN_TUNING.accept(this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.getItem() instanceof FuelCanItem) {
            if (!level().isClientSide) {
                setFuel(getFuel() + FuelCanItem.LITRES);
                level().playSound(null, getX(), getY(), getZ(), TCRegistry.snd("misc.fuel_pump"), SoundSource.NEUTRAL, 0.8f, 1.1f);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.displayClientMessage(Component.translatable("msg.trafficcraft.refueled", Math.round(getFuel()),
                        Math.round(spec().fuelCapacity)), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.getItem() instanceof com.trafficcraft.station.FuelNozzleItem) {
            if (!level().isClientSide) com.trafficcraft.station.FuelStations.startRefuel(player, this, stack);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.is(TCRegistry.INSPECTION_STAMP.get())) {
            if (!level().isClientSide) com.trafficcraft.npc.Workplaces.stamp(player, this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.getItem() instanceof DyeItem dye) {
            if (!level().isClientSide) {
                int c = dye.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF;
                setColor(c);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive()) {
            if (stack.isEmpty() && getPassengers().isEmpty() && !level().isClientSide && (player.getAbilities().instabuild)) {
                // creative: pick the vehicle up
                discard();
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        if (getMode() == MODE_AI) {
            if (!level().isClientSide) {
                if (driver != null && driver.state == com.trafficcraft.ai.TrafficDriver.State.PARKED && getPassengers().isEmpty() && level() instanceof ServerLevel sl) {
                    com.trafficcraft.ai.TrafficManager.get(sl).parking.releaseVehicle(getId());
                    driver = null;
                    setMode(MODE_FREE);
                    return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
                }
                // ride along: the AI keeps driving, the player takes a free passenger seat
                if (canAddPassenger(player)) return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
                player.displayClientMessage(Component.translatable("msg.trafficcraft.occupied"), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (!canAddPassenger(player)) return InteractionResult.PASS;
        if (!level().isClientSide) {
            if (getMode() == MODE_PARKED) setMode(MODE_FREE);
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || isRemoved()) return true;
        if (src.getEntity() instanceof Player p && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            ejectPassengers();
            discard();
            return true;
        }
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) HeadLighting.clear(this);
        ejectPassengers();
        super.remove(reason);
    }

    // ================================================================= lerp for remote clients
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = 3;
    }

    @Override
    public double lerpTargetX() { return lerpSteps > 0 ? lerpX : getX(); }
    @Override
    public double lerpTargetY() { return lerpSteps > 0 ? lerpY : getY(); }
    @Override
    public double lerpTargetZ() { return lerpSteps > 0 ? lerpZ : getZ(); }
    @Override
    public float lerpTargetYRot() { return lerpSteps > 0 ? (float) lerpYRot : getYRot(); }

    private void tickLerp() {
        if (isLocallySimulated()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, getXRot());
            lerpSteps--;
        }
    }

    // ================================================================= tick
    @Override
    public void tick() {
        float yaw0 = getYRot();
        bodyRollO = bodyRoll;
        leanO = lean;
        bodyPitchO = bodyPitch;
        wheelSpinO = wheelSpin;
        steerVisO = steerVis;
        super.tick();
        tickLerp();
        fallDistance = 0;
        if (isLocallySimulated()) {
            LivingEntity driver = getControllingPassenger();
            VehicleInput in = driver instanceof Player && level().isClientSide ? CLIENT_INPUT.apply(this) : aiInput();
            VehiclePhysics.step(this, in);
            if (level().isClientSide) CLIENT_SEND.accept(this);
            else publishState();
        } else if (!level().isClientSide && getControllingPassenger() == null) {
            VehiclePhysics.step(this, aiInput());
            publishState();
        }
        deltaYaw = Mth.wrapDegrees(getYRot() - yaw0);
        if (!level().isClientSide) {
            serverTick();
            HeadLighting.tick(this);
        }
        if (level().isClientSide) clientVisuals();
    }

    /** AI / driverless input; overridden by the traffic AI. */
    protected VehicleInput aiInput() {
        if (driver != null && getMode() == MODE_AI) return com.trafficcraft.ai.TrafficManager.input(this);
        return VehicleInput.PARK;
    }

    @Override
    public boolean shouldBeSaved() {
        return getMode() != MODE_AI && super.shouldBeSaved();
    }

    private void publishState() {
        entityData.set(SPEED, speed);
        entityData.set(STEER, steer);
        entityData.set(RPM, has(F_ENGINE) ? rpm : 0f);
        entityData.set(LOAD, Math.max(throttle, revLevel));
        entityData.set(GEAR, gear);
    }

    /** Throttle blip with the clutch in (revving at a standstill or while rolling). */
    public void revBlip(float strength, int hold) {
        if (!has(F_ENGINE)) return;
        revTarget = Math.max(revTarget, Mth.clamp(strength, 0, 1));
        revHold = Math.max(revHold, hold);
    }

    /** Rev envelope: fast rise, short hold, slower fall (runs where the vehicle is simulated). */
    public void tickRev() {
        if (revHold > 0) {
            revHold--;
            revLevel += (revTarget - revLevel) * 0.45f;
        } else {
            revTarget = 0;
            revLevel = Math.max(0, revLevel - 0.09f);
        }
    }

    /** Server: exhaust pops when the revs fall fast from high rpm with the throttle closed (lift-off, upshift, end of a rev blip). */
    void backfireCheck() {
        VehicleSpec s = spec();
        float rpm = visRpm();
        if (backfireCooldown > 0) backfireCooldown--;
        float bfc = Math.max(s.backfire, proBackfire);
        if (bfc > 0 && has(F_ENGINE) && backfireCooldown == 0) {
            float red = s.redRpm;
            float load = entityData.get(LOAD);
            boolean falling = rpmPrevS > red * 0.6f && rpm < rpmPrevS - red * 0.03f;
            if (falling && (load < 0.3f || random.nextFloat() < 0.3f) && random.nextFloat() < bfc * 0.55f) {
                float x = random.nextFloat();
                int kind = rpmPrevS > red * 0.85f && x < 0.22f ? 2 : x < 0.6f ? 0 : 1;
                level().broadcastEntityEvent(this, (byte) (60 + kind));
                com.trafficcraft.ai.TrafficStats.inc("backfires");
                backfireCooldown = kind == 1 ? 18 : 7;
            }
        }
        rpmPrevS = rpm;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= 60 && id <= 62) {
            backfireTick = tickCount;
            backfireKind = id - 60;
            if (level().isClientSide) backfireFx(backfireKind);
            CLIENT_EVENT.accept(this, "backfire_" + backfireKind);
            return;
        }
        super.handleEntityEvent(id);
    }

    /** Applies the driver's state packet on the server. */
    public void applyClientState(float speed, float steer, float rpm, float load, int gear, int bits) {
        entityData.set(SPEED, speed);
        entityData.set(STEER, steer);
        entityData.set(RPM, rpm);
        entityData.set(LOAD, load);
        entityData.set(GEAR, gear);
        int f = flags() & ~CLIENT_BITS | (bits & CLIENT_BITS);
        if (f != flags()) entityData.set(FLAGS, f);
        if (load > 0.05f && !has(F_ENGINE) && !has(F_STARTING)) startEngine();
    }

    public void startEngine() {
        if (has(F_ENGINE) || has(F_STARTING)) return;
        if (TCConfig.REQUIRE_FUEL.get() && getFuel() <= 0.01f && !spec().electric) return;
        setFlag(F_STARTING, true);
        startTimer = spec().diesel ? 50 : (spec().cylinders >= 8 ? 36 : 40);
        level().playSound(null, getX(), getY(), getZ(), TCRegistry.sound("engine." + getModel() + ".start", () -> "misc.gear_shift"),
                SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    public void stopEngine() {
        setFlag(F_ENGINE | F_STARTING, false);
    }

    private void serverTick() {
        backfireCheck();
        if (startTimer > 0 && --startTimer == 0) {
            setFlag(F_STARTING, false);
            setFlag(F_ENGINE, true);
        }
        if (hornCooldown > 0) hornCooldown--;
        if (has(F_HORN) && hornCooldown == 0) setFlag(F_HORN, false);
        float v = entityData.get(SPEED);
        // fuel: distance-based, scaled by engine load
        if (has(F_ENGINE)) {
            Vec3 p = position();
            if (lastFuelPos != null) {
                double d = Math.min(5, p.distanceTo(lastFuelPos));
                float load = entityData.get(LOAD);
                float perM = 0.00009f * (spec().mass / 1500f) * (float) (double) TCConfig.FUEL_CONSUMPTION.get();
                float use = (float) (d * perM * (0.55f + load)) + 0.00003f * (float) (double) TCConfig.FUEL_CONSUMPTION.get();
                setFuel(getFuel() - use);
                if (getFuel() <= 0 && TCConfig.REQUIRE_FUEL.get() && !spec().electric) stopEngine();
            }
            lastFuelPos = p;
        } else lastFuelPos = null;
        // indicators cancel themselves once the steering returns after a turn
        float st = entityData.get(STEER);
        if (has(F_IND_L) || has(F_IND_R)) {
            float signed = has(F_IND_L) ? st : -st;
            indPeakSteer = Math.max(indPeakSteer, signed);
            if (indPeakSteer > 14 && signed < 3) {
                setFlag(F_IND_L | F_IND_R, false);
                indPeakSteer = 0;
            }
        } else indPeakSteer = 0;
        // knock entities out of the way
        if (Math.abs(v) > 2.5f && TCConfig.VEHICLE_DAMAGE_ENTITIES.get()) hitEntities(v);
        if (tickCount % 5 == 0 && TCConfig.DYNAMIC_HEADLIGHTS.get() && getMode() == MODE_PLAYER) HeadlightLights.update(this);
    }

    private void hitEntities(float v) {
        Vec3 f = forward().scale(Math.signum(v) * (spec().length / 2 + 0.3));
        AABB box = getBoundingBox().move(f.x, 0, f.z).inflate(0.2, 0, 0.2);
        List<LivingEntity> list = level().getEntitiesOfClass(LivingEntity.class, box, e -> !e.isPassenger() && e.isAlive() && e.getVehicle() != this);
        for (LivingEntity e : list) {
            Vec3 push = forward().scale(Math.signum(v) * Math.min(2.0, Math.abs(v) * 0.08)).add(0, 0.3, 0);
            e.push(push.x, push.y, push.z);
            e.hurtMarked = true;
            if (Math.abs(v) > 5f) {
                Entity drv = getControllingPassenger();
                DamageSource ds = drv != null ? damageSources().mobAttack((LivingEntity) drv) : damageSources().flyIntoWall();
                e.hurt(ds, Math.min(20f, Math.abs(v) * 0.6f));
            }
        }
    }

    public void handleAction(Player player, int action) {
        switch (action) {
            case A_ENGINE -> {
                if (has(F_ENGINE) || has(F_STARTING)) stopEngine();
                else if (getFuel() <= 0.01f && TCConfig.REQUIRE_FUEL.get() && !spec().electric)
                    player.displayClientMessage(Component.translatable("msg.trafficcraft.no_fuel"), true);
                else startEngine();
            }
            case A_LIGHTS -> {
                boolean low = has(F_LOW);
                setFlag(F_LOW, !low);
                if (low) setFlag(F_HIGH, false);
            }
            case A_HIGH -> {
                setFlag(F_HIGH, !has(F_HIGH));
                if (has(F_HIGH)) setFlag(F_LOW, true);
            }
            case A_IND_L -> {
                boolean on = has(F_IND_L);
                setFlag(F_IND_L | F_IND_R, false);
                setFlag(F_IND_L, !on);
            }
            case A_IND_R -> {
                boolean on = has(F_IND_R);
                setFlag(F_IND_L | F_IND_R, false);
                setFlag(F_IND_R, !on);
            }
            case A_HAZARD -> setFlag(F_HAZARD, !has(F_HAZARD));
            case A_INTERIOR -> setFlag(F_INTERIOR, !has(F_INTERIOR));
            case A_HORN -> honk();
            case A_SIREN -> {
                if (spec().siren) {
                    setFlag(F_SIREN, !has(F_SIREN));
                    if (has(F_SIREN)) setFlag(F_BEACONS, true);
                }
            }
            case A_BEACONS -> {
                if (spec().beacons) {
                    setFlag(F_BEACONS, !has(F_BEACONS));
                    if (!has(F_BEACONS)) setFlag(F_SIREN, false);
                }
            }
            case A_GEARMODE -> setFlag(F_MANUAL, !has(F_MANUAL));
            default -> { }
        }
    }

    public void honk() {
        if (hornCooldown > 0) return;
        hornCooldown = 18;
        setFlag(F_HORN, true);
        level().playSound(null, getX(), getY() + 0.8, getZ(), TCRegistry.sound("horn." + spec().horn, () -> "horn.car"), SoundSource.NEUTRAL,
                spec().isHeavy() ? 3.0f : 2.2f, 0.97f + random.nextFloat() * 0.06f);
        if (!level().isClientSide) for (var p : level().getEntitiesOfClass(com.trafficcraft.npc.PedestrianEntity.class, getBoundingBox().inflate(12))) p.brain.horn(this);
    }

    // ================================================================= client visuals
    private void clientVisuals() {
        float v = visSpeed();
        driftVisO = driftVis;
        driftVis += (getDrift() - driftVis) * 0.3f;
        tickBackfireFx();
        // tyre smoke: drifting, or a sporty car launched / burning out on full throttle
        String cat = spec().category;
        boolean sporty = cat.equals("sports") || cat.equals("super") || cat.equals("muscle");
        boolean burnout = sporty && has(F_ENGINE) && visLoad() > 0.85f && Math.abs(v) < 13f && visGear() >= 1;
        float drift = Math.abs(driftVis);
        if (burnout || (drift > 6 && Math.abs(v) > 3)) {
            float k = burnout ? Math.max(0.4f, 1 - Math.abs(v) / 13f) : Math.min(1f, drift / 22f);
            for (VehicleSpec.Wheel w : spec().wheels) {
                if (w.steer() || random.nextFloat() > 0.35f + 0.6f * k) continue;
                Vec3 p = position().add(toWorldOffset(w.x() * 1.05, 0.12, w.z() - 0.2, getYRot()));
                Vec3 back = toWorldOffset((random.nextFloat() - 0.5f) * 0.06, 0, -0.04 - 0.05 * k, getYRot());
                if (smoke.size() < 140)
                    smoke.add(new float[]{(float) p.x, (float) p.y, (float) p.z, (float) back.x, 0.012f + random.nextFloat() * 0.01f, (float) back.z, 0,
                            45 + random.nextInt(35), 0.5f + 0.4f * k});
            }
        }
        for (int i = smoke.size() - 1; i >= 0; i--) {
            float[] p = smoke.get(i);
            p[0] += p[3];
            p[1] += p[4];
            p[2] += p[5];
            p[3] *= 0.94f;
            p[5] *= 0.94f;
            p[4] = p[4] * 0.97f + 0.0015f;
            p[8] += 0.035f;
            if (++p[6] > p[7]) smoke.remove(i);
        }
        if (drift > 6 && Math.abs(v) > 3 || burnout) {
            if (tickCount - skidTick > 26) {
                skidTick = tickCount;
                level().playLocalSound(getX(), getY(), getZ(), com.trafficcraft.registry.TCRegistry.snd("misc.skid"), net.minecraft.sounds.SoundSource.NEUTRAL,
                        Math.min(1f, Math.abs(driftVis) / 20f), 0.9f + random.nextFloat() * 0.2f, false);
            }
        }
        float r = spec().wheels.isEmpty() ? 0.33f : spec().wheels.get(0).r();
        wheelSpin += (float) Math.toDegrees(v * 0.05f / r);
        steerVis += (visSteer() - steerVis) * 0.5f;
        if (spec().bike) {
            // lean into turns: tan(lean) = v * yaw rate / g
            double omega = Math.toRadians(deltaYaw * 20.0);
            float target = (float) Mth.clamp(Math.toDegrees(Math.atan(v * omega / 9.81)), -42, 42);
            lean += (target - lean) * 0.2f;
        }
        if (has(F_IND_L) || has(F_IND_R) || has(F_HAZARD)) {
            int phase = (int) ((level().getGameTime() / 8) & 1);
            if (phase != blinkTick) {
                blinkTick = phase;
                CLIENT_EVENT.accept(this, phase == 1 ? "blink_on" : "blink_off");
            }
        }
    }

    public boolean blinkOn() {
        return (level().getGameTime() / 8 & 1) == 1;
    }

    // ================================================================= collision helpers (used by physics)
    /** True if solid blocks sit in front of (dir=+1) or behind (dir=-1) the vehicle at the given position. */
    public boolean blockedAt(Vec3 pos, float yaw, int dir) {
        VehicleSpec s = spec();
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r), lx = Math.cos(r), lz = Math.sin(r);
        double half = s.length / 2 + 0.05;
        double step = maxUpStep();
        double[] heights = {pos.y + step + 0.12, pos.y + Math.min(s.height * 0.7, step + 0.9)};
        for (double side : new double[]{-0.42, 0, 0.42}) {
            double px = pos.x + fx * half * dir + lx * side * s.width;
            double pz = pos.z + fz * half * dir + lz * side * s.width;
            for (double h : heights) {
                BlockPos bp = BlockPos.containing(px, h, pz);
                BlockState st = level().getBlockState(bp);
                if (st.isAir()) continue;
                VoxelShape shape = st.getCollisionShape(level(), bp);
                if (shape.isEmpty()) continue;
                double top = bp.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
                double bot = bp.getY() + shape.min(net.minecraft.core.Direction.Axis.Y);
                if (h <= top && h >= bot) return true;
            }
        }
        return false;
    }

    /** Oriented-box overlap with another vehicle (2D SAT + vertical range). */
    public boolean overlaps(VehicleEntity o, Vec3 myPos, float myYaw) {
        if (Math.abs(o.getY() - myPos.y) > Math.max(spec().height, o.spec().height)) return false;
        double[][] a = corners(myPos.x, myPos.z, myYaw, spec().length / 2, spec().width / 2);
        double[][] b = corners(o.getX(), o.getZ(), o.getYRot(), o.spec().length / 2, o.spec().width / 2);
        return !separated(a, b) && !separated(b, a);
    }

    private static double[][] corners(double x, double z, float yaw, double hl, double hw) {
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r), lx = Math.cos(r), lz = Math.sin(r);
        return new double[][]{{x + fx * hl + lx * hw, z + fz * hl + lz * hw}, {x + fx * hl - lx * hw, z + fz * hl - lz * hw},
                {x - fx * hl - lx * hw, z - fz * hl - lz * hw}, {x - fx * hl + lx * hw, z - fz * hl + lz * hw}};
    }

    private static boolean separated(double[][] a, double[][] b) {
        for (int i = 0; i < 4; i++) {
            double[] p = a[i], q = a[(i + 1) % 4];
            double nx = -(q[1] - p[1]), nz = q[0] - p[0];
            double amin = Double.MAX_VALUE, amax = -Double.MAX_VALUE, bmin = Double.MAX_VALUE, bmax = -Double.MAX_VALUE;
            for (double[] c : a) {
                double d = c[0] * nx + c[1] * nz;
                amin = Math.min(amin, d);
                amax = Math.max(amax, d);
            }
            for (double[] c : b) {
                double d = c[0] * nx + c[1] * nz;
                bmin = Math.min(bmin, d);
                bmax = Math.max(bmax, d);
            }
            if (amax < bmin || bmax < amin) return true;
        }
        return false;
    }

    void moveBody(Vec3 motion) {
        move(MoverType.SELF, motion);
    }

    float vy() { return vy; }
    void setVy(float v) { vy = v; }
    int lastAutoGear() { return lastAutoGear; }
    void setLastAutoGear(int g) { lastAutoGear = g; }
}
