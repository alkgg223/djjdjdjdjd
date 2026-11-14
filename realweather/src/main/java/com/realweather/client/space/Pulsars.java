package com.realweather.client.space;

import static com.realweather.client.space.Space3D.ADD;

/**
 * A pulsar: a neutron star (a city-sized ball of matter denser than an atom's nucleus, white-blue hot) spinning on
 * its axis, its magnetic axis tipped from the spin axis so that the two beams of radiation from its magnetic poles
 * sweep round the sky like a lighthouse's. The beams turn with the star; each time one sweeps across us the pulsar
 * flashes (the pulse), in step with the spin. Its magnetic field's loops (a dipole) turn with it. Real pulsars spin
 * from about once a second to hundreds of times a second: shown slowed down (the faster the faster still), the
 * fastest smeared by their speed into blurred fans.
 */
final class Pulsars {
    private static final double[] M = new double[3], E1 = new double[3], E2 = new double[3];

    private Pulsars() {}

    /** Turns a real spin rate into one you can watch (1 Hz stays about 1 Hz; 716 Hz becomes about 5 Hz). */
    static double shownRate(double hz) {
        return 0.72 * Math.pow(Math.max(hz, 0.05), 0.3);
    }

    /**
     * Draws a pulsar at c (view space) of radius r (view units): spin axis `axis` (view space, unit), magnetic tilt in
     * degrees, real spin rate, beam colour, and the size of a pixel there.
     */
    static void draw(double cx, double cy, double cz, double r, double[] axis, double tiltDeg, double hz, float br, float bg, float bb, float vis,
                     double px, long seed, double seconds, boolean fieldLines) {
        draw(cx, cy, cz, r, axis, tiltDeg, hz, br, bg, bb, vis, px, seed, seconds, fieldLines, false, false);
    }

    /**
     * As above; `jets`: a pair of relativistic jets along the spin axis (the Crab's and Vela's); `magnetar`: a
     * magnetar's monstrous field, its quakes far more often and now and then a giant flare.
     */
    static void draw(double cx, double cy, double cz, double r, double[] axis, double tiltDeg, double hz, float br, float bg, float bb, float vis,
                     double px, long seed, double seconds, boolean fieldLines, boolean jets, boolean magnetar) {
        double rate = shownRate(hz);
        double phase = Math.PI * 2 * rate * seconds;
        double tilt = Math.toRadians(tiltDeg);
        // Two directions square to the spin axis.
        double ax = axis[0], ay = axis[1], az = axis[2];
        double tx = Math.abs(ay) < 0.9 ? 0 : 1, ty = Math.abs(ay) < 0.9 ? 1 : 0;
        E1[0] = ty * az;
        E1[1] = -tx * az;
        E1[2] = tx * ay - ty * ax;
        double l = Math.sqrt(E1[0] * E1[0] + E1[1] * E1[1] + E1[2] * E1[2]);
        E1[0] /= l;
        E1[1] /= l;
        E1[2] /= l;
        E2[0] = ay * E1[2] - az * E1[1];
        E2[1] = az * E1[0] - ax * E1[2];
        E2[2] = ax * E1[1] - ay * E1[0];
        double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double tox = -cx / dl, toy = -cy / dl, toz = -cz / dl;
        // The star itself, turning.
        double[] rot = Space3D.orient(ax, ay, az, phase);
        Space3D.sphere(Space3D.STAR_MAP_PUBLIC, cx, cy, cz, r, rot, 0, 0, 1, 0F, 0.78F * 1.5F, 0.86F * 1.5F, 1.5F, 0F, vis, true,
                (float) (phase / (Math.PI * 2) % 1.0), 24);
        // How directly a beam points at us now: the pulse.
        magnetic(phase, tilt, ax, ay, az);
        double facing = Math.abs(M[0] * tox + M[1] * toy + M[2] * toz);
        float pulse = (float) Math.exp(-(1 - facing) * 45);
        Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, Math.max(r * (3 + 5 * pulse), px * 5), 0F, br, bg, bb, (0.35F + 0.35F * pulse) * vis);
        Space3D.billboard(Space3D.STAR, ADD, cx, cy, cz, Math.max(r * (5 + 9 * pulse), px * 8), 0F, br, bg, bb, (0.15F + 0.3F * pulse) * vis);
        // The beams, blurred over the frame for the fast ones.
        int blur = (int) Math.max(1, Math.min(8, Math.round(rate * 1.4)));
        double frame = Math.PI * 2 * rate / 60.0;
        double len = r * 160;
        for (int k = 0; k < blur; k++) {
            magnetic(phase - frame * k / blur, tilt, ax, ay, az);
            float a = vis / blur * (k == 0 ? 1.4F : 1F);
            // (A beam pointing at us is seen end on: the flash, not a cone filling the view.)
            float endOn = (float) Math.pow(Math.abs(M[0] * tox + M[1] * toy + M[2] * toz), 6);
            a *= 1F - 0.9F * endOn;
            for (int sg = -1; sg <= 1; sg += 2) {
                double mx = M[0] * sg, my = M[1] * sg, mz = M[2] * sg;
                // Nested cones: a bright narrow core inside softer, wider glow (a beam of light in 3D, not a flat cone).
                Space3D.cone(cx + mx * r, cy + my * r, cz + mz * r, mx, my, mz, len, len * 0.035, br, bg, bb, 0.55F * a);
                Space3D.cone(cx + mx * r, cy + my * r, cz + mz * r, mx, my, mz, len * 0.8, len * 0.08, br, bg, bb, 0.28F * a);
                Space3D.cone(cx + mx * r, cy + my * r, cz + mz * r, mx, my, mz, len * 0.55, len * 0.16, br * 0.8F, bg * 0.85F, bb, 0.14F * a);
                if (k == 0) {
                    // Charged particles streaming out along it.
                    for (int i = 0; i < 26; i++) {
                        double f = (Space3D.h(seed, i * 5L + sg) + seconds * (0.35 + 0.3 * Space3D.h(seed, i * 5L + 2))) % 1.0;
                        double spread = f * len * 0.07;
                        double ox = (Space3D.h(seed, i * 5L + 3) - 0.5) * spread, oy = (Space3D.h(seed, i * 5L + 4) - 0.5) * spread;
                        double qx = cx + mx * (r + f * len) + E1[0] * ox + E2[0] * oy, qy = cy + my * (r + f * len) + E1[1] * ox + E2[1] * oy,
                                qz = cz + mz * (r + f * len) + E1[2] * ox + E2[2] * oy;
                        Space3D.billboard(Space3D.GLOW, ADD, qx, qy, qz, Math.max(px * 1.5, r * (1.2 + 3 * f)), 0F, br, bg, bb, 0.35F * (float) (1 - f) * vis);
                    }
                }
            }
        }
        // Hot spots on the star where the beams leave it.
        magnetic(phase, tilt, ax, ay, az);
        for (int sg = -1; sg <= 1; sg += 2) {
            Space3D.billboard(Space3D.GLOW, ADD, cx + M[0] * r * sg, cy + M[1] * r * sg, cz + M[2] * r * sg, r * 0.9, 0F, 1F, 1F, 1F, 0.8F * vis);
        }
        if (fieldLines) {
            field(cx, cy, cz, r, phase, tilt, ax, ay, az, br, bg, bb, vis * (magnetar ? 2.2F : 1F), px);
        }
        if (jets) {
            Jets.draw(cx, cy, cz, axis, r * 70, r * 2.2, 0.62F, 0.78F, 1F, 0.6, seed, seconds, px, vis * 0.7F);
        }
        quakes(cx, cy, cz, r, rot, seed, seconds, px, vis, magnetar);
    }

    /**
     * Starquakes: the crust, strained by the star's field and spin, suddenly cracks. A flash of X-rays bursts from the
     * epicentre, ripples race round the whole star, glowing fractures open in the crust, and a spray of hot plasma is
     * flung off. (A magnetar's are far stronger and more frequent, with now and then a giant flare.)
     */
    private static void quakes(double cx, double cy, double cz, double r, double[] rot, long seed, double seconds, double px, float vis,
                               boolean magnetar) {
        double every = magnetar ? 12 : 30;
        long slot = (long) Math.floor(seconds / every);
        double[] epi = new double[3], q = new double[3], t1 = new double[3], t2 = new double[3];
        for (long k = slot - 1; k <= slot; k++) {
            double start = k * every + Space3D.h(seed, k * 13) * every * 0.5;
            double age = seconds - start;
            if (age < 0 || age > 6) {
                continue;
            }
            boolean giant = magnetar && Space3D.h(seed, k * 13 + 9) < 0.3F;
            double lat = Math.asin(Space3D.h(seed, k * 13 + 1) * 2 - 1), lon = Space3D.h(seed, k * 13 + 2) * Math.PI * 2;
            Space3D.apply(rot, Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon), epi);
            // Two directions along the surface there.
            double tx = Math.abs(epi[1]) < 0.9 ? 0 : 1, ty = Math.abs(epi[1]) < 0.9 ? 1 : 0;
            t1[0] = ty * epi[2];
            t1[1] = -tx * epi[2];
            t1[2] = tx * epi[1] - ty * epi[0];
            double l = Math.sqrt(t1[0] * t1[0] + t1[1] * t1[1] + t1[2] * t1[2]);
            t1[0] /= l;
            t1[1] /= l;
            t1[2] /= l;
            t2[0] = epi[1] * t1[2] - epi[2] * t1[1];
            t2[1] = epi[2] * t1[0] - epi[0] * t1[2];
            t2[2] = epi[0] * t1[1] - epi[1] * t1[0];
            float flash = (float) Math.exp(-age * 2.5) * vis;
            double ex = cx + epi[0] * r, ey = cy + epi[1] * r, ez = cz + epi[2] * r;
            Space3D.billboard(Space3D.GLOW, ADD, ex, ey, ez, Math.max(px * 3, r * (0.5 + 2.5 * flash)), 0F, 0.85F, 0.9F, 1F, flash);
            if (giant) {
                Space3D.billboard(Space3D.STAR, ADD, cx, cy, cz, r * (8 + 30 * flash), 0F, 0.85F, 0.88F, 1F, flash * 0.8F);
                Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, r * (4 + 12 * flash), 0F, 0.9F, 0.9F, 1F, flash * 0.6F);
            }
            // Ripples racing round the star from the epicentre.
            for (int w = 0; w < 3; w++) {
                double ang = age * 0.9 - w * 0.35;
                if (ang <= 0.02 || ang >= Math.PI - 0.02) {
                    continue;
                }
                float a = (float) (Math.exp(-age * 0.7) * (1 - ang / Math.PI)) * vis * 0.6F;
                int n = 40;
                double[] prev = null;
                for (int i = 0; i <= n; i++) {
                    double az = Math.PI * 2 * i / n;
                    double s = Math.sin(ang), c = Math.cos(ang);
                    for (int j = 0; j < 3; j++) {
                        q[j] = (epi[j] * c + (t1[j] * Math.cos(az) + t2[j] * Math.sin(az)) * s) * r * 1.015;
                    }
                    double[] cur = {cx + q[0], cy + q[1], cz + q[2]};
                    if (prev != null) {
                        Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, prev[0], prev[1], prev[2], cur[0], cur[1], cur[2], Math.max(px * 0.8, r * 0.02),
                                Math.max(px * 0.8, r * 0.02), 0.7F, 0.85F, 1F, a, a);
                    }
                    prev = cur;
                }
            }
            // Fractures glowing in the crust.
            float crack = (float) Math.exp(-age * 0.6) * vis;
            for (int c = 0; c < 7; c++) {
                double dirA = Math.PI * 2 * c / 7 + Space3D.h(seed, k * 13 + 20 + c);
                double u = 0, v = 0;
                double[] prev = {ex, ey, ez};
                for (int st = 1; st <= 7; st++) {
                    dirA += (Space3D.h(seed, k * 131 + c * 17L + st) - 0.5) * 1.1;
                    u += Math.cos(dirA) * 0.07;
                    v += Math.sin(dirA) * 0.07;
                    for (int j = 0; j < 3; j++) {
                        q[j] = epi[j] + t1[j] * u + t2[j] * v;
                    }
                    double ql = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
                    double[] cur = {cx + q[0] / ql * r * 1.005, cy + q[1] / ql * r * 1.005, cz + q[2] / ql * r * 1.005};
                    float fa = crack * (float) Math.min(1, age * 4) * (1F - st / 9F);
                    Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, prev[0], prev[1], prev[2], cur[0], cur[1], cur[2], Math.max(px * 0.7, r * 0.012),
                            Math.max(px * 0.7, r * 0.008), 1F, 0.75F, 0.45F, fa, fa);
                    prev = cur;
                }
            }
            // Hot plasma flung off.
            for (int i = 0; i < 36; i++) {
                double az = Space3D.h(seed, k * 77 + i) * Math.PI * 2, up = 0.4 + 0.6 * Space3D.h(seed, k * 77 + i + 50);
                double sp = (giant ? 9 : 3) * (0.4 + Space3D.h(seed, k * 77 + i + 100));
                double out = age * sp;
                for (int j = 0; j < 3; j++) {
                    q[j] = epi[j] * up + (t1[j] * Math.cos(az) + t2[j] * Math.sin(az)) * Math.sqrt(1 - up * up);
                }
                Space3D.billboard(Space3D.GLOW, ADD, ex + q[0] * r * out, ey + q[1] * r * out, ez + q[2] * r * out, Math.max(px * 1.5, r * 0.15), 0F,
                        0.8F, 0.85F, 1F, (float) Math.exp(-age * 1.2) * 0.5F * vis);
            }
        }
    }

    /** The magnetic axis at a phase of the spin. */
    private static void magnetic(double phase, double tilt, double ax, double ay, double az) {
        double c = Math.cos(phase), s = Math.sin(phase), ct = Math.cos(tilt), st = Math.sin(tilt);
        for (int i = 0; i < 3; i++) {
            double a = i == 0 ? ax : i == 1 ? ay : az;
            M[i] = a * ct + (E1[i] * c + E2[i] * s) * st;
        }
    }

    /** The loops of its dipole field, from one magnetic pole round to the other, turning with the star. */
    private static void field(double cx, double cy, double cz, double r, double phase, double tilt, double ax, double ay, double az, float br,
                              float bg, float bb, float vis, double px) {
        magnetic(phase, tilt, ax, ay, az);
        double mx = M[0], my = M[1], mz = M[2];
        // Two directions square to the magnetic axis, turning with it.
        double ux = my * az - mz * ay, uy = mz * ax - mx * az, uz = mx * ay - my * ax;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1.0E-6) {
            ux = E1[0];
            uy = E1[1];
            uz = E1[2];
            ul = 1;
        }
        ux /= ul;
        uy /= ul;
        uz /= ul;
        double wx = my * uz - mz * uy, wy = mz * ux - mx * uz, wz = mx * uy - my * ux;
        double[] shells = {4, 9, 18};
        double w = Math.max(px * 0.7, r * 0.05);
        for (double L : shells) {
            double th0 = Math.asin(Math.sqrt(1 / L));
            for (int j = 0; j < 8; j++) {
                double psi = Math.PI * 2 * j / 8 + phase * 0.0;
                double cp = Math.cos(psi), sp = Math.sin(psi);
                double px0 = 0, py0 = 0, pz0 = 0;
                int n = 18;
                for (int k = 0; k <= n; k++) {
                    double th = th0 + (Math.PI - 2 * th0) * k / n;
                    double rr = r * L * Math.sin(th) * Math.sin(th);
                    double along = Math.cos(th) * rr, out = Math.sin(th) * rr;
                    double qx = cx + mx * along + (ux * cp + wx * sp) * out, qy = cy + my * along + (uy * cp + wy * sp) * out,
                            qz = cz + mz * along + (uz * cp + wz * sp) * out;
                    if (k > 0) {
                        Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, px0, py0, pz0, qx, qy, qz, w, w, br * 0.7F, bg * 0.85F, bb,
                                (float) (0.22 / Math.sqrt(L / 4)) * vis, (float) (0.22 / Math.sqrt(L / 4)) * vis);
                    }
                    px0 = qx;
                    py0 = qy;
                    pz0 = qz;
                }
            }
        }
    }
}
