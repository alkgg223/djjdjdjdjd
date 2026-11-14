package com.vehicleworks.physics.sim;

import org.joml.Vector3d;

/** Fuel or ballast tank contributing dynamic mass at its position. */
public final class TankUnit {
    public enum Kind { FUEL, BALLAST }

    public final Kind kind;
    public final Vector3d localPos = new Vector3d();
    /** capacity in kg */
    public double capacity;
    /** content in kg */
    public double amount;
    public long voxel;
    public boolean intakeSubmerged;

    public TankUnit(Kind kind, double capacity) {
        this.kind = kind;
        this.capacity = capacity;
    }

    public double fill() {
        return capacity > 0 ? amount / capacity : 0;
    }
}
