package com.vehicleworks.physics.aero;

/** Planform families. They set defaults and enable planform-specific effects. */
public enum WingType {
    STANDARD("standard", 8.0, 0.80, AirfoilProfile.CAMBERED, 0.0),
    STRAIGHT("straight", 0.0, 0.88, AirfoilProfile.HIGH_LIFT, 0.0),
    SWEPT("swept", 35.0, 0.75, AirfoilProfile.SUPERCRITICAL, 0.0),
    DELTA("delta", 60.0, 0.70, AirfoilProfile.THIN_SUPERSONIC, 3.14),
    VARIABLE_SWEEP("variable_sweep", 20.0, 0.78, AirfoilProfile.SYMMETRIC, 0.0);

    public final String id;
    public final double defaultSweepDeg;
    /** Oswald span efficiency. */
    public final double oswald;
    public final AirfoilProfile defaultProfile;
    /** Polhamus leading-edge vortex lift constant (delta wings). */
    public final double vortexLiftK;

    WingType(String id, double sweep, double e, AirfoilProfile p, double kv) {
        this.id = id;
        this.defaultSweepDeg = sweep;
        this.oswald = e;
        this.defaultProfile = p;
        this.vortexLiftK = kv;
    }

    public static WingType byIndex(int i) {
        WingType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
