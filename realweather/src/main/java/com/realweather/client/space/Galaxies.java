package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

import static com.realweather.client.space.Space3D.ADD;
import static com.realweather.client.space.Space3D.NORMAL;

/**
 * Fifteen real galaxies, each built in 3D from its own structure: grand-design and flocculent spirals with their arms,
 * bars and bulges, young blue stars and pink star-forming regions along the arms and dark dust lanes on their inner
 * edges; giant ellipticals with their halos of globular clusters and jets; the Sombrero's huge bulge and edge-on dust
 * ring; Centaurus A's warped dust lane and jets; the Cigar's starburst blowing a red wind out of both sides; the
 * Cartwheel's rings and spokes after a collision; the Magellanic Clouds' clumpy irregular shapes. From far off a
 * glow; closer its structure; inside it, its stars all round you, and close to one of them a star system of its own
 * (those are generated, not catalogued: shown as simulated). Lengths in the galaxy's radius; the disc in x and z,
 * its axis along y.
 */
public final class Galaxies {
    private static final ResourceLocation STAR_MAP = RealWeather.id("textures/environment/space3d/star_surface.png");
    static final int SPIRAL = 0, BARRED = 1, ELLIPTICAL = 2, LENTICULAR = 3, IRREGULAR = 4, RING = 5, STARBURST = 6, PECULIAR = 7;

    /**
     * A galaxy: its shape, arms, how tightly wound (degrees), bulge and bar size, thickness, dust, star-forming
     * regions, the tilt it is seen at from Earth (degrees), its real diameter (light years), and its features.
     */
    record Look(String id, int type, int arms, double pitch, double bulge, double bar, double thick, double dust, double hii, double tilt,
                double diameterLy, boolean jet, boolean companion) {}

    private static final Map<String, Look> LOOKS = new HashMap<>();
    private static final Map<String, float[]> MODELS = new HashMap<>();

    private static void add(Look l) {
        LOOKS.put(l.id, l);
    }

    static {
        add(new Look("milky_way", BARRED, 4, 12, 0.12, 0.28, 0.02, 1.0, 1.0, 60, 100000, false, false));
        add(new Look("andromeda", SPIRAL, 2, 9, 0.2, 0.08, 0.025, 0.9, 0.7, 77, 220000, false, true));
        add(new Look("triangulum", SPIRAL, 2, 28, 0.05, 0, 0.03, 0.6, 1.6, 55, 61000, false, false));
        add(new Look("lmc", IRREGULAR, 1, 20, 0.0, 0.3, 0.08, 0.5, 1.4, 35, 32000, false, false));
        add(new Look("smc", IRREGULAR, 0, 0, 0.0, 0.0, 0.15, 0.3, 0.8, 50, 19000, false, false));
        add(new Look("whirlpool", SPIRAL, 2, 18, 0.1, 0.0, 0.02, 1.0, 1.4, 20, 76000, false, true));
        add(new Look("sombrero", LENTICULAR, 0, 0, 0.45, 0, 0.012, 1.4, 0.2, 84, 50000, false, false));
        add(new Look("pinwheel", SPIRAL, 5, 22, 0.06, 0, 0.02, 0.8, 1.8, 10, 170000, false, false));
        add(new Look("centaurus_a", PECULIAR, 0, 0, 0.6, 0, 0.05, 1.5, 0.6, 30, 60000, true, false));
        add(new Look("m87", ELLIPTICAL, 0, 0, 1.0, 0, 0.9, 0.0, 0.0, 0, 120000, true, false));
        add(new Look("sculptor", SPIRAL, 3, 16, 0.12, 0.15, 0.03, 1.3, 1.5, 76, 90000, false, false));
        add(new Look("black_eye", SPIRAL, 2, 10, 0.2, 0, 0.02, 1.6, 0.5, 60, 54000, false, false));
        add(new Look("cigar", STARBURST, 0, 0, 0.1, 0, 0.06, 1.2, 2.0, 82, 37000, false, false));
        add(new Look("bodes", SPIRAL, 2, 14, 0.22, 0, 0.02, 0.9, 0.8, 59, 90000, false, false));
        add(new Look("cartwheel", RING, 0, 0, 0.08, 0, 0.03, 0.4, 2.2, 40, 150000, false, true));
    }

    private Galaxies() {}

    static Look look(String id) {
        return LOOKS.get(id);
    }

    private static double gauss(long seed, long k) {
        double u1 = Math.max(1.0E-9, Space3D.h(seed, k)), u2 = Space3D.h(seed, k + 7919);
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(Math.PI * 2 * u2);
    }

    /** How densely packed with stars the galaxy is at a point (its radius 1), roughly. */
    static double density(Look g, double x, double y, double z) {
        double r = Math.sqrt(x * x + z * z);
        if (g.type == ELLIPTICAL) {
            double rr = Math.sqrt(x * x + y * y * 1.4 + z * z);
            return Math.exp(-rr * 5);
        }
        double disc = Math.exp(-r * 3.2) * Math.exp(-Math.abs(y) / Math.max(0.005, g.thick));
        double bulge = g.bulge > 0 ? Math.exp(-Math.sqrt(x * x + y * y * 1.8 + z * z) / Math.max(0.02, g.bulge * 0.4)) * 2 : 0;
        return disc + bulge;
    }

    /** The galaxy's particles: x, y, z, size, r, g, b, alpha, kind (0 star, 1 glowing gas, 2 dust). */
    private static float[] model(Look g) {
        float[] m = MODELS.get(g.id);
        if (m != null) {
            return m;
        }
        long seed = g.id.hashCode();
        int stars = 5200, gas = 700, dust = 650;
        m = new float[(stars + gas + dust) * 9];
        int i = 0;
        for (int k = 0; k < stars + gas + dust; k++) {
            int kind = k < stars ? 0 : k < stars + gas ? 1 : 2;
            double[] p = place(g, seed, k, kind);
            float size, r, gg, b, a;
            double rad = Math.sqrt(p[0] * p[0] + p[2] * p[2]);
            boolean young = p[3] > 0.5;
            if (kind == 0) {
                size = (float) (0.006 + 0.01 * Space3D.h(seed, k * 13L + 5));
                if (young) {
                    r = 0.62F;
                    gg = 0.76F;
                    b = 1F;
                } else if (rad < g.bulge * 1.2 || g.type == ELLIPTICAL || g.type == LENTICULAR && rad < 0.6) {
                    r = 1F;
                    gg = 0.82F;
                    b = 0.56F;
                } else {
                    r = 1F;
                    gg = 0.93F;
                    b = 0.82F;
                }
                a = 0.5F + 0.4F * Space3D.h(seed, k * 13L + 6);
            } else if (kind == 1) {
                size = (float) (0.012 + 0.03 * Space3D.h(seed, k * 13L + 5));
                boolean wind = g.type == STARBURST && p[3] > 1.5;
                r = 1F;
                gg = wind ? 0.32F : 0.4F;
                b = wind ? 0.36F : 0.58F;
                a = wind ? 0.12F : 0.22F;
            } else {
                size = (float) (0.02 + 0.035 * Space3D.h(seed, k * 13L + 5));
                r = 0.08F;
                gg = 0.05F;
                b = 0.03F;
                a = (float) Math.min(0.75, 0.35 * g.dust);
            }
            m[i++] = (float) p[0];
            m[i++] = (float) p[1];
            m[i++] = (float) p[2];
            m[i++] = size;
            m[i++] = r;
            m[i++] = gg;
            m[i++] = b;
            m[i++] = a;
            m[i++] = kind;
        }
        MODELS.put(g.id, m);
        return m;
    }

    /** Where particle k goes (x, y, z, and whether it is young / in the wind). */
    private static double[] place(Look g, long seed, int k, int kind) {
        double u = Space3D.h(seed, k * 13L), v = Space3D.h(seed, k * 13L + 1);
        double x, y, z, tag = 0;
        switch (g.type) {
            case ELLIPTICAL -> {
                // A huge round ball of old stars, with globular clusters in its halo.
                double r = 0.9 * Math.pow(u, 1.6);
                double th = Math.acos(1 - 2 * v), ph = Space3D.h(seed, k * 13L + 2) * Math.PI * 2;
                x = r * Math.sin(th) * Math.cos(ph);
                y = r * Math.cos(th) * 0.85;
                z = r * Math.sin(th) * Math.sin(ph);
                if (kind == 2) {
                    x *= 0.05;
                    y *= 0.05;
                    z *= 0.05;
                }
            }
            case IRREGULAR -> {
                // Clumps of stars and glowing gas strewn about, the LMC with its off-centre bar.
                int clump = (int) (Space3D.h(seed, k * 13L + 3) * 9);
                double cx = (Space3D.h(seed, 50 + clump) - 0.5) * 1.1, cz = (Space3D.h(seed, 70 + clump) - 0.5) * (g.id.equals("smc") ? 0.5 : 1.0);
                if (g.bar > 0 && u < 0.35) {
                    cx = (v - 0.5) * 2 * g.bar * 2;
                    cz = gauss(seed, k * 13L + 4) * 0.06 + 0.1;
                } else {
                    double s = 0.12 + 0.18 * Space3D.h(seed, 90 + clump);
                    cx += gauss(seed, k * 13L + 4) * s;
                    cz += gauss(seed, k * 13L + 5) * s;
                }
                x = cx;
                z = cz;
                y = gauss(seed, k * 13L + 6) * g.thick;
                tag = Space3D.h(seed, k * 13L + 8) < 0.45 ? 1 : 0;
                // The Tarantula: the brightest star-forming region of the Local Group.
                if (kind == 1 && g.id.equals("lmc") && u < 0.3) {
                    x = 0.35 + gauss(seed, k * 13L + 9) * 0.04;
                    z = 0.22 + gauss(seed, k * 13L + 10) * 0.04;
                }
            }
            case RING -> {
                // The Cartwheel: a bright outer ring of new stars racing outwards, an inner ring, spokes between.
                double which = Space3D.h(seed, k * 13L + 3);
                double r = which < 0.55 ? 0.82 + gauss(seed, k * 13L + 4) * 0.04 : which < 0.75 ? 0.22 + gauss(seed, k * 13L + 4) * 0.03
                        : 0.25 + Math.abs(gauss(seed, k * 13L + 4)) * 0.25;
                double ang = which >= 0.75 && which < 0.95 ? Math.PI * 2 * Math.floor(v * 14) / 14 + gauss(seed, k * 13L + 5) * 0.03 : v * Math.PI * 2;
                x = Math.cos(ang) * r;
                z = Math.sin(ang) * r;
                y = gauss(seed, k * 13L + 6) * g.thick;
                tag = which < 0.55 ? 1 : 0;
            }
            default -> {
                double bulgeShare = g.type == LENTICULAR ? 0.55 : g.type == PECULIAR ? 0.7 : Math.min(0.45, g.bulge * 1.6);
                if (kind == 0 && u < bulgeShare) {
                    // The bulge: old yellow stars in a squashed ball.
                    double r = g.bulge * Math.pow(Space3D.h(seed, k * 13L + 2), 1.8) * (g.type == PECULIAR ? 1.4 : 1);
                    double th = Math.acos(1 - 2 * v), ph = Space3D.h(seed, k * 13L + 3) * Math.PI * 2;
                    x = r * Math.sin(th) * Math.cos(ph);
                    y = r * Math.cos(th) * (g.type == LENTICULAR ? 0.6 : 0.7);
                    z = r * Math.sin(th) * Math.sin(ph);
                } else if (g.bar > 0 && kind == 0 && u < bulgeShare + 0.12) {
                    x = (v - 0.5) * 2 * g.bar;
                    z = gauss(seed, k * 13L + 4) * g.bar * 0.18;
                    y = gauss(seed, k * 13L + 5) * g.thick;
                } else {
                    // The disc, its stars crowded into the arms (the young ones most of all).
                    double r = Math.min(1.1, -0.36 * Math.log(Math.max(1.0E-4, 1 - 0.95 * Space3D.h(seed, k * 13L + 2))));
                    if (g.type == LENTICULAR || g.type == PECULIAR) {
                        r = 0.35 + 0.55 * Math.sqrt(Space3D.h(seed, k * 13L + 2));
                    }
                    double ang;
                    boolean inArm = g.arms > 0 && (kind != 0 || Space3D.h(seed, k * 13L + 3) < 0.7);
                    if (inArm) {
                        int arm = (int) (Space3D.h(seed, k * 13L + 4) * g.arms);
                        double start = Math.max(g.bar, 0.06);
                        double wind = Math.log(Math.max(r, start) / start) / Math.tan(Math.toRadians(g.pitch));
                        double spread = (kind == 0 ? 0.28 : 0.12) * (1 - Math.min(0.6, r * 0.4));
                        ang = Math.PI * 2 * arm / g.arms + wind + gauss(seed, k * 13L + 5) * spread;
                        // Dust lanes on the inside edge of the arms.
                        if (kind == 2) {
                            ang -= 0.18;
                        }
                        tag = kind == 0 && Space3D.h(seed, k * 13L + 6) < 0.5 ? 1 : 0;
                    } else {
                        ang = v * Math.PI * 2;
                    }
                    x = Math.cos(ang) * r;
                    z = Math.sin(ang) * r;
                    y = gauss(seed, k * 13L + 7) * g.thick * (1 + r);
                    if (g.type == LENTICULAR && kind == 2) {
                        // The Sombrero's dark ring of dust round its rim.
                        double rr = 0.78 + gauss(seed, k * 13L + 8) * 0.04;
                        x = Math.cos(ang) * rr;
                        z = Math.sin(ang) * rr;
                        y = gauss(seed, k * 13L + 9) * 0.008;
                    }
                    if (g.type == PECULIAR && kind == 2) {
                        // Centaurus A's warped lane of dust across its middle.
                        double t = (v - 0.5) * 1.6;
                        x = t;
                        z = gauss(seed, k * 13L + 8) * 0.06;
                        y = gauss(seed, k * 13L + 9) * 0.05 + 0.08 * Math.sin(t * 2.5);
                    }
                    if (g.id.equals("black_eye") && kind == 2 && u < 0.6) {
                        // The black eye: a band of dust in front of its nucleus.
                        double rr = 0.18 + gauss(seed, k * 13L + 8) * 0.04;
                        x = Math.cos(ang) * rr;
                        z = Math.sin(ang) * rr;
                        y = gauss(seed, k * 13L + 9) * 0.01;
                    }
                }
                if (g.type == STARBURST && kind == 1 && u < 0.6) {
                    // M82's superwind: red filaments blown out of both sides of its disc.
                    double side = v < 0.5 ? 1 : -1;
                    double h = Math.pow(Space3D.h(seed, k * 13L + 10), 0.7) * 0.9;
                    x = gauss(seed, k * 13L + 11) * 0.12 * (1 + h);
                    z = gauss(seed, k * 13L + 12) * 0.12 * (1 + h);
                    y = side * h;
                    tag = 2;
                }
            }
        }
        return new double[]{x, y, z, tag};
    }

    /**
     * Its frame: `base` turns a frame with x and y across the line of sight and z towards us into view space; the
     * galaxy is tipped to its real inclination (0 face-on) and spun to its own angle.
     */
    static double[] frame(Look g, double[] base) {
        double t = Math.PI / 2 - Math.toRadians(g.tilt), s = (g.id.hashCode() % 628) * 0.01;
        double[] rx = {1, 0, 0, 0, Math.cos(t), Math.sin(t), 0, -Math.sin(t), Math.cos(t)};
        double[] ry = {Math.cos(s), 0, -Math.sin(s), 0, 1, 0, Math.sin(s), 0, Math.cos(s)};
        double[] local = new double[9], out = new double[9];
        Space3D.mul(rx, ry, local);
        Space3D.mul(base, local, out);
        return out;
    }

    /**
     * Draws a galaxy at c (view), radius R (view units), its frame rot (galaxy to view), with the camera at `cam`
     * (galaxy frame, radii; for the stars close round you), view units per pixel at its centre, how strongly.
     */
    static void draw(Look g, double cx, double cy, double cz, double R, double[] rot, double seconds, double px, float vis) {
        if (vis <= 0.01F) {
            return;
        }
        double rPix = R / px;
        // From afar: its overall glow.
        Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, Math.max(px * 6, R * (g.type == ELLIPTICAL ? 1.2 : 0.9)), 0F, 1F, 0.9F, 0.76F,
                (float) (0.35 * vis * (1 - Space3D.smooth(30F, 400F, (float) rPix) * 0.6)));
        Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, Math.max(px * 3, R * Math.max(0.15, g.bulge * 0.8)), 0F, 1F, 0.86F, 0.62F, 0.6F * vis);
        if (rPix < 6) {
            return;
        }
        float detail = Space3D.smooth(6F, 40F, (float) rPix) * vis;
        float[] m = model(g);
        int n = m.length / 9;
        // Fewer particles while it is small on the screen.
        int step = rPix > 300 ? 1 : rPix > 80 ? 2 : 4;
        // It turns, very slowly (the outer parts going round in a galactic year).
        double turn = seconds * 0.0015;
        double ct = Math.cos(turn), st = Math.sin(turn);
        double[] q = new double[3];
        double camDist = Math.sqrt(cx * cx + cy * cy + cz * cz);
        for (int i = 0; i < n; i += step) {
            int o = i * 9;
            int kind = (int) m[o + 8];
            double x = m[o], y = m[o + 1], z = m[o + 2];
            double r = Math.sqrt(x * x + z * z);
            if (g.type != ELLIPTICAL && r > 0.02) {
                double sp = Math.min(1, 0.1 / r);
                double c = Math.cos(turn * sp * 10), s = Math.sin(turn * sp * 10);
                double nx = x * c - z * s;
                z = x * s + z * c;
                x = nx;
            } else {
                double nx = x * ct - z * st;
                z = x * st + z * ct;
                x = nx;
            }
            Space3D.apply(rot, x * R, y * R, z * R, q);
            double qx = cx + q[0], qy = cy + q[1], qz = cz + q[2];
            if (qz > 0) {
                continue;
            }
            double d = Math.sqrt(qx * qx + qy * qy + qz * qz);
            double pxHere = px * d / Math.max(1.0E-9, camDist);
            double size = Math.max(pxHere * (kind == 0 ? 1.4 : 2), m[o + 3] * R * (kind == 0 ? 1 : 1.6));
            float a = m[o + 7] * detail * (float) Math.min(1, step * 0.8);
            if (kind == 2) {
                Space3D.billboard(Space3D.GLOW, NORMAL, qx, qy, qz, size, i, m[o + 4], m[o + 5], m[o + 6], a);
            } else {
                Space3D.billboard(Space3D.GLOW, ADD, qx, qy, qz, size, 0F, m[o + 4], m[o + 5], m[o + 6], a * (kind == 0 ? 0.55F : 1F));
            }
        }
        // Its nucleus.
        Space3D.billboard(Space3D.STAR, ADD, cx, cy, cz, Math.max(px * 6, R * 0.06), 0F, 1F, 0.92F, 0.75F, 0.7F * vis);
        if (g.jet) {
            double[] axis = {rot[3], rot[4], rot[5]};
            Jets.draw(cx, cy, cz, axis, R * (g.type == ELLIPTICAL ? 0.45 : 0.9), R * 0.012, 0.6F, 0.75F, 1F, 0.95, g.id.hashCode(), seconds, px, vis);
        }
        if (g.companion) {
            // A smaller galaxy beside it (M51's NGC 5195, Andromeda's M32, the Cartwheel's intruders).
            double[] off = new double[3];
            Space3D.apply(rot, R * 0.95, R * 0.05, R * 0.55, off);
            Space3D.billboard(Space3D.GLOW, ADD, cx + off[0], cy + off[1], cz + off[2], R * 0.16, 0F, 1F, 0.85F, 0.6F, 0.5F * vis);
            Space3D.billboard(Space3D.STAR, ADD, cx + off[0], cy + off[1], cz + off[2], R * 0.06, 0F, 1F, 0.9F, 0.7F, 0.5F * vis);
        }
    }

    // ------------------------------------------------------------------------------------------- inside a galaxy

    /** The simulated star system nearest the camera (for the screen): its name, type and planets; or null. */
    public static String nearest;
    public static double nearestDist;

    /**
     * Inside a galaxy: the stars round you, generated cell by cell from how crowded the galaxy is there (none of
     * them catalogued), and close to one, its own planets. cam: the camera (galaxy frame, radii); rot: galaxy to view.
     */
    static void local(Look g, double[] cam, double[] rot, double R, double seconds, double pixAng) {
        nearest = null;
        nearestDist = Double.MAX_VALUE;
        double cell = 0.004;
        long seed = g.id.hashCode() * 31L;
        int ci = (int) Math.floor(cam[0] / cell), cj = (int) Math.floor(cam[1] / cell), ck = (int) Math.floor(cam[2] / cell);
        double[] q = new double[3];
        double[] best = null;
        long bestKey = 0;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                for (int k = -2; k <= 2; k++) {
                    long key = ((long) (ci + i) * 73856093L) ^ ((long) (cj + j) * 19349663L) ^ ((long) (ck + k) * 83492791L) ^ seed;
                    double ccx = (ci + i + 0.5) * cell, ccy = (cj + j + 0.5) * cell, ccz = (ck + k + 0.5) * cell;
                    int count = (int) Math.min(6, density(g, ccx, ccy, ccz) * 3 + Space3D.h(key, 0));
                    for (int s = 0; s < count; s++) {
                        double sx = (ci + i + Space3D.h(key, s * 3L + 1)) * cell, sy = (cj + j + Space3D.h(key, s * 3L + 2)) * cell,
                                sz = (ck + k + Space3D.h(key, s * 3L + 3)) * cell;
                        double dx = sx - cam[0], dy = sy - cam[1], dz = sz - cam[2];
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        Space3D.apply(rot, dx * R, dy * R, dz * R, q);
                        float h = Space3D.h(key, s * 3L + 50);
                        float[] col = h < 0.1 ? new float[]{0.65F, 0.76F, 1F} : h < 0.4 ? new float[]{1F, 0.95F, 0.85F} : h < 0.7
                                ? new float[]{1F, 0.8F, 0.55F} : new float[]{1F, 0.55F, 0.35F};
                        if (q[2] < 0) {
                            double sz2 = Math.max(pixAng * d * R * 2.5, 0);
                            Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], sz2, 0F, col[0], col[1], col[2], (float) Math.min(1, 0.4 + 0.004 / Math.max(d, 1.0E-4)));
                        }
                        if (d < nearestDist) {
                            nearestDist = d;
                            best = new double[]{dx, dy, dz, col[0], col[1], col[2], h};
                            bestKey = key * 7 + s;
                        }
                    }
                }
            }
        }
        if (best == null) {
            return;
        }
        long id = Math.abs(bestKey % 1000000L);
        String type = best[6] < 0.1 ? "B" : best[6] < 0.4 ? "G" : best[6] < 0.7 ? "K" : "M";
        int planets = (int) (Space3D.h(bestKey, 9) * 7);
        nearest = String.format(java.util.Locale.ROOT, "SIM-%06d (%s, %d)", id, type, planets);
        // Close up: the star itself and its planets.
        double starR = 0.0000025 * R * (best[6] < 0.1 ? 3 : 1);
        double[] c = new double[3];
        Space3D.apply(rot, best[0] * R, best[1] * R, best[2] * R, c);
        double dist = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        if (starR / Math.max(1.0E-12, dist * pixAng) < 0.3) {
            return;
        }
        Space3D.clearDepth();
        Space3D.range((float) Math.max(1.0E-5, dist * 0.02), (float) (dist + starR * 400));
        double[] srot = Space3D.orient(rot[3], rot[4], rot[5], seconds * 0.02);
        Space3D.sphere(STAR_MAP, c[0], c[1], c[2], starR, srot, 0, 0, 1, 0F, (float) best[3] * 1.3F, (float) best[4] * 1.3F, (float) best[5] * 1.3F, 0F, 1F,
                true, (float) (seconds * 0.002 % 1.0), 32);
        Space3D.billboard(Space3D.GLOW, ADD, c[0], c[1], c[2], starR * 6, 0F, (float) best[3], (float) best[4], (float) best[5], 0.7F);
        String[] maps = {"mars", "jupiter", "neptune", "venus", "kepler22b", "toi700d", "hd189733b", "mercury", "kepler186f", "wasp76b"};
        double[] pl = new double[3];
        for (int p = 0; p < planets; p++) {
            long ps = bestKey * 13 + p;
            double a = starR * (6 + 6 * p + 4 * Space3D.h(ps, 1));
            double ang = Space3D.h(ps, 2) * Math.PI * 2 + seconds * 0.05 / (1 + p);
            Space3D.apply(rot, Math.cos(ang) * a, 0, Math.sin(ang) * a, pl);
            double prad = starR * (0.08 + 0.15 * Space3D.h(ps, 3));
            double lx = -pl[0], ly = -pl[1], lz = -pl[2];
            double ll = Math.sqrt(lx * lx + ly * ly + lz * lz) + 1.0E-15;
            Space3D.sphere(RealWeather.id("textures/environment/space3d/" + maps[(int) (Space3D.h(ps, 4) * maps.length)] + ".png"), c[0] + pl[0],
                    c[1] + pl[1], c[2] + pl[2], prad, srot, lx / ll, ly / ll, lz / ll, 0.02F, 1F, 1F, 1F, 0F, 1F, false, (float) (seconds * 0.01 % 1.0), 24);
        }
    }
}
