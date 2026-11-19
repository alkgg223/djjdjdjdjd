package com.vehicleworks.vehicle;

import com.vehicleworks.Config;
import com.vehicleworks.VehicleWorks;
import com.vehicleworks.block.ComponentBlockEntity;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.network.Payloads;
import com.vehicleworks.physics.VoxelSet;
import com.vehicleworks.physics.aero.WingStrip;
import com.vehicleworks.physics.ground.WheelSim;
import com.vehicleworks.physics.propulsion.EngineSim;
import com.vehicleworks.physics.sim.RotorUnit;
import com.vehicleworks.physics.sim.TankUnit;
import com.vehicleworks.physics.sim.ThrusterUnit;
import com.vehicleworks.physics.sim.VehicleSimulation;
import com.vehicleworks.registry.ModEntities;
import com.vehicleworks.weapon.WeaponSystem;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * A moving block structure. The server simulates it with the VehicleWorks physics engine;
 * clients receive the structure once and then stream pose snapshots which they interpolate.
 * The entity position is the world position of the centre of mass.
 */
public class VehicleEntity extends Entity implements IEntityWithComplexSpawn {
    public final VehicleStructure structure = new VehicleStructure();
    /** structure-local centre of mass */
    public final Vector3d comLocal = new Vector3d();
    public final Quaterniond orientation = new Quaterniond();
    public final Quaterniond prevOrientation = new Quaterniond();
    public final Vector3d linearVelocity = new Vector3d();
    public final Vector3d angularVelocity = new Vector3d();
    public final VehicleControls controls = new VehicleControls();
    public VehicleTelemetry telemetry = new VehicleTelemetry();
    public final WeaponSystem weapons = new WeaponSystem(this);
    /** passenger UUID → seat position (structure-local key) */
    public final Map<UUID, Long> seats = new HashMap<>();
    /** interactive passenger seat states (CabinSeat packing), by seat block */
    public final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap cabin = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    {
        cabin.defaultReturnValue(CabinSeat.DEFAULT);
    }

    /** seat class of the seat a player sits in: -1 not seated, 0 crew / plain seat, 1..5 cabin class */
    public int seatClassOf(Entity e) {
        Long k = seats.get(e.getUUID());
        if (k == null) return -1;
        ComponentType ct = VehicleBuilder.typeOf(structure.get(k));
        return ct == null ? 0 : ct.seatClass();
    }

    /** Seated as a passenger (cabin seat, or a plain passenger seat that is not a gunner station). */
    public boolean isPassengerSeated(Entity e) {
        Long k = seats.get(e.getUUID());
        if (k == null) return false;
        ComponentType ct = VehicleBuilder.typeOf(structure.get(k));
        if (ct == null) return false;
        if (ct.seatClass() > 0) return true;
        return ct == ComponentType.PASSENGER_SEAT && !ComponentType.PASSENGER_SEAT.property("gunner").getBool(VehicleBuilder.config(structure, k));
    }

    /** seated in a cabin crew jump seat */
    public boolean isAttendantSeated(Entity e) {
        Long k = seats.get(e.getUUID());
        return k != null && VehicleBuilder.typeOf(structure.get(k)) == ComponentType.JUMPSEAT;
    }

    /** an open aircraft door: players walk through it */
    public boolean isPassable(long k) {
        ComponentType ct = VehicleBuilder.typeOf(structure.get(k));
        return ct == ComponentType.AIRCRAFT_DOOR && (cabin.get(k) & 1) != 0;
    }

    /** Right-click on a cabin fixture: doors open / close, lavatory door, galley ovens, trolley is pushed. */
    public boolean useCabinFixture(ServerPlayer sp, long k, ComponentType ct) {
        Vector3d w = localToWorld(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 0.5, BlockPos.getZ(k) + 0.5, new Vector3d());
        int st = cabin.containsKey(k) ? cabin.get(k) : 0;
        net.minecraft.sounds.SoundEvent snd;
        switch (ct) {
            case AIRCRAFT_DOOR -> {
                if ((st & 1) == 0 && linearVelocity.length() > 3 && !onGroundNow()) { msg(sp, "vehicleworks.msg.door_locked"); return true; }
                st ^= 1;
                snd = com.vehicleworks.registry.ModSounds.CABIN_DOOR.get();
            }
            case LAVATORY -> { st ^= 1; snd = com.vehicleworks.registry.ModSounds.CABIN_DOOR.get(); }
            case GALLEY -> { st = (st + 1) % 4; snd = com.vehicleworks.registry.ModSounds.TRAY.get(); } // 0 closed, 1 oven, 2 coffee, 3 cabinets
            case TROLLEY -> {
                // push the trolley one step along the aisle (forward / back), up to 12 blocks from its stowage
                int off = (byte) (st & 255), dir = (st & 256) != 0 ? -1 : 1;
                int next = off + dir;
                if (Math.abs(next) > 12 || blockedForTrolley(k, next)) { st ^= 256; next = off - dir; }
                st = (st & ~255) | (next & 255);
                snd = com.vehicleworks.registry.ModSounds.SEAT_MOTOR.get();
            }
            default -> { return false; }
        }
        cabin.put(k, st);
        level().playSound(null, w.x, w.y, w.z, snd, net.minecraft.sounds.SoundSource.NEUTRAL, 0.8f, ct == ComponentType.TROLLEY ? 0.6f : 1f);
        Payloads.sendCabin(this, null);
        return true;
    }

    private boolean blockedForTrolley(long home, int off) {
        Direction f = VehicleBuilder.facing(structure.get(home));
        long p = BlockPos.offset(home, f.getStepX() * off, 0, f.getStepZ() * off);
        return structure.contains(p) && !structure.get(p).isAir() && VehicleBuilder.typeOf(structure.get(p)) != ComponentType.TROLLEY;
    }

    public boolean onGroundNow() {
        return layout != null && layout.sim.onGround;
    }

    private static void msg(ServerPlayer sp, String key) {
        sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
    }

    /** passenger service: the bus coming on lights every personal screen; calls are cleared by the crew */
    private void cabinPowerChanged(boolean powered) {
        boolean changed = false;
        for (long k : structure.blockEntities().keySet()) {
            ComponentType ct = VehicleBuilder.typeOf(structure.get(k));
            if (ct == null || ct.seatClass() < 3) continue;
            int s0 = cabin.get(k), s1 = CabinSeat.withScreen(s0, powered);
            if (s0 != s1) { cabin.put(k, s1); changed = true; }
        }
        if (changed) Payloads.sendCabin(this, null);
    }

    public void clearCalls() {
        boolean changed = false;
        for (var e : cabin.long2IntEntrySet()) if (CabinSeat.call(e.getIntValue())) { e.setValue(CabinSeat.withCall(e.getIntValue(), false)); changed = true; }
        if (changed) Payloads.sendCabin(this, null);
    }

    /** a passenger operates its own seat (recline, leg rest, screen, tray, light, suite door) */
    public void cabinControl(ServerPlayer sp, int control) {
        Long k = seats.get(sp.getUUID());
        if (k == null) return;
        int cls = seatClassOf(sp);
        if (cls <= 0 || !Config.SERVER.passengerSeatInteraction.get()) return;
        int before = cabin.get((long) k), after = CabinSeat.apply(cls, before, control);
        if (after == before) return;
        cabin.put((long) k, after);
        Vector3d w = localToWorld(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 0.5, BlockPos.getZ(k) + 0.5, new Vector3d());
        var snd = switch (control) {
            case CabinSeat.CALL -> com.vehicleworks.registry.ModSounds.IFE_CHIME.get();
            case CabinSeat.DOOR -> com.vehicleworks.registry.ModSounds.CABIN_DOOR.get();
            case CabinSeat.SCREEN, CabinSeat.PAGE, CabinSeat.LIGHT -> com.vehicleworks.registry.ModSounds.BUTTON.get();
            case CabinSeat.TRAY -> com.vehicleworks.registry.ModSounds.TRAY.get();
            default -> com.vehicleworks.registry.ModSounds.SEAT_MOTOR.get();
        };
        level().playSound(null, w.x, w.y, w.z, snd, net.minecraft.sounds.SoundSource.NEUTRAL, 0.7f, 1f);
        Payloads.sendCabin(this, null);
    }
    /** client-only render/interpolation data, created lazily by client code */
    public Object clientState;

    private VehicleLayout layout;
    private CompletableFuture<VehicleLayout> pending;
    private boolean rebuildRequested;
    private final Long2FloatOpenHashMap pendingDamage = new Long2FloatOpenHashMap();
    private final LongArrayList changedBlocks = new LongArrayList();
    private boolean damageChanged;
    private int ticksSinceSync;
    private int frozenTicks;
    private ServerPlayer pilot;
    private ControlProfile profileOverride = ControlProfile.AUTO;
    public double stressLevel;
    private int crushTimer;
    private int infoViewers;

    public VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    // ------------------------------------------------------------------ creation

    /** Create a vehicle from an assembled structure whose core block was at {@code origin}. */
    public static VehicleEntity create(ServerLevel level, VehicleStructure structure, BlockPos origin) {
        VehicleEntity v = new VehicleEntity(ModEntities.VEHICLE.get(), level);
        v.structure.load(structure.save(), level);
        VehicleLayout layout = VehicleBuilder.build(v.structure);
        Vector3d com = layout.sim.com;
        v.comLocal.set(com);
        v.setPos(origin.getX() + com.x, origin.getY() + com.y, origin.getZ() + com.z);
        v.xo = v.getX(); v.yo = v.getY(); v.zo = v.getZ();
        v.installLayout(layout);
        v.loadCoreSettings();
        // a new aircraft leaves the factory cold and dark
        if (v.isAircraft()) v.controls.coldAndDark();
        v.controls.initialized = true;
        return v;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public VehicleSimulation simulation() {
        return layout != null ? layout.sim : null;
    }

    public VehicleLayout layout() {
        return layout;
    }

    public boolean isFrozen() {
        return frozenTicks > 0;
    }

    public void freeze(int ticks) {
        frozenTicks = Math.max(frozenTicks, ticks);
    }

    public ControlProfile profile() {
        if (profileOverride != ControlProfile.AUTO) return profileOverride;
        return layout != null ? layout.detectedProfile : ControlProfile.CAR;
    }

    @Nullable
    public ServerPlayer pilot() {
        return pilot;
    }

    private void loadCoreSettings() {
        long core = BlockPos.asLong(0, 0, 0);
        if (VehicleBuilder.typeOf(structure.get(core)) != ComponentType.VEHICLE_CORE) return;
        CompoundTag c = VehicleBuilder.config(structure, core);
        ComponentType t = ComponentType.VEHICLE_CORE;
        profileOverride = ControlProfile.byIndex(t.property("profile").getInt(c));
        controls.assist = t.property("assist").getBool(c);
        controls.mouseAim = t.property("mouse_aim").getBool(c);
        if (layout != null) {
            layout.sim.differential = t.property("differential").getInt(c);
            layout.sim.transmission.automatic = t.property("auto_gearbox").getBool(c);
            layout.sim.handling = t.property("handling").getInt(c);
        }
    }

    // ------------------------------------------------------------------ pose transforms

    /**
     * Keep the pose quaternion unit length. Interpolated / integrated quaternions drift slightly,
     * and then transform / transformInverse stop being exact inverses (≈0.1 % scale error), which
     * broke walking and jumping on a rotating vehicle.
     */
    private Quaterniond unitOrientation() {
        double l2 = orientation.lengthSquared();
        if (Math.abs(l2 - 1) > 1e-12) orientation.normalize();
        return orientation;
    }

    /** World position of a structure-local point using the current tick pose. */
    public Vector3d localToWorld(double lx, double ly, double lz, Vector3d out) {
        out.set(lx - comLocal.x, ly - comLocal.y, lz - comLocal.z);
        unitOrientation().transform(out);
        return out.add(getX(), getY(), getZ());
    }

    public Vector3d worldToLocal(double wx, double wy, double wz, Vector3d out) {
        out.set(wx - getX(), wy - getY(), wz - getZ());
        unitOrientation().transformInverse(out);
        return out.add(comLocal);
    }

    public Vector3d localDirToWorld(double x, double y, double z, Vector3d out) {
        out.set(x, y, z);
        return unitOrientation().transform(out);
    }

    public Vector3d worldDirToLocal(double x, double y, double z, Vector3d out) {
        out.set(x, y, z);
        return unitOrientation().transformInverse(out);
    }

    /** Interpolated orientation for rendering. */
    public Quaterniond renderOrientation(float partial, Quaterniond out) {
        return prevOrientation.slerp(orientation, partial, out);
    }

    public Vec3 renderPosition(float partial) {
        return new Vec3(Mth.lerp(partial, xo, getX()), Mth.lerp(partial, yo, getY()), Mth.lerp(partial, zo, getZ()));
    }

    /** World velocity of a world point that moves with the vehicle. */
    public Vector3d pointVelocity(double wx, double wy, double wz, Vector3d out) {
        double rx = wx - getX(), ry = wy - getY(), rz = wz - getZ();
        Vector3d w = angularVelocity;
        return out.set(linearVelocity.x + (w.y * rz - w.z * ry), linearVelocity.y + (w.z * rx - w.x * rz), linearVelocity.z + (w.x * ry - w.y * rx));
    }

    @Override
    protected AABB makeBoundingBox() {
        if (structure == null || structure.isEmpty() || orientation == null) {
            return new AABB(getX() - 0.5, getY() - 0.5, getZ() - 0.5, getX() + 0.5, getY() + 0.5, getZ() + 0.5);
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        Vector3d p = new Vector3d();
        for (int c = 0; c < 8; c++) {
            double x = (c & 1) == 0 ? structure.minX() : structure.maxX() + 1;
            double y = (c & 2) == 0 ? structure.minY() : structure.maxY() + 1;
            double z = (c & 4) == 0 ? structure.minZ() : structure.maxZ() + 1;
            localToWorld(x, y, z, p);
            minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        double r = 64 + getBoundingBox().getSize() * 4;
        return d < r * r * 16;
    }

    // ------------------------------------------------------------------ layout lifecycle

    public void requestRebuild() {
        rebuildRequested = true;
    }

    private void startRebuild() {
        rebuildRequested = false;
        writeRuntimeToStructure();
        VehicleStructure snapshot = structure.copy();
        if (snapshot.size() < 2500) {
            installLayout(VehicleBuilder.build(snapshot));
        } else {
            pending = CompletableFuture.supplyAsync(() -> VehicleBuilder.build(snapshot), net.minecraft.Util.backgroundExecutor());
        }
    }

    private void installLayout(VehicleLayout n) {
        VehicleSimulation ns = n.sim;
        VehicleLayout old = layout;
        // keep the structure fixed in the world while its centre of mass changes
        Vector3d origin = new Vector3d(-comLocal.x, -comLocal.y, -comLocal.z);
        orientation.transform(origin).add(getX(), getY(), getZ());
        Vector3d newPos = new Vector3d(ns.com);
        orientation.transform(newPos).add(origin);
        ns.body.position.set(newPos);
        ns.body.orientation.set(orientation);
        ns.body.linearVelocity.set(linearVelocity);
        ns.body.angularVelocity.set(angularVelocity);
        ns.body.updateDerived();
        if (old != null) {
            VehicleSimulation os = old.sim;
            for (var e : n.thrusterIndex.long2IntEntrySet()) {
                int oi = old.thrusterIndex.get(e.getLongKey());
                if (oi < 0) continue;
                EngineSim a = os.thrusters.get(oi).engine, b = ns.thrusters.get(e.getIntValue()).engine;
                b.state = a.state;
                b.phase = a.phase;
                b.autoStart = a.autoStart;
                b.startTimer = a.startTimer;
                b.crankTime = a.crankTime;
                b.spool = a.spool;
                b.temperatureC = a.temperatureC;
                b.damage = Math.max(b.damage, a.damage);
                b.startTimer = a.startTimer;
            }
            for (var e : n.tankIndex.long2IntEntrySet()) {
                int oi = old.tankIndex.get(e.getLongKey());
                if (oi >= 0) ns.tanks.get(e.getIntValue()).amount = os.tanks.get(oi).amount;
            }
            for (var e : n.rotorIndex.long2IntEntrySet()) {
                int oi = old.rotorIndex.get(e.getLongKey());
                if (oi >= 0) ns.rotors.get(e.getIntValue()).model.omega = os.rotors.get(oi).model.omega;
            }
            for (var e : n.wheelIndex.long2IntEntrySet()) {
                int oi = old.wheelIndex.get(e.getLongKey());
                if (oi >= 0) {
                    WheelSim a = os.wheels.get(oi), b = ns.wheels.get(e.getIntValue());
                    b.omega = a.omega;
                    b.extension = a.extension;
                }
            }
            ns.transmission.gear = os.transmission.gear;
            ns.envelopeTempC = os.envelopeTempC;
            if (os.gasFill.length == ns.fluid.gasPockets.size()) ns.gasFill = os.gasFill.clone();
            if (level() instanceof ServerLevel sl) VehiclePhysicsManager.get(sl).forget(os);
        }
        ns.body.wake();
        layout = n;
        comLocal.set(ns.com);
        setPos(newPos.x, newPos.y, newPos.z);
        weapons.rebuild(n);
        loadCoreSettings();
        // passengers whose seat disappeared are dismounted
        for (Entity p : new ArrayList<>(getPassengers())) {
            Long seat = seats.get(p.getUUID());
            if (seat == null || n.seats.stream().noneMatch(s -> s.pos() == seat)) p.stopRiding();
        }
        syncSeats();
        forceStateSync();
    }

    /** Copy simulation runtime state (fuel, ballast, engine wear, ammo) back into block entity data. */
    public void writeRuntimeToStructure() {
        if (layout == null) return;
        VehicleSimulation s = layout.sim;
        for (TankUnit t : s.tanks) {
            CompoundTag be = structure.blockEntity(t.voxel);
            if (be == null) continue;
            CompoundTag d = be.getCompound(ComponentBlockEntity.DATA);
            d.putDouble(t.kind == TankUnit.Kind.FUEL ? "Fuel" : "Water", t.amount);
            if (t.kind == TankUnit.Kind.FUEL) d.putInt("FuelType", t.fuelType);
            be.put(ComponentBlockEntity.DATA, d);
        }
        for (ThrusterUnit t : s.thrusters) {
            CompoundTag be = structure.blockEntity(t.voxel);
            if (be == null) continue;
            CompoundTag d = be.getCompound(ComponentBlockEntity.DATA);
            d.putDouble("Damage", t.engine.damage);
            d.putDouble("Temp", t.engine.temperatureC);
            be.put(ComponentBlockEntity.DATA, d);
        }
        weapons.writeToStructure();
    }

    // ------------------------------------------------------------------ physics hooks (server)

    /** Called by the physics manager before the world step. */
    public void prePhysics() {
        if (pending != null && pending.isDone()) {
            try {
                VehicleLayout n = pending.getNow(null);
                if (n != null) installLayout(n);
            } catch (Exception e) {
                VehicleWorks.LOGGER.error("Vehicle layout build failed", e);
            }
            pending = null;
        }
        if (layout == null) return;
        if (frozenTicks > 0) frozenTicks--;
        VehicleSimulation sim = layout.sim;
        updatePilot();
        boolean fresh = (level().getGameTime() - controls.lastInputTick < 10 && pilot != null) || controls.testPilot;
        if (!fresh) controls.keys = 0;
        controls.apply(sim.input, profile(), fresh, 0.05);
        updateElectrical(sim, 0.05);
        firstOfficerAssist(sim);
        rollEmergencies(sim);
        autoAnnouncements(sim);
        sim.hydraulicFailure = controls.hydFailed;
        if (controls.fuelLeakKgS > 0) for (TankUnit t : sim.tanks) if (t.kind == TankUnit.Kind.FUEL) t.amount = Math.max(0, t.amount - controls.fuelLeakKgS * 0.05 / Math.max(1, sim.tanks.size()));
        if (controls.depressurized && sim.altitude < 3000 && sim.onGround) controls.depressurized = false;
        // A/THR: hold the FCU speed with the thrust levers
        if (controls.autothrust && !sim.onGround) controls.throttle = Math.max(0.05, Math.min(1, controls.throttle + Math.max(-0.02, Math.min(0.02, (controls.apSpeed - sim.airspeed) * 0.01))));
        // a parked aircraft may sleep, but not while its engines start or run
        if (sim.body.sleeping && (controls.starterTicks > 0 || sim.thrusters.stream().anyMatch(t -> t.engine.state != com.vehicleworks.physics.propulsion.EngineSim.State.OFF
                && t.engine.state != com.vehicleworks.physics.propulsion.EngineSim.State.FAILED))) sim.body.wake();
        tickRefuel(sim);
        sim.fuelScale = Config.SERVER.fuelConsumptionScale.get();
        sim.fbwSensitivity = Config.SERVER.sidestickSensitivity.get();
        sim.fbwDeadZone = Config.SERVER.sidestickDeadZone.get();
        controls.sensitivity = Config.SERVER.controlSensitivity.get();
        if (!Config.SERVER.requireFuel.get()) {
            for (TankUnit t : sim.tanks) if (t.kind == TankUnit.Kind.FUEL) t.amount = t.capacity;
            if (sim.tanks.stream().noneMatch(t -> t.kind == TankUnit.Kind.FUEL)) {
                TankUnit virtual = new TankUnit(TankUnit.Kind.FUEL, 1);
                virtual.amount = 1;
                virtual.localPos.set(sim.com);
                virtual.voxel = Long.MIN_VALUE;
                sim.tanks.add(virtual);
            }
        }
    }

    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet groundLights = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    /** Landing / taxi lights light the ground ahead with invisible light blocks that follow the beam. */
    private void updateGroundLights() {
        if (!(level() instanceof ServerLevel sl)) return;
        it.unimi.dsi.fastutil.longs.LongOpenHashSet want = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        if (layout != null && controls.busPowered && Config.SERVER.lightsIlluminateWorld.get() && !layout.lights.isEmpty()) {
            for (long k : layout.lights) {
                if (!structure.contains(k)) continue;
                int type = ComponentType.AIRCRAFT_LIGHT.property("light_type").getInt(VehicleBuilder.config(structure, k));
                boolean landing = type == 6 && controls.landingLights, taxi = type == 7 && controls.taxiLights, turn = type == 8 && controls.turnoffLights;
                if (!landing && !taxi && !turn) continue;
                net.minecraft.core.Direction f = VehicleBuilder.facing(structure.get(k));
                Vector3d o = localToWorld(BlockPos.getX(k) + 0.5 + f.getStepX() * 0.6, BlockPos.getY(k) + 0.5, BlockPos.getZ(k) + 0.5 + f.getStepZ() * 0.6, new Vector3d());
                Vector3d d = localDirToWorld(f.getStepX(), landing ? -0.18 : -0.3, f.getStepZ(), new Vector3d()).normalize();
                double range = landing ? 60 : 30;
                Vec3 from = new Vec3(o.x, o.y, o.z), to = from.add(d.x * range, d.y * range, d.z * range);
                var hit = sl.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
                if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) continue;
                BlockPos lp = hit.getBlockPos().relative(hit.getDirection());
                if (sl.getBlockState(lp).isAir() || sl.getBlockState(lp).is(net.minecraft.world.level.block.Blocks.LIGHT)) want.add(lp.asLong());
                if (want.size() >= 3) break;
            }
        }
        // remove light blocks no longer lit, place the new ones
        var it = groundLights.iterator();
        while (it.hasNext()) {
            long p = it.nextLong();
            if (want.contains(p)) continue;
            BlockPos bp = BlockPos.of(p);
            if (sl.isLoaded(bp) && sl.getBlockState(bp).is(net.minecraft.world.level.block.Blocks.LIGHT)) sl.setBlock(bp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            it.remove();
        }
        for (long p : want) {
            if (groundLights.contains(p)) continue;
            BlockPos bp = BlockPos.of(p);
            if (sl.getBlockState(bp).isAir()) {
                sl.setBlock(bp, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 14), 2);
                groundLights.add(p);
            }
        }
    }

    private void clearGroundLights() {
        if (!(level() instanceof ServerLevel sl)) return;
        for (long p : groundLights) {
            BlockPos bp = BlockPos.of(p);
            if (sl.isLoaded(bp) && sl.getBlockState(bp).is(net.minecraft.world.level.block.Blocks.LIGHT)) sl.setBlock(bp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        }
        groundLights.clear();
    }

    // ------------------------------------------------------------------ refuelling (interactive, through a refuel port)
    private java.util.UUID refueler;
    private int refuelGrade;
    private double refuelRemaining, refuelBuffer;

    public boolean isRefueling() { return refueler != null; }
    public double refuelRemaining() { return refuelRemaining; }

    public void startRefuel(ServerPlayer sp, int grade, double kg) {
        if (layout == null || kg <= 0) return;
        if (!layout.structureHas(ComponentType.REFUEL_PORT)) { sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("vehicleworks.refuel.no_port"), true); return; }
        refueler = sp.getUUID();
        refuelGrade = grade;
        refuelRemaining = kg;
        refuelBuffer = 0;
        level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.BUCKET_FILL, net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 0.8f);
    }

    public void stopRefuel(ServerPlayer sp, boolean byPlayer) {
        if (refueler == null) return;
        refueler = null;
        writeRuntimeToStructure();
        if (sp != null) sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(byPlayer ? "vehicleworks.refuel.stopped" : "vehicleworks.refuel.done",
                String.format("%.0f", layout != null ? layout.sim.fuelAmount : 0)), true);
    }

    /** Fuel flows from the hose into the emptiest tanks, a little every tick (15 kg/s + 3 kg/s per tank). */
    private void tickRefuel(VehicleSimulation sim) {
        if (refueler == null || !(level() instanceof ServerLevel sl)) return;
        ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(refueler);
        if (sp == null || sp.distanceToSqr(this) > 40 * 40) { stopRefuel(sp, true); return; }
        int ntank = 0;
        for (TankUnit t : sim.tanks) if (t.kind == TankUnit.Kind.FUEL && t.voxel != Long.MIN_VALUE) ntank++;
        double step = Math.min(refuelRemaining, (15 + 3 * ntank) * 0.05);
        // fuel comes from canisters in the player's inventory unless creative
        if (!sp.getAbilities().instabuild && refuelBuffer < step) {
            var inv = sp.getInventory();
            for (int i = 0; i < inv.getContainerSize() && refuelBuffer < step; i++) {
                var st = inv.getItem(i);
                if (st.getItem() instanceof com.vehicleworks.item.FuelItem fi) { refuelBuffer += fi.kg; st.shrink(1); }
            }
            if (refuelBuffer < 1e-6) { sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("vehicleworks.refuel.no_canisters"), true); stopRefuel(null, false); return; }
            step = Math.min(step, refuelBuffer);
        }
        TankUnit best = null;
        for (TankUnit t : sim.tanks) {
            if (t.kind != TankUnit.Kind.FUEL || t.voxel == Long.MIN_VALUE || t.amount >= t.capacity - 1e-3) continue;
            if (best == null || t.fill() < best.fill()) best = t;
        }
        if (best == null || step <= 0) { stopRefuel(sp, false); return; }
        double add = Math.min(step, best.capacity - best.amount);
        if (best.amount > 1 && best.fuelType != 0 && best.fuelType != refuelGrade) best.fuelType = 3; // contaminated
        else if (best.amount <= 1 || best.fuelType == 0) best.fuelType = refuelGrade;
        best.amount += add;
        refuelRemaining -= add;
        if (!sp.getAbilities().instabuild) refuelBuffer -= add;
        if (tickCount % 20 == 0) level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 1.3f);
        if (refuelRemaining <= 1e-3) stopRefuel(sp, false);
    }

    /** Battery capacity (J) of the aircraft electrical system. */
    private static final double BATTERY_J = 1.5e6;

    /**
     * Electrical bus: battery (drains with the load) and engine generators (power the bus and
     * recharge the battery). Systems without power stop working; ignition and fuel pumps gate the engines.
     */
    /** an aircraft (wings or rotors): has the full cockpit electrical / start procedure */
    public boolean isAircraft() {
        return layout != null && (layout.wingArea > 0 || !layout.sim.rotors.isEmpty());
    }

    private void updateElectrical(VehicleSimulation sim, double dt) {
        VehicleControls c = controls;
        boolean aircraft = isAircraft();
        if (!c.initialized) {
            // a new aircraft leaves the factory cold and dark
            if (aircraft) c.coldAndDark();
            c.initialized = true;
        }
        boolean gen = false, cranking = false;
        for (ThrusterUnit t : sim.thrusters) {
            if (t.engine.type == com.vehicleworks.physics.propulsion.EngineType.ELECTRIC) continue;
            if (t.engine.isRunning() && t.engine.spool > t.engine.type.idleFraction * 0.8) gen = true;
            if (t.engine.state == com.vehicleworks.physics.propulsion.EngineSim.State.STARTING && t.engine.starterCmd) cranking = true;
        }
        gen &= c.generatorSw && c.masterElec && !c.genFailed;
        double load = 0;
        if (c.avionics) load += 280;
        if (c.fuelPump) load += 90 * Math.max(1, sim.fuelPumps);
        if (c.lights) load += 60;
        if (c.beacon) load += 50;
        if (c.strobe) load += 70;
        if (c.landingLights) load += 250;
        if (c.taxiLights) load += 150;
        if (c.turnoffLights) load += 120;
        load += c.cabinLight * 40 + c.panelLight * 20 + (c.consoleLight ? 25 : 0);
        if (cranking) load += 2500;
        if (c.autopilot) load += 60;
        if (!c.masterElec) load = 0;
        double genW = gen ? 3500 : 0;
        if (c.battery) c.batteryCharge = Math.max(0, Math.min(1, c.batteryCharge + (genW - load) / BATTERY_J * dt));
        c.generator = gen;
        boolean was = c.busPowered;
        c.busPowered = c.masterElec && ((c.battery && c.batteryCharge > 0.001) || gen);
        if (c.busPowered && !was) c.powerOnTick = level().getGameTime();
        if (c.busPowered != was) cabinPowerChanged(c.busPowered);
        boolean avionicsOn = c.busPowered && c.avionics;
        if (!avionicsOn && c.autopilot) c.autopilot = false;
        sim.boostPumps = c.busPowered && c.fuelPump;
        sim.fuelSelector = c.fuelSelector;
        if (!aircraft) {
            c.starterTicks = 0;
            return;
        }
        // cockpit start procedure: starter, ignition (magnetos / igniters) and fuel come from the switches
        if (c.starterTicks > 0) c.starterTicks--;
        if (!c.busPowered) c.starterTicks = 0;
        boolean starter = c.starterTicks > 0;
        boolean anyCranking = false;
        for (ThrusterUnit t : sim.thrusters) {
            com.vehicleworks.physics.propulsion.EngineSim e = t.engine;
            if (e.type == com.vehicleworks.physics.propulsion.EngineType.ELECTRIC) {
                e.ignitionCmd = e.fuelCmd = true;
                continue;
            }
            boolean piston = e.type == com.vehicleworks.physics.propulsion.EngineType.PISTON;
            e.starterPower = c.busPowered;
            e.starterCmd = starter;
            // piston magnetos make their own spark; turbine igniters need the bus
            e.ignitionCmd = c.ignition && (piston || c.busPowered);
            e.fuelCmd = c.engineMaster && c.fuelSelector != 3;
            if (e.state == com.vehicleworks.physics.propulsion.EngineSim.State.STARTING && !(e.type.isTurbine() && e.spool >= e.starterCutout() && e.phase == com.vehicleworks.physics.propulsion.EngineSim.Phase.ACCEL))
                anyCranking = true;
        }
        // turbine starters cut out at self-sustaining speed; a piston starter is released once it runs
        if (starter && !anyCranking) c.starterTicks = 0;
    }

    /** Engine start. Quick start (I key) runs the whole checklist; the cockpit starter only engages the starter. */
    public String startEngines(boolean automatic) {
        if (layout == null) return "vehicleworks.msg.no_engines";
        VehicleControls c = controls;
        VehicleSimulation sim = layout.sim;
        if (automatic) {
            c.battery = true;
            c.masterElec = true;
            c.avionics = true;
            c.fuelPump = true;
            c.ignition = true;
            c.engineMaster = true;
            c.generatorSw = true;
            if (c.fuelSelector == 3) c.fuelSelector = 0;
            c.parkingBrake = false; // quick start = whole checklist, ready to taxi
            c.busPowered = c.batteryCharge > 0.001;
            if (!c.busPowered) return "vehicleworks.start.no_power";
            sim.boostPumps = true;
            boolean any = false;
            for (ThrusterUnit t : sim.thrusters) if (t.engine.enabled) { t.engine.requestStart(true); any = true; }
            return any ? null : "vehicleworks.msg.no_engines";
        }
        return engageStarter();
    }

    /** Cockpit starter switch: engage (or release) the starter. The engines then follow the real procedure. */
    public String engageStarter() {
        if (layout == null) return "vehicleworks.msg.no_engines";
        VehicleControls c = controls;
        VehicleSimulation sim = layout.sim;
        if (!c.battery && !c.generator) return "vehicleworks.start.no_power";
        if (!c.masterElec || !c.busPowered) return "vehicleworks.start.no_bus";
        if (c.starterTicks > 0) { c.starterTicks = 0; return "vehicleworks.start.released"; }
        boolean any = false, turbine = false;
        for (ThrusterUnit t : sim.thrusters) {
            if (!t.engine.enabled || t.engine.type == com.vehicleworks.physics.propulsion.EngineType.ELECTRIC) continue;
            if (t.engine.type.needsStarter && !sim.hasStarter) return "vehicleworks.msg.need_starter";
            if (t.engine.state == com.vehicleworks.physics.propulsion.EngineSim.State.OFF || t.engine.state == com.vehicleworks.physics.propulsion.EngineSim.State.STOPPING)
                t.engine.requestStart(false);
            turbine |= t.engine.type.isTurbine();
            any = true;
        }
        if (!any) {
            for (ThrusterUnit t : sim.thrusters) if (t.engine.enabled) { t.engine.requestStart(true); any = true; }
            return any ? null : "vehicleworks.msg.no_engines";
        }
        // piston: the key is held in START for up to 6 s; turbine: the start valve stays open until cut-out (max 60 s)
        c.starterTicks = turbine ? 1200 : 120;
        return null;
    }

    /** Called by the physics manager after the world step. */
    public void postPhysics() {
        if (layout == null) return;
        VehicleSimulation sim = layout.sim;
        if (sim.frozen && frozenTicks <= 0 && !isFrozen()) {
            // region not loaded: hold position
            sim.body.linearVelocity.zero();
            sim.body.angularVelocity.zero();
        }
        Vector3d p = sim.body.position;
        orientation.set(sim.body.orientation);
        linearVelocity.set(sim.body.linearVelocity);
        angularVelocity.set(sim.body.angularVelocity);
        comLocal.set(sim.com);
        setPos(p.x, p.y, p.z);
        applyCrash();
        checkGearLoads();
        if (tickCount % 4 == 0) updateGroundLights();
        applyPendingDamage();
        checkStructuralLimits(sim);
    }

    /** Accumulates collision impulses (called from the physics listener per substep). */
    public void onImpact(long voxel, double impulse, double approach, @Nullable VehicleEntity other) {
        if (!Config.SERVER.crashDamage.get()) return;
        if (approach > crashApproach) { crashApproach = approach; crashVoxel = voxel; }
        double thr = Config.SERVER.crashSpeedThreshold.get();
        if (approach < thr) return;
        double dmg = impulse * (approach - thr) / 400.0 * Config.SERVER.crashDamageScale.get();
        if (dmg < 0.5) return;
        long key = BlockPos.asLong(VoxelSet.unpackX(voxel), VoxelSet.unpackY(voxel), VoxelSet.unpackZ(voxel));
        if (!structure.contains(key)) return;
        pendingDamage.addTo(key, (float) dmg);
    }

    /** Landing gear overload (hard landings, too heavy aircraft) and tyre over-speed damage the gear. */
    private void checkGearLoads() {
        if (layout == null || tickCount % 2 != 0) return;
        for (var w : layout.sim.wheels) {
            if (!w.grounded || !structure.contains(w.voxel)) continue;
            float hp = BlockPhysics.hitPoints(structure.get(w.voxel));
            double cap = w.params.loadCapacity;
            if (cap > 0 && w.load > 1.5 * cap) damageBlock(w.voxel, (float) (hp * 0.04 * (w.load / cap - 1.5)));
            double v = Math.abs(w.omega * w.params.radius);
            if (w.params.maxSpeed > 0 && v > w.params.maxSpeed) damageBlock(w.voxel, (float) (hp * 0.01 * (v / w.params.maxSpeed - 1) * 10));
        }
    }

    private double crashApproach;
    private long crashVoxel;
    private int crashCooldown;

    /**
     * Big crashes: the faster the hit, the more blocks around the impact point break
     * (about 23 blocks at 100 km/h, scaling up with speed).
     */
    private void applyCrash() {
        if (crashCooldown > 0) crashCooldown--;
        double v = crashApproach;
        long vox = crashVoxel;
        crashApproach = 0;
        double kmh = v * 3.6;
        if (crashCooldown > 0 || kmh < 45 || !(level() instanceof ServerLevel sl)) return;
        int n = (int) Math.round((kmh - 30) / 3.0 * Config.SERVER.crashDamageScale.get() * Config.SERVER.aircraftDamageMultiplier.get());
        int size = structure.blocks().size();
        n = Math.min(n, Math.max(1, (int) (size * 0.7)));
        if (n <= 0) return;
        crashCooldown = 10;
        int cx = VoxelSet.unpackX(vox), cy = VoxelSet.unpackY(vox), cz = VoxelSet.unpackZ(vox);
        long core = BlockPos.asLong(0, 0, 0);
        List<long[]> cand = new ArrayList<>();
        for (long k : structure.blocks().keySet()) {
            if (k == core && n < size * 0.6) continue;
            long dx = BlockPos.getX(k) - cx, dy = BlockPos.getY(k) - cy, dz = BlockPos.getZ(k) - cz;
            cand.add(new long[]{k, dx * dx + dy * dy + dz * dz});
        }
        cand.sort(java.util.Comparator.comparingLong(a -> a[1]));
        int broken = 0;
        for (long[] c : cand) {
            if (broken >= n) {
                // the next ring is only damaged
                if (broken >= n * 2) break;
                damageBlock(c[0], BlockPhysics.hitPoints(structure.get(c[0])) * 0.6f);
                broken++;
                continue;
            }
            int ti = layout != null ? layout.thrusterIndex.get(c[0]) : -1;
            if (ti >= 0) layout.sim.thrusters.get(ti).engine.fail("crash");
            destroyBlock(c[0], 0.15);
            broken++;
        }
        Vector3d w = localToWorld(cx + 0.5, cy + 0.5, cz + 0.5, new Vector3d());
        float vol = (float) Math.min(4, 1 + n / 10.0);
        sl.playSound(null, w.x, w.y, w.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.NEUTRAL, vol, 0.7f);
        sl.sendParticles(n > 15 ? net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER : net.minecraft.core.particles.ParticleTypes.EXPLOSION, w.x, w.y, w.z, 1, 0, 0, 0, 0);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, w.x, w.y, w.z, 20 + n, 1.5, 1.0, 1.5, 0.05);
        splitDisconnected();
    }

    /** Make one engine (or all, index &lt; 0) explode: fire, failure and damage to the blocks around it. */
    public int explodeEngine(int index) {
        if (layout == null || !(level() instanceof ServerLevel sl)) return 0;
        int count = 0;
        for (var e : layout.thrusterIndex.long2IntEntrySet()) {
            int ti = e.getIntValue();
            if (index >= 0 && ti != index) continue;
            long key = e.getLongKey();
            var eng = layout.sim.thrusters.get(ti).engine;
            eng.fail("explosion");
            eng.damage = 1;
            int bx = BlockPos.getX(key), by = BlockPos.getY(key), bz = BlockPos.getZ(key);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                long k = BlockPos.asLong(bx + dx, by + dy, bz + dz);
                if (k == key || !structure.contains(k)) continue;
                damageBlock(k, BlockPhysics.hitPoints(structure.get(k)) * (random.nextFloat() < 0.4f ? 1.1f : 0.5f));
            }
            if (structure.contains(key)) structure.setDamage(key, BlockPhysics.hitPoints(structure.get(key)) * 0.95f);
            damageChanged = true;
            Vector3d w = localToWorld(bx + 0.5, by + 0.5, bz + 0.5, new Vector3d());
            sl.playSound(null, w.x, w.y, w.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 3f, 0.8f);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, w.x, w.y, w.z, 1, 0, 0, 0, 0);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, w.x, w.y, w.z, 40, 0.6, 0.6, 0.6, 0.08);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, w.x, w.y, w.z, 40, 0.8, 0.8, 0.8, 0.05);
            count++;
        }
        return count;
    }

    public void damageBlock(long key, float amount) {
        if (structure.contains(key) && amount > 0) pendingDamage.addTo(key, amount);
    }

    private void applyPendingDamage() {
        if (pendingDamage.isEmpty()) return;
        List<Long> destroyed = new ArrayList<>();
        for (var e : pendingDamage.long2FloatEntrySet()) {
            long key = e.getLongKey();
            BlockState st = structure.get(key);
            if (st.isAir()) continue;
            float d = structure.damage(key) + e.getFloatValue() * (float) (double) Config.SERVER.aircraftDamageMultiplier.get();
            if (d >= BlockPhysics.hitPoints(st)) destroyed.add(key);
            else {
                structure.setDamage(key, d);
                damageChanged = true;
                int ti = layout.thrusterIndex.get(key);
                if (ti >= 0) layout.sim.thrusters.get(ti).engine.damage = Math.min(1, d / BlockPhysics.hitPoints(st));
                int wi = layout.wingStripOfBlock.get(key);
                if (wi >= 0) {
                    WingStrip w = layout.sim.wings.get(wi);
                    w.damageFactor = Math.max(0.2, 1 - d / BlockPhysics.hitPoints(st) * 0.6);
                }
            }
        }
        pendingDamage.clear();
        for (long key : destroyed) destroyBlock(key, 0.35);
        if (!destroyed.isEmpty()) splitDisconnected();
    }

    /** Remove a block, with break effects and an optional item drop chance. */
    public void destroyBlock(long key, double dropChance) {
        BlockState st = structure.get(key);
        if (st.isAir()) return;
        Vector3d w = localToWorld(BlockPos.getX(key) + 0.5, BlockPos.getY(key) + 0.5, BlockPos.getZ(key) + 0.5, new Vector3d());
        BlockPos wp = BlockPos.containing(w.x, w.y, w.z);
        if (level() instanceof ServerLevel sl) {
            sl.levelEvent(2001, wp, Block.getId(st));
            if (random.nextDouble() < dropChance) Block.popResource(sl, wp, new ItemStack(st.getBlock().asItem()));
        }
        removeBlockLocal(key);
    }

    public void setBlockLocal(long key, BlockState state, @Nullable CompoundTag be) {
        structure.put(key, state, be);
        changedBlocks.add(key);
        requestRebuild();
    }

    public void removeBlockLocal(long key) {
        structure.remove(key);
        changedBlocks.add(key);
        requestRebuild();
        if (structure.isEmpty()) discard();
    }

    /** Break off parts that are no longer connected to the core into separate debris vehicles. */
    public void splitDisconnected() {
        if (structure.isEmpty() || !(level() instanceof ServerLevel sl)) return;
        LongOpenHashSet remaining = new LongOpenHashSet(structure.blocks().keySet());
        List<LongOpenHashSet> parts = new ArrayList<>();
        while (!remaining.isEmpty()) {
            long start = remaining.iterator().nextLong();
            LongOpenHashSet part = new LongOpenHashSet();
            ArrayDeque<Long> q = new ArrayDeque<>();
            q.add(start);
            remaining.remove(start);
            while (!q.isEmpty()) {
                long k = q.poll();
                part.add(k);
                for (Direction d : Direction.values()) {
                    long n = BlockPos.offset(k, d);
                    if (remaining.remove(n)) q.add(n);
                }
            }
            parts.add(part);
        }
        if (parts.size() <= 1) return;
        long core = BlockPos.asLong(0, 0, 0);
        LongOpenHashSet keep = parts.stream().filter(p -> p.contains(core)).findFirst()
                .orElse(parts.stream().max(Comparator.comparingInt(LongOpenHashSet::size)).orElseThrow());
        writeRuntimeToStructure();
        for (LongOpenHashSet part : parts) {
            if (part == keep) continue;
            if (part.size() <= 2) {
                for (long k : part) destroyBlock(k, 0.5);
                continue;
            }
            VehicleStructure debris = new VehicleStructure();
            // re-origin the debris at its first block so its local frame stays consistent
            long anchor = part.iterator().nextLong();
            int ax = BlockPos.getX(anchor), ay = BlockPos.getY(anchor), az = BlockPos.getZ(anchor);
            for (long k : part) {
                long nk = BlockPos.asLong(BlockPos.getX(k) - ax, BlockPos.getY(k) - ay, BlockPos.getZ(k) - az);
                debris.put(nk, structure.get(k), structure.blockEntity(k));
                float dmg = structure.damage(k);
                if (dmg > 0) debris.setDamage(nk, dmg);
            }
            for (long k : part) removeBlockLocal(k);
            VehicleEntity v = new VehicleEntity(ModEntities.VEHICLE.get(), sl);
            v.structure.load(debris.save(), sl);
            VehicleLayout dl = VehicleBuilder.build(v.structure);
            // place debris so its blocks keep their current world positions
            v.orientation.set(orientation);
            v.comLocal.set(dl.sim.com);
            Vector3d anchorWorld = localToWorld(ax, ay, az, new Vector3d());
            Vector3d pos = new Vector3d(dl.sim.com);
            orientation.transform(pos).add(anchorWorld);
            v.setPos(pos.x, pos.y, pos.z);
            v.xo = pos.x; v.yo = pos.y; v.zo = pos.z;
            pointVelocity(pos.x, pos.y, pos.z, v.linearVelocity);
            v.angularVelocity.set(angularVelocity);
            v.installLayout(dl);
            sl.addFreshEntity(v);
        }
    }

    private void checkStructuralLimits(VehicleSimulation sim) {
        // wing load limits (structural stress)
        double limit = 26000 * Config.SERVER.structuralStrength.get();
        double maxRatio = 0;
        for (int i = 0; i < sim.wings.size(); i++) {
            WingStrip w = sim.wings.get(i);
            double perBlock = Math.abs(w.lastLift) / Math.max(1, w.blocks) / Math.max(0.5, w.params.chord);
            double ratio = perBlock / limit;
            maxRatio = Math.max(maxRatio, ratio);
            if (ratio > 1.0 && w.voxels.length > 0) {
                long k = w.voxels[random.nextInt(w.voxels.length)];
                damageBlock(k, (float) ((ratio - 1.0) * 6.0));
            }
        }
        stressLevel = maxRatio;
        // hull crush depth for submarines
        if (sim.inWater && sim.fluidForces.maxDepth > Config.SERVER.crushDepth.get()) {
            if (++crushTimer % 10 == 0 && !structure.isEmpty()) {
                long[] keys = structure.blocks().keySet().toLongArray();
                long k = keys[random.nextInt(keys.length)];
                damageBlock(k, (float) (sim.fluidForces.maxDepth - Config.SERVER.crushDepth.get()) * 2f);
            }
        } else crushTimer = 0;
    }

    // ------------------------------------------------------------------ pilot & seats

    private void updatePilot() {
        pilot = null;
        if (layout == null) return;
        VehicleLayout.Seat ps = layout.pilotSeat();
        for (Entity e : getPassengers()) {
            if (!(e instanceof ServerPlayer sp)) continue;
            Long seat = seats.get(e.getUUID());
            if (seat == null) continue;
            if (ps != null ? seat == ps.pos() : true) { pilot = sp; break; }
        }
    }

    public void receiveInput(ServerPlayer player, int keys, float yaw, float pitch) {
        if (player != pilot && !isGunner(player)) return;
        if (player == pilot) {
            controls.keys = keys;
            controls.lookYaw = yaw;
            controls.lookPitch = pitch;
            controls.lastInputTick = level().getGameTime();
        }
        weapons.setAim(player, yaw, pitch, keys);
    }

    public boolean isGunner(Player p) {
        if (layout == null) return false;
        Long seat = seats.get(p.getUUID());
        if (seat == null) return false;
        for (VehicleLayout.Seat s : layout.seats) if (s.pos() == seat && s.gunner()) return true;
        return false;
    }

    public boolean trySit(net.minecraft.world.entity.LivingEntity player, long seatPos) {
        if (layout == null || player.isPassenger()) return false;
        VehicleLayout.Seat seat = null;
        for (VehicleLayout.Seat s : layout.seats) if (s.pos() == seatPos) seat = s;
        if (seat == null) return false;
        if (seats.containsValue(seatPos)) return false;
        seats.put(player.getUUID(), seatPos);
        if (!player.startRiding(this, true)) {
            seats.remove(player.getUUID());
            return false;
        }
        syncSeats();
        return true;
    }

    // ================================================================== people on board: passengers, first officer

    /** passenger seats (cabin classes and plain passenger seats that are not gunner stations) */
    private boolean isPassengerSeatKey(long k) {
        ComponentType ct = VehicleBuilder.typeOf(structure.get(k));
        if (ct == null) return false;
        if (ct.seatClass() > 0) return true;
        return ct == ComponentType.PASSENGER_SEAT && !ComponentType.PASSENGER_SEAT.property("gunner").getBool(VehicleBuilder.config(structure, k));
    }

    /** Fill every free passenger seat with a passenger (boarding). Returns how many boarded. */
    public int boardPassengers(int max) {
        if (layout == null || level().isClientSide) return 0;
        int n = 0;
        for (VehicleLayout.Seat s : layout.seats) {
            if (n >= max) break;
            if (!isPassengerSeatKey(s.pos()) || seats.containsValue(s.pos())) continue;
            NpcEntity p = com.vehicleworks.registry.ModEntities.NPC.get().create(level());
            if (p == null) continue;
            Vector3d w = localToWorld(BlockPos.getX(s.pos()) + 0.5, BlockPos.getY(s.pos()) + 0.5, BlockPos.getZ(s.pos()) + 0.5, new Vector3d());
            p.moveTo(w.x, w.y, w.z, getYRot(), 0);
            p.setRole(NpcEntity.PASSENGER);
            level().addFreshEntity(p);
            if (trySit(p, s.pos())) n++;
            else p.discard();
        }
        return n;
    }

    public int deplanePassengers() {
        int n = 0;
        for (Entity e : new java.util.ArrayList<>(getPassengers())) {
            if (e instanceof NpcEntity npc && npc.role() == NpcEntity.PASSENGER) { npc.stopRiding(); npc.discard(); n++; }
        }
        return n;
    }

    public NpcEntity firstOfficer() {
        for (Entity e : getPassengers()) if (e instanceof NpcEntity npc && npc.role() == NpcEntity.FIRST_OFFICER) return npc;
        return null;
    }

    /** J key: seat the AI first officer, then switch its help off, then dismiss it. */
    public String copilotKey() {
        if (layout == null) return null;
        NpcEntity fo = firstOfficer();
        if (fo == null) {
            Long seat = null;
            for (VehicleLayout.Seat s : layout.seats)
                if (VehicleBuilder.typeOf(structure.get(s.pos())) == ComponentType.COPILOT_SEAT && !seats.containsValue(s.pos())) seat = s.pos();
            if (seat == null) return "vehicleworks.fo.no_seat";
            NpcEntity p = com.vehicleworks.registry.ModEntities.NPC.get().create(level());
            if (p == null) return null;
            Vector3d w = localToWorld(BlockPos.getX(seat) + 0.5, BlockPos.getY(seat) + 0.5, BlockPos.getZ(seat) + 0.5, new Vector3d());
            p.moveTo(w.x, w.y, w.z, getYRot(), 0);
            p.setRole(NpcEntity.FIRST_OFFICER);
            level().addFreshEntity(p);
            if (!trySit(p, seat)) { p.discard(); return "vehicleworks.fo.no_seat"; }
            controls.copilotAssist = true;
            return "vehicleworks.fo.seated";
        }
        if (controls.copilotAssist) { controls.copilotAssist = false; return "vehicleworks.fo.assist_off"; }
        fo.stopRiding();
        fo.discard();
        return "vehicleworks.fo.dismissed";
    }

    private long foGearCall = -1000, foPullUp = -1000;

    /** The AI first officer: gear up after a positive climb, gear down on approach, terrain / stall recovery, lights. */
    private void firstOfficerAssist(VehicleSimulation sim) {
        NpcEntity fo = firstOfficer();
        if (fo == null || !controls.copilotAssist || !isAircraft() || !sim.rotors.isEmpty()) return;
        long now = level().getGameTime();
        double agl = sim.heightAboveGround, vs = sim.verticalSpeed, ias = sim.airspeed;
        boolean air = !sim.onGround;
        if (air && controls.gearDown && vs > 2 && agl > 15 && !controls.gearJammed && now - foGearCall > 60) {
            controls.gearDown = false;
            foGearCall = now;
            foSay("vehicleworks.fo.gear_up");
            level().playSound(null, getX(), getY(), getZ(), com.vehicleworks.registry.ModSounds.GEAR_RETRACT.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.2f, 1f);
        }
        if (air && !controls.gearDown && vs < -1 && agl < 120 && ias < 95 && !controls.gearJammed && now - foGearCall > 60) {
            controls.gearDown = true;
            foGearCall = now;
            foSay("vehicleworks.fo.gear_down");
            level().playSound(null, getX(), getY(), getZ(), com.vehicleworks.registry.ModSounds.GEAR_EXTEND.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.2f, 1f);
        }
        // terrain: sinking fast close to the ground without landing gear (not a landing) -> pull up, full power
        double tti = vs < -0.5 ? agl / -vs : 99;
        boolean landing = controls.gearDown && vs > -6;
        if (air && !landing && agl < 90 && (tti < 6 || vs < -15)) {
            sim.input.pitch = Math.min(1, sim.input.pitch + 0.8);
            sim.input.roll *= 0.3;
            controls.throttle = 1;
            if (now - foPullUp > 80) { foPullUp = now; foSay("vehicleworks.fo.pull_up"); }
        }
        if (air && sim.stallWarning) {
            sim.input.pitch = Math.min(sim.input.pitch, -0.4);
            controls.throttle = 1;
            if (now - foPullUp > 80) { foPullUp = now; foSay("vehicleworks.fo.stall"); }
        }
        // landing lights below 300 m with the gear down, off when climbing through 3000 m
        if (air && controls.gearDown && agl < 300 && !controls.landingLights) controls.landingLights = true;
        if (air && agl > 3000 && controls.landingLights) controls.landingLights = false;
    }

    private void foSay(String key) {
        for (Entity e : getPassengers()) if (e instanceof ServerPlayer sp && seats.get(sp.getUUID()) != null)
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("vehicleworks.fo.prefix", net.minecraft.network.chat.Component.translatable(key)), true);
    }

    // ================================================================== cabin announcements

    public static final int PA_BOARDING = 0, PA_WELCOME = 1, PA_SAFETY = 2, PA_TAKEOFF = 3, PA_CRUISE = 4, PA_LANDING = 5, PA_ARRIVAL = 6, PA_DEPLANING = 7, PA_COUNT = 8;
    private int paLast = -1;
    private boolean paFlown;

    /** Play an announcement on the cabin speakers: chime + text (and the voice when enabled) for everyone on board. */
    public void announce(int id) {
        if (id < 0 || id >= PA_COUNT || level().isClientSide) return;
        level().playSound(null, getX(), getY(), getZ(), com.vehicleworks.registry.ModSounds.PA_CHIME.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.4f, 1f);
        AABB box = getBoundingBox().inflate(3);
        for (ServerPlayer sp : ((net.minecraft.server.level.ServerLevel) level()).players())
            if (sp.getVehicle() == this || box.contains(sp.position())) Payloads.toPlayer(sp, new Payloads.Announce(getId(), id));
        paLast = id;
    }

    /** automatic announcements by flight phase */
    private void autoAnnouncements(VehicleSimulation sim) {
        if (!controls.autoAnnouncements || !isAircraft() || !controls.busPowered || layout.seats.size() < 6) return;
        boolean running = sim.thrusters.stream().anyMatch(t -> t.engine.isRunning());
        boolean air = !sim.onGround;
        double gs = sim.body.linearVelocity.length();
        int next = -1;
        if (paLast < PA_BOARDING && !running && !air) next = PA_BOARDING;
        else if (paLast == PA_BOARDING && allDoorsClosed()) next = PA_WELCOME;
        else if ((paLast == PA_WELCOME || paLast == PA_BOARDING) && running) next = PA_SAFETY;
        else if (paLast == PA_SAFETY && !air && gs > 4) next = PA_TAKEOFF;
        else if (paLast == PA_TAKEOFF && air && sim.heightAboveGround > 250) next = PA_CRUISE;
        else if (paLast == PA_CRUISE && air && sim.verticalSpeed < -1.5 && sim.heightAboveGround < 300 && controls.gearDown) next = PA_LANDING;
        else if ((paLast == PA_LANDING || paLast == PA_CRUISE) && !air && gs < 10 && paFlown) next = PA_ARRIVAL;
        else if (paLast == PA_ARRIVAL && !running) next = PA_DEPLANING;
        if (air) paFlown = true;
        if (next >= 0) {
            announce(next);
            if (next == PA_DEPLANING) { paFlown = false; paLast = -1; }
        }
    }

    public boolean allDoorsClosed() {
        for (long k : structure.blockEntities().keySet())
            if (VehicleBuilder.typeOf(structure.get(k)) == ComponentType.AIRCRAFT_DOOR && (cabin.get(k) & 1) != 0) return false;
        return true;
    }

    /** doors bit field for the crew panel / ECAM (bit set = open), in structure order */
    public int doorBits() {
        int b = 0, i = 0;
        for (long k : structure.blockEntities().keySet()) {
            if (VehicleBuilder.typeOf(structure.get(k)) != ComponentType.AIRCRAFT_DOOR) continue;
            if ((cabin.get(k) & 1) != 0) b |= 1 << Math.min(i, 30);
            i++;
        }
        return b;
    }

    // ================================================================== emergencies (config: enable / chance per flight hour)

    public static final String[] EMERGENCIES = {"engineFailure", "engineFire", "birdStrike", "hydraulicFailure", "generatorFailure", "fuelLeak", "gearJam", "instrumentFailure", "depressurization"};

    private void rollEmergencies(VehicleSimulation sim) {
        if (!Config.SERVER.emergenciesEnabled.get() || !isAircraft() || sim.onGround || sim.heightAboveGround < 30) return;
        if (tickCount % 20 != 0) return;
        for (int i = 0; i < EMERGENCIES.length; i++) {
            if (!Config.SERVER.emergencyEnabled[i].get()) continue;
            double perHour = Config.SERVER.emergencyChance[i].get() / 100.0;
            if (random.nextDouble() < perHour / 3600.0) triggerEmergency(EMERGENCIES[i]);
        }
    }

    /** Make an emergency happen now (also /vehicleworks emergency <name>). */
    public boolean triggerEmergency(String name) {
        if (layout == null) return false;
        VehicleSimulation sim = layout.sim;
        java.util.List<ThrusterUnit> eng = sim.thrusters.stream().filter(t -> t.engine.type != com.vehicleworks.physics.propulsion.EngineType.ELECTRIC).toList();
        ThrusterUnit e = eng.isEmpty() ? null : eng.get(random.nextInt(eng.size()));
        switch (name) {
            case "engineFailure" -> { if (e == null) return false; e.engine.fail("engine_failure"); }
            case "engineFire" -> { if (e == null) return false; e.engine.damage = Math.max(e.engine.damage, 0.85); e.engine.fail("fire"); }
            case "birdStrike" -> { if (e == null) return false; e.engine.damage = Math.min(1, e.engine.damage + 0.35); }
            case "hydraulicFailure" -> controls.hydFailed = true;
            case "generatorFailure" -> controls.genFailed = true;
            case "fuelLeak" -> controls.fuelLeakKgS = 1.5;
            case "gearJam" -> controls.gearJammed = true;
            case "instrumentFailure" -> controls.instrumentFailed = true;
            case "depressurization" -> controls.depressurized = true;
            default -> { return false; }
        }
        com.vehicleworks.VehicleWorks.LOGGER.info("Vehicle {} emergency: {}", getId(), name);
        return true;
    }

    public void clearEmergencies() {
        controls.hydFailed = controls.genFailed = controls.gearJammed = controls.instrumentFailed = controls.depressurized = false;
        controls.fuelLeakKgS = 0;
    }

    public Long seatOf(Entity e) {
        return seats.get(e.getUUID());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!cabin.isEmpty()) Payloads.sendCabin(this, player);
    }

    public void syncSeats() {
        if (level().isClientSide) return;
        Payloads.sendSeats(this);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return layout != null && getPassengers().size() < Math.max(1, layout.seats.size()) || level().isClientSide;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide) {
            seats.remove(passenger.getUUID());
            if (passenger == pilot) {
                controls.keys = 0;
                if (profile() == ControlProfile.CAR) controls.parkingBrake = true;
            }
            syncSeats();
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction fn) {
        Long seat = seats.get(passenger.getUUID());
        Vector3d w = new Vector3d();
        if (seat != null) {
            localToWorld(BlockPos.getX(seat) + 0.5, BlockPos.getY(seat) + 0.45, BlockPos.getZ(seat) + 0.5, w);
        } else {
            w.set(getX(), getY(), getZ());
        }
        Vec3 attach = passenger.getVehicleAttachmentPoint(this);
        fn.accept(passenger, w.x - attach.x, w.y - attach.y, w.z - attach.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Long seat = seats.get(passenger.getUUID());
        Vector3d w = new Vector3d();
        if (seat != null) localToWorld(BlockPos.getX(seat) + 0.5, BlockPos.getY(seat) + 1.05, BlockPos.getZ(seat) + 0.5, w);
        else w.set(getX(), getBoundingBox().maxY + 0.2, getZ());
        return new Vec3(w.x, w.y, w.z);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        prevOrientation.set(orientation);
        if (level().isClientSide) {
            if (clientState == null) com.vehicleworks.VehicleWorks.PROXY.initClientState(this);
            com.vehicleworks.VehicleWorks.PROXY.clientTick(this);
            return;
        }
        if (layout == null && pending == null) startRebuild();
        if (rebuildRequested && pending == null) startRebuild();
        if (layout == null) return;
        if (getY() < level().getMinBuildHeight() - 64) {
            ejectPassengers();
            discard();
            return;
        }
        weapons.tick(controls.fireBits());
        buildTelemetry();
        // network sync
        ServerLevel sl = (ServerLevel) level();
        VehicleSimulation sim = layout.sim;
        ticksSinceSync++;
        boolean moving = !sim.body.sleeping;
        if (moving || ticksSinceSync >= 20) {
            Payloads.sendState(this, sl.getGameTime());
            ticksSinceSync = 0;
        }
        if (tickCount % 2 == 0) Payloads.sendTelemetry(this);
        if (!changedBlocks.isEmpty() || damageChanged) {
            Payloads.sendBlockChanges(this, changedBlocks, damageChanged);
            changedBlocks.clear();
            damageChanged = false;
        }
        if (tickCount % 200 == 0) writeRuntimeToStructure();
    }

    public void forceStateSync() {
        ticksSinceSync = 100;
    }

    private void buildTelemetry() {
        VehicleTelemetry t = telemetry;
        VehicleSimulation s = layout.sim;
        t.profile = profile().ordinal();
        t.airspeed = (float) s.airspeed;
        t.groundSpeed = (float) Math.hypot(s.body.linearVelocity.x, s.body.linearVelocity.z);
        t.altitude = (float) getY();
        t.agl = (float) s.heightAboveGround;
        t.verticalSpeed = (float) s.verticalSpeed;
        t.aoa = (float) Math.toDegrees(s.angleOfAttack);
        t.sideslip = (float) Math.toDegrees(s.sideslip);
        t.gLoad = (float) s.gLoad;
        t.mach = (float) s.mach;
        Vector3d fw = localDirToWorld(s.forward.x, s.forward.y, s.forward.z, new Vector3d());
        Vector3d rw = localDirToWorld(s.right.x, s.right.y, s.right.z, new Vector3d());
        Vector3d uw = localDirToWorld(s.up.x, s.up.y, s.up.z, new Vector3d());
        t.heading = (float) ((Math.toDegrees(Math.atan2(fw.x, -fw.z)) + 360) % 360);
        t.pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(fw.y, -1, 1)));
        t.bank = (float) Math.toDegrees(Math.atan2(-rw.y, uw.y));
        t.stall = s.stallWarning;
        t.onGround = s.onGround;
        t.inWater = s.inWater;
        t.instruments = layout.instruments;
        t.throttle = (float) controls.throttle;
        t.collective = (float) controls.collective;
        t.flaps = (float) controls.flaps;
        t.shipThrottle = (float) controls.shipThrottle;
        t.lift = (float) s.input.lift;
        t.gearDown = controls.gearDown;
        t.armed = controls.armed;
        t.assist = controls.assist;
        t.mouseAim = controls.mouseAim;
        t.afterburner = s.input.afterburner;
        t.parkingBrake = controls.parkingBrake;
        t.fuel = (float) s.fuelAmount;
        t.fuelCapacity = (float) s.fuelCapacity;
        t.fuelFlow = (float) s.fuelFlow;
        t.ballast = (float) s.ballastFill;
        t.envelopeTemp = (float) s.envelopeTempC;
        t.gasLift = (float) s.gasLift;
        t.mass = (float) s.mass();
        t.carGear = s.transmission.gear;
        t.rpmFraction = (float) s.carEngineRpmFraction;
        t.depth = (float) (s.inWater ? s.fluidForces.maxDepth : 0);
        t.integrity = integrity();
        t.stress = (float) stressLevel;
        t.staticMargin = (float) layout.staticMargin;
        t.rotorOffset = (float) layout.rotorOffset;
        t.navActive = false;
        t.autopilot = controls.autopilot;
        t.battery = controls.battery;
        t.vne = layout.wingArea > 0 ? (float) Math.max(110, s.rotateSpeed * 4.2) : 0;
        t.hydraulics = (float) s.hydraulicAuthority;
        // a yoke is linked to the surfaces; a side stick shows the pilot's own (spring-centred) input
        t.stickPitch = (float) (s.fbw ? s.stickPitchIn : s.effPitch);
        t.stickRoll = (float) (s.fbw ? s.stickRollIn : s.effRoll);
        t.batteryCharge = (float) controls.batteryCharge;
        t.switches = controls.switchBits();
        t.fuelSelector = controls.fuelSelector;
        t.cabinLight = controls.cabinLight;
        t.panelLight = controls.panelLight;
        t.dimmer = controls.dimmer;
        t.displayBrt = controls.displayBrt;
        t.powerAge = controls.busPowered ? (int) Math.min(100000, level().getGameTime() - controls.powerOnTick) : 0;
        t.fbw = layout != null && layout.types.contains(ComponentType.SIDESTICK);
        t.speedBrake = (float) controls.speedBrake;
        t.apSpeed = (float) controls.apSpeed;
        t.stickYaw = (float) s.input.yaw;
        t.cabinLights = controls.cabinLights;
        t.mcdu1 = controls.mcdu1;
        t.mcdu2 = controls.mcdu2;
        t.doors = doorBits();
        t.failures = (controls.genFailed ? VehicleTelemetry.FAIL_GEN : 0) | (controls.hydFailed ? VehicleTelemetry.FAIL_HYD : 0) | (controls.gearJammed ? VehicleTelemetry.FAIL_GEAR : 0)
                | (controls.instrumentFailed ? VehicleTelemetry.FAIL_INSTR : 0) | (controls.depressurized ? VehicleTelemetry.FAIL_PRESS : 0) | (controls.fuelLeakKgS > 0 ? VehicleTelemetry.FAIL_FUEL : 0);
        t.seatbelt = controls.seatbeltSign;
        t.autothrust = controls.autothrust;
        t.foPresent = firstOfficer() != null;
        t.foAssist = controls.copilotAssist;
        t.autoPA = controls.autoAnnouncements;
        t.busPowered = controls.busPowered;
        t.generator = controls.generator;
        t.wrongFuel = s.wrongFuel;
        t.refueling = isRefueling();
        t.refuelRemaining = (float) refuelRemaining;
        int nft = 0;
        for (TankUnit tu : s.tanks) if (tu.kind == TankUnit.Kind.FUEL && tu.voxel != Long.MIN_VALUE) nft++;
        if (t.tankFill.length != nft) { t.tankFill = new float[nft]; t.tankGrade = new byte[nft]; t.tankSide = new byte[nft]; }
        int ti2 = 0;
        for (TankUnit tu : s.tanks) {
            if (tu.kind != TankUnit.Kind.FUEL || tu.voxel == Long.MIN_VALUE) continue;
            t.tankFill[ti2] = (float) tu.fill(); t.tankGrade[ti2] = (byte) tu.fuelType; t.tankSide[ti2] = (byte) tu.side; ti2++;
        }
        t.lights = controls.lights;
        t.emergencyTest = level().getGameTime() < controls.emergencyUntil;
        t.apHeading = (float) controls.apHeading;
        t.apAltitude = (float) controls.apAltitude;
        if (layout.navComputer != Long.MIN_VALUE) {
            CompoundTag c = VehicleBuilder.config(structure, layout.navComputer);
            ComponentType nt = ComponentType.NAVIGATION_COMPUTER;
            if (nt.property("waypoint_active").getBool(c)) {
                double dx = nt.property("waypoint_x").get(c) + 0.5 - getX(), dz = nt.property("waypoint_z").get(c) + 0.5 - getZ();
                t.navActive = true;
                t.navDistance = (float) Math.sqrt(dx * dx + dz * dz);
                t.navBearing = (float) ((Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360);
            }
        }
        int ne = s.thrusters.size();
        if (t.engineSpool.length != ne) {
            t.engineState = new byte[ne]; t.engineType = new byte[ne]; t.enginePhase = new byte[ne];
            t.engineSpool = new float[ne]; t.engineTemp = new float[ne]; t.engineDamage = new float[ne];
            t.engineThrust = new float[ne]; t.propOmega = new float[ne]; t.engineAb = new boolean[ne];
        }
        for (int i = 0; i < ne; i++) {
            ThrusterUnit u = s.thrusters.get(i);
            t.engineState[i] = (byte) u.engine.state.ordinal();
            t.engineType[i] = (byte) u.engine.type.ordinal();
            var ph = u.engine.phase;
            boolean lit = u.engine.isRunning() || (u.engine.state == com.vehicleworks.physics.propulsion.EngineSim.State.STARTING
                    && (ph == com.vehicleworks.physics.propulsion.EngineSim.Phase.LIGHT_OFF || ph == com.vehicleworks.physics.propulsion.EngineSim.Phase.ACCEL));
            boolean turning = u.engine.state == com.vehicleworks.physics.propulsion.EngineSim.State.STARTING && u.engine.starterCmd || u.engine.autoStart && u.engine.state == com.vehicleworks.physics.propulsion.EngineSim.State.STARTING;
            t.enginePhase[i] = (byte) (ph.ordinal() | (u.device == ThrusterUnit.Device.ROTOR ? 8 : 0) | (lit ? 16 : 0) | (turning ? 32 : 0));
            t.engineSpool[i] = (float) u.engine.spool;
            t.engineTemp[i] = (float) u.engine.temperatureC;
            t.engineDamage[i] = (float) u.engine.damage;
            t.engineThrust[i] = (float) (u.lastThrust + u.engine.shaftPowerW / 100.0);
            t.propOmega[i] = (float) u.propOmega;
            t.engineAb[i] = u.engine.afterburner && u.engine.type.afterburnerMultiplier > 1 && u.engine.throttle > 0.95 && u.engine.isRunning();
        }
        int nr = s.rotors.size();
        if (t.rotorOmega.length != nr) t.rotorOmega = new float[nr];
        for (int i = 0; i < nr; i++) {
            RotorUnit r = s.rotors.get(i);
            t.rotorOmega[i] = (float) (r.model.omega * r.spinDirection);
        }
        int nw = s.wheels.size();
        if (t.wheelOmega.length != nw) {
            t.wheelOmega = new float[nw]; t.wheelSteer = new float[nw]; t.wheelComp = new float[nw]; t.wheelExt = new float[nw]; t.wheelSkid = new boolean[nw];
        }
        for (int i = 0; i < nw; i++) {
            WheelSim w = s.wheels.get(i);
            t.wheelOmega[i] = (float) w.omega;
            t.wheelSteer[i] = (float) w.steerAngle;
            t.wheelComp[i] = (float) w.compression;
            t.wheelExt[i] = (float) w.extension;
            t.wheelSkid[i] = w.skidding && w.grounded;
        }
        weapons.fillTelemetry(t);
        if (t.engineKeys.length != ne || t.wheelKeys.length != nw || t.rotorKeys.length != nr || t.weaponKeys.length != weapons.slots().size() || tickCount % 40 == 0) {
            t.engineKeys = new long[ne];
            t.deviceKeys = new long[ne];
            for (int i = 0; i < ne; i++) { t.engineKeys[i] = s.thrusters.get(i).voxel; t.deviceKeys[i] = s.thrusters.get(i).deviceVoxel; }
            t.rotorKeys = new long[nr];
            for (int i = 0; i < nr; i++) t.rotorKeys[i] = s.rotors.get(i).voxel;
            t.wheelKeys = new long[nw];
            for (int i = 0; i < nw; i++) t.wheelKeys[i] = s.wheels.get(i).voxel;
            t.weaponKeys = weapons.slots().stream().mapToLong(w -> w.def.pos).toArray();
        }
    }

    public float integrity() {
        if (layout == null || layout.blockCount == 0) return 1;
        double dmgFrac = 0;
        for (var e : structure.damageMap().long2FloatEntrySet()) {
            BlockState st = structure.get(e.getLongKey());
            dmgFrac += Math.min(1, e.getFloatValue() / BlockPhysics.hitPoints(st));
        }
        double missing = Math.max(0, layout.blockCount - structure.size());
        return (float) Math.max(0, 1 - (dmgFrac + missing) / Math.max(1, layout.blockCount));
    }

    // ------------------------------------------------------------------ vanilla behaviour

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // pose is driven by VehicleWorks state packets, not vanilla movement sync
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return !isRemoved() && !structure.isEmpty();
    }

    @Override
    public void remove(RemovalReason reason) {
        clearGroundLights();
        super.remove(reason);
        if (layout != null && level() instanceof ServerLevel sl) VehiclePhysicsManager.get(sl).forget(layout.sim);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        VehicleRegistry.add(this);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        VehicleRegistry.remove(this);
        if (level().isClientSide) com.vehicleworks.VehicleWorks.PROXY.disposeClientState(this);
    }

    // ------------------------------------------------------------------ persistence & spawn data

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        cabin.clear();
        long[] ck = tag.getLongArray("CabinKeys");
        int[] cs = tag.getIntArray("CabinStates");
        for (int i = 0; i < ck.length && i < cs.length; i++) cabin.put(ck[i], cs[i]);
        structure.load(tag.getCompound("Structure"), level());
        CompoundTag p = tag.getCompound("Pose");
        orientation.set(p.getDouble("qx"), p.getDouble("qy"), p.getDouble("qz"), p.contains("qw") ? p.getDouble("qw") : 1).normalize();
        prevOrientation.set(orientation);
        comLocal.set(p.getDouble("cx"), p.getDouble("cy"), p.getDouble("cz"));
        linearVelocity.set(p.getDouble("vx"), p.getDouble("vy"), p.getDouble("vz"));
        angularVelocity.set(p.getDouble("wx"), p.getDouble("wy"), p.getDouble("wz"));
        controls.load(tag.getCompound("Controls"));
        layout = null;
        setBoundingBox(makeBoundingBox());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLongArray("CabinKeys", cabin.keySet().toLongArray());
        tag.putIntArray("CabinStates", cabin.values().toIntArray());
        writeRuntimeToStructure();
        tag.put("Structure", structure.save());
        CompoundTag p = new CompoundTag();
        p.putDouble("qx", orientation.x); p.putDouble("qy", orientation.y); p.putDouble("qz", orientation.z); p.putDouble("qw", orientation.w);
        p.putDouble("cx", comLocal.x); p.putDouble("cy", comLocal.y); p.putDouble("cz", comLocal.z);
        p.putDouble("vx", linearVelocity.x); p.putDouble("vy", linearVelocity.y); p.putDouble("vz", linearVelocity.z);
        p.putDouble("wx", angularVelocity.x); p.putDouble("wy", angularVelocity.y); p.putDouble("wz", angularVelocity.z);
        tag.put("Pose", p);
        tag.put("Controls", controls.save());
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        structure.write(buf);
        buf.writeDouble(orientation.x); buf.writeDouble(orientation.y); buf.writeDouble(orientation.z); buf.writeDouble(orientation.w);
        buf.writeDouble(comLocal.x); buf.writeDouble(comLocal.y); buf.writeDouble(comLocal.z);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        structure.read(buf);
        orientation.set(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()).normalize();
        prevOrientation.set(orientation);
        comLocal.set(buf.readDouble(), buf.readDouble(), buf.readDouble());
        setBoundingBox(makeBoundingBox());
    }

    public void addInfoViewer() {
        infoViewers = 40;
    }
}
