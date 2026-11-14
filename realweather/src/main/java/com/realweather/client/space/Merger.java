package com.realweather.client.space;

import static com.realweather.client.space.Space3D.ADD;

/**
 * GW150914, two black holes (36 and 29 times the Sun's mass) spiralling together and merging, as LIGO first heard in
 * 2015: they circle faster and faster as they lose energy to gravitational waves, merge in a flash, ring down into a
 * single black hole, and the whole cycle starts again. Spacetime round them is drawn as a sheet of grid lines rippling
 * with the spiral pattern of the waves they send out at the speed of light, stronger and faster as they close in.
 */
final class Merger {
    private static final double CYCLE = 45, MERGE = 34;

    private Merger() {}

    /** Separation (units of the starting separation) at a time in the cycle. */
    private static double separation(double t) {
        return t >= MERGE ? 0 : Math.max(0.03, Math.pow((MERGE - t) / MERGE, 0.25));
    }

    /** Orbital phase (the chirp: ever faster). */
    private static double phase(double t) {
        double left = Math.max(0, (MERGE - Math.min(t, MERGE)) / MERGE);
        return -60 * Math.pow(left, 0.625) + (t > MERGE ? (t - MERGE) * 40 : 0);
    }

    /** How strong the waves are when they leave (grows to the merger, rings down after). */
    private static double strength(double t) {
        if (t < 0) {
            return 0;
        }
        if (t < MERGE) {
            return Math.min(1.4, 0.12 / separation(t));
        }
        return 1.4 * Math.exp(-(t - MERGE) * 2.5);
    }

    /**
     * The pair at c (view), with `scale` view units for the starting separation, its orbit's axis (view, unit),
     * seconds, view units per pixel there.
     */
    static void draw(double cx, double cy, double cz, double scale, double[] axis, double seconds, double px, float vis) {
        if (vis <= 0.01F) {
            return;
        }
        double t = seconds % CYCLE;
        double[] e1 = perp(axis);
        double[] e2 = {axis[1] * e1[2] - axis[2] * e1[1], axis[2] * e1[0] - axis[0] * e1[2], axis[0] * e1[1] - axis[1] * e1[0]};
        double sep = separation(t) * scale * 0.5, ph = phase(t);
        double size = scale / px;
        // The black holes themselves (black, rimmed by their lensed photon rings), or the one they become.
        double[] d0 = new double[3];
        for (int k = 0; k < (t < MERGE ? 2 : 1); k++) {
            double a = ph + k * Math.PI;
            double rr = t < MERGE ? sep * (k == 0 ? 0.9 : 1.1) : 0;
            double bx = cx + (e1[0] * Math.cos(a) + e2[0] * Math.sin(a)) * rr, by = cy + (e1[1] * Math.cos(a) + e2[1] * Math.sin(a)) * rr,
                    bz = cz + (e1[2] * Math.cos(a) + e2[2] * Math.sin(a)) * rr;
            double hr = scale * (t < MERGE ? 0.06 : 0.09) * (k == 0 ? 1.1 : 0.95);
            Space3D.billboard(Space3D.GLOW, ADD, bx, by, bz, Math.max(px * 3, hr * 5), 0F, 0.55F, 0.7F, 1F, 0.6F * vis);
            Space3D.billboard(Space3D.GLOW, ADD, bx, by, bz, Math.max(px * 2, hr * 2.2), 0F, 1F, 0.85F, 0.6F, 0.8F * vis);
            if (size > 20) {
                Space3D.sphere(Space3D.WHITE, bx, by, bz, hr, Space3D.orient(axis[0], axis[1], axis[2], 0), 0, 0, 1, 0F, 0F, 0F, 0F, 0F, vis, false, 0F,
                        16);
            }
            d0[0] = bx;
        }
        // The flash of the merger.
        if (t > MERGE - 0.3 && t < MERGE + 3) {
            float f = (float) Math.exp(-Math.abs(t - MERGE) * 1.6);
            Space3D.billboard(Space3D.STAR, ADD, cx, cy, cz, scale * 3 * f + px * 4, 0F, 0.8F, 0.9F, 1F, 0.8F * f * vis);
            Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, scale * 1.2, 0F, 0.7F, 0.85F, 1F, 0.6F * f * vis);
        }
        if (size < 6) {
            return;
        }
        // The sheet of spacetime: rings and spokes, rippled by the waves (spiralling out at their speed).
        float detail = Space3D.smooth(6F, 40F, (float) size);
        int rings = (int) Math.max(8, Math.min(30, size / 8)), seg = (int) Math.max(48, Math.min(128, size / 2));
        double reach = scale * 9, speed = scale * 1.1;
        double w = Math.max(px * 1.1, scale * 0.006);
        double[] q0 = new double[3], q1 = new double[3];
        for (int i = 1; i <= rings; i++) {
            double rho = reach * i / rings;
            for (int j = 0; j < seg; j++) {
                double a0 = Math.PI * 2 * j / seg, a1 = Math.PI * 2 * (j + 1) / seg;
                float l0 = sheet(rho, a0, t, scale, speed, cx, cy, cz, e1, e2, axis, q0), l1 = sheet(rho, a1, t, scale, speed, cx, cy, cz, e1, e2, axis, q1);
                float fade = (float) (1 - (double) i / rings * 0.7) * detail * vis;
                Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, q0[0], q0[1], q0[2], q1[0], q1[1], q1[2], w, w, 0.35F + 0.5F * l0, 0.75F + 0.25F * l0, 1F,
                        (0.3F + 0.6F * l0) * fade, (0.3F + 0.6F * l1) * fade);
            }
        }
        int spokes = 24;
        for (int j = 0; j < spokes; j++) {
            double a = Math.PI * 2 * j / spokes;
            int n = rings * 2;
            for (int i = 0; i < n; i++) {
                double r0 = reach * (i + 0.5) / n, r1 = reach * (i + 1.5) / n;
                float l0 = sheet(r0, a, t, scale, speed, cx, cy, cz, e1, e2, axis, q0), l1 = sheet(r1, a, t, scale, speed, cx, cy, cz, e1, e2, axis, q1);
                float fade = (float) (1 - (double) i / n * 0.7) * detail * vis;
                Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, q0[0], q0[1], q0[2], q1[0], q1[1], q1[2], w, w, 0.35F + 0.5F * l0, 0.75F + 0.25F * l0, 1F,
                        (0.25F + 0.5F * l0) * fade, (0.25F + 0.5F * l1) * fade);
            }
        }
    }

    /** A point of the rippling sheet; returns how strongly the wave lifts it there (0..1). */
    private static float sheet(double rho, double a, double t, double scale, double speed, double cx, double cy, double cz, double[] e1, double[] e2,
                               double[] axis, double[] out) {
        double tr = t - rho / speed;
        double amp = strength(tr) * scale * 0.35 / Math.pow(Math.max(rho / scale, 0.4), 0.8);
        // Two crests round the circle (the waves of a pair are quadrupolar), turning with twice the orbital phase.
        double h = amp * Math.cos(2 * (a - phase(tr)));
        // The dip of the pair's gravity well.
        double well = -scale * 0.5 / (rho / scale + 0.6);
        double z = h + well;
        out[0] = cx + (e1[0] * Math.cos(a) + e2[0] * Math.sin(a)) * rho + axis[0] * z;
        out[1] = cy + (e1[1] * Math.cos(a) + e2[1] * Math.sin(a)) * rho + axis[1] * z;
        out[2] = cz + (e1[2] * Math.cos(a) + e2[2] * Math.sin(a)) * rho + axis[2] * z;
        return (float) Math.min(1, Math.abs(h) / (scale * 0.15));
    }

    private static double[] perp(double[] a) {
        double tx = Math.abs(a[1]) < 0.9 ? 0 : 1, ty = Math.abs(a[1]) < 0.9 ? 1 : 0;
        double[] e = {ty * a[2], -tx * a[2], tx * a[1] - ty * a[0]};
        double l = Math.sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2]) + 1.0E-12;
        e[0] /= l;
        e[1] /= l;
        e[2] /= l;
        return e;
    }
}
