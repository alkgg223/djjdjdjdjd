package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.resources.ResourceLocation;

import static com.realweather.client.space.Space3D.ADD;

/**
 * Things seen from close by, for the planet map and for really being there: a star's boiling face with its
 * prominences, loops, spicules, flares and corona; a pulsar's spinning beams, field, jets and starquakes; the black
 * hole ray traced from where you are, with its relativistic jets; a nebula's gas all round you (the supernova's shock
 * front sweeping out); a comet's tails; two black holes merging in rippling spacetime; and planets with their storms,
 * rings of particles, aurorae and plumes.
 */
final class CloseViews {
    private static final ResourceLocation STAR_MAP = RealWeather.id("textures/environment/space3d/star_surface.png");
    private static final ResourceLocation MOON_MAP = RealWeather.id("textures/environment/space3d/moon.png");
    private static final ResourceLocation RINGS = RealWeather.id("textures/environment/space3d/rings.png");

    private CloseViews() {}

    /** How big (view units) an object is drawn up close. */
    static double radius(Catalog.Body b) {
        return switch (b.kind) {
            case Catalog.PULSAR -> 20;
            case Catalog.BLACKHOLE -> 10;
            case Catalog.NEBULA -> 400;
            case Catalog.COMET -> 30;
            case Catalog.MERGER -> 40;
            default -> 100;
        };
    }

    /** How far off (in its radii) a visit starts. */
    static double start(Catalog.Body b) {
        return switch (b.kind) {
            case Catalog.PULSAR -> b.magnetar ? 7 : 9;
            case Catalog.BLACKHOLE -> 30;
            case Catalog.NEBULA -> 1.7;
            case Catalog.COMET -> 6;
            case Catalog.MERGER -> 9;
            default -> 3.6;
        };
    }

    /** How close (in its radii) you can come; nearer than `deadly` kills. */
    static double closest(Catalog.Body b) {
        return switch (b.kind) {
            case Catalog.PULSAR -> 1.6;
            case Catalog.BLACKHOLE -> 2.2;
            case Catalog.NEBULA -> 0.0;
            case Catalog.COMET -> 0.2;
            case Catalog.MERGER -> 1.0;
            default -> 1.12;
        };
    }

    /** Nearer than this (radii) is the end of you; and what kills you (the hazard sent to the server). */
    static double deadly(Catalog.Body b) {
        return switch (b.kind) {
            case Catalog.PULSAR -> 1.8;
            case Catalog.BLACKHOLE -> 2.4;
            case Catalog.MERGER -> 1.2;
            case Catalog.STAR, Catalog.DWARF -> 1.15;
            default -> -1;
        };
    }

    static int hazard(Catalog.Body b) {
        return b.kind == Catalog.BLACKHOLE || b.kind == Catalog.MERGER ? 1 : b.kind == Catalog.PULSAR ? 2 : 0;
    }

    /**
     * The object at the origin of its frame. view: frame to view; cam: the camera in the frame (view units); R: its
     * radius (view units); f, right, cu: the camera's forward, right and up (frame); px: view units per pixel at the
     * object; tx, ty: tangents of half the field of view.
     */
    static void object(Catalog.Body body, double[] view, double[] cam, double R, double[] f, double[] right, double[] cu, double px, double tx,
                       double ty, double clock) {
        double[] c = new double[3];
        Space3D.apply(view, -cam[0], -cam[1], -cam[2], c);
        double dist = Math.sqrt(cam[0] * cam[0] + cam[1] * cam[1] + cam[2] * cam[2]);
        Space3D.clearDepth();
        Space3D.range((float) Math.max(0.05, (dist - R * 4) * 0.3), (float) (dist + R * 80));
        long seed = body.id.hashCode();
        double[] dx = {right[0] * tx, right[1] * tx, right[2] * tx}, dy = {cu[0] * ty, cu[1] * ty, cu[2] * ty};
        switch (body.kind) {
            case Catalog.PULSAR -> {
                double[] axis = unit(view, 0.2, 1, 0.1);
                Pulsars.draw(c[0], c[1], c[2], R, axis, body.tiltDeg, body.spinHz, body.pr, body.pg, body.pb, 1F, px, seed, clock, true, body.jets,
                        body.magnetar);
            }
            case Catalog.BLACKHOLE -> {
                double[] o = {cam[0] / R, cam[1] / R, cam[2] / R};
                double[] n = {0.12, 0.98, 0.16};
                double nl = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
                n[0] /= nl;
                n[1] /= nl;
                n[2] /= nl;
                double[] nv = unit(view, n[0], n[1], n[2]);
                SpaceShaders.blackHole(-1F, -1F, 1F, 1F, o, new double[3], new double[3], f, dx, dy, n, (float) clock, 1F, 0F, exposure(body), 420F);
                // The great cluster of bright stars swarming round it (some with planets), and its jets, over the lensed
                // sky; what is behind the hole's shadow stays hidden.
                Space3D.clearDepth();
                Space3D.range((float) Math.max(0.05, R * 0.2), (float) (dist + R * 1000));
                Space3D.depthSphere(c[0], c[1], c[2], R * 2.6);
                double[] hole = new double[9];
                Space3D.mul(view, Space3D.orient(n[0], n[1], n[2], 0), hole);
                StarSwarm.draw(c[0], c[1], c[2], R, hole, seed, 260, 18, 700, clock, px, 1F);
                if (body.jets) {
                    Jets.draw(c[0], c[1], c[2], nv, R * 700, R * 14, 0.6F, 0.74F, 1F, 0.98, seed, clock, px, 1F);
                }
            }
            case Catalog.NEBULA -> nebula(body, c, view, cam, R, f, dx, dy, px, clock);
            case Catalog.COMET -> {
                double[] sun = unit(view, 1, 0.12, 0.35), vel = unit(view, -0.3, 0.05, 1);
                // The nucleus: a dark, lumpy rock a few kilometres across.
                double[] rot = Space3D.orient(0.3, 1, 0.2, clock * 0.05);
                double[] rv = new double[9];
                Space3D.mul(view, rot, rv);
                Space3D.scale(1.3, 0.8, 1);
                Space3D.sphere(MOON_MAP, c[0], c[1], c[2], R * 0.06, rv, sun[0], sun[1], sun[2], 0.02F, 0.35F, 0.33F, 0.3F, 0F, 1F, false, 0F, 24);
                Space3D.scale(1, 1, 1);
                Comets.draw(c[0], c[1], c[2], R * 0.5, sun, vel, R * 60, seed, clock, px, 1F);
            }
            case Catalog.MERGER -> Merger.draw(c[0], c[1], c[2], R, unit(view, 0.15, 1, 0.1), clock, px, 1F);
            default -> star(body, c, view, R, seed, clock, px, 1F);
        }
    }

    /** How bright a black hole's disc glows: TON 618 a blazing quasar, M87* bright, Sagittarius A* dim and quiet. */
    static float exposure(Catalog.Body b) {
        return switch (b.id) {
            case "sgr_a_star" -> 0.55F;
            case "m87_star" -> 0.9F;
            default -> 1.15F;
        };
    }

    /** A star up close: its face (ray traced), activity, fire and corona. */
    static void star(Catalog.Body body, double[] c, double[] view, double R, long seed, double clock, double px, float vis) {
        double[] axis = unit(view, 0.1, 1, 0.15);
        double[] rot = Space3D.orient(axis[0], axis[1], axis[2], clock * 0.01);
        Space3D.scale(1, body.oblate, 1);
        Space3D.sphere(STAR_MAP, c[0], c[1], c[2], R, rot, 0, 0, 1, 0F, body.r * 1.25F, body.g * 1.25F, body.b * 1.25F, 0F, vis, true,
                (float) (clock * 0.003 % 1.0), 72);
        Space3D.scale(1, 1, 1);
        if (body.oblate >= 0.95F && body.kind == Catalog.STAR) {
            SolarActivity.draw(c[0], c[1], c[2], R, rot, body.r, body.g, body.b, seed, clock, px, vis);
        }
        Space3D.billboard(Space3D.GLOW, ADD, c[0], c[1], c[2], R * 3.2, 0F, body.r, body.g, body.b, 0.45F * vis);
        if (body.kind == Catalog.STAR) {
            Space3D.starFire(c[0], c[1], c[2], R, seed, 90, body.b, (float) (clock * 20), 0.6F * vis);
            TelescopeView.corona(c[0], c[1], c[2], R, seed, 0.55F * vis);
        }
    }

    private static void nebula(Catalog.Body body, double[] c, double[] view, double[] cam, double R, double[] f, double[] dx, double[] dy, double px,
                               double clock) {
        // (The supernova goes on whether or not you are watching.)
        Nebulae.Recipe rc = Nebulae.at(body.recipe, System.currentTimeMillis() % 100000000L / 1000.0);
        if (rc == null) {
            return;
        }
        double[] o = {cam[0] / R, cam[1] / R, cam[2] / R};
        SpaceShaders.nebula(rc, -1F, -1F, 1F, 1F, o, new double[3], new double[3], f, dx, dy, 110F, 6F, 1F);
        double[] q = new double[3];
        for (Nebulae.Stars st : rc.stars) {
            long sd = (body.id + st.hashCode()).hashCode();
            for (int i = 0; i < st.count(); i++) {
                double sx = st.x() + (Space3D.h(sd, i * 3L) - 0.5) * 2 * st.spread(), sy = st.y() + (Space3D.h(sd, i * 3L + 1) - 0.5) * 2 * st.spread(),
                        sz = st.z() + (Space3D.h(sd, i * 3L + 2) - 0.5) * st.spread();
                Space3D.apply(view, sx * R, sy * R, sz * R, q);
                float br = st.bright() * (0.4F + 0.6F * Space3D.h(sd, i * 3L + 7));
                Space3D.billboard(Space3D.GLOW, ADD, c[0] + q[0], c[1] + q[1], c[2] + q[2], Math.max(px * 2, R * 0.012 * Math.min(br, 6F)), 0F, st.r(),
                        st.g(), st.b(), Math.min(1F, br));
                Space3D.billboard(Space3D.STAR, ADD, c[0] + q[0], c[1] + q[1], c[2] + q[2], Math.max(px * 3, R * 0.03 * Math.min(br, 12F)), 0F, st.r(),
                        st.g(), st.b(), Math.min(1F, 0.6F * br));
            }
        }
    }

    /**
     * A planet or moon seen from space, made of what it is: clouds of a gas giant (its winds and storms), its rings of
     * particles, aurorae, plumes, air. c: centre (view), R: radius (view units), rot: its frame to view (Y its axis),
     * sun: view direction towards its star, light colour, seconds, view units per pixel there.
     */
    static void planet(Catalog.Body b, double[] c, double R, double[] rot, double[] sun, float lr, float lg, float lb, double seconds, double px,
                       float vis, float uShift) {
        double rPix = R / px;
        Space3D.scale(1, b.oblate, 1);
        Space3D.sphere(b.map, c[0], c[1], c[2], R, rot, sun[0], sun[1], sun[2], 0.012F, b.r, b.g, b.b, b.glow, vis, false, uShift,
                (int) Math.max(16, Math.min(72, rPix / 3)));
        Space3D.scale(1, 1, 1);
        if (b.kind == Catalog.GAS && rPix > 20) {
            Trace t = Trace.sphere(c[0], c[1], c[2], R, 1.06, rot);
            if (t != null) {
                SpaceShaders.GasLook g = GasGiants.look(b, seconds);
                double[] lt = new double[3];
                Trace.local(rot, sun[0], sun[1], sun[2], lt);
                g.lx = lt[0];
                g.ly = lt[1];
                g.lz = lt[2];
                g.lightR = lr;
                g.lightG = lg;
                g.lightB = lb;
                g.pixel = (float) (1 / rPix);
                g.alpha = Space3D.smooth(20F, 45F, (float) rPix) * vis;
                SpaceShaders.gasPlanet(g, t.x0, t.y0, t.x1, t.y1, t.o, t.ox, t.oy, t.d, t.dx, t.dy);
            }
        }
        if (b.rings) {
            rings(b, c, R, rot, sun, seconds, px, vis);
        }
        if (b.air) {
            Space3D.atmosphere(c[0], c[1], c[2], R, sun[0], sun[1], sun[2], b.ar, b.ag, b.ab, (b.kind == Catalog.GAS ? 0.4F : 0.55F) * vis);
        }
        double[] axis = {rot[3], rot[4], rot[5]};
        Aurorae.draw(b.id, c[0], c[1], c[2], R, axis, sun, seconds, px, vis);
        Plumes.draw(b.id, c[0], c[1], c[2], R, rot, sun, seconds, px, vis);
        if (b.halo > 0) {
            Space3D.billboard(Space3D.GLOW, ADD, c[0], c[1], c[2], R * 2.6, 0F, b.hr, b.hg, b.hb, b.halo * vis);
        }
    }

    /** A planet's rings: from afar the light and dark bands; close up, ray traced into their orbiting particles. */
    static void rings(Catalog.Body b, double[] c, double R, double[] rot, double[] sun, double seconds, double px, float vis) {
        double in = b.oblate < 0.8F ? 2.0 : 1.25, out = 2.3;
        double rPix = R / px;
        if (rPix > 30 && SpaceShaders.ringsReady()) {
            Trace t = Trace.sphere(c[0], c[1], c[2], R, out, rot);
            if (t != null) {
                double[] lt = new double[3];
                Trace.local(rot, sun[0], sun[1], sun[2], lt);
                double dist = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]) + 1.0E-9;
                SpaceShaders.rings((float) in, (float) out, lt[0], lt[1], lt[2], 1F, 0.95F, 0.88F, (float) (seconds % 100000.0), (float) (px / dist),
                        vis * (b.oblate < 0.8F ? 0.4F : 1F), t.near, t.x0, t.y0, t.x1, t.y1, t.o, t.ox, t.oy, t.d, t.dx, t.dy);
                return;
            }
        }
        Space3D.ring(RINGS, c[0], c[1], c[2], R * in, R * out, rot, sun[0], sun[1], sun[2], (b.oblate < 0.8F ? 0.35F : 0.9F) * vis);
    }

    private static double[] unit(double[] m, double x, double y, double z) {
        double[] out = new double[3];
        Space3D.apply(m, x, y, z, out);
        double l = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]) + 1.0E-12;
        out[0] /= l;
        out[1] /= l;
        out[2] /= l;
        return out;
    }
}
