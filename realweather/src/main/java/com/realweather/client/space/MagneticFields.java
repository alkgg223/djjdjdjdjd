package com.realweather.client.space;

import static com.realweather.client.space.Space3D.ADD;

/**
 * The magnetic fields of the planets that have them, as their field lines: loops from pole to pole round each world,
 * tilted from its spin axis as they really are (Uranus's and Neptune's far over, and off centre), with charged
 * particles drifting along them. A visualisation: the field itself can't be seen.
 */
final class MagneticFields {
    /** Field strength (how far the lines reach, in radii) and tilt from the spin axis (degrees); 0: no global field. */
    private static double[] look(String id) {
        return switch (id) {
            case "earth" -> new double[]{6, 11};
            case "jupiter" -> new double[]{9, 10};
            case "saturn" -> new double[]{7, 0};
            case "uranus" -> new double[]{5, 59};
            case "neptune" -> new double[]{5, 47};
            case "mercury" -> new double[]{2.5, 3};
            case "ganymede" -> new double[]{2.5, 4};
            default -> null;
        };
    }

    static boolean has(String id) {
        return look(id) != null;
    }

    /** Round a world at c (view) of radius r, its spin axis (view, unit), seconds, view units per pixel there. */
    static void draw(String id, double cx, double cy, double cz, double r, double[] axis, double seconds, double px, float vis) {
        double[] lk = look(id);
        if (lk == null || vis <= 0.01F) {
            return;
        }
        double tilt = Math.toRadians(lk[1]);
        double[] e1 = perp(axis);
        double[] e2 = {axis[1] * e1[2] - axis[2] * e1[1], axis[2] * e1[0] - axis[0] * e1[2], axis[0] * e1[1] - axis[1] * e1[0]};
        double turn = seconds * 0.05;
        double[] m = new double[3];
        for (int i = 0; i < 3; i++) {
            m[i] = axis[i] * Math.cos(tilt) + (e1[i] * Math.cos(turn) + e2[i] * Math.sin(turn)) * Math.sin(tilt);
        }
        double[] f1 = perp(m);
        double[] f2 = {m[1] * f1[2] - m[2] * f1[1], m[2] * f1[0] - m[0] * f1[2], m[0] * f1[1] - m[1] * f1[0]};
        // Uranus's and Neptune's fields are off centre too.
        double off = id.equals("uranus") ? 0.3 : id.equals("neptune") ? 0.55 : 0;
        double ox = cx + f1[0] * r * off, oy = cy + f1[1] * r * off, oz = cz + f1[2] * r * off;
        int lons = 10;
        double[] shells = {1.6, 2.4, lk[0] * 0.6, lk[0]};
        for (int s = 0; s < shells.length; s++) {
            double L = shells[s];
            float a = (float) (0.32 - s * 0.05) * vis;
            for (int k = 0; k < lons; k++) {
                double ph = Math.PI * 2 * k / lons + s * 0.3;
                double cph = Math.cos(ph), sph = Math.sin(ph);
                double[] prev = null;
                // The dipole line r = L sin^2(theta), from one foot to the other.
                double th0 = Math.asin(Math.min(1, Math.sqrt(1 / L)));
                for (int i = 0; i <= 48; i++) {
                    double th = th0 + (Math.PI - 2 * th0) * i / 48;
                    double rr = L * Math.sin(th) * Math.sin(th) * r;
                    double along = Math.cos(th) * rr, across = Math.sin(th) * rr;
                    double[] q = {ox + m[0] * along + (f1[0] * cph + f2[0] * sph) * across, oy + m[1] * along + (f1[1] * cph + f2[1] * sph) * across,
                            oz + m[2] * along + (f1[2] * cph + f2[2] * sph) * across};
                    if (prev != null) {
                        Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, prev[0], prev[1], prev[2], q[0], q[1], q[2], Math.max(px * 0.7, r * 0.004),
                                Math.max(px * 0.7, r * 0.004), 0.45F, 0.8F, 1F, a, a);
                    }
                    prev = q;
                }
                // A particle bouncing along it.
                double u = 0.5 + 0.45 * Math.sin(seconds * (0.6 + 0.1 * k) + k + s);
                double th = th0 + (Math.PI - 2 * th0) * u;
                double rr = L * Math.sin(th) * Math.sin(th) * r;
                double along = Math.cos(th) * rr, across = Math.sin(th) * rr;
                Space3D.billboard(Space3D.GLOW, ADD, ox + m[0] * along + (f1[0] * cph + f2[0] * sph) * across,
                        oy + m[1] * along + (f1[1] * cph + f2[1] * sph) * across, oz + m[2] * along + (f1[2] * cph + f2[2] * sph) * across,
                        Math.max(px * 1.5, r * 0.02), 0F, 0.6F, 0.9F, 1F, a * 1.5F);
            }
        }
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
