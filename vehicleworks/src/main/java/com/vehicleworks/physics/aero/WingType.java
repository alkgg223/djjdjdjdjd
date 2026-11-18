package com.vehicleworks.physics.aero;

/**
 * Planform families. Each sets defaults and real aerodynamic differences: lift-curve slope,
 * maximum lift, profile drag, span efficiency, stall abruptness, vortex lift, effective dihedral
 * (roll stability from wing position) and ground effect.
 */
public enum WingType {
    //               id                sweep  oswald profile                          vortexK  slope  clMax  cd0   stallW stallOff dihedral ground
    STANDARD("standard",                8.0, 0.80, AirfoilProfile.CAMBERED,         0.0,  1.00, 1.00, 1.00, 1.0,  0.0, 0.0, 1.0),
    STRAIGHT("straight",                0.0, 0.88, AirfoilProfile.HIGH_LIFT,        0.0,  1.03, 1.05, 1.00, 1.6,  0.0, 0.0, 1.0),
    SWEPT("swept",                     35.0, 0.75, AirfoilProfile.SUPERCRITICAL,    0.0,  0.95, 0.95, 0.95, 1.0,  1.5, 1.0, 1.0),
    DELTA("delta",                     60.0, 0.70, AirfoilProfile.THIN_SUPERSONIC,  3.14, 0.90, 1.00, 0.90, 2.2,  0.0, 0.0, 1.0),
    VARIABLE_SWEEP("variable_sweep",   20.0, 0.78, AirfoilProfile.SYMMETRIC,        0.0,  1.00, 1.00, 1.05, 1.0,  0.0, 0.0, 1.0),
    /** tips stay attached (root stalls first): docile stall, good aileron control, agile */
    FORWARD_SWEPT("forward_swept",    -25.0, 0.82, AirfoilProfile.SUPERCRITICAL,    0.0,  1.02, 1.05, 1.00, 2.0,  2.0, -1.0, 1.0),
    /** strake + delta: strong vortex lift, very high stall angle */
    DOUBLE_DELTA("double_delta",       55.0, 0.72, AirfoilProfile.THIN_SUPERSONIC,  3.6,  0.92, 1.15, 0.92, 2.4,  4.0, 0.0, 1.0),
    /** ideal lift distribution: lowest induced drag, but the whole span stalls at once */
    ELLIPTICAL("elliptical",            0.0, 1.00, AirfoilProfile.CAMBERED,         0.0,  1.05, 1.00, 0.97, 0.6,  0.0, 0.0, 1.0),
    /** good compromise: near-elliptic efficiency, light and cheap */
    TAPERED("tapered",                  5.0, 0.92, AirfoilProfile.CAMBERED,         0.0,  1.02, 1.00, 0.95, 1.0,  0.0, 0.0, 1.0),
    /** wing above the fuselage: strong roll stability (pendulum effect), forgiving */
    HIGH_WING("high_wing",              0.0, 0.86, AirfoilProfile.HIGH_LIFT,        0.0,  1.00, 1.08, 1.02, 1.6,  0.0, 5.0, 0.9),
    /** wing under the fuselage: stronger ground effect (floats on landing), more agile in roll */
    LOW_WING("low_wing",                3.0, 0.84, AirfoilProfile.CAMBERED,         0.0,  1.00, 1.00, 1.00, 1.1,  0.0, -1.0, 1.45),
    /** small foreplane: stalls earlier than the main wing (natural stall protection) */
    CANARD("canard",                    0.0, 0.80, AirfoilProfile.SYMMETRIC,        0.0,  1.00, 0.95, 1.00, 1.0, -3.0, 0.0, 1.0),
    /** tailless all-wing: reflexed airfoil, very low drag, efficient; less lift per area */
    FLYING_WING("flying_wing",         25.0, 0.95, AirfoilProfile.SYMMETRIC,        0.0,  0.98, 0.92, 0.78, 1.2,  0.0, 1.0, 1.1);

    public final String id;
    public final double defaultSweepDeg;
    /** Oswald span efficiency. */
    public final double oswald;
    public final AirfoilProfile defaultProfile;
    /** Polhamus leading-edge vortex lift constant (delta wings). */
    public final double vortexLiftK;
    public final double liftSlopeScale, clMaxScale, cd0Scale;
    /** stall sigmoid width multiplier (small = abrupt stall, large = gentle) */
    public final double stallWidth;
    /** added to the stall angle (deg) */
    public final double stallOffsetDeg;
    /** effective dihedral (deg): roll stability from sideslip */
    public final double dihedralDeg;
    /** ground-effect strength multiplier */
    public final double groundEffect;

    WingType(String id, double sweep, double e, AirfoilProfile p, double kv, double slope, double clMax, double cd0,
             double stallW, double stallOff, double dihedral, double ground) {
        this.id = id;
        this.defaultSweepDeg = sweep;
        this.oswald = e;
        this.defaultProfile = p;
        this.vortexLiftK = kv;
        this.liftSlopeScale = slope;
        this.clMaxScale = clMax;
        this.cd0Scale = cd0;
        this.stallWidth = stallW;
        this.stallOffsetDeg = stallOff;
        this.dihedralDeg = dihedral;
        this.groundEffect = ground;
    }

    public static WingType byIndex(int i) {
        WingType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
