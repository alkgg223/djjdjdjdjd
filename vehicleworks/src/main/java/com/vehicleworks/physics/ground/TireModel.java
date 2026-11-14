package com.vehicleworks.physics.ground;

/**
 * Simplified Pacejka "magic formula" tire with a friction-circle combination.
 * Longitudinal force from slip ratio κ, lateral from slip angle α.
 */
public final class TireModel {
    // longitudinal
    public static final double BX = 10.0, CX = 1.65, DX = 1.0, EX = 0.1;
    // lateral
    public static final double BY = 12.0, CY = 1.30, DY = 0.95, EY = -0.3;

    private TireModel() {}

    public static double magic(double slip, double b, double c, double d, double e) {
        double bs = b * slip;
        return d * Math.sin(c * Math.atan(bs - e * (bs - Math.atan(bs))));
    }

    /** Normalised longitudinal force (multiply by μ·Fz). */
    public static double longitudinal(double slipRatio) {
        return magic(slipRatio, BX, CX, DX, EX);
    }

    /** Normalised lateral force for slip angle in radians. */
    public static double lateral(double slipAngle) {
        return magic(slipAngle, BY, CY, DY, EY);
    }

    /**
     * Combine longitudinal and lateral normalised forces so the resultant stays within
     * the friction circle. Returns scale factor to apply to both.
     */
    public static double frictionCircleScale(double fxN, double fyN) {
        double m = Math.sqrt(fxN * fxN + fyN * fyN);
        return m > 1.0 ? 1.0 / m : 1.0;
    }
}
