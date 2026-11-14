package com.vehicleworks.physics.propulsion;

/**
 * Propeller / marine screw thrust from shaft power using actuator-disk momentum theory.
 * Static thrust is limited by T³ = 2ρA·(ηP)² (figure of merit), and at speed by T = ηP / V.
 */
public final class PropellerModel {
    private PropellerModel() {}

    /**
     * @param powerW    shaft power delivered
     * @param speed     axial speed through the fluid (m/s, + forward)
     * @param density   fluid density
     * @param diameter  propeller diameter (m)
     * @param efficiency peak propulsive efficiency (0.75..0.88)
     * @return thrust in Newtons
     */
    public static double thrust(double powerW, double speed, double density, double diameter, double efficiency) {
        if (powerW <= 0 || density <= 0) return 0;
        double area = Math.PI * diameter * diameter * 0.25;
        double figureOfMerit = 0.75;
        double fp = figureOfMerit * powerW;
        double staticThrust = Math.cbrt(2.0 * density * area * fp * fp);
        double v = Math.max(0, speed);
        double dynamicThrust = efficiency * powerW / Math.max(1e-3, v);
        // smooth minimum of static and dynamic regimes
        double t = Math.min(staticThrust, dynamicThrust);
        // ideal momentum theory: thrust at speed V solves T = 2ρA(V+vi)vi with P = T(V+vi)
        return t;
    }
}
