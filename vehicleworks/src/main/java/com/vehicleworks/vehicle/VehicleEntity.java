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

    /** World position of a structure-local point using the current tick pose. */
    public Vector3d localToWorld(double lx, double ly, double lz, Vector3d out) {
        out.set(lx - comLocal.x, ly - comLocal.y, lz - comLocal.z);
        orientation.transform(out);
        return out.add(getX(), getY(), getZ());
    }

    public Vector3d worldToLocal(double wx, double wy, double wz, Vector3d out) {
        out.set(wx - getX(), wy - getY(), wz - getZ());
        orientation.transformInverse(out);
        return out.add(comLocal);
    }

    public Vector3d localDirToWorld(double x, double y, double z, Vector3d out) {
        out.set(x, y, z);
        return orientation.transform(out);
    }

    public Vector3d worldDirToLocal(double x, double y, double z, Vector3d out) {
        out.set(x, y, z);
        return orientation.transformInverse(out);
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
        sim.fuelScale = Config.SERVER.fuelConsumptionScale.get();
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
        int n = (int) Math.round((kmh - 30) / 3.0 * Config.SERVER.crashDamageScale.get());
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
            float d = structure.damage(key) + e.getFloatValue();
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

    public boolean trySit(ServerPlayer player, long seatPos) {
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

    public Long seatOf(Entity e) {
        return seats.get(e.getUUID());
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
        t.stickPitch = (float) s.effPitch;
        t.stickRoll = (float) s.effRoll;
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
            t.engineState = new byte[ne]; t.engineType = new byte[ne];
            t.engineSpool = new float[ne]; t.engineTemp = new float[ne]; t.engineDamage = new float[ne];
            t.engineThrust = new float[ne]; t.propOmega = new float[ne]; t.engineAb = new boolean[ne];
        }
        for (int i = 0; i < ne; i++) {
            ThrusterUnit u = s.thrusters.get(i);
            t.engineState[i] = (byte) u.engine.state.ordinal();
            t.engineType[i] = (byte) u.engine.type.ordinal();
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
