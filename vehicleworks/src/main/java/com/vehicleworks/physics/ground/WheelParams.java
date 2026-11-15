package com.vehicleworks.physics.ground;

/** Configurable wheel / landing gear parameters. */
public final class WheelParams {
    public double radius = 0.5;
    public double restLength = 0.45;   // suspension travel at rest (m)
    public double stiffness = 60000;   // N/m (scaled by vehicle mass at build)
    public double damping = 6000;      // N s/m
    public double maxSteerDeg = 30;
    public boolean steerable = false;
    public boolean driven = false;
    public boolean braked = true;
    public double brakeTorque = 6000;  // N m
    public double gripScale = 1.0;
    public double mass = 40;           // wheel mass (kg) → inertia
    public boolean retractable = false;
    /** +1 front, -1 rear: Ackermann / handbrake helper, set by layout. */
    public int axleSign;

    public double inertia() {
        return 0.5 * mass * radius * radius;
    }

    public WheelParams copy() {
        WheelParams p = new WheelParams();
        p.radius = radius; p.restLength = restLength; p.stiffness = stiffness; p.damping = damping;
        p.maxSteerDeg = maxSteerDeg; p.steerable = steerable; p.driven = driven; p.braked = braked;
        p.brakeTorque = brakeTorque; p.gripScale = gripScale; p.mass = mass; p.retractable = retractable; p.axleSign = axleSign;
        return p;
    }

    public WheelParams sanitize() {
        radius = clamp(radius, 0.2, 2.5);
        restLength = clamp(restLength, 0.05, 1.5);
        stiffness = clamp(stiffness, 2000, 2_000_000);
        damping = clamp(damping, 100, 300_000);
        maxSteerDeg = clamp(maxSteerDeg, 0, 60);
        brakeTorque = clamp(brakeTorque, 0, 60000);
        gripScale = clamp(gripScale, 0.2, 2.0);
        mass = clamp(mass, 5, 800);
        return this;
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }
}
