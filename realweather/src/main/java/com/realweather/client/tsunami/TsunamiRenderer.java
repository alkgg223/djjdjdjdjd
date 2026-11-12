package com.realweather.client.tsunami;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.RealWeather;
import com.realweather.client.sky.SkyLight;
import com.realweather.server.Tsunami;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

/**
 * Draws each tsunami as a real wave: a long, rising back slope, a crest that pitches forward into a curling lip,
 * a steep concave face down to the toe where it runs over the land, all in moving sea-green water with white foam
 * along the crest, the lip and the toe; behind it a sheet of foaming flood water over everything it has covered.
 */
public final class TsunamiRenderer {
    private static final ResourceLocation WATER = RealWeather.id("textures/environment/tsunami_water.png");
    private static final ResourceLocation FOAM = RealWeather.id("textures/environment/tsunami_foam.png");

    // Cross-section in wave heights: (ahead of the toe, up from the water it rides on). Back slope to the curling lip...
    private static final float[][] BODY = {{-7.0F, -0.05F}, {-4.6F, 0.1F}, {-3.1F, 0.3F}, {-1.9F, 0.55F}, {-1.0F, 0.8F}, {-0.4F, 0.95F},
            {0.1F, 1.03F}, {0.5F, 1.0F}, {0.78F, 0.88F}, {0.88F, 0.7F}, {0.8F, 0.55F}, {0.64F, 0.48F}};
    private static final float[] BODY_LIGHT = {0.5F, 0.55F, 0.6F, 0.66F, 0.74F, 0.82F, 0.92F, 0.95F, 0.92F, 0.86F, 0.8F, 0.75F};
    private static final float[] BODY_FOAM = {0.1F, 0.08F, 0.08F, 0.12F, 0.2F, 0.45F, 0.85F, 0.95F, 0.95F, 0.9F, 0.75F, 0.6F};
    // ...and the face, from the toe up under the lip.
    private static final float[][] FACE = {{1.15F, -0.05F}, {1.0F, 0.1F}, {0.8F, 0.24F}, {0.66F, 0.36F}, {0.6F, 0.45F}, {0.64F, 0.48F}};
    private static final float[] FACE_LIGHT = {0.85F, 0.8F, 0.76F, 0.74F, 0.74F, 0.75F};
    private static final float[] FACE_FOAM = {0.9F, 0.7F, 0.3F, 0.25F, 0.45F, 0.6F};
    static final float TOE_DS = 1.15F, LIP_DS = 0.85F;

    private static boolean failed;

    private TsunamiRenderer() {}

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (failed || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || ClientTsunamis.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        try {
            render(event, mc, level);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            RealWeather.LOG.error("Tsunami rendering disabled after an error", e);
        }
    }

    private static void render(RenderLevelStageEvent event, Minecraft mc, ClientLevel level) {
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        // The flood is drawn over the loaded land; the wave itself stands out above the horizon even beyond it.
        int q = com.realweather.ClientConfig.quality();
        float reach = Math.min(new float[]{170F, 250F, 330F, 420F}[q], mc.options.getEffectiveRenderDistance() * 16F + 64F);
        float waveReach = Math.max(320F, Math.min(560F, reach * 1.3F));
        float[] fog = RenderSystem.getShaderFogColor();
        float sky = Mth.clamp(level.getSkyDarken(pt), 0.18F, 1F);
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        LightTexture light = mc.gameRenderer.lightTexture();
        light.turnOnLightLayer();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        try {
            for (int pass = 0; pass < 2; pass++) {
                RenderSystem.setShaderTexture(0, pass == 0 ? WATER : FOAM);
                BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                boolean any = false;
                for (ClientTsunamis.W w : ClientTsunamis.all()) {
                    any |= wave(buf, level, w.t, cam, pt, reach, waveReach, fog, sky, pass == 1);
                }
                MeshData mesh = buf.build();
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            light.turnOffLightLayer();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static final float[] PX = new float[400 * 12], PY = new float[400 * 12], PZ = new float[400 * 12];
    private static final float[] GROUND = new float[2 * 400 * 12];
    private static int groundId = -1, groundSegs;
    private static float groundTime = Float.NaN;

    private static final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap LAND = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
    private static long landClearedAt;
    static final byte SEA = 0, DRY = 1, WET = 2;

    /**
     * What lies under a grid corner of the flood sheet (looked up once and remembered for a few seconds): the sea,
     * land, or land already standing under the real flood water the server has filled in (no sheet needed there).
     */
    private static byte corner(ClientLevel level, Tsunami t, int si, int li, float tile) {
        long now = level.getGameTime();
        if (now - landClearedAt > 100 || LAND.size() > 30000) {
            LAND.clear();
            landClearedAt = now;
        }
        long key = ((long) t.id << 48) | ((si & 0xFFFFFFL) << 24) | (li & 0xFFFFFFL);
        byte v = LAND.getOrDefault(key, (byte) -1);
        if (v < 0) {
            float s = si * tile, l = li * tile;
            double x = t.ox + t.dirX * s - t.dirZ * l;
            double z = t.oz + t.dirZ * s + t.dirX * l;
            if (!level.hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4) || !t.overLand(t.ground(level, x, z))) {
                v = SEA;
            } else {
                int bx = Mth.floor(x), bz = Mth.floor(z);
                int top = com.realweather.server.TsunamiGround.top(level, bx, bz);
                v = level.getFluidState(new net.minecraft.core.BlockPos(bx, top, bz)).isEmpty() ? DRY : WET;
            }
            LAND.put(key, v);
        }
        return v;
    }

    private static boolean land(ClientLevel level, Tsunami t, int si, int li, float tile) {
        return corner(level, t, si, li, tile) != SEA;
    }

    private static boolean wave(BufferBuilder buf, ClientLevel level, Tsunami t, Vec3 cam, float pt, float floodReach, float reach, float[] fog,
                                float sky, boolean foam) {
        float time = t.age + pt;
        float front = t.front + (t.stage == Tsunami.RUNNING ? t.speed() * pt : 0F);
        double sc = t.along(cam.x, cam.z);
        double lc = t.across(cam.x, cam.z);
        float width = t.width();
        float l0 = (float) Math.max(-width, lc - reach), l1 = (float) Math.min(width, lc + reach);
        if (l1 <= l0) {
            return false;
        }
        float h = t.height();
        // SkyLight already holds the daylight (dim at night, under storm clouds, warm at sunset).
        float lr = Math.max(0.08F, SkyLight.r), lg = Math.max(0.08F, SkyLight.g), lb = Math.max(0.1F, SkyLight.b);
        boolean any = false;
        // The flood behind the wave.
        float backEnd = front + (BODY[0][0] - TOE_DS) * h + 1F + 2F;
        float s0 = (float) Math.max(0.0, sc - floodReach);
        float s1 = Math.min(backEnd, (float) (sc + floodReach));
        float fl0 = (float) Math.max(-width, lc - floodReach), fl1 = (float) Math.min(width, lc + floodReach);
        if (t.drained < 0.999F && s1 > s0 && fl1 > fl0) {
            float tile = 16F;
            float flow = t.stage == Tsunami.RUNNING ? time * t.speed() * 0.45F : time * 0.02F;
            float fade = Math.min(1F, (1F - t.drained) * 3F);
            float sea = t.baseY - 0.35F;
            float keep = 1F - t.drained;
            // A sheet on a fixed grid along the wave, lying on the land at the depth of the flood (it follows the ground up
            // and down), sloping into the sea at the shore and tucked under the real water where the server filled it in.
            int si0 = Mth.floor(s0 / tile), si1 = Mth.ceil(s1 / tile);
            int li0 = Mth.floor(fl0 / tile), li1 = Mth.ceil(fl1 / tile);
            float[] cs = new float[4], cl = new float[4];
            byte[] ck = new byte[4];
            for (int si = si0; si < si1; si++) {
                for (int li = li0; li < li1; li++) {
                    ck[0] = corner(level, t, si, li, tile);
                    ck[1] = corner(level, t, si + 1, li, tile);
                    ck[2] = corner(level, t, si + 1, li + 1, tile);
                    ck[3] = corner(level, t, si, li + 1, tile);
                    if (ck[0] != DRY && ck[1] != DRY && ck[2] != DRY && ck[3] != DRY) {
                        continue;
                    }
                    float sa = Math.max(s0, si * tile), sb = Math.min(s1, (si + 1) * tile);
                    float la = Math.max(fl0, li * tile), lb2 = Math.min(fl1, (li + 1) * tile);
                    cs[0] = sa; cl[0] = la;
                    cs[1] = sb; cl[1] = la;
                    cs[2] = sb; cl[2] = lb2;
                    cs[3] = sa; cl[3] = lb2;
                    for (int c = 0; c < 4; c++) {
                        double wx = t.ox + t.dirX * cs[c] - t.dirZ * cl[c];
                        double wz = t.oz + t.dirZ * cs[c] + t.dirX * cl[c];
                        double x = wx - cam.x;
                        double z = wz - cam.z;
                        float surface = ck[c] == SEA ? sea : t.ground(level, wx, wz) + t.floodDepth(cs[c]) * keep - (ck[c] == WET ? 1.4F : 0F);
                        // A gentle swell on the flood.
                        surface += 0.12F * Mth.sin((float) (wx * 0.21 + wz * 0.17) + time * 0.06F);
                        float y = (float) (surface - cam.y);
                        float d = (float) Math.sqrt(x * x + z * z);
                        float haze = 1F - (float) Math.exp(-d / 420F);
                        // Muddy water that mirrors the sky at a grazing angle and lets you see into it from above.
                        float grazing = Mth.clamp(1F - Math.abs(y) / (d + 1F) * 3F, 0F, 1F);
                        float a, r, g, b;
                        if (foam) {
                            a = (0.22F + 0.18F * grazing) * fade;
                            r = 0.92F;
                            g = 0.95F;
                            b = 0.96F;
                        } else {
                            a = (0.72F + 0.22F * grazing) * fade;
                            float mud = 0.35F * (1F - t.drained * 0.5F);
                            r = Mth.lerp(grazing * 0.55F, 0.3F + 0.2F * mud, fog[0]);
                            g = Mth.lerp(grazing * 0.55F, 0.42F + 0.08F * mud, fog[1]);
                            b = Mth.lerp(grazing * 0.55F, 0.45F - 0.12F * mud, fog[2]);
                        }
                        if (ck[c] != DRY) {
                            a *= 0.5F;
                        }
                        vertex(buf, (float) x, y, (float) z, cl[c] / 32F, (cs[c] - flow) / 32F, r * lr, g * lg, b * lb, a, fog, haze, d, floodReach);
                    }
                    any = true;
                }
            }
        }
        if (h < 0.3F || t.stage != Tsunami.RUNNING || Math.abs(sc - front) > reach + 8 * h) {
            return any;
        }
        // The wave itself, segment by segment along its length.
        int segs = Mth.clamp((int) ((l1 - l0) / new float[]{12F, 8F, 6F, 5F}[com.realweather.ClientConfig.quality()]), 4, 399);
        float sx = SkyLight.sunX, sy = SkyLight.sunY, sz = SkyLight.sunZ;
        boolean reuse = groundId == t.id && groundTime == time && groundSegs == segs;
        if (!foam) {
            groundId = t.id;
            groundTime = time;
            groundSegs = segs;
        }
        for (int part = 0; part < 2; part++) {
            float[][] prof = part == 0 ? BODY : FACE;
            float[] lit = part == 0 ? BODY_LIGHT : FACE_LIGHT;
            float[] fo = part == 0 ? BODY_FOAM : FACE_FOAM;
            int m = prof.length;
            for (int i = 0; i <= segs; i++) {
                float l = l0 + (l1 - l0) * i / segs;
                float end = Mth.clamp((width - Math.abs(l)) / (width * 0.18F), 0F, 1F);
                float hl = h * (1F + 0.07F * Mth.sin(l * 0.045F + (t.seed & 255)) + 0.04F * Mth.sin(l * 0.11F + time * 0.03F))
                        * Math.max(0.02F, end * end * (3F - 2F * end));
                float wob = 0.15F * h * Mth.sin(l * 0.02F + (t.seed & 127) * 0.1F + time * 0.01F);
                // Over flooded land the back of the wave runs down onto the water standing behind it, not down to the sea.
                float sBack = front + (BODY[0][0] - TOE_DS) * hl;
                float rest = part == 0 && land(level, t, Mth.floor(sBack / 16F), Mth.floor(l / 16F), 16F) ? t.floodDepth(sBack) : 0F;
                for (int k = 0; k < m; k++) {
                    float s = front + (prof[k][0] - TOE_DS) * hl + 1F + wob;
                    int idx = i * 12 + k;
                    double wx = t.ox + t.dirX * s - t.dirZ * l, wz = t.oz + t.dirZ * s + t.dirX * l;
                    PX[idx] = (float) (wx - cam.x);
                    PZ[idx] = (float) (wz - cam.z);
                    float back = rest > 0F && prof[k][0] < 0F ? Mth.clamp(-prof[k][0] / -BODY[0][0], 0F, 1F) : 0F;
                    // The wave rides on the ground under it: up the hills, down into the valleys (looked up once per frame).
                    int gi = part * 400 * 12 + idx;
                    if (!foam || !reuse) {
                        GROUND[gi] = t.ground(level, wx, wz);
                    }
                    PY[idx] = (float) (GROUND[gi] + prof[k][1] * hl + rest * back - cam.y);
                }
            }
            float vScroll = time * 0.02F;
            for (int i = 0; i < segs; i++) {
                for (int k = 0; k < m - 1; k++) {
                    // Normal of the profile segment (in the plane of travel), for the sunlight.
                    float ds = prof[k + 1][0] - prof[k][0], dy = prof[k + 1][1] - prof[k][1];
                    float nl = Mth.sqrt(ds * ds + dy * dy) + 1.0E-4F;
                    float ns = dy / nl, ny = -ds / nl;
                    if (part == 1) {
                        ns = -ns;
                        ny = -ny;
                    }
                    float nx = t.dirX * ns, nz = t.dirZ * ns;
                    float sun = Math.max(0F, nx * sx + ny * sy + nz * sz) * SkyLight.day;
                    int[] ks = {k, k, k + 1, k + 1};
                    int[] is = {i, i + 1, i + 1, i};
                    for (int c = 0; c < 4; c++) {
                        int kk = ks[c], ii = is[c];
                        int idx = ii * 12 + kk;
                        float x = PX[idx], y = PY[idx], z = PZ[idx];
                        float d = Mth.sqrt(x * x + z * z);
                        float haze = 1F - (float) Math.exp(-d / 420F);
                        float b = (lit[kk] + 0.25F * sun) * (foam ? 1.05F : 1F);
                        float a = foam ? fo[kk] : (part == 1 ? 0.82F : 0.93F);
                        float u = (l0 + (l1 - l0) * ii / segs) / 24F;
                        float v = (part * 0.5F + kk * 0.09F) * Math.max(1F, h / 10F) - vScroll * (part == 0 ? 1F : -0.6F);
                        vertex(buf, x, y, z, u, v, b * lr, b * lg, b * lb, a, fog, haze, d, reach);
                    }
                    any = true;
                }
            }
        }
        return any;
    }

    private static void vertex(BufferBuilder buf, float x, float y, float z, float u, float v, float r, float g, float b, float a, float[] fog,
                               float haze, float d, float reach) {
        float fade = 1F - Mth.clamp((d - reach * 0.8F) / (reach * 0.2F), 0F, 1F);
        r += (fog[0] - r) * haze * 0.85F;
        g += (fog[1] - g) * haze * 0.85F;
        b += (fog[2] - b) * haze * 0.85F;
        buf.addVertex(x, y, z).setUv(u, v).setColor(k(r), k(g), k(b), k(a * fade)).setLight(LightTexture.FULL_BRIGHT);
    }

    private static float k(float v) {
        return v < 0F ? 0F : v > 1F ? 1F : v;
    }
}
