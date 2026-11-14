package com.vehicleworks.physics.sim;

import com.vehicleworks.physics.TerrainBoxes;
import com.vehicleworks.physics.fluid.FluidSampler;
import org.joml.Vector3d;

/** World services required by the simulation (implemented by the game layer or tests). */
public interface Environment extends FluidSampler {
    /** Terrain boxes around this simulation for the current tick. */
    TerrainBoxes terrainFor(VehicleSimulation sim);

    /** Wind including turbulence at a world position (m/s). */
    void wind(double x, double y, double z, Vector3d out);

    /** Ambient air temperature at a world height (°C). */
    double ambientTemperature(double y);

    /** Geometric altitude (m) used for the atmosphere model at a world height. */
    double altitude(double y);

    /** Distance from a world point straight down to solid ground or water (m), capped at maxDist. */
    double heightAboveSurface(double x, double y, double z, double maxDist);

    /** Friction coefficient of the block below a world point. */
    float frictionAt(double x, double y, double z);

    double gravity();
}
