package com.realweather.client.space;

import java.util.ArrayList;
import java.util.List;

/**
 * The solar system to scale, for flying through it: the Sun, the planets and dwarf planets on their orbits, their
 * moons round them, and comets, each at its real distance (km) and with its real size, moving on as the days go by
 * (one game day for one day). Positions are from the Sun, the ecliptic in x and z, its north along y.
 */
final class SolarSystem {
    static final double AU = 1.495978707e8, C = 299792.458;
    static final int SUN = 0, ROCKY = 1, GAS = 2, COMET = 3;

    /** One body: its id (as in the catalogue), what it goes round, orbit (km, days, start, tilt), radius (km) and kind. */
    record Body(String id, String parent, double a, double period, double phase, double incl, double node, double radius, int kind) {}

    static final List<Body> ALL = new ArrayList<>();

    private SolarSystem() {}

    private static void add(String id, String parent, double a, double period, double phase, double incl, double node, double radius, int kind) {
        ALL.add(new Body(id, parent, a, period, phase, incl, node, radius, kind));
    }

    static {
        add("sun", null, 0, 1, 0, 0, 0, 696340, SUN);
        add("mercury", null, 57.91e6, 87.97, 0.12, 7.0, 48, 2440, ROCKY);
        add("venus", null, 108.21e6, 224.7, 0.61, 3.4, 77, 6052, ROCKY);
        add("earth", null, 149.6e6, 365.25, 0.3, 0, 0, 6371, ROCKY);
        add("moon", "earth", 384400, 27.32, 0.2, 5.1, 30, 1737, ROCKY);
        add("mars", null, 227.94e6, 686.98, 0.83, 1.85, 49, 3390, ROCKY);
        add("ceres", null, 413.7e6, 1680, 0.45, 10.6, 80, 470, ROCKY);
        add("jupiter", null, 778.5e6, 4332.6, 0.07, 1.3, 100, 69911, GAS);
        add("io", "jupiter", 421700, 1.769, 0.1, 0.05, 0, 1822, ROCKY);
        add("europa", "jupiter", 671034, 3.551, 0.6, 0.47, 0, 1561, ROCKY);
        add("ganymede", "jupiter", 1070412, 7.155, 0.3, 0.2, 0, 2634, ROCKY);
        add("callisto", "jupiter", 1882709, 16.69, 0.8, 0.28, 0, 2410, ROCKY);
        add("saturn", null, 1433.5e6, 10759, 0.55, 2.49, 113, 58232, GAS);
        add("enceladus", "saturn", 237948, 1.37, 0.4, 0.02, 0, 252, ROCKY);
        add("titan", "saturn", 1221870, 15.95, 0.4, 0.35, 0, 2575, ROCKY);
        add("uranus", null, 2872.5e6, 30687, 0.22, 0.77, 74, 25362, GAS);
        add("neptune", null, 4495.1e6, 60190, 0.94, 1.77, 131, 24622, GAS);
        add("triton", "neptune", 354759, -5.877, 0.5, 157, 0, 1353, ROCKY);
        add("pluto", null, 5906.4e6, 90560, 0.7, 17.2, 110, 1188, ROCKY);
        add("charon", "pluto", 19591, 6.387, 0.2, 0.0, 0, 606, ROCKY);
        add("haumea", null, 6452e6, 103468, 0.35, 28.2, 122, 816, ROCKY);
        add("hiiaka", "haumea", 49880, 49.1, 0.3, 0, 0, 160, ROCKY);
        add("namaka", "haumea", 25657, 18.3, 0.7, 13, 0, 85, ROCKY);
        add("makemake", null, 6850e6, 111845, 0.15, 29, 79, 715, ROCKY);
        add("eris", null, 10152e6, 203830, 0.9, 44, 36, 1163, ROCKY);
        add("dysnomia", "eris", 37300, 15.79, 0.5, 0, 0, 350, ROCKY);
        // Comets, on their way in towards the Sun (their tails growing).
        add("halley", null, 1.4 * AU, 3000, 0.37, 18, 58, 5.5, COMET);
        add("hale_bopp", null, 2.1 * AU, 6000, 0.12, 89, 282, 30, COMET);
        add("neowise", null, 0.95 * AU, 1800, 0.74, 129, 61, 2.5, COMET);
    }

    static Body byId(String id) {
        for (Body b : ALL) {
            if (b.id.equals(id)) {
                return b;
            }
        }
        return null;
    }

    /** Where a body is (km from the Sun) on a given day. */
    static void position(Body b, double days, double[] out) {
        if (b.parent == null) {
            out[0] = out[1] = out[2] = 0;
        } else {
            position(byId(b.parent), days, out);
        }
        if (b.a <= 0) {
            return;
        }
        double th = Math.PI * 2 * (b.phase + days / b.period);
        double x = b.a * Math.cos(th), z = b.a * Math.sin(th);
        double inc = Math.toRadians(b.incl), node = Math.toRadians(b.node);
        // Tilted about the line of nodes.
        double cn = Math.cos(node), sn = Math.sin(node);
        double xa = x * cn + z * sn, za = -x * sn + z * cn;
        double y = za * Math.sin(inc);
        za *= Math.cos(inc);
        out[0] += xa * cn - za * sn;
        out[1] += y;
        out[2] += xa * sn + za * cn;
    }

    /** Which way it is going (unit), for a comet's curving dust tail. */
    static void velocity(Body b, double days, double[] out) {
        double[] p0 = new double[3], p1 = new double[3];
        position(b, days, p0);
        position(b, days + 0.01, p1);
        double l = Math.sqrt(sq(p1[0] - p0[0]) + sq(p1[1] - p0[1]) + sq(p1[2] - p0[2])) + 1.0E-9;
        for (int i = 0; i < 3; i++) {
            out[i] = (p1[i] - p0[i]) / l;
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    /**
     * Standing on a world, how its sky lies in the solar system: the world's axes (x east, y up, z south) as directions
     * in space (columns of a 3 x 3, column-major), with its star where the game's sun is (the time of day `th`, as
     * the game's sky turns) and its north towards the ecliptic's.
     */
    static double[] frame(Body b, double days, double th) {
        double[] p = new double[3];
        position(b, days, p);
        double l = Math.sqrt(p[0] * p[0] + p[2] * p[2]) + 1.0E-9;
        // Towards the Sun, in the ecliptic.
        double sx = -p[0] / l, sz = -p[2] / l;
        // q = north x s.
        double qx = sz, qz = -sx;
        double c = Math.cos(th), s = Math.sin(th);
        double[] ex = {-s * sx + c * qx, 0, -s * sz + c * qz};
        double[] ey = {c * sx + s * qx, 0, c * sz + s * qz};
        return new double[]{ex[0], ex[1], ex[2], ey[0], ey[1], ey[2], 0, -1, 0};
    }
}
