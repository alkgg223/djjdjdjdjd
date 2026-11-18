package com.vehicleworks.physics.propulsion;

import java.util.Random;

/**
 * Engine state machine and thermodynamics: start-up, spool dynamics, throttle response,
 * fuel flow, temperature, overheating damage and mechanical failure.
 */
public final class EngineSim {
    public enum State { OFF, STARTING, RUNNING, STOPPING, FAILED }
    /**
     * Start phases. Piston: CRANK (starter turns the engine) -> ACCEL (it catches on ignition + fuel)
     * -> IDLE. Turbine: CRANK (starter spools N2) -> LIGHT_OFF (fuel introduced with igniters, EGT
     * rises) -> ACCEL (combustion and starter accelerate it, starter cuts out) -> IDLE.
     */
    public enum Phase { NONE, CRANK, LIGHT_OFF, ACCEL, IDLE }

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
    public Phase phase = Phase.NONE;
    /** run the whole start procedure by itself (quick start, cars, boats); cleared once running */
    public boolean autoStart = true;
    /** cockpit inputs: starter engaged, ignition (magnetos / igniters), fuel to the engine (engine master / mixture) */
    public boolean starterCmd, ignitionCmd = true, fuelCmd = true;
    /** electrical power for the starter motor */
    public boolean starterPower = true;
    public double crankTime, phaseTimer, wetTime;
    public boolean hotStart;
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
        requestStart(true);
    }

    /** @param auto true: run the full procedure automatically; false: follow the cockpit inputs */
    public void requestStart(boolean auto) {
        if (state == State.OFF || state == State.STOPPING) {
            state = State.STARTING;
            startTimer = 0;
            crankTime = phaseTimer = wetTime = 0;
            hotStart = false;
            phase = Phase.CRANK;
            autoStart = auto;
        }
    }

    /** fraction of idle at which the starter of a turbine cuts out (self-sustaining speed) */
    public double starterCutout() {
        return type.idleFraction * 0.78;
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
        double targetSpool = 0, startRate = 0.3;
        boolean combustion = type != EngineType.ELECTRIC;
        switch (state) {
            case OFF -> targetSpool = 0;
            case FAILED -> targetSpool = 0;
            case STOPPING -> {
                targetSpool = 0;
                phase = Phase.NONE;
                if (spool < 0.02) state = State.OFF;
            }
            case STARTING -> {
                startTimer += dt;
                phaseTimer += dt;
                boolean auto = autoStart;
                if (auto && type.needsStarter && !starterAvailable) {
                    lastFailure = "no_starter";
                    state = State.OFF;
                    phase = Phase.NONE;
                    break;
                }
                boolean starter = (auto || starterCmd) && starterPower && (!type.needsStarter || starterAvailable);
                boolean ign = auto || ignitionCmd;
                boolean fuelIn = (auto || fuelCmd) && fuelSupply > 0.01;
                if (combustion && submerged) {
                    lastFailure = "flooded";
                    state = State.OFF;
                    phase = Phase.NONE;
                    break;
                }
                double idle = type.idleFraction;
                if (!combustion) {
                    targetSpool = idle;
                    startRate = 1.5;
                    if (spool >= idle * 0.97) { state = State.RUNNING; phase = Phase.IDLE; autoStart = false; }
                    break;
                }
                if (type == EngineType.PISTON) {
                    if (phase == Phase.CRANK || phase == Phase.NONE) {
                        phase = Phase.CRANK;
                        targetSpool = starter ? 0.10 : 0; // ~270 rpm on the starter
                        startRate = starter ? 0.22 : type.spoolDown;
                        // it catches after a few compression strokes with spark and fuel
                        if (starter && spool > 0.06 && ign && fuelIn) crankTime += dt;
                        if (crankTime > 0.8) { phase = Phase.ACCEL; phaseTimer = 0; }
                    }
                    if (phase == Phase.ACCEL) {
                        targetSpool = idle;
                        startRate = 0.55;
                        if (!ign || !fuelIn) { phase = Phase.CRANK; crankTime = 0; lastFailure = !ign ? "no_ignition" : "no_fuel"; }
                        else if (spool >= idle * 0.97) { state = State.RUNNING; phase = Phase.IDLE; autoStart = false; }
                    }
                } else {
                    double crank = idle * 0.45, lightMin = idle * 0.3, cutout = starterCutout();
                    if (phase == Phase.CRANK || phase == Phase.NONE) {
                        phase = Phase.CRANK;
                        targetSpool = starter ? crank : 0;
                        startRate = starter ? crank / Math.max(1, type.startSeconds * 0.45) : type.spoolDown * 0.5;
                        // the automatic sequence opens the fuel only at a proper light-off speed
                        if (fuelIn && (!auto || spool >= lightMin * 1.3)) {
                            if (ign && spool >= lightMin * 0.6) {
                                // fuel introduced too early (below the light-off speed): hot start
                                hotStart = spool < lightMin;
                                phase = Phase.LIGHT_OFF;
                                phaseTimer = 0;
                            } else if (!ign && (wetTime += dt) > 6) {
                                lastFailure = "no_light_off";
                                state = State.STOPPING;
                                phase = Phase.NONE;
                            }
                        }
                    }
                    if (phase == Phase.LIGHT_OFF) {
                        targetSpool = starter ? crank * 1.15 : spool;
                        startRate = crank / Math.max(1, type.startSeconds);
                        if (!fuelIn) { phase = Phase.CRANK; }
                        else if (phaseTimer > 1.4) { phase = Phase.ACCEL; phaseTimer = 0; }
                    }
                    if (phase == Phase.ACCEL) {
                        targetSpool = idle;
                        startRate = idle / Math.max(1, type.startSeconds * 0.6);
                        if (!fuelIn) { state = State.STOPPING; phase = Phase.NONE; lastFailure = "fuel_cutoff"; }
                        else if (!starter && spool < cutout) {
                            // starter released before self-sustaining speed: hung start
                            targetSpool = 0;
                            startRate = type.spoolDown * 0.4;
                            if (spool < lightMin) { state = State.STOPPING; phase = Phase.NONE; lastFailure = "hung_start"; }
                        } else if (spool >= idle * 0.97) { state = State.RUNNING; phase = Phase.IDLE; autoStart = false; }
                    }
                }
                if (state == State.STARTING && !starter && phase == Phase.CRANK && spool < 0.01 && startTimer > 1.0) {
                    state = State.OFF;
                    phase = Phase.NONE;
                }
                if (state == State.STARTING && startTimer > 60) {
                    lastFailure = "start_timeout";
                    state = State.STOPPING;
                    phase = Phase.NONE;
                }
            }
            case RUNNING -> {
                phase = Phase.IDLE;
                double t = enabled ? Math.max(0, Math.min(1, throttle)) : 0;
                targetSpool = type.idleFraction + (1.0 - type.idleFraction) * t;
                // fuel shut off (engine master / mixture cut-off) or piston magnetos off: the engine stops
                if (combustion && !fuelCmd) { lastFailure = "fuel_cutoff"; state = State.STOPPING; }
                if (type == EngineType.PISTON && !ignitionCmd) { lastFailure = "ignition_off"; state = State.STOPPING; }
                if (combustion && (fuelSupply <= 0.001 || submerged || densityRatio < 0.08)) {
                    lastFailure = submerged ? "flooded" : (fuelSupply <= 0.001 ? "fuel_starvation" : "flameout");
                    state = State.STOPPING;
                }
                if (!enabled) state = State.STOPPING;
            }
        }
        // spool dynamics: turbines accelerate slowly at low spool (first-order with spool-dependent rate)
        double rate = targetSpool > spool ? type.spoolUp * (0.35 + 0.65 * spool) : type.spoolDown;
        if (state == State.STARTING) rate = startRate;
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
        // light-off: exhaust gas temperature peaks during the start (much more on a hot start)
        if (state == State.STARTING && type.isTurbine() && (phase == Phase.LIGHT_OFF || phase == Phase.ACCEL))
            equilibrium = ambientC + (type.maxTempC - ambientC) * (hotStart ? 1.12 : 0.78);
        double cooling = 0.08 + 0.004 * Math.min(150, Math.abs(airspeed)) + (submerged ? 0.5 : 0);
        if (state == State.STARTING) cooling = Math.max(cooling, 0.6); // start transients are fast
        temperatureC += (equilibrium - temperatureC) * Math.min(1.0, cooling * dt);
        // random failures for damaged engines
        if (state == State.RUNNING && damage > 0.45) {
            double chance = (damage - 0.45) * 0.02 * dt;
            if (random.nextDouble() < chance) fail("mechanical_failure");
        }
        if (damage >= 1.0 && state != State.FAILED) fail("destroyed");
        onFire = state == State.FAILED && damage > 0.8;
        if (temperatureC > type.maxTempC) damage = Math.min(1, damage + 0.06 * dt); // over-temperature (hot start)
        temperatureC = Math.min(temperatureC, type.maxTempC * (hotStart ? 1.1 : 0.9));
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
