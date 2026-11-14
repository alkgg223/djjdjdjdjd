package com.vehicleworks.physics.propulsion;

/**
 * Helicopter rotor using blade-element momentum theory with uniform inflow.
 * Thrust coefficient C_T = (σa/2)(θ/3 − λ/2), induced inflow from momentum theory
 * including forward flight (Glauert) and climb, power from C_Q = C_T λ + σC_d0/8,
 * plus Cheeseman–Bennett ground effect.
 */
public final class RotorModel {
    public double radius = 6.0;
    public int blades = 4;
    public double chord = 0.45;
    public double liftSlope = 5.7;
    public double cd0 = 0.011;
    /** Rotor polar moment of inertia (kg m²). */
    public double inertia = 900.0;
    public double nominalOmega = 32.0; // rad/s (~300 rpm)
    public double maxCollectiveRad = Math.toRadians(16);
    public double minCollectiveRad = Math.toRadians(-2);

    // state
    public double omega;
    public double collective;        // rad
    public double lastThrust, lastTorque, lastInflow, lastCT;

    public double solidity() {
        return blades * chord / (Math.PI * radius);
    }

    public double diskArea() {
        return Math.PI * radius * radius;
    }

    /**
     * Compute thrust and aerodynamic torque for the current state.
     *
     * @param density       air density
     * @param axialVelocity climb velocity along the shaft (m/s, + = moving toward thrust direction)
     * @param inplaneSpeed  edgewise speed through the air (m/s)
     * @param heightAboveGround height of the hub above the ground (m)
     */
    public void compute(double density, double axialVelocity, double inplaneSpeed, double heightAboveGround) {
        double tipSpeed = Math.max(1e-3, omega * radius);
        double sigma = solidity();
        double lambdaC = axialVelocity / tipSpeed;
        double mu = inplaneSpeed / tipSpeed;
        double lambda = 0.05 + lambdaC;
        double ct = 0;
        for (int i = 0; i < 12; i++) {
            ct = sigma * liftSlope / 2.0 * (collective / 3.0 * (1.0 + 1.5 * mu * mu) - lambda / 2.0);
            double lambdaI;
            double denom = 2.0 * Math.sqrt(mu * mu + lambda * lambda);
            lambdaI = denom > 1e-6 ? ct / denom : 0;
            double newLambda = lambdaC + lambdaI;
            lambda = 0.6 * lambda + 0.4 * newLambda;
        }
        double geFactor = 1.0;
        if (heightAboveGround > 0 && heightAboveGround < 2.5 * radius) {
            double r = radius / (4.0 * Math.max(heightAboveGround, radius * 0.3));
            geFactor = 1.0 / Math.max(0.6, 1.0 - r * r);
        }
        double ref = density * diskArea() * tipSpeed * tipSpeed;
        double thrust = ct * ref * geFactor;
        double cq = ct * lambda + sigma * cd0 / 8.0 * (1.0 + 4.6 * mu * mu);
        double torque = Math.max(0, cq) * ref * radius;
        lastCT = ct;
        lastInflow = lambda;
        lastThrust = thrust;
        lastTorque = torque;
    }

    /** Integrate rotor speed: J dΩ/dt = Q_engine − Q_aero − friction. */
    public void integrate(double engineTorque, double dt) {
        double friction = 0.004 * inertia * omega;
        omega += (engineTorque - lastTorque - friction) / inertia * dt;
        if (omega < 0) omega = 0;
        omega = Math.min(omega, nominalOmega * 1.25);
    }
}
