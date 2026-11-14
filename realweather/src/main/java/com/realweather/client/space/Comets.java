package com.realweather.client.space;

import static com.realweather.client.space.Space3D.ADD;

/**
 * A comet near the Sun: its small dark nucleus with jets of gas on the sunlit side, the glowing coma round it, the
 * straight blue ion tail (gas ionised by sunlight and blown straight back by the solar wind, streaming in rays with
 * knots and kinks racing outwards along it), and the broad, curving yellow-white dust tail (dust pushed back more
 * gently by sunlight, so it lags behind along the comet's path and fans out), all in 3D.
 */
final class Comets {
    private Comets() {}

    /**
     * c: the nucleus (view), coma radius (view units), sun: unit view direction from the comet towards the Sun, vel:
     * unit view direction it is moving in, the length of its tails (view units), seconds, view units per pixel there.
     */
    static void draw(double cx, double cy, double cz, double coma, double[] sun, double[] vel, double tail, long seed, double seconds, double px,
                     float vis) {
        if (vis <= 0.01F) {
            return;
        }
        double ax = -sun[0], ay = -sun[1], az = -sun[2];
        // Two directions across the tail.
        double[] e1 = cross(ax, ay, az, vel[0], vel[1], vel[2]);
        if (len(e1) < 1.0E-6) {
            e1 = cross(ax, ay, az, 0, 1, 0);
        }
        norm(e1);
        double[] e2 = cross(ax, ay, az, e1[0], e1[1], e1[2]);
        norm(e2);
        // The dust tail: curved back along the comet's path, broad and fanned, built from many soft clouds of dust.
        for (int i = 0; i < 260; i++) {
            double s = Math.pow(Space3D.h(seed, i * 5L), 0.8);
            double sync = (Space3D.h(seed, i * 5L + 1) - 0.5);
            double lag = s * s * 0.55 + sync * s * 0.35;
            double spread = (Space3D.h(seed, i * 5L + 2) - 0.5) * s * 0.12;
            double qx = cx + (ax * s - vel[0] * lag) * tail + e2[0] * spread * tail, qy = cy + (ay * s - vel[1] * lag) * tail + e2[1] * spread * tail,
                    qz = cz + (az * s - vel[2] * lag) * tail + e2[2] * spread * tail;
            double size = Math.max(px * 3, coma * 0.8 + tail * 0.06 * s);
            float a = (float) (0.13 * (1 - s) * (0.6 + 0.4 * Math.sin(sync * 40 + seconds * 0.02))) * vis;
            Space3D.billboard(Space3D.GLOW, ADD, qx, qy, qz, size, 0F, 1F, 0.9F, 0.72F, a);
        }
        // The ion tail: straight back from the Sun, a bundle of thin rays.
        for (int k = 0; k < 9; k++) {
            double fan = (k - 4) * 0.012 + Math.sin(seconds * 0.07 + k) * 0.004;
            double tw = (Space3D.h(seed, 100 + k) - 0.5) * 0.02;
            double dx = ax + e1[0] * fan + e2[0] * tw, dy = ay + e1[1] * fan + e2[1] * tw, dz = az + e1[2] * fan + e2[2] * tw;
            double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= dl;
            dy /= dl;
            dz /= dl;
            double l = tail * (1.1 + 0.5 * Space3D.h(seed, 120 + k));
            int n = 10;
            for (int j = 0; j < n; j++) {
                double s0 = (double) j / n, s1 = (double) (j + 1) / n;
                // A kink travelling out along the ray.
                double kink0 = Math.sin(s0 * 12 - seconds * 0.6 + k) * 0.008 * s0 * l, kink1 = Math.sin(s1 * 12 - seconds * 0.6 + k) * 0.008 * s1 * l;
                Space3D.ribbon(Space3D.GLOW, ADD, cx + dx * l * s0 + e1[0] * kink0, cy + dy * l * s0 + e1[1] * kink0, cz + dz * l * s0 + e1[2] * kink0,
                        cx + dx * l * s1 + e1[0] * kink1, cy + dy * l * s1 + e1[1] * kink1, cz + dz * l * s1 + e1[2] * kink1,
                        Math.max(px * 0.8, coma * 0.12 + l * 0.004 * s0), Math.max(px * 0.8, coma * 0.12 + l * 0.004 * s1), 0.45F, 0.68F, 1F,
                        (float) (0.4 * (1 - s0 * 0.8)) * vis, (float) (0.4 * (1 - s1 * 0.8)) * vis);
            }
            // Knots of plasma racing outwards.
            for (int j = 0; j < 4; j++) {
                double s = (Space3D.h(seed, 200 + k * 7 + j) + seconds * 0.05) % 1.0;
                Space3D.billboard(Space3D.GLOW, ADD, cx + dx * l * s, cy + dy * l * s, cz + dz * l * s, Math.max(px * 2, coma * 0.4 + l * 0.01 * s), 0F,
                        0.5F, 0.72F, 1F, (float) (0.3 * (1 - s)) * vis);
            }
        }
        // The coma and the nucleus.
        Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, Math.max(px * 6, coma * 3.5), 0F, 0.55F, 0.95F, 0.75F, 0.35F * vis);
        Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, Math.max(px * 3, coma * 1.2), 0F, 0.8F, 1F, 0.9F, 0.6F * vis);
        Space3D.billboard(Space3D.STAR, ADD, cx, cy, cz, Math.max(px * 6, coma * 2), 0F, 0.9F, 1F, 0.95F, 0.45F * vis);
        // Jets of gas off the sunlit side of the nucleus.
        for (int j = 0; j < 5; j++) {
            double a = seconds * 0.3 + j * 1.3;
            double jx = sun[0] + e1[0] * Math.cos(a) * 0.5 + e2[0] * Math.sin(a) * 0.5, jy = sun[1] + e1[1] * Math.cos(a) * 0.5 + e2[1] * Math.sin(a) * 0.5,
                    jz = sun[2] + e1[2] * Math.cos(a) * 0.5 + e2[2] * Math.sin(a) * 0.5;
            Space3D.ribbon(Space3D.GLOW, ADD, cx, cy, cz, cx + jx * coma * 0.8, cy + jy * coma * 0.8, cz + jz * coma * 0.8, Math.max(px, coma * 0.05),
                    Math.max(px, coma * 0.15), 0.85F, 1F, 0.9F, 0.35F * vis, 0F);
        }
    }

    private static double[] cross(double ax, double ay, double az, double bx, double by, double bz) {
        return new double[]{ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

    private static double len(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static void norm(double[] v) {
        double l = len(v) + 1.0E-12;
        v[0] /= l;
        v[1] /= l;
        v[2] /= l;
    }
}
