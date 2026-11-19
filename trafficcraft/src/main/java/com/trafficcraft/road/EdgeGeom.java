package com.trafficcraft.road;

import net.minecraft.world.phys.Vec3;

/** Arc-length sampled centre line of an edge (cubic Bezier in XZ, linear grade in Y). */
public final class EdgeGeom {
    public static final double STEP = 0.5;
    public final double[] x, y, z, tx, tz, s;
    public final double length;
    public double trimA, trimB;

    public EdgeGeom(Vec3 a, Vec3 p1, Vec3 p2, Vec3 b) {
        int fine = 400;
        double[] fx = new double[fine + 1], fz = new double[fine + 1], fs = new double[fine + 1];
        for (int i = 0; i <= fine; i++) {
            double t = (double) i / fine, u = 1 - t;
            fx[i] = u * u * u * a.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * b.x;
            fz[i] = u * u * u * a.z + 3 * u * u * t * p1.z + 3 * u * t * t * p2.z + t * t * t * b.z;
            if (i > 0) fs[i] = fs[i - 1] + Math.hypot(fx[i] - fx[i - 1], fz[i] - fz[i - 1]);
        }
        length = Math.max(0.01, fs[fine]);
        int n = Math.max(2, (int) Math.ceil(length / STEP) + 1);
        x = new double[n];
        y = new double[n];
        z = new double[n];
        tx = new double[n];
        tz = new double[n];
        s = new double[n];
        int j = 0;
        for (int i = 0; i < n; i++) {
            double target = length * i / (n - 1);
            while (j < fine - 1 && fs[j + 1] < target) j++;
            double seg = fs[j + 1] - fs[j];
            double f = seg > 1e-9 ? (target - fs[j]) / seg : 0;
            x[i] = fx[j] + (fx[j + 1] - fx[j]) * f;
            z[i] = fz[j] + (fz[j + 1] - fz[j]) * f;
            s[i] = target;
            double g = target / length;
            y[i] = a.y + (b.y - a.y) * g;
            double dx = fx[j + 1] - fx[j], dz = fz[j + 1] - fz[j];
            double l = Math.hypot(dx, dz);
            tx[i] = l > 1e-9 ? dx / l : 0;
            tz[i] = l > 1e-9 ? dz / l : 1;
        }
    }

    public int count() {
        return x.length;
    }

    private int idx(double sv) {
        int i = (int) Math.floor(sv / length * (x.length - 1));
        return Math.max(0, Math.min(x.length - 2, i));
    }

    /** Point at arc length sv with a lateral offset (positive = left of the A->B direction). */
    public Vec3 point(double sv, double offset) {
        sv = Math.max(0, Math.min(length, sv));
        int i = idx(sv);
        double f = (sv - s[i]) / Math.max(1e-9, s[i + 1] - s[i]);
        double px = x[i] + (x[i + 1] - x[i]) * f, py = y[i] + (y[i + 1] - y[i]) * f, pz = z[i] + (z[i + 1] - z[i]) * f;
        double ttx = tx[i] + (tx[i + 1] - tx[i]) * f, ttz = tz[i] + (tz[i + 1] - tz[i]) * f;
        double l = Math.hypot(ttx, ttz);
        ttx /= l;
        ttz /= l;
        // left of direction (tx,tz) is (tz,-tx)
        return new Vec3(px + ttz * offset, py, pz - ttx * offset);
    }

    public Vec3 tangent(double sv) {
        sv = Math.max(0, Math.min(length, sv));
        int i = idx(sv);
        double f = (sv - s[i]) / Math.max(1e-9, s[i + 1] - s[i]);
        double ttx = tx[i] + (tx[i + 1] - tx[i]) * f, ttz = tz[i] + (tz[i + 1] - tz[i]) * f;
        double l = Math.hypot(ttx, ttz);
        return new Vec3(ttx / l, 0, ttz / l);
    }

    /** Curvature-based safe speed (m/s) around arc length sv. */
    public double curvatureAt(double sv) {
        Vec3 t0 = tangent(sv - 2), t1 = tangent(sv + 2);
        double ang = Math.acos(Math.max(-1, Math.min(1, t0.x * t1.x + t0.z * t1.z)));
        return ang / 4.0;
    }

    /** Arc length of the sample closest to point p (lateral distance returned through out[0], signed: + = left). */
    public double project(Vec3 p, double[] out) {
        double best = Double.MAX_VALUE;
        int bi = 0;
        for (int i = 0; i < x.length; i++) {
            double d = (x[i] - p.x) * (x[i] - p.x) + (z[i] - p.z) * (z[i] - p.z);
            if (d < best) {
                best = d;
                bi = i;
            }
        }
        if (out != null) {
            double dx = p.x - x[bi], dz = p.z - z[bi];
            out[0] = dx * tz[bi] - dz * tx[bi];
            out[1] = Math.sqrt(best);
        }
        return s[bi];
    }
}
