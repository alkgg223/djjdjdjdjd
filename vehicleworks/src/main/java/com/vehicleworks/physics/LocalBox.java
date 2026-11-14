package com.vehicleworks.physics;

/** Axis-aligned box in a body's local frame (structure coordinates, metres). */
public final class LocalBox {
    public final double minX, minY, minZ, maxX, maxY, maxZ;
    /** Coulomb friction coefficient of the material. */
    public final float friction;
    /** Number of voxels merged into this box (for diagnostics). */
    public final int voxels;

    public LocalBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float friction, int voxels) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.friction = friction;
        this.voxels = voxels;
    }

    public boolean contains(double x, double y, double z) {
        return x > minX && x < maxX && y > minY && y < maxY && z > minZ && z < maxZ;
    }

    public double volume() {
        return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
    }

    @Override
    public String toString() {
        return "Box[" + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ + "]";
    }
}
