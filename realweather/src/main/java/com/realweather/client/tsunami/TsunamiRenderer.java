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
        float reach = Math.min(420F, mc.options.getEffectiveRenderDistance() * 16F + 64F);
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

    private static final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap LAND = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
    private static long landClearedAt;

    /**
     * Whether the flood stands on land at a grid corner of the flood sheet (looked up once and remembered; the world changes
     * slowly). Out at sea, and where the client has no terrain, the water behind the wave is back at sea level.
     */
    private static boolean land(ClientLevel level, Tsunami t, int si, int li, float tile) {
        long now = level.getGameTime();
        if (now - landClearedAt > 100 || LAND.size() > 20000) {
            LAND.clear();
            landClearedAt = now;
        }
        long key = ((long) t.id << 48) | ((si & 0xFFFFFFL) << 24) | (li & 0xFFFFFFL);
        byte v = LAND.getOrDefault(key, (byte) -1);
        if (v < 0) {
            v = 0;
            float s = si * tile, l = li * tile;
            float[][] pts = {{0F, 0F}, {-0.3F, 0F}, {0.3F, 0F}, {0F, -0.3F}, {0F, 0.3F}};
            for (float[] p : pts) {
                float ss = s + p[0] * tile, ll = l + p[1] * tile;
                int x = Mth.floor(t.ox + t.dirX * ss - t.dirZ * ll);
                int z = Mth.floor(t.oz + t.dirZ * ss + t.dirX * ll);
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                net.minecraft.core.BlockPos below = new net.minecraft.core.BlockPos(x, top - 1, z);
                if (top - 1 > t.baseY + 0.5F || level.getFluidState(below).isEmpty()) {
                    v = 1;
                    break;
                }
            }
            LAND.put(key, v);
        }
        return v == 1;
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
        float flood = t.floodY();
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
            float flow = t.stage == Tsunami.RUNNING ? time * t.speed() * 0.45F : -time * 0.05F;
            float alpha = (foam ? 0.3F : 0.86F) * Math.min(1F, (1F - t.drained) * 3F);
            float bright = foam ? 0.9F : 0.62F;
            float sea = t.baseY - 0.35F;
            // A sheet on a fixed grid along the wave: level over the flooded land, sloping down into the sea at the shore.
            int si0 = Mth.floor(s0 / tile), si1 = Mth.ceil(s1 / tile);
            int li0 = Mth.floor(fl0 / tile), li1 = Mth.ceil(fl1 / tile);
            float[] cs = new float[4], cl = new float[4];
            boolean[] cland = new boolean[4];
            for (int si = si0; si < si1; si++) {
                for (int li = li0; li < li1; li++) {
                    cland[0] = land(level, t, si, li, tile);
                    cland[1] = land(level, t, si + 1, li, tile);
                    cland[2] = land(level, t, si + 1, li + 1, tile);
                    cland[3] = land(level, t, si, li + 1, tile);
                    if (!cland[0] && !cland[1] && !cland[2] && !cland[3]) {
                        continue;
                    }
                    float sa = Math.max(s0, si * tile), sb = Math.min(s1, (si + 1) * tile);
                    float la = Math.max(fl0, li * tile), lb2 = Math.min(fl1, (li + 1) * tile);
                    cs[0] = sa; cl[0] = la;
                    cs[1] = sb; cl[1] = la;
                    cs[2] = sb; cl[2] = lb2;
                    cs[3] = sa; cl[3] = lb2;
                    for (int c = 0; c < 4; c++) {
                        double x = t.ox + t.dirX * cs[c] - t.dirZ * cl[c] - cam.x;
                        double z = t.oz + t.dirZ * cs[c] + t.dirX * cl[c] - cam.z;
                        float y = (float) ((cland[c] ? flood : sea) - cam.y);
                        float d = (float) Math.sqrt(x * x + z * z);
                        float haze = 1F - (float) Math.exp(-d / 420F);
                        vertex(buf, (float) x, y, (float) z, cl[c] / 32F, (cs[c] - flow) / 32F, bright * lr, bright * lg, bright * lb,
                                cland[c] ? alpha : alpha * 0.5F, fog, haze, d, floodReach);
                    }
                    any = true;
                }
            }
        }
        if (h < 0.3F || t.stage != Tsunami.RUNNING || Math.abs(sc - front) > reach + 8 * h) {
            return any;
        }
        // The wave itself, segment by segment along its length.
        int segs = Mth.clamp((int) ((l1 - l0) / 5F), 4, 399);
        float sx = SkyLight.sunX, sy = SkyLight.sunY, sz = SkyLight.sunZ;
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
                for (int k = 0; k < m; k++) {
                    float s = front + (prof[k][0] - TOE_DS) * hl + 1F + wob;
                    int idx = i * 12 + k;
                    PX[idx] = (float) (t.ox + t.dirX * s - t.dirZ * l - cam.x);
                    PZ[idx] = (float) (t.oz + t.dirZ * s + t.dirX * l - cam.z);
                    PY[idx] = (float) (t.baseY + prof[k][1] * hl - cam.y);
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
