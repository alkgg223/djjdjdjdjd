package com.realweather.client.space;

import static com.realweather.client.space.Space3D.ADD;

/**
 * What a star's surface throws up, in 3D and turning with it: prominences (great arches of cooler, glowing red plasma
 * held up by the magnetic field, draining back down along themselves, now and then erupting outwards), spicules (a
 * grass of thin jets all over the surface, shooting up and falling back in minutes, seen best at the limb), and
 * coronal loops (bright, thin, nested arcs of million-degree gas tracing the field over active regions, flickering).
 * Plus the boiling granulated face itself, ray traced.
 */
final class SolarActivity {
    private SolarActivity() {}

    /** The active regions (sunspot groups) of a star, in its own frame: direction and size, for the face and the loops. */
    static float[] spots(long seed) {
        float[] s = new float[16];
        for (int i = 0; i < 4; i++) {
            double lat = (Space3D.h(seed, i * 7L + 1) - 0.5) * 0.9 * (i % 2 == 0 ? 1 : -1), lon = Space3D.h(seed, i * 7L + 2) * Math.PI * 2;
            s[i * 4] = (float) (Math.cos(lat) * Math.cos(lon));
            s[i * 4 + 1] = (float) Math.sin(lat);
            s[i * 4 + 2] = (float) (Math.cos(lat) * Math.sin(lon));
            s[i * 4 + 3] = (float) (0.025 + 0.035 * Space3D.h(seed, i * 7L + 3));
        }
        return s;
    }

    /**
     * A star's face and activity. c: centre (view), r: radius (view units), rot: the star's frame (turning) to view,
     * its colour, seconds, view units per pixel there, and how strongly to draw it.
     */
    static void draw(double cx, double cy, double cz, double r, double[] rot, float cr, float cg, float cb, long seed, double seconds, double px,
                     float vis) {
        if (vis <= 0.01F) {
            return;
        }
        double rPix = r / px;
        float[] spots = spots(seed);
        // The boiling face.
        if (rPix > 12) {
            Trace t = Trace.sphere(cx, cy, cz, r, 1.0, rot);
            if (t != null) {
                double dist = Math.sqrt(cx * cx + cy * cy + cz * cz) + 1.0E-9;
                SpaceShaders.starSurface(cr, cg, cb, 1.3F, (float) Math.min(220, 40 + rPix * 0.15), (float) seconds, (float) (px / dist), spots,
                        vis * Space3D.smooth(12F, 30F, (float) rPix), t.near, t.x0, t.y0, t.x1, t.y1, t.o, t.ox, t.oy, t.d, t.dx, t.dy);
            }
        }
        if (rPix < 25) {
            return;
        }
        float near = Space3D.smooth(25F, 80F, (float) rPix) * vis;
        double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double[] toCam = {-cx / dl, -cy / dl, -cz / dl};
        prominences(cx, cy, cz, r, rot, seed, seconds, px, toCam, near);
        loops(cx, cy, cz, r, rot, spots, seed, seconds, px, toCam, near);
        if (rPix > 140) {
            spicules(cx, cy, cz, r, rot, seed, seconds, px, toCam, near * Space3D.smooth(140F, 300F, (float) rPix));
        }
    }

    private static double[] onStar(double[] rot, double lat, double lon, double rad) {
        double[] out = new double[3];
        Space3D.apply(rot, Math.cos(lat) * Math.cos(lon) * rad, Math.sin(lat) * rad, Math.cos(lat) * Math.sin(lon) * rad, out);
        return out;
    }

    /** How much of a point near the surface shows against the sky (at the limb) rather than over the bright disc. */
    private static float limb(double[] n, double[] toCam) {
        double l = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        double f = (n[0] * toCam[0] + n[1] * toCam[1] + n[2] * toCam[2]) / l;
        if (f < -0.25) {
            return 0F;
        }
        return (float) (0.25 + 0.75 * Math.exp(-f * f * 12));
    }

    private static void prominences(double cx, double cy, double cz, double r, double[] rot, long seed, double t, double px, double[] toCam,
                                    float vis) {
        for (int i = 0; i < 9; i++) {
            long k = seed * 31 + i;
            // Each lives a few minutes, then another rises somewhere else.
            double life = 160 + 120 * Space3D.h(k, 1);
            double age = (t + Space3D.h(k, 2) * life) / life;
            long gen = (long) Math.floor(age);
            double f = age - gen;
            long g = k * 977 + gen;
            double lat = (Space3D.h(g, 3) - 0.5) * 1.6, lon = Space3D.h(g, 4) * Math.PI * 2;
            double span = 0.08 + 0.14 * Space3D.h(g, 5), turn = Space3D.h(g, 6) * Math.PI;
            boolean erupt = Space3D.h(g, 7) < 0.22;
            double height = r * (0.08 + 0.22 * Space3D.h(g, 8)) * (erupt ? 1 + Math.pow(Math.max(0, f - 0.5) * 2, 2) * 6 : 1);
            float a = (float) (Math.min(1, f * 6) * Math.min(1, (1 - f) * 5)) * vis;
            if (a <= 0.01F) {
                continue;
            }
            double la0 = lat + Math.sin(turn) * span * 0.5, lo0 = lon + Math.cos(turn) * span * 0.5;
            double la1 = lat - Math.sin(turn) * span * 0.5, lo1 = lon - Math.cos(turn) * span * 0.5;
            double[] p0 = onStar(rot, la0, lo0, 1), p1 = onStar(rot, la1, lo1, 1), mid = onStar(rot, lat, lon, 1);
            float vis2 = a * limb(mid, toCam);
            if (vis2 <= 0.01F) {
                continue;
            }
            int n = 16;
            double[] prev = null;
            double w = r * (0.014 + 0.014 * Space3D.h(g, 9)) * (erupt ? 1 + f * 2 : 1);
            for (int j = 0; j <= n; j++) {
                double s = (double) j / n;
                double lift = Math.sin(s * Math.PI) * height / r;
                // Along the great circle between the feet, raised above the surface (and a little twisted).
                double ux = p0[0] + (p1[0] - p0[0]) * s, uy = p0[1] + (p1[1] - p0[1]) * s, uz = p0[2] + (p1[2] - p0[2]) * s;
                double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
                double wob = Math.sin(s * 9 + t * 0.05 + i) * 0.015;
                double rr = r * (1 + lift + wob * lift);
                double[] q = {cx + ux / ul * rr, cy + uy / ul * rr, cz + uz / ul * rr};
                if (prev != null) {
                    Space3D.ribbon(Space3D.GLOW, ADD, prev[0], prev[1], prev[2], q[0], q[1], q[2], w, w, 1F, 0.45F, 0.3F, 0.7F * vis2, 0.7F * vis2);
                    Space3D.ribbon(Space3D.GLOW, ADD, prev[0], prev[1], prev[2], q[0], q[1], q[2], w * 3, w * 3, 1F, 0.3F, 0.2F, 0.22F * vis2,
                            0.22F * vis2);
                }
                prev = q;
            }
            // Knots of plasma draining down both legs.
            for (int j = 0; j < 8; j++) {
                double s = (Space3D.h(g, 20 + j) + t * 0.015) % 1.0;
                double leg = j % 2 == 0 ? s * 0.5 : 1 - s * 0.5;
                double lift = Math.sin(leg * Math.PI) * height / r;
                double ux = p0[0] + (p1[0] - p0[0]) * leg, uy = p0[1] + (p1[1] - p0[1]) * leg, uz = p0[2] + (p1[2] - p0[2]) * leg;
                double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
                double rr = r * (1 + lift);
                Space3D.billboard(Space3D.GLOW, ADD, cx + ux / ul * rr, cy + uy / ul * rr, cz + uz / ul * rr, Math.max(px * 1.5, w * 2.2), 0F, 1F,
                        0.55F, 0.35F, 0.45F * vis2);
            }
        }
    }

    private static void loops(double cx, double cy, double cz, double r, double[] rot, float[] spots, long seed, double t, double px,
                              double[] toCam, float vis) {
        for (int a = 0; a < 4; a++) {
            double sx = spots[a * 4], sy = spots[a * 4 + 1], sz = spots[a * 4 + 2];
            double lat = Math.asin(sy), lon = Math.atan2(sz, sx);
            double[] centre = onStar(rot, lat, lon, 1);
            float lv = limb(centre, toCam) * vis;
            if (lv <= 0.01F) {
                continue;
            }
            long g = seed * 131 + a;
            double turn = Space3D.h(g, 1) * Math.PI;
            for (int i = 0; i < 11; i++) {
                double span = 0.03 + 0.012 * i + 0.02 * Space3D.h(g, 10 + i);
                double shift = (i - 5) * 0.006;
                double la0 = lat + Math.sin(turn) * span + Math.cos(turn) * shift, lo0 = lon + Math.cos(turn) * span - Math.sin(turn) * shift;
                double la1 = lat - Math.sin(turn) * span + Math.cos(turn) * shift, lo1 = lon - Math.cos(turn) * span - Math.sin(turn) * shift;
                double[] p0 = onStar(rot, la0, lo0, 1), p1 = onStar(rot, la1, lo1, 1);
                double height = span * (0.55 + 0.25 * Space3D.h(g, 30 + i));
                float flick = (float) (0.55 + 0.45 * Math.sin(t * (0.4 + 0.3 * Space3D.h(g, 40 + i)) + i * 1.7));
                double[] prev = null;
                int n = 12;
                double w = Math.max(px * 0.6, r * 0.0016);
                for (int j = 0; j <= n; j++) {
                    double s = (double) j / n;
                    double ux = p0[0] + (p1[0] - p0[0]) * s, uy = p0[1] + (p1[1] - p0[1]) * s, uz = p0[2] + (p1[2] - p0[2]) * s;
                    double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
                    double rr = r * (1 + Math.sin(s * Math.PI) * height);
                    double[] q = {cx + ux / ul * rr, cy + uy / ul * rr, cz + uz / ul * rr};
                    if (prev != null) {
                        Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, prev[0], prev[1], prev[2], q[0], q[1], q[2], w, w, 1F, 0.86F, 0.5F,
                                0.55F * lv * flick, 0.55F * lv * flick);
                    }
                    prev = q;
                }
            }
        }
    }

    private static void spicules(double cx, double cy, double cz, double r, double[] rot, long seed, double t, double px, double[] toCam,
                                 float vis) {
        if (vis <= 0.01F) {
            return;
        }
        double w = Math.max(px * 0.55, r * 0.0012);
        for (int i = 0; i < 700; i++) {
            long g = seed * 7 + i;
            double lat = Math.asin(Space3D.h(g, 1) * 2 - 1), lon = Space3D.h(g, 2) * Math.PI * 2;
            double[] n = onStar(rot, lat, lon, 1);
            float lv = limb(n, toCam);
            // Only near the limb, where they stand out against the sky.
            double f = n[0] * toCam[0] + n[1] * toCam[1] + n[2] * toCam[2];
            if (Math.abs(f) > 0.35 || lv <= 0.05F) {
                continue;
            }
            double life = 6 + 6 * Space3D.h(g, 3);
            double ph = ((t + Space3D.h(g, 4) * life) / life) % 1.0;
            double len = r * (0.012 + 0.03 * Space3D.h(g, 5)) * Math.sin(ph * Math.PI);
            double lean = (Space3D.h(g, 6) - 0.5) * 0.3;
            double bx = cx + n[0] * r, by = cy + n[1] * r, bz = cz + n[2] * r;
            double[] side = onStar(rot, lat + lean, lon + lean, 1);
            double tx = n[0] + (side[0] - n[0]) * 0.5, ty = n[1] + (side[1] - n[1]) * 0.5, tz = n[2] + (side[2] - n[2]) * 0.5;
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            Space3D.ribbon(Space3D.GLOW, ADD, bx, by, bz, bx + tx / tl * len, by + ty / tl * len, bz + tz / tl * len, w, w * 0.5, 1F, 0.45F, 0.35F,
                    0.4F * vis * lv, 0.05F * vis);
        }
    }
}
