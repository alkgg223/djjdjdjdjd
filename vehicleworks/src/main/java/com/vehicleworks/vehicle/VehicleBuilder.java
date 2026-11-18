package com.vehicleworks.vehicle;

import com.vehicleworks.Config;
import com.vehicleworks.block.ComponentBlock;
import com.vehicleworks.block.ComponentBlockEntity;
import com.vehicleworks.block.WingOrientation;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.component.WingConfig;
import com.vehicleworks.physics.CollisionShape;
import com.vehicleworks.physics.VoxelSet;
import com.vehicleworks.physics.aero.AeroModel;
import com.vehicleworks.physics.aero.ControlRole;
import com.vehicleworks.physics.aero.WingParams;
import com.vehicleworks.physics.aero.WingStrip;
import com.vehicleworks.physics.fluid.FluidElements;
import com.vehicleworks.physics.fluid.StructureAnalysis;
import com.vehicleworks.physics.ground.WheelParams;
import com.vehicleworks.physics.ground.WheelSim;
import com.vehicleworks.physics.propulsion.EngineSim;
import com.vehicleworks.physics.propulsion.EngineType;
import com.vehicleworks.physics.propulsion.PropellerModel;
import com.vehicleworks.physics.sim.RotorUnit;
import com.vehicleworks.physics.sim.TankUnit;
import com.vehicleworks.physics.sim.ThrusterUnit;
import com.vehicleworks.physics.sim.VehicleSimulation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

import java.util.*;

/**
 * Converts a {@link VehicleStructure} into a {@link VehicleSimulation} and component index.
 * Pure function of the (immutable) structure snapshot, so it can run on a worker thread.
 */
public final class VehicleBuilder {
    public static final double FUEL_TANK_CAPACITY = 300.0;
    public static final double BALLAST_CAPACITY = 1000.0;

    private VehicleBuilder() {}

    public static ComponentType typeOf(BlockState s) {
        return s.getBlock() instanceof ComponentBlock cb ? cb.type : null;
    }

    public static CompoundTag config(VehicleStructure s, long key) {
        CompoundTag be = s.blockEntity(key);
        return be == null ? new CompoundTag() : be.getCompound(ComponentBlockEntity.CONFIG);
    }

    public static CompoundTag data(VehicleStructure s, long key) {
        CompoundTag be = s.blockEntity(key);
        return be == null ? new CompoundTag() : be.getCompound(ComponentBlockEntity.DATA);
    }

    public static Direction facing(BlockState s) {
        if (s.hasProperty(ComponentBlock.FACING)) return s.getValue(ComponentBlock.FACING);
        if (s.hasProperty(ComponentBlock.HORIZONTAL_FACING)) return s.getValue(ComponentBlock.HORIZONTAL_FACING);
        return Direction.NORTH;
    }

    private static Vector3d vec(Direction d) {
        return new Vector3d(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private static double axisCoord(Direction.Axis a, double x, double y, double z) {
        return a == Direction.Axis.X ? x : (a == Direction.Axis.Y ? y : z);
    }

    public static VehicleLayout build(VehicleStructure s) {
        VehicleSimulation sim = new VehicleSimulation();
        VehicleLayout layout = new VehicleLayout(sim, s.version);
        // ---- orientation from the core
        BlockState core = s.get(0, 0, 0);
        Direction fwdDir = typeOf(core) == ComponentType.VEHICLE_CORE ? facing(core) : Direction.NORTH;
        if (fwdDir.getAxis() == Direction.Axis.Y) fwdDir = Direction.NORTH;
        layout.forwardDir = fwdDir;
        Vector3d fwd = vec(fwdDir);
        Vector3d up = new Vector3d(0, 1, 0);
        Vector3d right = new Vector3d(fwd).cross(up);
        sim.forward.set(fwd);
        sim.up.set(up);
        sim.right.set(right);
        layout.forward.set(fwd);
        layout.right.set(right);
        Direction.Axis fwdAxis = fwdDir.getAxis();

        CollisionShape.Builder cb = new CollisionShape.Builder();
        VoxelSet flags = new VoxelSet(Math.max(16, s.size()));
        List<Long> wings = new ArrayList<>();
        List<Long> engines = new ArrayList<>();
        List<Long> rotors = new ArrayList<>();
        List<Long> wheels = new ArrayList<>();
        List<Long> gasCells = new ArrayList<>();
        int pumps = 0, starters = 0, hydraulics = 0, burners = 0;
        double burnerScale = 0;
        double mass = 0;

        for (var e : s.blocks().long2ObjectEntrySet()) {
            long key = e.getLongKey();
            BlockState st = e.getValue();
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            ComponentType t = typeOf(st);
            double m = BlockPhysics.mass(st);
            mass += m;
            sim.baseMass.addBox(m, x + 0.5, y + 0.5, z + 0.5, 1, 1, 1);
            int f = 0;
            float mu = BlockPhysics.friction(st);
            boolean collides = t == null || !t.excludedFromCollision();
            if (collides) {
                VoxelShape shape = st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (!shape.isEmpty()) {
                    List<AABB> boxes = shape.toAabbs();
                    double vol = 0;
                    for (AABB b : boxes) vol += b.getXsize() * b.getYsize() * b.getZsize();
                    if (boxes.size() == 1 && vol > 0.999) {
                        cb.addFullVoxel(x, y, z, mu);
                    } else {
                        for (AABB b : boxes) cb.addPartialBox(x, y, z, x + b.minX, y + b.minY, z + b.minZ, x + b.maxX, y + b.maxY, z + b.maxZ, mu);
                    }
                    if (vol >= 0.45) f |= StructureAnalysis.FLAG_SOLID;
                }
            }
            if (t != null) {
                layout.types.add(t);
                switch (t) {
                    case BALLOON_ENVELOPE -> f |= StructureAnalysis.FLAG_ENVELOPE | StructureAnalysis.FLAG_SOLID;
                    case GAS_CELL -> gasCells.add(key);
                    case PILOT_SEAT -> layout.seats.add(new VehicleLayout.Seat(key, facing(st), true, false));
                    case PASSENGER_SEAT -> layout.seats.add(new VehicleLayout.Seat(key, facing(st), false,
                            t.property("gunner").getBool(config(s, key))));
                    case FUEL_PUMP -> pumps++;
                    case ENGINE_STARTER -> starters++;
                    case HYDRAULIC_PUMP -> hydraulics++;
                    case BURNER -> { burners++; burnerScale += t.property("power").get(config(s, key)); }
                    case FLIGHT_INSTRUMENTS -> layout.instruments = true;
                    case NAVIGATION_COMPUTER -> layout.navComputer = key;
                    case AMMO_BOX -> layout.ammoBoxes.add(key);
                    case AIRCRAFT_LIGHT -> layout.lights.add(key);
                    case REMOTE_CONTROL_UNIT -> layout.rcus.add(key);
                    case MAIN_ROTOR, TAIL_ROTOR -> rotors.add(key);
                    case WHEEL, LANDING_GEAR, HEAVY_LANDING_GEAR, DUAL_WHEEL_GEAR, BOGIE_GEAR, REINFORCED_GEAR, TUNDRA_GEAR, PERFORMANCE_GEAR -> wheels.add(key);
                    case FUEL_TANK -> {
                        TankUnit tu = new TankUnit(TankUnit.Kind.FUEL, FUEL_TANK_CAPACITY);
                        tu.amount = Math.min(FUEL_TANK_CAPACITY, data(s, key).getDouble("Fuel"));
                        tu.fuelType = data(s, key).getInt("FuelType");
                        tu.side = Integer.signum(x);
                        tu.localPos.set(x + 0.5, y + 0.5, z + 0.5);
                        tu.voxel = key;
                        layout.tankIndex.put(key, sim.tanks.size());
                        sim.tanks.add(tu);
                    }
                    case BALLAST_TANK -> {
                        TankUnit tu = new TankUnit(TankUnit.Kind.BALLAST, BALLAST_CAPACITY);
                        tu.amount = Math.min(BALLAST_CAPACITY, data(s, key).getDouble("Water"));
                        tu.localPos.set(x + 0.5, y + 0.5, z + 0.5);
                        tu.voxel = key;
                        layout.tankIndex.put(key, sim.tanks.size());
                        sim.tanks.add(tu);
                    }
                    default -> {}
                }
                if (t.isWing()) {
                    WingOrientation o = st.getValue(ComponentBlock.PLANE);
                    if (o.normal != fwdAxis) {
                        wings.add(key);
                        f |= StructureAnalysis.FLAG_NO_BODY_DRAG;
                    }
                }
                if (t.isEngine()) engines.add(key);
                if (t.isDynamicRender()) {
                    layout.dynamicBlocks.add(key);
                    f |= StructureAnalysis.FLAG_NO_BODY_DRAG;
                }
                if (t.isWeapon()) {
                    boolean mounted = false;
                    long turret = Long.MIN_VALUE;
                    for (Direction d : Direction.values()) {
                        long nk = BlockPos.offset(key, d);
                        ComponentType nt = typeOf(s.get(nk));
                        if (nt == ComponentType.WEAPON_MOUNT) mounted = true;
                        if (nt == ComponentType.TURRET_BASE) { mounted = true; turret = nk; }
                    }
                    layout.weapons.add(new VehicleLayout.WeaponSlot(key, t, facing(st), mounted, turret, layout.weapons.size()));
                }
            }
            if (f != 0) flags.put(x, y, z, f);
        }
        layout.blockCount = s.size();
        layout.dryMass = mass;
        layout.sizeX = s.maxX() - s.minX() + 1;
        layout.sizeY = s.maxY() - s.minY() + 1;
        layout.sizeZ = s.maxZ() - s.minZ() + 1;

        sim.shape = cb.build();
        StructureAnalysis analysis = new StructureAnalysis(flags, 6_000_000);
        FluidElements fluid = new FluidElements(analysis, flags);
        int envelopePockets = fluid.gasPockets.size();
        for (long key : gasCells) {
            var p = new StructureAnalysis.Pocket(new long[]{VoxelSet.pack(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key))}, true, Integer.MAX_VALUE, new long[0]);
            p.envelopeFraction = 1;
            fluid.gasPockets.add(p);
        }
        sim.fluid = fluid;
        sim.gasHelium = new boolean[fluid.gasPockets.size()];
        for (int i = 0; i < sim.gasHelium.length; i++) sim.gasHelium[i] = i >= envelopePockets || burners == 0;
        for (var p : fluid.gasPockets) layout.gasVolume += p.volume;
        sim.burners = burners;
        sim.burnerPowerW = 2.5e6 * (burners > 0 ? burnerScale / burners : 1);
        sim.hotAir = burners > 0;
        sim.gasLiftScale = Config.SERVER.gasLiftScale.get();
        sim.fuelPumps = pumps;
        sim.hasStarter = starters > 0;
        sim.hydraulicPumps = hydraulics;

        // ---- engines (thrusters); mass must exist first for COM-relative decisions later
        boolean hasMainRotor = false;
        for (long k : rotors) if (typeOf(s.get(k)) == ComponentType.MAIN_ROTOR) hasMainRotor = true;
        boolean anyDrivenWheel = false;
        for (long k : wheels) {
            ComponentType wt = typeOf(s.get(k));
            int drive = wt.property("drive").getInt(config(s, k));
            if (drive == 1 || (drive == 0 && wt == ComponentType.WHEEL)) anyDrivenWheel = true;
        }
        for (long key : engines) {
            BlockState st = s.get(key);
            ComponentType t = typeOf(st);
            EngineType et = t.engineType();
            CompoundTag c = config(s, key);
            Direction d = facing(st);
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            ThrusterUnit.Device device;
            long deviceKey = key;
            long front = BlockPos.offset(key, d), back = BlockPos.offset(key, d.getOpposite());
            ComponentType ft = typeOf(s.get(front)), bt = typeOf(s.get(back));
            if (!et.isShaft()) device = ThrusterUnit.Device.JET;
            else if (ft == ComponentType.PROPELLER) { device = ThrusterUnit.Device.PROPELLER; deviceKey = front; }
            else if (bt == ComponentType.PROPELLER) { device = ThrusterUnit.Device.PROPELLER; deviceKey = back; }
            else if (ft == ComponentType.MARINE_PROPELLER) { device = ThrusterUnit.Device.MARINE_PROPELLER; deviceKey = front; }
            else if (bt == ComponentType.MARINE_PROPELLER) { device = ThrusterUnit.Device.MARINE_PROPELLER; deviceKey = back; }
            else if (hasMainRotor) device = ThrusterUnit.Device.ROTOR;
            else device = ThrusterUnit.Device.DRIVETRAIN;
            EngineSim es = new EngineSim(et, key * 31 + 7);
            es.powerScale = t.property("power_scale").get(c);
            es.throttleGroup = t.property("throttle_group").getInt(c);
            es.enabled = t.property("enabled").getBool(c);
            CompoundTag dt = data(s, key);
            es.damage = Math.min(1, dt.getDouble("Damage"));
            es.temperatureC = dt.contains("Temp") ? dt.getDouble("Temp") : 15;
            ThrusterUnit tu = new ThrusterUnit(es, device);
            tu.localPos.set(x + 0.5, y + 0.5, z + 0.5);
            tu.localDir.set(d.getStepX(), d.getStepY(), d.getStepZ());
            tu.devicePos.set(BlockPos.getX(deviceKey) + 0.5, BlockPos.getY(deviceKey) + 0.5, BlockPos.getZ(deviceKey) + 0.5);
            tu.voxel = key;
            tu.deviceVoxel = deviceKey;
            if (device == ThrusterUnit.Device.PROPELLER || device == ThrusterUnit.Device.MARINE_PROPELLER) {
                ComponentType pt = typeOf(s.get(deviceKey));
                CompoundTag pc = config(s, deviceKey);
                tu.propDiameter = pt.property("diameter").get(pc);
                tu.propEfficiency = pt.property("efficiency").get(pc);
                double rho = device == ThrusterUnit.Device.PROPELLER ? 1.225 : 1000;
                layout.maxStaticThrust += PropellerModel.thrust(et.maxPowerW * es.powerScale, 0, rho, tu.propDiameter, tu.propEfficiency);
            } else if (device == ThrusterUnit.Device.JET) {
                layout.maxStaticThrust += et.maxThrustN * es.powerScale;
            }
            layout.thrusterIndex.put(key, sim.thrusters.size());
            sim.thrusters.add(tu);
        }

        // ---- rotors
        for (long key : rotors) {
            BlockState st = s.get(key);
            ComponentType t = typeOf(st);
            CompoundTag c = config(s, key);
            Direction d = facing(st);
            RotorUnit r = new RotorUnit();
            r.localPos.set(BlockPos.getX(key) + 0.5, BlockPos.getY(key) + 0.5, BlockPos.getZ(key) + 0.5);
            r.axisLocal.set(d.getStepX(), d.getStepY(), d.getStepZ());
            r.voxel = key;
            r.model.radius = t.property("radius").get(c);
            r.model.blades = t.property("blades").getInt(c);
            if (t == ComponentType.MAIN_ROTOR) {
                r.model.chord = t.property("chord").get(c);
                r.model.maxCollectiveRad = Math.toRadians(t.property("max_collective").get(c));
                r.hubStiffness = t.property("hub_stiffness").get(c);
                r.spinDirection = t.property("spin").getInt(c) == 0 ? 1 : -1;
                r.model.inertia = 25.0 * r.model.blades * r.model.radius * r.model.radius;
            } else {
                r.tail = true;
                r.model.chord = 0.18 * Math.max(0.6, r.model.radius);
                r.model.inertia = 4.0 * r.model.blades * r.model.radius * r.model.radius;
            }
            layout.rotorIndex.put(key, sim.rotors.size());
            sim.rotors.add(r);
        }

        // ---- mass properties (COM needed for wings / wheels)
        sim.body.position.zero();
        sim.rebuildMass();
        Vector3d com = sim.com;
        layout.comFwd = com.dot(fwd);

        // ---- wings: strips grouped by span station, panels grouped for aspect ratio
        Map<String, List<Long>> stripGroups = new LinkedHashMap<>();
        Map<String, double[]> wingGroups = new HashMap<>(); // key → {minSpan, maxSpan, area}
        for (long key : wings) {
            BlockState st = s.get(key);
            ComponentType t = typeOf(st);
            WingOrientation o = st.getValue(ComponentBlock.PLANE);
            Direction.Axis spanAxis = otherAxis(o.normal, fwdAxis);
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            int nCoord = (int) axisCoord(o.normal, x, y, z);
            int sCoord = (int) axisCoord(spanAxis, x, y, z);
            CompoundTag c = config(s, key);
            String sk = o + "|" + nCoord + "|" + sCoord + "|" + t.id + "|" + c.hashCode();
            stripGroups.computeIfAbsent(sk, k -> new ArrayList<>()).add(key);
            String gk = o + "|" + nCoord;
            double chord = t.property("chord").get(c);
            double[] g = wingGroups.computeIfAbsent(gk, k -> new double[]{Double.MAX_VALUE, -Double.MAX_VALUE, 0});
            g[0] = Math.min(g[0], sCoord);
            g[1] = Math.max(g[1], sCoord + 1);
            g[2] += chord;
        }
        double npNum = 0, npDen = 0, chordSum = 0;
        int chordCount = 0;
        AeroModel probe = new AeroModel();
        for (var entry : stripGroups.entrySet()) {
            List<Long> keys = entry.getValue();
            long k0 = keys.get(0);
            BlockState st = s.get(k0);
            ComponentType t = typeOf(st);
            WingOrientation o = st.getValue(ComponentBlock.PLANE);
            CompoundTag c = config(s, k0);
            WingParams params = WingConfig.toParams(t, c);
            WingStrip strip = new WingStrip(params);
            double cx = 0, cy = 0, cz = 0, area = 0;
            for (long k : keys) {
                cx += BlockPos.getX(k) + 0.5;
                cy += BlockPos.getY(k) + 0.5;
                cz += BlockPos.getZ(k) + 0.5;
                area += params.chord;
            }
            strip.localCenter.set(cx / keys.size(), cy / keys.size(), cz / keys.size());
            strip.forward.set(fwd);
            strip.normal.set(o.normal == Direction.Axis.X ? 1 : 0, o.normal == Direction.Axis.Y ? 1 : 0, o.normal == Direction.Axis.Z ? 1 : 0);
            strip.area = area;
            strip.blocks = keys.size();
            strip.voxels = keys.stream().mapToLong(Long::longValue).toArray();
            int nCoord = (int) axisCoord(o.normal, BlockPos.getX(k0), BlockPos.getY(k0), BlockPos.getZ(k0));
            double[] g = wingGroups.get(o + "|" + nCoord);
            double span = g[1] - g[0];
            strip.span = span;
            strip.aspectRatio = Math.max(0.5, span * span / Math.max(0.25, g[2]));
            int idx = sim.wings.size();
            for (long k : keys) layout.wingStripOfBlock.put(k, idx);
            sim.wings.add(strip);
            layout.wingArea += o == WingOrientation.HORIZONTAL ? area : 0;
            if (o == WingOrientation.HORIZONTAL) {
                probe.compute(params, Math.toRadians(2), 0, 0, 0, strip.aspectRatio, Math.toRadians(params.sweepDeg), 0.2, 99);
                double cl2 = probe.cl;
                probe.compute(params, Math.toRadians(-2), 0, 0, 0, strip.aspectRatio, Math.toRadians(params.sweepDeg), 0.2, 99);
                double slope = (cl2 - probe.cl) / Math.toRadians(4);
                double w = slope * area;
                // aerodynamic centre at the quarter chord ahead of the strip centre
                double posFwd = strip.localCenter.dot(fwd) + 0.25 * params.chord * keys.size() * 0.5;
                npNum += w * posFwd;
                npDen += w;
                chordSum += params.chord * keys.size();
                chordCount++;
            }
        }
        if (npDen > 0) {
            layout.neutralPointFwd = npNum / npDen;
            layout.meanChord = chordCount > 0 ? chordSum / chordCount : 1;
            layout.staticMargin = (layout.comFwd - layout.neutralPointFwd) / Math.max(0.5, layout.meanChord);
        }

        // ---- wheels (suspension tuned to the vehicle mass)
        double totalMass = sim.mass();
        int nWheels = Math.max(1, wheels.size());
        for (long key : wheels) {
            BlockState st = s.get(key);
            ComponentType t = typeOf(st);
            CompoundTag c = config(s, key);
            WheelParams p = new WheelParams();
            p.radius = t.property("radius").get(c);
            p.restLength = t.property("travel").get(c);
            double load = totalMass * 9.81 / nWheels;
            p.stiffness = t.property("suspension").get(c) * load / (0.35 * p.restLength);
            p.damping = t.property("damping").get(c) * 2 * 0.45 * Math.sqrt(p.stiffness * totalMass / nWheels);
            double posFwd = (BlockPos.getX(key) + 0.5) * fwd.x + (BlockPos.getY(key) + 0.5) * fwd.y + (BlockPos.getZ(key) + 0.5) * fwd.z;
            boolean front = posFwd > layout.comFwd;
            p.axleSign = front ? 1 : -1;
            int steer = t.property("steering").getInt(c);
            p.steerable = steer == 1 || (steer == 0 && front);
            p.maxSteerDeg = t.property("max_steer").get(c);
            int drive = t.property("drive").getInt(c);
            p.driven = drive == 1 || (drive == 0 && t == ComponentType.WHEEL && !front);
            p.braked = t.property("braked").getBool(c);
            p.brakeTorque = t.property("brake_torque").get(c) * Math.max(1, totalMass / 1500.0 / nWheels * 4);
            p.gripScale = t.property("grip").get(c);
            p.retractable = t.property("retractable").getBool(c);
            p.mass = 20 + 60 * p.radius;
            // per-type tyre physics: rolling resistance, rough-ground behaviour, rated load, speed rating
            switch (t) {
                case LANDING_GEAR -> { p.rollingResistance = 0.015; p.roughSensitivity = 1.0; p.loadCapacity = 120e3; p.maxSpeed = 95; }
                case DUAL_WHEEL_GEAR -> { p.rollingResistance = 0.016; p.roughSensitivity = 0.9; p.loadCapacity = 350e3; p.maxSpeed = 100; p.mass = 160; }
                case BOGIE_GEAR -> { p.rollingResistance = 0.018; p.roughSensitivity = 0.8; p.loadCapacity = 900e3; p.maxSpeed = 105; p.mass = 400; }
                case REINFORCED_GEAR -> { p.rollingResistance = 0.017; p.roughSensitivity = 0.7; p.loadCapacity = 550e3; p.maxSpeed = 100; p.mass = 220; }
                case TUNDRA_GEAR -> { p.rollingResistance = 0.022; p.roughSensitivity = 0.15; p.loadCapacity = 60e3; p.maxSpeed = 55; p.mass = 70; }
                case PERFORMANCE_GEAR -> { p.rollingResistance = 0.010; p.roughSensitivity = 1.6; p.loadCapacity = 150e3; p.maxSpeed = 140; p.retractRate = 2.2; p.mass = 40; }
                case HEAVY_LANDING_GEAR -> { p.rollingResistance = 0.018; p.roughSensitivity = 0.6; p.loadCapacity = 1200e3; p.maxSpeed = 100; }
                default -> { p.rollingResistance = 0.014; p.roughSensitivity = 1.0; }
            }
            p.sanitize();
            WheelSim w = new WheelSim(p);
            w.mountLocal.set(BlockPos.getX(key) + 0.5, BlockPos.getY(key) + 0.5, BlockPos.getZ(key) + 0.5);
            w.downLocal.set(0, -1, 0);
            w.forwardLocal.set(fwd);
            w.voxel = key;
            layout.wheelIndex.put(key, sim.wheels.size());
            sim.wheels.add(w);
        }
        // a car whose wheels are all "auto" but none behind the COM: drive all wheels
        if (!anyDrivenWheel || sim.wheels.stream().noneMatch(w -> w.params.driven)) {
            boolean carLike = false;
            for (long k : wheels) if (typeOf(s.get(k)) == ComponentType.WHEEL) carLike = true;
            if (carLike) for (WheelSim w : sim.wheels) if (typeOf(s.get(w.voxel)) == ComponentType.WHEEL && config(s, w.voxel).getInt("drive") == 0) w.params.driven = true;
        }

        // ---- rotor offset from the COM (helicopter balance indicator)
        for (RotorUnit r : sim.rotors) {
            if (r.tail) continue;
            Vector3d d = new Vector3d(r.localPos).sub(com);
            d.sub(new Vector3d(r.axisLocal).mul(d.dot(r.axisLocal)));
            layout.rotorOffset = d.length();
        }

        sim.initControlAxes();
        if (layout.wingArea > 0) sim.rotateSpeed = 1.2 * Math.sqrt(2 * totalMass * 9.81 / (1.225 * layout.wingArea * 1.3));

        // ---- profile detection
        double weight = totalMass * 9.81;
        double gasLiftEstimate = layout.gasVolume * 1.0 * 9.81 * sim.gasLiftScale * (sim.hotAir ? 0.3 : 1.0);
        boolean hasThrust = sim.thrusters.stream().anyMatch(tu -> tu.device == ThrusterUnit.Device.JET || tu.device == ThrusterUnit.Device.PROPELLER);
        boolean marine = sim.thrusters.stream().anyMatch(tu -> tu.device == ThrusterUnit.Device.MARINE_PROPELLER);
        boolean ballast = sim.tanks.stream().anyMatch(tu -> tu.kind == TankUnit.Kind.BALLAST);
        if (hasMainRotor) layout.detectedProfile = ControlProfile.HELICOPTER;
        else if (gasLiftEstimate > 0.3 * weight) layout.detectedProfile = ControlProfile.AIRSHIP;
        else if (ballast) layout.detectedProfile = ControlProfile.SUBMARINE;
        else if (layout.wingArea > 0 && hasThrust) layout.detectedProfile = ControlProfile.AIRCRAFT;
        else if (marine) layout.detectedProfile = ControlProfile.SHIP;
        else if (!sim.wheels.isEmpty()) layout.detectedProfile = ControlProfile.CAR;
        else if (layout.wingArea > 0) layout.detectedProfile = ControlProfile.AIRCRAFT;
        else layout.detectedProfile = ControlProfile.SHIP;

        // ---- top-down mass distribution grid (right × forward)
        buildMassGrid(s, layout, fwd, right);
        return layout;
    }

    private static Direction.Axis otherAxis(Direction.Axis a, Direction.Axis b) {
        for (Direction.Axis x : Direction.Axis.values()) if (x != a && x != b) return x;
        return Direction.Axis.Y;
    }

    private static void buildMassGrid(VehicleStructure s, VehicleLayout layout, Vector3d fwd, Vector3d right) {
        double minR = Double.MAX_VALUE, maxR = -Double.MAX_VALUE, minF = Double.MAX_VALUE, maxF = -Double.MAX_VALUE;
        for (long k : s.blocks().keySet()) {
            double x = BlockPos.getX(k) + 0.5, y = BlockPos.getY(k) + 0.5, z = BlockPos.getZ(k) + 0.5;
            double r = x * right.x + y * right.y + z * right.z, f = x * fwd.x + y * fwd.y + z * fwd.z;
            minR = Math.min(minR, r); maxR = Math.max(maxR, r); minF = Math.min(minF, f); maxF = Math.max(maxF, f);
        }
        if (minR == Double.MAX_VALUE) return;
        double extent = Math.max(maxR - minR, maxF - minF) + 1;
        double cell = Math.max(1, Math.ceil(extent / 40.0));
        int w = (int) Math.ceil((maxR - minR + 1) / cell), h = (int) Math.ceil((maxF - minF + 1) / cell);
        double[] grid = new double[Math.max(1, w * h)];
        for (var e : s.blocks().long2ObjectEntrySet()) {
            long k = e.getLongKey();
            double x = BlockPos.getX(k) + 0.5, y = BlockPos.getY(k) + 0.5, z = BlockPos.getZ(k) + 0.5;
            int gi = (int) ((x * right.x + y * right.y + z * right.z - minR + 0.5) / cell);
            int gj = (int) ((x * fwd.x + y * fwd.y + z * fwd.z - minF + 0.5) / cell);
            gi = Math.max(0, Math.min(w - 1, gi));
            gj = Math.max(0, Math.min(h - 1, gj));
            grid[gj * w + gi] += BlockPhysics.mass(e.getValue());
        }
        layout.massGrid = grid;
        layout.gridW = w;
        layout.gridH = h;
        layout.gridCell = cell;
        layout.gridMinRight = minR - 0.5;
        layout.gridMinFwd = minF - 0.5;
    }

    /** Distinguishes control roles for statistics. */
    public static boolean isControlSurface(WingStrip s) {
        return s.params.role != ControlRole.NONE;
    }
}
