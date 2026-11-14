package com.vehicleworks.physics.sim;

import com.vehicleworks.physics.propulsion.RotorModel;
import org.joml.Vector3d;

/** Helicopter main or tail rotor mounted on the structure. */
public final class RotorUnit {
    public final RotorModel model = new RotorModel();
    public final Vector3d localPos = new Vector3d();
    /** shaft axis = thrust direction at zero cyclic (local, unit) */
    public final Vector3d axisLocal = new Vector3d(0, 1, 0);
    public boolean tail;
    /** +1 counter-clockwise seen from the thrust side, −1 clockwise */
    public int spinDirection = 1;
    public double maxCyclicRad = Math.toRadians(12);
    /** effective hub moment arm (m) of a stiff rotor head */
    public double hubStiffness = 1.5;
    public long voxel;

    public double cyclicPitch, cyclicRoll; // applied, rad
    public double tailPitch;               // tail rotor thrust command −1..1
    public double lastThrust;
    public double bladeAngle;              // render
    public double groundHeight = 100;
}
