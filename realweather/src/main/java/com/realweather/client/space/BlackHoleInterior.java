package com.realweather.client.space;

import static com.realweather.client.space.Space3D.ADD;

/**
 * Inside a black hole's event horizon: a theoretical simulation, scientifically inspired (no one can see in, and what
 * is really there is not known). Every path now leads inwards to the singularity. The whole outside universe is
 * squeezed into a shrinking, blue-shifted window of light behind and above you, the accretion disc's light a burning
 * ring round its edge, the stars smeared into arcs as space itself is stretched; ahead, towards the centre, only
 * darkness, and streaks of red as the tidal pull grows until it tears everything apart.
 */
final class BlackHoleInterior {
    private BlackHoleInterior() {}

    /**
     * r: how far in (1 at the horizon, 0 at the singularity); out: the unit direction outwards (frame); m: frame to
     * view; seconds; how strongly.
     */
    static void draw(double r, double[] out, double[] m, double seconds, double px) {
        float[] field = TelescopeView.field();
        double[] o = new double[3];
        Space3D.apply(m, out[0], out[1], out[2], o);
        // The window of the outside: its angular radius shrinks as you fall.
        double window = Math.max(0.03, Math.PI * 0.42 * Math.pow(r, 0.75));
        // Deeper in, space is stretched along the fall and squeezed across it: the sky then gathers into a band
        // round you instead (as in simulations of a fall into a black hole).
        double band = smooth(0.55, 0.12, r), bandWidth = 0.08 + 0.7 * r;
        double s = 2000;
        Space3D.range(1F, (float) (s * 3));
        double[] q = new double[3];
        float blue = (float) Math.min(1, (1 - r) * 1.3);
        for (int i = 0; i < 6000; i++) {
            double sx = field[i * 5], sy = field[i * 5 + 1], sz = field[i * 5 + 2];
            double cos = sx * out[0] + sy * out[1] + sz * out[2];
            double th = Math.acos(Math.max(-1, Math.min(1, cos)));
            // Every star of the sky, squeezed into the window.
            double th2 = th / Math.PI * window * (1 - band) + (Math.PI / 2 + (th / Math.PI - 0.5) * bandWidth) * band;
            double px0 = sx - out[0] * cos, py0 = sy - out[1] * cos, pz0 = sz - out[2] * cos;
            double pl = Math.sqrt(px0 * px0 + py0 * py0 + pz0 * pz0);
            if (pl < 1.0E-6) {
                continue;
            }
            double c2 = Math.cos(th2), s2 = Math.sin(th2);
            double dx = out[0] * c2 + px0 / pl * s2, dy = out[1] * c2 + py0 / pl * s2, dz = out[2] * c2 + pz0 / pl * s2;
            Space3D.apply(m, dx * s, dy * s, dz * s, q);
            if (q[2] > -1) {
                continue;
            }
            float b = field[i * 5 + 3];
            // Stretched along the way they are squeezed: arcs, not points.
            double arc = 0.004 + 0.02 * (1 - r) * (th / Math.PI);
            double ax = out[0] * Math.cos(th2 + arc) + px0 / pl * Math.sin(th2 + arc), ay = out[1] * Math.cos(th2 + arc) + py0 / pl * Math.sin(th2 + arc),
                    az = out[2] * Math.cos(th2 + arc) + pz0 / pl * Math.sin(th2 + arc);
            double[] q2 = new double[3];
            Space3D.apply(m, ax * s, ay * s, az * s, q2);
            float a = (0.35F + 0.65F * b) * (float) (1.2 - 0.4 * r);
            Space3D.ribbon(Space3D.GLOW, ADD, q[0], q[1], q[2], q2[0], q2[1], q2[2], px * s * (1.2 + 2 * b), px * s * (0.8 + 1.5 * b), 0.75F - 0.3F * blue,
                    0.85F, 1F, a, a * 0.4F);
        }
        // The ring of the disc's light round the window's edge, and a fainter second image inside it.
        int n = 96;
        double[] e1 = perp(o), e2 = {o[1] * e1[2] - o[2] * e1[1], o[2] * e1[0] - o[0] * e1[2], o[0] * e1[1] - o[1] * e1[0]};
        for (int k = 0; k < 2; k++) {
            double rim = window * (k == 0 ? 1.0 : 0.86) * (1 - band) + (Math.PI / 2 + (k == 0 ? 0.5 : -0.5) * bandWidth) * band;
            float a = (float) (k == 0 ? 0.55 : 0.2);
            double[] prev = null;
            for (int i = 0; i <= n; i++) {
                double ph = Math.PI * 2 * i / n + seconds * 0.4;
                double c = Math.cos(rim), sn = Math.sin(rim);
                double vx = o[0] * c + (e1[0] * Math.cos(ph) + e2[0] * Math.sin(ph)) * sn, vy = o[1] * c + (e1[1] * Math.cos(ph) + e2[1] * Math.sin(ph)) * sn,
                        vz = o[2] * c + (e1[2] * Math.cos(ph) + e2[2] * Math.sin(ph)) * sn;
                double[] cur = {vx * s, vy * s, vz * s};
                // Brighter on the side the disc is coming towards you.
                float beam = (float) (0.55 + 0.45 * Math.sin(ph - seconds * 0.4));
                if (prev != null) {
                    Space3D.ribbon(Space3D.GLOW, ADD, prev[0], prev[1], prev[2], cur[0], cur[1], cur[2], s * 0.012 * (1.5 - k), s * 0.012 * (1.5 - k), 1F,
                            0.62F + 0.2F * blue, 0.3F + 0.5F * blue, a * beam, a * beam);
                }
                prev = cur;
            }
        }
        // Ahead, towards the centre: dark, with red streaks of the growing tidal stretch.
        double tide = Math.pow(Math.max(0, 1 - r), 2.2);
        for (int i = 0; i < 70; i++) {
            double ph = Space3D.h(9119, i) * Math.PI * 2, th = Math.PI - 0.08 - Space3D.h(9119, i + 100) * 0.9;
            double len = 0.05 + 0.3 * tide * (0.5 + 0.5 * Math.sin(seconds * 2 + i));
            double[] a0 = dir(o, e1, e2, th, ph), a1 = dir(o, e1, e2, th + len, ph);
            Space3D.ribbon(Space3D.GLOW, ADD, a0[0] * s, a0[1] * s, a0[2] * s, a1[0] * s, a1[1] * s, a1[2] * s, s * 0.004, s * 0.001, 1F, 0.25F, 0.15F,
                    (float) (0.35 + 0.5 * tide), 0F);
        }
        // Ahead: gas from the disc that fell in before you, still falling, its light reddened, spiralling in towards
        // the centre; a deep red glow where the paths meet (theory only: what happens there is unknown).
        double[] in = {-o[0], -o[1], -o[2]};
        double[] g1 = perp(in), g2 = {in[1] * g1[2] - in[2] * g1[1], in[2] * g1[0] - in[0] * g1[2], in[0] * g1[1] - in[1] * g1[0]};
        float glow = (float) (0.35 + 0.65 * (1 - r));
        for (int arm = 0; arm < 4; arm++) {
            double[] prev = null;
            for (int i = 0; i <= 64; i++) {
                double u = i / 64.0;
                double th = (1 - u) * (0.75 + 0.2 * r) + 0.004;
                double ph = arm * Math.PI / 2 + u * 6.5 + seconds * (0.25 + 0.9 * u) + 0.3 * Math.sin(seconds * 0.7 + arm);
                double[] d = dir(in, g1, g2, th, ph);
                double[] cur = {d[0] * s, d[1] * s, d[2] * s};
                if (prev != null) {
                    float a = glow * (float) (0.15 + 0.5 * u) * (0.7F + 0.3F * (float) Math.sin(i * 0.7 - seconds * 3 + arm));
                    double w = s * (0.03 * (1 - u) + 0.004);
                    Space3D.ribbon(Space3D.GLOW, ADD, prev[0], prev[1], prev[2], cur[0], cur[1], cur[2], w, w, 1F, (float) (0.28 + 0.3 * u),
                            (float) (0.1 + 0.15 * u), a, a);
                }
                prev = cur;
            }
        }
        Space3D.billboard(Space3D.GLOW, ADD, in[0] * s, in[1] * s, in[2] * s, s * (0.1 + 0.25 * (1 - r)), 0F, 1F, 0.28F, 0.1F, 0.5F * glow);
        Space3D.billboard(Space3D.GLOW, ADD, in[0] * s, in[1] * s, in[2] * s, s * 0.03, 0F, 1F, 0.6F, 0.35F, 0.6F * glow);
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static double[] dir(double[] o, double[] e1, double[] e2, double th, double ph) {
        double c = Math.cos(th), sn = Math.sin(th);
        return new double[]{o[0] * c + (e1[0] * Math.cos(ph) + e2[0] * Math.sin(ph)) * sn, o[1] * c + (e1[1] * Math.cos(ph) + e2[1] * Math.sin(ph)) * sn,
                o[2] * c + (e1[2] * Math.cos(ph) + e2[2] * Math.sin(ph)) * sn};
    }

    private static double[] perp(double[] a) {
        double tx = Math.abs(a[1]) < 0.9 ? 0 : 1, ty = Math.abs(a[1]) < 0.9 ? 1 : 0;
        double[] e = {ty * a[2], -tx * a[2], tx * a[1] - ty * a[0]};
        double l = Math.sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2]) + 1.0E-12;
        e[0] /= l;
        e[1] /= l;
        e[2] /= l;
        return e;
    }
}
