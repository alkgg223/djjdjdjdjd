package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * What the telescope can find: the Moon and the planets (moving along the ecliptic from night to night, with their own
 * moons), bright stars, and the stars with the exoplanets of the list, each planet going round its star on its own
 * orbit. Sizes and orbits are angles (radians) on the sky: far too small to see without the telescope, so it has to be
 * pointed at the place and zoomed right in.
 */
public final class Catalog {
    public static final int PLANETS = 0, STARS = 1, EXOPLANETS = 2;
    /** Kinds of body. */
    static final int ROCKY = 0, GAS = 1, STAR = 2, PULSAR = 3, DWARF = 4;

    /** One body: its look, size, and orbit round the centre of its system (none for the centre itself). */
    static final class Body {
        final String id;
        final int kind;
        final ResourceLocation map;
        final double radius;
        final double orbit, period, incl, node, phase;
        final float r, g, b;
        /** Its own glow (a hot young planet), or a star's flames. */
        float glow;
        boolean air, rings, tail;
        float ar = 0.6F, ag = 0.75F, ab = 1F;

        Body(String id, int kind, String map, double radius, double orbit, double period, double incl, double node, double phase, float r, float g,
             float b) {
            this.id = id;
            this.kind = kind;
            this.map = map == null ? null : RealWeather.id("textures/environment/space3d/" + map + ".png");
            this.radius = radius;
            this.orbit = orbit;
            this.period = period;
            this.incl = Math.toRadians(incl);
            this.node = Math.toRadians(node);
            this.phase = phase;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        Body glow(float v) {
            glow = v;
            return this;
        }

        Body air(float r, float g, float b) {
            air = true;
            ar = r;
            ag = g;
            ab = b;
            return this;
        }

        Body rings() {
            rings = true;
            return this;
        }

        Body tail() {
            tail = true;
            return this;
        }
    }

    /** A place in the sky: a centre body (planet or star) and what goes round it. */
    static final class Sys {
        final String id;
        /** Fixed direction in the sky frame (stars), or null for a planet of the solar system. */
        final double[] dir;
        /** Elongation from the sun at day 0, its swing (inner planets) or change per day (outer ones), and latitude. */
        final double lon0, lonRate, lat;
        final boolean inner;
        final String distance;
        final List<Body> bodies = new ArrayList<>();
        boolean disk, cluster;

        Sys(String id, double[] dir, String distance) {
            this.id = id;
            this.dir = dir;
            this.lon0 = this.lonRate = this.lat = 0;
            this.inner = false;
            this.distance = distance;
        }

        Sys(String id, double lon0, double lonRate, double lat, boolean inner, String distance) {
            this.id = id;
            this.dir = null;
            this.lon0 = Math.toRadians(lon0);
            this.lonRate = lonRate;
            this.lat = lat;
            this.inner = inner;
            this.distance = distance;
        }

        Sys add(Body b) {
            bodies.add(b);
            return this;
        }

        /** Direction in the sky frame at a given (fractional) day. */
        double[] direction(double day) {
            if (dir != null) {
                return dir;
            }
            double lon = inner ? Math.toRadians(lonRate) * Math.sin(Math.PI * 2 * day / 37.0 + lon0) : lon0 + Math.PI * 2 * day / lonRate;
            double x = lat, y = Math.cos(lon), z = Math.sin(lon);
            double l = Math.sqrt(x * x + y * y + z * z);
            return new double[]{x / l, y / l, z / l};
        }
    }

    /** A menu entry: a body in a system. */
    public static final class Target {
        public final String id;
        public final int group;
        final Sys sys;
        final Body body;

        Target(String id, int group, Sys sys, Body body) {
            this.id = id;
            this.group = group;
            this.sys = sys;
            this.body = body;
        }
    }

    static final List<Sys> SYSTEMS = new ArrayList<>();
    public static final List<Target> TARGETS = new ArrayList<>();
    static Sys moon;

    private static double[] d(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    static {
        // The Moon: where vanilla's is, at its size.
        moon = new Sys("moon", d(0, -1, 0), "384 400 km");
        moon.add(new Body("moon", ROCKY, "moon", Math.atan(0.1), 0, 0, 0, 0, 0, 1F, 1F, 1F));
        SYSTEMS.add(moon);
        TARGETS.add(new Target("moon", PLANETS, moon, moon.bodies.get(0)));

        planet("mercury", new Sys("mercury", 0.4, 22, 0.03, true, "0.6-1.4 AU"), new Body("mercury", ROCKY, "mercury", 0.35e-4, 0, 0, 0, 0, 0, 1F, 0.98F, 0.95F));
        planet("venus", new Sys("venus", 1.7, 44, -0.02, true, "0.3-1.7 AU"),
                new Body("venus", ROCKY, "venus", 1.2e-4, 0, 0, 0, 0, 0, 1F, 1F, 0.95F).air(1F, 0.92F, 0.7F));
        planet("mars", new Sys("mars", 200, 40, 0.05, false, "0.4-2.5 AU"), new Body("mars", ROCKY, "mars", 0.6e-4, 0, 0, 0, 0, 0, 1F, 1F, 1F)
                .air(1F, 0.7F, 0.5F));
        Sys jupiter = new Sys("jupiter", 165, 80, -0.04, false, "4.2-6.2 AU");
        jupiter.add(new Body("jupiter", GAS, "jupiter", 2.0e-4, 0, 0, 0, 0, 0, 1F, 1F, 1F).air(0.9F, 0.85F, 0.75F));
        // The four moons Galileo saw, in a line either side.
        jupiter.add(new Body("io", ROCKY, "venus", 0.11e-4, 5.9 * 2.0e-4, 1200, 88, 0, 0.1, 1F, 0.95F, 0.6F));
        jupiter.add(new Body("europa", ROCKY, "lhs1140b", 0.09e-4, 9.4 * 2.0e-4, 2400, 88, 0, 0.6, 1F, 0.98F, 0.92F));
        jupiter.add(new Body("ganymede", ROCKY, "mercury", 0.15e-4, 15 * 2.0e-4, 4800, 88, 0, 0.3, 0.9F, 0.88F, 0.82F));
        jupiter.add(new Body("callisto", ROCKY, "moon", 0.14e-4, 26 * 2.0e-4, 11000, 88, 0, 0.8, 0.7F, 0.66F, 0.6F));
        SYSTEMS.add(jupiter);
        TARGETS.add(new Target("jupiter", PLANETS, jupiter, jupiter.bodies.get(0)));
        Sys saturn = new Sys("saturn", 140, 75, 0.06, false, "8.0-11.1 AU");
        saturn.add(new Body("saturn", GAS, "saturn", 0.9e-4, 0, 0, 0, 0, 0, 1F, 1F, 1F).rings());
        saturn.add(new Body("titan", ROCKY, "venus", 0.07e-4, 20 * 0.9e-4, 8000, 80, 20, 0.4, 1F, 0.8F, 0.5F));
        SYSTEMS.add(saturn);
        TARGETS.add(new Target("saturn", PLANETS, saturn, saturn.bodies.get(0)));
        planet("uranus", new Sys("uranus", 222, 74, -0.07, false, "18-20 AU"), new Body("uranus", GAS, "uranus", 0.25e-4, 0, 0, 0, 0, 0, 1F, 1F, 1F)
                .air(0.6F, 0.9F, 0.95F));
        planet("neptune", new Sys("neptune", 118, 73, 0.08, false, "29-31 AU"),
                new Body("neptune", GAS, "neptune", 0.18e-4, 0, 0, 0, 0, 0, 1F, 1F, 1F).air(0.4F, 0.55F, 1F));

        // Bright stars.
        star("sirius", d(0.35, -0.8, 0.45), "8.6 ly", new Body("sirius", STAR, "star_surface", 4e-6, 0, 0, 0, 0, 0, 0.82F, 0.88F, 1F).glow(0.8F),
                new Body("sirius_b", DWARF, "star_surface", 0.7e-6, 3.5e-5, 20000, 50, 30, 0.2, 0.9F, 0.93F, 1F));
        star("betelgeuse", d(-0.5, -0.7, -0.5), "550 ly", new Body("betelgeuse", STAR, "star_surface", 2.2e-5, 0, 0, 0, 0, 0, 1F, 0.42F, 0.18F)
                .glow(1.6F));
        star("vega", d(0.6, -0.6, -0.3), "25 ly", new Body("vega", STAR, "star_surface", 5e-6, 0, 0, 0, 0, 0, 0.75F, 0.84F, 1F).glow(0.8F));
        star("polaris", d(-0.92, -0.35, 0.15), "430 ly", new Body("polaris", STAR, "star_surface", 6e-6, 0, 0, 0, 0, 0, 1F, 0.94F, 0.78F).glow(0.9F));
        star("antares", d(0.1, -0.9, -0.42), "550 ly", new Body("antares", STAR, "star_surface", 1.8e-5, 0, 0, 0, 0, 0, 1F, 0.35F, 0.15F).glow(1.5F),
                new Body("antares_b", STAR, "star_surface", 1.6e-6, 4.5e-5, 30000, 40, 60, 0.7, 0.7F, 0.82F, 1F).glow(0.5F));
        star("rigel", d(-0.2, -0.55, 0.8), "860 ly", new Body("rigel", STAR, "star_surface", 7e-6, 0, 0, 0, 0, 0, 0.7F, 0.8F, 1F).glow(1F));

        // The exoplanets and their stars.
        Sys gj436 = new Sys("gj436", d(0.45, -0.75, -0.1), "32 ly");
        gj436.add(new Body("gj436", STAR, "star_surface", 5e-6, 0, 0, 0, 0, 0, 1F, 0.45F, 0.25F).glow(1F));
        gj436.add(new Body("gj436b", GAS, "gj436b", 2.2e-6, 1.7e-5, 900, 86, 10, 0.1, 1F, 1F, 1F).air(0.6F, 0.8F, 1F).tail());
        exo(gj436, "gj436b");
        Sys lhs1140 = new Sys("lhs1140", d(-0.3, -0.85, 0.35), "49 ly");
        lhs1140.add(new Body("lhs1140", STAR, "star_surface", 5e-6, 0, 0, 0, 0, 0, 1F, 0.42F, 0.22F).glow(1F));
        lhs1140.add(new Body("lhs1140b", ROCKY, "lhs1140b", 1.7e-6, 4.5e-5, 3000, 78, -20, 0.3, 1F, 1F, 1F).air(0.7F, 0.85F, 1F));
        exo(lhs1140, "lhs1140b");
        Sys k218 = new Sys("k2_18", d(0.7, -0.5, 0.35), "124 ly");
        k218.add(new Body("k2_18", STAR, "star_surface", 5.5e-6, 0, 0, 0, 0, 0, 1F, 0.5F, 0.3F).glow(1F));
        k218.add(new Body("k2_18b", GAS, "k2_18b", 2.1e-6, 4e-5, 2800, 70, 35, 0.55, 1F, 1F, 1F).air(0.4F, 0.8F, 1F));
        exo(k218, "k2_18b");
        Sys toi700 = new Sys("toi700", d(-0.55, -0.8, -0.1), "101 ly");
        toi700.add(new Body("toi700", STAR, "star_surface", 5e-6, 0, 0, 0, 0, 0, 1F, 0.48F, 0.28F).glow(1F));
        toi700.add(new Body("toi700e", ROCKY, "toi700e", 1.35e-6, 4.0e-5, 2700, 74, 0, 0.2, 1F, 1F, 1F).air(0.7F, 0.8F, 1F));
        toi700.add(new Body("toi700d", ROCKY, "toi700d", 1.45e-6, 5.4e-5, 3700, 74, 0, 0.65, 1F, 1F, 1F).air(0.5F, 0.7F, 1F));
        exo(toi700, "toi700d");
        exo(toi700, "toi700e");
        Sys betapic = new Sys("betapic", d(0.2, -0.6, -0.75), "63 ly");
        betapic.add(new Body("betapic", STAR, "star_surface", 6e-6, 0, 0, 0, 0, 0, 0.92F, 0.93F, 1F).glow(1F));
        betapic.add(new Body("betapic_b", GAS, "betapic_b", 2.6e-6, 5.5e-5, 6000, 88.5, 30, 0.35, 1F, 1F, 1F).air(0.9F, 0.75F, 0.6F));
        betapic.disk = true;
        exo(betapic, "betapic_b");
        Sys hr8799 = new Sys("hr8799", d(-0.1, -0.7, 0.7), "133 ly");
        hr8799.add(new Body("hr8799", STAR, "star_surface", 6e-6, 0, 0, 0, 0, 0, 0.88F, 0.92F, 1F).glow(1F));
        hr8799.add(new Body("hr8799e", GAS, "hr8799e", 2.4e-6, 2.6e-5, 2600, 25, 0, 0.0, 1F, 1F, 1F).glow(0.22F).air(1F, 0.6F, 0.5F));
        hr8799.add(new Body("hr8799d", GAS, "hr8799d", 2.5e-6, 4.0e-5, 5000, 25, 0, 0.3, 1F, 1F, 1F).glow(0.2F).air(1F, 0.7F, 0.4F));
        hr8799.add(new Body("hr8799c", GAS, "hr8799c", 2.5e-6, 5.6e-5, 8000, 25, 0, 0.55, 1F, 1F, 1F).glow(0.18F).air(1F, 0.6F, 0.7F));
        hr8799.add(new Body("hr8799b", GAS, "hr8799b", 2.3e-6, 7.5e-5, 13000, 25, 0, 0.8, 1F, 1F, 1F).glow(0.2F).air(1F, 0.5F, 0.4F));
        for (String p : new String[]{"hr8799b", "hr8799c", "hr8799d", "hr8799e"}) {
            exo(hr8799, p);
        }
        Sys psr = new Sys("psr1620", d(0.05, -0.95, -0.2), "12 400 ly (M4)");
        psr.add(new Body("psr1620", PULSAR, "star_surface", 0.7e-6, 0.4e-5, 1600, 50, 0, 0.0, 0.75F, 0.85F, 1F).glow(1F));
        psr.add(new Body("psr1620_wd", DWARF, "star_surface", 1.2e-6, 0.9e-5, 1600, 50, 0, 0.5, 0.95F, 0.95F, 1F));
        psr.add(new Body("psr_b1620_26b", GAS, "psr_b", 2.4e-6, 6.2e-5, 12000, 55, 40, 0.25, 1F, 1F, 1F).air(0.8F, 0.85F, 0.95F));
        psr.cluster = true;
        exo(psr, "psr_b1620_26b");
    }

    private static void planet(String id, Sys sys, Body body) {
        sys.add(body);
        SYSTEMS.add(sys);
        TARGETS.add(new Target(id, PLANETS, sys, body));
    }

    private static void star(String id, double[] dir, String distance, Body... bodies) {
        Sys sys = new Sys(id, dir, distance);
        for (Body b : bodies) {
            sys.add(b);
        }
        SYSTEMS.add(sys);
        TARGETS.add(new Target(id, STARS, sys, bodies[0]));
    }

    private static void exo(Sys sys, String planet) {
        if (!SYSTEMS.contains(sys)) {
            SYSTEMS.add(sys);
        }
        for (Body b : sys.bodies) {
            if (b.id.equals(planet)) {
                TARGETS.add(new Target(planet, EXOPLANETS, sys, b));
                return;
            }
        }
    }

    public static Target byId(String id) {
        for (Target t : TARGETS) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    public static List<Target> group(int group) {
        List<Target> out = new ArrayList<>();
        for (Target t : TARGETS) {
            if (t.group == group) {
                out.add(t);
            }
        }
        return out;
    }

    private Catalog() {}
}
