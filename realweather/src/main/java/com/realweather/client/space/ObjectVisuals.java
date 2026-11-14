package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.resources.ResourceLocation;

import static com.realweather.client.space.Space3D.ADD;
import static com.realweather.client.space.Space3D.NORMAL;

/**
 * The hundred kinds of astronomical object, each drawn as what it is, in 3D and moving: stars of every size and
 * stage (pulsing, flaring, shedding dust, in pairs and threes), the compact remains (neutron stars, pulsars,
 * magnetars, black holes alone or feeding), worlds of other stars (dark, molten, iron, puffed up, round two suns),
 * clusters and streams of stars, and the large things (clouds, disks, nebulae, groups and clusters of galaxies,
 * voids, quasars, lenses, the dark matter we can only infer). Built from stars (spheres of fire), gas and dust
 * (puffs that glow or block the light), particles on orbits, jets, lensed black holes and galaxies.
 * <p>
 * c: centre (view); R: its size (view units); rot: its frame to view (Y its axis); seconds; px: view units per
 * pixel there; vis: how strongly.
 */
final class ObjectVisuals {
    private static final ResourceLocation STAR_MAP = Space3D.STAR_MAP_PUBLIC;
    private static final ResourceLocation GLOW = Space3D.GLOW, SPARK = Space3D.STAR;
    private static final ResourceLocation JUPITER = map("jupiter"), NEPTUNE = map("neptune"), URANUS = map("uranus"), MARS = map("mars"),
            MOON = map("moon"), MERCURY = map("mercury"), VENUS = map("venus"), LAVA = map("cnc55e"), OCEAN = map("kepler22b"), ROCK = map("kepler442b"),
            EARTH = map("earth"), PLUTO = map("pluto"), ERIS = map("eris");

    private ObjectVisuals() {}

    private static ResourceLocation map(String n) {
        return RealWeather.id("textures/environment/space3d/" + n + ".png");
    }

    private static double h(long s, long k) {
        return Space3D.h(s, k);
    }

    private static double gauss(long s, long k) {
        double u1 = Math.max(1.0E-6, h(s, k)), u2 = h(s, k + 7777);
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(Math.PI * 2 * u2);
    }

    private static double[] at(double[] c, double[] rot, double x, double y, double z) {
        double[] q = new double[3];
        Space3D.apply(rot, x, y, z, q);
        return new double[]{c[0] + q[0], c[1] + q[1], c[2] + q[2]};
    }

    private static double[] sub(double[] rot, double ax, double ay, double az, double spin) {
        double[] out = new double[9];
        Space3D.mul(rot, Space3D.orient(ax, ay, az, spin), out);
        return out;
    }

    private static double[] dirTo(double[] from, double[] to) {
        double x = to[0] - from[0], y = to[1] - from[1], z = to[2] - from[2], l = Math.sqrt(x * x + y * y + z * z) + 1.0E-12;
        return new double[]{x / l, y / l, z / l};
    }

    private static double[] vec(double[] rot, double x, double y, double z) {
        double[] q = new double[3];
        Space3D.apply(rot, x, y, z, q);
        double l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]) + 1.0E-12;
        return new double[]{q[0] / l, q[1] / l, q[2] / l};
    }

    private static float wave(double t) {
        return (float) (0.5 + 0.5 * Math.sin(t));
    }

    // ---------------------------------------------------------------- building blocks

    /** A point of light (too small to show a disc). */
    private static void dot(double[] c, double size, float cr, float cg, float cb, float a) {
        Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], size, 0F, cr, cg, cb, Math.min(1F, a * 0.8F));
        Space3D.billboard(SPARK, ADD, c[0], c[1], c[2], size * 1.7, 0F, cr, cg, cb, Math.min(1F, a * 0.6F));
    }

    /** A star: a ball of fire with its glow (and, close up, its prominences and loops). */
    private static void star(double[] c, double[] rot, double r, float cr, float cg, float cb, float bright, double seconds, double px, float vis,
                             long seed, boolean activity) {
        if (r / px < 1.5) {
            dot(c, Math.max(px * 3, r * 4), cr, cg, cb, vis * Math.min(1.5F, bright));
            return;
        }
        double[] sr = sub(rot, 0, 1, 0, seconds * 0.02);
        float k = 1.2F * bright;
        Space3D.sphere(STAR_MAP, c[0], c[1], c[2], r, sr, 0, 0, 1, 0F, cr * k, cg * k, cb * k, 0F, vis, true, (float) (seconds * 0.003 % 1.0),
                (int) Math.max(16, Math.min(64, r / px / 3)));
        Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], r * 3.2, 0F, cr, cg, cb, 0.45F * vis * Math.min(1.5F, bright));
        if (activity && r / px > 25) {
            SolarActivity.draw(c[0], c[1], c[2], r, sr, cr, cg, cb, seed, seconds, px, vis);
        }
    }

    /** A world lit from `light` (view, unit), turning, with its air. */
    private static void world(double[] c, double[] rot, double r, ResourceLocation tex, float cr, float cg, float cb, float glow, double[] light,
                              double seconds, double px, float vis, float ar, float ag, float ab, float air) {
        if (r / px < 1.2) {
            dot(c, px * 2, cr, cg, cb, vis * 0.6F);
            return;
        }
        double[] sr = sub(rot, 0, 1, 0, seconds * 0.04);
        Space3D.sphere(tex, c[0], c[1], c[2], r, sr, light[0], light[1], light[2], 0.012F, cr, cg, cb, glow, vis, false, 0F,
                (int) Math.max(16, Math.min(64, r / px / 3)));
        if (air > 0) {
            Space3D.atmosphere(c[0], c[1], c[2], r, light[0], light[1], light[2], ar, ag, ab, air * vis);
        }
    }

    /** Particles going round on (Keplerian) orbits from `in` to `out`: a disc, a ring, a belt. */
    private static void disk(double[] c, double[] rot, double in, double out, int n, float r0, float g0, float b0, float r1, float g1, float b1, float a,
                             double seconds, double speed, double thick, long seed, double px, int mode, double size) {
        for (int i = 0; i < n; i++) {
            double u = h(seed, i * 3L), rr = in + (out - in) * Math.pow(u, 0.8);
            double ang = h(seed, i * 3L + 1) * Math.PI * 2 + seconds * speed * Math.pow(in / rr, 1.5);
            double y = gauss(seed, i * 3L + 2) * thick * rr;
            double[] q = at(c, rot, Math.cos(ang) * rr, y, Math.sin(ang) * rr);
            float t = (float) ((rr - in) / Math.max(1.0E-9, out - in));
            float aa = mode == NORMAL ? a : a * (1F - 0.5F * t);
            Space3D.billboard(GLOW, mode, q[0], q[1], q[2], Math.max(px * 1.2, size * (0.6 + 0.8 * h(seed, i * 3L + 7))), i, r0 + (r1 - r0) * t,
                    g0 + (g1 - g0) * t, b0 + (b1 - b0) * t, aa);
        }
    }

    /** A cloud of puffs of gas or dust (glowing with ADD, blocking the light with NORMAL), slowly turning. */
    private static void puffs(double[] c, double[] rot, double sx, double sy, double sz, int n, float cr, float cg, float cb, float a, long seed, int mode,
                              double px, double seconds, double size) {
        double turn = seconds * 0.015, ct = Math.cos(turn), st = Math.sin(turn);
        for (int i = 0; i < n; i++) {
            double x = gauss(seed, i * 4L) * sx, y = gauss(seed, i * 4L + 1) * sy, z = gauss(seed, i * 4L + 2) * sz;
            double[] q = at(c, rot, x * ct - z * st, y, x * st + z * ct);
            double s = Math.max(px * 2, size * (0.6 + 0.8 * h(seed, i * 4L + 3)));
            float v = (float) (0.7 + 0.3 * h(seed, i * 4L + 5));
            Space3D.billboard(GLOW, mode, q[0], q[1], q[2], s, (float) (i * 1.7), cr * v, cg * v, cb * v, a);
        }
    }

    /** Puffs over a sphere of radius `rad` (a shell of gas or dust), roughened. */
    private static void shell(double[] c, double[] rot, double rad, double rough, int n, float cr, float cg, float cb, float a, long seed, double px,
                              double size, int mode) {
        for (int i = 0; i < n; i++) {
            double z = h(seed, i * 4L) * 2 - 1, ph = h(seed, i * 4L + 1) * Math.PI * 2, s = Math.sqrt(1 - z * z);
            double k = rad * (1 + (h(seed, i * 4L + 2) - 0.5) * rough * 2);
            double[] q = at(c, rot, s * Math.cos(ph) * k, z * k, s * Math.sin(ph) * k);
            Space3D.billboard(GLOW, mode, q[0], q[1], q[2], Math.max(px * 1.5, size * (0.6 + 0.8 * h(seed, i * 4L + 3))), i, cr, cg, cb, a);
        }
    }

    /**
     * A cluster of stars: n of them, crowded towards a core, flattened on y by `flat`, a share `young` of them hot and
     * blue (the rest yellow and red), turning slowly.
     */
    private static void cluster(double[] c, double[] rot, double R, int n, double core, float young, long seed, double seconds, double px, float vis,
                                double flat, double spin) {
        double lim = Math.atan(R / core);
        for (int i = 0; i < n; i++) {
            double rr = core * Math.tan(h(seed, i * 5L) * lim);
            double z = h(seed, i * 5L + 1) * 2 - 1, s = Math.sqrt(1 - z * z);
            double ph = h(seed, i * 5L + 2) * Math.PI * 2 + seconds * spin * core / (rr + core);
            double[] q = at(c, rot, s * Math.cos(ph) * rr, z * rr * flat, s * Math.sin(ph) * rr);
            boolean blue = h(seed, i * 5L + 3) < young;
            float br = (float) (0.25 + 0.75 * Math.pow(h(seed, i * 5L + 4), 3));
            float cr = blue ? 0.7F : 1F, cg = blue ? 0.8F : (float) (0.6 + 0.3 * h(seed, i * 5L + 6)), cb = blue ? 1F : (float) (0.35 + 0.3 * h(seed, i * 5L + 6));
            double size = Math.max(px * 1.3, R * 0.006 * (0.5 + br * 2));
            Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], size * 2, 0F, cr, cg, cb, 0.55F * br * vis);
            if (br > 0.55F) {
                Space3D.billboard(SPARK, ADD, q[0], q[1], q[2], size * 3.5, 0F, cr, cg, cb, 0.4F * br * vis);
            }
        }
    }

    /** An orbit's ellipse (semi-major axis a, eccentricity e, turned by w), as a faint line. */
    private static void orbit(double[] c, double[] rot, double a, double e, double w, float cr, float cg, float cb, float alpha, double px) {
        double[] prev = null;
        for (int i = 0; i <= 96; i++) {
            double th = Math.PI * 2 * i / 96;
            double r = a * (1 - e * e) / (1 + e * Math.cos(th));
            double[] q = at(c, rot, r * Math.cos(th + w) - a * e * Math.cos(w), 0, r * Math.sin(th + w) - a * e * Math.sin(w));
            if (prev != null) {
                Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, prev[0], prev[1], prev[2], q[0], q[1], q[2], px * 0.8, px * 0.8, cr, cg, cb, alpha, alpha);
            }
            prev = q;
        }
    }

    /** Where a body on that ellipse is, at a time. */
    private static double[] onOrbit(double[] c, double[] rot, double a, double e, double w, double t) {
        // Kepler's equation, a few rounds of Newton's method.
        double m = t % (Math.PI * 2), E = m;
        for (int k = 0; k < 6; k++) {
            E -= (E - e * Math.sin(E) - m) / (1 - e * Math.cos(E));
        }
        double x = a * (Math.cos(E) - e), z = a * Math.sqrt(1 - e * e) * Math.sin(E);
        return at(c, rot, x * Math.cos(w) - z * Math.sin(w), 0, x * Math.sin(w) + z * Math.cos(w));
    }

    /** A stream of gas from p0 curling into a disc round p1. */
    private static void stream(double[] p0, double[] p1, double[] axis, double curl, double w, float cr, float cg, float cb, float a, double seconds,
                               double px) {
        double[] d = {p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]};
        double[] side = {axis[1] * d[2] - axis[2] * d[1], axis[2] * d[0] - axis[0] * d[2], axis[0] * d[1] - axis[1] * d[0]};
        double[] prev = null;
        for (int i = 0; i <= 24; i++) {
            double u = i / 24.0, bend = Math.sin(u * Math.PI) * curl;
            double[] q = {p0[0] + d[0] * u + side[0] * bend, p0[1] + d[1] * u + side[1] * bend, p0[2] + d[2] * u + side[2] * bend};
            if (prev != null) {
                float flick = 0.75F + 0.25F * wave(u * 20 - seconds * 4);
                Space3D.ribbon(GLOW, ADD, prev[0], prev[1], prev[2], q[0], q[1], q[2], Math.max(px, w * (1 - u * 0.6)), Math.max(px, w * (1 - u * 0.6)), cr,
                        cg, cb, a * flick, a * flick);
            }
            prev = q;
        }
    }

    /** Stars far behind (to see something pass in front of them), in a sheet across the line of sight. */
    private static void backdrop(double[] c, double R, int n, long seed, double px, float vis, float brightness, double[] bend, double bendR) {
        double l = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]) + 1.0E-12;
        double[] z = {c[0] / l, c[1] / l, c[2] / l};
        double[] e1 = {-z[2], 0, z[0]};
        double el = Math.sqrt(e1[0] * e1[0] + e1[2] * e1[2]) + 1.0E-12;
        e1[0] /= el;
        e1[2] /= el;
        double[] e2 = {z[1] * e1[2] - z[2] * e1[1], z[2] * e1[0] - z[0] * e1[2], z[0] * e1[1] - z[1] * e1[0]};
        for (int i = 0; i < n; i++) {
            double x = (h(seed, i * 3L) - 0.5) * R * 7, y = (h(seed, i * 3L + 1) - 0.5) * R * 5;
            float b = (float) (0.3 + 0.7 * Math.pow(h(seed, i * 3L + 2), 2)) * brightness;
            // Brightened as a lens passes close in front (microlensing).
            if (bend != null) {
                double dx = x - bend[0], dy = y - bend[1], u = Math.sqrt(dx * dx + dy * dy) / bendR;
                b *= (float) Math.min(6, (u * u + 2) / (u * Math.sqrt(u * u + 4) + 1.0E-6));
            }
            double[] q = {c[0] + z[0] * R * 3 + e1[0] * x + e2[0] * y, c[1] + z[1] * R * 3 + e1[1] * x + e2[1] * y, c[2] + z[2] * R * 3 + e1[2] * x + e2[2] * y};
            float tint = (float) h(seed, i * 3L + 5);
            Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.008 * Math.min(4, b)), 0F, 0.8F + 0.2F * tint, 0.8F, 1F - 0.3F * tint,
                    Math.min(1F, 0.6F * b) * vis);
        }
    }

    /** A black hole: lensed and with its disc when big enough on screen, else a dark dot in its glow. */
    private static void hole(double[] c, double rs, double[] n, double seconds, float vis, float exposure, double px) {
        if (rs / px > 2.5 && c[2] < 0) {
            CloseViews.lensed(c[0], c[1], c[2], rs, n, (float) seconds, vis, exposure, 16, 160F);
        } else {
            Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], Math.max(px * 3, rs * 14), 0F, 1F, 0.75F, 0.45F, 0.35F * vis * exposure);
            Space3D.billboard(GLOW, NORMAL, c[0], c[1], c[2], Math.max(px * 1.2, rs * 3), 0F, 0F, 0F, 0F, 0.9F * vis);
        }
    }

    /** A filament along a curve (a list of points in the frame), glowing or dark. */
    private static void strand(double[] c, double[] rot, double[][] pts, double w, float cr, float cg, float cb, float a, int mode, double px, long seed,
                               int fill) {
        for (int k = 0; k + 1 < pts.length; k++) {
            for (int i = 0; i < fill; i++) {
                double u = (i + h(seed, k * 100L + i)) / fill;
                double x = pts[k][0] + (pts[k + 1][0] - pts[k][0]) * u + gauss(seed, k * 100L + i + 50) * w * 0.4;
                double y = pts[k][1] + (pts[k + 1][1] - pts[k][1]) * u + gauss(seed, k * 100L + i + 60) * w * 0.4;
                double z = pts[k][2] + (pts[k + 1][2] - pts[k][2]) * u + gauss(seed, k * 100L + i + 70) * w * 0.4;
                double[] q = at(c, rot, x, y, z);
                Space3D.billboard(GLOW, mode, q[0], q[1], q[2], Math.max(px * 1.5, w * (0.6 + 0.6 * h(seed, k * 100L + i + 80))), i, cr, cg, cb, a);
            }
        }
    }

    /** Small galaxies (glowing smudges with a core), for groups and clusters. */
    private static void smudge(double[] q, double[] rot, double size, double flat, float cr, float cg, float cb, float a, long seed, double px,
                               double seconds) {
        double[] r2 = sub(rot, h(seed, 1) - 0.5, 1, h(seed, 2) - 0.5, h(seed, 3) * 6 + seconds * 0.01);
        disk(q, r2, size * 0.1, size, 60, 1F, 0.9F, 0.75F, cr, cg, cb, a, seconds, 0.05, 0.08 * flat, seed, px, ADD, size * 0.25);
        Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 2, size * 0.45), 0F, 1F, 0.9F, 0.72F, a * 1.2F);
    }

    // ---------------------------------------------------------------- the hundred

    static void draw(int type, double cx, double cy, double cz, double R, double[] rot, double seconds, double px, long seed, float vis) {
        if (vis <= 0.01F) {
            return;
        }
        double[] c = {cx, cy, cz};
        double[] axis = vec(rot, 0, 1, 0);
        double t = seconds;
        switch (type) {
            // ---- A. Stars and stellar systems
            case 0 -> {
                // A red dwarf: small, cool and red, and flaring now and then.
                float flare = (float) Math.pow(Math.max(0, Math.sin(t * 0.37) * Math.sin(t * 0.13)), 8);
                star(c, rot, R * 0.35, 1F, 0.42F + 0.3F * flare, 0.22F + 0.4F * flare, 1F + 2F * flare, t, px, vis, seed, true);
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * (0.6 + 1.6 * flare), 0F, 1F, 0.75F, 0.6F, 0.6F * flare * vis);
            }
            case 1 -> {
                // A blue giant: hot, bright, its wind streaming away.
                star(c, rot, R * 0.6, 0.7F, 0.8F, 1F, 1.4F, t, px, vis, seed, true);
                windStreaks(c, rot, R * 0.6, R * 2.2, 80, 0.6F, 0.75F, 1F, 0.25F * vis, seed, t, px, 0.3);
            }
            case 2 -> {
                // A red giant: swollen, orange-red, its soft outer layers.
                star(c, rot, R * 0.9, 1F, 0.55F, 0.3F, 0.9F, t * 0.3, px, vis, seed, false);
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 1.6, 0F, 1F, 0.45F, 0.2F, 0.25F * vis);
                shell(c, sub(rot, 0, 1, 0, t * 0.01), R * 1.05, 0.08, 120, 1F, 0.5F, 0.25F, 0.08F * vis, seed, px, R * 0.2, ADD);
            }
            case 3 -> {
                // A blue supergiant: huge and fierce, blowing off gas in clumps.
                star(c, rot, R * 0.75, 0.65F, 0.78F, 1F, 1.6F, t, px, vis, seed, true);
                for (int i = 0; i < 90; i++) {
                    double life = (t * 0.08 + h(seed, i)) % 1.0;
                    double z = h(seed, i + 100) * 2 - 1, ph = h(seed, i + 200) * Math.PI * 2, s = Math.sqrt(1 - z * z), d = R * (0.8 + 1.8 * life);
                    double[] q = at(c, rot, s * Math.cos(ph) * d, z * d, s * Math.sin(ph) * d);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 2, R * 0.12), 0F, 0.6F, 0.75F, 1F, (float) (0.3 * (1 - life)) * vis);
                }
            }
            case 4 -> {
                // A red supergiant: vast, deep red, its surface heaving in a few giant cells, wrapped in its own dust.
                double pulse = 1 + 0.03 * Math.sin(t * 0.2);
                Space3D.scale(1 + 0.04 * Math.sin(t * 0.17), 1 - 0.03 * Math.sin(t * 0.13), 1);
                star(c, rot, R * 0.85 * pulse, 1F, 0.32F, 0.14F, 0.85F, t * 0.2, px, vis, seed, false);
                Space3D.scale(1, 1, 1);
                shell(c, sub(rot, 0.2, 1, 0, t * 0.005), R * 1.7, 0.35, 220, 0.25F, 0.08F, 0.04F, 0.35F * vis, seed, px, R * 0.22, NORMAL);
                shell(c, rot, R * 1.4, 0.3, 120, 1F, 0.35F, 0.15F, 0.07F * vis, seed + 1, px, R * 0.3, ADD);
            }
            case 5 -> {
                // A yellow hypergiant: pulsating, now and then puffing off a whole shell.
                double pulse = 1 + 0.06 * Math.sin(t * 0.3);
                star(c, rot, R * 0.8 * pulse, 1F, 0.85F, 0.5F, 1.2F, t * 0.3, px, vis, seed, false);
                for (int k = 0; k < 3; k++) {
                    double life = (t * 0.03 + k / 3.0) % 1.0;
                    shell(c, sub(rot, 0, 1, 0, k), R * (0.9 + 2.2 * life), 0.05, 140, 1F, 0.8F, 0.45F, (float) (0.12 * (1 - life)) * vis, seed + k, px,
                            R * 0.15, ADD);
                }
            }
            case 6 -> {
                // A Wolf-Rayet star: a small, blazing hot core that has lost its outer layers, in the clumpy nebula it blew.
                star(c, rot, R * 0.18, 0.75F, 0.85F, 1F, 2.2F, t, px, vis, seed, false);
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 0.9, 0F, 0.7F, 0.8F, 1F, 0.35F * vis);
                shell(c, sub(rot, 0, 1, 0, t * 0.004), R * 1.05, 0.25, 320, 1F, 0.35F, 0.4F, 0.16F * vis, seed, px, R * 0.12, ADD);
                windStreaks(c, rot, R * 0.3, R * 1.1, 60, 0.9F, 0.5F, 0.6F, 0.15F * vis, seed + 5, t, px, 0.6);
            }
            case 7 -> {
                // R Coronae Borealis: a star that fades as clouds of soot form round it, then clears again.
                double fade = Math.pow(wave(t * 0.12), 4);
                star(c, rot, R * 0.55, 1F, 0.88F, 0.6F, 1.1F, t, px, vis, seed, false);
                puffs(c, sub(rot, 0, 1, 0, t * 0.05), R * 0.55, R * 0.5, R * 0.55, 60, 0.05F, 0.04F, 0.03F, (float) (0.9 * fade) * vis, seed, NORMAL, px, t,
                        R * 0.35);
                shell(c, rot, R * 1.6, 0.4, 100, 0.08F, 0.06F, 0.05F, 0.25F * vis, seed + 3, px, R * 0.25, NORMAL);
            }
            case 8 -> {
                // A Cepheid: swelling and shrinking in a steady beat, brighter and hotter when big.
                double ph = t * 0.8;
                double size = 1 + 0.12 * Math.sin(ph);
                float hot = wave(ph);
                star(c, rot, R * 0.6 * size, 1F, 0.8F + 0.15F * hot, 0.5F + 0.4F * hot, 0.9F + 0.8F * hot, t, px, vis, seed, false);
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 2.2 * size, 0F, 1F, 0.9F, 0.7F, 0.2F * hot * vis);
            }
            case 9 -> {
                // Mira: a pulsating red giant racing through space, trailing a tail of shed gas light years long.
                double ph = t * 0.15;
                star(c, rot, R * 0.6 * (1 + 0.1 * Math.sin(ph)), 1F, 0.4F, 0.2F, 0.6F + 0.9F * wave(ph), t * 0.3, px, vis, seed, false);
                // The bow shock ahead and the tail behind.
                for (int i = 0; i < 40; i++) {
                    double a = (i / 39.0 - 0.5) * 2.4;
                    double[] q = at(c, rot, -R * 1.1 * Math.cos(a), R * 1.1 * Math.sin(a) * 0.6, R * 1.1 * Math.sin(a));
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], R * 0.18, 0F, 0.6F, 0.75F, 1F, 0.12F * vis);
                }
                for (int i = 0; i < 260; i++) {
                    double u = h(seed, i * 3L);
                    double x = R * (1 + u * 9), spread = R * (0.3 + u * 0.9);
                    double[] q = at(c, rot, x, gauss(seed, i * 3L + 1) * spread * 0.4, gauss(seed, i * 3L + 2) * spread * 0.5);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 2, R * 0.3 * (0.5 + u)), i, 0.55F, 0.7F, 1F, (float) (0.09 * (1 - u * 0.7)) * vis);
                }
            }
            case 10 -> {
                // A T Tauri star: a young, restless star still gathering gas from its disc, jets from its poles.
                float flick = 0.8F + 0.4F * wave(t * 2.3) * wave(t * 0.7);
                star(c, rot, R * 0.3, 1F, 0.7F, 0.45F, flick, t, px, vis, seed, true);
                disk(c, rot, R * 0.5, R * 2.0, 700, 0.9F, 0.55F, 0.35F, 0.45F, 0.3F, 0.25F, 0.12F * vis, t, 0.6, 0.06, seed, px, ADD, R * 0.12);
                disk(c, rot, R * 0.7, R * 1.9, 220, 0.12F, 0.08F, 0.06F, 0.1F, 0.07F, 0.05F, 0.35F * vis, t, 0.6, 0.02, seed + 1, px, NORMAL, R * 0.1);
                Jets.draw(cx, cy, cz, axis, R * 2.5, R * 0.05, 1F, 0.55F, 0.6F, 0.3, seed, t, px, vis * 0.7F);
            }
            case 11 -> {
                // A Herbig Ae/Be star: a young hot star in a wide disc with spiral arms.
                star(c, rot, R * 0.25, 0.85F, 0.9F, 1F, 1.6F, t, px, vis, seed, false);
                for (int arm = 0; arm < 2; arm++) {
                    for (int i = 0; i < 260; i++) {
                        double u = h(seed, arm * 1000L + i);
                        double rr = R * (0.4 + 2.2 * u);
                        double ph = arm * Math.PI + Math.log(rr / (R * 0.4)) / Math.tan(Math.toRadians(14)) + t * 0.05 * Math.pow(R * 0.4 / rr, 1.5)
                                + gauss(seed, arm * 1000L + i + 500) * 0.15;
                        double[] q = at(c, rot, Math.cos(ph) * rr, gauss(seed, i + 9000L) * rr * 0.03, Math.sin(ph) * rr);
                        Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 2, R * 0.12), i, 1F, 0.85F, 0.7F, (float) (0.1 * (1 - u * 0.6)) * vis);
                    }
                }
                disk(c, rot, R * 0.4, R * 2.6, 400, 0.6F, 0.45F, 0.35F, 0.3F, 0.25F, 0.25F, 0.06F * vis, t, 0.05, 0.04, seed + 2, px, ADD, R * 0.15);
            }
            case 12 -> {
                // Luhman 16: a pair of brown dwarfs, too small to shine as stars, banded with clouds, going round each other.
                double ph = t * 0.12;
                double[] a = at(c, rot, Math.cos(ph) * R * 0.9, 0, Math.sin(ph) * R * 0.9), b = at(c, rot, -Math.cos(ph) * R * 1.0, 0, -Math.sin(ph) * R * 1.0);
                double[] light = dirTo(a, b);
                world(a, sub(rot, 0, 1, 0, t * 0.6), R * 0.32, JUPITER, 0.55F, 0.22F, 0.25F, 0.35F, light, t, px, vis, 0.6F, 0.25F, 0.3F, 0.3F);
                world(b, sub(rot, 0, 1, 0, -t * 0.5), R * 0.3, JUPITER, 0.45F, 0.2F, 0.3F, 0.3F, new double[]{-light[0], -light[1], -light[2]}, t, px, vis,
                        0.5F, 0.2F, 0.35F, 0.3F);
                Space3D.billboard(GLOW, ADD, a[0], a[1], a[2], R * 0.6, 0F, 0.8F, 0.25F, 0.2F, 0.25F * vis);
                Space3D.billboard(GLOW, ADD, b[0], b[1], b[2], R * 0.55, 0F, 0.8F, 0.25F, 0.25F, 0.22F * vis);
                orbit(c, rot, R * 0.95, 0.35, 0.3, 0.8F, 0.5F, 0.5F, 0.15F * vis, px);
            }
            case 13 -> {
                // Sirius B: a white dwarf no bigger than Earth beside Sirius A, the brightest star in our sky.
                double ph = t * 0.05;
                double[] a = at(c, rot, Math.cos(ph) * R * 2.2, 0, Math.sin(ph) * R * 2.2);
                star(a, rot, R * 0.5, 0.8F, 0.88F, 1F, 1.5F, t, px, vis, seed, true);
                star(c, rot, R * 0.06, 0.9F, 0.95F, 1F, 3F, t, px, vis, seed + 1, false);
                dot(c, Math.max(px * 4, R * 0.25), 0.85F, 0.92F, 1F, vis);
                orbit(c, rot, R * 1.4, 0.59, 0, 0.7F, 0.8F, 1F, 0.15F * vis, px);
            }
            case 14 -> {
                // A helium white dwarf: a tiny, light one, stripped by a companion, here flying fast through space.
                star(c, rot, R * 0.1, 0.85F, 0.9F, 1F, 2.5F, t, px, vis, seed, false);
                dot(c, Math.max(px * 4, R * 0.3), 0.8F, 0.9F, 1F, vis);
                for (int i = 0; i < 30; i++) {
                    double[] q = at(c, rot, R * (0.15 + i * 0.09), 0, 0);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.06), 0F, 0.6F, 0.75F, 1F, (float) (0.25 * (1 - i / 30.0)) * vis);
                }
            }
            case 15 -> {
                // A carbon star: crimson (carbon soot in its air soaks up the blue), blowing out clouds of soot.
                star(c, rot, R * 0.75, 1F, 0.22F, 0.1F, 0.95F, t * 0.3, px, vis, seed, false);
                for (int i = 0; i < 160; i++) {
                    double life = (t * 0.02 + h(seed, i)) % 1.0;
                    double z = h(seed, i + 300) * 2 - 1, ph = h(seed, i + 400) * Math.PI * 2, s = Math.sqrt(1 - z * z), d = R * (0.85 + 2 * life);
                    double[] q = at(c, rot, s * Math.cos(ph) * d, z * d, s * Math.sin(ph) * d);
                    Space3D.billboard(GLOW, NORMAL, q[0], q[1], q[2], Math.max(px * 2, R * 0.25 * (0.5 + life)), i, 0.12F, 0.05F, 0.03F,
                            (float) (0.45 * (1 - life)) * vis);
                }
            }
            case 16 -> {
                // WR 104: two hot stars whose colliding winds make dust, flung out in a turning pinwheel spiral.
                double ph = t * 0.4;
                double[] a = at(c, rot, Math.cos(ph) * R * 0.08, 0, Math.sin(ph) * R * 0.08), b = at(c, rot, -Math.cos(ph) * R * 0.1, 0, -Math.sin(ph) * R * 0.1);
                star(a, rot, R * 0.05, 0.75F, 0.85F, 1F, 2F, t, px, vis, seed, false);
                star(b, rot, R * 0.06, 0.8F, 0.85F, 1F, 1.8F, t, px, vis, seed + 1, false);
                for (int i = 0; i < 600; i++) {
                    double u = i / 600.0;
                    double rr = R * (0.12 + 2.4 * u);
                    double ang = u * Math.PI * 2 * 3.2 - t * 0.12;
                    double[] q = at(c, rot, Math.cos(ang) * rr + gauss(seed, i) * R * 0.03, gauss(seed, i + 1000L) * R * 0.02,
                            Math.sin(ang) * rr + gauss(seed, i + 2000L) * R * 0.03);
                    float hot = (float) Math.max(0, 1 - u * 2.2);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * (0.05 + 0.12 * u)), i, 1F, 0.55F + 0.3F * hot, 0.3F + 0.4F * hot,
                            (float) (0.32 * (1 - u * 0.75)) * vis);
                }
            }
            case 17 -> {
                // W Ursae Majoris: two stars so close they touch and share one glowing envelope, spinning round fast.
                double ph = t * 0.9;
                double[] a = at(c, rot, Math.cos(ph) * R * 0.42, 0, Math.sin(ph) * R * 0.42), b = at(c, rot, -Math.cos(ph) * R * 0.32, 0, -Math.sin(ph) * R * 0.32);
                star(a, rot, R * 0.45, 1F, 0.88F, 0.65F, 1.1F, t, px, vis, seed, false);
                star(b, rot, R * 0.36, 1F, 0.8F, 0.55F, 1F, t, px, vis, seed + 1, false);
                for (int i = 0; i < 20; i++) {
                    double u = i / 19.0;
                    double[] q = {a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u};
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], R * 0.45, 0F, 1F, 0.85F, 0.6F, 0.1F * vis);
                }
            }
            case 18 -> {
                // Algol: a bright blue star eclipsed every few days by its larger, dimmer orange companion going round it edge on.
                double ph = t * 0.3;
                double[] edge = sub(rot, 1, 0.05, 0, 0);
                double[] b = at(c, edge, Math.cos(ph) * R * 1.5, 0, Math.sin(ph) * R * 1.5);
                star(c, rot, R * 0.45, 0.75F, 0.85F, 1F, 1.6F, t, px, vis, seed, false);
                world(b, rot, R * 0.6, STAR_MAP, 0.85F, 0.55F, 0.3F, 0.35F, dirTo(b, c), t, px, vis, 0F, 0F, 0F, 0F);
                Space3D.billboard(GLOW, ADD, b[0], b[1], b[2], R * 1.2, 0F, 1F, 0.6F, 0.3F, 0.15F * vis);
                orbit(c, edge, R * 1.5, 0, 0, 0.7F, 0.75F, 1F, 0.12F * vis, px);
            }
            case 19 -> {
                // HD 188753: a triple star, a close pair going round a third.
                double ph = t * 0.06, ph2 = t * 0.6;
                double[] pair = at(c, rot, Math.cos(ph) * R * 1.6, 0, Math.sin(ph) * R * 1.6);
                double[] b = at(pair, rot, Math.cos(ph2) * R * 0.25, 0, Math.sin(ph2) * R * 0.25), d = at(pair, rot, -Math.cos(ph2) * R * 0.3, 0,
                        -Math.sin(ph2) * R * 0.3);
                star(c, rot, R * 0.4, 1F, 0.95F, 0.8F, 1.2F, t, px, vis, seed, true);
                star(b, rot, R * 0.22, 1F, 0.75F, 0.45F, 1F, t, px, vis, seed + 1, false);
                star(d, rot, R * 0.17, 1F, 0.55F, 0.35F, 0.9F, t, px, vis, seed + 2, false);
                orbit(c, rot, R * 1.6, 0, 0, 1F, 0.9F, 0.7F, 0.12F * vis, px);
                orbit(pair, rot, R * 0.28, 0, 0, 1F, 0.7F, 0.5F, 0.15F * vis, px);
            }
            // ---- B. Compact objects
            case 20 -> Pulsars.draw(cx, cy, cz, R * 0.12, axis, 20, 0.12, 0.75F, 0.85F, 1F, vis, px, seed, t, true);
            case 21 -> {
                // A millisecond pulsar: spun up by gas from a companion to hundreds of turns a second.
                Pulsars.draw(cx, cy, cz, R * 0.1, axis, 55, 642, 0.8F, 0.85F, 1F, vis, px, seed, t, true);
                disk(c, rot, R * 0.6, R * 1.0, 300, 0.5F, 0.6F, 0.9F, 0.3F, 0.4F, 0.7F, 0.06F * vis, t, 2, 0.01, seed, px, ADD, R * 0.05);
            }
            case 22 -> Pulsars.draw(cx, cy, cz, R * 0.12, axis, 35, 0.19, 0.9F, 0.6F, 1F, vis, px, seed, t, true, false, true);
            case 23 -> {
                // Scorpius X-1: a neutron star pulling gas off a companion into a blazing disc of X-rays.
                double[] comp = at(c, rot, R * 1.8, 0, 0);
                star(comp, rot, R * 0.45, 1F, 0.65F, 0.4F, 0.9F, t, px, vis, seed, false);
                disk(c, rot, R * 0.08, R * 0.8, 600, 0.75F, 0.85F, 1F, 1F, 0.55F, 0.3F, 0.3F * vis, t, 3, 0.03, seed, px, ADD, R * 0.05);
                stream(at(c, rot, R * 1.35, 0, 0), at(c, rot, R * 0.7, 0, -R * 0.3), axis, R * 0.3, R * 0.05, 1F, 0.7F, 0.45F, 0.4F * vis, t, px);
                dot(c, Math.max(px * 3, R * 0.15), 0.8F, 0.9F, 1F, vis);
            }
            case 24 -> {
                // Gaia BH1: a quiet black hole, feeding on nothing, with a Sun-like star going round it.
                double[] n = vec(rot, 0.1, 1, 0.2);
                hole(c, R * 0.06, n, t, vis, 0.12F, px);
                double[] s = onOrbit(c, rot, R * 1.8, 0.45, 0.4, t * 0.1);
                star(s, rot, R * 0.3, 1F, 0.93F, 0.82F, 1.1F, t, px, vis, seed, true);
                orbit(c, rot, R * 1.8, 0.45, 0.4, 1F, 0.9F, 0.7F, 0.15F * vis, px);
            }
            case 25 -> {
                // HLX-1: an intermediate-mass black hole, between the stellar and the giant ones, in a small cluster of its own.
                hole(c, R * 0.06, vec(rot, 0.2, 1, 0.1), t, vis, 1.0F, px);
                StarSwarm.draw(cx, cy, cz, R * 0.06, rot, seed, 120, 6, 40, t, px, vis * 0.8F);
            }
            case 26 -> {
                // A supermassive black hole at the heart of a giant elliptical galaxy.
                hole(c, R * 0.1, vec(rot, 0.1, 1, 0.3), t, vis, 0.7F, px);
                cluster(c, rot, R * 3, 1600, R * 0.5, 0.02F, seed, t, px, vis * 0.6F, 0.75, 0.01);
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 3, 0F, 1F, 0.85F, 0.65F, 0.18F * vis);
            }
            case 27 -> {
                // OJ 287: a smaller black hole crashing through the disc of a giant one twice each orbit, flaring each time.
                double ph = t * 0.25;
                double[] n = vec(rot, 0, 1, 0);
                hole(c, R * 0.12, n, t, vis, 0.9F, px);
                double[] tilt = sub(rot, 0.6, 1, 0, 0);
                double[] b = onOrbit(c, tilt, R * 1.4, 0.6, 0, ph);
                hole(b, R * 0.04, vec(tilt, 0, 1, 0), t, vis, 0.6F, px);
                orbit(c, tilt, R * 1.4, 0.6, 0, 0.9F, 0.8F, 0.6F, 0.12F * vis, px);
                // Crossing the disc: a flash.
                double[] rel = {b[0] - cx, b[1] - cy, b[2] - cz};
                double height = Math.abs(rel[0] * n[0] + rel[1] * n[1] + rel[2] * n[2]) / R;
                float flash = (float) Math.exp(-height * height * 60);
                dot(b, R * (0.2 + 0.8 * flash), 1F, 0.85F, 0.6F, flash * vis);
            }
            case 28 -> {
                // Cygnus X-1: a black hole tearing gas from a blue supergiant into a disc, jets out of it.
                double ph = t * 0.15;
                double[] comp = at(c, rot, Math.cos(ph) * R * 1.8, 0, Math.sin(ph) * R * 1.8);
                Space3D.scale(1.15, 1, 1);
                star(comp, rot, R * 0.6, 0.7F, 0.8F, 1F, 1.4F, t, px, vis, seed, false);
                Space3D.scale(1, 1, 1);
                double[] n = vec(rot, 0, 1, 0);
                hole(c, R * 0.04, n, t, vis, 1.1F, px);
                disk(c, rot, R * 0.12, R * 0.7, 500, 0.8F, 0.85F, 1F, 1F, 0.6F, 0.3F, 0.25F * vis, t, 3, 0.02, seed, px, ADD, R * 0.04);
                double[] l1 = at(c, rot, Math.cos(ph) * R * 1.1, 0, Math.sin(ph) * R * 1.1);
                stream(l1, at(c, rot, Math.cos(ph - 0.6) * R * 0.6, 0, Math.sin(ph - 0.6) * R * 0.6), n, R * 0.25, R * 0.06, 0.7F, 0.8F, 1F, 0.35F * vis, t,
                        px);
                Jets.draw(cx, cy, cz, n, R * 2.2, R * 0.03, 0.6F, 0.75F, 1F, 0.6, seed, t, px, vis * 0.8F);
            }
            case 29 -> {
                // Hercules X-1: a pulsar fed by a companion, its disc warped and slowly wobbling.
                double[] warp = sub(rot, Math.sin(t * 0.05) * 0.3, 1, Math.cos(t * 0.05) * 0.3, 0);
                Pulsars.draw(cx, cy, cz, R * 0.08, axis, 40, 0.8, 0.85F, 0.9F, 1F, vis, px, seed, t, true);
                disk(c, warp, R * 0.15, R * 0.9, 600, 0.9F, 0.9F, 1F, 1F, 0.6F, 0.35F, 0.22F * vis, t, 2.5, 0.05, seed, px, ADD, R * 0.05);
                double[] comp = at(c, rot, R * 2, 0, 0);
                star(comp, rot, R * 0.5, 1F, 0.9F, 0.75F, 1F, t, px, vis, seed + 1, false);
                stream(at(c, rot, R * 1.5, 0, 0), at(c, rot, R * 0.8, 0, -R * 0.3), vec(rot, 0, 1, 0), R * 0.3, R * 0.04, 1F, 0.8F, 0.5F, 0.35F * vis, t,
                        px);
            }
            case 30 -> {
                // A gamma-ray burster: a collapsing star firing two needle-thin jets; bursts and a fading afterglow ring.
                float burst = (float) Math.pow(Math.max(0, Math.sin(t * 0.21)), 30);
                star(c, rot, R * 0.2, 0.7F, 0.8F, 1F, 1.3F, t, px, vis, seed, false);
                Jets.draw(cx, cy, cz, axis, R * 4, R * 0.012, 0.9F, 0.85F, 1F, 0.999, seed, t, px, vis * (0.4F + burst));
                Space3D.billboard(SPARK, ADD, cx, cy, cz, R * (0.5 + 3 * burst), 0F, 0.9F, 0.9F, 1F, burst * vis);
                double life = (t * 0.21 / (Math.PI * 2)) % 1.0;
                shell(c, rot, R * (0.3 + 2.5 * life), 0.05, 90, 1F, 0.6F, 0.35F, (float) (0.15 * (1 - life)) * vis, seed, px, R * 0.08, ADD);
            }
            case 31 -> {
                // SS 433: a microquasar whose jets wobble round in a cone, tracing corkscrews.
                double[] comp = at(c, rot, R * 1.6, 0, 0);
                star(comp, rot, R * 0.45, 0.8F, 0.85F, 1F, 1.2F, t, px, vis, seed, false);
                disk(c, rot, R * 0.08, R * 0.6, 400, 0.85F, 0.9F, 1F, 1F, 0.65F, 0.35F, 0.25F * vis, t, 3, 0.04, seed, px, ADD, R * 0.04);
                for (int side = -1; side <= 1; side += 2) {
                    for (int i = 0; i < 120; i++) {
                        double age = i / 120.0;
                        double prec = (t - age * 30) * 0.12;
                        double cone = 0.35;
                        double dxl = Math.sin(cone) * Math.cos(prec), dzl = Math.sin(cone) * Math.sin(prec), dyl = Math.cos(cone) * side;
                        double d = R * 3.2 * age;
                        double[] q = at(c, rot, dxl * d, dyl * d, dzl * d);
                        Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.05), 0F, 0.6F, 0.75F, 1F, (float) (0.4 * (1 - age)) * vis);
                    }
                }
            }
            case 32 -> {
                // RX J1856.5-3754: so small for its brightness it was put forward as a quark star; a bow shock as it flies.
                star(c, rot, R * 0.07, 0.85F, 0.8F, 1F, 3F, t, px, vis, seed, false);
                dot(c, Math.max(px * 4, R * 0.3), 0.8F, 0.75F, 1F, vis);
                for (int i = 0; i < 400; i++) {
                    double u = h(seed, i), ph = h(seed, i + 500) * Math.PI * 2;
                    double x = -R * 0.6 + u * R * 2.4, rad = R * 0.35 * Math.sqrt(1 + u * 6);
                    double[] q = at(c, rot, x, Math.cos(ph) * rad, Math.sin(ph) * rad);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.06), 0F, 1F, 0.35F, 0.4F, (float) (0.12 * (1 - u)) * vis);
                }
            }
            case 33 -> {
                // HV 2112, a Thorne-Zytkow candidate: a red supergiant with (in theory) a neutron star at its centre.
                Space3D.billboard(SPARK, ADD, cx, cy, cz, R * 0.4, 0F, 0.8F, 0.85F, 1F, 0.8F * vis);
                star(c, rot, R * 0.85, 1F, 0.35F, 0.15F, 0.8F, t * 0.2, px, vis * 0.55F, seed, false);
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 0.5, 0F, 0.7F, 0.8F, 1F, (0.3F + 0.2F * wave(t * 2)) * vis);
            }
            case 34 -> {
                // OGLE-2011-BLG-0462: a lone black hole drifting through space, seen only as it bends and brightens the stars behind.
                double drift = Math.sin(t * 0.05) * R * 2;
                double[] at = {drift, 0};
                backdrop(new double[]{cx + drift * 0.0, cy, cz}, R, 260, seed, px, vis, 1F, at, R * 0.25);
                double[] l = {c[0], c[1], c[2]};
                double dl = Math.sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2]) + 1.0E-12;
                double[] e1 = {-l[2] / dl, 0, l[0] / dl};
                double el = Math.sqrt(e1[0] * e1[0] + e1[2] * e1[2]) + 1.0E-12;
                double[] p = {cx + e1[0] / el * drift, cy, cz + e1[2] / el * drift};
                hole(p, R * 0.08, vec(rot, 0, 1, 0), t, vis, 0.02F, px);
            }
            // ---- C. Planets and planetary objects
            case 35 -> planetWithStar(c, rot, R, JUPITER, 0.09F, 0.08F, 0.08F, 0.15F, 1F, 0.95F, 0.85F, 3.2, t, px, vis, 0.3F, 0.2F, 0.15F, 0.1F, seed);
            case 36 -> planetWithStar(c, rot, R * 0.8, NEPTUNE, 1.25F, 1.25F, 1.3F, 0F, 1F, 0.8F, 0.55F, 2.6, t, px, vis, 0.9F, 0.95F, 1F, 0.5F, seed);
            case 37 -> planetWithStar(c, rot, R * 0.7, ROCK, 0.9F, 0.85F, 0.75F, 0F, 1F, 0.55F, 0.3F, 4.0, t, px, vis, 0.6F, 0.7F, 1F, 0.45F, seed);
            case 38 -> planetWithStar(c, rot, R * 0.75, URANUS, 0.65F, 0.75F, 0.8F, 0F, 1F, 0.55F, 0.35F, 4.0, t, px, vis, 0.7F, 0.8F, 0.9F, 1.0F, seed);
            case 39 -> planetWithStar(c, rot, R * 0.8, OCEAN, 0.45F, 0.65F, 1F, 0F, 1F, 0.55F, 0.35F, 4.0, t, px, vis, 0.5F, 0.7F, 1F, 0.6F, seed);
            case 40 -> {
                planetWithStar(c, rot, R * 0.6, LAVA, 1F, 0.7F, 0.5F, 0.55F, 1F, 0.9F, 0.75F, 2.0, t, px, vis, 1F, 0.5F, 0.2F, 0.25F, seed);
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 1.1, 0F, 1F, 0.4F, 0.1F, 0.18F * vis);
            }
            case 41 -> {
                planetWithStar(c, rot, R * 0.85, URANUS, 0.6F, 0.95F, 1F, 0F, 1F, 0.95F, 0.85F, 9.0, t, px, vis, 0.6F, 0.9F, 1F, 0.5F, seed);
                disk(c, sub(rot, 0.3, 1, 0, 0), R * 1.3, R * 1.6, 500, 0.5F, 0.55F, 0.6F, 0.4F, 0.45F, 0.5F, 0.08F * vis, t, 0.4, 0.002, seed, px, ADD, R * 0.03);
            }
            case 42 -> {
                // PSO J318.5-22: a planet with no star, alone in the dark, faintly warm from within.
                world(c, rot, R * 0.8, JUPITER, 0.25F, 0.12F, 0.1F, 0.35F, vec(rot, 0, 0, 1), t, px, vis, 0.6F, 0.2F, 0.1F, 0.25F);
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 1.6, 0F, 0.6F, 0.15F, 0.08F, 0.15F * vis);
            }
            case 43 -> planetWithStar(c, rot, R * 0.7, MERCURY, 0.28F, 0.25F, 0.22F, 0F, 1F, 0.95F, 0.85F, 4.0, t, px, vis, 0.6F, 0.4F, 0.2F, 0.7F, seed);
            case 44 -> {
                planetWithStar(c, rot, R * 0.5, MERCURY, 0.75F, 0.72F, 0.7F, 0.2F, 1F, 0.6F, 0.35F, 1.6, t, px, vis, 0F, 0F, 0F, 0F, seed);
            }
            case 45 -> {
                planetWithStar(c, rot, R * 0.75, MARS, 1.15F, 0.9F, 0.55F, 0F, 1F, 0.95F, 0.85F, 4.0, t, px, vis, 1F, 0.8F, 0.55F, 0.4F, seed);
                puffs(c, sub(rot, 0, 1, 0, t * 0.1), R * 0.5, R * 0.15, R * 0.5, 40, 0.9F, 0.7F, 0.45F, 0.12F * vis, seed, ADD, px, t, R * 0.25);
            }
            case 46 -> {
                // LP 791-18 d: squeezed by its neighbours like Io, likely covered in volcanoes.
                double[] light = planetWithStar(c, rot, R * 0.65, VENUS, 0.85F, 0.75F, 0.4F, 0.1F, 1F, 0.55F, 0.35F, 4.0, t, px, vis, 0.9F, 0.7F, 0.4F, 0.2F,
                        seed);
                volcanoes(c, rot, R * 0.65, light, t, px, vis, seed);
            }
            case 47 -> {
                // LHS 3844 b: tidally locked, one face always to its star (scorched), the other in endless night.
                double[] sp = at(c, rot, R * 4, 0, 0);
                star(sp, rot, R * 0.8, 1F, 0.5F, 0.3F, 1F, t, px, vis, seed, true);
                double[] light = dirTo(c, sp);
                Space3D.sphere(MERCURY, cx, cy, cz, R * 0.6, rot, light[0], light[1], light[2], 0.005F, 0.75F, 0.55F, 0.45F, 0F, vis, false, 0F, 48);
                double[] hot = {cx + light[0] * R * 0.6, cy + light[1] * R * 0.6, cz + light[2] * R * 0.6};
                Space3D.billboard(GLOW, ADD, hot[0], hot[1], hot[2], R * 0.35, 0F, 1F, 0.45F, 0.15F, 0.3F * vis);
            }
            case 48 -> {
                // Kepler-51 d: a "super-puff", Saturn's size with the mass of a few Earths, wrapped in a vast haze.
                planetWithStar(c, rot, R * 0.85, JUPITER, 0.85F, 0.75F, 0.6F, 0F, 1F, 0.95F, 0.85F, 4.0, t, px, vis, 0.95F, 0.85F, 0.7F, 1F, seed);
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 2.2, 0F, 0.9F, 0.8F, 0.6F, 0.12F * vis);
            }
            case 49 -> {
                // TOI-1338 b: a planet going round two stars.
                double ph = t * 0.5;
                double[] a = at(c, rot, Math.cos(ph) * R * 0.35, 0, Math.sin(ph) * R * 0.35), b = at(c, rot, -Math.cos(ph) * R * 0.6, 0, -Math.sin(ph) * R * 0.6);
                star(a, rot, R * 0.25, 1F, 0.93F, 0.8F, 1.2F, t, px, vis, seed, false);
                star(b, rot, R * 0.12, 1F, 0.55F, 0.35F, 1F, t, px, vis, seed + 1, false);
                double[] p = at(c, rot, Math.cos(t * 0.06) * R * 2.3, 0, Math.sin(t * 0.06) * R * 2.3);
                world(p, rot, R * 0.18, NEPTUNE, 0.6F, 0.7F, 0.9F, 0F, dirTo(p, c), t, px, vis, 0.6F, 0.7F, 1F, 0.4F);
                orbit(c, rot, R * 2.3, 0, 0, 0.6F, 0.75F, 1F, 0.15F * vis, px);
            }
            case 50 -> {
                // Kepler-1625b and its possible moon (as big as Neptune).
                double[] light = vec(rot, 1, 0.2, 0.4);
                world(c, rot, R * 0.7, JUPITER, 0.95F, 0.85F, 0.7F, 0F, light, t, px, vis, 0.9F, 0.85F, 0.7F, 0.35F);
                double[] m = at(c, sub(rot, 0.15, 1, 0, 0), Math.cos(t * 0.15) * R * 2, 0, Math.sin(t * 0.15) * R * 2);
                world(m, rot, R * 0.26, NEPTUNE, 0.6F, 0.75F, 1F, 0F, light, t, px, vis, 0.6F, 0.75F, 1F, 0.4F);
                orbit(c, sub(rot, 0.15, 1, 0, 0), R * 2, 0, 0, 0.6F, 0.75F, 1F, 0.12F * vis, px);
            }
            case 51 -> {
                // 2020 CD3: a tiny asteroid caught for a while by Earth, on a looping path round it.
                double[] e = at(c, rot, -R * 1.6, 0, 0);
                double[] light = vec(rot, 0.4, 0.1, 1);
                world(e, rot, R * 0.9, EARTH, 1F, 1F, 1F, 0F, light, t, px, vis, 0.55F, 0.75F, 1F, 0.5F);
                double[] prev = null;
                for (int i = 0; i <= 120; i++) {
                    double u = i / 120.0 * Math.PI * 2;
                    double rr = R * (1.6 + 0.7 * Math.cos(u * 3));
                    double[] q = at(e, rot, Math.cos(u) * rr, Math.sin(u * 2) * R * 0.3, Math.sin(u) * rr);
                    if (prev != null) {
                        Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, prev[0], prev[1], prev[2], q[0], q[1], q[2], px * 0.8, px * 0.8, 0.8F, 0.8F, 0.7F,
                                0.2F * vis, 0.2F * vis);
                    }
                    prev = q;
                }
                double u = t * 0.1;
                double rr = R * (1.6 + 0.7 * Math.cos(u * 3));
                double[] a = at(e, rot, Math.cos(u) * rr, Math.sin(u * 2) * R * 0.3, Math.sin(u) * rr);
                Space3D.scale(1.5, 0.8, 1);
                world(a, sub(rot, 0.3, 1, 0.2, t), R * 0.06, MOON, 0.6F, 0.55F, 0.5F, 0F, light, t, px, vis, 0F, 0F, 0F, 0F);
                Space3D.scale(1, 1, 1);
                dot(a, px * 3, 0.9F, 0.85F, 0.8F, vis * 0.6F);
            }
            case 52 -> {
                // Quaoar: a dwarf planet with a ring far outside where a ring ought to last, and its little moon.
                double[] light = vec(rot, 1, 0.2, 0.5);
                world(c, rot, R * 0.5, PLUTO, 0.85F, 0.6F, 0.5F, 0F, light, t, px, vis, 0F, 0F, 0F, 0F);
                disk(c, sub(rot, 0.1, 1, 0.1, 0), R * 2.0, R * 2.1, 700, 0.6F, 0.55F, 0.5F, 0.6F, 0.55F, 0.5F, 0.1F * vis, t, 0.2, 0.002, seed, px, ADD,
                        R * 0.02);
                double[] m = at(c, rot, Math.cos(t * 0.08) * R * 3, 0, Math.sin(t * 0.08) * R * 3);
                world(m, rot, R * 0.1, MOON, 0.7F, 0.65F, 0.6F, 0F, light, t, px, vis, 0F, 0F, 0F, 0F);
            }
            case 53 -> {
                // Arrokoth: two lumps of ancient ice gently stuck together, red with organic dust.
                double[] light = vec(rot, 1, 0.3, 0.6);
                double[] spin = sub(rot, 0, 1, 0, t * 0.08);
                Space3D.scale(1, 0.55, 1);
                world(at(c, spin, R * 0.55, 0, 0), spin, R * 0.6, MOON, 0.8F, 0.45F, 0.35F, 0F, light, t, px, vis, 0F, 0F, 0F, 0F);
                Space3D.scale(1, 0.75, 0.9);
                world(at(c, spin, -R * 0.55, 0, 0), spin, R * 0.45, MOON, 0.75F, 0.42F, 0.33F, 0F, light, t, px, vis, 0F, 0F, 0F, 0F);
                Space3D.scale(1, 1, 1);
            }
            case 54 -> {
                // Sedna: a deep red world on an orbit stretching hundreds of times farther than Neptune's.
                double[] light = vec(rot, 1, 0.1, 0.3);
                world(c, rot, R * 0.45, ERIS, 1F, 0.45F, 0.3F, 0F, light, t, px, vis, 0F, 0F, 0F, 0F);
                double[] sun = at(c, rot, R * 3.2, 0, 0);
                dot(sun, Math.max(px * 3, R * 0.08), 1F, 0.95F, 0.85F, vis);
                orbit(sun, rot, R * 9, 0.85, Math.PI, 1F, 0.5F, 0.4F, 0.15F * vis, px);
            }
            // ---- D. Clusters and stellar structures
            case 55 -> cluster(c, rot, R * 2, 3000, R * 0.15, 0.05F, seed, t, px, vis, 1, 0.02);
            case 56 -> {
                // The Pleiades: a loose young cluster of hot blue stars, passing through a cloud that reflects their light.
                cluster(c, rot, R * 1.6, 140, R * 0.8, 0.85F, seed, t, px, vis * 1.3F, 0.8, 0.005);
                puffs(c, rot, R * 0.9, R * 0.5, R * 0.9, 90, 0.45F, 0.6F, 1F, 0.05F * vis, seed + 1, ADD, px, t, R * 0.45);
                strand(c, rot, new double[][]{{-R, 0, -R * 0.5}, {0, R * 0.2, 0}, {R, -R * 0.1, R * 0.6}}, R * 0.15, 0.5F, 0.65F, 1F, 0.05F * vis, ADD, px,
                        seed, 40);
            }
            case 57 -> {
                // R136: a super star cluster of the most massive stars known, carving out a glowing cavity.
                cluster(c, rot, R * 0.6, 900, R * 0.05, 0.9F, seed, t, px, vis * 1.3F, 1, 0.01);
                shell(c, rot, R * 1.8, 0.45, 300, 1F, 0.35F, 0.45F, 0.08F * vis, seed + 1, px, R * 0.35, ADD);
                puffs(c, rot, R * 1.2, R * 1.0, R * 1.2, 100, 0.4F, 0.65F, 0.9F, 0.04F * vis, seed + 2, ADD, px, t, R * 0.5);
            }
            case 58 -> {
                // The Scorpius-Centaurus association: groups of young hot stars, born together and drifting apart.
                for (int g = 0; g < 3; g++) {
                    double[] gc = at(c, rot, (g - 1) * R * 1.5, gauss(seed, g) * R * 0.2, gauss(seed, g + 10) * R * 0.5);
                    cluster(gc, rot, R * 0.9, 90, R * 0.6, 0.8F, seed + g, t, px, vis * 1.2F, 0.6, 0.004);
                }
                shell(c, rot, R * 2.6, 0.2, 200, 0.6F, 0.7F, 1F, 0.03F * vis, seed + 9, px, R * 0.5, ADD);
            }
            case 59 -> {
                // The Ursa Major moving group: stars spread across the sky but sharing one motion through space.
                for (int i = 0; i < 70; i++) {
                    double[] q = at(c, rot, gauss(seed, i) * R, gauss(seed, i + 100) * R * 0.4, gauss(seed, i + 200) * R);
                    float br = (float) (0.4 + 0.6 * h(seed, i + 300));
                    double[] q2 = at(q, rot, R * 0.5, R * 0.06, 0);
                    double flow = (t * 0.2 + h(seed, i + 400)) % 1.0;
                    double[] q3 = {q[0] + (q2[0] - q[0]) * flow, q[1] + (q2[1] - q[1]) * flow, q[2] + (q2[2] - q[2]) * flow};
                    Space3D.ribbon(GLOW, ADD, q[0], q[1], q[2], q2[0], q2[1], q2[2], Math.max(px, R * 0.006), Math.max(px * 0.5, R * 0.002), 0.75F, 0.85F,
                            1F, 0.2F * vis, 0F);
                    dot(q3, Math.max(px * 2, R * 0.03 * br), 0.85F, 0.9F, 1F, br * vis);
                }
            }
            case 60 -> {
                // Andromeda's double nucleus: an eccentric disc of old stars that looks like two cores, round a black hole.
                double[] n = sub(rot, 0.2, 1, 0, 0);
                for (int i = 0; i < 1400; i++) {
                    double e = 0.6, a = R * (0.2 + 0.8 * h(seed, i));
                    double m = h(seed, i + 3000) * Math.PI * 2 + t * 0.05 * Math.pow(R / a, 1.5) * 0.3;
                    double[] q = onOrbit(c, n, a, e, 0, m);
                    q[1] += gauss(seed, i + 6000) * R * 0.04;
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.03), 0F, 1F, 0.85F, 0.65F, 0.2F * vis);
                }
                hole(c, R * 0.03, vec(n, 0, 1, 0), t, vis, 0.4F, px);
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 0.4, 0F, 0.7F, 0.8F, 1F, 0.3F * vis);
            }
            case 61 -> {
                // The Milky Way's nuclear star cluster round Sagittarius A*: crowded old stars and a disc of young ones.
                cluster(c, rot, R * 1.8, 1800, R * 0.2, 0.08F, seed, t, px, vis, 0.7, 0.015);
                disk(c, sub(rot, 0.6, 1, 0.3, 0), R * 0.15, R * 0.6, 120, 0.7F, 0.8F, 1F, 0.7F, 0.8F, 1F, 0.5F * vis, t, 0.3, 0.05, seed, px, ADD, R * 0.015);
                hole(c, R * 0.02, vec(rot, 0, 1, 0), t, vis, 0.35F, px);
            }
            case 62 -> {
                // GD-1: a long thin stream of stars torn from a cluster, with a gap and a spur where something passed through.
                for (int i = 0; i < 1100; i++) {
                    double u = h(seed, i) * 2 - 1;
                    if (Math.abs(u - 0.2) < 0.05 && h(seed, i + 77) < 0.85) {
                        continue;
                    }
                    double along = u * R * 3.2;
                    double[] q = at(c, rot, along, Math.sin(u * 1.4) * R * 0.6 + gauss(seed, i + 3000) * R * 0.04, Math.cos(u * 1.4) * R * 0.6 - R * 0.6
                            + gauss(seed, i + 4000) * R * 0.04);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.3, R * 0.012), 0F, 1F, 0.9F, 0.7F, 0.45F * vis);
                }
                for (int i = 0; i < 90; i++) {
                    double u = 0.25 + h(seed, i + 9000) * 0.15;
                    double[] q = at(c, rot, u * R * 3.2, Math.sin(u * 1.4) * R * 0.6 + R * 0.15 * h(seed, i + 9100), Math.cos(u * 1.4) * R * 0.6 - R * 0.6);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.3, R * 0.012), 0F, 1F, 0.9F, 0.7F, 0.4F * vis);
                }
            }
            case 63 -> {
                // The Antennae: two colliding galaxies, their stars thrown out into two long curving tails.
                for (int g = 0; g < 2; g++) {
                    int s = g == 0 ? 1 : -1;
                    double[] core = at(c, rot, s * R * 0.35, 0, s * R * 0.1);
                    cluster(core, sub(rot, 0.3 * s, 1, 0, 0), R * 0.5, 500, R * 0.12, 0.2F, seed + g, t, px, vis, 0.35, 0.02);
                    for (int i = 0; i < 700; i++) {
                        double u = h(seed, g * 5000L + i);
                        double ang = s * (u * 2.6 + 0.3);
                        double rr = R * (0.4 + u * 2.4);
                        double[] q = at(c, rot, s * R * 0.35 + Math.cos(ang) * rr * s, gauss(seed, g * 5000L + i + 1000) * R * 0.05 * (1 + u),
                                Math.sin(ang) * rr + gauss(seed, g * 5000L + i + 2000) * R * 0.08);
                        Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.4, R * 0.02), 0F, 0.8F, 0.85F, 1F, (float) (0.22 * (1 - u * 0.7)) * vis);
                    }
                }
                puffs(c, rot, R * 0.35, R * 0.1, R * 0.25, 50, 1F, 0.35F, 0.5F, 0.15F * vis, seed + 3, ADD, px, t, R * 0.08);
            }
            case 64 -> {
                // A galaxy's halo: a faint ball of old stars and globular clusters round its disc.
                Galaxies.Look mw = Galaxies.look("milky_way");
                if (mw != null) {
                    Galaxies.draw(mw, cx, cy, cz, R * 0.7, Galaxies.frame(mw, rot), t, px, vis * 0.8F);
                }
                cluster(c, rot, R * 2.6, 900, R * 0.6, 0F, seed, t, px, vis * 0.4F, 0.85, 0.003);
                for (int i = 0; i < 30; i++) {
                    double[] q = at(c, rot, gauss(seed, i + 50) * R * 1.2, gauss(seed, i + 60) * R * 1.0, gauss(seed, i + 70) * R * 1.2);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 2, R * 0.05), 0F, 1F, 0.9F, 0.7F, 0.5F * vis);
                }
            }
            // ---- E. Cosmic structures and other objects
            case 65 -> {
                // The Taurus molecular cloud: cold, dark, clumpy gas where young stars are forming.
                backdrop(c, R, 200, seed, px, vis, 0.8F, null, 1);
                puffs(c, rot, R * 1.6, R * 0.5, R * 0.8, 160, 0.06F, 0.05F, 0.05F, 0.55F * vis, seed, NORMAL, px, t, R * 0.45);
                for (int i = 0; i < 14; i++) {
                    double[] q = at(c, rot, gauss(seed, i + 900) * R, gauss(seed, i + 950) * R * 0.3, gauss(seed, i + 990) * R * 0.5);
                    dot(q, Math.max(px * 2, R * 0.05), 1F, 0.6F, 0.4F, 0.8F * vis);
                }
            }
            case 66 -> {
                // Barnard 68: a small, dense, round cloud blotting out every star behind it, its edge reddening them.
                backdrop(c, R, 420, seed, px, vis, 1F, null, 1);
                shell(c, rot, R * 0.85, 0.12, 120, 0.6F, 0.3F, 0.15F, 0.08F * vis, seed + 1, px, R * 0.2, ADD);
                puffs(c, rot, R * 0.35, R * 0.35, R * 0.35, 90, 0.02F, 0.015F, 0.015F, 0.8F * vis, seed, NORMAL, px, t, R * 0.45);
            }
            case 67 -> {
                // The Coalsack: a dark nebula against the crowded Milky Way.
                backdrop(c, R, 700, seed, px, vis, 1.1F, null, 1);
                puffs(c, rot, R * 1.0, R * 0.7, R * 0.4, 130, 0.03F, 0.025F, 0.025F, 0.65F * vis, seed, NORMAL, px, t, R * 0.5);
            }
            case 68 -> {
                // The Iris Nebula: dust lit up blue by the star inside it.
                star(c, rot, R * 0.08, 0.85F, 0.9F, 1F, 2F, t, px, vis, seed, false);
                puffs(c, rot, R * 0.8, R * 0.7, R * 0.6, 150, 0.4F, 0.55F, 1F, 0.06F * vis, seed, ADD, px, t, R * 0.4);
                for (int k = 0; k < 7; k++) {
                    double a = k / 7.0 * Math.PI * 2;
                    strand(c, rot, new double[][]{{0, 0, 0}, {Math.cos(a) * R * 0.7, gauss(seed, k) * R * 0.2, Math.sin(a) * R * 0.7},
                            {Math.cos(a + 0.3) * R * 1.3, gauss(seed, k + 9) * R * 0.3, Math.sin(a + 0.3) * R * 1.3}}, R * 0.06, 0.08F, 0.07F, 0.08F, 0.3F * vis,
                            NORMAL, px, seed + k, 30);
                }
            }
            case 69 -> {
                // The Butterfly Nebula: a dying star's gas thrown out in two great wings, pinched by a dark ring of dust.
                star(c, rot, R * 0.04, 0.9F, 0.9F, 1F, 3F, t, px, vis, seed, false);
                for (int side = -1; side <= 1; side += 2) {
                    for (int i = 0; i < 360; i++) {
                        double u = h(seed, i * 2L + (side > 0 ? 0 : 99999));
                        double y = side * R * 1.8 * Math.pow(u, 0.7);
                        double rad = R * 0.7 * Math.sin(Math.PI * Math.pow(u, 0.8)) * (0.6 + 0.4 * h(seed, i + 7000));
                        double ph = h(seed, i + 8000) * Math.PI * 2;
                        double[] q = at(c, rot, Math.cos(ph) * rad, y, Math.sin(ph) * rad);
                        float hot = (float) (1 - u);
                        Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.12), i, 0.9F + 0.1F * hot, 0.4F + 0.5F * hot, 0.35F + 0.6F * hot,
                                0.1F * vis);
                    }
                }
                disk(c, rot, R * 0.08, R * 0.3, 160, 0.12F, 0.06F, 0.04F, 0.1F, 0.05F, 0.03F, 0.6F * vis, t, 0.2, 0.15, seed, px, NORMAL, R * 0.08);
            }
            case 70 -> {
                // Cassiopeia A: the expanding shell of a star that exploded, knots of oxygen, silicon and iron glowing in colours.
                double grow = 1 + 0.02 * Math.sin(t * 0.03);
                shell(c, sub(rot, 0, 1, 0, t * 0.002), R * 1.1 * grow, 0.12, 380, 0.4F, 1F, 0.55F, 0.12F * vis, seed, px, R * 0.08, ADD);
                shell(c, sub(rot, 0, 1, 0, t * 0.002), R * 1.0 * grow, 0.18, 300, 1F, 0.35F, 0.3F, 0.12F * vis, seed + 1, px, R * 0.1, ADD);
                shell(c, sub(rot, 0, 1, 0, t * 0.002), R * 0.85 * grow, 0.25, 200, 0.45F, 0.55F, 1F, 0.12F * vis, seed + 2, px, R * 0.1, ADD);
                dot(c, Math.max(px * 2, R * 0.04), 0.8F, 0.85F, 1F, vis);
            }
            case 71 -> {
                // 3C 58: a young pulsar blowing a nebula of its own, a torus and jets in a web of glowing filaments.
                Pulsars.draw(cx, cy, cz, R * 0.04, axis, 30, 15, 0.8F, 0.85F, 1F, vis, px, seed, t, false, true, false);
                disk(c, rot, R * 0.2, R * 0.45, 400, 0.75F, 0.8F, 1F, 0.6F, 0.7F, 1F, 0.1F * vis, t, 0.3, 0.05, seed, px, ADD, R * 0.05);
                puffs(c, rot, R * 0.9, R * 0.6, R * 0.6, 160, 0.55F, 0.65F, 1F, 0.05F * vis, seed + 1, ADD, px, t, R * 0.35);
                for (int k = 0; k < 10; k++) {
                    double a = h(seed, k + 50) * Math.PI * 2, b = h(seed, k + 60) * 2 - 1;
                    strand(c, rot, new double[][]{{Math.cos(a) * R * 0.3, b * R * 0.2, Math.sin(a) * R * 0.3}, {Math.cos(a + 0.5) * R * 0.9, b * R * 0.6,
                            Math.sin(a + 0.5) * R * 0.7}}, R * 0.03, 0.75F, 0.8F, 1F, 0.12F * vis, ADD, px, seed + k, 25);
                }
            }
            case 72 -> {
                // NGC 604: a vast glowing cloud of hydrogen lit pink by the hot young stars inside it.
                cluster(c, rot, R * 0.6, 200, R * 0.3, 0.9F, seed, t, px, vis * 1.2F, 0.8, 0.003);
                shell(c, rot, R * 1.1, 0.35, 380, 1F, 0.35F, 0.5F, 0.09F * vis, seed + 1, px, R * 0.3, ADD);
                puffs(c, rot, R * 0.8, R * 0.6, R * 0.7, 120, 1F, 0.45F, 0.55F, 0.05F * vis, seed + 2, ADD, px, t, R * 0.4);
            }
            case 73 -> {
                // HH 47: a jet from a newborn star hidden in its cloud, ending in glowing bow shocks.
                puffs(at(c, rot, 0, -R * 1.6, 0), rot, R * 0.4, R * 0.4, R * 0.4, 50, 0.05F, 0.04F, 0.04F, 0.7F * vis, seed, NORMAL, px, t, R * 0.4);
                for (int i = 0; i < 200; i++) {
                    double u = i / 200.0, w = Math.sin(u * 30 + t * 0.5) * R * 0.03;
                    double[] q = at(c, rot, w, -R * 1.5 + u * R * 3, 0);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.05), 0F, 1F, 0.4F, 0.45F, 0.12F * vis);
                }
                for (int k = 0; k < 3; k++) {
                    double y = -R * 0.4 + k * R * 0.7 + ((t * 0.03) % 1.0) * R * 0.2;
                    for (int i = 0; i < 40; i++) {
                        double a = (i / 39.0 - 0.5) * 2.4;
                        double[] q = at(c, rot, Math.sin(a) * R * 0.3, y - (1 - Math.cos(a)) * R * 0.25, Math.cos(a) * R * 0.05);
                        Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.07), 0F, 1F, 0.5F, 0.4F, 0.2F * vis);
                    }
                }
            }
            case 74 -> {
                // HL Tauri: a young star in a disc of dust banded by dark gaps, where planets are forming.
                star(c, rot, R * 0.1, 1F, 0.75F, 0.5F, 1.5F, t, px, vis, seed, false);
                double[] gaps = {0.3, 0.45, 0.62, 0.8, 0.95};
                for (int k = 0; k < gaps.length; k++) {
                    double in = (k == 0 ? 0.12 : gaps[k - 1] + 0.035) * R * 2, out = (gaps[k] - 0.02) * R * 2;
                    disk(c, rot, in, out, 360, 1F, 0.7F, 0.45F, 0.85F, 0.5F, 0.3F, 0.16F * vis, t, 0.4, 0.01, seed + k, px, ADD, R * 0.06);
                }
            }
            case 75 -> {
                // AU Microscopii: a dusty debris disc seen edge on, with blobs racing outwards along it.
                double[] edge = sub(rot, 1, 0.03, 0, 0);
                star(c, rot, R * 0.12, 1F, 0.5F, 0.3F, 1.3F, t, px, vis, seed, true);
                disk(c, edge, R * 0.3, R * 2.6, 900, 0.75F, 0.7F, 0.75F, 0.5F, 0.5F, 0.6F, 0.1F * vis, t, 0.15, 0.015, seed, px, ADD, R * 0.07);
                for (int i = 0; i < 6; i++) {
                    double d = R * (0.6 + ((t * 0.02 + i / 6.0) % 1.0) * 2.0);
                    double[] q = at(c, edge, (i % 2 == 0 ? 1 : -1) * d, R * 0.04 * Math.sin(i), 0);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], R * 0.1, 0F, 0.8F, 0.8F, 0.9F, 0.25F * vis);
                }
            }
            case 76 -> {
                // Epsilon Aurigae: a bright star eclipsed for two years by a huge dark disc round a companion.
                star(c, rot, R * 0.6, 1F, 0.9F, 0.7F, 1.3F, t, px, vis, seed, false);
                double x = Math.sin(t * 0.04) * R * 2.2;
                double[] dc = at(c, rot, x, 0, R * 1.5);
                double[] tilt = sub(rot, 0.08, 1, 0.3, 0);
                disk(dc, tilt, R * 0.2, R * 1.1, 600, 0.08F, 0.06F, 0.05F, 0.05F, 0.04F, 0.04F, 0.55F * vis, t, 0.3, 0.06, seed, px, NORMAL, R * 0.18);
                star(dc, rot, R * 0.12, 0.7F, 0.8F, 1F, 1.2F, t, px, vis * 0.5F, seed + 1, false);
            }
            case 77 -> {
                // SS Cygni: a white dwarf drawing gas from a red dwarf into a disc that flares up in outbursts every few weeks.
                float out = (float) Math.pow(wave(t * 0.15), 6);
                double[] comp = at(c, rot, R * 1.6, 0, 0);
                star(comp, rot, R * 0.45, 1F, 0.45F, 0.25F, 1F, t, px, vis, seed, false);
                disk(c, rot, R * 0.05, R * 0.6, 600, 0.8F, 0.85F, 1F, 1F, 0.75F, 0.55F, (0.2F + 0.5F * out) * vis, t, 2, 0.03, seed, px, ADD, R * 0.04);
                stream(at(c, rot, R * 1.2, 0, 0), at(c, rot, R * 0.5, 0, -R * 0.3), axis, R * 0.25, R * 0.03, 1F, 0.6F, 0.4F, 0.4F * vis, t, px);
                dot(at(c, rot, R * 0.5, 0, -R * 0.3), Math.max(px * 2, R * 0.1), 1F, 0.85F, 0.6F, 0.6F * vis);
                dot(c, Math.max(px * 2, R * 0.12), 0.85F, 0.9F, 1F, vis);
            }
            case 78 -> {
                // NGC 891: a spiral seen edge on, its dust lane a dark stripe with filaments rising out of it.
                double[] edge = sub(rot, 1, 0.02, 0, 0);
                disk(c, edge, R * 0.05, R * 2.4, 1600, 1F, 0.88F, 0.7F, 0.8F, 0.85F, 1F, 0.12F * vis, t, 0.05, 0.05, seed, px, ADD, R * 0.08);
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 0.8, 0F, 1F, 0.85F, 0.65F, 0.25F * vis);
                disk(c, edge, R * 0.1, R * 2.2, 700, 0.05F, 0.04F, 0.03F, 0.05F, 0.04F, 0.03F, 0.45F * vis, t, 0.05, 0.012, seed + 1, px, NORMAL, R * 0.07);
                for (int i = 0; i < 20; i++) {
                    double x = (h(seed, i + 40) - 0.5) * R * 3.6;
                    strand(c, edge, new double[][]{{x, 0, 0}, {x + R * 0.05, R * 0.25 * (i % 2 == 0 ? 1 : -1), 0}}, R * 0.02, 0.06F, 0.05F, 0.04F, 0.35F * vis,
                            NORMAL, px, seed + i, 8);
                }
            }
            case 79 -> {
                // The Musca filament: a long, thin thread of cold gas and dust, with fine striations alongside.
                backdrop(c, R, 300, seed, px, vis, 0.9F, null, 1);
                double[][] line = {{-R * 2.5, -R * 0.3, 0}, {-R, 0, R * 0.1}, {0, R * 0.1, 0}, {R, 0, -R * 0.1}, {R * 2.5, R * 0.3, 0}};
                strand(c, rot, line, R * 0.12, 0.04F, 0.035F, 0.035F, 0.6F * vis, NORMAL, px, seed, 80);
                for (int k = -3; k <= 3; k++) {
                    if (k == 0) {
                        continue;
                    }
                    double[][] st = {{k * R * 0.15 - R * 0.2, -R * 0.6, 0}, {k * R * 0.15 + R * 0.2, R * 0.6, 0}};
                    strand(c, rot, st, R * 0.03, 0.05F, 0.045F, 0.045F, 0.25F * vis, NORMAL, px, seed + k + 10, 30);
                }
            }
            case 80 -> {
                // LAB-1: a vast blob of hydrogen glowing in Lyman-alpha light, galaxies forming inside it.
                puffs(c, rot, R * 1.2, R * 0.9, R * 1.0, 200, 0.75F, 1F, 0.45F, 0.05F * vis, seed, ADD, px, t, R * 0.6);
                for (int i = 0; i < 6; i++) {
                    double[] q = at(c, rot, gauss(seed, i + 30) * R * 0.5, gauss(seed, i + 40) * R * 0.4, gauss(seed, i + 50) * R * 0.5);
                    smudge(q, rot, R * 0.15, 0.6, 0.8F, 0.85F, 1F, 0.25F * vis, seed + i, px, t);
                }
            }
            case 81 -> {
                // Stephan's Quintet: five galaxies (four in a real group) with a shock front glowing between them.
                double[][] pos = {{-R, 0, 0}, {-R * 0.3, R * 0.15, R * 0.4}, {R * 0.2, -R * 0.1, -R * 0.2}, {R * 0.9, R * 0.3, R * 0.2}, {-R * 0.6, -R * 0.6, -R * 0.4}};
                for (int i = 0; i < 5; i++) {
                    double[] q = at(c, rot, pos[i][0], pos[i][1], pos[i][2]);
                    smudge(q, rot, R * 0.35, i == 4 ? 0.5 : 1, i == 4 ? 0.7F : 1F, i == 4 ? 0.8F : 0.85F, 1F, 0.22F * vis, seed + i * 7, px, t);
                }
                strand(c, rot, new double[][]{{R * 0.4, -R * 0.6, 0}, {R * 0.5, 0, R * 0.1}, {R * 0.4, R * 0.6, 0}}, R * 0.12, 0.6F, 1F, 0.7F, 0.06F * vis, ADD,
                        px, seed + 99, 40);
            }
            case 82 -> {
                // The Coma Cluster: a thousand galaxies (mostly ellipticals) in a cloud of hot gas, two giants at its centre.
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 3, 0F, 0.65F, 0.55F, 1F, 0.08F * vis);
                for (int i = 0; i < 260; i++) {
                    double rr = R * 0.3 * Math.tan(h(seed, i) * Math.atan(2.4 / 0.3));
                    double z = h(seed, i + 500) * 2 - 1, ph = h(seed, i + 600) * Math.PI * 2, s = Math.sqrt(1 - z * z);
                    double[] q = at(c, rot, s * Math.cos(ph) * rr, z * rr, s * Math.sin(ph) * rr);
                    double size = R * (0.02 + 0.05 * Math.pow(h(seed, i + 700), 4));
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, size), 0F, 1F, 0.88F, 0.7F, 0.6F * vis);
                }
                for (int g = -1; g <= 1; g += 2) {
                    double[] q = at(c, rot, g * R * 0.15, 0, 0);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], R * 0.3, 0F, 1F, 0.86F, 0.65F, 0.4F * vis);
                }
            }
            case 83 -> {
                // Laniakea: our supercluster, its galaxies all flowing along streams towards the Great Attractor.
                for (int i = 0; i < 600; i++) {
                    double[] p0 = {gauss(seed, i) * R * 1.5, gauss(seed, i + 1000) * R * 0.6, gauss(seed, i + 2000) * R * 1.5};
                    double flow = (t * 0.02 + h(seed, i + 3000)) % 1.0;
                    double k = 1 - 0.7 * flow;
                    double[] q = at(c, rot, p0[0] * k, p0[1] * k, p0[2] * k);
                    double[] q2 = at(c, rot, p0[0] * (k - 0.06), p0[1] * (k - 0.06), p0[2] * (k - 0.06));
                    Space3D.ribbon(GLOW, ADD, q[0], q[1], q[2], q2[0], q2[1], q2[2], Math.max(px, R * 0.008), Math.max(px * 0.6, R * 0.004), 1F, 0.85F, 0.6F,
                            0.3F * vis, 0.05F * vis);
                }
                Space3D.billboard(GLOW, ADD, cx, cy, cz, R * 0.5, 0F, 1F, 0.8F, 0.55F, 0.35F * vis);
            }
            case 84 -> {
                // The Bootes Void: a vast near-empty bubble, walled by galaxies.
                shell(c, sub(rot, 0, 1, 0, t * 0.003), R * 1.8, 0.12, 900, 1F, 0.85F, 0.65F, 0.35F * vis, seed, px, R * 0.025, ADD);
                for (int i = 0; i < 12; i++) {
                    double[] q = at(c, rot, gauss(seed, i + 70) * R * 0.6, gauss(seed, i + 80) * R * 0.6, gauss(seed, i + 90) * R * 0.6);
                    dot(q, Math.max(px * 1.5, R * 0.02), 1F, 0.85F, 0.7F, 0.5F * vis);
                }
            }
            case 85 -> {
                // The Sloan Great Wall: a wall of galaxies hundreds of millions of light years long, part of the cosmic web.
                for (int k = 0; k < 6; k++) {
                    double a = h(seed, k) * Math.PI * 2, b = h(seed, k + 10) - 0.5;
                    double[][] line = {{-R * 2.8, b * R, -R * 0.3}, {Math.cos(a) * R, b * R * 0.4, Math.sin(a) * R}, {R * 2.8, -b * R, R * 0.3}};
                    strand(c, rot, line, R * 0.15, 1F, 0.85F, 0.7F, 0.35F * vis, ADD, px, seed + k, 120);
                }
            }
            case 86 -> {
                // 3C 273: a quasar, a black hole's disc outshining its whole galaxy, a single long jet.
                smudge(c, rot, R * 1.2, 0.7, 1F, 0.85F, 0.7F, 0.08F * vis, seed, px, t);
                dot(c, Math.max(px * 4, R * 0.3), 0.85F, 0.9F, 1F, (1.2F + 0.3F * wave(t * 0.5)) * vis);
                double[] jet = vec(rot, 1, 0.2, 0.3);
                Jets.draw(cx, cy, cz, jet, R * 3, R * 0.06, 0.6F, 0.75F, 1F, 0.95, seed, t, px, vis);
            }
            case 87 -> {
                // 3C 279: a blazar, its jet aimed almost at us, flickering wildly.
                double l = Math.sqrt(cx * cx + cy * cy + cz * cz) + 1.0E-12;
                double[] toUs = {-cx / l + 0.08, -cy / l + 0.05, -cz / l};
                double tl = Math.sqrt(toUs[0] * toUs[0] + toUs[1] * toUs[1] + toUs[2] * toUs[2]);
                toUs[0] /= tl;
                toUs[1] /= tl;
                toUs[2] /= tl;
                float flick = (float) (0.6 + 0.4 * wave(t * 3.1) * wave(t * 1.3) + 0.6 * Math.pow(wave(t * 0.4), 12));
                Jets.draw(cx, cy, cz, toUs, R * 2, R * 0.12, 0.6F, 0.75F, 1F, 0.99, seed, t, px, vis);
                dot(c, Math.max(px * 5, R * 0.5 * flick), 0.85F, 0.9F, 1F, flick * vis);
                smudge(c, rot, R * 0.9, 0.8, 1F, 0.85F, 0.7F, 0.06F * vis, seed, px, t);
            }
            case 88 -> {
                // Cygnus A: a radio galaxy, jets running from its core into two great lobes with hot spots at their tips.
                smudge(c, rot, R * 0.35, 1, 1F, 0.85F, 0.65F, 0.25F * vis, seed, px, t);
                double[] jet = vec(rot, 1, 0, 0);
                Jets.draw(cx, cy, cz, jet, R * 2.4, R * 0.03, 0.7F, 0.8F, 1F, 0.9, seed, t, px, vis);
                Jets.draw(cx, cy, cz, new double[]{-jet[0], -jet[1], -jet[2]}, R * 2.4, R * 0.03, 0.7F, 0.8F, 1F, 0.9, seed + 1, t, px, vis);
                for (int s = -1; s <= 1; s += 2) {
                    double[] lobe = at(c, rot, s * R * 2.5, 0, 0);
                    puffs(lobe, rot, R * 0.6, R * 0.4, R * 0.4, 90, 0.45F, 0.55F, 1F, 0.08F * vis, seed + s, ADD, px, t, R * 0.3);
                    puffs(lobe, rot, R * 0.4, R * 0.3, R * 0.3, 50, 1F, 0.55F, 0.3F, 0.06F * vis, seed + s + 5, ADD, px, t, R * 0.25);
                    dot(at(c, rot, s * R * 2.9, 0, 0), Math.max(px * 2, R * 0.1), 1F, 0.9F, 0.8F, vis);
                }
            }
            case 89 -> {
                // The Cosmic Horseshoe: a massive red galaxy bending a far blue galaxy's light into a near-ring.
                smudge(c, rot, R * 0.5, 1, 1F, 0.6F, 0.35F, 0.35F * vis, seed, px, t);
                double l = Math.sqrt(cx * cx + cy * cy + cz * cz) + 1.0E-12;
                double[] z = {cx / l, cy / l, cz / l};
                double[] e1 = {-z[2], 0, z[0]};
                double el = Math.sqrt(e1[0] * e1[0] + e1[2] * e1[2]) + 1.0E-12;
                e1[0] /= el;
                e1[2] /= el;
                double[] e2 = {z[1] * e1[2] - z[2] * e1[1], z[2] * e1[0] - z[0] * e1[2], z[0] * e1[1] - z[1] * e1[0]};
                for (int i = 0; i < 300; i++) {
                    double a = (h(seed, i) * 1.65 + 0.1) * Math.PI * 2 * 0.5 * 2 - 0.4;
                    if (Math.cos(a - 1.2) < -0.8) {
                        continue;
                    }
                    double rr = R * (1.2 + gauss(seed, i + 400) * 0.03);
                    double[] q = {cx + (e1[0] * Math.cos(a) + e2[0] * Math.sin(a)) * rr, cy + (e1[1] * Math.cos(a) + e2[1] * Math.sin(a)) * rr,
                            cz + (e1[2] * Math.cos(a) + e2[2] * Math.sin(a)) * rr};
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.06), 0F, 0.45F, 0.6F, 1F, 0.25F * vis);
                }
            }
            case 90 -> {
                // The Einstein Cross: one quasar seen four times round a nearby galaxy's core, each image flickering on its own.
                smudge(c, rot, R * 1.4, 0.6, 1F, 0.85F, 0.65F, 0.18F * vis, seed, px, t);
                double l = Math.sqrt(cx * cx + cy * cy + cz * cz) + 1.0E-12;
                double[] z = {cx / l, cy / l, cz / l};
                double[] e1 = {-z[2], 0, z[0]};
                double el = Math.sqrt(e1[0] * e1[0] + e1[2] * e1[2]) + 1.0E-12;
                e1[0] /= el;
                e1[2] /= el;
                double[] e2 = {z[1] * e1[2] - z[2] * e1[1], z[2] * e1[0] - z[0] * e1[2], z[0] * e1[1] - z[1] * e1[0]};
                for (int k = 0; k < 4; k++) {
                    double a = k * Math.PI / 2 + 0.3, rr = R * (k % 2 == 0 ? 0.42 : 0.36);
                    double[] q = {cx + (e1[0] * Math.cos(a) + e2[0] * Math.sin(a)) * rr, cy + (e1[1] * Math.cos(a) + e2[1] * Math.sin(a)) * rr,
                            cz + (e1[2] * Math.cos(a) + e2[2] * Math.sin(a)) * rr};
                    float f = 0.6F + 0.5F * wave(t * (0.3 + 0.13 * k) + k);
                    dot(q, Math.max(px * 3, R * 0.1 * f), 0.7F, 0.8F, 1F, f * vis);
                }
            }
            case 91 -> {
                // ASASSN-14li: a star torn apart by a black hole, its debris wound into a spiral falling in, glowing.
                double[] n = vec(rot, 0, 1, 0);
                hole(c, R * 0.06, n, t, vis, 0.9F, px);
                for (int i = 0; i < 500; i++) {
                    double u = i / 500.0;
                    double ang = u * Math.PI * 5 + t * 0.2;
                    double rr = R * (0.15 + 2.2 * (1 - u));
                    double[] q = at(c, rot, Math.cos(ang) * rr, gauss(seed, i) * R * 0.02, Math.sin(ang) * rr);
                    float hot = (float) u;
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.05), 0F, 1F, 0.5F + 0.4F * hot, 0.3F + 0.6F * hot, 0.22F * vis);
                }
            }
            case 92 -> {
                // FRB 20121102A: a source in a small galaxy that keeps firing bursts of radio waves, each sweeping down in pitch.
                smudge(c, rot, R * 0.8, 0.8, 0.8F, 0.85F, 1F, 0.15F * vis, seed, px, t);
                double ph = (t * 0.25) % 1.0;
                float burst = (float) Math.exp(-ph * 30);
                dot(c, Math.max(px * 3, R * (0.05 + 0.5 * burst)), 0.8F, 0.5F, 1F, (0.5F + burst) * vis);
                for (int k = 0; k < 4; k++) {
                    double life = (ph + k * 0.08) % 1.0;
                    shell(c, rot, R * (0.1 + 3 * life), 0.02, 80, 0.75F - 0.15F * k, 0.4F, 1F, (float) (0.2 * (1 - life)) * vis, seed + k, px, R * 0.04, ADD);
                }
            }
            case 93 -> {
                // The CMB Cold Spot: the afterglow of the Big Bang, mottled by tiny temperature differences; one region unusually cold.
                for (int i = 0; i < 2200; i++) {
                    double z = h(seed, i) * 2 - 1, ph = h(seed, i + 3000) * Math.PI * 2, s = Math.sqrt(1 - z * z);
                    double[] n = {s * Math.cos(ph), z, s * Math.sin(ph)};
                    double temp = Math.sin(n[0] * 9 + n[1] * 4) * Math.cos(n[2] * 8 - n[0] * 3) + 0.6 * Math.sin(n[1] * 17 + n[2] * 11);
                    double cold = Math.exp(-((n[0] - 0.7) * (n[0] - 0.7) + n[1] * n[1] + (n[2] - 0.7) * (n[2] - 0.7)) * 8);
                    temp -= cold * 2.2;
                    float w = (float) Math.max(-1, Math.min(1, temp * 0.6));
                    double[] q = at(c, sub(rot, 0, 1, 0, t * 0.01), n[0] * R * 1.6, n[1] * R * 1.6, n[2] * R * 1.6);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.09), 0F, 0.5F + 0.5F * Math.max(0, w), 0.45F, 0.5F + 0.5F * Math.max(0, -w),
                            0.18F * vis);
                }
            }
            case 94 -> {
                // A dark matter halo (shown here only as an illustration: dark matter gives no light): a vast ball round a galaxy, with clumps.
                Galaxies.Look mw = Galaxies.look("andromeda");
                if (mw != null) {
                    Galaxies.draw(mw, cx, cy, cz, R * 0.5, Galaxies.frame(mw, rot), t, px, vis * 0.8F);
                }
                shell(c, rot, R * 1.8, 0.5, 500, 0.55F, 0.35F, 0.9F, 0.035F * vis, seed, px, R * 0.4, ADD);
                for (int i = 0; i < 18; i++) {
                    double[] q = at(c, rot, gauss(seed, i + 20) * R * 1.3, gauss(seed, i + 40) * R * 1.3, gauss(seed, i + 60) * R * 1.3);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], R * 0.25, 0F, 0.6F, 0.4F, 1F, 0.08F * vis);
                }
            }
            case 95 -> {
                // Abell 222 and 223: two galaxy clusters joined by a filament of dark matter (illustrated) and hot gas.
                for (int s = -1; s <= 1; s += 2) {
                    double[] q = at(c, rot, s * R * 1.6, 0, 0);
                    for (int i = 0; i < 80; i++) {
                        double[] g = at(q, rot, gauss(seed, i + s * 500) * R * 0.35, gauss(seed, i + s * 600) * R * 0.35, gauss(seed, i + s * 700) * R * 0.35);
                        Space3D.billboard(GLOW, ADD, g[0], g[1], g[2], Math.max(px * 1.5, R * 0.03), 0F, 1F, 0.86F, 0.68F, 0.5F * vis);
                    }
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], R * 0.9, 0F, 0.6F, 0.5F, 1F, 0.12F * vis);
                }
                strand(c, rot, new double[][]{{-R * 1.5, 0, 0}, {0, R * 0.15, 0}, {R * 1.5, 0, 0}}, R * 0.35, 0.55F, 0.4F, 1F, 0.035F * vis, ADD, px, seed, 90);
            }
            case 96 -> {
                // Smith's Cloud: a cloud of hydrogen plunging back into the Milky Way's disc, shaped like a comet.
                for (int i = 0; i < 260; i++) {
                    double u = h(seed, i);
                    double[] q = at(c, rot, R * (-0.6 + u * 3), gauss(seed, i + 300) * R * 0.25 * (0.4 + u), gauss(seed, i + 600) * R * 0.25 * (0.4 + u));
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], Math.max(px * 2, R * 0.2 * (0.6 + u)), i, 0.45F, 0.75F, 1F, (float) (0.09 * (1 - u * 0.75)) * vis);
                }
                Space3D.billboard(GLOW, ADD, at(c, rot, -R * 0.55, 0, 0)[0], at(c, rot, -R * 0.55, 0, 0)[1], at(c, rot, -R * 0.55, 0, 0)[2], R * 0.6, 0F, 0.6F,
                        0.85F, 1F, 0.15F * vis);
            }
            case 97 -> {
                // NGC 3079: a galaxy seen edge on, a superbubble of hot gas rising out of its centre.
                double[] edge = sub(rot, 1, 0.02, 0, 0);
                disk(c, edge, R * 0.05, R * 2.2, 1200, 1F, 0.88F, 0.7F, 0.75F, 0.8F, 1F, 0.12F * vis, t, 0.05, 0.05, seed, px, ADD, R * 0.08);
                disk(c, edge, R * 0.1, R * 2.0, 400, 0.05F, 0.04F, 0.03F, 0.05F, 0.04F, 0.03F, 0.4F * vis, t, 0.05, 0.012, seed + 1, px, NORMAL, R * 0.07);
                for (int k = 0; k < 9; k++) {
                    double a = (k / 8.0 - 0.5) * 1.6;
                    double[][] tower = {{0, 0, 0}, {Math.sin(a) * R * 0.3, R * 0.6, 0}, {Math.sin(a) * R * 0.55, R * 1.1 + R * 0.05 * Math.sin(t * 0.3 + k), 0}};
                    strand(c, rot, tower, R * 0.05, 1F, 0.45F, 0.4F, 0.12F * vis, ADD, px, seed + k, 30);
                }
            }
            case 98 -> {
                // W44: a supernova remnant crashing into a molecular cloud, a source of cosmic rays streaking away.
                shell(c, rot, R * 1.0, 0.15, 300, 0.7F, 0.75F, 1F, 0.08F * vis, seed, px, R * 0.1, ADD);
                puffs(at(c, rot, R * 1.3, 0, 0), rot, R * 0.5, R * 0.6, R * 0.6, 80, 0.06F, 0.05F, 0.05F, 0.5F * vis, seed + 1, NORMAL, px, t, R * 0.35);
                for (int i = 0; i < 70; i++) {
                    double life = (t * 0.6 + h(seed, i)) % 1.0;
                    double z = h(seed, i + 100) * 2 - 1, ph = h(seed, i + 200) * Math.PI * 2, s = Math.sqrt(1 - z * z);
                    double d0 = R * (1 + life * 2.5), d1 = d0 + R * 0.25;
                    double[] a = at(c, rot, s * Math.cos(ph) * d0, z * d0, s * Math.sin(ph) * d0), b = at(c, rot, s * Math.cos(ph) * d1, z * d1,
                            s * Math.sin(ph) * d1);
                    Space3D.ribbon(GLOW, ADD, a[0], a[1], a[2], b[0], b[1], b[2], Math.max(px * 0.5, R * 0.006), Math.max(px * 0.5, R * 0.004), 0.75F, 0.85F,
                            1F, 0F, (float) (0.5 * (1 - life)) * vis);
                }
            }
            case 99 -> {
                // GW170817: two neutron stars spiralling together, sending out ripples of spacetime, then a kilonova.
                double cyc = (t * 0.05) % 1.0;
                double sep = R * Math.max(0.05, Math.pow(1 - cyc / 0.8, 0.25)) * 0.8;
                double ph = cyc < 0.8 ? -Math.pow(1 - cyc / 0.8, 0.625) * 120 : 0;
                if (cyc < 0.8) {
                    for (int s = -1; s <= 1; s += 2) {
                        double[] q = at(c, rot, s * Math.cos(ph) * sep, 0, s * Math.sin(ph) * sep);
                        star(q, rot, R * 0.06, 0.8F, 0.85F, 1F, 2F, t, px, vis, seed + s, false);
                    }
                    for (int k = 0; k < 6; k++) {
                        double rad = R * (0.2 + ((t * 0.3 + k / 6.0) % 1.0) * 2.5);
                        disk(c, rot, rad, rad * 1.01, 80, 0.5F, 0.75F, 1F, 0.5F, 0.75F, 1F, (float) (0.12 * (1 - rad / (R * 2.7)) * (0.3 + cyc)) * vis, 0, 0, 0,
                                seed + k, px, ADD, R * 0.02);
                    }
                } else {
                    double k = (cyc - 0.8) / 0.2;
                    dot(c, R * (0.3 + 0.8 * k), 1F, 0.9F, 0.8F, (float) (1 - k * 0.5) * vis);
                    shell(c, rot, R * (0.2 + 1.8 * k), 0.15, 260, 1F, 0.4F + 0.3F * (float) (1 - k), 0.25F, (float) (0.25 * (1 - k * 0.6)) * vis, seed, px,
                            R * 0.12, ADD);
                }
            }
            default -> dot(c, Math.max(px * 3, R * 0.2), 1F, 1F, 1F, vis);
        }
    }

    /** A planet with its star off to one side, the star `far` radii away; returns the light direction. */
    private static double[] planetWithStar(double[] c, double[] rot, double r, ResourceLocation tex, float cr, float cg, float cb, float glow, float sr,
                                           float sg, float sb, double far, double t, double px, float vis, float ar, float ag, float ab, float air, long seed) {
        double[] sp = at(c, rot, r * far * 1.2, r * 0.2, -r * far * 0.4);
        star(sp, rot, r * 0.6, sr, sg, sb, 1.1F, t, px, vis, seed, true);
        double[] light = dirTo(c, sp);
        world(c, rot, r, tex, cr, cg, cb, glow, light, t, px, vis, ar, ag, ab, air);
        return light;
    }

    /** Glowing plumes and lava on a volcanic world. */
    private static void volcanoes(double[] c, double[] rot, double r, double[] light, double t, double px, float vis, long seed) {
        double[] sr = sub(rot, 0, 1, 0, t * 0.04);
        for (int i = 0; i < 9; i++) {
            double z = h(seed, i * 3L) * 1.6 - 0.8, ph = h(seed, i * 3L + 1) * Math.PI * 2, s = Math.sqrt(1 - z * z);
            double[] n = vec(sr, s * Math.cos(ph), z, s * Math.sin(ph));
            double[] base = {c[0] + n[0] * r, c[1] + n[1] * r, c[2] + n[2] * r};
            double l = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]) + 1.0E-12;
            if (n[0] * c[0] / l + n[1] * c[1] / l + n[2] * c[2] / l > 0.2) {
                continue;
            }
            Space3D.billboard(GLOW, ADD, base[0], base[1], base[2], Math.max(px * 1.5, r * 0.07), 0F, 1F, 0.45F, 0.1F, 0.6F * vis);
            float puff = 0.6F + 0.4F * wave(t * 0.5 + i);
            double hgt = r * 0.25 * puff;
            double[] top = {base[0] + n[0] * hgt, base[1] + n[1] * hgt, base[2] + n[2] * hgt};
            Space3D.ribbon(GLOW, ADD, base[0], base[1], base[2], top[0], top[1], top[2], Math.max(px, r * 0.02), Math.max(px, r * 0.08), 0.95F, 0.85F, 0.6F,
                    0.25F * vis, 0.02F * vis);
        }
    }

    /** Streaks of a star's wind blowing outwards. */
    private static void windStreaks(double[] c, double[] rot, double r0, double r1, int n, float cr, float cg, float cb, float a, long seed, double t,
                                    double px, double speed) {
        for (int i = 0; i < n; i++) {
            double life = (t * speed * 0.2 + h(seed, i * 3L)) % 1.0;
            double z = h(seed, i * 3L + 1) * 2 - 1, ph = h(seed, i * 3L + 2) * Math.PI * 2, s = Math.sqrt(1 - z * z);
            double d0 = r0 + (r1 - r0) * life, d1 = d0 + (r1 - r0) * 0.12;
            double[] p0 = at(c, rot, s * Math.cos(ph) * d0, z * d0, s * Math.sin(ph) * d0), p1 = at(c, rot, s * Math.cos(ph) * d1, z * d1, s * Math.sin(ph) * d1);
            Space3D.ribbon(GLOW, ADD, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], Math.max(px * 0.6, r0 * 0.02), Math.max(px * 0.6, r0 * 0.015), cr, cg, cb,
                    (float) (a * (1 - life)), 0F);
        }
    }
}
