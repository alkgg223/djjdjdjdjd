package com.vehicleworks.physics;

import com.vehicleworks.physics.aero.Atmosphere;
import com.vehicleworks.physics.sim.Environment;
import com.vehicleworks.physics.sim.VehicleSimulation;
import org.joml.Vector3d;

import java.util.IdentityHashMap;
import java.util.Map;

/** Flat ground at y = groundY (solid below), optional water up to waterY. */
public final class TestEnv implements Environment {
    public double groundY = 0;
    public boolean ground = true;
    public double waterY = Double.NEGATIVE_INFINITY;
    public float friction = 0.8f;
    public final Vector3d windV = new Vector3d();
    private final Map<VehicleSimulation, TerrainBoxes> cache = new IdentityHashMap<>();

    @Override
    public TerrainBoxes terrainFor(VehicleSimulation sim) {
        TerrainBoxes t = cache.computeIfAbsent(sim, k -> new TerrainBoxes());
        t.clear();
        if (!ground) return t;
        double[] b = new double[6];
        sim.worldBounds(b);
        double v = sim.body.linearVelocity.length() * 0.05 + 1.5;
        int x0 = (int) Math.floor(b[0] - v), y0 = (int) Math.floor(b[1] - v), z0 = (int) Math.floor(b[2] - v);
        int x1 = (int) Math.floor(b[3] + v), y1 = (int) Math.floor(b[4] + v), z1 = (int) Math.floor(b[5] + v);
        int gy = (int) Math.floor(groundY) - 1;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = Math.max(y0, gy - 2); y <= Math.min(y1, gy); y++) {
                    t.beginBlock(x, y, z, true);
                    t.addBox(x, y, z, x + 1, y + 1, z + 1, friction);
                }
        t.endBlock();
        return t;
    }

    @Override
    public void wind(double x, double y, double z, Vector3d out) {
        out.set(windV);
    }

    @Override
    public double ambientTemperature(double y) {
        return Atmosphere.temperature(altitude(y)) - 273.15;
    }

    @Override
    public double altitude(double y) {
        return y;
    }

    @Override
    public double heightAboveSurface(double x, double y, double z, double maxDist) {
        double s = ground ? groundY : -1e9;
        if (waterY > s) s = waterY;
        return Math.min(maxDist, Math.max(0, y - s));
    }

    @Override
    public float frictionAt(double x, double y, double z) {
        return friction;
    }

    @Override
    public double gravity() {
        return 9.81;
    }

    @Override
    public void sample(double x, double y, double z, double[] out) {
        if (waterY > Double.NEGATIVE_INFINITY && y < waterY + 2) {
            out[0] = waterY;
            out[1] = 1000;
        } else {
            out[0] = Double.NEGATIVE_INFINITY;
            out[1] = 0;
        }
        out[2] = out[3] = 0;
    }

    @Override
    public double airDensity(double y) {
        return Atmosphere.density(altitude(y));
    }
}
