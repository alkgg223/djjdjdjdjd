package com.vehicleworks.physics;

import com.vehicleworks.physics.aero.ControlRole;
import com.vehicleworks.physics.aero.WingParams;
import com.vehicleworks.physics.aero.WingStrip;
import com.vehicleworks.physics.fluid.FluidElements;
import com.vehicleworks.physics.fluid.StructureAnalysis;
import com.vehicleworks.physics.sim.VehicleSimulation;
import org.joml.Vector3d;

/** Helper to assemble simulations from voxels in tests (mirrors the game layer). */
public final class SimBuilder {
    public final VehicleSimulation sim = new VehicleSimulation();
    public final CollisionShape.Builder shape = new CollisionShape.Builder();
    public final VoxelSet flags = new VoxelSet();

    public SimBuilder voxel(int x, int y, int z, double mass) {
        return voxel(x, y, z, mass, 0.6f, 0);
    }

    public SimBuilder voxel(int x, int y, int z, double mass, float friction, int extraFlags) {
        sim.baseMass.addBox(mass, x + 0.5, y + 0.5, z + 0.5, 1, 1, 1);
        shape.addFullVoxel(x, y, z, friction);
        flags.put(x, y, z, StructureAnalysis.FLAG_SOLID | extraFlags);
        return this;
    }

    public SimBuilder box(int x0, int y0, int z0, int x1, int y1, int z1, double massPerVoxel) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) voxel(x, y, z, massPerVoxel);
        return this;
    }

    /** Hollow box shell (walls one voxel thick). */
    public SimBuilder shell(int x0, int y0, int z0, int x1, int y1, int z1, double massPerVoxel, boolean top) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    boolean wall = x == x0 || x == x1 || z == z0 || z == z1 || y == y0 || (top && y == y1);
                    if (wall) voxel(x, y, z, massPerVoxel);
                }
        return this;
    }

    public WingStrip wing(double cx, double cy, double cz, double area, double ar, double span, WingParams p) {
        WingStrip w = new WingStrip(p);
        w.localCenter.set(cx, cy, cz);
        w.forward.set(0, 0, -1);
        w.normal.set(0, 1, 0);
        w.area = area;
        w.aspectRatio = ar;
        w.span = span;
        sim.wings.add(w);
        return w;
    }

    public WingStrip fin(double cx, double cy, double cz, double area, ControlRole role) {
        WingParams p = new WingParams();
        p.profile = com.vehicleworks.physics.aero.AirfoilProfile.SYMMETRIC;
        p.incidenceDeg = 0;
        p.role = role;
        WingStrip w = new WingStrip(p);
        w.localCenter.set(cx, cy, cz);
        w.forward.set(0, 0, -1);
        w.normal.set(-1, 0, 0);
        w.area = area;
        w.aspectRatio = 1.5;
        w.span = 2;
        sim.wings.add(w);
        return w;
    }

    public VehicleSimulation build(double wx, double wy, double wz) {
        sim.shape = shape.build();
        StructureAnalysis a = new StructureAnalysis(flags, 2_000_000);
        sim.fluid = new FluidElements(a, flags);
        sim.rebuildMass();
        // place COM at requested world position
        sim.body.position.set(wx, wy, wz);
        sim.initControlAxes();
        return sim;
    }

    public static Vector3d v(double x, double y, double z) {
        return new Vector3d(x, y, z);
    }
}
