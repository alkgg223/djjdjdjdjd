package com.vehicleworks.physics.propulsion;

import java.util.Random;

/**
 * Engine state machine and thermodynamics: start-up, spool dynamics, throttle response,
 * fuel flow, temperature, overheating damage and mechanical failure.
 */
public final class EngineSim {
    public enum State { OFF, STARTING, RUNNING, STOPPING, FAILED }

    public final EngineType type;
    public State state = State.OFF;
    /** Rotor speed fraction (N1 / RPM relative to max), 0..1.05 */
    public double spool;
    public double throttle;       // commanded 0..1
    public boolean afterburner;
    public double temperatureC = 15.0;
    /** 0 = pristine, 1 = destroyed */
    public double damage;
    public double startTimer;
    /** Performance tuning multiplier set by the player (0.5..1.5). */
    public double powerScale = 1.0;
    /** Throttle group 0 = all, 1 = left/A, 2 = right/B. */
    public int throttleGroup;
    public boolean enabled = true;

    // outputs of the last update
    public double shaftPowerW;
    public double thrustN;
    public double fuelFlowKgS;
    public boolean onFire;
    public String lastFailure = "";

    private final Random random;

    public EngineSim(EngineType type, long seed) {
        this.type = type;
        this.random = new Random(seed);
    }

    public void requestStart() {
        if (state == State.OFF || state == State.STOPPING) {
            state = State.STARTING;
            startTimer = 0;
        }
    }

    public void requestStop() {
        if (state == State.RUNNING || state == State.STARTING) state = State.STOPPING;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public double rpm() {
        return spool * maxRpm();
    }

    public double maxRpm() {
        return switch (type) {
            case PISTON -> 2700;
            case TURBOPROP -> 2000;   // propeller shaft rpm after gearbox
            case TURBOFAN -> 10500;
            case TURBOJET -> 14000;
            case ELECTRIC -> 4000;
        };
    }

    /**
     * Advance the engine.
     *
     * @param fuelSupply      fraction of requested fuel that the fuel system can deliver (0..1)
     * @param densityRatio    ρ/ρ0 of the intake air (0 = vacuum / submerged intake)
     * @param airspeed        true airspeed along the engine axis (m/s)
     * @param ambientC        ambient temperature in °C
     * @param starterAvailable a starter / APU is installed and powered
     * @param submerged       intake under water (combustion engines flame out)
     */
    public void update(double dt, double fuelSupply, double densityRatio, double airspeed, double ambientC,
                       boolean starterAvailable, boolean submerged) {
        double targetSpool = 0;
        boolean combustion = type != EngineType.ELECTRIC;
        switch (state) {
            case OFF -> targetSpool = 0;
            case FAILED -> targetSpool = 0;
            case STOPPING -> {
                targetSpool = 0;
                if (spool < 0.02) state = State.OFF;
            }
            case STARTING -> {
                if (type.needsStarter && !starterAvailable) {
                    lastFailure = "no_starter";
                    state = State.OFF;
                    break;
                }
                if (fuelSupply <= 0.01 || (combustion && submerged)) {
                    lastFailure = fuelSupply <= 0.01 ? "no_fuel" : "flooded";
                    state = State.OFF;
                    break;
                }
                startTimer += dt;
                targetSpool = type.idleFraction * Math.min(1.0, startTimer / type.startSeconds) * 1.05;
                if (startTimer >= type.startSeconds) state = State.RUNNING;
            }
            case RUNNING -> {
                double t = enabled ? Math.max(0, Math.min(1, throttle)) : 0;
                targetSpool = type.idleFraction + (1.0 - type.idleFraction) * t;
                if (combustion && (fuelSupply <= 0.001 || submerged || densityRatio < 0.08)) {
                    lastFailure = submerged ? "flooded" : (fuelSupply <= 0.001 ? "fuel_starvation" : "flameout");
                    state = State.STOPPING;
                }
                if (!enabled) state = State.STOPPING;
            }
        }
        // spool dynamics: turbines accelerate slowly at low spool (first-order with spool-dependent rate)
        double rate = targetSpool > spool ? type.spoolUp * (0.35 + 0.65 * spool) : type.spoolDown;
        if (state == State.STARTING) rate = 1.0 / Math.max(0.5, type.startSeconds);
        double diff = targetSpool - spool;
        double step = Math.signum(diff) * Math.min(Math.abs(diff), rate * dt);
        spool = Math.max(0, spool + step);

        double healthFactor = 1.0 - 0.6 * damage;
        double produce = (state == State.RUNNING) ? 1.0 : 0.0;
        double sigma = Math.max(0, densityRatio);
        double lapse = type.altitudeExponent > 0 ? Math.pow(sigma, type.altitudeExponent) : 1.0;
        double ab = (afterburner && type.afterburnerMultiplier > 1.0 && throttle > 0.95 && state == State.RUNNING) ? type.afterburnerMultiplier : 1.0;
        double supply = Math.max(0, Math.min(1, fuelSupply));
        double fuelLimited = combustion ? Math.min(1.0, supply * 1.0) : 1.0;

        if (type.isShaft()) {
            // shaft power ~ cubic-ish in spool for turbines, ~linear for pistons
            double pFrac = type == EngineType.PISTON || type == EngineType.ELECTRIC ? spool : Math.pow(spool, 2.2);
            shaftPowerW = produce * type.maxPowerW * powerScale * pFrac * lapse * healthFactor * fuelLimited;
            thrustN = 0;
            fuelFlowKgS = combustion ? (shaftPowerW / 1e6) * type.sfc / Math.max(0.3, healthFactor) + (state == State.RUNNING || state == State.STARTING ? 0.002 : 0) : 0;
        } else {
            // thrust: N1 ~ thrust^(1/2) ; ram drag lapse with airspeed
            double tFrac = Math.pow(spool, 2.0);
            double ramLapse = type == EngineType.TURBOFAN ? Math.max(0.45, 1.0 - 0.0022 * airspeed) : Math.max(0.65, 1.0 - 0.0009 * airspeed);
            thrustN = produce * type.maxThrustN * powerScale * tFrac * lapse * ramLapse * ab * healthFactor * fuelLimited;
            shaftPowerW = 0;
            fuelFlowKgS = (thrustN / 1000.0) * type.sfc * (ab > 1 ? 2.6 : 1.0) / Math.max(0.3, healthFactor)
                    + (state == State.RUNNING || state == State.STARTING ? 0.004 : 0);
        }

        // thermal model
        double load = type.isShaft() ? shaftPowerW / Math.max(1, type.maxPowerW) : thrustN / Math.max(1, type.maxThrustN);
        double equilibrium = ambientC + (type.maxTempC - ambientC) * (0.35 * spool + 0.62 * load) * type.heatRate * (ab > 1 ? 1.12 : 1.0);
        if (state == State.OFF || state == State.FAILED) equilibrium = ambientC;
        double cooling = 0.08 + 0.004 * Math.min(150, Math.abs(airspeed)) + (submerged ? 0.5 : 0);
        temperatureC += (equilibrium - temperatureC) * Math.min(1.0, cooling * dt);
        // random failures for damaged engines
        if (state == State.RUNNING && damage > 0.45) {
            double chance = (damage - 0.45) * 0.02 * dt;
            if (random.nextDouble() < chance) fail("mechanical_failure");
        }
        if (damage >= 1.0 && state != State.FAILED) fail("destroyed");
        onFire = state == State.FAILED && damage > 0.8;
        temperatureC = Math.min(temperatureC, type.maxTempC * 0.9);
    }

    public void fail(String reason) {
        state = State.FAILED;
        lastFailure = reason;
        shaftPowerW = 0;
        thrustN = 0;
    }

    public void repair(double amount) {
        damage = Math.max(0, damage - amount);
        if (state == State.FAILED && damage < 0.5) state = State.OFF;
    }
}
