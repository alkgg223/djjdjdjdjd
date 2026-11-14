package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Random;

import static com.realweather.client.space.Space3D.ADD;

/**
 * What you see through the telescope: the dark field of view, with more and more faint stars the further you zoom in,
 * and the Sun, Moon, planets and stars of the catalogue as real lit 3D bodies at their tiny true-to-the-sky sizes, the
 * planets of other stars going round them on their orbits, with their discs of dust, nebulae, tails and flares.
 */
public final class TelescopeView {
    private static final double D = 1000;
    private static final ResourceLocation STAR_MAP = RealWeather.id("textures/environment/space3d/star_surface.png");
    private static final ResourceLocation LINE = RealWeather.id("textures/environment/space3d/line.png");
    static final ResourceLocation LINE_PUBLIC = LINE;
    private static final ResourceLocation RINGS = RealWeather.id("textures/environment/space3d/rings.png");
    private static final ResourceLocation MOON_MAP = RealWeather.id("textures/environment/space3d/moon.png");
    /** The brighter stars (sky frame), each with its brightness and colour. */
    private static final float[] FIELD = new float[6000 * 5];

    /** The stars of the field: direction (sky frame), brightness and colour, five numbers each. */
    static float[] field() {
        return FIELD;
    }
    /** The last frame's view (double precision) and projection scale, for the marker on the screen. */
    static final double[] lastView = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    static double lastPx = 1, lastPy = 1;
    /** Everything goes round this many times slower than it did (steadier to watch at high power). */
    static final double SLOW = 5;
    /** The field of view (radians) at the highest power on a 70 degree setting. */
    private static final double FINEST = Math.toRadians(70.0 / Telescope.MAX_POWER);
    private static double time;
    /** For the tests: how the target's system was handled in the last frame. */
    public static String debug = "";

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
        if (t.sys == Catalog.earth) {
            // Under your feet.
            return new double[]{0, -1, 0};
        }
        double tm = level.getGameTime() + pt;
        double day = level.getDayTime() / 24000.0;
        double[] dir = t.sys.direction(day);
        double[] off = new double[3];
        offset(t.sys, t.body, dir, tm, off);
        double x = dir[0] + off[0], y = dir[1] + off[1], z = dir[2] + off[2];
        double l = Math.sqrt(x * x + y * y + z * z);
        double th = Space3D.timeOfDay(level, pt) * Math.PI * 2;
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
        double ph = Math.PI * 2 * (tm / (b.period * SLOW) + b.phase);
        double x = b.orbit * Math.cos(ph), y = b.orbit * Math.sin(ph) * Math.cos(b.incl), z = b.orbit * Math.sin(ph) * Math.sin(b.incl);
        double xr = x * Math.cos(b.node) - y * Math.sin(b.node), yr = x * Math.sin(b.node) + y * Math.cos(b.node);
        for (int k = 0; k < 3; k++) {
            out[k] = e1[k] * xr + e2[k] * yr - d[k] * z;
        }
    }

    public static void render(RenderLevelStageEvent event, ClientLevel level, float pt) {
        System.arraycopy(Space3D.VIEW, 0, lastView, 0, 9);
        lastPx = Space3D.projX();
        lastPy = Space3D.projY();
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
        Catalog.Target target = Telescope.target();
        double day = level.getDayTime() / 24000.0;
        for (Catalog.Sys s : Catalog.SYSTEMS) {
            if (s == Catalog.earth) {
                continue;
            }
            double[] dir = s.direction(day);
            double[] c = new double[3];
            Space3D.apply(Space3D.SKYVIEW, dir[0] * D, dir[1] * D, dir[2] * D, c);
            // (atan2, not acos: near the centre acos loses all precision.)
            double ang = Math.atan2(Math.sqrt(c[0] * c[0] + c[1] * c[1]), -c[2]);
            double extent = 0;
            for (Catalog.Body b : s.bodies) {
                extent = Math.max(extent, b.orbit + b.radius * (b.kind == Catalog.BLACKHOLE ? 2500 : b.kind == Catalog.NEBULA ? 1.2 : 3));
            }
            if (s.cluster || s.hasDisk() || s.nebula > 0) {
                extent = Math.max(extent, 1.6e-4);
            }
            boolean mine = target != null && target.sys == s;
            if (mine) {
                debug = String.format("%s ang=%.3g limit=%.3g c=(%.4g, %.4g, %.4g)", s.id, ang, fovRad * 0.9 + extent, c[0], c[1], c[2]);
            }
            // The system being looked for is always drawn; the others only when in the field.
            if (!mine && ang > fovRad * 0.9 + extent || c[2] > 0) {
                continue;
            }
            if (s == Catalog.moon) {
                if (cover > 0.3F) {
                    moon(level, c, cover);
                }
                continue;
            }
            if (s == Catalog.sun) {
                if (cover > 0.3F) {
                    sun(c, cover);
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
        int face;
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

    /** The brighter stars all over the sky at an ordinary field of view (out in space, by day too). */
    static void brightStars(float vis) {
        if (vis <= 0.01F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double px = 2.0 / (Space3D.projY() * Math.max(1, mc.getWindow().getHeight()));
        double[] m = Space3D.SKYVIEW;
        double[] q = new double[3];
        for (int i = 0; i < 6000; i++) {
            Space3D.apply(m, FIELD[i * 5] * 2000, FIELD[i * 5 + 1] * 2000, FIELD[i * 5 + 2] * 2000, q);
            if (q[2] > -1) {
                continue;
            }
            starPoint(q, FIELD[i * 5 + 3], FIELD[i * 5 + 4], px * 2000 * 1.2, vis);
        }
    }

    private static void starPoint(double[] q, float bright, float tint, double px, float vis) {
        float r = tint < 0.3F ? 1F : tint < 0.6F ? 0.95F : 0.8F, g = tint < 0.3F ? 0.82F : tint < 0.6F ? 0.95F : 0.88F, b = tint < 0.3F ? 0.65F : 1F;
        Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], px * (1.6 + 3.5 * bright), 0F, r, g, b, vis * (0.45F + 0.55F * bright));
    }

    /** The Moon, close up: a lit 3D sphere with its craters and seas, in the phase it is in tonight. */
    static void moon(ClientLevel level, double[] c, float cover) {
        Space3D.clearDepth();
        Space3D.range((float) (D * 0.8), (float) (D * 1.2));
        double r = D * 0.1;
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
        blocks(Catalog.moonBody(), c[0], c[1], c[2], r, rot, 1, 0, lx, ly, lz, pixel(c), 1F, 1F, 1F, cover * Space3D.clear);
    }

    /** View units per screen pixel at a point. */
    private static double pixel(double[] c) {
        Minecraft mc = Minecraft.getInstance();
        double dist = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        return 2 * dist / (Space3D.projY() * Math.max(1, mc.getWindow().getHeight()));
    }

    /** How many blocks make up a body's radius (so they are a few pixels across at the highest power). */
    static int blockRadius(Catalog.Body b) {
        return (int) Mth.clamp(Math.round(b.radius / FINEST * 1000 / 6), 12, 16384);
    }

    /**
     * A body as a planet of blocks, fading in over the smooth sphere once its blocks are big enough to see. `rot`
     * turns the body into view space, `sy` stretches it along its axis, `spin` is how far it has turned (0..1, as
     * the sphere's texture).
     */
    static void blocks(Catalog.Body b, double bx, double by, double bz, double r, double[] rot, double sy, double spin, double lx, double ly,
                       double lz, double px, float lr, float lg, float lb, float vis) {
        if (!SpaceShaders.voxelReady() || b.map == null || vis <= 0.01F) {
            return;
        }
        int blocksR = blockRadius(b);
        double blockPx = r / blocksR / px;
        float a = Space3D.smooth(2.2F, 4.5F, (float) blockPx) * vis;
        if (a <= 0.01F) {
            return;
        }
        SpaceShaders.Planet pl = b.voxel(blocksR);
        pl.alpha = a;
        pl.steps = 180F;
        // Seen from outside: no sky of its own (its air is the glow round its edge).
        pl.air = 0F;
        pl.pixelSize = (float) (1 / blockPx);
        pl.lightR = lr;
        pl.lightG = lg;
        pl.lightB = lb;
        double dist = Math.sqrt(bx * bx + by * by + bz * bz);
        double[] dv = {bx / dist, by / dist, bz / dist};
        double[] e1 = {1 - dv[0] * dv[0], -dv[0] * dv[1], -dv[0] * dv[2]};
        double l1 = Math.sqrt(e1[0] * e1[0] + e1[1] * e1[1] + e1[2] * e1[2]);
        e1[0] /= l1;
        e1[1] /= l1;
        e1[2] /= l1;
        double[] e2 = {e1[1] * dv[2] - e1[2] * dv[1], e1[2] * dv[0] - e1[0] * dv[2], e1[0] * dv[1] - e1[1] * dv[0]};
        double q = r * Math.max(1, sy) * (1 + (pl.relief + 2.5) / blocksR) * 1.01;
        double ncx = Space3D.projX() * bx / -bz, ncy = Space3D.projY() * by / -bz;
        double hx = Space3D.projX() * q / -bz, hy = Space3D.projY() * q / -bz;
        double x0 = Math.max(ncx - hx, -1.02), x1 = Math.min(ncx + hx, 1.02), y0 = Math.max(ncy - hy, -1.02), y1 = Math.min(ncy + hy, 1.02);
        if (x0 >= x1 || y0 >= y1) {
            return;
        }
        double am = ((x0 + x1) / 2 - ncx) / hx, ha = (x1 - x0) / 2 / hx, bm = ((y0 + y1) / 2 - ncy) / hy, hb = (y1 - y0) / 2 / hy;
        double k = blocksR / r;
        double cs = Math.cos(Math.PI * 2 * spin), sn = Math.sin(Math.PI * 2 * spin);
        double[] t1 = local(rot, sy, e1, cs, sn), t2 = local(rot, sy, e2, cs, sn), td = local(rot, sy, dv, cs, sn);
        double dl = Math.sqrt(td[0] * td[0] + td[1] * td[1] + td[2] * td[2]);
        double back = 2 * (blocksR + pl.relief + 3);
        double[] o = new double[3], ox = new double[3], oy = new double[3], d = new double[3];
        for (int i = 0; i < 3; i++) {
            d[i] = td[i] / dl;
            o[i] = k * q * (t1[i] * am + t2[i] * bm) - d[i] * back;
            ox[i] = k * q * t1[i] * ha;
            oy[i] = k * q * t2[i] * hb;
        }
        double[] lt = local(rot, 1, new double[]{lx, ly, lz}, cs, sn);
        pl.lx = lt[0];
        pl.ly = lt[1];
        pl.lz = lt[2];
        SpaceShaders.voxel(pl, (float) x0, (float) y0, (float) x1, (float) y1, o, ox, oy, d, new double[3], new double[3]);
    }

    /** A view-space vector in the body's own frame (unstretched, turned with its spin). */
    private static double[] local(double[] rot, double sy, double[] v, double cs, double sn) {
        double x = rot[0] * v[0] + rot[1] * v[1] + rot[2] * v[2];
        double y = (rot[3] * v[0] + rot[4] * v[1] + rot[5] * v[2]) / sy;
        double z = rot[6] * v[0] + rot[7] * v[1] + rot[8] * v[2];
        return new double[]{x * cs - z * sn, y, x * sn + z * cs};
    }

    /** The Sun through a solar filter: its granulated surface, sunspots, and prominences of fire round the limb. */
    private static void sun(double[] c, float cover) {
        float up = Space3D.smooth(-0.05F, 0.1F, (float) Space3D.SKY[4]) * Space3D.clear * cover;
        if (up <= 0.01F) {
            return;
        }
        Space3D.clearDepth();
        Space3D.range((float) (D * 0.7), (float) (D * 1.3));
        double r = D * Math.tan(Math.atan(0.15));
        double[] rot = Space3D.orient(0.1, 1, 0.15, time * 0.00005);
        Space3D.sphere(STAR_MAP, c[0], c[1], c[2], r, rot, 0, 0, 1, 0F, 1F, 0.72F, 0.38F, 0F, up, true, (float) (time * 0.00003 % 1.0), 40);
        SolarActivity.draw(c[0], c[1], c[2], r, rot, 1F, 0.72F, 0.38F, 4242, time / 20.0, pixel(c), up);
        Space3D.starFire(c[0], c[1], c[2], r, 4242, 220, 0.3F, (float) time, up);
        flares(c[0], c[1], c[2], r, 4242, up);
        corona(c[0], c[1], c[2], r, 4242, 0.55F * up);
    }

    /**
     * The corona: the Sun's outer air of million-degree gas, a pearly glow round the disc drawn out into long
     * streamers along its magnetic field, slowly changing.
     */
    static void corona(double cx, double cy, double cz, double r, long seed, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, r * 3.2, 0F, 1F, 0.95F, 0.88F, 0.45F * alpha);
        Space3D.billboard(Space3D.GLOW, ADD, cx, cy, cz, r * 6.5, 0F, 0.95F, 0.92F, 0.9F, 0.18F * alpha);
        double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double vx = cx / dl, vy = cy / dl, vz = cz / dl;
        double ax = Math.abs(vy) < 0.9 ? -vz : 1, ay = Math.abs(vy) < 0.9 ? 0 : 0, az = Math.abs(vy) < 0.9 ? vx : 0;
        double al = Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= al;
        ay /= al;
        az /= al;
        double bx = vy * az - vz * ay, by = vz * ax - vx * az, bz = vx * ay - vy * ax;
        for (int i = 0; i < 26; i++) {
            double a = Math.PI * 2 * (i + Space3D.h(seed, i * 3L) * 0.6) / 26 + time * 0.000004;
            double len = r * (1.6 + 3.2 * Math.pow(Space3D.h(seed, i * 3L + 1), 2) * (0.75 + 0.25 * Math.sin(time * 0.0003 + i)));
            // Streamers bend a little, like helmet streamers over the active belts.
            double bend = (Space3D.h(seed, i * 3L + 2) - 0.5) * 0.25;
            double c0 = Math.cos(a), s0 = Math.sin(a), c1 = Math.cos(a + bend), s1 = Math.sin(a + bend);
            double x0 = cx + (ax * c0 + bx * s0) * r * 0.95, y0 = cy + (ay * c0 + by * s0) * r * 0.95, z0 = cz + (az * c0 + bz * s0) * r * 0.95;
            double x1 = cx + (ax * c1 + bx * s1) * (r + len), y1 = cy + (ay * c1 + by * s1) * (r + len), z1 = cz + (az * c1 + bz * s1) * (r + len);
            Space3D.ribbon(Space3D.GLOW, ADD, x0, y0, z0, x1, y1, z1, r * 0.32, r * 0.05, 1F, 0.96F, 0.9F, 0.22F * alpha, 0F);
        }
    }

    /**
     * Solar flares: now and then a sudden blinding brightening over an active region, with an arcade of glowing
     * loops rising over it as it fades.
     */
    private static void flares(double cx, double cy, double cz, double r, long seed, float vis) {
        long slot = (long) (time / 900.0);
        for (long k = slot - 1; k <= slot; k++) {
            if (Space3D.h(seed, k * 11) > 0.6F) {
                continue;
            }
            double age = (time - k * 900.0 - Space3D.h(seed, k * 11 + 1) * 300) / 600.0;
            if (age < 0 || age > 1) {
                continue;
            }
            float f = (float) (age < 0.05 ? age / 0.05 : Math.exp(-(age - 0.05) * 4));
            // Where on the disc (the side facing us).
            double th = Space3D.h(seed, k * 11 + 2) * Math.PI * 2, rr = Math.sqrt(Space3D.h(seed, k * 11 + 3)) * 0.85;
            double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
            double vx = cx / dl, vy = cy / dl, vz = cz / dl;
            double ux = Math.abs(vy) < 0.9 ? -vz : 1, uz = Math.abs(vy) < 0.9 ? vx : 0;
            double ul = Math.sqrt(ux * ux + uz * uz);
            ux /= ul;
            uz /= ul;
            double wx = vy * uz, wy = vz * ux - vx * uz, wz = -vy * ux;
            double ox = (ux * Math.cos(th) + wx * Math.sin(th)) * rr, oy = wy * Math.sin(th) * rr, oz = (uz * Math.cos(th) + wz * Math.sin(th)) * rr;
            double depth = -Math.sqrt(Math.max(0, 1 - rr * rr));
            double px = cx + (ox + vx * depth) * r, py = cy + (oy + vy * depth) * r, pz = cz + (oz + vz * depth) * r;
            Space3D.billboard(Space3D.GLOW, ADD, px, py, pz, r * (0.08 + 0.12 * f), 0F, 1F, 1F, 0.92F, f * vis);
            Space3D.billboard(Space3D.STAR, ADD, px, py, pz, r * 0.35 * f, 0F, 1F, 0.95F, 0.8F, 0.6F * f * vis);
            // The loop arcade.
            for (int i = 0; i < 7; i++) {
                double sp = (i - 3) * 0.018 * r;
                double hgt = r * (0.03 + 0.09 * Math.min(1, age * 2));
                for (int j = 0; j < 8; j++) {
                    double a0 = Math.PI * j / 8, a1 = Math.PI * (j + 1) / 8;
                    double qx0 = px + ux * sp + wx * Math.cos(a0) * r * 0.04 - vx * Math.sin(a0) * hgt;
                    double qy0 = py + wy * Math.cos(a0) * r * 0.04 - vy * Math.sin(a0) * hgt;
                    double qz0 = pz + uz * sp + wz * Math.cos(a0) * r * 0.04 - vz * Math.sin(a0) * hgt;
                    double qx1 = px + ux * sp + wx * Math.cos(a1) * r * 0.04 - vx * Math.sin(a1) * hgt;
                    double qy1 = py + wy * Math.cos(a1) * r * 0.04 - vy * Math.sin(a1) * hgt;
                    double qz1 = pz + uz * sp + wz * Math.cos(a1) * r * 0.04 - vz * Math.sin(a1) * hgt;
                    Space3D.ribbon(Space3D.FLAME, ADD, qx0, qy0, qz0, qx1, qy1, qz1, r * 0.006, r * 0.006, 1F, 0.7F, 0.35F,
                            0.5F * vis * (float) Math.min(1, age * 4), 0.5F * vis * (float) Math.min(1, age * 4));
                }
            }
        }
    }

    /** A star, planet or system: its bodies lit as they should be, orbits, and what is special about it. */
    private static void system(Catalog.Sys s, double[] dir, double[] c, double pix, ClientLevel level) {
        Space3D.clearDepth();
        double extent = 2e-4;
        for (Catalog.Body b : s.bodies) {
            if (b.kind == Catalog.NEBULA) {
                extent = Math.max(extent, b.radius * 1.3);
            }
        }
        Space3D.range((float) (D * (1 - extent * 4) - 1), (float) (D * (1 + extent * 4) + 1));
        float vis = Math.max(Space3D.night, s.dir == null ? 0.5F : 0.15F) * Space3D.clear;
        double px = pix * D;
        double[] off = new double[3], p = new double[3];
        // The light: the system's (first) star, or the Sun for our own planets.
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
        if (s.hasDisk()) {
            disk(s, dir, c, px, vis);
        }
        if (s.nebula > 0) {
            nebula(s, dir, c, px, vis);
        }
        long seed = s.id.hashCode();
        for (Catalog.Body b : s.bodies) {
            offset(s, b, dir, time, off);
            Space3D.apply(Space3D.SKYVIEW, off[0] * D, off[1] * D, off[2] * D, p);
            double bx = c[0] + p[0], by = c[1] + p[1], bz = c[2] + p[2];
            double r = b.radius * D;
            double rPix = r / px;
            if (b.orbit > 0 && s.dir != null && b.kind != Catalog.DWARF && b.kind != Catalog.STAR && b.kind != Catalog.PULSAR) {
                orbit(s, b, dir, c, px, vis);
            }
            switch (b.kind) {
                case Catalog.STAR, Catalog.DWARF -> star(b, bx, by, bz, r, rPix, px, seed, vis);
                case Catalog.BLACKHOLE -> blackHole(b, dir, bx, by, bz, r, px, vis);
                case Catalog.PULSAR -> {
                    double[] ax = pulsarAxis(b);
                    Pulsars.draw(bx, by, bz, r, ax, b.tiltDeg, b.spinHz, b.pr, b.pg, b.pb, vis, px, seed + b.id.hashCode(), time / 20.0, rPix > 2, b.jets,
                            b.magnetar);
                }
                case Catalog.COMET -> comet(b, bx, by, bz, r, px, vis);
                case Catalog.MERGER -> {
                    Space3D.billboard(Space3D.GLOW, ADD, bx, by, bz, Math.max(px * 4, r * 30), 0F, 0.6F, 0.78F, 1F, 0.35F * vis);
                    Merger.draw(bx, by, bz, r * 20, pulsarAxis(b), time / 20.0, px, vis);
                }
                case Catalog.NEBULA -> nebula(b, dir, bx, by, bz, r, px, vis);
                default -> body(s, b, bx, by, bz, r, rPix, px, lightPos, light, vis);
            }
        }
    }

    /** A comet in the night sky: its tails pointing away from the Sun. */
    private static void comet(Catalog.Body b, double bx, double by, double bz, double r, double px, float vis) {
        double l = Math.sqrt(bx * bx + by * by + bz * bz);
        double tx = bx / l - Space3D.SUN[0], ty = by / l - Space3D.SUN[1], tz = bz / l - Space3D.SUN[2];
        double tl = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1.0E-9;
        double[] sun = {-tx / tl, -ty / tl, -tz / tl};
        double[] vel = {-tz / tl, 0.15, tx / tl};
        double vl = Math.sqrt(vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2]) + 1.0E-9;
        vel[0] /= vl;
        vel[1] /= vl;
        vel[2] /= vl;
        Comets.draw(bx, by, bz, r * 0.4, sun, vel, r * 70, b.id.hashCode(), time / 20.0, px, Math.max(vis, 0.6F));
    }

    /** A pulsar's spin axis (fixed among the stars) in view space. */
    static double[] pulsarAxis(Catalog.Body b) {
        long h = b.id.hashCode();
        double x = Space3D.h(h, 1) - 0.5, y = Space3D.h(h, 2) - 0.5 + 0.8, z = Space3D.h(h, 3) - 0.5;
        double[] a = new double[3];
        Space3D.apply(Space3D.SKYVIEW, x, y, z, a);
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        a[0] /= l;
        a[1] /= l;
        a[2] /= l;
        return a;
    }

    /**
     * A nebula: from far off a faint smudge in its colour; bigger on the screen, ray marched through its gas in 3D
     * (more steps and finer turbulence the more it fills the view), with the stars that light it.
     */
    private static void nebula(Catalog.Body b, double[] dirSky, double bx, double by, double bz, double rv, double px, float vis) {
        Nebulae.Recipe rc = Nebulae.at(b.recipe, time / 20.0);
        if (rc == null) {
            return;
        }
        double quadPx = rv / px;
        float far = 1F - Space3D.smooth(8F, 40F, (float) quadPx);
        Space3D.billboard(Space3D.GLOW, ADD, bx, by, bz, Math.max(rv * 1.3, px * 4), 0F, rc.r, rc.g, rc.b, 0.3F * vis * Math.max(far, 0.05F));
        double[] e1 = new double[3], e2 = new double[3];
        across(dirSky, e1, e2);
        double[] e1v = new double[3], e2v = new double[3], zv = new double[3];
        Space3D.apply(Space3D.SKYVIEW, e1[0], e1[1], e1[2], e1v);
        Space3D.apply(Space3D.SKYVIEW, e2[0], e2[1], e2[2], e2v);
        Space3D.apply(Space3D.SKYVIEW, -dirSky[0], -dirSky[1], -dirSky[2], zv);
        float alpha = Space3D.smooth(6F, 30F, (float) quadPx) * vis;
        if (alpha > 0.01F && SpaceShaders.nebulaReady()) {
            double q = rv * 1.12;
            double ncx = Space3D.projX() * bx / -bz, ncy = Space3D.projY() * by / -bz;
            double hx = Space3D.projX() * q / -bz, hy = Space3D.projY() * q / -bz;
            double x0 = Math.max(ncx - hx, -1.02), x1 = Math.min(ncx + hx, 1.02), y0 = Math.max(ncy - hy, -1.02), y1 = Math.min(ncy + hy, 1.02);
            if (x0 < x1 && y0 < y1) {
                double am = ((x0 + x1) / 2 - ncx) / hx, ha = (x1 - x0) / 2 / hx, bm = ((y0 + y1) / 2 - ncy) / hy, hb = (y1 - y0) / 2 / hy;
                double dist = Math.sqrt(bx * bx + by * by + bz * bz);
                double[] dv = {bx / dist, by / dist, bz / dist};
                // Screen right and up across the line of sight (view space), then into the nebula's frame.
                double[] sr = {1 - dv[0] * dv[0], -dv[0] * dv[1], -dv[0] * dv[2]};
                double sl = Math.sqrt(sr[0] * sr[0] + sr[1] * sr[1] + sr[2] * sr[2]);
                sr[0] /= sl;
                sr[1] /= sl;
                sr[2] /= sl;
                double[] su = {sr[1] * dv[2] - sr[2] * dv[1], sr[2] * dv[0] - sr[0] * dv[2], sr[0] * dv[1] - sr[1] * dv[0]};
                double[] lr = toFrame(sr, e1v, e2v, zv), lu = toFrame(su, e1v, e2v, zv), ld = toFrame(dv, e1v, e2v, zv);
                double k = q / rv;
                double[] o = new double[3], ox = new double[3], oy = new double[3];
                for (int i = 0; i < 3; i++) {
                    o[i] = k * (lr[i] * am + lu[i] * bm) - ld[i] * 2.5;
                    ox[i] = k * lr[i] * ha;
                    oy[i] = k * lu[i] * hb;
                }
                float steps = (float) Mth.clamp(36 + quadPx / 40, 36, 76);
                float oct = (float) Mth.clamp(3 + Math.log(Math.max(1, quadPx / 120)) / Math.log(2), 3, 6);
                SpaceShaders.nebula(rc, (float) x0, (float) y0, (float) x1, (float) y1, o, ox, oy, ld, new double[3], new double[3], steps, oct, alpha);
            }
        }
        // The stars inside it.
        for (Nebulae.Stars st : rc.stars) {
            long seed = (b.id + st.hashCode()).hashCode();
            for (int i = 0; i < st.count(); i++) {
                double sx = st.x() + (Space3D.h(seed, i * 3L) - 0.5) * 2 * st.spread(), sy = st.y() + (Space3D.h(seed, i * 3L + 1) - 0.5) * 2 * st.spread(),
                        sz = st.z() + (Space3D.h(seed, i * 3L + 2) - 0.5) * st.spread();
                double qx = bx + (e1v[0] * sx + e2v[0] * sy + zv[0] * sz) * rv, qy = by + (e1v[1] * sx + e2v[1] * sy + zv[1] * sz) * rv,
                        qz = bz + (e1v[2] * sx + e2v[2] * sy + zv[2] * sz) * rv;
                float br = st.bright() * (0.4F + 0.6F * Space3D.h(seed, i * 3L + 7));
                Space3D.billboard(Space3D.GLOW, ADD, qx, qy, qz, Math.max(px * 2.5, rv * 0.006 * br), 0F, st.r(), st.g(), st.b(), Math.min(1F, br) * vis);
                Space3D.billboard(Space3D.STAR, ADD, qx, qy, qz, Math.max(px * 4, rv * 0.012 * br), 0F, st.r(), st.g(), st.b(), 0.6F * br * vis);
            }
        }
    }

    private static double[] toFrame(double[] v, double[] e1, double[] e2, double[] z) {
        return new double[]{v[0] * e1[0] + v[1] * e1[1] + v[2] * e1[2], v[0] * e2[0] + v[1] * e2[1] + v[2] * e2[2], v[0] * z[0] + v[1] * z[1] + v[2] * z[2]};
    }

    /** How strongly a flare star is flaring right now (0..1, sudden rises and slow fades). */
    private static float flare(Catalog.Body b) {
        long seed = b.id.hashCode();
        long slot = (long) (time / 240.0);
        float best = 0F;
        for (long k = slot - 1; k <= slot; k++) {
            if (Space3D.h(seed, k) > 0.5F) {
                continue;
            }
            float f = (float) ((time - k * 240.0 - Space3D.h(seed, k + 7) * 80.0) / 200.0);
            if (f >= 0F && f <= 1F) {
                best = Math.max(best, f < 0.06F ? f / 0.06F : (float) Math.exp(-(f - 0.06F) * 5F));
            }
        }
        return best;
    }

    /** A star: a glowing sphere (flattened if it spins fast), its granulated surface, fire round it and its glare. */
    private static void star(Catalog.Body b, double bx, double by, double bz, double r, double rPix, double px, long seed, float vis) {
        float fl = b.flare ? flare(b) : 0F;
        float bright = 1.15F + 0.9F * fl;
        if (rPix > 0.7) {
            double[] rot = Space3D.orient(0.1, 1, 0.2, time * 0.0003);
            Space3D.scale(1, b.oblate, 1);
            Space3D.sphere(STAR_MAP, bx, by, bz, r, rot, 0, 0, 1, 0F, b.r * bright, b.g * bright, b.b * bright, 0F, vis, true,
                    (float) (time * 0.00015 % 1.0), (int) Mth.clamp(rPix / 3, 10, 26));
            Space3D.scale(1, 1, 1);
            if (b.kind == Catalog.STAR) {
                if (rPix > 12 && b.oblate >= 0.95F) {
                    SolarActivity.draw(bx, by, bz, r, rot, b.r * bright, b.g * bright, b.b * bright, seed + b.id.hashCode(), time / 20.0, px, vis);
                }
                Space3D.starFire(bx, by, bz, r, seed + b.id.hashCode(), (int) (90 + 70 * b.glow), b.b, (float) time, vis * (1F + 1.5F * fl));
            }
        }
        // Its glare (and, unresolved, the point of light you first see); a flare floods it with light.
        Space3D.billboard(Space3D.GLOW, ADD, bx, by, bz, Math.max(r * (4.5 + 6 * fl), px * 6), 0F, b.r, b.g + 0.3F * fl, b.b + 0.4F * fl,
                (0.55F + 0.4F * fl) * vis);
        Space3D.billboard(Space3D.STAR, ADD, bx, by, bz, Math.max(r * (7 + 8 * fl), px * 10), 0F, b.r, b.g, b.b, (0.5F + 0.4F * fl) * vis);
    }

    /** A planet or moon: a lit sphere, its air, its rings, glow, and tails or streams for the strangest worlds. */
    private static void body(Catalog.Sys s, Catalog.Body b, double bx, double by, double bz, double r, double rPix, double px, double[] lightPos,
                             Catalog.Body light, float vis) {
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
        double[] rot;
        if (b.egg) {
            // Stretched into an egg by its star's pull, the point towards the star.
            rot = Space3D.orient(lx, ly, lz, 0);
            Space3D.scale(1, 1.4, 1);
        } else {
            double[] axis = new double[3];
            Space3D.apply(Space3D.SKYVIEW, 0.25, 1, 0.15, axis);
            rot = Space3D.orient(axis[0], axis[1], axis[2], 0);
            Space3D.scale(1, b.oblate, 1);
        }
        float spin = (float) (time / ((b.kind == Catalog.GAS ? 3000.0 : b.oblate < 0.8F ? 300.0 : 6000.0) * SLOW) % 1.0);
        Space3D.sphere(b.map, bx, by, bz, r, rot, lx, ly, lz, 0.012F, b.r, b.g, b.b, b.glow, vis, false, spin, (int) Mth.clamp(rPix / 3, 10, 30));
        Space3D.scale(1, 1, 1);
        // Close enough, it is seen to be made of blocks (a gas giant: its clouds, turning, with its storms).
        float lr = light == null ? 1F : 0.75F + 0.25F * light.r, lg = light == null ? 1F : 0.75F + 0.25F * light.g,
                lb = light == null ? 1F : 0.75F + 0.25F * light.b;
        if (b.kind == Catalog.GAS) {
            if (rPix > 20 && !b.egg) {
                Trace t = Trace.sphere(bx, by, bz, r, 1.06, rot);
                if (t != null) {
                    SpaceShaders.GasLook g = GasGiants.look(b, time / 20.0);
                    double[] lt = new double[3];
                    Trace.local(rot, lx, ly, lz, lt);
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
        } else {
            blocks(b, bx, by, bz, r, rot, b.egg ? 1.4 : b.oblate, b.egg ? 0 : spin, lx, ly, lz, px, lr, lg, lb, vis);
        }
        double[] sunV = {lx, ly, lz};
        Aurorae.draw(b.id, bx, by, bz, r, new double[]{rot[3], rot[4], rot[5]}, sunV, time / 20.0, px, vis);
        Plumes.draw(b.id, bx, by, bz, r, rot, sunV, time / 20.0, px, vis);
        if (b.air) {
            Space3D.atmosphere(bx, by, bz, r, lx, ly, lz, b.ar, b.ag, b.ab, (b.kind == Catalog.GAS ? 0.4F : 0.55F) * vis);
        }
        if (b.halo > 0) {
            Space3D.billboard(Space3D.GLOW, ADD, bx, by, bz, r * 2.6, 0F, b.hr, b.hg, b.hb, b.halo * vis);
        }
        if (b.rings) {
            CloseViews.rings(b, new double[]{bx, by, bz}, r, rot, new double[]{lx, ly, lz}, time / 20.0, px, vis);
        }
        long seed = b.id.hashCode();
        if (b.tail) {
            // Hydrogen boiling off a hot world, blown back away from its star into a long glowing tail.
            for (int i = 0; i < 140; i++) {
                float sp = 0.002F + 0.003F * Space3D.h(seed, i * 3L);
                float f = (float) ((Space3D.h(seed, i * 3L + 1) + time * sp) % 1.0);
                double spread = (Space3D.h(seed, i * 3L + 2) - 0.5) * r * 3 * f;
                double tx = bx - lx * r * (1.5 + 14 * f) + spread, ty = by - ly * r * (1.5 + 14 * f) - spread * 0.5, tz = bz - lz * r * (1.5 + 14 * f);
                Space3D.billboard(Space3D.GLOW, ADD, tx, ty, tz, r * (0.5 + 2.5 * f), 0F, 0.55F, 0.75F, 1F, 0.12F * (1F - f) * vis);
            }
        }
        if (b.stream) {
            // Gas torn off the planet, spiralling down on to its star.
            double sx = lightPos[0] - bx, sy = lightPos[1] - by, sz = lightPos[2] - bz;
            for (int i = 0; i < 120; i++) {
                float f = (float) ((Space3D.h(seed, i * 5L) + time * 0.004) % 1.0);
                double swirl = Math.sin(f * Math.PI) * r * 2.5;
                double a = f * 5 + i;
                double qx = bx + sx * f + Math.cos(a) * swirl * 0.4, qy = by + sy * f + Math.sin(a) * swirl, qz = bz + sz * f;
                Space3D.billboard(Space3D.GLOW, ADD, qx, qy, qz, r * (0.8 - 0.4 * f), 0F, 1F, 0.55F, 0.3F, 0.16F * vis);
            }
        }
    }

    /** A faint trace of a body's orbit. */
    private static void orbit(Catalog.Sys s, Catalog.Body b, double[] dir, double[] c, double px, float vis) {
        double[] off = new double[3], p0 = new double[3], p1 = new double[3];
        int n = 72;
        for (int i = 0; i <= n; i++) {
            double tm = b.period * SLOW * ((double) i / n - b.phase);
            offset(s, b, dir, tm, off);
            Space3D.apply(Space3D.SKYVIEW, off[0] * D, off[1] * D, off[2] * D, p1);
            if (i > 0) {
                Space3D.ribbon(LINE, ADD, c[0] + p0[0], c[1] + p0[1], c[2] + p0[2], c[0] + p1[0], c[1] + p1[1], c[2] + p1[2], px * 0.9, px * 0.9, 0.6F,
                        0.75F, 1F, 0.14F * vis, 0.14F * vis);
            }
            p0[0] = p1[0];
            p0[1] = p1[1];
            p0[2] = p1[2];
        }
    }

    /** A disc or ring of dust round a star (edge-on as a line through it, or tilted and off-centre like Fomalhaut's). */
    private static void disk(Catalog.Sys s, double[] dir, double[] c, double px, float vis) {
        double[] e1 = new double[3], e2 = new double[3], q = new double[3];
        across(dir, e1, e2);
        long seed = s.id.hashCode();
        for (int i = 0; i < 560; i++) {
            double rad = (s.diskIn + (s.diskOut - s.diskIn) * Math.sqrt(Space3D.h(seed, i * 3L))) * D;
            double ph = Space3D.h(seed, i * 3L + 1) * Math.PI * 2 + time * 0.00002;
            double x = rad * Math.cos(ph) + s.diskShift * D, y = rad * Math.sin(ph) * Math.cos(s.diskIncl), z = rad * Math.sin(ph) * Math.sin(s.diskIncl);
            y += (Space3D.h(seed, i * 3L + 2) - 0.5) * rad * 0.06;
            double xr = x * Math.cos(s.diskNode) - y * Math.sin(s.diskNode), yr = x * Math.sin(s.diskNode) + y * Math.cos(s.diskNode);
            Space3D.apply(Space3D.SKYVIEW, e1[0] * xr + e2[0] * yr - dir[0] * z, e1[1] * xr + e2[1] * yr - dir[1] * z,
                    e1[2] * xr + e2[2] * yr - dir[2] * z, q);
            Space3D.billboard(Space3D.GLOW, ADD, c[0] + q[0], c[1] + q[1], c[2] + q[2], Math.max(px * 2, D * 2.5e-6), 0F, s.diskR, s.diskG, s.diskB,
                    0.09F * vis);
        }
    }

    /** Gas and dust thrown off a dying giant: a lumpy shell (VY Canis Majoris) or two great lobes (Eta Carinae). */
    private static void nebula(Catalog.Sys s, double[] dir, double[] c, double px, float vis) {
        long seed = s.id.hashCode();
        double[] q = new double[3];
        for (int i = 0; i < 520; i++) {
            double th = Math.acos(1 - 2 * Space3D.h(seed, i * 4L)), ph = Space3D.h(seed, i * 4L + 1) * Math.PI * 2;
            double ux = Math.sin(th) * Math.cos(ph), uy = Math.cos(th), uz = Math.sin(th) * Math.sin(ph);
            double cx, cy, cz, rad;
            float r, g, b, a;
            if (s.nebula == 2) {
                // The Homunculus: two lobes either side of the star along its axis, and a thin skirt round its middle.
                int lobe = i % 2 == 0 ? 1 : -1;
                rad = 5.5e-5 * (0.92 + 0.12 * Space3D.h(seed, i * 4L + 2));
                cx = ux * rad;
                cy = uy * rad + lobe * 6e-5;
                cz = uz * rad;
                if (i % 7 == 0) {
                    cx = ux * 1.2e-4;
                    cy = (Space3D.h(seed, i * 4L + 3) - 0.5) * 6e-6;
                    cz = uz * 1.2e-4;
                }
                r = 1F;
                g = 0.62F;
                b = 0.38F;
                a = 0.1F;
            } else {
                rad = (5e-5 + 6e-5 * Space3D.h(seed, i * 4L + 2));
                cx = ux * rad;
                cy = uy * rad;
                cz = uz * rad;
                r = 1F;
                g = 0.45F;
                b = 0.28F;
                a = 0.07F;
            }
            Space3D.apply(Space3D.SKYVIEW, cx * D, cy * D, cz * D, q);
            Space3D.billboard(Space3D.FLAME, ADD, c[0] + q[0], c[1] + q[1], c[2] + q[2], D * 1.4e-5 * (0.6 + Space3D.h(seed, i * 4L + 3)), i,
                    r, g, b, a * vis);
        }
    }

    /**
     * A black hole (TON 618, the quasar): from afar a single dazzling point; zoomed in, ray traced: its shadow, the
     * thin photon ring round it, the accretion disc with its far side lensed over and under the hole, a galaxy
     * behind lensed into an Einstein ring, its jets, and the vast glowing nebula round the quasar.
     */
    private static void blackHole(Catalog.Body b, double[] dirSky, double bx, double by, double bz, double rs, double px, float vis) {
        double edge = 40;
        double quad = rs * edge;
        double quadPx = quad / px;
        float point = 1F - Space3D.smooth(8F, 60F, (float) quadPx);
        Space3D.billboard(Space3D.GLOW, ADD, bx, by, bz, Math.max(px * 7, rs * 24), 0F, 0.72F, 0.82F, 1F, (0.25F + 0.6F * point) * vis);
        Space3D.billboard(Space3D.STAR, ADD, bx, by, bz, Math.max(px * 12, rs * 30), 0F, 0.8F, 0.88F, 1F, 0.8F * point * vis);
        // The nebula of glowing hydrogen round the quasar, hundreds of thousands of light years across.
        long seed = 618;
        for (int i = 0; i < 46; i++) {
            double ang = Space3D.h(seed, i * 3L) * Math.PI * 2, rad = rs * (150 + 1600 * Math.pow(Space3D.h(seed, i * 3L + 1), 1.5));
            double gx = bx + Math.cos(ang) * rad, gy = by + Math.sin(ang) * rad * 0.8;
            Space3D.billboard(Space3D.GLOW, ADD, gx, gy, bz, rs * (300 + 500 * Space3D.h(seed, i * 3L + 2)), (float) ang, 0.35F, 0.85F, 0.7F,
                    0.05F * vis);
        }
        // The disc's axis, fixed among the stars, tipped 80 degrees from our line of sight.
        double[] e1 = new double[3], e2 = new double[3];
        across(dirSky, e1, e2);
        double tilt = Math.toRadians(80);
        double[] nSky = {-dirSky[0] * Math.cos(tilt) + e2[0] * Math.sin(tilt), -dirSky[1] * Math.cos(tilt) + e2[1] * Math.sin(tilt),
                -dirSky[2] * Math.cos(tilt) + e2[2] * Math.sin(tilt)};
        double[] n = new double[3];
        Space3D.apply(Space3D.SKYVIEW, nSky[0], nSky[1], nSky[2], n);
        // The jets, along the axis both ways.
        float alpha = Space3D.smooth(5F, 16F, (float) quadPx) * vis;
        if (alpha <= 0.01F || !SpaceShaders.holeReady()) {
            Jets.draw(bx, by, bz, n, rs * 900, rs * 16, 0.6F, 0.74F, 1F, 0.98, 618, time / 20.0, px, vis);
            return;
        }
        double dist = Math.sqrt(bx * bx + by * by + bz * bz);
        double[] zb = {-bx / dist, -by / dist, -bz / dist};
        double[] xb = {1 - zb[0] * zb[0], -zb[0] * zb[1], -zb[0] * zb[2]};
        double xl = Math.sqrt(xb[0] * xb[0] + xb[1] * xb[1] + xb[2] * xb[2]);
        xb[0] /= xl;
        xb[1] /= xl;
        xb[2] /= xl;
        double[] yb = {zb[1] * xb[2] - zb[2] * xb[1], zb[2] * xb[0] - zb[0] * xb[2], zb[0] * xb[1] - zb[1] * xb[0]};
        double[] nb = {n[0] * xb[0] + n[1] * xb[1] + n[2] * xb[2], n[0] * yb[0] + n[1] * yb[1] + n[2] * yb[2],
                n[0] * zb[0] + n[1] * zb[1] + n[2] * zb[2]};
        double ncx = Space3D.projX() * bx / -bz, ncy = Space3D.projY() * by / -bz;
        double hx = Space3D.projX() * quad / -bz, hy = Space3D.projY() * quad / -bz;
        double x0 = Math.max(ncx - hx, -1.02), x1 = Math.min(ncx + hx, 1.02), y0 = Math.max(ncy - hy, -1.02), y1 = Math.min(ncy + hy, 1.02);
        if (x0 >= x1 || y0 >= y1) {
            return;
        }
        double am = ((x0 + x1) / 2 - ncx) / hx, ha = (x1 - x0) / 2 / hx, bm = ((y0 + y1) / 2 - ncy) / hy, hb = (y1 - y0) / 2 / hy;
        SpaceShaders.blackHole((float) x0, (float) y0, (float) x1, (float) y1, new double[]{am * edge, bm * edge, 60}, new double[]{ha * edge, 0, 0},
                new double[]{0, hb * edge, 0}, new double[]{0, 0, -1}, new double[3], new double[3], nb, (float) (time % 200000.0) * 0.05F, alpha,
                (float) edge, 1.15F, 320F);
        Jets.draw(bx, by, bz, n, rs * 900, rs * 16, 0.6F, 0.74F, 1F, 0.98, 618, time / 20.0, px, vis);
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
