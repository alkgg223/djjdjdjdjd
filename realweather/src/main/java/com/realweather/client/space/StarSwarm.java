package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.resources.ResourceLocation;

import static com.realweather.client.space.Space3D.ADD;

/**
 * The great cluster of stars round a supermassive black hole: hundreds of bright stars swarming round it on their own
 * tilted, stretched orbits (faster the closer in, like the S-stars round Sagittarius A*), blue giants and yellow and
 * red stars, many of them with planets of their own going round them, all inside a glowing haze of countless fainter
 * stars. Lengths in the hole's horizon radius.
 */
final class StarSwarm {
    private static final ResourceLocation STAR_MAP = RealWeather.id("textures/environment/space3d/star_surface.png");
    private static final String[] MAPS = {"mars", "jupiter", "neptune", "venus", "kepler22b", "toi700d", "hd189733b", "uranus", "mercury", "saturn",
            "kepler186f", "wasp76b"};
    private static final ResourceLocation[] PLANET_MAPS = new ResourceLocation[MAPS.length];
    /** Star colours: blue giant, white, yellow, orange, red. */
    private static final float[][] COLOURS = {{0.62F, 0.74F, 1F}, {0.86F, 0.9F, 1F}, {1F, 0.92F, 0.75F}, {1F, 0.74F, 0.46F}, {1F, 0.5F, 0.3F}};

    static {
        for (int i = 0; i < MAPS.length; i++) {
            PLANET_MAPS[i] = RealWeather.id("textures/environment/space3d/" + MAPS[i] + ".png");
        }
    }

    private StarSwarm() {}

    /**
     * The swarm round a hole at c (view): `unit` view units per horizon radius, rot: the hole's frame to view, how
     * many stars, from `inner` to `outer` horizon radii, seconds, view units per pixel at the hole.
     */
    static void draw(double cx, double cy, double cz, double unit, double[] rot, long seed, int count, double inner, double outer, double seconds,
                     double px, float vis) {
        if (vis <= 0.01F) {
            return;
        }
        double[] p = new double[3], q = new double[3], pl = new double[3], lv = new double[3];
        double camDist = Math.sqrt(cx * cx + cy * cy + cz * cz);
        // The haze of countless fainter stars: a glow thickest at the middle.
        for (int i = 0; i < 6; i++) {
            Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, unit * outer * (0.15 + 0.17 * i), 0F, 1F, 0.88F, 0.72F, 0.035F * vis);
        }
        for (int i = 0; i < count * 5; i++) {
            orbit(seed * 7 + 99991, i, inner * 0.6, outer * 1.2, seconds * 0.3, p);
            Space3D.apply(rot, p[0] * unit, p[1] * unit, p[2] * unit, q);
            double x = cx + q[0], y = cy + q[1], z = cz + q[2];
            if (z > 0) {
                continue;
            }
            double d = Math.sqrt(x * x + y * y + z * z);
            float[] col = COLOURS[Math.min(4, (int) (Space3D.h(seed * 7 + 99991, i * 9L + 8) * 5))];
            Space3D.billboard(Space3D.GLOW, ADD, x, y, z, Math.max(d * px / camDist * 1.8, unit * 0.25), 0F, col[0], col[1], col[2], 0.55F * vis);
        }
        // The bright stars, each on its own orbit.
        for (int i = 0; i < count; i++) {
            orbit(seed, i, inner, outer, seconds, p);
            Space3D.apply(rot, p[0] * unit, p[1] * unit, p[2] * unit, q);
            double x = cx + q[0], y = cy + q[1], z = cz + q[2];
            double d = Math.sqrt(x * x + y * y + z * z);
            if (z > 0 && d > unit * 6) {
                continue;
            }
            // Pixels per view unit there.
            double pxHere = px * d / Math.max(1.0E-9, camDist);
            double h = Space3D.h(seed, i * 9L + 7);
            int kind = h < 0.12 ? 0 : h < 0.3 ? 1 : h < 0.6 ? 2 : h < 0.82 ? 3 : 4;
            float[] col = COLOURS[kind];
            double r = unit * (kind == 0 ? 0.9 : kind == 4 ? 0.45 : 0.6) * (0.6 + 0.8 * Space3D.h(seed, i * 9L + 6));
            double rPix = r / pxHere;
            // Bright: a dazzling glare with spikes, and the star itself once it shows a disc.
            Space3D.billboard(Space3D.GLOW, ADD, x, y, z, Math.max(pxHere * 4, r * 7), 0F, col[0], col[1], col[2], 0.75F * vis);
            Space3D.billboard(Space3D.STAR, ADD, x, y, z, Math.max(pxHere * 9, r * 14), 0F, col[0], col[1], col[2], 0.6F * vis);
            if (rPix > 1.2) {
                double[] srot = Space3D.orient(0.2, 1, 0.1, seconds * 0.05 + i);
                Space3D.sphere(STAR_MAP, x, y, z, r, srot, 0, 0, 1, 0F, col[0] * 1.3F, col[1] * 1.3F, col[2] * 1.3F, 0F, vis, true,
                        (float) (seconds * 0.002 % 1.0), (int) Math.max(10, Math.min(40, rPix / 2)));
            }
            // Its planets.
            if (Space3D.h(seed, i * 9L + 5) > 0.55 || rPix < 0.3) {
                continue;
            }
            int n = 1 + (int) (Space3D.h(seed, i * 9L + 4) * 4);
            for (int k = 0; k < n; k++) {
                long ps = seed * 31 + i * 7L + k;
                double a = r * (4 + 4.5 * k + 3 * Space3D.h(ps, 1));
                double per = 6 + 9 * k + 6 * Space3D.h(ps, 2);
                double ang = Space3D.h(ps, 3) * Math.PI * 2 + seconds * Math.PI * 2 / per;
                double tilt = (Space3D.h(ps, 4) - 0.5) * 0.5;
                pl[0] = Math.cos(ang) * a;
                pl[1] = Math.sin(ang) * a * Math.sin(tilt);
                pl[2] = Math.sin(ang) * a * Math.cos(tilt);
                Space3D.apply(rot, pl[0], pl[1], pl[2], lv);
                double ppx = x + lv[0], ppy = y + lv[1], ppz = z + lv[2];
                double prad = r * (0.12 + 0.2 * Space3D.h(ps, 5));
                double prPix = prad / pxHere;
                // Lit by its star.
                double lx = x - ppx, ly = y - ppy, lz = z - ppz;
                double ll = Math.sqrt(lx * lx + ly * ly + lz * lz) + 1.0E-12;
                if (prPix > 1.5) {
                    double[] prot = Space3D.orient(0.1, 1, 0.2, 0);
                    Space3D.sphere(PLANET_MAPS[(int) (Space3D.h(ps, 6) * PLANET_MAPS.length) % PLANET_MAPS.length], ppx, ppy, ppz, prad, prot, lx / ll,
                            ly / ll, lz / ll, 0.02F, 0.6F + 0.4F * col[0], 0.6F + 0.4F * col[1], 0.6F + 0.4F * col[2], 0F, vis, false,
                            (float) (seconds / per % 1.0), (int) Math.max(10, Math.min(36, prPix / 2)));
                } else {
                    Space3D.billboard(Space3D.GLOW, ADD, ppx, ppy, ppz, pxHere * 1.6, 0F, 0.9F, 0.9F, 0.85F, 0.5F * vis);
                }
                // A faint trace of its orbit, close up.
                if (a / pxHere > 25) {
                    double[] prev = null;
                    for (int s = 0; s <= 48; s++) {
                        double t = Math.PI * 2 * s / 48;
                        Space3D.apply(rot, Math.cos(t) * a, Math.sin(t) * a * Math.sin(tilt), Math.sin(t) * a * Math.cos(tilt), lv);
                        double[] cur = {x + lv[0], y + lv[1], z + lv[2]};
                        if (prev != null) {
                            Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, prev[0], prev[1], prev[2], cur[0], cur[1], cur[2], pxHere * 0.6, pxHere * 0.6,
                                    0.6F, 0.75F, 1F, 0.12F * vis, 0.12F * vis);
                        }
                        prev = cur;
                    }
                }
            }
        }
    }

    /** Where star i of a swarm is: a tilted, stretched orbit round the hole, faster the closer in (Kepler). */
    private static void orbit(long seed, int i, double inner, double outer, double seconds, double[] out) {
        double a = inner * Math.pow(outer / inner, Math.pow(Space3D.h(seed, i * 9L), 1.3));
        double e = 0.1 + 0.7 * Space3D.h(seed, i * 9L + 1);
        double speed = 0.6 * Math.pow(a / inner, -1.5);
        double m = Space3D.h(seed, i * 9L + 2) * Math.PI * 2 + seconds * speed;
        // The angle along its orbit (sped up near the hole, as a stretched orbit is).
        double nu = m + 2 * e * Math.sin(m) + 1.25 * e * e * Math.sin(2 * m);
        double r = a * (1 - e * e) / (1 + e * Math.cos(nu));
        double inc = Math.acos(1 - 2 * Space3D.h(seed, i * 9L + 3)), node = Space3D.h(seed, i * 9L + 4) * Math.PI * 2;
        double x = r * Math.cos(nu), y = r * Math.sin(nu);
        double yi = y * Math.cos(inc), zi = y * Math.sin(inc);
        out[0] = x * Math.cos(node) - yi * Math.sin(node);
        out[1] = zi;
        out[2] = x * Math.sin(node) + yi * Math.cos(node);
    }
}
