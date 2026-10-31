package com.realweather.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.RealWeather;
import it.unimi.dsi.fastutil.ints.IntArrays;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * One frame's worth of soft sky sprites (cloud puffs, wall cloud, rain shafts, tornado funnel slices, dust),
 * sorted far to near and drawn in one pass so the translucent shapes blend into each other properly.
 */
public final class SkyBillboards {
    /** Faces the camera (puffs). */
    public static final int SPHERE = 0;
    /** Stands upright and turns to face the camera (rain shafts). */
    public static final int CYLINDER = 1;
    /** A funnel slice: upright, soft-edged in columns, shaded like a cylinder and lit on the sun side. */
    public static final int FUNNEL = 2;
    /** Lies flat on the ground (dust skirt). */
    public static final int FLAT = 3;

    public static final int TEX_ATLAS = 0;
    public static final int TEX_STREAKS = 1;
    private static final ResourceLocation[] TEXTURES = {
            RealWeather.id("textures/environment/sky_atlas.png"),
            RealWeather.id("textures/environment/tornado_streaks.png")};

    private static final int MAX = 14000;
    private static final float[] X = new float[MAX], Y = new float[MAX], Z = new float[MAX];
    private static final float[] HW = new float[MAX], HH = new float[MAX], ROT = new float[MAX];
    private static final float[] U0 = new float[MAX], V0 = new float[MAX], U1 = new float[MAX], V1 = new float[MAX];
    private static final float[] R = new float[MAX], G = new float[MAX], B = new float[MAX], A = new float[MAX];
    private static final float[] BOTTOM = new float[MAX], SIDE = new float[MAX], DIST = new float[MAX];
    private static final int[] TEX = new int[MAX], TYPE = new int[MAX], ORDER = new int[MAX];
    private static int count;
    private static int split;
    private static boolean sorted;

    // Per-frame view settings (set by begin()).
    private static float lookX, lookY, lookZ = 1F;
    private static float limit = 700F;
    private static float fogR = 0.7F, fogG = 0.8F, fogB = 1F;
    private static float visibility = 2000F;
    private static float fadeEnd = 2000F;

    /** Atlas cells (4 x 4). */
    public static final int CUMULUS = 0, SCUD = 6, BASE = 8, STRATUS = 10, DUST = 11, SHAFT = 12, RING = 13, WALL = 14;

    private SkyBillboards() {}

    /**
     * Starts a frame. Sprites further than {@code limit} are pulled in to that distance and shrunk to keep their
     * size on screen (so the far plane never clips them); haze toward the fog colour grows with distance over
     * {@code visibility}, and sprites fade out completely by {@code fadeEnd}.
     */
    public static void begin(Camera camera, float limit, float[] fog, float visibility, float fadeEnd) {
        count = 0;
        sorted = false;
        Vector3f look = camera.getLookVector();
        lookX = look.x;
        lookY = look.y;
        lookZ = look.z;
        SkyBillboards.limit = limit;
        fogR = fog[0];
        fogG = fog[1];
        fogB = fog[2];
        SkyBillboards.visibility = Math.max(8F, visibility);
        SkyBillboards.fadeEnd = fadeEnd;
    }

    public static int count() {
        return count;
    }

    public static boolean full() {
        return count >= MAX;
    }

    /** Adds a sprite showing one atlas cell. Positions are camera-relative. */
    public static void cell(int type, int cell, float x, float y, float z, float hw, float hh, float rot,
                            float r, float g, float b, float a, float bottomShade, float side) {
        float u0 = (cell % 4) * 0.25F;
        float v0 = (cell / 4) * 0.25F;
        add(TEX_ATLAS, type, x, y, z, hw, hh, rot, u0 + 0.002F, v0 + 0.002F, u0 + 0.248F, v0 + 0.248F, r, g, b, a, bottomShade, side);
    }

    public static void add(int tex, int type, float x, float y, float z, float hw, float hh, float rot, float u0, float v0, float u1, float v1,
                           float r, float g, float b, float a, float bottomShade, float side) {
        if (count >= MAX || a <= 0.004F) {
            return;
        }
        float d2 = x * x + y * y + z * z;
        float d = (float) Math.sqrt(d2);
        float size = Math.max(hw, hh);
        // Behind the camera: skip.
        if (x * lookX + y * lookY + z * lookZ < -size * 1.5F && d > size) {
            return;
        }
        if (d > fadeEnd * 0.75F) {
            float t = Math.min(1F, (d - fadeEnd * 0.75F) / (fadeEnd * 0.25F));
            a *= 1F - t * t * (3F - 2F * t);
            if (a <= 0.004F) {
                return;
            }
        }
        // Aerial perspective: distant clouds take on the colour of the haze.
        float haze = (1F - (float) Math.exp(-d / visibility)) * 0.92F;
        r += (fogR - r) * haze;
        g += (fogG - g) * haze;
        b += (fogB - b) * haze;
        if (d > limit) {
            float k = limit / d;
            x *= k;
            y *= k;
            z *= k;
            hw *= k;
            hh *= k;
        }
        int i = count++;
        X[i] = x;
        Y[i] = y;
        Z[i] = z;
        HW[i] = hw;
        HH[i] = hh;
        ROT[i] = rot;
        U0[i] = u0;
        V0[i] = v0;
        U1[i] = u1;
        V1[i] = v1;
        R[i] = r;
        G[i] = g;
        B[i] = b;
        A[i] = a;
        BOTTOM[i] = bottomShade;
        SIDE[i] = side;
        TEX[i] = tex;
        TYPE[i] = type;
        DIST[i] = d2;
        ORDER[i] = i;
    }

    /**
     * Draws either the far sprites (beyond {@code splitDist}, drawn right after the sky so the terrain covers them)
     * or the near ones (drawn later, depth-tested against the world).
     */
    public static void render(Camera camera, boolean far, float splitDist) {
        if (count == 0) {
            return;
        }
        if (!sorted) {
            IntArrays.quickSort(ORDER, 0, count, (a, b) -> Float.compare(DIST[b], DIST[a]));
            float s2 = splitDist * splitDist;
            split = 0;
            while (split < count && DIST[ORDER[split]] > s2) {
                split++;
            }
            sorted = true;
        }
        int from = far ? 0 : split;
        int to = far ? split : count;
        if (from >= to) {
            return;
        }
        Vector3f left = new Vector3f(camera.getLeftVector());
        Vector3f up = new Vector3f(camera.getUpVector());
        Vector3f right = new Vector3f(left).negate();
        int bound = -1;
        BufferBuilder buf = null;
        int light = LightTexture.FULL_BRIGHT;
        for (int k = from; k < to; k++) {
            int i = ORDER[k];
            if (TEX[i] != bound || buf == null) {
                if (buf != null) {
                    draw(buf);
                }
                bound = TEX[i];
                RenderSystem.setShaderTexture(0, TEXTURES[bound]);
                buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }
            float x = X[i], y = Y[i], z = Z[i];
            float r = R[i], g = G[i], b = B[i], a = A[i];
            float rb = r * BOTTOM[i], gb = g * BOTTOM[i], bb = b * BOTTOM[i];
            switch (TYPE[i]) {
                case SPHERE -> {
                    float c = (float) Math.cos(ROT[i]), s = (float) Math.sin(ROT[i]);
                    float rx = right.x * c + up.x * s, ry = right.y * c + up.y * s, rz = right.z * c + up.z * s;
                    float ux = -right.x * s + up.x * c, uy = -right.y * s + up.y * c, uz = -right.z * s + up.z * c;
                    float w = HW[i], h = HH[i];
                    // Lit on top, shaded underneath (the bottom edge of the sprite is darker).
                    float shadeL = 1F - SIDE[i] * 0.12F, shadeR = 1F + SIDE[i] * 0.12F;
                    buf.addVertex(x - rx * w - ux * h, y - ry * w - uy * h, z - rz * w - uz * h).setUv(U0[i], V1[i])
                            .setColor(k(rb * shadeL), k(gb * shadeL), k(bb * shadeL), k(a)).setLight(light);
                    buf.addVertex(x + rx * w - ux * h, y + ry * w - uy * h, z + rz * w - uz * h).setUv(U1[i], V1[i])
                            .setColor(k(rb * shadeR), k(gb * shadeR), k(bb * shadeR), k(a)).setLight(light);
                    buf.addVertex(x + rx * w + ux * h, y + ry * w + uy * h, z + rz * w + uz * h).setUv(U1[i], V0[i])
                            .setColor(k(r * shadeR), k(g * shadeR), k(b * shadeR), k(a)).setLight(light);
                    buf.addVertex(x - rx * w + ux * h, y - ry * w + uy * h, z - rz * w + uz * h).setUv(U0[i], V0[i])
                            .setColor(k(r * shadeL), k(g * shadeL), k(b * shadeL), k(a)).setLight(light);
                }
                case CYLINDER, FUNNEL -> {
                    float hx = -z, hz = x;
                    float len = (float) Math.sqrt(hx * hx + hz * hz);
                    if (len < 1.0E-4F) {
                        hx = 1F;
                        hz = 0F;
                    } else {
                        hx /= len;
                        hz /= len;
                    }
                    float w = HW[i], h = HH[i];
                    if (TYPE[i] == CYLINDER) {
                        buf.addVertex(x - hx * w, y - h, z - hz * w).setUv(U0[i], V1[i]).setColor(k(rb), k(gb), k(bb), k(a)).setLight(light);
                        buf.addVertex(x + hx * w, y - h, z + hz * w).setUv(U1[i], V1[i]).setColor(k(rb), k(gb), k(bb), k(a)).setLight(light);
                        buf.addVertex(x + hx * w, y + h, z + hz * w).setUv(U1[i], V0[i]).setColor(k(r), k(g), k(b), k(a * 0.8F)).setLight(light);
                        buf.addVertex(x - hx * w, y + h, z - hz * w).setUv(U0[i], V0[i]).setColor(k(r), k(g), k(b), k(a * 0.8F)).setLight(light);
                    } else {
                        funnelSlice(buf, x, y, z, hx, hz, w, h, i, light);
                    }
                }
                default -> {
                    float w = HW[i];
                    float c = (float) Math.cos(ROT[i]), s = (float) Math.sin(ROT[i]);
                    float ax = c * w, az = s * w, bx = -s * w, bz = c * w;
                    buf.addVertex(x - ax - bx, y, z - az - bz).setUv(U0[i], V1[i]).setColor(k(r), k(g), k(b), k(a)).setLight(light);
                    buf.addVertex(x + ax - bx, y, z + az - bz).setUv(U1[i], V1[i]).setColor(k(r), k(g), k(b), k(a)).setLight(light);
                    buf.addVertex(x + ax + bx, y, z + az + bz).setUv(U1[i], V0[i]).setColor(k(r), k(g), k(b), k(a)).setLight(light);
                    buf.addVertex(x - ax + bx, y, z - az + bz).setUv(U0[i], V0[i]).setColor(k(r), k(g), k(b), k(a)).setLight(light);
                }
            }
        }
        if (buf != null) {
            draw(buf);
        }
    }

    private static final float[] COL_ALPHA = {0F, 0.55F, 0.92F, 1F, 1F, 0.92F, 0.55F, 0F};
    private static final float[] COL_POS = {-1F, -0.82F, -0.55F, -0.2F, 0.2F, 0.55F, 0.82F, 1F};

    /** Seven columns across the slice: soft edges, a brighter middle (it is round), and the sun side brighter still. */
    private static void funnelSlice(BufferBuilder buf, float x, float y, float z, float hx, float hz, float w, float h, int i, int light) {
        float du = U1[i] - U0[i];
        float side = SIDE[i];
        for (int c = 0; c < 7; c++) {
            float p0 = COL_POS[c], p1 = COL_POS[c + 1];
            float a0 = A[i] * COL_ALPHA[c], a1 = A[i] * COL_ALPHA[c + 1];
            float s0 = shade(p0, side), s1 = shade(p1, side);
            float x0 = x + hx * w * p0, z0 = z + hz * w * p0;
            float x1 = x + hx * w * p1, z1 = z + hz * w * p1;
            float u0 = U0[i] + du * (p0 + 1F) * 0.5F, u1 = U0[i] + du * (p1 + 1F) * 0.5F;
            float bot = BOTTOM[i];
            buf.addVertex(x0, y - h, z0).setUv(u0, V1[i]).setColor(k(R[i] * s0 * bot), k(G[i] * s0 * bot), k(B[i] * s0 * bot), k(a0)).setLight(light);
            buf.addVertex(x1, y - h, z1).setUv(u1, V1[i]).setColor(k(R[i] * s1 * bot), k(G[i] * s1 * bot), k(B[i] * s1 * bot), k(a1)).setLight(light);
            buf.addVertex(x1, y + h, z1).setUv(u1, V0[i]).setColor(k(R[i] * s1), k(G[i] * s1), k(B[i] * s1), k(a1)).setLight(light);
            buf.addVertex(x0, y + h, z0).setUv(u0, V0[i]).setColor(k(R[i] * s0), k(G[i] * s0), k(B[i] * s0), k(a0)).setLight(light);
        }
    }

    private static float shade(float p, float side) {
        float round = 0.78F + 0.22F * (float) Math.sqrt(Math.max(0F, 1F - p * p));
        return round * (1F + side * p * 0.28F);
    }

    /** Vertex colours are packed into bytes: anything over 1 would spill into the next channel. */
    private static float k(float v) {
        return v < 0F ? 0F : v > 1F ? 1F : v;
    }

    private static void draw(BufferBuilder buf) {
        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}
