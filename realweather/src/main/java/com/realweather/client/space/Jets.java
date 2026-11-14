package com.realweather.client.space;

import static com.realweather.client.space.Space3D.ADD;

/**
 * Relativistic jets: beams of particles shot out from both poles of a black hole's disc or a neutron star at nearly
 * the speed of light, wound in a slow helix by the magnetic field, with bright knots (shocks) racing out along them.
 * The jet coming towards us is beamed far brighter and bluer than the one going away (relativistic Doppler boosting).
 */
final class Jets {
    private Jets() {}

    /**
     * Both jets from c (view) along `axis` (view, unit): length and width (view units), colour, speed (fraction of
     * the speed of light), seconds, view units per pixel there.
     */
    static void draw(double cx, double cy, double cz, double[] axis, double length, double width, float r, float g, float b, double beta,
                     long seed, double seconds, double px, float vis) {
        if (vis <= 0.01F || length / px < 3) {
            return;
        }
        double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double[] toCam = {-cx / dl, -cy / dl, -cz / dl};
        double gamma = 1 / Math.sqrt(1 - beta * beta);
        double[] e1 = perp(axis);
        double[] e2 = {axis[1] * e1[2] - axis[2] * e1[1], axis[2] * e1[0] - axis[0] * e1[2], axis[0] * e1[1] - axis[1] * e1[0]};
        for (int sg = -1; sg <= 1; sg += 2) {
            double ax = axis[0] * sg, ay = axis[1] * sg, az = axis[2] * sg;
            double cos = ax * toCam[0] + ay * toCam[1] + az * toCam[2];
            double delta = 1 / (gamma * (1 - beta * cos));
            float boost = (float) Math.min(3.0, Math.pow(delta, 1.6) * 0.6);
            // Bluer coming towards us, redder going away.
            float shift = (float) Math.max(-1, Math.min(1, Math.log(delta) * 0.5));
            float jr = r * (1 - 0.25F * shift), jg = g, jb = Math.min(1F, b * (1 + 0.2F * shift));
            int n = 14;
            for (int j = 0; j < n; j++) {
                double s0 = (double) j / n, s1 = (double) (j + 1) / n;
                double h0 = Math.sin(s0 * 14 + seconds * 0.4) * width * 0.6 * s0, h1 = Math.sin(s1 * 14 + seconds * 0.4) * width * 0.6 * s1;
                double k0 = Math.cos(s0 * 14 + seconds * 0.4) * width * 0.6 * s0, k1 = Math.cos(s1 * 14 + seconds * 0.4) * width * 0.6 * s1;
                double x0 = cx + ax * length * s0 + e1[0] * h0 + e2[0] * k0, y0 = cy + ay * length * s0 + e1[1] * h0 + e2[1] * k0,
                        z0 = cz + az * length * s0 + e1[2] * h0 + e2[2] * k0;
                double x1 = cx + ax * length * s1 + e1[0] * h1 + e2[0] * k1, y1 = cy + ay * length * s1 + e1[1] * h1 + e2[1] * k1,
                        z1 = cz + az * length * s1 + e1[2] * h1 + e2[2] * k1;
                float a0 = (float) (1 - s0 * 0.85) * boost * vis, a1 = (float) (1 - s1 * 0.85) * boost * vis;
                double w0 = Math.max(px * 0.7, width * (0.15 + s0 * 0.9)), w1 = Math.max(px * 0.7, width * (0.15 + s1 * 0.9));
                // A thin bright spine inside a wider glowing sheath.
                Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, x0, y0, z0, x1, y1, z1, w0 * 0.25, w1 * 0.25, 1F, 1F, 1F, 0.55F * a0, 0.55F * a1);
                Space3D.ribbon(Space3D.GLOW, ADD, x0, y0, z0, x1, y1, z1, w0, w1, jr, jg, jb, 0.3F * a0, 0.3F * a1);
                Space3D.ribbon(Space3D.GLOW, ADD, x0, y0, z0, x1, y1, z1, w0 * 2.4, w1 * 2.4, jr * 0.8F, jg * 0.85F, jb, 0.08F * a0, 0.08F * a1);
            }
            // Knots: shocks racing outwards (seen to move almost at the speed of light).
            for (int k = 0; k < 10; k++) {
                double s = (Space3D.h(seed, k * 3L + (sg > 0 ? 0 : 1)) + seconds * 0.04 * (0.7 + 0.6 * Space3D.h(seed, k * 3L + 2))) % 1.0;
                double hh = Math.sin(s * 14 + seconds * 0.4) * width * 0.6 * s, kk = Math.cos(s * 14 + seconds * 0.4) * width * 0.6 * s;
                Space3D.billboard(Space3D.GLOW, ADD, cx + ax * length * s + e1[0] * hh + e2[0] * kk, cy + ay * length * s + e1[1] * hh + e2[1] * kk,
                        cz + az * length * s + e1[2] * hh + e2[2] * kk, Math.max(px * 2, width * (0.6 + 1.2 * s)), 0F, jr, jg, jb,
                        (float) (0.55 * (1 - s)) * boost * vis);
            }
        }
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
