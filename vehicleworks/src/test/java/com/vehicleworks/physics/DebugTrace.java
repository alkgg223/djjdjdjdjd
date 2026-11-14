package com.vehicleworks.physics;

import com.vehicleworks.physics.sim.PhysicsWorld;
import com.vehicleworks.physics.sim.VehicleSimulation;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("debug")
class DebugTrace {
    @Test
    void plane() {
        PhysicsWorld w3 = new PhysicsWorld();
        VehicleSimulation p = VehicleTest.buildPlane();
        w3.sims.add(p);
        TestEnv e3 = new TestEnv();
        for (int i = 0; i < 40; i++) w3.step(0.05, e3);
        double g0 = p.body.position.y;
        System.out.println("com=" + p.com + " mass=" + p.mass());
        p.thrusters.get(0).engine.requestStart();
        p.input.throttle = 1;
        p.input.flaps = 0.5;
        for (int i = 0; i < 900; i++) {
            p.input.pitch = p.airspeed > 42 ? 0.35 : 0;
            if (p.body.position.y - g0 > 15) { p.input.pitch = 0; p.input.gearDown = false; p.input.flaps = 0; }
            w3.step(0.05, e3);
            Vector3d fw = p.body.localDirToWorld(0, 0, -1, new Vector3d());
            if (i % 20 == 0) System.out.printf("plane t=%d h=%.2f v=%.1f thrust=%.0f lift=%.0f drag=%.0f aoa=%.1f pitchAng=%.1f effPitch=%.2f wx=%.3f contacts=%d%n", i, p.body.position.y - g0, p.airspeed, p.totalThrust, p.totalLift, p.totalDrag,
                    Math.toDegrees(p.angleOfAttack), Math.toDegrees(Math.asin(fw.y)), p.effPitch, p.body.angularVelocity.x, w3.lastContactCount);
        }
    }

    @Test
    void heli() {
        new VehicleTest().helicopterHoversLevelWithAssist();
    }
}
