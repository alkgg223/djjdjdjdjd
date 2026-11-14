package com.vehicleworks.physics.aero;

/**
 * 2D section characteristics of common airfoil families. Values are representative of
 * published wind-tunnel data at moderate Reynolds numbers.
 */
public enum AirfoilProfile {
    //            name                 cl0    a0/rad clMax stallDeg cd0     cdAlpha2 dragDivMach
    FLAT_PLATE("flat_plate",          0.00, 5.6,  0.85,  9.0,  0.0200, 0.60, 0.70),
    SYMMETRIC("naca0012",             0.00, 6.1,  1.45, 15.0,  0.0080, 0.40, 0.74),
    CAMBERED("naca2412",              0.24, 6.2,  1.60, 15.5,  0.0090, 0.40, 0.72),
    HIGH_LIFT("naca4412",             0.47, 6.2,  1.90, 14.0,  0.0110, 0.45, 0.68),
    SUPERCRITICAL("supercritical",    0.35, 6.3,  1.55, 13.0,  0.0075, 0.40, 0.82),
    THIN_SUPERSONIC("biconvex",       0.00, 5.8,  1.05, 11.0,  0.0060, 0.50, 0.88),
    REFLEX("reflex",                  0.12, 6.0,  1.30, 14.0,  0.0095, 0.42, 0.72);

    public final String id;
    /** Section lift coefficient at zero angle of attack (camber). */
    public final double cl0;
    /** Section lift-curve slope per radian (thin-airfoil theory gives 2π). */
    public final double liftSlope;
    public final double clMax;
    public final double stallDeg;
    public final double cd0;
    /** Profile drag growth with α² (per rad²). */
    public final double cdAlpha2;
    public final double dragDivergenceMach;

    AirfoilProfile(String id, double cl0, double a0, double clMax, double stallDeg, double cd0, double cdAlpha2, double mdd) {
        this.id = id;
        this.cl0 = cl0;
        this.liftSlope = a0;
        this.clMax = clMax;
        this.stallDeg = stallDeg;
        this.cd0 = cd0;
        this.cdAlpha2 = cdAlpha2;
        this.dragDivergenceMach = mdd;
    }

    public static AirfoilProfile byIndex(int i) {
        AirfoilProfile[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
