package com.vehicleworks.physics;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * A rigid body integrated with semi-implicit Euler and an implicit gyroscopic term.
 * <p>
 * Positions are those of the center of mass in world space. All vectors are SI units
 * (1 block = 1 metre, kilograms, seconds, Newtons).
 * The class performs no allocation in its per-step methods.
 */
public final class RigidBody {
    public double mass = 1.0;
    public double invMass = 1.0;
    public final Matrix3d inertiaLocal = new Matrix3d();
    public final Matrix3d invInertiaLocal = new Matrix3d();
    public final Matrix3d invInertiaWorld = new Matrix3d();
    public final Matrix3d rotation = new Matrix3d();

    public final Vector3d position = new Vector3d();
    public final Quaterniond orientation = new Quaterniond();
    public final Vector3d linearVelocity = new Vector3d();
    public final Vector3d angularVelocity = new Vector3d();

    public final Vector3d force = new Vector3d();
    public final Vector3d torque = new Vector3d();

    /** Fraction of linear velocity removed per second (numerical safety, normally ~0). */
    public double linearDamping = 0.0;
    /** Fraction of angular velocity removed per second (numerical safety). */
    public double angularDamping = 0.02;
    public double maxLinearSpeed = 400.0;
    public double maxAngularSpeed = 25.0;

    public boolean isStatic;
    /** Owner object (e.g. the simulation) for contact callbacks. */
    public Object owner;
    public boolean sleeping;
    public int sleepTimer;

    private final Vector3d tmpA = new Vector3d();
    private final Vector3d tmpB = new Vector3d();
    private final Vector3d tmpC = new Vector3d();
    private final Vector3d tmpD = new Vector3d();
    private final Vector3d tmpE = new Vector3d();
    private final Matrix3d tmpM = new Matrix3d();
    private final Matrix3d tmpN = new Matrix3d();

    public RigidBody() {
        inertiaLocal.identity();
        invInertiaLocal.identity();
        updateDerived();
    }

    public void setMassProperties(double mass, Matrix3d inertia) {
        this.mass = Math.max(mass, 1e-6);
        this.invMass = isStatic ? 0.0 : 1.0 / this.mass;
        this.inertiaLocal.set(inertia);
        // Guard against singular inertia (e.g. a single line of blocks)
        double eps = this.mass * 1e-3;
        inertiaLocal.m00 = Math.max(inertiaLocal.m00, eps);
        inertiaLocal.m11 = Math.max(inertiaLocal.m11, eps);
        inertiaLocal.m22 = Math.max(inertiaLocal.m22, eps);
        if (isStatic) {
            invInertiaLocal.zero();
        } else {
            inertiaLocal.invert(invInertiaLocal);
        }
        updateDerived();
    }

    /** Recompute the rotation matrix and world-space inverse inertia from the orientation. */
    public void updateDerived() {
        orientation.normalize();
        orientation.get(rotation);
        // Iw^-1 = R * I^-1 * R^T
        rotation.mul(invInertiaLocal, tmpM);
        rotation.transpose(tmpN);
        tmpM.mul(tmpN, invInertiaWorld);
    }

    public void clearForces() {
        force.zero();
        torque.zero();
    }

    public void applyForce(Vector3d f) {
        force.add(f);
    }

    public void applyForce(double fx, double fy, double fz) {
        force.add(fx, fy, fz);
    }

    public void applyTorque(Vector3d t) {
        torque.add(t);
    }

    /** Apply a world-space force at a world-space point. */
    public void applyForceAtPoint(double fx, double fy, double fz, double px, double py, double pz) {
        force.add(fx, fy, fz);
        double rx = px - position.x, ry = py - position.y, rz = pz - position.z;
        torque.add(ry * fz - rz * fy, rz * fx - rx * fz, rx * fy - ry * fx);
    }

    public void applyForceAtPoint(Vector3d f, Vector3d p) {
        applyForceAtPoint(f.x, f.y, f.z, p.x, p.y, p.z);
    }

    /** Apply a world-space impulse at a world-space point (instant velocity change). */
    public void applyImpulseAtPoint(double jx, double jy, double jz, double px, double py, double pz) {
        if (isStatic) return;
        linearVelocity.add(jx * invMass, jy * invMass, jz * invMass);
        double rx = px - position.x, ry = py - position.y, rz = pz - position.z;
        tmpA.set(ry * jz - rz * jy, rz * jx - rx * jz, rx * jy - ry * jx);
        invInertiaWorld.transform(tmpA);
        angularVelocity.add(tmpA);
    }

    public void applyAngularImpulse(double x, double y, double z) {
        if (isStatic) return;
        tmpA.set(x, y, z);
        invInertiaWorld.transform(tmpA);
        angularVelocity.add(tmpA);
    }

    /** Velocity of a world-space point attached to the body. */
    public Vector3d velocityAtPoint(double px, double py, double pz, Vector3d out) {
        double rx = px - position.x, ry = py - position.y, rz = pz - position.z;
        Vector3d w = angularVelocity;
        out.set(linearVelocity.x + (w.y * rz - w.z * ry),
                linearVelocity.y + (w.z * rx - w.x * rz),
                linearVelocity.z + (w.x * ry - w.y * rx));
        return out;
    }

    /** Transform a body-local point (relative to the center of mass) into world space. */
    public Vector3d localToWorld(double lx, double ly, double lz, Vector3d out) {
        out.set(lx, ly, lz);
        rotation.transform(out);
        return out.add(position);
    }

    /** Rotate a body-local direction into world space. */
    public Vector3d localDirToWorld(double lx, double ly, double lz, Vector3d out) {
        out.set(lx, ly, lz);
        return rotation.transform(out);
    }

    /** Transform a world point into body-local space (relative to center of mass). */
    public Vector3d worldToLocal(double wx, double wy, double wz, Vector3d out) {
        out.set(wx - position.x, wy - position.y, wz - position.z);
        return rotation.transformTranspose(out);
    }

    public Vector3d worldDirToLocal(double wx, double wy, double wz, Vector3d out) {
        out.set(wx, wy, wz);
        return rotation.transformTranspose(out);
    }

    /**
     * Integrate velocities from accumulated forces. Uses an implicit gyroscopic solve
     * (one Newton iteration in body space) so spinning bodies remain stable.
     */
    public void integrateVelocity(double dt, double gx, double gy, double gz) {
        if (isStatic || sleeping) return;
        linearVelocity.x += (force.x * invMass + gx) * dt;
        linearVelocity.y += (force.y * invMass + gy) * dt;
        linearVelocity.z += (force.z * invMass + gz) * dt;

        // Gyroscopic term in body space: I (w1 - w0) + dt * w1 x (I w1) = 0, solved with Newton iterations
        Vector3d w0 = rotation.transformTranspose(tmpD.set(angularVelocity));
        Vector3d wb = tmpA.set(w0);
        for (int it = 0; it < 2; it++) {
            Vector3d iw = inertiaLocal.transform(tmpB.set(wb));
            // f = I (wb - w0) + dt * (wb x I wb)
            Vector3d f = tmpC.set(wb).cross(iw).mul(dt);
            f.add(inertiaLocal.transform(tmpE.set(wb).sub(w0)));
            Matrix3d skewW = skew(wb, tmpM);
            Matrix3d j = skewW.mul(inertiaLocal, tmpM);
            Matrix3d skewIw = skew(iw, tmpN);
            j.sub(skewIw).scale(dt).add(inertiaLocal);
            if (Math.abs(j.determinant()) < 1e-12) break;
            j.invert();
            j.transform(f);
            wb.sub(f);
        }
        rotation.transform(wb);
        angularVelocity.set(wb);

        // External torque
        tmpB.set(torque);
        invInertiaWorld.transform(tmpB);
        angularVelocity.fma(dt, tmpB);

        if (linearDamping > 0) linearVelocity.mul(Math.max(0.0, 1.0 - linearDamping * dt));
        if (angularDamping > 0) angularVelocity.mul(Math.max(0.0, 1.0 - angularDamping * dt));

        double ls = linearVelocity.lengthSquared();
        if (ls > maxLinearSpeed * maxLinearSpeed) linearVelocity.mul(maxLinearSpeed / Math.sqrt(ls));
        double as = angularVelocity.lengthSquared();
        if (as > maxAngularSpeed * maxAngularSpeed) angularVelocity.mul(maxAngularSpeed / Math.sqrt(as));
    }

    /** Integrate position and orientation from velocities. */
    public void integratePosition(double dt) {
        if (isStatic || sleeping) return;
        position.fma(dt, linearVelocity);
        Vector3d w = angularVelocity;
        double hx = 0.5 * dt * w.x, hy = 0.5 * dt * w.y, hz = 0.5 * dt * w.z;
        Quaterniond q = orientation;
        double qx = q.x, qy = q.y, qz = q.z, qw = q.w;
        // q += 0.5 * dt * (w,0) * q
        q.x = qx + (hx * qw + hy * qz - hz * qy);
        q.y = qy + (hy * qw + hz * qx - hx * qz);
        q.z = qz + (hz * qw + hx * qy - hy * qx);
        q.w = qw - (hx * qx + hy * qy + hz * qz);
        updateDerived();
    }

    public double kineticEnergy() {
        Vector3d wb = rotation.transformTranspose(tmpA.set(angularVelocity));
        Vector3d iw = inertiaLocal.transform(tmpB.set(wb));
        return 0.5 * mass * linearVelocity.lengthSquared() + 0.5 * wb.dot(iw);
    }

    /** World-space angular momentum L = R I R^T w. */
    public Vector3d angularMomentum(Vector3d out) {
        Vector3d wb = rotation.transformTranspose(out.set(angularVelocity));
        inertiaLocal.transform(wb);
        return rotation.transform(wb);
    }

    public void wake() {
        sleeping = false;
        sleepTimer = 0;
    }

    /** Update sleep state; bodies resting for a while stop simulating. */
    public void updateSleep(double linThreshold, double angThreshold, int ticksToSleep) {
        if (isStatic) return;
        if (linearVelocity.lengthSquared() < linThreshold * linThreshold
                && angularVelocity.lengthSquared() < angThreshold * angThreshold) {
            if (++sleepTimer >= ticksToSleep) {
                sleeping = true;
                linearVelocity.zero();
                angularVelocity.zero();
            }
        } else {
            sleepTimer = 0;
            sleeping = false;
        }
    }

    public static Matrix3d skew(Vector3d v, Matrix3d out) {
        // JOML is column-major: mRC means column R? JOML uses mColRow naming (m10 = column 1 row 0).
        out.m00 = 0;     out.m10 = -v.z; out.m20 = v.y;
        out.m01 = v.z;   out.m11 = 0;    out.m21 = -v.x;
        out.m02 = -v.y;  out.m12 = v.x;  out.m22 = 0;
        return out;
    }
}
