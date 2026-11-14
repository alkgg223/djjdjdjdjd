package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.resources.ResourceLocation;

import static com.realweather.client.space.Space3D.ADD;

/**
 * The swarms of small bodies round the Sun, for flying through the solar system: the main asteroid belt between Mars
 * and Jupiter, the icy Kuiper Belt beyond Neptune, and the vast, faint Oort Cloud of comet nuclei far out round it all.
 * From afar each is a scatter of faint points on their orbits; inside one, the rocks and ice near you are drawn as
 * bodies of their own you can fly among (far more crowded than the real belts, so there is something to see).
 */
final class Belts {
    private static final ResourceLocation ROCK = RealWeather.id("textures/environment/space3d/moon.png"),
            ICE = RealWeather.id("textures/environment/space3d/eris.png");
    private static final double AU = SolarSystem.AU;
    /** Semi-major axis (AU), eccentricity, inclination, node, perihelion, phase; for each member. */
    private static double[][] asteroids, kuiper, oort;

    private Belts() {}

    private static double[][] make(int n, long seed, double a0, double a1, double eMax, double iMax, boolean sphere) {
        double[][] out = new double[n][6];
        for (int i = 0; i < n; i++) {
            double u = Space3D.h(seed, i * 7L);
            out[i][0] = sphere ? a0 * Math.pow(a1 / a0, u) : a0 + (a1 - a0) * u;
            out[i][1] = Space3D.h(seed, i * 7L + 1) * eMax;
            out[i][2] = sphere ? Math.acos(1 - 2 * Space3D.h(seed, i * 7L + 2)) : Space3D.h(seed, i * 7L + 2) * iMax;
            out[i][3] = Space3D.h(seed, i * 7L + 3) * Math.PI * 2;
            out[i][4] = Space3D.h(seed, i * 7L + 4) * Math.PI * 2;
            out[i][5] = Space3D.h(seed, i * 7L + 5);
        }
        return out;
    }

    private static void init() {
        if (asteroids == null) {
            asteroids = make(3200, 4401, 2.1, 3.3, 0.25, 0.3, false);
            kuiper = make(2200, 4402, 30, 50, 0.2, 0.5, false);
            oort = make(1600, 4403, 2000, 100000, 0.6, 0, true);
        }
    }

    /** Where a member is (km from the Sun) on a day. */
    private static void at(double[] o, double days, double[] out) {
        double a = o[0] * AU, e = o[1];
        double period = 365.25 * Math.pow(o[0], 1.5);
        double m = (o[5] + days / period) * Math.PI * 2;
        double E = m;
        for (int k = 0; k < 5; k++) {
            E -= (E - e * Math.sin(E) - m) / (1 - e * Math.cos(E));
        }
        double x = a * (Math.cos(E) - e), z = a * Math.sqrt(1 - e * e) * Math.sin(E);
        double cw = Math.cos(o[4]), sw = Math.sin(o[4]);
        double xp = x * cw - z * sw, zp = x * sw + z * cw;
        double ci = Math.cos(o[2]), si = Math.sin(o[2]), cn = Math.cos(o[3]), sn = Math.sin(o[3]);
        double y = zp * si;
        zp *= ci;
        out[0] = xp * cn - zp * sn;
        out[1] = y;
        out[2] = xp * sn + zp * cn;
    }

    /** Which belt (a translation key) the camera is in, or null. */
    static String region(double[] cam) {
        double r = Math.sqrt(cam[0] * cam[0] + cam[1] * cam[1] + cam[2] * cam[2]) / AU, y = Math.abs(cam[1]) / AU;
        if (r > 2.1 && r < 3.3 && y < 0.25) {
            return "realweather.belt.asteroids";
        }
        if (r > 30 && r < 50 && y < 6) {
            return "realweather.belt.kuiper";
        }
        if (r > 2000) {
            return "realweather.belt.oort";
        }
        return null;
    }

    /** m: frame (ecliptic) to view; cam: the camera (km from the Sun); days; radians per pixel. */
    static void draw(double[] m, double[] cam, double days, double pixAng) {
        if (!SpaceTime.belts) {
            return;
        }
        init();
        far(asteroids, m, cam, days, pixAng, 0.82F, 0.76F, 0.68F, 3.0E7);
        far(kuiper, m, cam, days, pixAng, 0.75F, 0.84F, 1F, 2.0E8);
        far(oort, m, cam, days, pixAng, 0.85F, 0.9F, 1F, 3.0E9);
        String in = region(cam);
        if (in != null) {
            boolean rocky = in.endsWith("asteroids");
            near(m, cam, pixAng, rocky ? 30000 : in.endsWith("kuiper") ? 120000 : 2000000, rocky, in.hashCode());
            Space3D.clearDepth();
            Space3D.range(5F, 80000F);
        }
    }

    private static void far(double[][] list, double[] m, double[] cam, double days, double pixAng, float r, float g, float b, double near) {
        double[] p = new double[3], v = new double[3];
        double s = 5000;
        for (double[] o : list) {
            at(o, days, p);
            double rx = p[0] - cam[0], ry = p[1] - cam[1], rz = p[2] - cam[2];
            double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (d < 1) {
                continue;
            }
            Space3D.apply(m, rx / d, ry / d, rz / d, v);
            if (v[2] > 0) {
                continue;
            }
            float bright = (float) Math.min(0.75, 0.05 + 0.6 * near / (d + near));
            Space3D.billboard(Space3D.GLOW, ADD, v[0] * s, v[1] * s, v[2] * s, pixAng * s * (1.1 + 1.6 * bright), 0F, r, g, b, bright);
        }
    }

    /** The bodies round you in a belt: a few per cell of a grid that moves with you, each a lumpy rock or ball of ice. */
    private static void near(double[] m, double[] cam, double pixAng, double cell, boolean rocky, long seed) {
        long cx = (long) Math.floor(cam[0] / cell), cy = (long) Math.floor(cam[1] / cell), cz = (long) Math.floor(cam[2] / cell);
        double sun = Math.sqrt(cam[0] * cam[0] + cam[1] * cam[1] + cam[2] * cam[2]) + 1;
        double[] light = new double[3];
        Space3D.apply(m, -cam[0] / sun, -cam[1] / sun, -cam[2] / sun, light);
        double[] v = new double[3];
        double D = 2000;
        for (long i = cx - 3; i <= cx + 3; i++) {
            for (long j = cy - 1; j <= cy + 1; j++) {
                for (long k = cz - 3; k <= cz + 3; k++) {
                    long h = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ (k * 0x165667B19E3779F9L);
                    if (Space3D.h(h, 1) > 0.45) {
                        continue;
                    }
                    double px = (i + Space3D.h(h, 2)) * cell, py = (j + Space3D.h(h, 3)) * cell, pz = (k + Space3D.h(h, 4)) * cell;
                    double radius = (rocky ? 0.3 : 2) * Math.pow(10, Space3D.h(h, 5) * 2.4);
                    double rx = px - cam[0], ry = py - cam[1], rz = pz - cam[2];
                    double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
                    Space3D.apply(m, rx / d, ry / d, rz / d, v);
                    if (v[2] > 0.2) {
                        continue;
                    }
                    double sizePx = radius / d / pixAng;
                    if (sizePx < 1.5) {
                        float b = (float) Math.min(0.8, 0.15 + sizePx * 0.4);
                        Space3D.billboard(Space3D.GLOW, ADD, v[0] * 5000, v[1] * 5000, v[2] * 5000, pixAng * 5000 * 1.6, 0F, rocky ? 0.85F : 0.8F,
                                rocky ? 0.8F : 0.88F, rocky ? 0.72F : 1F, b);
                        continue;
                    }
                    double r = radius * D / d;
                    double[] c = {v[0] * D, v[1] * D, v[2] * D};
                    Space3D.clearDepth();
                    Space3D.range((float) Math.max(0.05, D - r * 1.6), (float) (D + r * 1.6));
                    double[] rot = Space3D.orient(Space3D.h(h, 6) - 0.5, 1, Space3D.h(h, 7) - 0.5, Space3D.h(h, 8) * 6);
                    double[] rv = new double[9];
                    Space3D.mul(m, rot, rv);
                    Space3D.scale(1 + 0.5 * Space3D.h(h, 9), 0.6 + 0.3 * Space3D.h(h, 10), 0.85 + 0.3 * Space3D.h(h, 11));
                    float tone = (float) (0.55 + 0.35 * Space3D.h(h, 12));
                    Space3D.sphere(rocky ? ROCK : ICE, c[0], c[1], c[2], r, rv, light[0], light[1], light[2], 0.02F, tone * (rocky ? 0.95F : 1F),
                            tone * (rocky ? 0.88F : 1F), tone * (rocky ? 0.78F : 1.05F), 0F, 1F, false, 0F, (int) Math.max(12, Math.min(40, sizePx / 3)));
                    Space3D.scale(1, 1, 1);
                }
            }
        }
    }
}
