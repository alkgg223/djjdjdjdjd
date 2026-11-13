package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * What the telescope can find: the Sun, the Moon, the planets and dwarf planets (moving along the ecliptic from night
 * to night, with their moons), stars from red dwarfs to hypergiants, and the stars with known exoplanets, each planet
 * going round its star on its own orbit. Sizes and orbits are angles (radians) on the sky: far too small to see without
 * the telescope, so it has to be pointed at the place and zoomed right in.
 */
public final class Catalog {
    public static final int PLANETS = 0, STARS = 1, EXOPLANETS = 2;
    /** Kinds of body. */
    static final int ROCKY = 0, GAS = 1, STAR = 2, PULSAR = 3, DWARF = 4, BLACKHOLE = 5;
    /** The planet blocks, by their place in the block atlas. */
    static final int REGOLITH = 0, BASALT = 1, SAND = 2, ICE = 3, FROST = 4, MAGMA = 5, CLOUD = 6, WATER = 7, MOSS = 8, CRYSTAL = 9, SULFUR = 10,
            IRONSTONE = 11, STORM = 12, OBSIDIAN = 13, HOTROCK = 14, ASH = 15;
    /** Star colours by spectral class. */
    private static final float[] O_B = {0.62F, 0.72F, 1F}, A_T = {0.82F, 0.88F, 1F}, F_T = {0.98F, 0.96F, 0.9F}, G_T = {1F, 0.9F, 0.72F},
            K_T = {1F, 0.7F, 0.42F}, M_T = {1F, 0.45F, 0.25F}, WD = {0.92F, 0.95F, 1F};

    /** One body: its look, size, and orbit round the centre of its system (none for the centre itself). */
    static final class Body {
        final String id;
        final int kind;
        final ResourceLocation map;
        final double radius;
        final double orbit, period, incl, node, phase;
        final float r, g, b;
        /** Its own glow (a hot planet, a lava world), or for a star how fiery it is. */
        float glow;
        boolean air, rings, tail, flare, egg, stream;
        /** Flattening of a fast-spinning body (polar radius / equatorial). */
        float oblate = 1F;
        float ar = 0.6F, ag = 0.75F, ab = 1F;
        /** A glow round a scorching world. */
        float hr, hg, hb, halo;
        /** Its ground as blocks: low, middle and high ground, and whether its blue parts are seas. */
        int lowT = BASALT, midT = REGOLITH, highT = REGOLITH;
        boolean ocean;

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

        Body flare() {
            flare = true;
            return this;
        }

        Body oblate(float v) {
            oblate = v;
            return this;
        }

        Body egg() {
            egg = true;
            return this;
        }

        Body stream() {
            stream = true;
            return this;
        }

        Body halo(float r, float g, float b, float a) {
            hr = r;
            hg = g;
            hb = b;
            halo = a;
            return this;
        }

        Body ground(int low, int mid, int high) {
            lowT = low;
            midT = mid;
            highT = high;
            return this;
        }

        Body ocean() {
            ocean = true;
            return this;
        }

        /** This body as a planet of blocks with a radius of `blocks` (for the ray-traced view). */
        public SpaceShaders.Planet voxel(int blocks) {
            SpaceShaders.Planet p = new SpaceShaders.Planet();
            p.map = map;
            p.radius = blocks;
            boolean gas = kind == GAS;
            p.gas = gas;
            p.relief = gas ? (float) Math.max(1, Math.min(3, Math.round(blocks * 0.04))) : (float) Math.max(2, Math.min(8, Math.round(blocks * 0.08)));
            p.sea = ocean ? Math.max(1F, Math.round(p.relief * 0.35F)) : -1F;
            p.tiles = gas ? new float[]{CLOUD, CLOUD, STORM, WATER} : new float[]{lowT, midT, highT, WATER};
            p.glow = glow;
            p.ambient = 0.012F;
            if (air) {
                p.air = gas ? 0.9F : 0.75F;
                p.airR = ar;
                p.airG = ag;
                p.airB = ab;
            }
            return p;
        }

        public String id() {
            return id;
        }

        public boolean isPlanet() {
            return kind == ROCKY || kind == GAS;
        }
    }

    /** A place in the sky: a centre body (planet or star) and what goes round it. */
    static final class Sys {
        final String id;
        /** Fixed direction in the sky frame (stars), or null for a body of the solar system. */
        final double[] dir;
        /** Elongation from the sun at day 0, its swing (inner planets) or days per round (outer ones), and latitude. */
        final double lon0, lonRate, lat;
        final boolean inner;
        final String distance;
        final List<Body> bodies = new ArrayList<>();
        boolean cluster;
        /** A dusty disc or ring: radii, tilt, turn, colour, and how far off centre (eccentric rings). */
        double diskIn, diskOut, diskIncl, diskNode, diskShift;
        float diskR = 1F, diskG = 0.88F, diskB = 0.75F;
        /** A nebula round the star: 1 a lumpy shell of dust, 2 two great lobes. */
        int nebula;

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

        Sys add(Body... list) {
            for (Body b : list) {
                bodies.add(b);
            }
            return this;
        }

        Sys disk(double in, double out, double incl, double node, double shift, float r, float g, float b) {
            diskIn = in;
            diskOut = out;
            diskIncl = Math.toRadians(incl);
            diskNode = Math.toRadians(node);
            diskShift = shift;
            diskR = r;
            diskG = g;
            diskB = b;
            return this;
        }

        boolean hasDisk() {
            return diskOut > 0;
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
    static Sys moon, sun, earth;

    private static double[] d(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    /** A place in the night sky for a star (the same every time), well away from the moon. */
    private static double[] at(String id) {
        long h = id.hashCode() * 0x9E3779B97F4A7C15L;
        double x = (Space3D.h(h, 1) - 0.5) * 1.5, z = (Space3D.h(h, 2) - 0.5) * 1.6;
        double off = Math.sqrt(x * x + z * z);
        if (off < 0.3) {
            double k = off < 1.0E-3 ? 0 : 0.3 / off;
            x = off < 1.0E-3 ? 0.3 : x * k;
            z *= k;
        }
        return d(x, -1, z);
    }

    private static Body star(String id, double r, float[] c, float fire) {
        return new Body(id, STAR, "star_surface", r, 0, 0, 0, 0, 0, c[0], c[1], c[2]).glow(fire);
    }

    private static Body star(String id, double r, float[] c, float fire, double orbit, double period, double incl, double node, double phase) {
        return new Body(id, STAR, "star_surface", r, orbit, period, incl, node, phase, c[0], c[1], c[2]).glow(fire);
    }

    private static Body planet(String id, int kind, String map, double r, double orbit, double period, double incl, double node, double phase) {
        return new Body(id, kind, map, r, orbit, period, incl, node, phase, 1F, 1F, 1F);
    }

    private static Sys sys(String id, String distance, Body... bodies) {
        Sys s = new Sys(id, at(id), distance);
        s.add(bodies);
        SYSTEMS.add(s);
        return s;
    }

    private static void target(String id, int group, Sys s, String body) {
        for (Body b : s.bodies) {
            if (b.id.equals(body)) {
                TARGETS.add(new Target(id, group, s, b));
                return;
            }
        }
        throw new IllegalStateException("no body " + body + " in " + s.id);
    }

    private static void solar(String id, Sys s, Body... bodies) {
        s.add(bodies);
        SYSTEMS.add(s);
        TARGETS.add(new Target(id, PLANETS, s, bodies[0]));
    }

    static {
        // ------------------------------------------------------------------------- the solar system
        sun = new Sys("sun", d(0, 1, 0), "1 AU");
        solar("sun", sun, new Body("sun", STAR, "star_surface", Math.atan(0.15), 0, 0, 0, 0, 0, 1F, 0.86F, 0.6F).glow(1F));
        solar("mercury", new Sys("mercury", 0.4, 22, 0.03, true, "0.6-1.4 AU"), planet("mercury", ROCKY, "mercury", 0.35e-4, 0, 0, 0, 0, 0).ground(BASALT, REGOLITH, REGOLITH));
        solar("venus", new Sys("venus", 1.7, 44, -0.02, true, "0.3-1.7 AU"), planet("venus", ROCKY, "venus", 1.2e-4, 0, 0, 0, 0, 0)
                .air(1F, 0.92F, 0.7F).ground(BASALT, SULFUR, ASH));
        earth = new Sys("earth", d(0, 1, 0), "0 km");
        solar("earth", earth, planet("earth", ROCKY, "earth", 1e-4, 0, 0, 0, 0, 0).air(0.45F, 0.65F, 1F).ground(SAND, MOSS, BASALT).ocean());
        moon = new Sys("moon", d(0, -1, 0), "384 400 km");
        solar("moon", moon, planet("moon", ROCKY, "moon", Math.atan(0.1), 0, 0, 0, 0, 0).ground(BASALT, REGOLITH, REGOLITH));
        solar("mars", new Sys("mars", 200, 40, 0.05, false, "0.4-2.5 AU"), planet("mars", ROCKY, "mars", 0.6e-4, 0, 0, 0, 0, 0).air(1F, 0.7F, 0.5F)
                .ground(BASALT, IRONSTONE, SAND));
        solar("ceres", new Sys("ceres", 250, 60, 0.09, false, "1.6-3.0 AU"), planet("ceres", ROCKY, "ceres", 0.12e-4, 0, 0, 0, 0, 0).ground(BASALT, REGOLITH, ICE));
        solar("jupiter", new Sys("jupiter", 165, 80, -0.04, false, "4.2-6.2 AU"),
                planet("jupiter", GAS, "jupiter", 2.0e-4, 0, 0, 0, 0, 0).air(0.9F, 0.85F, 0.75F),
                // The four moons Galileo saw, in a line either side.
                new Body("io", ROCKY, "venus", 0.11e-4, 5.9 * 2.0e-4, 1200, 88, 0, 0.1, 1F, 0.95F, 0.6F).ground(SULFUR, SULFUR, ASH),
                new Body("europa", ROCKY, "lhs1140b", 0.09e-4, 9.4 * 2.0e-4, 2400, 88, 0, 0.6, 1F, 0.98F, 0.92F).ground(ICE, ICE, FROST),
                new Body("ganymede", ROCKY, "mercury", 0.15e-4, 15 * 2.0e-4, 4800, 88, 0, 0.3, 0.9F, 0.88F, 0.82F).ground(BASALT, REGOLITH, ICE),
                new Body("callisto", ROCKY, "moon", 0.14e-4, 26 * 2.0e-4, 11000, 88, 0, 0.8, 0.7F, 0.66F, 0.6F).ground(BASALT, REGOLITH, ICE));
        solar("saturn", new Sys("saturn", 140, 75, 0.06, false, "8.0-11.1 AU"), planet("saturn", GAS, "saturn", 0.9e-4, 0, 0, 0, 0, 0).rings(),
                new Body("titan", ROCKY, "venus", 0.07e-4, 20 * 0.9e-4, 8000, 80, 20, 0.4, 1F, 0.8F, 0.5F).ground(ASH, SAND, ICE));
        solar("uranus", new Sys("uranus", 222, 74, -0.07, false, "18-20 AU"), planet("uranus", GAS, "uranus", 0.25e-4, 0, 0, 0, 0, 0)
                .air(0.6F, 0.9F, 0.95F));
        solar("neptune", new Sys("neptune", 118, 73, 0.08, false, "29-31 AU"), planet("neptune", GAS, "neptune", 0.18e-4, 0, 0, 0, 0, 0)
                .air(0.4F, 0.55F, 1F));
        solar("pluto", new Sys("pluto", 200, 72, 0.2, false, "30-50 AU"), planet("pluto", ROCKY, "pluto", 0.09e-4, 0, 0, 0, 0, 0)
                        .ground(IRONSTONE, ICE, FROST),
                new Body("charon", ROCKY, "charon", 0.045e-4, 0.3e-4, 1500, 60, 30, 0.2, 1F, 1F, 1F).ground(BASALT, REGOLITH, ICE));
        solar("haumea", new Sys("haumea", 150, 72, 0.32, false, "35-51 AU"),
                planet("haumea", ROCKY, "haumea", 0.08e-4, 0, 0, 0, 0, 0).oblate(0.52F).rings().ground(ICE, ICE, CRYSTAL),
                new Body("hiiaka", ROCKY, "eris", 0.012e-4, 0.55e-4, 5000, 70, 0, 0.3, 1F, 1F, 1F),
                new Body("namaka", ROCKY, "eris", 0.008e-4, 0.3e-4, 2000, 60, 40, 0.7, 1F, 1F, 1F));
        solar("makemake", new Sys("makemake", 120, 71, 0.38, false, "38-53 AU"), planet("makemake", ROCKY, "makemake", 0.07e-4, 0, 0, 0, 0, 0).ground(IRONSTONE, ICE, FROST));
        solar("eris", new Sys("eris", 230, 70, 0.28, false, "38-98 AU"), planet("eris", ROCKY, "eris", 0.075e-4, 0, 0, 0, 0, 0).ground(ICE, FROST, FROST),
                new Body("dysnomia", ROCKY, "charon", 0.02e-4, 0.32e-4, 2600, 70, 60, 0.5, 0.9F, 0.9F, 0.9F));

        // ------------------------------------------------------------------------- stars
        Sys alphaCen = sys("alpha_centauri", "4.37 ly", star("alpha_centauri_a", 6e-6, G_T, 1F, 1.2e-5, 40000, 60, 20, 0),
                star("alpha_centauri_b", 5e-6, K_T, 1F, 1.5e-5, 40000, 60, 20, 0.5));
        target("alpha_centauri_a", STARS, alphaCen, "alpha_centauri_a");
        target("alpha_centauri_b", STARS, alphaCen, "alpha_centauri_b");
        target("rigel_kentaurus", STARS, alphaCen, "alpha_centauri_a");
        // Proxima is about two degrees from Alpha Centauri, as in the real sky.
        double[] ac = alphaCen.dir;
        Sys proxima = new Sys("proxima", d(ac[0] + 0.03, ac[1], ac[2] + 0.02), "4.24 ly");
        proxima.add(star("proxima_centauri", 3.5e-6, M_T, 1.2F).flare(),
                planet("proxima_b", ROCKY, "proxima_b", 1.15e-6, 1.4e-5, 700, 80, 10, 0.3).air(0.6F, 0.75F, 1F)
                        .ground(SAND, IRONSTONE, ICE).ocean());
        SYSTEMS.add(proxima);
        target("proxima_centauri", STARS, proxima, "proxima_centauri");
        Sys sirius = sys("sirius", "8.6 ly", star("sirius", 4.5e-6, A_T, 0.8F),
                new Body("sirius_b", DWARF, "star_surface", 0.7e-6, 3.5e-5, 20000, 50, 30, 0.2, WD[0], WD[1], WD[2]));
        target("sirius", STARS, sirius, "sirius");
        target("betelgeuse", STARS, sys("betelgeuse", "550 ly", star("betelgeuse", 2.4e-5, M_T, 1.7F)), "betelgeuse");
        target("rigel", STARS, sys("rigel", "860 ly", star("rigel", 8e-6, O_B, 1F)), "rigel");
        Sys vega = sys("vega", "25 ly", star("vega", 5e-6, A_T, 0.8F).oblate(0.85F));
        vega.disk(2.5e-5, 6e-5, 10, 0, 0, 0.9F, 0.85F, 0.8F);
        target("vega", STARS, vega, "vega");
        target("polaris", STARS, sys("polaris", "430 ly", star("polaris", 6.5e-6, F_T, 0.9F),
                star("polaris_b", 1.4e-6, F_T, 0.4F, 3e-5, 30000, 40, 60, 0.3)), "polaris");
        target("altair", STARS, sys("altair", "16.7 ly", star("altair", 5e-6, A_T, 0.8F).oblate(0.78F)), "altair");
        target("deneb", STARS, sys("deneb", "2600 ly", star("deneb", 1.1e-5, A_T, 1.2F)), "deneb");
        target("antares", STARS, sys("antares", "550 ly", star("antares", 2.1e-5, M_T, 1.6F),
                star("antares_b", 1.6e-6, O_B, 0.5F, 4.5e-5, 30000, 40, 60, 0.7)), "antares");
        target("aldebaran", STARS, sys("aldebaran", "65 ly", star("aldebaran", 1.3e-5, K_T, 1.2F)), "aldebaran");
        target("arcturus", STARS, sys("arcturus", "37 ly", star("arcturus", 1.2e-5, K_T, 1.2F)), "arcturus");
        // Spica: two hot blue stars so close they are pulled into egg shapes.
        target("spica", STARS, sys("spica", "250 ly", star("spica", 5e-6, O_B, 1F, 0.8e-5, 700, 65, 10, 0).oblate(0.88F),
                star("spica_b", 3.5e-6, O_B, 0.8F, 1.3e-5, 700, 65, 10, 0.5).oblate(0.9F)), "spica");
        target("canopus", STARS, sys("canopus", "310 ly", star("canopus", 1.2e-5, F_T, 1.2F)), "canopus");
        target("capella", STARS, sys("capella", "43 ly", star("capella", 7e-6, G_T, 1.1F, 1.5e-5, 6000, 50, 40, 0),
                star("capella_b", 6.5e-6, G_T, 1.1F, 1.7e-5, 6000, 50, 40, 0.5)), "capella");
        target("regulus", STARS, sys("regulus", "79 ly", star("regulus", 5.5e-6, O_B, 0.9F).oblate(0.75F)), "regulus");
        Sys fomalhaut = sys("fomalhaut", "25 ly", star("fomalhaut", 5e-6, A_T, 0.8F));
        fomalhaut.disk(5.6e-5, 6.6e-5, 66, 20, 0.8e-5, 0.95F, 0.85F, 0.7F);
        target("fomalhaut", STARS, fomalhaut, "fomalhaut");
        target("barnards_star", STARS, sys("barnards_star", "6 ly", star("barnards_star", 3.2e-6, M_T, 1F).flare()), "barnards_star");
        target("wolf_359", STARS, sys("wolf_359", "7.9 ly", star("wolf_359", 2.8e-6, M_T, 1.2F).flare()), "wolf_359");
        Sys tauCeti = sys("tau_ceti", "11.9 ly", star("tau_ceti", 4.5e-6, G_T, 0.9F));
        tauCeti.disk(2e-5, 7e-5, 35, 50, 0, 0.9F, 0.85F, 0.75F);
        target("tau_ceti", STARS, tauCeti, "tau_ceti");
        Sys epsEri = sys("epsilon_eridani", "10.5 ly", star("epsilon_eridani", 4.5e-6, K_T, 1F));
        epsEri.disk(4.5e-5, 7.5e-5, 30, 0, 0, 0.95F, 0.82F, 0.65F);
        target("epsilon_eridani", STARS, epsEri, "epsilon_eridani");
        target("uy_scuti", STARS, sys("uy_scuti", "5900 ly", star("uy_scuti", 3.6e-5, M_T, 2F)), "uy_scuti");
        Sys vy = sys("vy_canis_majoris", "3800 ly", star("vy_canis_majoris", 3.2e-5, M_T, 2F));
        vy.nebula = 1;
        target("vy_canis_majoris", STARS, vy, "vy_canis_majoris");
        target("stephenson_2_18", STARS, sys("stephenson_2_18", "19 000 ly", star("stephenson_2_18", 3.9e-5, M_T, 2.2F)), "stephenson_2_18");
        Sys eta = sys("eta_carinae", "7500 ly", star("eta_carinae", 6.5e-6, O_B, 1.5F));
        eta.nebula = 2;
        target("eta_carinae", STARS, eta, "eta_carinae");

        // TON 618: one of the most massive black holes known (66 billion Suns), feeding a quasar.
        Sys ton = sys("ton618", "10.8 billion ly", new Body("ton618", BLACKHOLE, null, 1.2e-6, 0, 0, 0, 0, 0, 0.75F, 0.85F, 1F));
        target("ton618", STARS, ton, "ton618");

        // ------------------------------------------------------------------------- exoplanets
        target("proxima_b", EXOPLANETS, proxima, "proxima_b");
        exo("peg51b", "51 Pegasi", "50 ly", star("51_pegasi", 5e-6, G_T, 1F),
                planet("peg51b", GAS, "peg51b", 2.4e-6, 1.3e-5, 700, 80, 15, 0.2).glow(0.12F).halo(1F, 0.6F, 0.4F, 0.2F).air(1F, 0.8F, 0.6F));
        exo("cnc55e", "55 Cancri", "41 ly", star("55_cancri", 4.5e-6, K_T, 1F),
                planet("cnc55e", ROCKY, "cnc55e", 1.2e-6, 1.0e-5, 300, 83, 0, 0.6).glow(0.45F).halo(1F, 0.45F, 0.15F, 0.35F)
                        .ground(MAGMA, OBSIDIAN, CRYSTAL));
        exo("hd189733b", "HD 189733", "64 ly", star("hd189733", 4.5e-6, K_T, 1F),
                planet("hd189733b", GAS, "hd189733b", 2.6e-6, 1.4e-5, 800, 86, 20, 0.1).air(0.4F, 0.6F, 1F));
        exo("hd209458b", "HD 209458", "157 ly", star("hd209458", 5e-6, G_T, 1F),
                planet("hd209458b", GAS, "hd209458b", 2.8e-6, 1.5e-5, 900, 86, 10, 0.4).air(0.6F, 0.75F, 1F).tail());
        exo("wasp12b", "WASP-12", "1400 ly", star("wasp12", 6e-6, F_T, 1F),
                planet("wasp12b", GAS, "wasp12b", 3.2e-6, 1.1e-5, 420, 83, 0, 0.3).glow(0.5F).egg().stream().halo(1F, 0.4F, 0.15F, 0.3F));
        exo("wasp76b", "WASP-76", "640 ly", star("wasp76", 6e-6, F_T, 1F),
                planet("wasp76b", GAS, "wasp76b", 3.0e-6, 1.3e-5, 600, 85, 30, 0.7).glow(0.35F).halo(1F, 0.55F, 0.2F, 0.25F));
        exo("wasp17b", "WASP-17", "1300 ly", star("wasp17", 5.5e-6, F_T, 1F),
                planet("wasp17b", GAS, "wasp17b", 3.9e-6, 2.0e-5, -1500, 80, 40, 0.1).air(0.85F, 0.85F, 0.9F));
        exo("kelt9b", "KELT-9", "670 ly", star("kelt9", 7e-6, O_B, 1.2F),
                planet("kelt9b", GAS, "kelt9b", 3.2e-6, 1.2e-5, 450, 80, 70, 0.5).glow(0.6F).halo(1F, 0.85F, 0.7F, 0.4F));
        Sys k16 = new Sys("kepler16", at("kepler16"), "245 ly");
        k16.add(star("kepler16a", 4.5e-6, K_T, 1F, 0.6e-5, 900, 89, 0, 0), star("kepler16b_star", 2.5e-6, M_T, 1F, 1.6e-5, 900, 89, 0, 0.5),
                planet("kepler16b", GAS, "kepler16b", 2.2e-6, 4.5e-5, 5000, 89.5, 0, 0.3).air(0.85F, 0.8F, 0.7F));
        SYSTEMS.add(k16);
        target("kepler16b", EXOPLANETS, k16, "kepler16b");
        exo("kepler22b", "Kepler-22", "640 ly", star("kepler22", 5e-6, G_T, 1F),
                planet("kepler22b", ROCKY, "kepler22b", 1.7e-6, 5e-5, 5000, 80, 20, 0.6).air(0.5F, 0.75F, 1F).ground(SAND, MOSS, BASALT).ocean());
        exo("kepler186f", "Kepler-186", "580 ly", star("kepler186", 4e-6, M_T, 1F),
                planet("kepler186f", ROCKY, "kepler186f", 1.3e-6, 5.5e-5, 4500, 78, 0, 0.2).air(0.7F, 0.7F, 0.9F)
                        .ground(SAND, MOSS, IRONSTONE).ocean());
        exo("kepler452b", "Kepler-452", "1800 ly", star("kepler452", 5.2e-6, G_T, 1F),
                planet("kepler452b", ROCKY, "kepler452b", 1.6e-6, 6e-5, 7000, 75, 30, 0.45).air(0.55F, 0.7F, 1F)
                        .ground(SAND, MOSS, BASALT).ocean());
        exo("kepler10b", "Kepler-10", "610 ly", star("kepler10", 5e-6, G_T, 1F),
                planet("kepler10b", ROCKY, "kepler10b", 1.2e-6, 1.0e-5, 260, 84, 10, 0.1).glow(0.4F).halo(1F, 0.45F, 0.12F, 0.3F)
                        .ground(MAGMA, HOTROCK, OBSIDIAN));
        exo("kepler78b", "Kepler-78", "400 ly", star("kepler78", 4.6e-6, K_T, 1F),
                planet("kepler78b", ROCKY, "kepler78b", 1.0e-6, 0.9e-5, 200, 80, 50, 0.8).glow(0.45F).halo(1F, 0.6F, 0.2F, 0.3F)
                        .ground(MAGMA, HOTROCK, OBSIDIAN));
        exo("kepler1649c", "Kepler-1649", "300 ly", star("kepler1649", 3.8e-6, M_T, 1F),
                planet("kepler1649c", ROCKY, "kepler1649c", 1.2e-6, 3.5e-5, 2600, 80, 20, 0.35).air(0.7F, 0.75F, 0.95F)
                        .ground(SAND, IRONSTONE, ICE).ocean());
        exo("kepler442b", "Kepler-442", "1200 ly", star("kepler442", 4.6e-6, K_T, 1F),
                planet("kepler442b", ROCKY, "kepler442b", 1.4e-6, 5e-5, 4600, 76, 40, 0.55).air(0.55F, 0.72F, 1F)
                        .ground(SAND, MOSS, BASALT).ocean());
        exo("kepler62f", "Kepler-62", "980 ly", star("kepler62", 4.6e-6, K_T, 1F),
                planet("kepler62f", ROCKY, "kepler62f", 1.4e-6, 6.5e-5, 7000, 78, 10, 0.15).air(0.6F, 0.8F, 1F)
                        .ground(SAND, MOSS, ICE).ocean());
        // TRAPPIST-1: seven Earth-sized worlds round a tiny red star, seen almost edge-on, in a chain of resonances.
        Sys trappist = new Sys("trappist1", at("trappist1"), "40 ly");
        trappist.add(star("trappist1", 3.0e-6, new float[]{1F, 0.35F, 0.2F}, 1.1F).flare());
        String[] tp = {"b", "c", "d", "e", "f", "g", "h"};
        double[] ta = {1.1, 1.5, 2.1, 2.8, 3.7, 4.5, 5.9}, tr = {1.12, 1.1, 0.79, 0.92, 1.05, 1.13, 0.76}, tper = {1.51, 2.42, 4.05, 6.1, 9.21, 12.35, 18.77};
        for (int i = 0; i < 7; i++) {
            Body b = planet("trappist1" + tp[i], ROCKY, "trappist1" + tp[i], tr[i] * 1.1e-6, ta[i] * 1e-5, tper[i] * 300, 89.4, 5, i * 0.137);
            if (i == 0) {
                b.glow(0.25F).halo(1F, 0.45F, 0.2F, 0.2F).ground(HOTROCK, BASALT, OBSIDIAN);
            } else {
                b.air(0.6F, 0.75F, 1F);
                // The inner ones rocky and dry, the middle ones with seas, the outer ones frozen.
                if (i == 1) {
                    b.ground(BASALT, REGOLITH, ASH);
                } else if (i <= 3) {
                    b.ground(SAND, MOSS, ICE).ocean();
                } else {
                    b.ground(ICE, FROST, FROST);
                }
            }
            trappist.add(b);
        }
        SYSTEMS.add(trappist);
        for (String p : tp) {
            target("trappist1" + p, EXOPLANETS, trappist, "trappist1" + p);
        }
        exo("gj1214b", "GJ 1214", "48 ly", star("gj1214", 3.8e-6, M_T, 1F),
                planet("gj1214b", GAS, "gj1214b", 2.2e-6, 1.4e-5, 500, 85, 10, 0.4).air(0.85F, 0.9F, 0.95F));
        exo("gj436b", "GJ 436", "32 ly", star("gj436", 5e-6, M_T, 1F),
                planet("gj436b", GAS, "gj436b", 2.2e-6, 1.7e-5, 900, 86, 10, 0.1).air(0.6F, 0.8F, 1F).tail());
        exo("lhs1140b", "LHS 1140", "49 ly", star("lhs1140", 5e-6, M_T, 1F),
                planet("lhs1140b", ROCKY, "lhs1140b", 1.7e-6, 4.5e-5, 3000, 78, -20, 0.3).air(0.7F, 0.85F, 1F)
                        .ground(ICE, ICE, FROST).ocean());
        exo("k2_18b", "K2-18", "124 ly", star("k2_18", 5.5e-6, M_T, 1F),
                planet("k2_18b", GAS, "k2_18b", 2.1e-6, 4e-5, 2800, 70, 35, 0.55).air(0.4F, 0.8F, 1F));
        Sys toi700 = new Sys("toi700", at("toi700"), "101 ly");
        toi700.add(star("toi700", 5e-6, M_T, 1F),
                planet("toi700e", ROCKY, "toi700e", 1.35e-6, 4.0e-5, 2700, 74, 0, 0.2).air(0.7F, 0.8F, 1F).ground(SAND, MOSS, ICE).ocean(),
                planet("toi700d", ROCKY, "toi700d", 1.45e-6, 5.4e-5, 3700, 74, 0, 0.65).air(0.5F, 0.7F, 1F).ground(SAND, MOSS, BASALT).ocean());
        SYSTEMS.add(toi700);
        target("toi700d", EXOPLANETS, toi700, "toi700d");
        target("toi700e", EXOPLANETS, toi700, "toi700e");
        Sys betapic = new Sys("betapic", at("betapic"), "63 ly");
        betapic.add(star("betapic", 6e-6, A_T, 1F), planet("betapic_b", GAS, "betapic_b", 2.6e-6, 5.5e-5, 6000, 88.5, 30, 0.35).air(0.9F, 0.75F, 0.6F));
        betapic.disk(1.2e-5, 9.2e-5, 88.5, 30, 0, 1F, 0.88F, 0.75F);
        SYSTEMS.add(betapic);
        target("betapic_b", EXOPLANETS, betapic, "betapic_b");
        Sys hr8799 = new Sys("hr8799", at("hr8799"), "133 ly");
        hr8799.add(star("hr8799", 6e-6, A_T, 1F),
                planet("hr8799e", GAS, "hr8799e", 2.4e-6, 2.6e-5, 2600, 25, 0, 0.0).glow(0.22F).air(1F, 0.6F, 0.5F),
                planet("hr8799d", GAS, "hr8799d", 2.5e-6, 4.0e-5, 5000, 25, 0, 0.3).glow(0.2F).air(1F, 0.7F, 0.4F),
                planet("hr8799c", GAS, "hr8799c", 2.5e-6, 5.6e-5, 8000, 25, 0, 0.55).glow(0.18F).air(1F, 0.6F, 0.7F),
                planet("hr8799b", GAS, "hr8799b", 2.3e-6, 7.5e-5, 13000, 25, 0, 0.8).glow(0.2F).air(1F, 0.5F, 0.4F));
        SYSTEMS.add(hr8799);
        for (String p : new String[]{"hr8799b", "hr8799c", "hr8799d", "hr8799e"}) {
            target(p, EXOPLANETS, hr8799, p);
        }
        Sys psr = new Sys("psr1620", at("psr1620"), "12 400 ly (M4)");
        psr.add(new Body("psr1620", PULSAR, "star_surface", 0.7e-6, 0.4e-5, 1600, 50, 0, 0.0, 0.75F, 0.85F, 1F).glow(1F),
                new Body("psr1620_wd", DWARF, "star_surface", 1.2e-6, 0.9e-5, 1600, 50, 0, 0.5, 0.95F, 0.95F, 1F),
                planet("psr_b1620_26b", GAS, "psr_b", 2.4e-6, 6.2e-5, 12000, 55, 40, 0.25).air(0.8F, 0.85F, 0.95F));
        psr.cluster = true;
        SYSTEMS.add(psr);
        target("psr_b1620_26b", EXOPLANETS, psr, "psr_b1620_26b");
    }

    private static void exo(String planet, String host, String distance, Body star, Body p) {
        Sys s = new Sys(host, at(host), distance);
        s.add(star, p);
        SYSTEMS.add(s);
        target(planet, EXOPLANETS, s, planet);
    }

    /** The Moon itself (for its blocks). */
    static Body moonBody() {
        return moon.bodies.get(0);
    }

    public static Body body(Target t) {
        return t.body;
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
