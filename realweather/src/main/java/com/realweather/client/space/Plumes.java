package com.realweather.client.space;

import static com.realweather.client.space.Space3D.ADD;
import static com.realweather.client.space.Space3D.NORMAL;

/**
 * Volcanic plumes on moons, every grain on its own ballistic path in the moon's low gravity: Io's great umbrella
 * plumes (sulphur gas and dust thrown hundreds of kilometres up, falling back in a wide ring that stains the ground
 * red), and the thin jets of ice from the tiger stripes at Enceladus's south pole, rising far into space.
 */
final class Plumes {
    /** A vent: latitude, longitude (degrees), how high it throws (in the moon's radii), and its kind. */
    record Vent(double lat, double lon, double height, int kind) {}

    static final int UMBRELLA = 0, JET = 1;
    private static final Vent[] IO = {new Vent(-19, 255, 0.17, UMBRELLA), new Vent(13, 309, 0.11, UMBRELLA), new Vent(-1.6, 153, 0.05, UMBRELLA),
            new Vent(63, 123, 0.18, UMBRELLA), new Vent(-36, 40, 0.07, UMBRELLA)};
    private static final Vent[] ENCELADUS = {new Vent(-84, 20, 1.6, JET), new Vent(-80, 110, 1.3, JET), new Vent(-82, 200, 1.5, JET),
            new Vent(-78, 290, 1.2, JET), new Vent(-87, 160, 1.8, JET), new Vent(-76, 60, 1.0, JET)};

    private Plumes() {}

    static Vent[] of(String id) {
        return switch (id) {
            case "io" -> IO;
            case "enceladus" -> ENCELADUS;
            default -> null;
        };
    }

    /**
     * The plumes of a moon at c (view) of radius r, turned by rot (its frame to view), lit from `sun` (view, unit),
     * seconds, view units per pixel there.
     */
    static void draw(String id, double cx, double cy, double cz, double r, double[] rot, double[] sun, double seconds, double px, float vis) {
        Vent[] vents = of(id);
        if (vents == null || vis <= 0.01F || r / px < 18) {
            return;
        }
        float detail = Space3D.smooth(18F, 60F, (float) (r / px));
        double[] up = new double[3], e1 = new double[3], e2 = new double[3];
        for (int v = 0; v < vents.length; v++) {
            Vent vent = vents[v];
            double la = Math.toRadians(vent.lat), lo = Math.toRadians(vent.lon);
            Space3D.apply(rot, Math.cos(la) * Math.cos(lo), Math.sin(la), Math.cos(la) * Math.sin(lo), up);
            Space3D.apply(rot, -Math.sin(lo), 0, Math.cos(lo), e1);
            e2[0] = up[1] * e1[2] - up[2] * e1[1];
            e2[1] = up[2] * e1[0] - up[0] * e1[2];
            e2[2] = up[0] * e1[1] - up[1] * e1[0];
            double lit = up[0] * sun[0] + up[1] * sun[1] + up[2] * sun[2];
            float light = (float) Math.max(0.15, Math.min(1, lit * 1.5 + 0.6));
            double bx = cx + up[0] * r, by = cy + up[1] * r, bz = cz + up[2] * r;
            plume(bx, by, bz, up, e1, e2, vent.height * r, vent.kind, id.hashCode() * 31L + v, seconds, px, detail * vis, light, r * 0.015);
        }
    }

    /**
     * One plume from a vent at b (view), with the local up and two directions along the ground there, how high it
     * throws (view units), its kind, seconds, view units per pixel, strength, how well lit, and the vent's own size.
     */
    static void plume(double bx, double by, double bz, double[] up, double[] e1, double[] e2, double hgt, int kind, long seed, double seconds,
                      double px, float vis, float light, double ventSize) {
        int n = kind == JET ? 220 : 260;
        // Each grain's flight lasts a while; launched at an angle, it rises and falls back (a parabola).
        double period = kind == JET ? 40 : 26;
        for (int i = 0; i < n; i++) {
            double f = ((seconds + Space3D.h(seed, i * 6L) * period) / period) % 1.0;
            double az = Space3D.h(seed, i * 6L + 1) * Math.PI * 2;
            double tilt = kind == JET ? Space3D.h(seed, i * 6L + 2) * 0.12 : 0.2 + Space3D.h(seed, i * 6L + 2) * 0.55;
            double lift = 4 * f * (1 - f) * hgt * (0.7 + 0.3 * Space3D.h(seed, i * 6L + 3));
            double out = Math.tan(tilt) * f * hgt * 2.2 * (kind == JET ? 1.6 : 1.0);
            double ox = Math.cos(az) * out, oy = Math.sin(az) * out;
            double qx = bx + up[0] * lift + e1[0] * ox + e2[0] * oy, qy = by + up[1] * lift + e1[1] * ox + e2[1] * oy,
                    qz = bz + up[2] * lift + e1[2] * ox + e2[2] * oy;
            double size = Math.max(px * 1.4, hgt * (kind == JET ? 0.03 + 0.05 * f : 0.05 + 0.1 * f));
            float fade = (float) (Math.min(1, f * 8) * (1 - f * 0.7)) * vis;
            if (kind == JET) {
                // Ice crystals, glittering in the sunlight.
                Space3D.billboard(Space3D.GLOW, ADD, qx, qy, qz, size, 0F, 0.82F * light, 0.9F * light, 1F * light, 0.16F * fade);
            } else if (i % 3 == 0) {
                // Darker dust and sulphur in the umbrella.
                Space3D.billboard(Space3D.GLOW, NORMAL, qx, qy, qz, size * 1.3, 0F, 0.55F * light, 0.45F * light, 0.3F * light, 0.12F * fade);
            } else {
                // Sulphur dioxide gas lit by the Sun, bluish at the top of the canopy.
                float top = (float) (lift / Math.max(1.0E-9, hgt));
                Space3D.billboard(Space3D.GLOW, ADD, qx, qy, qz, size, 0F, (0.85F - 0.3F * top) * light, (0.8F - 0.1F * top) * light,
                        (0.55F + 0.4F * top) * light, 0.14F * fade);
            }
        }
        if (kind == UMBRELLA) {
            // The red ring of fallen sulphur round the vent.
            double ring = Math.tan(0.5) * hgt * 2.2;
            for (int i = 0; i < 40; i++) {
                double a = Math.PI * 2 * i / 40;
                double qx = bx + (e1[0] * Math.cos(a) + e2[0] * Math.sin(a)) * ring, qy = by + (e1[1] * Math.cos(a) + e2[1] * Math.sin(a)) * ring,
                        qz = bz + (e1[2] * Math.cos(a) + e2[2] * Math.sin(a)) * ring;
                Space3D.billboard(Space3D.GLOW, NORMAL, qx, qy, qz, Math.max(px * 1.5, ring * 0.12), 0F, 0.75F * light, 0.28F * light, 0.12F * light,
                        0.18F * vis);
            }
            // The vent's glow of molten lava.
            Space3D.billboard(Space3D.GLOW, ADD, bx, by, bz, Math.max(px * 1.5, ventSize), 0F, 1F, 0.45F, 0.15F, 0.7F * vis);
        }
    }
}
