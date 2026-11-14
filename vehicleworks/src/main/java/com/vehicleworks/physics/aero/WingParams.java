package com.vehicleworks.physics.aero;

/**
 * Player-configurable aerodynamic characteristics of a wing block / panel.
 * All values are clamped to physically sensible ranges by {@link #sanitize()}.
 */
public final class WingParams {
    public WingType type = WingType.STANDARD;
    public AirfoilProfile profile = AirfoilProfile.CAMBERED;
    public ControlRole role = ControlRole.NONE;
    /** Multiplier on section lift (lift coefficient tuning). */
    public double liftScale = 1.0;
    /** Chord length per block in metres (wing surface area per block = 1 m span × chord). */
    public double chord = 1.0;
    /** Wing incidence relative to the vehicle's forward axis. */
    public double incidenceDeg = 2.0;
    /** Multiplier on parasitic (profile) drag. */
    public double dragScale = 1.0;
    /** Stall angle of attack (section). Negative = use profile default. */
    public double stallDeg = -1.0;
    /** Flap deflection when flaps are fully deployed. */
    public double flapMaxDeg = 30.0;
    /** Maximum control surface deflection. */
    public double controlMaxDeg = 20.0;
    /** Spoiler deployment at full spoiler command (0..1). */
    public double spoilerMax = 0.0;
    /** Leading-edge sweep (minimum sweep for variable-sweep wings). */
    public double sweepDeg = 8.0;
    /** Maximum sweep for variable-sweep wings. */
    public double sweepMaxDeg = 68.0;
    /** Control surface rate/authority multiplier. */
    public double responsiveness = 1.0;
    public boolean invert = false;

    public double stallAngleDeg() {
        return stallDeg > 0 ? stallDeg : profile.stallDeg;
    }

    public WingParams copy() {
        WingParams p = new WingParams();
        p.set(this);
        return p;
    }

    public void set(WingParams o) {
        type = o.type; profile = o.profile; role = o.role;
        liftScale = o.liftScale; chord = o.chord; incidenceDeg = o.incidenceDeg; dragScale = o.dragScale;
        stallDeg = o.stallDeg; flapMaxDeg = o.flapMaxDeg; controlMaxDeg = o.controlMaxDeg; spoilerMax = o.spoilerMax;
        sweepDeg = o.sweepDeg; sweepMaxDeg = o.sweepMaxDeg; responsiveness = o.responsiveness; invert = o.invert;
    }

    /** Apply the planform defaults of a wing type. */
    public WingParams applyTypeDefaults(WingType t) {
        type = t;
        profile = t.defaultProfile;
        sweepDeg = t.defaultSweepDeg;
        if (t == WingType.DELTA) {
            stallDeg = 32.0;
            incidenceDeg = 0.0;
        } else {
            stallDeg = -1.0;
        }
        return this;
    }

    public WingParams sanitize() {
        if (type == null) type = WingType.STANDARD;
        if (profile == null) profile = AirfoilProfile.CAMBERED;
        if (role == null) role = ControlRole.NONE;
        liftScale = clamp(liftScale, 0.1, 2.5);
        chord = clamp(chord, 0.25, 4.0);
        incidenceDeg = clamp(incidenceDeg, -15, 15);
        dragScale = clamp(dragScale, 0.25, 4.0);
        if (stallDeg > 0) stallDeg = clamp(stallDeg, 4, 40);
        flapMaxDeg = clamp(flapMaxDeg, 0, 50);
        controlMaxDeg = clamp(controlMaxDeg, 0, 40);
        spoilerMax = clamp(spoilerMax, 0, 1);
        sweepDeg = clamp(sweepDeg, 0, 75);
        sweepMaxDeg = clamp(sweepMaxDeg, sweepDeg, 75);
        responsiveness = clamp(responsiveness, 0.1, 3.0);
        if (!Double.isFinite(liftScale)) liftScale = 1;
        return this;
    }

    static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }
}
