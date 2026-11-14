package com.vehicleworks.physics;

import com.vehicleworks.physics.sim.PhysicsWorld;
import com.vehicleworks.physics.sim.VehicleSimulation;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreDynamicsTest {

    @Test
    void freeFallMatchesAnalyticSolution() {
        RigidBody b = new RigidBody();
        b.setMassProperties(10, new Matrix3d().identity());
        b.angularDamping = 0;
        double dt = 1.0 / 80;
        for (int i = 0; i < 160; i++) {
            b.clearForces();
            b.integrateVelocity(dt, 0, -9.81, 0);
            b.integratePosition(dt);
        }
        double t = 2.0;
        assertEquals(-9.81 * t, b.linearVelocity.y, 1e-9);
        assertEquals(-0.5 * 9.81 * t * t, b.position.y, 0.15); // semi-implicit Euler error O(dt)
    }

    @Test
    void torqueFreeSpinConservesAngularMomentum() {
        RigidBody b = new RigidBody();
        b.angularDamping = 0;
        b.setMassProperties(5, new Matrix3d().scaling(1, 2, 3));
        b.angularVelocity.set(0.2, 0.3, 3.0); // near the stable major axis
        Vector3d l0 = b.angularMomentum(new Vector3d());
        double e0 = b.kineticEnergy();
        double dt = 1.0 / 100;
        for (int i = 0; i < 2000; i++) {
            b.clearForces();
            b.integrateVelocity(dt, 0, 0, 0);
            b.integratePosition(dt);
        }
        Vector3d l1 = b.angularMomentum(new Vector3d());
        assertEquals(0, l0.distance(l1) / l0.length(), 0.03, "angular momentum drift");
        assertTrue(b.kineticEnergy() <= e0 * 1.01, "energy must not grow");
    }

    @Test
    void inertiaOfRodOfCubes() {
        MassAccumulator m = new MassAccumulator();
        int n = 10;
        for (int i = 0; i < n; i++) m.addBox(1, i + 0.5, 0.5, 0.5, 1, 1, 1);
        Vector3d c = m.centerOfMass(new Vector3d());
        assertEquals(5.0, c.x, 1e-12);
        Matrix3d inertia = m.inertia(new Matrix3d());
        double expectedYY = n * (n * n - 1) / 12.0 + n / 6.0;
        assertEquals(expectedYY, inertia.m11, 1e-9);
        assertEquals(n / 6.0, inertia.m00, 1e-9);
        // solid 10x1x1 bar: I = m (L^2 + 1)/12
        assertEquals(n * (n * n + 1) / 12.0, inertia.m11, 1e-9);
    }

    @Test
    void greedyMergeProducesFewBoxesAndPreservesVolume() {
        CollisionShape.Builder b = new CollisionShape.Builder();
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) for (int y = 0; y < 2; y++) b.addFullVoxel(x, y, z, 0.6f);
        for (int y = 2; y < 6; y++) b.addFullVoxel(0, y, 0, 0.6f); // mast
        CollisionShape s = b.build();
        double vol = 0;
        for (LocalBox box : s.boxes) vol += box.volume();
        assertEquals(204, vol, 1e-9);
        assertTrue(s.boxes.length <= 3, "boxes: " + s.boxes.length);
        assertTrue(s.boxAt(5.5, 1.5, 5.5) >= 0);
        assertEquals(-1, s.boxAt(5.5, 3.5, 5.5));
        assertTrue(s.boxAt(0.5, 5.5, 0.5) >= 0);
        // interior lattice points removed: far fewer probes than voxels*8
        assertTrue(s.probeCount() < 204 * 2, "probes: " + s.probeCount());
    }

    private static VehicleSimulation crate(double y, double mass) {
        SimBuilder sb = new SimBuilder();
        sb.box(0, 0, 0, 1, 1, 1, mass / 8);
        return sb.build(0.3, y, 0.2);
    }

    @Test
    void boxSettlesOnGroundWithoutSinkingAndSleeps() {
        TestEnv env = new TestEnv();
        PhysicsWorld w = new PhysicsWorld();
        VehicleSimulation s = crate(4, 800);
        w.sims.add(s);
        for (int i = 0; i < 200; i++) w.step(0.05, env);
        double bottom = s.body.position.y - 1.0;
        assertEquals(0.0, bottom, 0.05, "resting height");
        assertTrue(s.body.linearVelocity.length() < 0.05);
        assertTrue(s.body.sleeping, "should sleep");
    }

    @Test
    void tiltedBoxTopplesAndComesToRest() {
        TestEnv env = new TestEnv();
        PhysicsWorld w = new PhysicsWorld();
        VehicleSimulation s = crate(3, 400);
        s.body.orientation.rotateXYZ(0.4, 0.3, 0.7);
        s.body.updateDerived();
        w.sims.add(s);
        for (int i = 0; i < 300; i++) w.step(0.05, env);
        assertTrue(s.body.position.y > 0.9 && s.body.position.y < 1.6, "y=" + s.body.position.y);
        assertTrue(s.body.angularVelocity.length() < 0.1);
    }

    @Test
    void slidingFrictionStopsNearAnalyticDistance() {
        TestEnv env = new TestEnv();
        env.friction = 0.5f;
        PhysicsWorld w = new PhysicsWorld();
        SimBuilder sb = new SimBuilder();
        sb.box(0, 0, 0, 3, 0, 3, 50); // flat 4x1x4 sled
        VehicleSimulation s = sb.build(0, 0.5, 0);
        s.shape = sb.shape.build();
        w.sims.add(s);
        for (int i = 0; i < 20; i++) w.step(0.05, env); // settle
        double x0 = s.body.position.x;
        double v0 = 6.0;
        s.body.linearVelocity.set(v0, 0, 0);
        s.body.wake();
        for (int i = 0; i < 100; i++) w.step(0.05, env);
        double dist = s.body.position.x - x0;
        double mu = Math.sqrt(0.5 * 0.6); // combined friction
        double expected = v0 * v0 / (2 * mu * 9.81);
        assertEquals(expected, dist, expected * 0.2, "slide distance");
    }

    @Test
    void headOnCollisionConservesMomentum() {
        TestEnv env = new TestEnv();
        env.ground = false;
        PhysicsWorld w = new PhysicsWorld();
        SimBuilder a = new SimBuilder();
        a.box(0, 0, 0, 1, 1, 1, 100);
        VehicleSimulation sa = a.build(-3, 50, 0);
        SimBuilder b = new SimBuilder();
        b.box(0, 0, 0, 1, 1, 1, 300);
        VehicleSimulation sb = b.build(3, 50, 0);
        sa.fluid = null; // no air drag: isolate the collision
        sb.fluid = null;
        sa.body.linearVelocity.set(8, 0, 0);
        sb.body.linearVelocity.set(-2, 0, 0);
        w.sims.add(sa);
        w.sims.add(sb);
        // remove gravity effect on momentum check: compare x momentum only
        double p0 = sa.mass() * sa.body.linearVelocity.x + sb.mass() * sb.body.linearVelocity.x;
        for (int i = 0; i < 30; i++) w.step(0.05, env);
        double p1 = sa.mass() * sa.body.linearVelocity.x + sb.mass() * sb.body.linearVelocity.x;
        assertEquals(p0, p1, Math.abs(p0) * 0.01 + 1e-6);
        assertTrue(sa.body.linearVelocity.x < sb.body.linearVelocity.x + 0.2, "bodies separated after impact");
        assertTrue(sa.body.position.x < sb.body.position.x);
    }

    @Test
    void terrainRaycastFindsGround() {
        TerrainBoxes t = new TerrainBoxes();
        t.clear();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) { t.beginBlock(x, -1, z, true); t.addBox(x, -1, z, x + 1, 0, z + 1, 0.6f); }
        t.endBlock();
        double[] n = new double[3];
        double d = t.raycast(0.5, 3.0, 0.5, 0, -1, 0, 10, n);
        assertEquals(3.0, d, 1e-9);
        assertEquals(1.0, n[1], 1e-9);
        assertEquals(-1, t.raycast(0.5, 3.0, 0.5, 0, 1, 0, 10, n), 1e-9);
    }
}
