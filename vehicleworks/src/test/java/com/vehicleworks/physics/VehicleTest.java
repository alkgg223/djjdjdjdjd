package com.vehicleworks.physics;

import com.vehicleworks.physics.aero.ControlRole;
import com.vehicleworks.physics.aero.WingParams;
import com.vehicleworks.physics.aero.WingType;
import com.vehicleworks.physics.ground.WheelParams;
import com.vehicleworks.physics.ground.WheelSim;
import com.vehicleworks.physics.propulsion.EngineSim;
import com.vehicleworks.physics.propulsion.EngineType;
import com.vehicleworks.physics.sim.*;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleTest {

    private static WheelSim wheel(VehicleSimulation sim, double x, double y, double z, WheelParams p) {
        WheelSim w = new WheelSim(p);
        w.mountLocal.set(x, y, z);
        sim.wheels.add(w);
        return w;
    }

    private static TankUnit fuel(VehicleSimulation sim, double x, double y, double z) {
        TankUnit t = new TankUnit(TankUnit.Kind.FUEL, 400);
        t.amount = 400;
        t.localPos.set(x, y, z);
        sim.tanks.add(t);
        return t;
    }

    static VehicleSimulation buildPlane() {
        SimBuilder sb = new SimBuilder();
        for (int z = 0; z < 10; z++) sb.voxel(0, 1, z, 140);
        VehicleSimulation sim = sb.sim;
        // main wing at z=3..4 (forward = -z, so nose at z=0)
        for (int i = 1; i <= 6; i++) {
            for (int side = -1; side <= 1; side += 2) {
                WingParams p = new WingParams().applyTypeDefaults(WingType.STANDARD);
                p.incidenceDeg = 3;
                if (i >= 5) p.role = ControlRole.AILERON;
                if (i <= 2) { p.role = ControlRole.FLAP; }
                sb.wing(side * i + 0.5, 1.5, 5.5, 1.6, 8, 12, p);
                sb.voxel(side * i, 1, 5, 25, 0.6f, com.vehicleworks.physics.fluid.StructureAnalysis.FLAG_NO_BODY_DRAG);
            }
        }
        // horizontal tail
        for (int i = -2; i <= 2; i++) {
            WingParams p = new WingParams();
            p.profile = com.vehicleworks.physics.aero.AirfoilProfile.SYMMETRIC;
            p.incidenceDeg = -1.5;
            p.role = ControlRole.ELEVATOR;
            p.controlMaxDeg = 25;
            if (i != 0) sb.wing(i + 0.5, 1.5, 9.5, 1.2, 4, 4, p);
        }
        sb.fin(0.5, 2.5, 9.5, 1.2, ControlRole.RUDDER);
        sb.voxel(0, 2, 9, 30);
        EngineSim e = new EngineSim(EngineType.TURBOJET, 7);
        e.powerScale = 0.35;
        ThrusterUnit jet = new ThrusterUnit(e, ThrusterUnit.Device.JET);
        jet.localPos.set(0.5, 1.5, 8.5);
        jet.localDir.set(0, 0, -1);
        sim.thrusters.add(jet);
        sim.baseMass.addBox(EngineType.TURBOJET.massKg * 0.35, 0.5, 1.5, 8.5, 1, 1, 1);
        sim.hasStarter = true;
        fuel(sim, 0.5, 1.5, 5.0);
        WheelParams nose = new WheelParams();
        nose.steerable = true; nose.braked = false; nose.stiffness = 30000; nose.damping = 6000; nose.restLength = 0.5;
        WheelParams main = new WheelParams();
        main.stiffness = 45000; main.damping = 9000; main.restLength = 0.5;
        wheel(sim, 0.5, 1.0, 1.0, nose);
        wheel(sim, -1.5, 1.0, 6.6, main);
        wheel(sim, 2.5, 1.0, 6.6, main);
        sim.fuelPumps = 1;
        return sb.build(0.5, 1.6, 5.5);
    }

    @Test
    void aircraftTakesOffAndClimbs() {
        TestEnv env = new TestEnv();
        PhysicsWorld w = new PhysicsWorld();
        VehicleSimulation sim = buildPlane();
        w.sims.add(sim);
        for (int i = 0; i < 40; i++) w.step(0.05, env);
        double groundY = sim.body.position.y;
        sim.thrusters.get(0).engine.requestStart();
        sim.input.throttle = 1;
        sim.input.flaps = 0.5;
        double maxAlt = 0;
        for (int i = 0; i < 1200; i++) {
            sim.input.pitch = sim.airspeed > 42 ? 0.35 : 0;
            if (sim.body.position.y - groundY > 15) { sim.input.pitch = 0; sim.input.gearDown = false; sim.input.flaps = 0; }
            w.step(0.05, env);
            maxAlt = Math.max(maxAlt, sim.body.position.y - groundY);
        }
        assertTrue(sim.thrusters.get(0).engine.isRunning());
        assertTrue(maxAlt > 40, "max altitude " + maxAlt);
        assertTrue(sim.body.position.y - groundY > 20, "still flying at " + (sim.body.position.y - groundY));
        assertTrue(sim.fuelAmount < 400, "fuel consumed");
    }

    @Test
    void wingRollsTheAircraftTheCommandedWay() {
        TestEnv env = new TestEnv();
        env.ground = false;
        PhysicsWorld w = new PhysicsWorld();
        VehicleSimulation sim = buildPlane();
        sim.body.position.y = 500;
        sim.body.linearVelocity.set(0, 0, -70);
        sim.input.assist = false;
        sim.input.roll = 1;
        w.sims.add(sim);
        for (int i = 0; i < 10; i++) w.step(0.05, env);
        Vector3d fw = sim.body.localDirToWorld(0, 0, -1, new Vector3d());
        double rollRate = sim.body.angularVelocity.dot(fw);
        assertTrue(rollRate > 0.2, "roll right rate " + rollRate);
        sim.input.roll = 0;
        sim.input.pitch = 1;
        sim.body.angularVelocity.zero();
        sim.body.orientation.identity();
        sim.body.updateDerived();
        sim.body.linearVelocity.set(0, 0, -70);
        for (int i = 0; i < 6; i++) w.step(0.05, env);
        Vector3d rw = sim.body.localDirToWorld(1, 0, 0, new Vector3d());
        assertTrue(sim.body.angularVelocity.dot(rw) > 0.1, "nose up rate");
    }

    @Test
    void carAcceleratesSteersAndBrakes() {
        TestEnv env = new TestEnv();
        PhysicsWorld w = new PhysicsWorld();
        SimBuilder sb = new SimBuilder();
        sb.box(0, 1, 0, 2, 1, 4, 80);
        VehicleSimulation sim = sb.sim;
        EngineSim e = new EngineSim(EngineType.PISTON, 3);
        e.powerScale = 0.6;
        ThrusterUnit eng = new ThrusterUnit(e, ThrusterUnit.Device.DRIVETRAIN);
        eng.localPos.set(1.5, 1.5, 0.5);
        sim.thrusters.add(eng);
        fuel(sim, 1.5, 1.5, 2.5);
        for (int i = 0; i < 4; i++) {
            WheelParams p = new WheelParams();
            boolean front = i < 2;
            p.steerable = front;
            p.driven = !front;
            p.axleSign = front ? 1 : -1;
            p.stiffness = 40000; p.damping = 5000; p.restLength = 0.5;
            wheel(sim, (i % 2 == 0) ? -0.5 : 3.5, 1.5, front ? 0.5 : 4.5, p);
        }
        sim.body.angularDamping = 0.02;
        sb.build(1.5, 2.0, 2.5);
        w.sims.add(sim);
        for (int i = 0; i < 40; i++) w.step(0.05, env);
        e.requestStart();
        for (int i = 0; i < 60; i++) w.step(0.05, env);
        assertTrue(e.isRunning());
        sim.input.drive = 1;
        for (int i = 0; i < 200; i++) w.step(0.05, env);
        double speed = sim.body.linearVelocity.length();
        assertTrue(speed > 12, "speed " + speed);
        Vector3d fw0 = sim.body.localDirToWorld(0, 0, -1, new Vector3d());
        assertTrue(sim.body.linearVelocity.dot(fw0) > 10, "moves forward");
        sim.input.steer = 1;
        sim.input.drive = 0.3;
        for (int i = 0; i < 40; i++) w.step(0.05, env);
        Vector3d fw1 = sim.body.localDirToWorld(0, 0, -1, new Vector3d());
        double turned = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, fw0.dot(fw1)))));
        assertTrue(turned > 20, "turned " + turned);
        // right turn: forward rotates toward +x when starting north (-z)
        assertTrue(fw0.cross(fw1, new Vector3d()).y < 0, "turned right");
        sim.input.steer = 0;
        sim.input.drive = 0;
        sim.input.brake = true;
        for (int i = 0; i < 160; i++) w.step(0.05, env);
        assertTrue(sim.body.linearVelocity.length() < 1.0, "stopped " + sim.body.linearVelocity.length());
        Vector3d upW = sim.body.localDirToWorld(0, 1, 0, new Vector3d());
        assertTrue(upW.y > 0.9, "stayed upright");
    }

    @Test
    void helicopterHoversLevelWithAssist() {
        TestEnv env = new TestEnv();
        PhysicsWorld w = new PhysicsWorld();
        SimBuilder sb = new SimBuilder();
        sb.box(0, 1, 0, 2, 2, 5, 90);
        for (int z = 6; z < 11; z++) sb.voxel(1, 2, z, 40);
        VehicleSimulation sim = sb.sim;
        EngineSim e = new EngineSim(EngineType.TURBOPROP, 4);
        e.powerScale = 1.4;
        ThrusterUnit eng = new ThrusterUnit(e, ThrusterUnit.Device.ROTOR);
        eng.localPos.set(1.5, 2.5, 3.5);
        sim.thrusters.add(eng);
        sim.hasStarter = true;
        fuel(sim, 1.5, 1.5, 3.0);
        RotorUnit main = new RotorUnit();
        main.localPos.set(1.5, 3.5, 3.1);
        sim.rotors.add(main);
        RotorUnit tail = new RotorUnit();
        tail.tail = true;
        tail.localPos.set(2.5, 2.5, 10.5);
        tail.axisLocal.set(1, 0, 0);
        tail.model.radius = 1.5;
        tail.model.blades = 4;
        tail.model.chord = 0.2;
        tail.model.inertia = 20;
        sim.rotors.add(tail);
        for (int i = 0; i < 2; i++) {
            WheelParams p = new WheelParams();
            p.stiffness = 60000; p.damping = 12000; p.restLength = 0.4;
            wheel(sim, i == 0 ? -0.5 : 3.5, 1.5, 2.5, p);
        }
        sb.build(1.5, 2.2, 3.5);
        sim.baseMass.addBox(400, 1.5, 1.5, 3.0, 1, 1, 1);
        w.sims.add(sim);
        for (int i = 0; i < 30; i++) w.step(0.05, env);
        double groundY = sim.body.position.y;
        e.requestStart();
        sim.input.throttle = 1;
        for (int i = 0; i < 300; i++) w.step(0.05, env);
        assertTrue(main.model.omega > main.model.nominalOmega * 0.85, "rotor rpm " + main.model.omega);
        double maxClimb = 0;
        for (int i = 0; i < 400; i++) {
            sim.input.collective = Math.min(0.75, sim.input.collective + 0.01);
            w.step(0.05, env);
            if (Boolean.getBoolean("vw.debug") || System.getenv("VW_DEBUG") != null) if (i % 20 == 0) System.out.printf("heli t=%d h=%.2f omega=%.1f T=%.0f Q=%.0f power=%.0f spool=%.2f thr=%.2f coll=%.3f tail=%.2f up.y=%.3f w=%s mass=%.0f%n", i, sim.body.position.y - groundY, main.model.omega, main.model.lastThrust, main.model.lastTorque, e.shaftPowerW, e.spool, e.throttle, main.model.collective, tail.tailPitch, sim.body.localDirToWorld(0,1,0,new Vector3d()).y, sim.body.angularVelocity, sim.mass());
            maxClimb = Math.max(maxClimb, sim.body.position.y - groundY);
        }
        assertTrue(maxClimb > 8, "climbed " + maxClimb);
        Vector3d upW = sim.body.localDirToWorld(0, 1, 0, new Vector3d());
        assertTrue(upW.y > 0.9, "level attitude, up.y=" + upW.y);
        Vector3d u = sim.body.localDirToWorld(0, 1, 0, new Vector3d());
        assertTrue(Math.abs(sim.body.angularVelocity.dot(u)) < 0.6, "yaw rate controlled by tail rotor");
    }
}
