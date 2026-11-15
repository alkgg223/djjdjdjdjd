package com.vehicleworks.physics.sim;

import com.vehicleworks.physics.CollisionDetector;
import com.vehicleworks.physics.CollisionShape;
import com.vehicleworks.physics.ContactBuffer;
import com.vehicleworks.physics.MassAccumulator;
import com.vehicleworks.physics.RigidBody;
import com.vehicleworks.physics.TerrainBoxes;
import com.vehicleworks.physics.VoxelSet;
import com.vehicleworks.physics.aero.Atmosphere;
import com.vehicleworks.physics.aero.WingStrip;
import com.vehicleworks.physics.fluid.FluidElements;
import com.vehicleworks.physics.fluid.FluidForces;
import com.vehicleworks.physics.ground.Transmission;
import com.vehicleworks.physics.ground.WheelSim;
import com.vehicleworks.physics.propulsion.EngineSim;
import com.vehicleworks.physics.propulsion.EngineType;
import com.vehicleworks.physics.propulsion.PropellerModel;
import com.vehicleworks.physics.propulsion.RotorModel;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete simulation of one vehicle: a rigid body plus all its physical subsystems.
 * Independent of Minecraft; the game layer builds it from blocks and supplies an {@link Environment}.
 */
public final class VehicleSimulation {
    public static final double BASE_FUEL_FLOW = 0.6;    // kg/s gravity feed
    public static final double PUMP_FUEL_FLOW = 2.0;    // kg/s per pump
    /** gameplay multiplier on fuel consumption */
    public double fuelScale = 1.0;
    /** speed at which flight assist rotates the aircraft for take-off (m/s) */
    public double rotateSpeed = 40;

    public final RigidBody body = new RigidBody();
    public CollisionShape shape;
    /** structure-local centre of mass */
    public final Vector3d com = new Vector3d();
    public final MassAccumulator baseMass = new MassAccumulator();
    private final MassAccumulator totalMass = new MassAccumulator();

    /** structure-local axes */
    public final Vector3d forward = new Vector3d(0, 0, -1);
    public final Vector3d up = new Vector3d(0, 1, 0);
    public final Vector3d right = new Vector3d(1, 0, 0);

    public final List<WingStrip> wings = new ArrayList<>();
    public final List<WheelSim> wheels = new ArrayList<>();
    public final List<ThrusterUnit> thrusters = new ArrayList<>();
    public final List<RotorUnit> rotors = new ArrayList<>();
    public final List<TankUnit> tanks = new ArrayList<>();
    public int fuelPumps;
    public boolean hasStarter;
    public int hydraulicPumps;
    public int burners;

    public FluidElements fluid;
    public double[] pocketFlood = new double[0];
    public double[] gasDensity = new double[0];
    public double[] gasFill = new double[0];
    /** per gas pocket: true = helium cell, false = hot air (heated by burners) */
    public boolean[] gasHelium = new boolean[0];
    public double envelopeTempC = 15;
    public boolean hotAir;
    public double gasLiftScale = 4.0;
    public double burnerPowerW = 2.5e6;

    public final Transmission transmission = new Transmission();
    /** 0 open, 1 limited slip, 2 locked */
    public int differential = 1;
    public final ControlInput input = new ControlInput();
    public final FluidForces fluidForces = new FluidForces();
    private final MouseAim mouseAim = new MouseAim();

    public boolean frozen;
    public boolean anchored;   // parking: held in place (e.g. not yet ready)
    public double maxContactsPerTick = 64;

    // derived per-tick state (for HUD / GUI / sync)
    public double airspeed, mach, angleOfAttack, sideslip, verticalSpeed, altitude, heightAboveGround, gLoad, airDensity;
    public double totalLift, totalDrag, totalThrust, buoyancy, gasLift;
    public boolean stallWarning, inWater, onGround;
    public double fuelSupply = 1, fuelAmount, fuelCapacity, fuelFlow;
    public double ballastFill, carEngineRpmFraction;
    public double hydraulicAuthority = 1;
    public double effPitch, effRoll, effYaw;
    public int tickCount;
    private final double[] aimOut = new double[3];
    private double tailTrim = 0.0;
    private double holdPitch;
    private boolean holdingPitch;
    private double holdHeading;
    private double collectiveTrim = 0;
    private boolean holdingHeading;
    private final Vector3d lastVelocity = new Vector3d();
    private final Vector3d wind = new Vector3d();
    private final Vector3d tmp = new Vector3d(), tmp2 = new Vector3d(), tmp3 = new Vector3d();
    private final double[] sample = new double[4];
    private TerrainBoxes terrain;

    public VehicleSimulation() {
        body.owner = this;
    }

    // ------------------------------------------------------------------ mass

    /** Recompute mass properties including dynamic loads (fuel, ballast). Keeps the structure in place. */
    public void rebuildMass() {
        totalMass.set(baseMass);
        for (TankUnit t : tanks) {
            if (t.amount > 0) totalMass.addBox(t.amount, t.localPos.x, t.localPos.y, t.localPos.z, 1, 1, 1);
        }
        if (totalMass.mass() <= 0) totalMass.addBox(100, 0.5, 0.5, 0.5, 1, 1, 1);
        Vector3d newCom = totalMass.centerOfMass(new Vector3d());
        if (!newCom.equals(com, 1e-9)) {
            // keep the structure fixed in the world while the COM moves inside it
            tmp.set(newCom).sub(com);
            body.rotation.transform(tmp);
            body.position.add(tmp);
            boolean big = newCom.distanceSquared(com) > 0.0025;
            com.set(newCom);
            if (big || tickCount == 0) for (WingStrip w : wings) w.updateControlAxis(com);
        }
        body.setMassProperties(totalMass.mass(), totalMass.inertia(new Matrix3d()));
    }

    public void initControlAxes() {
        for (WingStrip w : wings) w.updateControlAxis(com);
    }

    public double mass() {
        return body.mass;
    }

    // ------------------------------------------------------------------ tick

    /** Once-per-tick systems: engines, fuel, controls, ballast, gas, gear. */
    public void beginTick(double dt, Environment env) {
        if (frozen) return;
        tickCount++;
        rebuildMass();
        terrain = env.terrainFor(this);

        // ---- flight data
        env.wind(body.position.x, body.position.y, body.position.z, wind);
        tmp.set(body.linearVelocity).sub(wind);
        airspeed = tmp.length();
        altitude = env.altitude(body.position.y);
        airDensity = env.airDensity(body.position.y);
        mach = airspeed / Atmosphere.speedOfSound(altitude);
        body.worldDirToLocal(tmp.x, tmp.y, tmp.z, tmp2);
        double vf = tmp2.dot(forward), vu = tmp2.dot(up), vr = tmp2.dot(right);
        angleOfAttack = airspeed > 1 ? Math.atan2(-vu, Math.max(1e-3, vf)) : 0;
        sideslip = airspeed > 1 ? Math.atan2(vr, Math.max(1e-3, vf)) : 0;
        verticalSpeed = body.linearVelocity.y;
        heightAboveGround = env.heightAboveSurface(body.position.x, body.position.y, body.position.z, 256);
        tmp3.set(body.linearVelocity).sub(lastVelocity).div(dt).add(0, env.gravity(), 0);
        body.localDirToWorld(up.x, up.y, up.z, tmp2);
        gLoad = tmp3.dot(tmp2) / env.gravity();
        lastVelocity.set(body.linearVelocity);

        // ---- control mixing (mouse aim + stability augmentation)
        double pitch = input.pitch, roll = input.roll, yaw = input.yaw;
        if (input.mouseAim && rotors.isEmpty()) {
            mouseAim.compute(body, forward, up, right, input.aimX, input.aimY, input.aimZ, aimOut);
            pitch = clamp(aimOut[0] + input.pitch, -1, 1);
            roll = Math.abs(input.roll) > 0.05 ? input.roll : aimOut[1];
            yaw = clamp(aimOut[2] * 0.5 + input.yaw, -1, 1);
        }
        if (input.assist && !input.mouseAim && rotors.isEmpty() && !wings.isEmpty()) {
            // EASY FLIGHT: W/S move a pitch-attitude target, A/D set a bank angle, release = wings level.
            body.localDirToWorld(forward.x, forward.y, forward.z, tmp);
            body.localDirToWorld(right.x, right.y, right.z, tmp2);
            body.localDirToWorld(up.x, up.y, up.z, tmp3);
            double p = body.angularVelocity.dot(tmp), q = body.angularVelocity.dot(tmp2), r = -body.angularVelocity.dot(tmp3);
            double pitchAngle = Math.asin(clamp(tmp.y, -1, 1));
            double bank = Math.atan2(-tmp2.y, tmp3.y);
            if (!holdingPitch) { holdPitch = clamp(pitchAngle, Math.toRadians(-10), Math.toRadians(15)); holdingPitch = true; }
            holdPitch = clamp(holdPitch + input.pitch * Math.toRadians(7) * dt, Math.toRadians(-15), Math.toRadians(20));
            if (onGround) {
                // automatic rotation at take-off speed (or earlier if the pilot pulls)
                boolean rotate = airspeed > rotateSpeed || (input.pitch > 0.3 && airspeed > rotateSpeed * 0.85);
                holdPitch = rotate ? Math.toRadians(9) : 0;
            }
            if (stallWarning) holdPitch = Math.max(Math.toRadians(-5), holdPitch - Math.toRadians(8) * dt);
            double targetBank = input.roll * Math.toRadians(60);
            double bankComp = Math.toRadians(5) * (1 / Math.max(0.45, Math.cos(bank)) - 1);
            pitch = clamp(2.5 * (holdPitch + bankComp - pitchAngle) - 1.0 * q, -1, 1);
            roll = onGround ? 0 : clamp(2.6 * (targetBank - bank) - 0.7 * p, -1, 1);
            if (!onGround) pitch = clamp(pitch + 0.35 * (1 / Math.max(0.45, Math.cos(bank)) - 1), -1, 1);
            yaw = clamp(input.yaw + (onGround ? input.roll : 0.5 * roll) - 0.3 * r, -1, 1);
            if (onGround && Math.abs(input.yaw) < 0.05 && Math.abs(input.roll) < 0.05 && airspeed > 3) {
                double hdg = Math.atan2(tmp.x, -tmp.z);
                if (!holdingHeading) { holdHeading = hdg; holdingHeading = true; }
                double herr = hdg - holdHeading;
                while (herr > Math.PI) herr -= 2 * Math.PI;
                while (herr < -Math.PI) herr += 2 * Math.PI;
                yaw = clamp(yaw - 3.0 * herr - 0.8 * r, -1, 1);
            } else if (!onGround || Math.abs(input.yaw) >= 0.05 || Math.abs(input.roll) >= 0.05) holdingHeading = false;
        } else if (input.assist) {
            body.localDirToWorld(forward.x, forward.y, forward.z, tmp);
            body.localDirToWorld(right.x, right.y, right.z, tmp2);
            body.localDirToWorld(up.x, up.y, up.z, tmp3);
            double p = body.angularVelocity.dot(tmp), q = body.angularVelocity.dot(tmp2), r = -body.angularVelocity.dot(tmp3);
            double k = 0.35 / Math.max(0.6, airspeed / 40.0);
            if (Math.abs(input.pitch) < 0.05 && !input.mouseAim) {
                pitch -= q * k;
                // attitude hold: keep the pitch angle the pilot released the stick at
                double pitchAngle = Math.asin(clamp(tmp.y, -1, 1));
                double bankNow = Math.atan2(-tmp2.y, tmp3.y);
                if (rotors.isEmpty() && airspeed > 15 && !onGround && Math.abs(bankNow) < Math.toRadians(60)) {
                    if (!holdingPitch) { holdPitch = clamp(pitchAngle, Math.toRadians(-25), Math.toRadians(25)); holdingPitch = true; }
                    pitch += clamp(2.2 * (holdPitch - pitchAngle), -0.6, 0.6);
                    // stall protection
                    if (stallWarning) pitch = Math.min(pitch, -0.3);
                }
            } else {
                holdingPitch = false;
            }
            if (Math.abs(input.roll) < 0.05 && !input.mouseAim) {
                roll -= p * k;
                // wing leveller: gently return to wings-level flight when the pilot releases the stick
                double bank = Math.atan2(-tmp2.y, tmp3.y);
                if (Math.abs(bank) < Math.toRadians(70) && rotors.isEmpty() && airspeed > 10) roll -= clamp(bank * 0.9, -0.5, 0.5);
            }
            if (Math.abs(input.yaw) < 0.05) yaw -= r * k * 0.8;
            // ground roll heading hold (counters propeller torque and crosswind on the runway)
            if (onGround && rotors.isEmpty() && !wings.isEmpty() && Math.abs(input.yaw) < 0.05 && Math.abs(input.steer) < 0.05 && airspeed > 3) {
                double hdg = Math.atan2(tmp.x, -tmp.z);
                if (!holdingHeading) { holdHeading = hdg; holdingHeading = true; }
                double err = hdg - holdHeading;
                while (err > Math.PI) err -= 2 * Math.PI;
                while (err < -Math.PI) err += 2 * Math.PI;
                yaw += clamp(-3.0 * err - 0.8 * r, -0.8, 0.8);
            } else if (Math.abs(input.yaw) >= 0.05 || !onGround) {
                holdingHeading = false;
            }
        }
        if (rotors.isEmpty() && !wings.isEmpty()) {
            // gentler surfaces at high dynamic pressure, rudder coordinated with roll
            double sched = clamp(Math.pow(45.0 / Math.max(45.0, airspeed), 2), 0.3, 1.0);
            if (!input.assist || input.mouseAim) {
                pitch *= sched;
                roll *= sched;
                if (input.assist && !onGround) yaw += 0.3 * roll;
            }
        }
        effPitch = clamp(pitch, -1, 1);
        effRoll = clamp(roll, -1, 1);
        effYaw = clamp(yaw, -1, 1);

        // ---- hydraulics: control authority
        boolean anyRunning = false;
        for (ThrusterUnit t : thrusters) if (t.engine.isRunning()) { anyRunning = true; break; }
        hydraulicAuthority = hydraulicPumps > 0 ? (anyRunning ? 1.0 : 0.55) : (anyRunning ? 0.7 : 0.45);

        // ---- fuel system
        fuelAmount = 0;
        fuelCapacity = 0;
        for (TankUnit t : tanks) if (t.kind == TankUnit.Kind.FUEL) { fuelAmount += t.amount; fuelCapacity += t.capacity; }
        double demand = 0;
        for (ThrusterUnit t : thrusters) demand += t.engine.fuelFlowKgS * fuelScale;
        double cap = BASE_FUEL_FLOW + PUMP_FUEL_FLOW * fuelPumps;
        if (fuelAmount <= 1e-6) fuelSupply = 0;
        else fuelSupply = demand > cap ? cap / demand : 1.0;
        double used = Math.min(demand, cap) * dt;
        fuelFlow = Math.min(demand, cap);
        if (used > 0 && fuelAmount > 0) {
            double f = Math.min(1.0, used / fuelAmount);
            for (TankUnit t : tanks) if (t.kind == TankUnit.Kind.FUEL) t.amount = Math.max(0, t.amount - t.amount * f);
        }

        // ---- engines
        double ambient = env.ambientTemperature(body.position.y);
        double rotorTarget = 0;
        for (ThrusterUnit t : thrusters) {
            EngineSim e = t.engine;
            body.localToWorld(t.localPos.x - com.x, t.localPos.y - com.y, t.localPos.z - com.z, tmp);
            env.sample(tmp.x, tmp.y, tmp.z, sample);
            t.submergedIntake = sample[0] > tmp.y + 0.1 && sample[1] > 500;
            body.localToWorld(t.devicePos.x - com.x, t.devicePos.y - com.y, t.devicePos.z - com.z, tmp2);
            env.sample(tmp2.x, tmp2.y, tmp2.z, sample);
            t.deviceInWater = sample[0] > tmp2.y && sample[1] > 500;
            double sigma = env.airDensity(tmp.y) / Atmosphere.RHO0;
            if (t.submergedIntake && e.type != EngineType.ELECTRIC) sigma = 0;
            switch (t.device) {
                case DRIVETRAIN -> e.throttle = Math.abs(input.drive) > 0.05 ? Math.abs(input.drive) : 0.0;
                case ROTOR -> {
                    // governor handled below; throttle set from rotor speed error
                }
                default -> e.throttle = input.groupThrottle(e.throttleGroup);
            }
            e.afterburner = input.afterburner;
            double axial = airspeed * Math.max(0, Math.cos(angleOfAttack));
            e.update(dt, fuelSupply, sigma, axial, ambient, hasStarter, t.submergedIntake);
        }

        // ---- rotors: governor and collective/cyclic
        if (!rotors.isEmpty()) {
            RotorUnit main = null;
            for (RotorUnit r : rotors) if (!r.tail) { main = r; break; }
            if (main != null) {
                RotorModel m = main.model;
                rotorTarget = m.nominalOmega * clamp(input.throttle * 1.1, 0, 1);
                double err = (rotorTarget - m.omega) / m.nominalOmega;
                double thr = clamp(0.55 + 4.0 * err + m.lastTorque * m.nominalOmega / 1.2e6, 0, 1);
                if (input.throttle < 0.02) thr = 0;
                for (ThrusterUnit t : thrusters) if (t.device == ThrusterUnit.Device.ROTOR) t.engine.throttle = thr;
                double collectiveCmd = clamp(input.collective, 0, 1);
                double cp = -effPitch, cr = effRoll;
                if (input.assist) {
                    body.localDirToWorld(forward.x, forward.y, forward.z, tmp);
                    body.localDirToWorld(right.x, right.y, right.z, tmp2);
                    double pitchAngle = Math.asin(clamp(tmp.y, -1, 1));
                    double bank = Math.atan2(-tmp2.y, Math.max(1e-3, Math.sqrt(1 - tmp2.y * tmp2.y)));
                    double p = body.angularVelocity.dot(tmp), q = body.angularVelocity.dot(tmp2);
                    // attitude command: the stick sets a tilt angle; released = hold position (damp drift)
                    double hx = tmp.x, hz = tmp.z, hl = Math.max(1e-3, Math.hypot(hx, hz));
                    double vFwd = (body.linearVelocity.x * hx + body.linearVelocity.z * hz) / hl;
                    double vRight = (body.linearVelocity.x * tmp2.x + body.linearVelocity.z * tmp2.z) / Math.max(1e-3, Math.hypot(tmp2.x, tmp2.z));
                    double targetPitch = Math.abs(input.pitch) > 0.05 ? input.pitch * Math.toRadians(16) : clamp(0.025 * vFwd, -0.17, 0.17);
                    double targetBank = Math.abs(input.roll) > 0.05 ? input.roll * Math.toRadians(20) : clamp(-0.025 * vRight, -0.17, 0.17);
                    if (onGround) { targetPitch = 0; targetBank = 0; }
                    cp = clamp(1.8 * (pitchAngle - targetPitch) + 0.9 * q, -1, 1);
                    cr = clamp(-1.8 * (bank - targetBank) - 0.9 * p, -1, 1);
                    // vertical speed hold: Space/Ctrl command ±5 m/s, released = hover
                    if (m.omega > m.nominalOmega * 0.8) {
                        // feed-forward: collective that makes thrust equal weight (momentum theory hover)
                        double tip = m.nominalOmega * m.radius;
                        double ct = body.mass * env.gravity() / Math.max(1, airDensity * m.diskArea() * tip * tip * Math.max(1, rotors.stream().filter(x -> !x.tail).count()));
                        double lam = Math.sqrt(Math.max(1e-6, ct) / 2);
                        double theta = 3 * (ct / (m.solidity() * m.liftSlope / 2) + lam / 2);
                        double hover = clamp((theta - m.minCollectiveRad) / (m.maxCollectiveRad - m.minCollectiveRad), 0, 1);
                        double targetVs = input.climb * 5.0;
                        double vsErr = targetVs - body.linearVelocity.y;
                        if (onGround && input.climb <= 0) {
                            collectiveTrim = 0;
                            collectiveCmd = 0;
                        } else {
                            collectiveTrim = clamp(collectiveTrim + vsErr * 0.04 * dt, -0.35, 0.35);
                            collectiveCmd = clamp(hover + collectiveTrim + 0.06 * vsErr, 0, 1);
                        }
                    } else {
                        collectiveTrim = 0;
                        collectiveCmd = 0;
                    }
                }
                m.collective = m.minCollectiveRad + (m.maxCollectiveRad - m.minCollectiveRad) * collectiveCmd;
                for (RotorUnit r : rotors) {
                    if (r.tail) continue;
                    r.cyclicPitch = approach(r.cyclicPitch, cp * r.maxCyclicRad, Math.toRadians(30) * dt);
                    r.cyclicRoll = approach(r.cyclicRoll, cr * r.maxCyclicRad, Math.toRadians(30) * dt);
                }
                // tail rotor pedals with yaw-rate assist
                body.localDirToWorld(up.x, up.y, up.z, tmp3);
                double yawRate = -body.angularVelocity.dot(tmp3);
                for (RotorUnit r : rotors) {
                    if (!r.tail) continue;
                    r.model.omega = m.omega * 4.6;
                    // yaw-right response of positive tail pitch, from geometry
                    double s = -tmp.set(r.localPos).sub(com).cross(r.axisLocal).dot(up);
                    s = s >= 0 ? 1 : -1;
                    if (input.assist) {
                        double desired = effYaw * 1.1;
                        double e = desired - yawRate;
                        tailTrim = clamp(tailTrim + s * e * 0.8 * dt, -1, 1);
                        r.tailPitch = clamp(tailTrim + 0.9 * s * e, -1, 1);
                    } else {
                        r.tailPitch = clamp(-0.35 * s * main.spinDirection + s * effYaw, -1, 1);
                    }
                    r.model.collective = r.tailPitch * Math.toRadians(18);
                }
            }
        }

        // ---- control surfaces
        double speedFactor = clamp((airspeed - 60) / 120.0, 0, 1);
        for (WingStrip w : wings) {
            w.updateControls(effPitch, effRoll, effYaw, input.flaps, input.spoilers, right, forward, up, speedFactor, hydraulicAuthority, dt);
            body.localToWorld(w.localCenter.x - com.x, w.localCenter.y - com.y, w.localCenter.z - com.z, tmp);
            w.groundHeight = env.heightAboveSurface(tmp.x, tmp.y, tmp.z, 64);
        }
        for (RotorUnit r : rotors) {
            body.localToWorld(r.localPos.x - com.x, r.localPos.y - com.y, r.localPos.z - com.z, tmp);
            r.groundHeight = env.heightAboveSurface(tmp.x, tmp.y, tmp.z, 64);
        }

        // ---- ballast tanks
        ballastFill = 0;
        int nb = 0;
        for (TankUnit t : tanks) {
            if (t.kind != TankUnit.Kind.BALLAST) continue;
            nb++;
            body.localToWorld(t.localPos.x - com.x, t.localPos.y - com.y, t.localPos.z - com.z, tmp);
            env.sample(tmp.x, tmp.y, tmp.z, sample);
            t.intakeSubmerged = sample[0] > tmp.y - 0.4 && sample[1] > 500;
            if (input.ballast > 0.05 && t.intakeSubmerged) t.amount = Math.min(t.capacity, t.amount + t.capacity * 0.06 * input.ballast * dt);
            else if (input.ballast < -0.05) t.amount = Math.max(0, t.amount + t.capacity * 0.05 * input.ballast * dt);
            ballastFill += t.fill();
        }
        if (nb > 0) ballastFill /= nb;

        // ---- hull flooding (open pockets with submerged rim, or breached pockets set by the game layer)
        if (fluid != null) {
            if (pocketFlood.length != fluid.pockets.size()) pocketFlood = new double[fluid.pockets.size()];
            for (int i = 0; i < fluid.pockets.size(); i++) {
                var p = fluid.pockets.get(i);
                if (p.sealed || p.rim.length == 0) continue;
                int submerged = 0;
                for (int k = 0; k < p.rim.length; k += Math.max(1, p.rim.length / 6)) {
                    long c = p.rim[k];
                    body.localToWorld(VoxelSet.unpackX(c) + 0.5 - com.x,
                            VoxelSet.unpackY(c) + 1.0 - com.y,
                            VoxelSet.unpackZ(c) + 0.5 - com.z, tmp);
                    env.sample(tmp.x, tmp.y, tmp.z, sample);
                    if (sample[0] > tmp.y && sample[1] > 500) submerged++;
                }
                if (submerged > 0) pocketFlood[i] = Math.min(1, pocketFlood[i] + 0.05 * submerged * dt * 20 / Math.max(10, p.volume));
                else pocketFlood[i] = Math.max(0, pocketFlood[i] - 0.01 * dt); // bilge pumps / draining
            }
        }

        // ---- lifting gas
        if (fluid != null && !fluid.gasPockets.isEmpty()) {
            int n = fluid.gasPockets.size();
            if (gasDensity.length != n) { gasDensity = new double[n]; }
            if (gasFill.length != n) { gasFill = new double[n]; java.util.Arrays.fill(gasFill, 1.0); }
            double ambientK = ambient + 273.15;
            double rhoAir = airDensity;
            double vol = 0;
            for (int i = 0; i < n; i++) if (!isHelium(i)) vol += fluid.gasPockets.get(i).volume;
            if (hotAir && vol > 0) {
                double burner = burners * burnerPowerW * clamp(input.lift, 0, 1) * (fuelSupply > 0 ? 1 : 0);
                double airMass = Math.max(1, rhoAir * vol * gasLiftScale);
                double cool = (0.004 + (input.lift < -0.05 ? 0.02 * -input.lift : 0)) * (envelopeTempC - ambient);
                envelopeTempC += (burner / (1005.0 * airMass) - cool) * dt;
                envelopeTempC = clamp(envelopeTempC, ambient, 130);
                if (burner > 0 && fuelAmount > 0) {
                    double u = burner / 46e6 * dt;
                    double f = Math.min(1.0, u / fuelAmount);
                    for (TankUnit t : tanks) if (t.kind == TankUnit.Kind.FUEL) t.amount -= t.amount * f;
                }
            } else {
                envelopeTempC = ambient;
            }
            double tK = envelopeTempC + 273.15;
            for (int i = 0; i < n; i++) {
                if (isHelium(i)) {
                    gasDensity[i] = rhoAir * 0.138; // helium at ambient pressure/temperature
                    gasFill[i] = clamp(gasFill[i] + input.lift * 0.04 * dt, 0.15, 1.0);
                } else {
                    gasDensity[i] = rhoAir * ambientK / tK;
                }
            }
        }

        // ---- landing gear and steering
        for (WheelSim w : wheels) {
            if (w.params.retractable) {
                boolean down = input.gearDown;
                w.retracted = !down && w.extension <= 0.01;
                w.extension = approach(w.extension, down ? 1 : 0, 0.33 * Math.max(0.3, hydraulicAuthority) * dt);
                if (!down) w.retracted = true;
                if (down && w.extension >= 0.95) w.retracted = false;
            }
            if (w.params.steerable) {
                double steerCmd = rotors.isEmpty() && !wings.isEmpty() && !hasDrivenWheels() ? effYaw : input.steer;
                double speedLimit = hasDrivenWheels() ? 1.0 / (1.0 + Math.max(0, airspeed - 20) / 60.0) : 1.0 / (1.0 + Math.max(0, airspeed - 12) / 30.0);
                w.steerAngle = approach(w.steerAngle, steerCmd * Math.toRadians(w.params.maxSteerDeg) * speedLimit, Math.toRadians(hasDrivenWheels() ? 360 : 260) * dt);
            }
        }
        // ---- transmission
        if (hasDrivenWheels()) {
            if (input.gearShiftRequest > 0) { transmission.automatic = false; transmission.shiftUp(); }
            if (input.gearShiftRequest < 0) { transmission.automatic = false; transmission.shiftDown(); }
            input.gearShiftRequest = 0;
            body.localDirToWorld(forward.x, forward.y, forward.z, tmp);
            double fwdSpeed = body.linearVelocity.dot(tmp);
            transmission.autoShift(carEngineRpmFraction, fwdSpeed, input.drive > 0.05 ? 1 : (input.drive < -0.05 ? -1 : 0), dt);
        }
    }

    private boolean isHelium(int i) {
        return i < gasHelium.length ? gasHelium[i] : !hotAir;
    }

    public boolean hasDrivenWheels() {
        for (WheelSim w : wheels) if (w.params.driven) return true;
        return false;
    }

    /** Per-substep force accumulation. */
    public void applyForces(double dt, Environment env) {
        if (frozen) return;
        totalLift = totalDrag = totalThrust = 0;
        stallWarning = false;
        double windX = wind.x, windY = wind.y, windZ = wind.z;

        // ---- thrusters
        for (ThrusterUnit t : thrusters) {
            EngineSim e = t.engine;
            double thrust = 0;
            body.localDirToWorld(t.localDir.x, t.localDir.y, t.localDir.z, tmp2);
            switch (t.device) {
                case JET -> {
                    thrust = e.thrustN;
                    body.localToWorld(t.localPos.x - com.x, t.localPos.y - com.y, t.localPos.z - com.z, tmp);
                }
                case PROPELLER, MARINE_PROPELLER -> {
                    body.localToWorld(t.devicePos.x - com.x, t.devicePos.y - com.y, t.devicePos.z - com.z, tmp);
                    body.velocityAtPoint(tmp.x, tmp.y, tmp.z, tmp3);
                    double axial = tmp3.dot(tmp2);
                    boolean marine = t.device == ThrusterUnit.Device.MARINE_PROPELLER;
                    if (marine == t.deviceInWater) {
                        double rho = marine ? 1000.0 : env.airDensity(tmp.y);
                        thrust = PropellerModel.thrust(e.shaftPowerW, marine && input.reverseThrust ? -axial : axial, rho,
                                marine ? Math.min(t.propDiameter, 1.6) : t.propDiameter, t.propEfficiency);
                        if (marine && input.reverseThrust) thrust = -0.6 * thrust;
                    }
                    t.propOmega = e.isRunning() || e.spool > 0.01 ? e.spool * e.maxRpm() * Math.PI / 30.0 : 0;
                    // propeller torque reaction (roll)
                    if (t.propOmega > 1 && e.shaftPowerW > 0) {
                        double q = e.shaftPowerW / t.propOmega * 0.25;
                        body.applyTorque(tmp3.set(tmp2).mul(-q));
                    }
                }
                default -> {}
            }
            t.lastThrust = thrust;
            if (thrust != 0) {
                body.applyForceAtPoint(tmp2.x * thrust, tmp2.y * thrust, tmp2.z * thrust, tmp.x, tmp.y, tmp.z);
                totalThrust += Math.abs(thrust);
            }
            t.propSpin += t.propOmega * dt;
        }

        // ---- rotors
        double rotorShaftPower = 0;
        for (ThrusterUnit t : thrusters) if (t.device == ThrusterUnit.Device.ROTOR) rotorShaftPower += t.engine.shaftPowerW;
        double tailTorqueLoad = 0;
        for (RotorUnit r : rotors) if (r.tail) tailTorqueLoad += r.model.lastTorque / 4.6;
        for (RotorUnit r : rotors) {
            RotorModel m = r.model;
            body.localToWorld(r.localPos.x - com.x, r.localPos.y - com.y, r.localPos.z - com.z, tmp);
            // thrust axis with cyclic tilt
            double ax = r.axisLocal.x, ay = r.axisLocal.y, az = r.axisLocal.z;
            if (!r.tail) {
                double tp = Math.tan(r.cyclicPitch), tr = Math.tan(r.cyclicRoll);
                ax += forward.x * tp + right.x * tr;
                ay += forward.y * tp + right.y * tr;
                az += forward.z * tp + right.z * tr;
            }
            body.localDirToWorld(ax, ay, az, tmp2).normalize();
            body.velocityAtPoint(tmp.x, tmp.y, tmp.z, tmp3);
            tmp3.sub(windX, windY, windZ);
            double axial = tmp3.dot(tmp2);
            double inplane = Math.sqrt(Math.max(0, tmp3.lengthSquared() - axial * axial));
            double rho = env.airDensity(tmp.y);
            m.compute(rho, axial, inplane, r.groundHeight);
            if (!r.tail) {
                double engineTorque = m.omega > 2 ? Math.min(rotorShaftPower / m.omega, 3.0e5) : (rotorShaftPower > 0 ? 6e4 : 0);
                m.lastTorque += tailTorqueLoad;
                m.integrate(engineTorque, dt);
                // reaction of the engine torque on the airframe
                double react = -engineTorque * r.spinDirection;
                body.localDirToWorld(r.axisLocal.x, r.axisLocal.y, r.axisLocal.z, tmp3);
                body.applyTorque(tmp3.mul(react));
                // hub moment from cyclic (rigid rotor stiffness)
                double hub = Math.max(0, m.lastThrust) * r.hubStiffness;
                body.localDirToWorld(right.x, right.y, right.z, tmp3);
                body.applyTorque(tmp3.mul(-hub * Math.sin(r.cyclicPitch)));
                body.localDirToWorld(forward.x, forward.y, forward.z, tmp3);
                body.applyTorque(tmp3.mul(hub * Math.sin(r.cyclicRoll)));
            }
            double th = m.lastThrust;
            r.lastThrust = th;
            body.applyForceAtPoint(tmp2.x * th, tmp2.y * th, tmp2.z * th, tmp.x, tmp.y, tmp.z);
            totalThrust += Math.abs(th);
            r.bladeAngle += m.omega * dt * r.spinDirection;
        }

        // ---- wings
        for (WingStrip w : wings) {
            body.localToWorld(w.localCenter.x - com.x, w.localCenter.y - com.y, w.localCenter.z - com.z, tmp);
            env.sample(tmp.x, tmp.y, tmp.z, sample);
            double rho = (sample[0] > tmp.y && sample[1] > 0) ? sample[1] : env.airDensity(tmp.y);
            w.applyForces(body, com, rho, windX, windY, windZ, mach, w.damageFactor);
            totalLift += w.lastLift;
            totalDrag += w.lastDrag;
            if (w.lastStalled && w.lastDynamicPressure > 50) stallWarning = true;
        }

        // ---- fluid (buoyancy + body drag) and gas lift
        if (fluid != null) {
            fluidForces.gravity = env.gravity();
            fluidForces.apply(body, com, fluid, env, pocketFlood);
            buoyancy = fluidForces.buoyancyForce;
            inWater = fluidForces.anyWater;
            if (!fluid.gasPockets.isEmpty()) gasLift = fluidForces.applyGasLift(body, com, fluid, env, gasDensity, gasFill, gasLiftScale);
        }

        // ---- wheels and drivetrain
        if (!wheels.isEmpty()) {
            double drivePower = 0;
            boolean drivetrainRunning = false;
            for (ThrusterUnit t : thrusters) {
                if (t.device != ThrusterUnit.Device.DRIVETRAIN) continue;
                if (t.engine.isRunning()) drivetrainRunning = true;
                drivePower += t.engine.type.maxPowerW * t.engine.powerScale * (1 - 0.6 * t.engine.damage) * (t.engine.isRunning() ? 1 : 0)
                        * Math.min(1, fuelSupply * 1.0 + (t.engine.type == EngineType.ELECTRIC ? 1 : 0));
            }
            int driven = 0;
            double avgOmega = 0;
            for (WheelSim w : wheels) if (w.params.driven) { driven++; avgOmega += w.omega; }
            if (driven > 0) avgOmega /= driven;
            double ratio = transmission.ratio();
            double maxOmegaE = 6000 * Math.PI / 30.0; // car engine redline 6000 rpm
            double idleOmega = 900 * Math.PI / 30.0;
            double engOmega = Math.abs(avgOmega * ratio);
            double throttle = 0;
            body.localDirToWorld(forward.x, forward.y, forward.z, tmp);
            double fwdSpeed = body.linearVelocity.dot(tmp);
            boolean braking = false;
            if (input.drive > 0.05) { if (transmission.gear < 0 && fwdSpeed < -0.5) braking = true; else throttle = input.drive; }
            else if (input.drive < -0.05) { if (transmission.gear > 0 && fwdSpeed > 0.5) braking = true; else throttle = -input.drive; }
            double rpmFrac = Math.max(idleOmega, engOmega) / maxOmegaE;
            carEngineRpmFraction = drivetrainRunning ? Math.min(1.1, Math.max(rpmFrac, 0.15 + 0.5 * throttle * (engOmega < idleOmega * 1.5 ? 1 : 0))) : 0;
            double torque = 0;
            if (drivetrainRunning && ratio != 0 && throttle > 0) {
                double peakT = drivePower / (maxOmegaE * 0.7);
                double x = rpmFrac;
                double curve = clamp(1.0 - 1.3 * (x - 0.55) * (x - 0.55), 0.35, 1.0);
                if (x > 1.0) curve = 0; // rev limiter
                double converter = engOmega < idleOmega * 1.5 ? 1.0 + 1.2 * (1 - engOmega / (idleOmega * 1.5)) : 1.0;
                torque = throttle * peakT * curve * converter * ratio * transmission.efficiency;
                if (transmission.shiftTimer > 0) torque *= 0.2;
            }
            double lock = differential == 2 ? 0.9 : (differential == 1 ? 0.35 : 0.0);
            for (WheelSim w : wheels) {
                w.driveTorque = 0;
                if (w.params.driven && driven > 0) {
                    w.driveTorque = torque / driven;
                    if (lock > 0) w.driveTorque += lock * (avgOmega - w.omega) * w.params.inertia() / dt * 0.5;
                }
                double br = 0;
                if (input.brake || braking) br = input.brake ? 1.0 : Math.min(1.0, Math.abs(input.drive));
                if (input.hardBrake) br = 3.0;
                // traction control: cut drive torque on a spinning wheel
                if (w.params.driven && w.grounded && Math.abs(w.slipRatio) > 0.3) w.driveTorque *= 0.6;
                if (input.handbrake && w.params.axleSign <= 0) br = 1.0;
                if (!drivetrainRunning && driven > 0 && Math.abs(input.drive) < 0.05 && !hasPilotThrust()) br = Math.max(br, 0.15);
                w.brakeInput = br;
                w.wheelCount = wheels.size();
                w.step(body, com, terrain, dt, env::frictionAt);
            }
            groundAssist(driven, dt);
        }
    }

    /** Strong brake (B) and car stability assist: easier, more forgiving ground handling. */
    private void groundAssist(int driven, double dt) {
        int grounded = 0;
        for (WheelSim w : wheels) if (w.grounded && !w.retracted) grounded++;
        if (grounded == 0) return;
        double gf = Math.min(1.0, grounded / Math.max(1.0, wheels.size() * 0.75));
        double vx = body.linearVelocity.x, vz = body.linearVelocity.z;
        double sp = Math.sqrt(vx * vx + vz * vz);
        if (input.hardBrake && sp > 0.05) {
            double a = Math.min(10.0 * gf, sp / dt);
            body.applyForce(-vx / sp * a * body.mass, 0, -vz / sp * a * body.mass);
        }
        if (driven > 0 && wings.isEmpty() && rotors.isEmpty() && !input.handbrake) {
            // remove sideways sliding (keeps the car pointing where it goes) and damp spin-outs
            body.localDirToWorld(right.x, right.y, right.z, tmp2);
            double vLat = body.linearVelocity.dot(tmp2);
            double aLat = clamp(-vLat * 4.0, -7, 7) * gf;
            body.applyForce(tmp2.x * aLat * body.mass, 0, tmp2.z * aLat * body.mass);
            if (Math.abs(input.drive) < 0.05 && !input.brake && sp < 1.5) {
                double a = Math.min(3.0 * gf, sp / dt); // coast to a stop instead of rolling forever
                if (sp > 0.01) body.applyForce(-vx / sp * a * body.mass, 0, -vz / sp * a * body.mass);
            }
        }
    }

    private boolean hasPilotThrust() {
        for (ThrusterUnit t : thrusters) if (t.device != ThrusterUnit.Device.DRIVETRAIN && t.engine.isRunning()) return true;
        return false;
    }

    /** Generate terrain contacts for this substep. */
    public void collectTerrainContacts(CollisionDetector detector, ContactBuffer out, double margin) {
        if (frozen || shape == null || terrain == null) return;
        detector.detectTerrain(body, shape, com, terrain, out, margin, (int) maxContactsPerTick);
    }

    public void endTick(double dt) {
        if (frozen) return;
        onGround = false;
        for (WheelSim w : wheels) if (w.grounded) { onGround = true; break; }
        boolean active = Math.abs(input.drive) > 0.05 || input.throttle > 0.02 || inWater || Math.abs(input.ballast) > 0.05 || Math.abs(input.lift) > 0.05;
        for (ThrusterUnit t : thrusters) if (t.engine.state != EngineSim.State.OFF) { active = true; break; }
        for (RotorUnit r : rotors) if (r.model.omega > 0.5) { active = true; break; }
        if (gasLift > 0) active = true;
        if (active) body.wake();
        else body.updateSleep(0.06, 0.04, 40);
    }

    /** World-space axis-aligned bounds of the collision shape: out = {minX,minY,minZ,maxX,maxY,maxZ}. */
    public void worldBounds(double[] out) {
        out[0] = out[1] = out[2] = Double.MAX_VALUE;
        out[3] = out[4] = out[5] = -Double.MAX_VALUE;
        if (shape == null || shape.isEmpty()) {
            out[0] = out[3] = body.position.x; out[1] = out[4] = body.position.y; out[2] = out[5] = body.position.z;
            return;
        }
        for (int c = 0; c < 8; c++) {
            double x = ((c & 1) == 0 ? shape.minX : shape.maxX) - com.x;
            double y = ((c & 2) == 0 ? shape.minY : shape.maxY) - com.y;
            double z = ((c & 4) == 0 ? shape.minZ : shape.maxZ) - com.z;
            body.localToWorld(x, y, z, tmp);
            out[0] = Math.min(out[0], tmp.x); out[1] = Math.min(out[1], tmp.y); out[2] = Math.min(out[2], tmp.z);
            out[3] = Math.max(out[3], tmp.x); out[4] = Math.max(out[4], tmp.y); out[5] = Math.max(out[5], tmp.z);
        }
    }

    /** World position of a structure-local point (block coordinates). */
    public Vector3d structureToWorld(double lx, double ly, double lz, Vector3d out) {
        return body.localToWorld(lx - com.x, ly - com.y, lz - com.z, out);
    }

    public Vector3d worldToStructure(double wx, double wy, double wz, Vector3d out) {
        body.worldToLocal(wx, wy, wz, out);
        return out.add(com);
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static double approach(double v, double target, double maxDelta) {
        if (v < target) return Math.min(target, v + maxDelta);
        return Math.max(target, v - maxDelta);
    }
}
