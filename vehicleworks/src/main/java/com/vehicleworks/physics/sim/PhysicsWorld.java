package com.vehicleworks.physics.sim;

import com.vehicleworks.physics.CollisionDetector;
import com.vehicleworks.physics.ContactBuffer;
import com.vehicleworks.physics.ContactSolver;
import com.vehicleworks.physics.RigidBody;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Steps all vehicle simulations of one world with a shared fixed timestep and adaptive
 * substepping. Broad phase between vehicles uses sort-and-sweep on world AABBs;
 * narrow phase and the impulse solver run on all contacts together so momentum is
 * exchanged correctly between colliding vehicles.
 */
public final class PhysicsWorld {
    public final List<VehicleSimulation> sims = new ArrayList<>();
    public final ContactBuffer contacts = new ContactBuffer();
    public final ContactSolver solver = new ContactSolver();
    public final CollisionDetector detector = new CollisionDetector();
    public int baseSubsteps = 4;
    public int maxSubsteps = 24;
    /** Maximum travel per substep (m) to prevent tunnelling. */
    public double maxStepDistance = 0.45;
    public ImpactListener listener;
    public int lastSubsteps;
    public int lastContactCount;
    public long lastStepNanos;

    private double[] bounds = new double[0];
    private Integer[] order = new Integer[0];

    public interface ImpactListener {
        /**
         * @param a        first vehicle
         * @param b        second vehicle or null for terrain
         * @param voxelA   packed structure voxel of a
         * @param voxelB   packed voxel of b, or terrain block position
         * @param impulse  normal impulse (N·s) accumulated this substep
         * @param approach approach speed (m/s) at the start of the substep
         */
        void onImpact(VehicleSimulation a, VehicleSimulation b, long voxelA, long voxelB, double impulse, double approach);
    }

    public void step(double dtTick, Environment env) {
        long t0 = System.nanoTime();
        int n = sims.size();
        double maxSpeed = 0;
        for (VehicleSimulation s : sims) {
            if (s.frozen) continue;
            s.beginTick(dtTick, env);
            if (!s.body.sleeping) maxSpeed = Math.max(maxSpeed, s.body.linearVelocity.length()
                    + s.body.angularVelocity.length() * radius(s));
        }
        int substeps = (int) Math.ceil(maxSpeed * dtTick / maxStepDistance);
        substeps = Math.max(baseSubsteps, Math.min(maxSubsteps, substeps));
        lastSubsteps = substeps;
        double dt = dtTick / substeps;
        double g = env.gravity();
        if (bounds.length < n * 6) bounds = new double[n * 6];
        if (order.length < n) order = new Integer[n];
        double[] b6 = new double[6];
        int totalContacts = 0;
        for (int step = 0; step < substeps; step++) {
            for (VehicleSimulation s : sims) {
                if (s.frozen || s.body.sleeping) continue;
                s.body.clearForces();
                s.applyForces(dt, env);
                s.body.integrateVelocity(dt, 0, -g, 0);
            }
            contacts.clear();
            for (VehicleSimulation s : sims) {
                if (s.frozen || s.body.sleeping) continue;
                double margin = s.body.linearVelocity.length() * dt + 0.05;
                s.collectTerrainContacts(detector, contacts, margin);
            }
            // broad phase between vehicles
            if (n > 1) {
                for (int i = 0; i < n; i++) {
                    sims.get(i).worldBounds(b6);
                    System.arraycopy(b6, 0, bounds, i * 6, 6);
                    order[i] = i;
                }
                Arrays.sort(order, 0, n, (x, y) -> Double.compare(bounds[x * 6], bounds[y * 6]));
                for (int ii = 0; ii < n; ii++) {
                    int i = order[ii];
                    VehicleSimulation a = sims.get(i);
                    if (a.frozen) continue;
                    for (int jj = ii + 1; jj < n; jj++) {
                        int j = order[jj];
                        if (bounds[j * 6] > bounds[i * 6 + 3]) break;
                        VehicleSimulation b = sims.get(j);
                        if (b.frozen) continue;
                        if (a.body.sleeping && b.body.sleeping) continue;
                        if (bounds[j * 6 + 1] > bounds[i * 6 + 4] || bounds[j * 6 + 4] < bounds[i * 6 + 1]
                                || bounds[j * 6 + 2] > bounds[i * 6 + 5] || bounds[j * 6 + 5] < bounds[i * 6 + 2]) continue;
                        int added = detector.detectBodies(a.body, a.shape, a.com, b.body, b.shape, b.com, contacts, 48);
                        if (added > 0) { a.body.wake(); b.body.wake(); }
                    }
                }
            }
            solver.solve(contacts, dt);
            totalContacts += contacts.count;
            if (listener != null) {
                for (int c = 0; c < contacts.count; c++) {
                    if (contacts.accN[c] <= 0) continue;
                    RigidBody ra = contacts.bodyA[c], rb = contacts.bodyB[c];
                    VehicleSimulation sa = (VehicleSimulation) ra.owner;
                    VehicleSimulation sb = rb != null ? (VehicleSimulation) rb.owner : null;
                    listener.onImpact(sa, sb, contacts.voxelA[c], contacts.voxelB[c], contacts.accN[c], contacts.approach[c]);
                }
            }
            for (VehicleSimulation s : sims) {
                if (s.frozen || s.body.sleeping) continue;
                s.body.integratePosition(dt);
            }
        }
        for (VehicleSimulation s : sims) s.endTick(dtTick);
        lastContactCount = totalContacts / Math.max(1, substeps);
        lastStepNanos = System.nanoTime() - t0;
    }

    private static double radius(VehicleSimulation s) {
        if (s.shape == null) return 1;
        double dx = Math.max(Math.abs(s.shape.maxX - s.com.x), Math.abs(s.shape.minX - s.com.x));
        double dy = Math.max(Math.abs(s.shape.maxY - s.com.y), Math.abs(s.shape.minY - s.com.y));
        double dz = Math.max(Math.abs(s.shape.maxZ - s.com.z), Math.abs(s.shape.minZ - s.com.z));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
