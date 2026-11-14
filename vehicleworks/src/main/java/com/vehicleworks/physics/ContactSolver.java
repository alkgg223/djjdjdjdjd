package com.vehicleworks.physics;

/**
 * Sequential-impulse contact solver with Coulomb friction, restitution and
 * Baumgarte position stabilisation. Operates on a {@link ContactBuffer}.
 */
public final class ContactSolver {
    public int iterations = 10;
    public double baumgarte = 0.18;
    public double slop = 0.015;
    public double maxBiasVelocity = 3.0;
    public double restitutionThreshold = 1.5;

    public void solve(ContactBuffer c, double dt) {
        int n = c.count;
        if (n == 0) return;
        for (int i = 0; i < n; i++) prepare(c, i, dt);
        for (int it = 0; it < iterations; it++) {
            for (int i = 0; i < n; i++) solveContact(c, i);
        }
    }

    private void prepare(ContactBuffer c, int i, double dt) {
        RigidBody a = c.bodyA[i], b = c.bodyB[i];
        double nx = c.nx[i], ny = c.ny[i], nz = c.nz[i];
        // tangent basis
        double t1x, t1y, t1z;
        if (Math.abs(nx) > 0.57735) { t1x = ny; t1y = -nx; t1z = 0; }
        else { t1x = 0; t1y = nz; t1z = -ny; }
        double inv = 1.0 / Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
        t1x *= inv; t1y *= inv; t1z *= inv;
        double t2x = ny * t1z - nz * t1y, t2y = nz * t1x - nx * t1z, t2z = nx * t1y - ny * t1x;
        c.t1x[i] = t1x; c.t1y[i] = t1y; c.t1z[i] = t1z;
        c.t2x[i] = t2x; c.t2y[i] = t2y; c.t2z[i] = t2z;
        c.massN[i] = effMass(a, b, c.px[i], c.py[i], c.pz[i], nx, ny, nz);
        c.massT1[i] = effMass(a, b, c.px[i], c.py[i], c.pz[i], t1x, t1y, t1z);
        c.massT2[i] = effMass(a, b, c.px[i], c.py[i], c.pz[i], t2x, t2y, t2z);
        double vn = relVelAlong(a, b, c.px[i], c.py[i], c.pz[i], nx, ny, nz);
        c.approach[i] = -vn;
        double bias = baumgarte / dt * Math.max(0.0, c.depth[i] - slop);
        if (bias > maxBiasVelocity) bias = maxBiasVelocity;
        if (vn < -restitutionThreshold) bias = Math.max(bias, -c.restitution[i] * vn);
        c.bias[i] = bias;
    }

    private static double effMass(RigidBody a, RigidBody b, double px, double py, double pz, double dx, double dy, double dz) {
        double k = a.invMass + angularTerm(a, px, py, pz, dx, dy, dz);
        if (b != null) k += b.invMass + angularTerm(b, px, py, pz, dx, dy, dz);
        return k > 1e-12 ? 1.0 / k : 0.0;
    }

    /** d · ((I^-1 (r × d)) × r) */
    private static double angularTerm(RigidBody body, double px, double py, double pz, double dx, double dy, double dz) {
        if (body.invMass == 0) return 0;
        double rx = px - body.position.x, ry = py - body.position.y, rz = pz - body.position.z;
        double cx = ry * dz - rz * dy, cy = rz * dx - rx * dz, cz = rx * dy - ry * dx;
        var m = body.invInertiaWorld;
        double ix = m.m00 * cx + m.m10 * cy + m.m20 * cz;
        double iy = m.m01 * cx + m.m11 * cy + m.m21 * cz;
        double iz = m.m02 * cx + m.m12 * cy + m.m22 * cz;
        // (I^-1 c) × r
        double ex = iy * rz - iz * ry, ey = iz * rx - ix * rz, ez = ix * ry - iy * rx;
        return ex * dx + ey * dy + ez * dz;
    }

    private static double relVelAlong(RigidBody a, RigidBody b, double px, double py, double pz, double dx, double dy, double dz) {
        double v = pointVelDot(a, px, py, pz, dx, dy, dz);
        if (b != null) v -= pointVelDot(b, px, py, pz, dx, dy, dz);
        return v;
    }

    private static double pointVelDot(RigidBody body, double px, double py, double pz, double dx, double dy, double dz) {
        double rx = px - body.position.x, ry = py - body.position.y, rz = pz - body.position.z;
        var w = body.angularVelocity;
        var v = body.linearVelocity;
        double vx = v.x + (w.y * rz - w.z * ry);
        double vy = v.y + (w.z * rx - w.x * rz);
        double vz = v.z + (w.x * ry - w.y * rx);
        return vx * dx + vy * dy + vz * dz;
    }

    private static void applyImpulse(RigidBody a, RigidBody b, double px, double py, double pz, double jx, double jy, double jz) {
        a.applyImpulseAtPoint(jx, jy, jz, px, py, pz);
        if (b != null) b.applyImpulseAtPoint(-jx, -jy, -jz, px, py, pz);
    }

    private void solveContact(ContactBuffer c, int i) {
        RigidBody a = c.bodyA[i], b = c.bodyB[i];
        double px = c.px[i], py = c.py[i], pz = c.pz[i];
        double nx = c.nx[i], ny = c.ny[i], nz = c.nz[i];
        // normal
        double vn = relVelAlong(a, b, px, py, pz, nx, ny, nz);
        double lambda = (c.bias[i] - vn) * c.massN[i];
        double old = c.accN[i];
        double acc = Math.max(0.0, old + lambda);
        lambda = acc - old;
        c.accN[i] = acc;
        if (lambda != 0) applyImpulse(a, b, px, py, pz, nx * lambda, ny * lambda, nz * lambda);

        double maxF = c.friction[i] * acc;
        // tangent 1
        double vt = relVelAlong(a, b, px, py, pz, c.t1x[i], c.t1y[i], c.t1z[i]);
        double lt = -vt * c.massT1[i];
        old = c.accT1[i];
        double at = clamp(old + lt, -maxF, maxF);
        lt = at - old;
        c.accT1[i] = at;
        if (lt != 0) applyImpulse(a, b, px, py, pz, c.t1x[i] * lt, c.t1y[i] * lt, c.t1z[i] * lt);
        // tangent 2
        vt = relVelAlong(a, b, px, py, pz, c.t2x[i], c.t2y[i], c.t2z[i]);
        lt = -vt * c.massT2[i];
        old = c.accT2[i];
        at = clamp(old + lt, -maxF, maxF);
        lt = at - old;
        c.accT2[i] = at;
        if (lt != 0) applyImpulse(a, b, px, py, pz, c.t2x[i] * lt, c.t2y[i] * lt, c.t2z[i] * lt);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
