package com.vehicleworks.vehicle;

import com.vehicleworks.physics.aero.AeroModel;
import com.vehicleworks.physics.aero.Atmosphere;
import com.vehicleworks.physics.aero.WingStrip;
import com.vehicleworks.physics.sim.TankUnit;
import com.vehicleworks.physics.sim.ThrusterUnit;
import com.vehicleworks.physics.sim.VehicleSimulation;
import com.vehicleworks.weapon.WeaponSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import org.joml.Matrix3d;

/** Builds the data shown by the engineering interface (sent on request while the screen is open). */
public final class EngineeringInfo {
    private EngineeringInfo() {}

    public static CompoundTag build(VehicleEntity v) {
        CompoundTag t = new CompoundTag();
        VehicleLayout l = v.layout();
        if (l == null) return t;
        VehicleSimulation s = l.sim;
        t.putInt("Blocks", v.structure.size());
        t.putDouble("Mass", s.mass());
        t.putDouble("DryMass", l.dryMass);
        t.putDouble("ComX", s.com.x); t.putDouble("ComY", s.com.y); t.putDouble("ComZ", s.com.z);
        Matrix3d in = s.body.inertiaLocal;
        t.putDouble("Ixx", in.m00); t.putDouble("Iyy", in.m11); t.putDouble("Izz", in.m22);
        t.putDouble("SizeX", l.sizeX); t.putDouble("SizeY", l.sizeY); t.putDouble("SizeZ", l.sizeZ);
        t.putInt("Profile", v.profile().ordinal());
        t.putInt("Detected", l.detectedProfile.ordinal());
        t.putInt("Forward", l.forwardDir.get3DDataValue());
        double weight = s.mass() * 9.81;
        t.putDouble("WingArea", l.wingArea);
        t.putDouble("WingLoading", l.wingArea > 0 ? s.mass() / l.wingArea : 0);
        t.putDouble("StaticMargin", l.staticMargin);
        t.putDouble("NeutralPoint", l.neutralPointFwd);
        t.putDouble("ComFwd", l.comFwd);
        t.putDouble("MeanChord", l.meanChord);
        // stall speed estimate from the best wing CLmax
        double clMax = 0, area = 0;
        AeroModel m = new AeroModel();
        for (WingStrip w : s.wings) {
            if (Math.abs(w.normal.y) < 0.5) continue;
            double best = 0;
            for (int a = 0; a <= 30; a++) {
                m.compute(w.params, Math.toRadians(a + w.params.incidenceDeg), 0, Math.toRadians(w.params.flapMaxDeg) * (w.params.role.flap ? 1 : 0), 0,
                        w.aspectRatio, Math.toRadians(w.params.sweepDeg), 0.15, 99);
                best = Math.max(best, m.cl);
            }
            clMax += best * w.area;
            area += w.area;
        }
        if (area > 0) {
            double clm = clMax / area;
            t.putDouble("ClMax", clm);
            t.putDouble("StallSpeed", Math.sqrt(2 * weight / (Atmosphere.RHO0 * area * Math.max(0.1, clm))));
        }
        t.putDouble("StaticThrust", l.maxStaticThrust);
        t.putDouble("ThrustToWeight", weight > 0 ? l.maxStaticThrust / weight : 0);
        t.putDouble("RotorOffset", l.rotorOffset);
        t.putDouble("GasVolume", l.gasVolume);
        t.putDouble("GasLift", s.gasLift);
        if (s.fluid != null) {
            t.putDouble("Displacement", s.fluid.totalSolidVolume + s.fluid.totalPocketVolume);
            t.putDouble("PocketVolume", s.fluid.totalPocketVolume);
            t.putInt("Pockets", s.fluid.pockets.size());
            t.putInt("FluidElements", s.fluid.count);
            t.putDouble("FloatMargin", (s.fluid.totalSolidVolume + s.fluid.totalPocketVolume) * 1000 / Math.max(1, s.mass()));
        }
        t.putInt("CollisionBoxes", s.shape != null ? s.shape.boxes.length : 0);
        t.putInt("Probes", s.shape != null ? s.shape.probeCount() : 0);
        t.putInt("WingStrips", s.wings.size());
        t.putDouble("Lift", s.totalLift);
        t.putDouble("Drag", s.totalDrag);
        t.putDouble("Thrust", s.totalThrust);
        t.putDouble("Buoyancy", s.buoyancy);
        t.putDouble("Airspeed", s.airspeed);
        t.putDouble("AoA", Math.toDegrees(s.angleOfAttack));
        // engines
        ListTag engines = new ListTag();
        for (int i = 0; i < s.thrusters.size(); i++) {
            ThrusterUnit u = s.thrusters.get(i);
            CompoundTag e = new CompoundTag();
            e.putInt("Type", u.engine.type.ordinal());
            e.putInt("Device", u.device.ordinal());
            e.putInt("State", u.engine.state.ordinal());
            e.putDouble("Spool", u.engine.spool);
            e.putDouble("Rpm", u.engine.rpm());
            e.putDouble("Temp", u.engine.temperatureC);
            e.putDouble("MaxTemp", u.engine.type.maxTempC);
            e.putDouble("Damage", u.engine.damage);
            e.putDouble("Power", u.engine.shaftPowerW);
            e.putDouble("Thrust", u.lastThrust);
            e.putDouble("Fuel", u.engine.fuelFlowKgS);
            e.putInt("Group", u.engine.throttleGroup);
            e.putBoolean("Enabled", u.engine.enabled);
            e.putString("Failure", u.engine.lastFailure);
            engines.add(e);
        }
        t.put("Engines", engines);
        double fuel = 0, cap = 0, ballast = 0, ballastCap = 0;
        int tanks = 0;
        for (TankUnit tu : s.tanks) {
            if (tu.voxel == Long.MIN_VALUE) continue;
            if (tu.kind == TankUnit.Kind.FUEL) { fuel += tu.amount; cap += tu.capacity; tanks++; }
            else { ballast += tu.amount; ballastCap += tu.capacity; }
        }
        t.putDouble("Fuel", fuel);
        t.putDouble("FuelCap", cap);
        t.putInt("Tanks", tanks);
        t.putDouble("FuelFlow", s.fuelFlow);
        t.putDouble("FuelSupply", s.fuelSupply);
        t.putInt("Pumps", s.fuelPumps);
        t.putBoolean("Starter", s.hasStarter);
        t.putInt("Hydraulics", s.hydraulicPumps);
        t.putDouble("Ballast", ballast);
        t.putDouble("BallastCap", ballastCap);
        // weapons
        ListTag weapons = new ListTag();
        for (WeaponSystem.Slot w : v.weapons.slots()) {
            CompoundTag e = new CompoundTag();
            e.putString("Type", w.def.type.id);
            e.putInt("Channel", w.channel);
            e.putInt("Ammo", w.ammo);
            e.putInt("Mag", w.spec.magazine);
            e.putInt("Reserve", v.weapons.availableAmmo(w.spec.ammo));
            e.putBoolean("Mounted", w.def.mounted);
            e.putBoolean("Turret", w.def.turretBase != Long.MIN_VALUE);
            e.putBoolean("Safety", w.safety);
            weapons.add(e);
        }
        t.put("Weapons", weapons);
        t.putBoolean("Armed", v.controls.armed);
        t.putInt("Rcus", l.rcus.size());
        t.putInt("AmmoBoxes", l.ammoBoxes.size());
        // damage
        t.putInt("DamagedBlocks", v.structure.damageMap().size());
        t.putFloat("Integrity", v.integrity());
        t.putDouble("Stress", v.stressLevel);
        // mass grid
        t.putInt("GridW", l.gridW);
        t.putInt("GridH", l.gridH);
        t.putDouble("GridCell", l.gridCell);
        t.putDouble("GridMinR", l.gridMinRight);
        t.putDouble("GridMinF", l.gridMinFwd);
        int[] grid = new int[l.massGrid.length];
        for (int i = 0; i < grid.length; i++) grid[i] = (int) Math.round(l.massGrid[i]);
        t.putIntArray("Grid", grid);
        t.putDouble("ComRight", s.com.dot(l.right));
        // physics performance
        if (v.level() instanceof ServerLevel sl) {
            var mgr = VehiclePhysicsManager.get(sl);
            t.putInt("Substeps", mgr.world.lastSubsteps);
            t.putInt("Contacts", mgr.world.lastContactCount);
            t.putDouble("StepMs", mgr.lastNanos / 1e6);
            t.putInt("TerrainBlocks", mgr.env.lastTerrainBlocks);
            t.putInt("Vehicles", mgr.world.sims.size());
        }
        t.putBoolean("Sleeping", s.body.sleeping);
        return t;
    }
}
