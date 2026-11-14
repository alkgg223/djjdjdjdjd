package com.realweather.client.space;

import static com.realweather.client.space.Space3D.ADD;

/**
 * The aurorae of other planets: rings of glowing curtains round their magnetic poles, where particles caught in the
 * field rain into the upper air. Each planet's own colours (Jupiter's violet-blue and the bright spot where Io's
 * current lands, Saturn's pink and blue, Uranus's and Neptune's pale blue, tilted far off their spin axes), each
 * curtain brightening and fading as waves run round the oval.
 */
final class Aurorae {
    /** A planet's aurora: oval size (degrees from the pole), height, colour, strength, and its magnetic tilt (degrees). */
    record Look(double colat, double height, float r, float g, float b, float strength, double tilt, boolean footprint) {}

    private Aurorae() {}

    static Look of(String id) {
        return switch (id) {
            case "jupiter" -> new Look(16, 0.035, 0.62F, 0.45F, 1F, 1F, 10, true);
            case "saturn" -> new Look(15, 0.03, 1F, 0.45F, 0.75F, 0.85F, 0, false);
            case "uranus" -> new Look(22, 0.04, 0.45F, 0.8F, 1F, 0.6F, 59, false);
            case "neptune" -> new Look(25, 0.04, 0.5F, 0.6F, 1F, 0.55F, 47, false);
            case "mars" -> new Look(40, 0.03, 0.4F, 1F, 0.5F, 0.3F, 0, false);
            case "earth_wander" -> new Look(50, 0.05, 0.4F, 1F, 0.55F, 0.8F, 65, false);
            default -> null;
        };
    }

    /**
     * The aurora of the planet `id` at c (view), radius r, its axis (view, unit), lit by `sun` (view, unit; the
     * aurora shows best on the night side), seconds, view units per pixel there.
     */
    static void draw(String id, double cx, double cy, double cz, double r, double[] axis, double[] sun, double seconds, double px, float vis) {
        Look a = of(id);
        if (a == null || vis <= 0.01F || r / px < 10) {
            return;
        }
        float detail = Space3D.smooth(10F, 40F, (float) (r / px));
        // The magnetic axis, tipped from the spin axis.
        double[] e1 = perp(axis);
        double[] e2 = {axis[1] * e1[2] - axis[2] * e1[1], axis[2] * e1[0] - axis[0] * e1[2], axis[0] * e1[1] - axis[1] * e1[0]};
        double t = Math.toRadians(a.tilt);
        double turn = seconds * 0.02;
        double[] m = new double[3];
        for (int i = 0; i < 3; i++) {
            m[i] = axis[i] * Math.cos(t) + (e1[i] * Math.cos(turn) + e2[i] * Math.sin(turn)) * Math.sin(t);
        }
        double[] f1 = perp(m);
        double[] f2 = {m[1] * f1[2] - m[2] * f1[1], m[2] * f1[0] - m[0] * f1[2], m[0] * f1[1] - m[1] * f1[0]};
        double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double[] toCam = {-cx / dl, -cy / dl, -cz / dl};
        int n = 72;
        long seed = id.hashCode();
        for (int pole = -1; pole <= 1; pole += 2) {
            double colat = Math.toRadians(a.colat) * (pole > 0 ? 1 : 1.1);
            for (int i = 0; i < n; i++) {
                double ph = Math.PI * 2 * i / n;
                // The oval is wider on the night side.
                double wob = colat * (1 + 0.12 * Math.sin(ph * 3 + seconds * 0.05) + 0.06 * Math.sin(ph * 7 - seconds * 0.11));
                double s = Math.sin(wob), c = Math.cos(wob) * pole;
                double nx = m[0] * c + (f1[0] * Math.cos(ph) + f2[0] * Math.sin(ph)) * s, ny = m[1] * c + (f1[1] * Math.cos(ph) + f2[1] * Math.sin(ph)) * s,
                        nz = m[2] * c + (f1[2] * Math.cos(ph) + f2[2] * Math.sin(ph)) * s;
                // Hidden behind the planet.
                double facing = nx * toCam[0] + ny * toCam[1] + nz * toCam[2];
                if (facing < -0.2) {
                    continue;
                }
                double night = 1 - Math.max(0, nx * sun[0] + ny * sun[1] + nz * sun[2]) * 0.65;
                float wave = (float) (0.35 + 0.65 * Math.pow(0.5 + 0.5 * Math.sin(ph * 5 - seconds * 0.35 + pole), 2)
                        * (0.6 + 0.4 * Math.sin(ph * 17 + seconds * 0.9 + Space3D.h(seed, i))));
                float alpha = (float) (a.strength * wave * night * Math.min(1, (facing + 0.2) * 3)) * detail * vis;
                double h = a.height * (0.7 + 0.6 * Space3D.h(seed, i * 3L + pole)) * (0.8 + 0.4 * wave);
                double bx = cx + nx * r * 1.004, by = cy + ny * r * 1.004, bz = cz + nz * r * 1.004;
                double tx = cx + nx * r * (1.004 + h), ty = cy + ny * r * (1.004 + h), tz = cz + nz * r * (1.004 + h);
                double w = Math.max(px * 0.8, r * Math.sin(colat) * Math.PI / n * 1.3);
                Space3D.ribbon(Space3D.GLOW, ADD, bx, by, bz, tx, ty, tz, w, w * 1.3, a.r, a.g, a.b, 0.55F * alpha, 0F);
            }
            // Where the current from Io comes down: a bright spot just outside Jupiter's main oval.
            if (a.footprint) {
                double ph = seconds * 0.08 + (pole > 0 ? 0 : Math.PI);
                double wob = Math.toRadians(a.colat * 0.75);
                double s = Math.sin(wob), c = Math.cos(wob) * pole;
                double nx = m[0] * c + (f1[0] * Math.cos(ph) + f2[0] * Math.sin(ph)) * s, ny = m[1] * c + (f1[1] * Math.cos(ph) + f2[1] * Math.sin(ph)) * s,
                        nz = m[2] * c + (f1[2] * Math.cos(ph) + f2[2] * Math.sin(ph)) * s;
                if (nx * toCam[0] + ny * toCam[1] + nz * toCam[2] > 0) {
                    Space3D.billboard(Space3D.GLOW, ADD, cx + nx * r * 1.01, cy + ny * r * 1.01, cz + nz * r * 1.01, Math.max(px * 2, r * 0.025), 0F,
                            0.75F, 0.6F, 1F, 0.8F * detail * vis);
                }
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
