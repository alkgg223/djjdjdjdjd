package com.realweather.client.space;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.realweather.RealWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * A small 3D renderer for what is out in space, separate from the Minecraft sky: real spheres (lit by the sun or their
 * star, with day and night sides and phases, turning on their axes), rings, boxes for spacecraft, ribbons, cones and
 * glowing particles, at real 3D positions far out from the camera with their own depth range (so they hide each
 * other properly and are never clipped by the far plane). Everything is worked out on the CPU in double precision in
 * view space, so even the tiniest things seen at a telescope's highest power are steady.
 */
public final class Space3D {
    public static final int ADD = 0, NORMAL = 1, DEPTH_ONLY = 2;
    public static final ResourceLocation WHITE = RealWeather.id("textures/environment/space3d/metal.png");
    public static final ResourceLocation FLAME = RealWeather.id("textures/environment/space3d/flame.png");
    public static final ResourceLocation GLOW = RealWeather.id("textures/environment/space/glow.png");
    public static final ResourceLocation STAR = RealWeather.id("textures/environment/space/star.png");

    /** World (camera-relative) to view, sky frame to world, sky frame to view: 3 x 3, column-major. */
    public static final double[] VIEW = new double[9], SKY = new double[9], SKYVIEW = new double[9];
    /** The sun's direction in view space. */
    public static final double[] SUN = new double[3];
    public static double time;
    public static float night, clear;
    private static Matrix4f baseProj;
    private static Matrix4f savedProj;
    private static VertexSorting savedSort;
    private static BufferBuilder buf;
    private static ResourceLocation bound;
    private static int boundMode = -1;
    private static boolean boundTest, boundWrite;
    private static boolean open;

    private Space3D() {}

    /** Starts drawing for this frame (after the sky). */
    public static void begin(RenderLevelStageEvent event, ClientLevel level, float pt) {
        Matrix4f mv = event.getModelViewMatrix();
        set(VIEW, mv.m00(), mv.m01(), mv.m02(), mv.m10(), mv.m11(), mv.m12(), mv.m20(), mv.m21(), mv.m22());
        double th = level.getTimeOfDay(pt) * Math.PI * 2.0;
        double c = Math.cos(th), s = Math.sin(th);
        // Turned -90 degrees about Y after turning by the time of day about X, as the sun, moon and stars are.
        set(SKY, 0, 0, 1, -s, c, 0, -c, -s, 0);
        mul(VIEW, SKY, SKYVIEW);
        double[] sw = {-Math.sin(th), Math.cos(th), 0};
        apply(VIEW, sw[0], sw[1], sw[2], SUN);
        time = level.getGameTime() + pt;
        night = Mth.clamp(level.getStarBrightness(pt) * 2.2F, 0F, 1F);
        clear = Mth.clamp(1F - level.getRainLevel(pt), 0F, 1F);
        baseProj = new Matrix4f(event.getProjectionMatrix());
        savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        savedSort = RenderSystem.getVertexSorting();
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        open = true;
        range(5F, 80000F);
    }

    /** The depth range for what comes next (as tight round it as possible, for precise hiding). */
    public static void range(float near, float far) {
        flush();
        Matrix4f p = new Matrix4f(baseProj);
        p.m22((far + near) / (near - far));
        p.m32(2F * far * near / (near - far));
        RenderSystem.setProjectionMatrix(p, VertexSorting.DISTANCE_TO_ORIGIN);
    }

    /** Clears what has been drawn into the depth buffer so far (between things at very different distances). */
    public static void clearDepth() {
        flush();
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** Fills the whole view with a colour (the telescope's dark field at high power). */
    public static void fullScreen(float r, float g, float b, float a) {
        use(WHITE, NORMAL, false, false);
        double z = -10, hx = 10 / Math.abs(baseProj.m00()) * 1.5, hy = 10 / Math.abs(baseProj.m11()) * 1.5;
        vtx(-hx, -hy, z, 0F, 1F, r, g, b, a);
        vtx(hx, -hy, z, 1F, 1F, r, g, b, a);
        vtx(hx, hy, z, 1F, 0F, r, g, b, a);
        vtx(-hx, hy, z, 0F, 0F, r, g, b, a);
        flush();
    }

    public static boolean isOpen() {
        return open;
    }

    /** Finishes: puts the projection back and clears the depth used (the land is drawn next). */
    public static void end() {
        flush();
        buf = null;
        bound = null;
        boundMode = -1;
        if (savedProj != null) {
            RenderSystem.setProjectionMatrix(savedProj, savedSort);
        }
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        open = false;
    }

    // ------------------------------------------------------------------------------------------- batching

    /** Begins (or continues) a batch: texture, blending, and whether it tests and writes depth. */
    public static void use(ResourceLocation texture, int mode, boolean depthTest, boolean depthWrite) {
        if (buf != null && bound == texture && boundMode == mode && boundTest == depthTest && boundWrite == depthWrite) {
            return;
        }
        flush();
        if (mode == ADD) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ZERO,
                    GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        }
        RenderSystem.colorMask(mode != DEPTH_ONLY, mode != DEPTH_ONLY, mode != DEPTH_ONLY, mode != DEPTH_ONLY);
        if (depthTest) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(depthWrite);
        com.realweather.client.sky.VolumeClouds.useGlowShader(texture);
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bound = texture;
        boundMode = mode;
        boundTest = depthTest;
        boundWrite = depthWrite;
    }

    public static void flush() {
        if (buf != null) {
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            buf = null;
        }
    }

    public static void vtx(double x, double y, double z, float u, float v, float r, float g, float b, float a) {
        if (maskOn) {
            double l = Math.sqrt(x * x + y * y + z * z) + 1.0E-12;
            float c = (float) ((x * maskX + y * maskY + z * maskZ) / l);
            a *= smooth(maskOuter, maskInner, c);
        }
        buf.addVertex((float) x, (float) y, (float) z).setUv(u, v).setColor(k(r), k(g), k(b), k(a));
    }

    private static boolean maskOn;
    private static double maskX, maskY, maskZ;
    private static float maskInner, maskOuter;

    /** Shows only what lies within `radius` radians of a view direction (things seen against the sun's disc). */
    public static void mask(double dx, double dy, double dz, double radius) {
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        maskX = dx / l;
        maskY = dy / l;
        maskZ = dz / l;
        maskInner = (float) Math.cos(radius * 0.96);
        maskOuter = (float) Math.cos(radius);
        maskOn = true;
    }

    public static void unmask() {
        flush();
        maskOn = false;
    }

    private static float k(float v) {
        return v < 0F ? 0F : v > 1F ? 1F : v;
    }

    // ------------------------------------------------------------------------------------------- maths

    public static void set(double[] m, double... v) {
        System.arraycopy(v, 0, m, 0, 9);
    }

    /** out = a * b (3 x 3, column-major). */
    public static void mul(double[] a, double[] b, double[] out) {
        double[] r = new double[9];
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                r[col * 3 + row] = a[row] * b[col * 3] + a[3 + row] * b[col * 3 + 1] + a[6 + row] * b[col * 3 + 2];
            }
        }
        System.arraycopy(r, 0, out, 0, 9);
    }

    public static void apply(double[] m, double x, double y, double z, double[] out) {
        double ox = m[0] * x + m[3] * y + m[6] * z, oy = m[1] * x + m[4] * y + m[7] * z, oz = m[2] * x + m[5] * y + m[8] * z;
        out[0] = ox;
        out[1] = oy;
        out[2] = oz;
    }

    /** A rotation (column-major) turning local Y to `axis` and spinning by `spin` about it. */
    public static double[] orient(double ax, double ay, double az, double spin) {
        double l = Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= l;
        ay /= l;
        az /= l;
        // Any vector not parallel to the axis gives the other two directions.
        double tx = Math.abs(ay) < 0.9 ? 0 : 1, ty = Math.abs(ay) < 0.9 ? 1 : 0, tz = 0;
        double xx = ty * az - tz * ay, xy = tz * ax - tx * az, xz = tx * ay - ty * ax;
        double xl = Math.sqrt(xx * xx + xy * xy + xz * xz);
        xx /= xl;
        xy /= xl;
        xz /= xl;
        double zx = xy * az - xz * ay, zy = xz * ax - xx * az, zz = xx * ay - xy * ax;
        double c = Math.cos(spin), s = Math.sin(spin);
        double rx = xx * c - zx * s, ry = xy * c - zy * s, rz = xz * c - zz * s;
        double fx = xx * s + zx * c, fy = xy * s + zy * c, fz = xz * s + zz * c;
        return new double[]{rx, ry, rz, ax, ay, az, fx, fy, fz};
    }

    public static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    // ------------------------------------------------------------------------------------------- shapes

    private static final double[] N = new double[3];

    /**
     * A lit sphere. Centre and light direction in view space; `rot` turns the body (local Y = its axis) into view
     * space. Lit side by the light, the night side at `ambient`; `glow` adds its own light (a hot planet or a star).
     * For a star (`star`), the disc darkens towards the limb instead.
     */
    public static void sphere(ResourceLocation tex, double cx, double cy, double cz, double r, double[] rot, double lx, double ly, double lz,
                              float ambient, float cr, float cg, float cb, float glow, float alpha, boolean star, float uShift, int detail) {
        int lat = Math.max(8, detail), lon = lat * 2;
        use(tex, NORMAL, true, true);
        double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double vx = -cx / dl, vy = -cy / dl, vz = -cz / dl;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                if (!facing(rot, cx, cy, cz, r, lat, lon, i, j)) {
                    continue;
                }
                corner(tex, cx, cy, cz, r, rot, lx, ly, lz, ambient, cr, cg, cb, glow, alpha, star, uShift, lat, lon, i, j, vx, vy, vz);
                corner(tex, cx, cy, cz, r, rot, lx, ly, lz, ambient, cr, cg, cb, glow, alpha, star, uShift, lat, lon, i + 1, j, vx, vy, vz);
                corner(tex, cx, cy, cz, r, rot, lx, ly, lz, ambient, cr, cg, cb, glow, alpha, star, uShift, lat, lon, i + 1, j + 1, vx, vy, vz);
                corner(tex, cx, cy, cz, r, rot, lx, ly, lz, ambient, cr, cg, cb, glow, alpha, star, uShift, lat, lon, i, j + 1, vx, vy, vz);
            }
        }
    }

    /** Whether a cell of a sphere faces the camera (the far side is left out). */
    public static boolean facing(double[] rot, double cx, double cy, double cz, double r, int lat, int lon, int i, int j) {
        double th = Math.PI * (i + 0.5) / lat, ph = Math.PI * 2 * (j + 0.5) / lon;
        apply(rot, Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph), N);
        double px = cx + N[0] * r, py = cy + N[1] * r, pz = cz + N[2] * r;
        // Generous at the limb, so the outline stays smooth.
        return -(N[0] * px + N[1] * py + N[2] * pz) / (Math.sqrt(px * px + py * py + pz * pz) + 1.0E-12) > -0.15;
    }

    /** A sphere that only goes into the depth buffer (it hides what is behind it but shows nothing itself). */
    public static void depthSphere(double cx, double cy, double cz, double r) {
        use(WHITE, DEPTH_ONLY, true, true);
        int lat = 16, lon = 32;
        double[] rot = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                if (!facing(rot, cx, cy, cz, r, lat, lon, i, j)) {
                    continue;
                }
                for (int q = 0; q < 4; q++) {
                    int ii = i + (q == 1 || q == 2 ? 1 : 0), jj = j + (q >= 2 ? 1 : 0);
                    double th = Math.PI * ii / lat, ph = Math.PI * 2 * jj / lon;
                    vtx(cx + Math.sin(th) * Math.cos(ph) * r, cy + Math.cos(th) * r, cz + Math.sin(th) * Math.sin(ph) * r, 0F, 0F, 1F, 1F, 1F, 1F);
                }
            }
        }
    }

    private static void corner(ResourceLocation tex, double cx, double cy, double cz, double r, double[] rot, double lx, double ly, double lz,
                               float ambient, float cr, float cg, float cb, float glow, float alpha, boolean star, float uShift, int lat, int lon,
                               int i, int j, double vx, double vy, double vz) {
        double th = Math.PI * i / lat, ph = Math.PI * 2 * j / lon;
        double sx = Math.sin(th) * Math.cos(ph), sy = Math.cos(th), sz = Math.sin(th) * Math.sin(ph);
        apply(rot, sx, sy, sz, N);
        float shade;
        if (star) {
            float mu = (float) Math.max(0, N[0] * vx + N[1] * vy + N[2] * vz);
            shade = 0.45F + 0.55F * (float) Math.pow(mu, 0.5);
        } else {
            float ndl = (float) (N[0] * lx + N[1] * ly + N[2] * lz);
            // A soft terminator (light scattering in the atmosphere), black on the night side.
            float lit = smooth(-0.08F, 0.35F, ndl) * (0.35F + 0.65F * Math.max(0F, ndl));
            shade = ambient + (1F - ambient) * lit + glow;
        }
        vtx(cx + N[0] * r, cy + N[1] * r, cz + N[2] * r, (float) j / lon + uShift, (float) i / lat, cr * shade, cg * shade, cb * shade, alpha);
    }

    /** A thin glowing shell round a planet's limb: its atmosphere, brighter on the lit side. */
    public static void atmosphere(double cx, double cy, double cz, double r, double lx, double ly, double lz, float ar, float ag, float ab,
                                  float strength) {
        use(GLOW, ADD, true, false);
        int n = 48;
        double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double ux = 1, uy = 0, uz = 0;
        // A ring of quads square to the line of sight, from the limb outwards.
        double vx = cx / dl, vy = cy / dl, vz = cz / dl;
        double ax = uy * vz - uz * vy, ay = uz * vx - ux * vz, az = ux * vy - uy * vx;
        double al = Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1.0E-6) {
            ax = 0;
            ay = 0;
            az = 1;
            al = 1;
        }
        ax /= al;
        ay /= al;
        az /= al;
        double bx = vy * az - vz * ay, by = vz * ax - vx * az, bz = vx * ay - vy * ax;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            double d0x = Math.cos(a0) * ax + Math.sin(a0) * bx, d0y = Math.cos(a0) * ay + Math.sin(a0) * by, d0z = Math.cos(a0) * az + Math.sin(a0) * bz;
            double d1x = Math.cos(a1) * ax + Math.sin(a1) * bx, d1y = Math.cos(a1) * ay + Math.sin(a1) * by, d1z = Math.cos(a1) * az + Math.sin(a1) * bz;
            float l0 = 0.15F + 0.85F * smooth(-0.3F, 0.6F, (float) (d0x * lx + d0y * ly + d0z * lz));
            float l1 = 0.15F + 0.85F * smooth(-0.3F, 0.6F, (float) (d1x * lx + d1y * ly + d1z * lz));
            double ri = r * 0.985, ro = r * 1.09;
            vtx(cx + d0x * ri, cy + d0y * ri, cz + d0z * ri, 0.5F, 0.5F, ar, ag, ab, strength * l0);
            vtx(cx + d1x * ri, cy + d1y * ri, cz + d1z * ri, 0.5F, 0.5F, ar, ag, ab, strength * l1);
            vtx(cx + d1x * ro, cy + d1y * ro, cz + d1z * ro, 0.5F, 0.98F, ar, ag, ab, 0F);
            vtx(cx + d0x * ro, cy + d0y * ro, cz + d0z * ro, 0.5F, 0.98F, ar, ag, ab, 0F);
        }
    }

    /** A flat ring in the body's equatorial plane (local XZ), radii r0..r1, lit by the light. */
    public static void ring(ResourceLocation tex, double cx, double cy, double cz, double r0, double r1, double[] rot, double lx, double ly, double lz,
                            float alpha) {
        use(tex, NORMAL, true, false);
        int n = 96;
        double[] p = new double[3];
        // How brightly the ring plane is lit (from either side).
        apply(rot, 0, 1, 0, p);
        float lit = 0.25F + 0.75F * (float) Math.abs(p[0] * lx + p[1] * ly + p[2] * lz);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            ringVertex(cx, cy, cz, rot, a0, r0, 0F, lit, alpha, p);
            ringVertex(cx, cy, cz, rot, a0, r1, 1F, lit, alpha, p);
            ringVertex(cx, cy, cz, rot, a1, r1, 1F, lit, alpha, p);
            ringVertex(cx, cy, cz, rot, a1, r0, 0F, lit, alpha, p);
        }
    }

    private static void ringVertex(double cx, double cy, double cz, double[] rot, double a, double r, float u, float lit, float alpha, double[] p) {
        apply(rot, Math.cos(a) * r, 0, Math.sin(a) * r, p);
        vtx(cx + p[0], cy + p[1], cz + p[2], u, 0.5F, lit, lit, lit, alpha);
    }

    /** A camera-facing square (view space), turned by `rot` radians. */
    public static void billboard(ResourceLocation tex, int mode, double x, double y, double z, double size, float rot, float r, float g, float b, float a) {
        if (a <= 0.003F) {
            return;
        }
        use(tex, mode, true, false);
        double c = Math.cos(rot) * size, s = Math.sin(rot) * size;
        vtx(x - c + s, y - s - c, z, 0F, 1F, r, g, b, a);
        vtx(x + c + s, y + s - c, z, 1F, 1F, r, g, b, a);
        vtx(x + c - s, y + s + c, z, 1F, 0F, r, g, b, a);
        vtx(x - c - s, y - s + c, z, 0F, 0F, r, g, b, a);
    }

    /** A strip from p0 (u = 0) to p1 (u = 1), turned to face the camera, of half-widths w0 and w1. */
    public static void ribbon(ResourceLocation tex, int mode, double x0, double y0, double z0, double x1, double y1, double z1, double w0, double w1,
                              float r, float g, float b, float a0, float a1) {
        if (a0 <= 0.003F && a1 <= 0.003F) {
            return;
        }
        use(tex, mode, true, false);
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double mx = (x0 + x1) * 0.5, my = (y0 + y1) * 0.5, mz = (z0 + z1) * 0.5;
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz) + 1.0E-12;
        sx /= l;
        sy /= l;
        sz /= l;
        vtx(x0 - sx * w0, y0 - sy * w0, z0 - sz * w0, 0F, 1F, r, g, b, a0);
        vtx(x1 - sx * w1, y1 - sy * w1, z1 - sz * w1, 1F, 1F, r, g, b, a1);
        vtx(x1 + sx * w1, y1 + sy * w1, z1 + sz * w1, 1F, 0F, r, g, b, a1);
        vtx(x0 + sx * w0, y0 + sy * w0, z0 + sz * w0, 0F, 0F, r, g, b, a0);
    }

    /** A glowing open cone from an apex along a direction (a pulsar's beam, a jet), fading towards its end. */
    public static void cone(double ax, double ay, double az, double dx, double dy, double dz, double length, double radius, float r, float g, float b,
                            float a) {
        use(GLOW, ADD, true, false);
        double[] rot = orient(dx, dy, dz, 0);
        int n = 20;
        double[] p0 = new double[3], p1 = new double[3];
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI * 2 * i / n, t1 = Math.PI * 2 * (i + 1) / n;
            apply(rot, Math.cos(t0) * radius, length, Math.sin(t0) * radius, p0);
            apply(rot, Math.cos(t1) * radius, length, Math.sin(t1) * radius, p1);
            vtx(ax, ay, az, 0.5F, 0.5F, r, g, b, a);
            vtx(ax, ay, az, 0.5F, 0.5F, r, g, b, a);
            vtx(ax + p1[0], ay + p1[1], az + p1[2], 0.5F, 0.97F, r, g, b, 0F);
            vtx(ax + p0[0], ay + p0[1], az + p0[2], 0.5F, 0.97F, r, g, b, 0F);
        }
    }

    /**
     * A box (spacecraft parts) with half-extents along three view-space axes, each face lit by the light; `spec`
     * adds a mirror glint where the face reflects the light into the camera (solar panels).
     */
    public static void box(ResourceLocation tex, double cx, double cy, double cz, double[] ax, double[] ay, double[] az, double lx, double ly, double lz,
                           float cr, float cg, float cb, float ambient, float spec, float alpha) {
        use(tex, NORMAL, true, alpha >= 0.999F);
        double[][] axes = {ax, ay, az};
        double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double vx = -cx / dl, vy = -cy / dl, vz = -cz / dl;
        for (int f = 0; f < 3; f++) {
            double[] n = axes[f], u = axes[(f + 1) % 3], v = axes[(f + 2) % 3];
            double nl = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double nx = n[0] / nl * sgn, ny = n[1] / nl * sgn, nz = n[2] / nl * sgn;
                float ndl = (float) Math.max(0, nx * lx + ny * ly + nz * lz);
                float shade = ambient + (1F - ambient) * ndl;
                if (spec > 0F) {
                    // Mirror reflection of the light towards the camera.
                    double rdot = 2 * (nx * lx + ny * ly + nz * lz);
                    double rx = rdot * nx - lx, ry = rdot * ny - ly, rz = rdot * nz - lz;
                    float g = (float) Math.max(0, rx * vx + ry * vy + rz * vz);
                    shade += spec * (float) Math.pow(g, 60);
                }
                double fx = cx + n[0] * sgn, fy = cy + n[1] * sgn, fz = cz + n[2] * sgn;
                vtx(fx - u[0] - v[0], fy - u[1] - v[1], fz - u[2] - v[2], 0F, 1F, cr * shade, cg * shade, cb * shade, alpha);
                vtx(fx + u[0] - v[0], fy + u[1] - v[1], fz + u[2] - v[2], 1F, 1F, cr * shade, cg * shade, cb * shade, alpha);
                vtx(fx + u[0] + v[0], fy + u[1] + v[1], fz + u[2] + v[2], 1F, 0F, cr * shade, cg * shade, cb * shade, alpha);
                vtx(fx - u[0] + v[0], fy - u[1] + v[1], fz - u[2] + v[2], 0F, 0F, cr * shade, cg * shade, cb * shade, alpha);
            }
        }
    }

    /** A stateless hash, 0..1. */
    public static float h(long seed, long k) {
        long x = seed * 0x9E3779B97F4A7C15L + k * 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 29;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 32;
        return (x >>> 40) / (float) (1L << 24);
    }

    /**
     * Fire round a star: flames and loops rising off its surface, curling and fading from white-hot through orange to
     * red; most of them are seen round the limb, as on the real Sun. Stateless (each flame's life repeats), so it
     * costs nothing to keep.
     */
    public static void starFire(double cx, double cy, double cz, double r, long seed, int count, float hot, float time, float strength) {
        if (strength <= 0.01F) {
            return;
        }
        use(FLAME, ADD, true, false);
        double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double vx = cx / dl, vy = cy / dl, vz = cz / dl;
        for (int i = 0; i < count; i++) {
            // A direction on the star, mostly round the limb as seen from here.
            float u1 = h(seed, i * 5L), u2 = h(seed, i * 5L + 1);
            double th = Math.acos(1 - 2 * u1), ph = u2 * Math.PI * 2;
            double dx = Math.sin(th) * Math.cos(ph), dy = Math.cos(th), dz = Math.sin(th) * Math.sin(ph);
            double along = dx * vx + dy * vy + dz * vz;
            double limb = 1 - Math.abs(along);
            float life = 50F + 80F * h(seed, i * 5L + 2);
            float age = ((time + h(seed, i * 5L + 3) * life) % life) / life;
            double lift = r * (0.015 + age * (0.18 + 0.35 * h(seed, i * 5L + 4)));
            // Curling sideways as it rises.
            double swirl = age * 0.6 * (h(seed, i * 7L) - 0.5);
            double px = cx + (dx + swirl * dz) * (r + lift), py = cy + dy * (r + lift), pz = cz + (dz - swirl * dx) * (r + lift);
            float size = (float) (r * (0.06 + 0.12 * h(seed, i * 7L + 1)) * (1.2 - 0.6 * age));
            float a = Mth.sin(Mth.PI * age) * strength * (0.25F + 0.75F * (float) limb) * 0.8F;
            // White-hot, yellow, orange, deep red.
            float red = 1F, green = Mth.lerp(age, 0.85F + 0.15F * hot, 0.25F), blue = Mth.lerp(age, 0.55F + 0.45F * hot * hot, 0.05F);
            billboard(FLAME, ADD, px, py, pz, size, age * 3F + i, red, green, blue, a);
        }
    }
}
