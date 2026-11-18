package com.vehicleworks.physics.propulsion;

/**
 * Engine families with distinct performance characteristics. Values are scaled for
 * Minecraft-sized vehicles but keep realistic ratios (power-to-weight, SFC, spool times).
 */
public enum EngineType {
    //              id            output  maxPower(W) maxThrust(N) mass(kg) idle  spoolUp spoolDown sfc(kg/MJ) maxTempC heatRate needsStarter startSec afterburner altitudeExp
    PISTON("piston",            Output.SHAFT, 260_000,      0,   380, 0.22, 1.20, 0.90, 0.085, 230, 1.00, false, 2.0, 1.0, 1.10),
    TURBOPROP("turboprop",      Output.SHAFT, 1_100_000,      0,   520, 0.30, 0.45, 0.60, 0.075, 820, 0.85, true,  6.0, 1.0, 0.75),
    TURBOFAN("turbofan",        Output.THRUST,        0, 48_000, 1400, 0.25, 0.28, 0.45, 0.018, 900, 0.80, true,  8.0, 1.0, 0.80),
    TURBOJET("turbojet",        Output.THRUST,        0, 62_000, 1100, 0.20, 0.35, 0.55, 0.030, 980, 1.10, true,  6.0, 1.55, 0.70),
    ELECTRIC("electric",        Output.SHAFT, 180_000,      0,   220, 0.00, 3.00, 3.00, 0.000, 110, 0.60, false, 0.3, 1.0, 0.0);

    public enum Output { SHAFT, THRUST }

    /** fuel grade this engine burns: 1 AVGAS, 2 JET-A, 0 none (electric) */
    public int fuelGrade() {
        return switch (this) {
            case PISTON -> 1;
            case TURBOPROP, TURBOFAN, TURBOJET -> 2;
            case ELECTRIC -> 0;
        };
    }

    public final String id;
    public final Output output;
    public final double maxPowerW;
    public final double maxThrustN;
    public final double massKg;
    public final double idleFraction;
    /** Spool-up rate in fraction/second near full power (turbines are slow). */
    public final double spoolUp;
    public final double spoolDown;
    /** Specific fuel consumption: kg of fuel per MJ of shaft work, or per kN·s×1000 for thrust engines. */
    public final double sfc;
    public final double maxTempC;
    public final double heatRate;
    public final boolean needsStarter;
    public final double startSeconds;
    public final double afterburnerMultiplier;
    /** Power lapse exponent with density ratio: P = P0 σ^k. */
    public final double altitudeExponent;

    EngineType(String id, Output output, double p, double t, double mass, double idle, double up, double down, double sfc,
               double maxTemp, double heat, boolean starter, double startSec, double ab, double altExp) {
        this.id = id;
        this.output = output;
        this.maxPowerW = p;
        this.maxThrustN = t;
        this.massKg = mass;
        this.idleFraction = idle;
        this.spoolUp = up;
        this.spoolDown = down;
        this.sfc = sfc;
        this.maxTempC = maxTemp;
        this.heatRate = heat;
        this.needsStarter = starter;
        this.startSeconds = startSec;
        this.afterburnerMultiplier = ab;
        this.altitudeExponent = altExp;
    }

    public boolean isShaft() {
        return output == Output.SHAFT;
    }

    public boolean isTurbine() {
        return this == TURBOPROP || this == TURBOFAN || this == TURBOJET;
    }

    public static EngineType byIndex(int i) {
        EngineType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
