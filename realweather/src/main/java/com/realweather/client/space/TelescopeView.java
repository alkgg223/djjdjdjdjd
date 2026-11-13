package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Random;

import static com.realweather.client.space.Space3D.ADD;

/**
 * What you see through the telescope: the dark field of view, with more and more faint stars the further you zoom in,
 * and the planets, moons and stars of the catalogue as real lit 3D bodies at their tiny true-to-the-sky sizes, the
 * planets of other stars going round them on their orbits.
 */
public final class TelescopeView {
    private static final double D = 1000;
    private static final ResourceLocation STAR_MAP = RealWeather.id("textures/environment/space3d/star_surface.png");
    private static final ResourceLocation LINE = RealWeather.id("textures/environment/space3d/line.png");
    private static final ResourceLocation RINGS = RealWeather.id("textures/environment/space3d/rings.png");
    private static final ResourceLocation MOON_MAP = RealWeather.id("textures/environment/space3d/moon.png");
    /** The brighter stars (sky frame), each with its brightness and colour. */
    private static final float[] FIELD = new float[6000 * 5];
    /** The matrices of the last frame (for the marker on the screen). */
    static Matrix4f lastProj = new Matrix4f(), lastView = new Matrix4f();
    private static double time;

    static {
        Random r = new Random(1977);
        for (int i = 0; i < 6000; i++) {
            double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
            FIELD[i * 5] = (float) (s * Math.cos(a));
            FIELD[i * 5 + 1] = (float) (s * Math.sin(a));
            FIELD[i * 5 + 2] = (float) z;
            FIELD[i * 5 + 3] = (float) Math.pow(r.nextDouble(), 3);
            FIELD[i * 5 + 4] = r.nextFloat();
        }
    }

    private TelescopeView() {}

    /** The current direction (world, camera-relative) of a target, for the marker and for pointing at it. */
    public static double[] direction(Catalog.Target t, ClientLevel level, float pt) {
        double tm = level.getGameTime() + pt;
        double day = level.getDayTime() / 24000.0;
        double[] dir = t.sys.direction(day);
        double[] off = new double[3];
        offset(t.sys, t.body, dir, tm, off);
        double x = dir[0] + off[0], y = dir[1] + off[1], z = dir[2] + off[2];
        double l = Math.sqrt(x * x + y * y + z * z);
        double th = level.getTimeOfDay(pt) * Math.PI * 2;
        double c = Math.cos(th), s = Math.sin(th);
        x /= l;
        y /= l;
        z /= l;
        return new double[]{-(y * s + z * c), y * c - z * s, x};
    }

    /** Two directions square to d (sky frame) across the sky, for laying out a system. */
    private static void across(double[] d, double[] e1, double[] e2) {
        double rx = Math.abs(d[0]) < 0.9 ? 1 : 0, ry = Math.abs(d[0]) < 0.9 ? 0 : 1;
        e1[0] = ry * d[2];
        e1[1] = -rx * d[2];
        e1[2] = rx * d[1] - ry * d[0];
        double l = Math.sqrt(e1[0] * e1[0] + e1[1] * e1[1] + e1[2] * e1[2]);
        e1[0] /= l;
        e1[1] /= l;
        e1[2] /= l;
        e2[0] = d[1] * e1[2] - d[2] * e1[1];
        e2[1] = d[2] * e1[0] - d[0] * e1[2];
        e2[2] = d[0] * e1[1] - d[1] * e1[0];
    }

    /** Where a body is in its system (sky frame, radians from the centre; positive towards us along -d). */
    static void offset(Catalog.Sys s, Catalog.Body b, double[] d, double tm, double[] out) {
        out[0] = out[1] = out[2] = 0;
        if (b.orbit <= 0) {
            return;
        }
        double[] e1 = new double[3], e2 = new double[3];
        across(d, e1, e2);
        double ph = Math.PI * 2 * (tm / b.period + b.phase);
        double x = b.orbit * Math.cos(ph), y = b.orbit * Math.sin(ph) * Math.cos(b.incl), z = b.orbit * Math.sin(ph) * Math.sin(b.incl);
        double xr = x * Math.cos(b.node) - y * Math.sin(b.node), yr = x * Math.sin(b.node) + y * Math.cos(b.node);
        for (int k = 0; k < 3; k++) {
            out[k] = e1[k] * xr + e2[k] * yr - d[k] * z;
        }
    }

    public static void render(RenderLevelStageEvent event, ClientLevel level, float pt) {
        lastProj = new Matrix4f(event.getProjectionMatrix());
        lastView = new Matrix4f(event.getModelViewMatrix());
        Minecraft mc = Minecraft.getInstance();
        double fov = Telescope.fov();
        double fovRad = Math.toRadians(fov);
        double pix = fovRad / Math.max(1, mc.getWindow().getHeight());
        time = level.getGameTime() + pt;
        // At high power the field is the dark sky itself (and vanilla's stars, sun and moon, far too coarse to magnify, are hidden).
        float cover = Space3D.smooth(24F, 7F, (float) fov);
        float night = Space3D.night;
        if (cover > 0.01F) {
            Vec3 sky = level.getSkyColor(mc.gameRenderer.getMainCamera().getPosition(), pt);
            float day = 1F - night;
            Space3D.fullScreen((float) sky.x * day * 0.8F + 0.004F, (float) sky.y * day * 0.8F + 0.006F, (float) sky.z * day * 0.8F + 0.012F, cover);
            field(fovRad, pix, cover * night * Space3D.clear);
        }
        double day = level.getDayTime() / 24000.0;
        double[] c = new double[3];
        for (Catalog.Sys s : Catalog.SYSTEMS) {
            double[] dir = s.direction(day);
            Space3D.apply(Space3D.SKYVIEW, dir[0] * D, dir[1] * D, dir[2] * D, c);
            double ang = Math.acos(Mth.clamp(-c[2] / D, -1, 1));
            double extent = 0;
            for (Catalog.Body b : s.bodies) {
                extent = Math.max(extent, b.orbit + b.radius * 3);
            }
            if (s.cluster || s.disk) {
                extent = Math.max(extent, 1.2e-4);
            }
            if (ang > fovRad * 0.9 + extent) {
                continue;
            }
            if (s == Catalog.moon) {
                if (cover > 0.3F) {
                    moon(level, c, cover);
                }
                continue;
            }
            system(s, dir, c, pix, level);
        }
    }

    /** Stars in the field: the brighter ones everywhere, and deeper and deeper ones as you zoom in. */
    private static void field(double fovRad, double pix, float vis) {
        if (vis <= 0.01F) {
            return;
        }
        Space3D.clearDepth();
        Space3D.range(5F, 80000F);
        double[] m = Space3D.SKYVIEW;
        double fx = -m[2], fy = -m[5], fz = -m[8];
        double cosLim = Math.cos(fovRad * 0.85);
        double[] q = new double[3];
        for (int i = 0; i < 6000; i++) {
            double x = FIELD[i * 5], y = FIELD[i * 5 + 1], z = FIELD[i * 5 + 2];
            if (x * fx + y * fy + z * fz < cosLim) {
                continue;
            }
            Space3D.apply(m, x * 2000, y * 2000, z * 2000, q);
            starPoint(q, FIELD[i * 5 + 3], FIELD[i * 5 + 4], pix * 2000, vis);
        }
        // Deeper stars: a grid of cells on the sky fixed among the stars, finer the more you zoom in.
        double cell = fovRad / 14;
        int level = (int) Mth.clamp(Math.round(Math.log(0.02 / cell) / Math.log(2)), 0, 16);
        double c = 0.02 / (1 << level);
        int face = 0;
        double ax = Math.abs(fx), ay = Math.abs(fy), az = Math.abs(fz);
        if (ay >= ax && ay >= az) {
            face = fy > 0 ? 2 : 3;
        } else if (az >= ax) {
            face = fz > 0 ? 4 : 5;
        } else {
            face = fx > 0 ? 0 : 1;
        }
        double major = face < 2 ? ax : face < 4 ? ay : az;
        double u = (face < 2 ? fy : fx) / major, w = (face < 4 ? fz : fy) / major;
        int ci = (int) Math.floor(u / c), cj = (int) Math.floor(w / c);
        int reach = (int) Math.min(12, Math.ceil(fovRad * 0.75 / c) + 1);
        for (int i = ci - reach; i <= ci + reach; i++) {
            for (int j = cj - reach; j <= cj + reach; j++) {
                long key = ((long) face << 58) ^ ((long) level << 52) ^ ((long) (i & 0x3FFFFFF) << 26) ^ (j & 0x3FFFFFF);
                if (Space3D.h(key, 1) > 0.55F) {
                    continue;
                }
                double su = (i + Space3D.h(key, 2)) * c, sw = (j + Space3D.h(key, 3)) * c;
                double dx, dy, dz;
                double sg = face % 2 == 0 ? 1 : -1;
                if (face < 2) {
                    dx = sg;
                    dy = su;
                    dz = sw;
                } else if (face < 4) {
                    dx = su;
                    dy = sg;
                    dz = sw;
                } else {
                    dx = su;
                    dy = sw;
                    dz = sg;
                }
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                Space3D.apply(m, dx / l * 2000, dy / l * 2000, dz / l * 2000, q);
                starPoint(q, Space3D.h(key, 4) * 0.6F, Space3D.h(key, 5), pix * 2000, vis * 0.85F);
            }
        }
    }

    private static void starPoint(double[] q, float bright, float tint, double px, float vis) {
        float r = tint < 0.3F ? 1F : tint < 0.6F ? 0.95F : 0.8F, g = tint < 0.3F ? 0.82F : tint < 0.6F ? 0.95F : 0.88F, b = tint < 0.3F ? 0.65F : 1F;
        Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], px * (1.6 + 3.5 * bright), 0F, r, g, b, vis * (0.45F + 0.55F * bright));
    }

    /** The Moon, close up: a lit 3D sphere with its craters and seas, in the phase it is in tonight. */
    private static void moon(ClientLevel level, double[] c, float cover) {
        Space3D.clearDepth();
        Space3D.range((float) (D * 0.8), (float) (D * 1.2));
        double r = D * Math.tan(Math.atan(0.1));
        int phase = level.getMoonPhase();
        double alpha = Math.PI * (phase <= 4 ? phase : 8 - phase) / 4.0;
        double side = phase <= 4 ? 1 : -1;
        // Sunlight on the Moon: from straight behind us at full moon, from the side at quarter.
        double[] to = {-c[0] / D, -c[1] / D, -c[2] / D};
        double[] sideV = new double[3];
        Space3D.apply(Space3D.SKYVIEW, 0, 0, side, sideV);
        double lx = Math.cos(alpha) * to[0] + Math.sin(alpha) * sideV[0], ly = Math.cos(alpha) * to[1] + Math.sin(alpha) * sideV[1],
                lz = Math.cos(alpha) * to[2] + Math.sin(alpha) * sideV[2];
        double[] axis = new double[3];
        Space3D.apply(Space3D.SKYVIEW, 1, 0, 0, axis);
        double[] rot = Space3D.orient(axis[0], axis[1], axis[2], Math.PI * 0.5);
        Space3D.sphere(MOON_MAP, c[0], c[1], c[2], r, rot, lx, ly, lz, 0.02F, 1F, 1F, 1F, 0F, cover * Space3D.clear, false, 0F, 40);
    }

    /** A star, planet or system: its bodies lit as they should be, orbits, and what is special about it. */
    private static void system(Catalog.Sys s, double[] dir, double[] c, double pix, ClientLevel level) {
        Space3D.clearDepth();
        double extent = 2e-4;
        Space3D.range((float) (D * (1 - extent * 4) - 1), (float) (D * (1 + extent * 4) + 1));
        float vis = Math.max(Space3D.night, s.dir == null ? 0.5F : 0.15F) * Space3D.clear;
        double px = pix * D;
        double[] off = new double[3], p = new double[3];
        // The light: the system's star (or the Sun for our own planets).
        Catalog.Body light = null;
        for (Catalog.Body b : s.bodies) {
            if (b.kind == Catalog.STAR || b.kind == Catalog.PULSAR) {
                light = b;
                break;
            }
        }
        double[] lightPos = c.clone();
        if (light != null) {
            offset(s, light, dir, time, off);
            Space3D.apply(Space3D.SKYVIEW, off[0] * D, off[1] * D, off[2] * D, p);
            lightPos = new double[]{c[0] + p[0], c[1] + p[1], c[2] + p[2]};
        }
        if (s.cluster) {
            cluster(s, c, px, vis);
        }
        if (s.disk) {
            disk(s, dir, c, px, vis);
        }
        long seed = s.id.hashCode();
        for (Catalog.Body b : s.bodies) {
            offset(s, b, dir, time, off);
            Space3D.apply(Space3D.SKYVIEW, off[0] * D, off[1] * D, off[2] * D, p);
            double bx = c[0] + p[0], by = c[1] + p[1], bz = c[2] + p[2];
            double r = b.radius * D;
            double rPix = r / px;
            if (b.orbit > 0 && s.dir != null && b.kind != Catalog.DWARF && !(b.kind == Catalog.STAR && b.orbit < 1e-5)) {
                orbit(s, b, dir, c, px, vis);
            }
            switch (b.kind) {
                case Catalog.STAR, Catalog.DWARF -> {
                    double[] rot = Space3D.orient(0.1, 1, 0.2, time * 0.0003);
                    if (rPix > 0.7) {
                        Space3D.sphere(STAR_MAP, bx, by, bz, r, rot, 0, 0, 1, 0F, b.r * 1.15F, b.g * 1.15F, b.b * 1.15F, 0F, vis, true,
                                (float) (time * 0.00015 % 1.0), (int) Mth.clamp(rPix / 3, 10, 26));
                        if (b.kind == Catalog.STAR) {
                            Space3D.starFire(bx, by, bz, r, seed + b.id.hashCode(), (int) (90 + 70 * b.glow), b.b, (float) time, vis);
                        }
                    }
                    // Its glare (and, unresolved, the point of light you first see).
                    Space3D.billboard(Space3D.GLOW, ADD, bx, by, bz, Math.max(r * 4.5, px * 6), 0F, b.r, b.g, b.b, 0.55F * vis);
                    Space3D.billboard(Space3D.STAR, ADD, bx, by, bz, Math.max(r * 7, px * 10), 0F, b.r, b.g, b.b, 0.5F * vis);
                }
                case Catalog.PULSAR -> {
                    // A neutron star: a tiny blinding point whose two beams sweep round like a lighthouse's.
                    double spin = time * 0.42;
                    double[] axis = new double[3];
                    Space3D.apply(Space3D.SKYVIEW, Math.cos(spin) * 0.9, 0.3, Math.sin(spin) * 0.9, axis);
                    double len = D * 5e-5;
                    Space3D.cone(bx, by, bz, axis[0], axis[1], axis[2], len, len * 0.12, 0.55F, 0.7F, 1F, 0.55F * vis);
                    Space3D.cone(bx, by, bz, -axis[0], -axis[1], -axis[2], len, len * 0.12, 0.55F, 0.7F, 1F, 0.55F * vis);
                    float pulse = 0.6F + 0.4F * (float) Math.abs(Math.cos(spin));
                    Space3D.billboard(Space3D.GLOW, ADD, bx, by, bz, Math.max(r * 10, px * 6), 0F, 0.7F, 0.82F, 1F, 0.7F * vis * pulse);
                    Space3D.billboard(Space3D.STAR, ADD, bx, by, bz, Math.max(r * 14, px * 10), 0F, 0.8F, 0.9F, 1F, 0.8F * vis * pulse);
                }
                default -> body(s, b, bx, by, bz, r, rPix, px, lightPos, vis);
            }
        }
    }

    /** A planet or moon: a lit sphere, its air, its rings, and a comet-like tail for an evaporating world. */
    private static void body(Catalog.Sys s, Catalog.Body b, double bx, double by, double bz, double r, double rPix, double px, double[] lightPos,
                             float vis) {
        double lx, ly, lz;
        if (s.dir == null) {
            lx = Space3D.SUN[0];
            ly = Space3D.SUN[1];
            lz = Space3D.SUN[2];
        } else {
            lx = lightPos[0] - bx;
            ly = lightPos[1] - by;
            lz = lightPos[2] - bz;
            double l = Math.sqrt(lx * lx + ly * ly + lz * lz) + 1.0E-15;
            lx /= l;
            ly /= l;
            lz /= l;
        }
        if (rPix < 0.6) {
            // Too small to show its disc: a faint point of reflected light.
            Space3D.billboard(Space3D.GLOW, ADD, bx, by, bz, px * 2.5, 0F, b.r, b.g, b.b, 0.35F * vis);
            return;
        }
        double[] axis = new double[3];
        Space3D.apply(Space3D.SKYVIEW, 0.25, 1, 0.15, axis);
        double[] rot = Space3D.orient(axis[0], axis[1], axis[2], 0);
        float spin = (float) (time / (b.kind == Catalog.GAS ? 3000.0 : 6000.0) % 1.0);
        Space3D.sphere(b.map, bx, by, bz, r, rot, lx, ly, lz, 0.012F, b.r, b.g, b.b, b.glow, vis, false, spin, (int) Mth.clamp(rPix / 3, 10, 30));
        if (b.air) {
            Space3D.atmosphere(bx, by, bz, r, lx, ly, lz, b.ar, b.ag, b.ab, (b.kind == Catalog.GAS ? 0.4F : 0.55F) * vis);
        }
        if (b.rings) {
            Space3D.ring(RINGS, bx, by, bz, r * 1.25, r * 2.3, rot, lx, ly, lz, 0.9F * vis);
        }
        if (b.tail) {
            // Hydrogen boiling off a hot world, blown back away from its star into a long glowing tail.
            long seed = b.id.hashCode();
            for (int i = 0; i < 140; i++) {
                float sp = 0.002F + 0.003F * Space3D.h(seed, i * 3L);
                float f = (float) ((Space3D.h(seed, i * 3L + 1) + time * sp) % 1.0);
                double spread = (Space3D.h(seed, i * 3L + 2) - 0.5) * r * 3 * f;
                double tx = bx - lx * r * (1.5 + 14 * f) + spread, ty = by - ly * r * (1.5 + 14 * f) - spread * 0.5, tz = bz - lz * r * (1.5 + 14 * f);
                Space3D.billboard(Space3D.GLOW, ADD, tx, ty, tz, r * (0.5 + 2.5 * f), 0F, 0.55F, 0.75F, 1F, 0.12F * (1F - f) * vis);
            }
        }
    }

    /** A faint trace of a body's orbit. */
    private static void orbit(Catalog.Sys s, Catalog.Body b, double[] dir, double[] c, double px, float vis) {
        double[] off = new double[3], p0 = new double[3], p1 = new double[3];
        double saved = time;
        int n = 72;
        for (int i = 0; i <= n; i++) {
            time = b.period * ((double) i / n - b.phase);
            offset(s, b, dir, time, off);
            Space3D.apply(Space3D.SKYVIEW, off[0] * D, off[1] * D, off[2] * D, p1);
            if (i > 0) {
                Space3D.ribbon(LINE, ADD, c[0] + p0[0], c[1] + p0[1], c[2] + p0[2], c[0] + p1[0], c[1] + p1[1], c[2] + p1[2], px * 0.9, px * 0.9, 0.6F,
                        0.75F, 1F, 0.14F * vis, 0.14F * vis);
            }
            p0[0] = p1[0];
            p0[1] = p1[1];
            p0[2] = p1[2];
        }
        time = saved;
    }

    /** The dusty disc round a young star (Beta Pictoris), seen almost edge-on as a line through it. */
    private static void disk(Catalog.Sys s, double[] dir, double[] c, double px, float vis) {
        Catalog.Body ref = s.bodies.get(s.bodies.size() - 1);
        double[] e1 = new double[3], e2 = new double[3], q = new double[3];
        across(dir, e1, e2);
        long seed = s.id.hashCode();
        for (int i = 0; i < 520; i++) {
            double rad = (1.2e-5 + 8e-5 * Math.sqrt(Space3D.h(seed, i * 3L))) * D;
            double ph = Space3D.h(seed, i * 3L + 1) * Math.PI * 2 + time * 0.00002;
            double x = rad * Math.cos(ph), y = rad * Math.sin(ph) * Math.cos(ref.incl), z = rad * Math.sin(ph) * Math.sin(ref.incl);
            y += (Space3D.h(seed, i * 3L + 2) - 0.5) * rad * 0.06;
            double xr = x * Math.cos(ref.node) - y * Math.sin(ref.node), yr = x * Math.sin(ref.node) + y * Math.cos(ref.node);
            Space3D.apply(Space3D.SKYVIEW, e1[0] * xr + e2[0] * yr - dir[0] * z, e1[1] * xr + e2[1] * yr - dir[1] * z,
                    e1[2] * xr + e2[2] * yr - dir[2] * z, q);
            Space3D.billboard(Space3D.GLOW, ADD, c[0] + q[0], c[1] + q[1], c[2] + q[2], Math.max(px * 2, D * 2.5e-6), 0F, 1F, 0.88F, 0.75F,
                    0.09F * vis);
        }
    }

    /** The old globular cluster the pulsar lives in: thousands of stars crowding to its centre. */
    private static void cluster(Catalog.Sys s, double[] c, double px, float vis) {
        long seed = s.id.hashCode();
        for (int i = 0; i < 520; i++) {
            double gx = (Space3D.h(seed, i * 4L) + Space3D.h(seed, i * 4L + 1) + Space3D.h(seed, i * 4L + 2) - 1.5) * 1.6e-4 * D;
            double gy = (Space3D.h(seed, i * 4L + 3) + Space3D.h(seed, i * 5L) + Space3D.h(seed, i * 5L + 1) - 1.5) * 1.6e-4 * D;
            float b = (float) Math.pow(Space3D.h(seed, i * 5L + 2), 2);
            Space3D.billboard(Space3D.GLOW, ADD, c[0] + gx, c[1] + gy, c[2], px * (1.5 + 3 * b), 0F, 1F, 0.9F, 0.7F, (0.35F + 0.5F * b) * vis);
        }
    }
}
