package com.vehicleworks.physics.fluid;

/** Supplies fluid surface height and density at world positions. */
public interface FluidSampler {
    /**
     * Sample the fluid column at a world position.
     *
     * @param out [0] = surface height (world y, or −Infinity when no fluid), [1] = density (kg/m³),
     *            [2],[3] = horizontal flow velocity x/z (m/s)
     */
    void sample(double x, double y, double z, double[] out);

    /** Density of air at a world height. */
    double airDensity(double y);
}
