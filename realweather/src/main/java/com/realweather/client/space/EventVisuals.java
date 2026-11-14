package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.resources.ResourceLocation;

import static com.realweather.client.space.Space3D.ADD;
import static com.realweather.client.space.Space3D.NORMAL;

/**
 * The 25 cosmic events, each its own animation with a beginning, middle and end, drawn in 3D round its centre (view
 * space) at a radius R, its frame `rot` (event to view), at a point `p` through it (0 start, 1 end). Every kind moves
 * in its own way: shells that expand and cool, jets, inspirals with spacetime ripples, streams of torn stars,
 * bubbles of plasma, fragments drifting apart, fields flipping, discs forming, galaxies passing through each other.
 */
final class EventVisuals {
    private static final ResourceLocation STAR_MAP = RealWeather.id("textures/environment/space3d/star_surface.png");
    private static final ResourceLocation ROCK = RealWeather.id("textures/environment/space3d/moon.png");
    private static final ResourceLocation PLANET = RealWeather.id("textures/environment/space3d/kepler22b.png");
    private static final ResourceLocation GIANT = RealWeather.id("textures/environment/space3d/saturn.png");
    private static final ResourceLocation MOON = RealWeather.id("textures/environment/space3d/venus.png");

    private EventVisuals() {}

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** 0..1 over [a, b] of p, eased. */
    private static double stage(double p, double a, double b) {
        double t = clamp((p - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    private static double[] at(double[] rot, double cx, double cy, double cz, double x, double y, double z) {
        double[] q = new double[3];
        Space3D.apply(rot, x, y, z, q);
        return new double[]{cx + q[0], cy + q[1], cz + q[2]};
    }

    private static void star(double[] c, double r, float cr, float cg, float cb, float bright, double seconds, float vis) {
        double[] rot = Space3D.orient(0.15, 1, 0.1, seconds * 0.05);
        Space3D.sphere(STAR_MAP, c[0], c[1], c[2], r, rot, 0, 0, 1, 0F, cr * bright, cg * bright, cb * bright, 0F, vis, true,
                (float) (seconds * 0.003 % 1.0), 32);
        Space3D.billboard(Space3D.GLOW, ADD, c[0], c[1], c[2], r * 4, 0F, cr, cg, cb, 0.6F * vis);
    }

    /** A turbulent glowing shell of gas (with fingers of denser gas). */
    private static void shell(double[] c, double[] rot, double rad, double thick, int n, float cr, float cg, float cb, float a, long seed, double px) {
        if (a <= 0.01F || rad <= 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            double z = Space3D.h(seed, i * 4L) * 2 - 1, ph = Space3D.h(seed, i * 4L + 1) * Math.PI * 2, s = Math.sqrt(1 - z * z);
            double rough = 1 + (Space3D.h(seed, i * 4L + 2) - 0.5) * thick * 2;
            double[] q = at(rot, c[0], c[1], c[2], s * Math.cos(ph) * rad * rough, z * rad * rough, s * Math.sin(ph) * rad * rough);
            Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], Math.max(px * 2, rad * (0.06 + 0.08 * Space3D.h(seed, i * 4L + 3))), i, cr, cg, cb, a);
        }
    }

    private static void flash(double[] c, double size, float cr, float cg, float cb, float a) {
        if (a <= 0.01F) {
            return;
        }
        Space3D.billboard(Space3D.STAR, ADD, c[0], c[1], c[2], size * 1.6, 0F, cr, cg, cb, a);
        Space3D.billboard(Space3D.GLOW, ADD, c[0], c[1], c[2], size, 0F, 1F, 1F, 1F, a);
    }

    /** Expanding rings of spacetime ripples round a pair (their gravitational waves). */
    private static void ripples(double[] c, double[] rot, double R, double strength, double phase, double px, float vis) {
        for (int k = 0; k < 6; k++) {
            double rad = R * (0.15 + ((phase * 0.3 + k / 6.0) % 1.0) * 1.6);
            float a = (float) (strength * (1 - rad / (R * 1.8)) * 0.4) * vis;
            double[] prev = null;
            for (int i = 0; i <= 48; i++) {
                double t = Math.PI * 2 * i / 48;
                double lift = Math.cos(2 * (t - phase)) * R * 0.04 * strength;
                double[] q = at(rot, c[0], c[1], c[2], Math.cos(t) * rad, lift, Math.sin(t) * rad);
                if (prev != null) {
                    Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, prev[0], prev[1], prev[2], q[0], q[1], q[2], Math.max(px, R * 0.004), Math.max(px, R * 0.004),
                            0.5F, 0.8F, 1F, a, a);
                }
                prev = q;
            }
        }
    }

    /** Debris flung out from a point: n pieces at their own speeds. */
    private static void spray(double[] c, double[] rot, double R, double t, int n, float cr, float cg, float cb, long seed, double px, float vis) {
        for (int i = 0; i < n; i++) {
            double z = Space3D.h(seed, i * 5L) * 2 - 1, ph = Space3D.h(seed, i * 5L + 1) * Math.PI * 2, s = Math.sqrt(1 - z * z);
            double d = R * t * (0.3 + 0.7 * Space3D.h(seed, i * 5L + 2));
            double[] q = at(rot, c[0], c[1], c[2], s * Math.cos(ph) * d, z * d, s * Math.sin(ph) * d);
            Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.015), 0F, cr, cg, cb, (float) (0.6 * (1 - t)) * vis);
        }
    }

    static void draw(int type, double cx, double cy, double cz, double R, double[] rot, double p, double seconds, double px, long seed, float vis) {
        vis *= com.realweather.ClientConfig.EVENT_INTENSITY.get().floatValue();
        double[] c = {cx, cy, cz};
        double[] axis = {rot[3], rot[4], rot[5]};
        if (p < 0) {
            // Not begun: what is there before (the star, the pair...), quiet.
            p = 0;
        }
        p = Math.min(p, 1.05);
        switch (type) {
            case CosmicEvents.SUPERNOVA, CosmicEvents.HYPERNOVA -> {
                boolean hyper = type == CosmicEvents.HYPERNOVA;
                // A massive star grows unstable (pulsing, flaring), its core collapses, a shock bursts out and the
                // star's outer layers fly off as a glowing, cooling shell; a neutron star (or black hole) is left.
                double collapse = 0.28;
                if (p < collapse + 0.02) {
                    double un = stage(p, 0, collapse);
                    double r = R * (hyper ? 0.08 : 0.16) * (1 + 0.08 * Math.sin(seconds * (2 + un * 8)) * un - (p > collapse ? 0.4 : 0));
                    star(c, r, hyper ? 0.65F : 1F, hyper ? 0.75F : 0.5F, hyper ? 1F : 0.3F, 1.2F + (float) un, seconds, vis);
                }
                double t = stage(p, collapse, 1);
                float burst = (float) Math.exp(-Math.max(0, p - collapse) * 25);
                flash(c, R * (0.5 + 1.5 * burst) * (p > collapse ? 1 : 0), 0.85F, 0.9F, 1F, burst * vis);
                if (p > collapse) {
                    float cool = (float) t;
                    double rad = R * (0.15 + 0.85 * t);
                    // A hypernova's shell is stretched along its axis (by the jets).
                    double[] srot = rot;
                    if (hyper) {
                        srot = rot.clone();
                        for (int i = 3; i < 6; i++) {
                            srot[i] *= 1.6;
                        }
                    }
                    shell(c, srot, rad, 0.12, 420, 1F, 0.95F - 0.45F * cool, 1F - 0.75F * cool, (float) (0.5 * (1 - t * 0.75)) * vis, seed, px);
                    shell(c, srot, rad * 0.6, 0.3, 160, 1F, 0.45F, 0.25F, (float) (0.3 * (1 - t)) * vis, seed + 7, px);
                    if (hyper && p < collapse + 0.15) {
                        Jets.draw(cx, cy, cz, axis, R * 2.5 * stage(p, collapse, collapse + 0.05), R * 0.02, 0.8F, 0.85F, 1F, 0.999, seed, seconds, px, vis);
                    }
                    if (p > collapse + 0.15) {
                        float rem = (float) stage(p, collapse + 0.15, collapse + 0.3);
                        if (hyper) {
                            Space3D.sphere(Space3D.WHITE, cx, cy, cz, R * 0.02, rot, 0, 0, 1, 0F, 0F, 0F, 0F, 0F, rem * vis, false, 0F, 16);
                            Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, R * 0.08, 0F, 1F, 0.7F, 0.4F, 0.5F * rem * vis);
                        } else {
                            Pulsars.draw(cx, cy, cz, R * 0.012, axis, 50, 30, 0.7F, 0.82F, 1F, rem * vis, px, seed, seconds, false);
                        }
                    }
                }
            }
            case CosmicEvents.KILONOVA, CosmicEvents.NS_MERGER -> {
                boolean nova = type == CosmicEvents.KILONOVA;
                double merge = nova ? 0.25 : 0.6;
                double left = clamp((merge - p) / merge);
                double sep = R * 0.3 * Math.pow(left, 0.25);
                double ph = -40 * Math.pow(left, 0.625);
                if (p < merge) {
                    for (int k = 0; k < 2; k++) {
                        double a = ph + k * Math.PI;
                        double[] q = at(rot, cx, cy, cz, Math.cos(a) * sep, 0, Math.sin(a) * sep);
                        star(q, R * 0.02, 0.75F, 0.85F, 1F, 1.5F, seconds, vis);
                    }
                }
                ripples(c, rot, R, p < merge ? 0.3 + 0.7 * (1 - left) : Math.exp(-(p - merge) * 8), ph, px, vis);
                float boom = (float) Math.exp(-Math.abs(p - merge) * 30);
                flash(c, R * 0.8, 0.8F, 0.9F, 1F, boom * vis);
                if (p > merge) {
                    double t = stage(p, merge, 1);
                    // The kilonova: fast blue ejecta towards the poles, slower red ejecta (heavy elements) round the middle.
                    double[] polar = rot.clone();
                    for (int i = 3; i < 6; i++) {
                        polar[i] *= 1.8;
                    }
                    shell(c, polar, R * (0.1 + 0.7 * t), 0.2, 220, 0.6F, 0.75F, 1F, (float) (0.45 * (1 - t)) * vis, seed, px);
                    double[] flat = rot.clone();
                    for (int i = 3; i < 6; i++) {
                        flat[i] *= 0.35;
                    }
                    shell(c, flat, R * (0.08 + 0.45 * t), 0.25, 260, 1F, 0.35F + 0.2F * (float) (1 - t), 0.2F, (float) (0.5 * (1 - t * 0.6)) * vis, seed + 3,
                            px);
                    if (p < merge + 0.05) {
                        Jets.draw(cx, cy, cz, axis, R * 1.8, R * 0.015, 0.8F, 0.85F, 1F, 0.99, seed, seconds, px, vis);
                    }
                }
            }
            case CosmicEvents.BH_MERGER -> Merger.draw(cx, cy, cz, R * 0.6, axis, p * 70, px, vis);
            case CosmicEvents.NS_COLLISION -> {
                // Head on: two neutron stars racing together, the impact, and the spray of hot neutron-rich matter.
                double hit = 0.4;
                if (p < hit) {
                    double d = R * 0.6 * Math.pow(1 - p / hit, 1.5);
                    for (int k = -1; k <= 1; k += 2) {
                        star(at(rot, cx, cy, cz, d * k, 0, 0), R * 0.02, 0.75F, 0.85F, 1F, 1.6F, seconds, vis);
                    }
                }
                float boom = (float) Math.exp(-Math.abs(p - hit) * 25);
                flash(c, R * 1.1, 1F, 0.9F, 0.8F, boom * vis);
                if (p > hit) {
                    double t = stage(p, hit, 1);
                    spray(c, rot, R, t, 260, 1F, 0.55F, 0.3F, seed, px, vis);
                    shell(c, rot, R * (0.05 + 0.6 * t), 0.35, 200, 0.85F, 0.8F, 1F, (float) (0.4 * (1 - t)) * vis, seed + 5, px);
                }
            }
            case CosmicEvents.GRB -> {
                // A massive star collapses; for a few seconds two needle-thin jets outshine whole galaxies, then the
                // afterglow fades as they plough into the gas round it.
                double on = stage(p, 0.15, 0.2) * (1 - stage(p, 0.32, 0.4));
                star(c, R * 0.05 * (1 - stage(p, 0.12, 0.18)), 0.7F, 0.8F, 1F, 1.4F, seconds, vis * (float) (1 - stage(p, 0.15, 0.2)));
                if (on > 0.01) {
                    Jets.draw(cx, cy, cz, axis, R * 3.5, R * 0.01, 0.95F, 0.95F, 1F, 0.9999, seed, seconds * 3, px, (float) on * vis);
                    flash(c, R * 0.6, 0.9F, 0.9F, 1F, (float) on * vis);
                }
                if (p > 0.3) {
                    double t = stage(p, 0.3, 1);
                    shell(c, rot, R * (0.1 + 0.6 * t), 0.15, 200, 1F, 0.75F, 0.5F, (float) (0.35 * (1 - t)) * vis, seed, px);
                }
            }
            case CosmicEvents.FRB -> {
                // A magnetar lets go a burst of radio waves lasting a thousandth of a second; crossing space, its high
                // tones arrive before its low ones (dispersion): drawn as rings of colour, then repeats.
                Pulsars.draw(cx, cy, cz, R * 0.02, axis, 25, 0.5, 0.8F, 0.7F, 1F, vis, px, seed, seconds, true, false, true);
                for (int b = 0; b < 3; b++) {
                    double bt = (p * 3 - b * 0.9);
                    if (bt < 0 || bt > 1) {
                        continue;
                    }
                    flash(c, R * 0.3, 0.9F, 0.85F, 1F, (float) Math.exp(-bt * 40) * vis);
                    float[][] cols = {{0.6F, 0.3F, 1F}, {0.3F, 0.6F, 1F}, {0.3F, 1F, 0.6F}, {1F, 0.9F, 0.3F}, {1F, 0.35F, 0.25F}};
                    for (int k = 0; k < 5; k++) {
                        double rad = R * (bt * 1.4 - k * 0.06);
                        if (rad <= 0) {
                            continue;
                        }
                        double[] prev = null;
                        for (int i = 0; i <= 56; i++) {
                            double t = Math.PI * 2 * i / 56;
                            double[] q = at(rot, cx, cy, cz, Math.cos(t) * rad, 0, Math.sin(t) * rad);
                            if (prev != null) {
                                Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, prev[0], prev[1], prev[2], q[0], q[1], q[2], Math.max(px, R * 0.006),
                                        Math.max(px, R * 0.006), cols[k][0], cols[k][1], cols[k][2], (float) (0.5 * (1 - bt)) * vis,
                                        (float) (0.5 * (1 - bt)) * vis);
                            }
                            prev = q;
                        }
                    }
                }
            }
            case CosmicEvents.TDE -> {
                // A star strays too close to a black hole: stretched into a long stream, half of it falls in and
                // flares up as a new disc, the rest is flung away.
                double[] n = axis;
                CloseViews.lensed(cx, cy, cz, R * 0.04, n, (float) seconds, vis, 0.6F + 1.4F * (float) stage(p, 0.45, 0.55) * (float) (1 - stage(p, 0.7, 1)),
                        12, 160F);
                if (p < 0.35) {
                    double d = R * (0.9 - 2.0 * p);
                    star(at(rot, cx, cy, cz, d, 0, R * 0.25 * (1 - p * 2.5)), R * 0.04 * (1 + p * 3), 1F, 0.85F, 0.6F, 1.2F, seconds, vis);
                }
                if (p > 0.25) {
                    double t = stage(p, 0.25, 1);
                    for (int i = 0; i < 160; i++) {
                        double s = Space3D.h(seed, i) * 2 - 1;
                        double ang = s * Math.PI * (0.5 + t);
                        double rad = R * (0.12 + 0.6 * Math.abs(s) * t + 0.1);
                        double[] q = at(rot, cx, cy, cz, Math.cos(ang) * rad, 0, Math.sin(ang) * rad * (s < 0 ? 0.6 : 1.2));
                        Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.025), 0F, 1F, 0.75F, 0.45F, (float) (0.4 * (1 - t * 0.6)) * vis);
                    }
                    flash(c, R * 0.6 * stage(p, 0.4, 0.5), 1F, 0.9F, 0.75F, (float) (0.7 * stage(p, 0.4, 0.5) * (1 - stage(p, 0.55, 1))) * vis);
                }
            }
            case CosmicEvents.STELLAR_FLARE, CosmicEvents.CME, CosmicEvents.PROMINENCE -> {
                boolean red = type == CosmicEvents.STELLAR_FLARE;
                double r = R * 0.3;
                double[] srot = Space3D.orient(axis[0], axis[1], axis[2], seconds * 0.02);
                star(c, r, 1F, red ? 0.45F : 0.86F, red ? 0.25F : 0.6F, 1.2F, seconds, vis);
                SolarActivity.draw(cx, cy, cz, r, srot, 1F, red ? 0.45F : 0.86F, red ? 0.25F : 0.6F, seed, seconds, px, vis);
                double[] side = at(rot, 0, 0, 0, 1, 0.35, 0.2);
                double sl = Math.sqrt(side[0] * side[0] + side[1] * side[1] + side[2] * side[2]);
                double[] up = {side[0] / sl, side[1] / sl, side[2] / sl};
                double[] foot = {cx + up[0] * r, cy + up[1] * r, cz + up[2] * r};
                if (red) {
                    float f = (float) (stage(p, 0.2, 0.25) * Math.exp(-Math.max(0, p - 0.25) * 6));
                    flash(foot, r * (0.4 + 1.6 * f), 1F, 0.95F, 0.9F, f * vis);
                    flash(c, r * 4, 1F, 0.55F, 0.4F, 0.4F * f * vis);
                } else {
                    // A bubble of plasma (bright front, dark cavity, bright core) or a prominence lifting off.
                    double t = stage(p, 0.15, 1);
                    double h = r * (0.2 + 6 * t * t);
                    double width = r * (0.25 + 2.2 * t);
                    double[] e1 = {rot[0], rot[1], rot[2]};
                    for (int i = 0; i < 64; i++) {
                        double a = Math.PI * i / 63;
                        double ox = Math.cos(a) * width, oy = Math.sin(a) * h;
                        double[] q = {foot[0] + e1[0] * ox + up[0] * oy, foot[1] + e1[1] * ox + up[1] * oy, foot[2] + e1[2] * ox + up[2] * oy};
                        boolean front = type == CosmicEvents.CME;
                        Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], Math.max(px * 2, width * (front ? 0.12 : 0.08)), 0F, front ? 1F : 1F,
                                front ? 0.9F : 0.4F, front ? 0.8F : 0.25F, (float) (0.5 * (1 - t * 0.7)) * vis);
                    }
                    if (type == CosmicEvents.CME) {
                        double[] core = {foot[0] + up[0] * h * 0.45, foot[1] + up[1] * h * 0.45, foot[2] + up[2] * h * 0.45};
                        Space3D.billboard(Space3D.GLOW, NORMAL, core[0], core[1], core[2], width * 0.8, 0F, 0F, 0F, 0F, (float) (0.35 * (1 - t)) * vis);
                        Space3D.billboard(Space3D.GLOW, ADD, core[0], core[1], core[2], width * 0.25, 0F, 1F, 0.5F, 0.4F, (float) (0.5 * (1 - t)) * vis);
                    }
                }
            }
            case CosmicEvents.MAGNETAR_FLARE, CosmicEvents.PULSAR_GLITCH -> {
                boolean flare = type == CosmicEvents.MAGNETAR_FLARE;
                double rate = flare ? 0.13 : 11 * (p > 0.5 ? 1.15 : 1);
                // A glitch: the star suddenly spins faster (its beams jump ahead).
                double extra = !flare && p > 0.5 ? 2.5 : 0;
                Pulsars.draw(cx, cy, cz, R * 0.03, axis, flare ? 25 : 60, rate, 0.75F, 0.8F, 1F, vis, px, seed, seconds + extra, true, !flare, flare);
                if (flare) {
                    float spike = (float) Math.exp(-Math.abs(p - 0.25) * 40);
                    flash(c, R * 1.5, 0.85F, 0.85F, 1F, spike * vis);
                    if (p > 0.25) {
                        double t = stage(p, 0.25, 1);
                        float tail = (float) ((0.5 + 0.5 * Math.sin(seconds * Math.PI * 2 / 7.5)) * (1 - t));
                        flash(c, R * 0.3, 0.8F, 0.75F, 1F, 0.5F * tail * vis);
                        shell(c, rot, R * (0.05 + 0.7 * t), 0.2, 180, 0.7F, 0.6F, 1F, (float) (0.3 * (1 - t)) * vis, seed, px);
                    }
                } else if (p > 0.5 && p < 0.6) {
                    flash(c, R * 0.25, 0.8F, 0.9F, 1F, (float) (1 - (p - 0.5) * 10) * vis);
                }
            }
            case CosmicEvents.ROGUE_CAPTURE -> {
                // A dark planet adrift between the stars comes in on a hyperbola, loses energy to a planet there and is
                // caught on a long, stretched orbit.
                star(c, R * 0.06, 1F, 0.9F, 0.7F, 1.3F, seconds, vis);
                double[] q;
                if (p < 0.5) {
                    double s = (p - 0.5) * 4;
                    q = at(rot, cx, cy, cz, R * (0.3 + s * s * 0.5), 0, R * s * 0.9);
                } else {
                    double ang = (p - 0.5) * Math.PI * 6;
                    double a = R * 0.55, e = 0.65;
                    double rr = a * (1 - e * e) / (1 + e * Math.cos(ang));
                    q = at(rot, cx, cy, cz, Math.cos(ang) * rr, 0, Math.sin(ang) * rr);
                }
                double[] l = {cx - q[0], cy - q[1], cz - q[2]};
                double ll = Math.sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2]) + 1.0E-12;
                Space3D.sphere(ROCK, q[0], q[1], q[2], R * 0.025, rot, l[0] / ll, l[1] / ll, l[2] / ll, 0.01F, 0.5F, 0.45F, 0.5F, 0F, vis, false, 0F, 20);
                Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], R * 0.05, 0F, 0.6F, 0.7F, 1F, 0.25F * vis);
            }
            case CosmicEvents.COMET_BREAKUP -> {
                double[] sun = at(rot, 0, 0, 0, 1, 0.1, 0.3);
                double sl = Math.sqrt(sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]);
                sun = new double[]{sun[0] / sl, sun[1] / sl, sun[2] / sl};
                double[] vel = {rot[6], rot[7], rot[8]};
                int pieces = p < 0.3 ? 1 : 6;
                for (int k = 0; k < pieces; k++) {
                    double t = stage(p, 0.3, 1);
                    double ox = (Space3D.h(seed, k) - 0.5) * R * 0.5 * t, oy = (Space3D.h(seed, k + 9) - 0.5) * R * 0.2 * t, oz = (Space3D.h(seed, k + 19) - 0.5)
                            * R * 0.5 * t - R * 0.25 * t * k / 6.0;
                    double[] q = at(rot, cx, cy, cz, ox, oy, oz);
                    Comets.draw(q[0], q[1], q[2], R * (k == 0 ? 0.05 : 0.025), sun, vel, R * (k == 0 ? 1.4 : 0.7), seed + k, seconds, px,
                            vis * (k == 0 ? 1F : 0.7F));
                }
                if (p > 0.3) {
                    spray(c, rot, R * 0.5, stage(p, 0.3, 1), 120, 1F, 0.9F, 0.75F, seed, px, vis * 0.6F);
                }
            }
            case CosmicEvents.ASTEROID_COLLISION -> {
                double hit = 0.35;
                if (p < hit) {
                    double d = R * 0.5 * (1 - p / hit);
                    for (int k = -1; k <= 1; k += 2) {
                        double[] q = at(rot, cx, cy, cz, d * k, d * 0.2 * k, 0);
                        Space3D.scale(1.3, 0.8, 1);
                        Space3D.sphere(ROCK, q[0], q[1], q[2], R * (k < 0 ? 0.06 : 0.04), Space3D.orient(0.3, 1, 0.2, seconds * 0.3 * k), 0.6, 0.5, 0.6, 0.02F,
                                0.6F, 0.55F, 0.5F, 0F, vis, false, 0F, 20);
                        Space3D.scale(1, 1, 1);
                    }
                }
                flash(c, R * 0.6, 1F, 0.85F, 0.6F, (float) Math.exp(-Math.abs(p - hit) * 30) * vis);
                if (p > hit) {
                    double t = stage(p, hit, 1);
                    spray(c, rot, R * 0.9, t, 200, 0.75F, 0.65F, 0.55F, seed, px, vis);
                    for (int k = 0; k < 9; k++) {
                        double z = Space3D.h(seed, k * 3L + 500) * 2 - 1, ph = Space3D.h(seed, k * 3L + 501) * Math.PI * 2, s = Math.sqrt(1 - z * z);
                        double d = R * 0.6 * t * (0.3 + 0.7 * Space3D.h(seed, k * 3L + 502));
                        double[] q = at(rot, cx, cy, cz, s * Math.cos(ph) * d, z * d, s * Math.sin(ph) * d);
                        Space3D.sphere(ROCK, q[0], q[1], q[2], R * 0.012, Space3D.orient(0.3, 1, 0.2, seconds * (k + 1) * 0.2), 0.6, 0.5, 0.6, 0.02F, 0.6F, 0.55F,
                                0.5F, 0F, vis, false, 0F, 10);
                    }
                }
            }
            case CosmicEvents.RING_DISRUPTION -> {
                // A ringed planet; something crosses its rings and scatters them into clumps and waves.
                double r = R * 0.25;
                double[] sun = {0.6, 0.3, 0.7};
                Space3D.sphere(GIANT, cx, cy, cz, r, rot, sun[0], sun[1], sun[2], 0.02F, 1F, 1F, 1F, 0F, vis, false, (float) (seconds * 0.01 % 1.0), 40);
                double t = stage(p, 0.3, 1);
                for (int i = 0; i < 500; i++) {
                    double rad = r * (1.3 + 1.1 * Space3D.h(seed, i));
                    double ang = Space3D.h(seed, i + 900) * Math.PI * 2 + seconds * 0.3 * Math.pow(rad / r, -1.5);
                    double kick = t * Math.sin(ang * 3 + seconds * 0.5) * r * 0.25 * Space3D.h(seed, i + 1800);
                    double y = t * (Space3D.h(seed, i + 2700) - 0.5) * r * 0.4;
                    double[] q = at(rot, cx, cy, cz, Math.cos(ang) * (rad + kick), y, Math.sin(ang) * (rad + kick));
                    Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], Math.max(px * 1.2, r * 0.03), 0F, 0.95F, 0.88F, 0.75F, 0.35F * vis);
                }
                if (p > 0.2 && p < 0.5) {
                    double s = (p - 0.35) / 0.15;
                    double[] q = at(rot, cx, cy, cz, r * 2, s * r * 0.8, r * (0.5 - s));
                    Space3D.sphere(ROCK, q[0], q[1], q[2], r * 0.08, rot, sun[0], sun[1], sun[2], 0.02F, 0.7F, 0.65F, 0.6F, 0F, vis, false, 0F, 16);
                }
            }
            case CosmicEvents.EXOMOON_ERUPTION -> {
                // A moon of a giant planet: a huge eruption, its plume rising hundreds of kilometres, lava spreading.
                double[] giant = at(rot, cx, cy, cz, -R * 0.7, R * 0.1, -R * 0.6);
                Space3D.sphere(GIANT, giant[0], giant[1], giant[2], R * 0.4, rot, 0.6, 0.4, 0.7, 0.02F, 0.9F, 0.75F, 0.6F, 0F, vis, false, 0F, 40);
                double r = R * 0.12;
                Space3D.sphere(MOON, cx, cy, cz, r, rot, 0.6, 0.4, 0.7, 0.02F, 1F, 0.95F, 0.6F, 0F, vis, false, 0F, 32);
                double t = stage(p, 0.1, 0.4) * (1 - stage(p, 0.8, 1) * 0.7);
                double[] up = {rot[3], rot[4], rot[5]}, e1 = {rot[0], rot[1], rot[2]}, e2 = {rot[6], rot[7], rot[8]};
                Plumes.plume(cx + up[0] * r, cy + up[1] * r, cz + up[2] * r, up, e1, e2, r * 3.5 * t, Plumes.UMBRELLA, seed, seconds, px, (float) t * vis, 1F,
                        r * 0.1);
                Space3D.billboard(Space3D.GLOW, ADD, cx + up[0] * r, cy + up[1] * r, cz + up[2] * r, r * (0.2 + 0.6 * t), 0F, 1F, 0.4F, 0.1F, (float) (0.7 * t) * vis);
            }
            case CosmicEvents.ATMOSPHERE_COLLAPSE -> {
                // A world's air freezing out on to its ground: the blue haze round it thins and frost spreads.
                double r = R * 0.35;
                double t = stage(p, 0.1, 1);
                double[] sun = {0.7, 0.3, 0.6};
                Space3D.sphere(PLANET, cx, cy, cz, r, rot, sun[0], sun[1], sun[2], 0.02F, 1F, 1F, 1F, 0F, vis, false, (float) (seconds * 0.005 % 1.0), 48);
                Space3D.sphere(Space3D.WHITE, cx, cy, cz, r * 1.002, rot, sun[0], sun[1], sun[2], 0.05F, 0.92F, 0.95F, 1F, 0F, (float) (0.85 * t) * vis, false, 0F,
                        48);
                Space3D.atmosphere(cx, cy, cz, r, sun[0], sun[1], sun[2], 0.45F, 0.65F, 1F, (float) (0.6 * (1 - t)) * vis);
            }
            case CosmicEvents.FIELD_REVERSAL -> {
                // A planet's magnetic field weakening, tangling into many poles, then flipping; aurorae wander.
                double r = R * 0.3;
                double[] sun = {0.7, 0.3, 0.6};
                Space3D.sphere(PLANET, cx, cy, cz, r, rot, sun[0], sun[1], sun[2], 0.02F, 1F, 1F, 1F, 0F, vis, false, (float) (seconds * 0.005 % 1.0), 48);
                double weak = 1 - Math.sin(Math.PI * clamp(p)) * 0.85;
                boolean flipped = p > 0.5;
                double tangle = Math.sin(Math.PI * clamp(p));
                for (int k = 0; k < 14; k++) {
                    double phi = Math.PI * 2 * k / 14;
                    double L = 2.2 + (k % 3) * 0.7;
                    double[] prev = null;
                    for (int i = 0; i <= 24; i++) {
                        double th = Math.asin(Math.sqrt(1 / L)) + (Math.PI - 2 * Math.asin(Math.sqrt(1 / L))) * i / 24;
                        double rr = r * L * Math.sin(th) * Math.sin(th);
                        double wob = tangle * Math.sin(th * 5 + k + seconds * 0.4) * 0.4;
                        double[] q = at(rot, cx, cy, cz, Math.cos(phi + wob) * Math.sin(th) * rr, Math.cos(th) * rr, Math.sin(phi + wob) * Math.sin(th) * rr);
                        if (prev != null) {
                            float a = (float) (0.35 * weak) * vis;
                            Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, prev[0], prev[1], prev[2], q[0], q[1], q[2], Math.max(px, r * 0.01), Math.max(px, r * 0.01),
                                    flipped ? 1F : 0.4F, 0.6F, flipped ? 0.4F : 1F, a, a);
                        }
                        prev = q;
                    }
                }
                Aurorae.draw("earth_wander", cx, cy, cz, r, new double[]{rot[3], rot[4], rot[5]}, sun, seconds, px, vis);
            }
            case CosmicEvents.DISK_FORMATION -> {
                // A cloud of gas collapsing, spinning faster as it shrinks into a disc round a new star; jets of gas
                // shoot out of its poles (Herbig-Haro objects).
                double t = stage(p, 0, 1);
                for (int i = 0; i < 500; i++) {
                    double z = Space3D.h(seed, i) * 2 - 1, ph = Space3D.h(seed, i + 600) * Math.PI * 2, s = Math.sqrt(1 - z * z);
                    double rad = R * (0.9 - 0.6 * t) * (0.3 + 0.7 * Space3D.h(seed, i + 1200));
                    double flat = 1 - 0.92 * t;
                    double spin = ph + seconds * 0.1 * (1 + 4 * t) * Math.pow(Math.max(0.1, rad / R), -1.5) * 0.2;
                    double[] q = at(rot, cx, cy, cz, Math.cos(spin) * s * rad, z * rad * flat, Math.sin(spin) * s * rad);
                    Space3D.billboard(Space3D.GLOW, i % 3 == 0 ? NORMAL : ADD, q[0], q[1], q[2], Math.max(px * 1.5, R * 0.03), 0F, i % 3 == 0 ? 0.08F : 1F,
                            i % 3 == 0 ? 0.05F : 0.6F, i % 3 == 0 ? 0.03F : 0.4F, i % 3 == 0 ? 0.3F * vis : 0.18F * vis);
                }
                star(c, R * 0.02 * (0.3 + t), 1F, 0.75F, 0.5F, 1.2F, seconds, (float) t * vis);
                if (t > 0.4) {
                    for (int sg = -1; sg <= 1; sg += 2) {
                        for (int k = 0; k < 6; k++) {
                            double f = ((seconds * 0.05 + k / 6.0) % 1.0);
                            double[] q = {cx + axis[0] * R * f * sg, cy + axis[1] * R * f * sg, cz + axis[2] * R * f * sg};
                            Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], R * 0.04, 0F, 1F, 0.45F, 0.5F, (float) (0.5 * (1 - f) * (t - 0.4) / 0.6) * vis);
                        }
                    }
                }
            }
            case CosmicEvents.STARBURST -> {
                // In a great cloud of gas and dust, new stars igniting one after another, blowing bubbles in it.
                for (int i = 0; i < 300; i++) {
                    double[] q = at(rot, cx, cy, cz, (Space3D.h(seed, i) - 0.5) * R * 1.6, (Space3D.h(seed, i + 400) - 0.5) * R * 0.8,
                            (Space3D.h(seed, i + 800) - 0.5) * R * 1.6);
                    boolean dust = i % 2 == 0;
                    Space3D.billboard(Space3D.GLOW, dust ? NORMAL : ADD, q[0], q[1], q[2], R * (0.08 + 0.1 * Space3D.h(seed, i + 1200)), i, dust ? 0.06F : 1F,
                            dust ? 0.04F : 0.4F, dust ? 0.03F : 0.55F, dust ? 0.25F * vis : 0.12F * vis);
                }
                for (int i = 0; i < 90; i++) {
                    double born = Space3D.h(seed, i + 2000) * 0.8;
                    if (p < born) {
                        continue;
                    }
                    double age = (p - born) / 0.2;
                    double[] q = at(rot, cx, cy, cz, (Space3D.h(seed, i + 2100) - 0.5) * R * 1.2, (Space3D.h(seed, i + 2200) - 0.5) * R * 0.5,
                            (Space3D.h(seed, i + 2300) - 0.5) * R * 1.2);
                    flash(q, R * 0.05 * Math.min(1, age), 0.65F, 0.75F, 1F, (float) Math.min(1, age) * 0.7F * vis);
                    if (age > 0.5) {
                        shell(q, rot, R * 0.06 * Math.min(3, age), 0.1, 30, 1F, 0.45F, 0.55F, 0.2F * vis, seed + i, px);
                    }
                }
            }
            case CosmicEvents.GALAXY_COLLISION, CosmicEvents.GALAXY_MERGER -> {
                // Two galaxies falling together: they pass through each other, their stars pulled out into long tidal
                // tails; at last they merge into one, a burst of star birth at its heart.
                boolean merger = type == CosmicEvents.GALAXY_MERGER;
                Galaxies.Look a = Galaxies.look("whirlpool"), b = Galaxies.look("bodes");
                double t = clamp(p);
                double sep = merger ? R * 0.5 * (1 - stage(p, 0, 0.7)) : R * (0.9 - 1.6 * t);
                double swing = merger ? t * 4 : t * 1.5;
                double[] pa = at(rot, cx, cy, cz, Math.cos(swing) * sep, 0, Math.sin(swing) * sep * 0.5);
                double[] pb = at(rot, cx, cy, cz, -Math.cos(swing) * sep, R * 0.05, -Math.sin(swing) * sep * 0.5);
                double size = R * (merger && p > 0.7 ? 0.5 : 0.35);
                if (!(merger && p > 0.75)) {
                    Galaxies.draw(a, pa[0], pa[1], pa[2], size, Galaxies.frame(a, rot), seconds, px, vis);
                    Galaxies.draw(b, pb[0], pb[1], pb[2], size * 0.8, Galaxies.frame(b, rot), seconds, px, vis);
                }
                // Tidal tails.
                double tails = merger ? 0.6 + 0.4 * t : stage(p, 0.35, 1);
                for (int k = 0; k < 2; k++) {
                    double[] from = k == 0 ? pa : pb;
                    for (int i = 0; i < 160; i++) {
                        double s = Space3D.h(seed, k * 1000L + i);
                        double ang = (k == 0 ? 1 : -1) * (swing + s * 2.2);
                        double rad = size * (0.5 + 2.5 * s * tails);
                        double[] q = at(rot, 0, 0, 0, Math.cos(ang) * rad, (s - 0.5) * size * 0.2, Math.sin(ang) * rad * 0.6);
                        Space3D.billboard(Space3D.GLOW, ADD, from[0] + q[0], from[1] + q[1], from[2] + q[2], Math.max(px * 1.5, size * 0.04), 0F, 0.75F, 0.82F,
                                1F, (float) (0.25 * tails * (1 - s * 0.6)) * vis);
                    }
                }
                if (merger && p > 0.6) {
                    double m = stage(p, 0.6, 1);
                    Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, R * 0.6, 0F, 1F, 0.85F, 0.65F, (float) (0.5 * m) * vis);
                    Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, R * 0.2, 0F, 1F, 0.6F, 0.7F, (float) (0.6 * m * (1 - m * 0.5)) * vis);
                    for (int k = 1; k <= 3; k++) {
                        shell(c, rot, R * 0.15 * k * (0.6 + 0.4 * m), 0.04, 90, 1F, 0.9F, 0.75F, (float) (0.12 * m) * vis, seed + k, px);
                    }
                }
            }
            default -> {
            }
        }
    }
}
