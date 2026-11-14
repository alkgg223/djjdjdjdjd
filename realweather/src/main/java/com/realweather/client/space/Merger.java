package com.realweather.client.space;

import static com.realweather.client.space.Space3D.ADD;

/**
 * GW150914, two black holes (36 and 29 times the Sun's mass) spiralling together and merging, as LIGO first heard in
 * 2015: they circle faster and faster as they lose energy to gravitational waves, merge in a flash, ring down into a
 * single black hole, and the whole cycle starts again. Spacetime round them is drawn as a sheet of grid lines rippling
 * with the spiral pattern of the waves they send out at the speed of light, stronger and faster as they close in.
 */
public final class Merger {
    private static final double CYCLE = 70, MERGE = 54;

    /** For tests: seconds added to the cycle. */
    public static double testOffset;

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
        double t = (seconds + testOffset) % CYCLE;
        double[] e1 = perp(axis);
        double[] e2 = {axis[1] * e1[2] - axis[2] * e1[1], axis[2] * e1[0] - axis[0] * e1[2], axis[0] * e1[1] - axis[1] * e1[0]};
        double sep = separation(t) * scale * 0.5, ph = phase(t);
        double size = scale / px;
        boolean merged = t >= MERGE;
        // The sheet of spacetime behind: rings and spokes, rippled by the waves (spiralling out at their speed).
        if (size >= 6) {
            grid(cx, cy, cz, scale, axis, e1, e2, t, px, size, vis);
        }
        // The holes, each lensing the sky behind it, each with its own disc (shaken more and more as they close in).
        double[][] at = new double[merged ? 1 : 2][];
        for (int k = 0; k < at.length; k++) {
            double a = ph + k * Math.PI;
            double rr = merged ? 0 : sep * (k == 0 ? 0.9 : 1.1);
            at[k] = new double[]{cx + (e1[0] * Math.cos(a) + e2[0] * Math.sin(a)) * rr, cy + (e1[1] * Math.cos(a) + e2[1] * Math.sin(a)) * rr,
                    cz + (e1[2] * Math.cos(a) + e2[2] * Math.sin(a)) * rr};
            double after = t - MERGE;
            // After the merger the new hole rings down: its size quivering for a moment, then still.
            double ring = merged ? 1 + 0.07 * Math.exp(-after * 1.4) * Math.sin(after * 22) : 1;
            double hr = scale * (merged ? 0.075 : 0.05) * (k == 0 ? 1.08 : 0.94) * ring;
            double hpx = hr / px;
            double shake = merged ? 0.25 * Math.exp(-after * 0.6) : 0.12 + 0.6 * Math.pow(1 - separation(t), 3);
            double wob = seconds * (merged ? 0.5 : 1.4 + k);
            double[] n = {axis[0] + (e1[0] * Math.cos(wob) + e2[0] * Math.sin(wob)) * shake, axis[1] + (e1[1] * Math.cos(wob) + e2[1] * Math.sin(wob)) * shake,
                    axis[2] + (e1[2] * Math.cos(wob) + e2[2] * Math.sin(wob)) * shake};
            double nl = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            n[0] /= nl;
            n[1] /= nl;
            n[2] /= nl;
            if (hpx > 3 && SpaceShaders.holeReady()) {
                CloseViews.lensed(at[k][0], at[k][1], at[k][2], hr, n, (float) (seconds * (merged ? 1.0 : 1.6)), vis, merged ? 1.0F : 0.8F, 13, 200F);
            } else {
                Space3D.billboard(Space3D.GLOW, ADD, at[k][0], at[k][1], at[k][2], Math.max(px * 3, hr * 6), 0F, 1F, 0.8F, 0.55F, 0.7F * vis);
                Space3D.billboard(Space3D.GLOW, ADD, at[k][0], at[k][1], at[k][2], Math.max(px * 2, hr * 2.5), 0F, 0.6F, 0.75F, 1F, 0.5F * vis);
            }
        }
        // The gas round the pair: a ring circling them both, and streams of it spiralling down on to each hole (the
        // final hole is left with a new, settled disc).
        long seed = 150914;
        double ringR = merged ? scale * 0.55 : Math.max(sep * 2.4, scale * 0.3);
        for (int i = 0; i < 180; i++) {
            double a = Space3D.h(seed, i) * Math.PI * 2 + seconds * 0.25 * Math.pow(ringR / scale, -1.5) * 0.1;
            double rad = ringR * (0.8 + 0.5 * Space3D.h(seed, i + 300));
            double hgt = (Space3D.h(seed, i + 600) - 0.5) * ringR * 0.08;
            double qx = cx + (e1[0] * Math.cos(a) + e2[0] * Math.sin(a)) * rad + axis[0] * hgt, qy = cy + (e1[1] * Math.cos(a) + e2[1] * Math.sin(a)) * rad
                    + axis[1] * hgt, qz = cz + (e1[2] * Math.cos(a) + e2[2] * Math.sin(a)) * rad + axis[2] * hgt;
            Space3D.billboard(Space3D.GLOW, ADD, qx, qy, qz, Math.max(px * 1.5, ringR * 0.05), 0F, 1F, 0.7F, 0.45F, 0.12F * vis);
        }
        if (!merged) {
            for (int k = 0; k < 2; k++) {
                for (int i = 0; i < 40; i++) {
                    double f = (Space3D.h(seed, 1000 + k * 100 + i) + seconds * 0.2) % 1.0;
                    double a = ph + k * Math.PI + (1 - f) * 2.2;
                    double rad = Math.max(0, ringR * (1 - f));
                    double qx = at[k][0] + (e1[0] * Math.cos(a) + e2[0] * Math.sin(a)) * rad * 0.6,
                            qy = at[k][1] + (e1[1] * Math.cos(a) + e2[1] * Math.sin(a)) * rad * 0.6,
                            qz = at[k][2] + (e1[2] * Math.cos(a) + e2[2] * Math.sin(a)) * rad * 0.6;
                    Space3D.billboard(Space3D.GLOW, ADD, qx, qy, qz, Math.max(px * 1.2, ringR * 0.03), 0F, 1F, 0.75F + 0.2F * (float) f, 0.5F + 0.4F * (float) f,
                            0.18F * (float) f * vis);
                }
            }
        }
        // At the merger: no fireball (it is gravitational waves that carry the energy away), only the gas round it
        // stirred up and briefly lit as spacetime rings.
        if (t > MERGE - 1.5 && t < MERGE + 6) {
            float f = (float) Math.exp(-Math.abs(t - MERGE) * 0.9);
            for (int i = 0; i < 60; i++) {
                double a = Space3D.h(seed, 2000 + i) * Math.PI * 2 + (t - MERGE) * 3;
                double rad = scale * (0.15 + 0.5 * Space3D.h(seed, 2100 + i)) * (1 + Math.max(0, t - MERGE) * 0.08);
                double qx = cx + (e1[0] * Math.cos(a) + e2[0] * Math.sin(a)) * rad, qy = cy + (e1[1] * Math.cos(a) + e2[1] * Math.sin(a)) * rad,
                        qz = cz + (e1[2] * Math.cos(a) + e2[2] * Math.sin(a)) * rad;
                Space3D.billboard(Space3D.GLOW, ADD, qx, qy, qz, Math.max(px * 2, scale * 0.06), 0F, 0.75F, 0.85F, 1F, 0.3F * f * vis);
            }
        }
    }

    /** The rippling sheet of spacetime. */
    private static void grid(double cx, double cy, double cz, double scale, double[] axis, double[] e1, double[] e2, double t, double px, double size,
                             float vis) {
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
        double amp = strength(tr) * scale * 0.12 / Math.pow(Math.max(rho / scale, 1.0), 0.7);
        // Two crests round the circle (the waves of a pair are quadrupolar), turning with twice the orbital phase.
        double h = amp * Math.cos(2 * (a - phase(tr)));
        // The dip of the pair's gravity well.
        double well = -scale * 0.25 / (rho / scale + 1.0);
        double z = h + well;
        out[0] = cx + (e1[0] * Math.cos(a) + e2[0] * Math.sin(a)) * rho + axis[0] * z;
        out[1] = cy + (e1[1] * Math.cos(a) + e2[1] * Math.sin(a)) * rho + axis[1] * z;
        out[2] = cz + (e1[2] * Math.cos(a) + e2[2] * Math.sin(a)) * rho + axis[2] * z;
        return (float) Math.min(1, Math.abs(h) / (scale * 0.06));
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
