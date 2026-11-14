package com.vehicleworks.physics.sim;

import com.vehicleworks.physics.propulsion.EngineSim;
import org.joml.Vector3d;

/** An engine together with the device it drives. */
public final class ThrusterUnit {
    public enum Device { JET, PROPELLER, MARINE_PROPELLER, DRIVETRAIN, ROTOR }

    public final EngineSim engine;
    public final Device device;
    /** structure-local position of the engine */
    public final Vector3d localPos = new Vector3d();
    /** structure-local thrust direction (unit) */
    public final Vector3d localDir = new Vector3d(0, 0, -1);
    /** position of the propeller (thrust application point) */
    public final Vector3d devicePos = new Vector3d();
    public double propDiameter = 2.4;
    public double propEfficiency = 0.82;
    public long voxel;
    public long deviceVoxel;

    public double lastThrust;
    public double propSpin;     // rad, for rendering
    public double propOmega;
    public boolean submergedIntake;
    public boolean deviceInWater;

    public ThrusterUnit(EngineSim engine, Device device) {
        this.engine = engine;
        this.device = device;
    }
}
