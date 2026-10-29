package com.realweather.client.tornado;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.ClientConfig;
import com.realweather.RealWeather;
import com.realweather.client.ClientWeather;
import com.realweather.server.Tornado;
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
 * Draws each tornado: a rotating condensation funnel made of several translucent shells (a thin rope for F0/F1,
 * a cone for F2/F3, a wide wedge for F4/F5), the dark rotating wall cloud it hangs from, and for violent
 * tornadoes the small suction vortices that orbit inside the debris cloud. Detail follows the tornado graphics
 * setting (LOW, MEDIUM, HIGH, ULTRA).
 */
public final class TornadoRenderer {
    private static final ResourceLocation FUNNEL = RealWeather.id("textures/environment/tornado_funnel.png");
    private static final ResourceLocation CLOUD = RealWeather.id("textures/environment/tornado_cloud.png");
    private static final int LIGHT = LightTexture.FULL_SKY;

    private static final int[] SEGMENTS = {12, 20, 32, 48};
    private static final int[] SLICES = {10, 16, 28, 40};
    private static final int[] SHELLS = {1, 2, 3, 4};
    private static final int[] CLOUD_RINGS = {4, 6, 8, 12};
    private static final int[] CLOUD_LAYERS = {1, 1, 2, 3};
    private static final float[] MAX_DIST = {300F, 450F, 640F, 900F};
    private static final float[] SHELL_RADIUS = {1.0F, 0.8F, 1.22F, 0.62F};
    private static final float[] SHELL_ALPHA = {0.86F, 0.9F, 0.42F, 0.95F};
    private static final float[] SHELL_SPEED = {1.0F, 1.35F, 0.8F, 1.7F};
    private static final float[] SHELL_SHADE = {1.0F, 0.86F, 1.08F, 0.74F};

    private static BufferBuilder buffer;
    /** Tornadoes drawn in the last frame (diagnostics). */
    public static int lastDrawn;

    private TornadoRenderer() {}

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || ClientTornadoes.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!ClientWeather.active(level)) {
            return;
        }
        int q = ClientConfig.tornadoQuality();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
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
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        lastDrawn = 0;
        try {
            for (ClientTornadoes.T t : ClientTornadoes.all()) {
                double tx = t.x(pt);
                double tz = t.z(pt);
                double dist = Math.sqrt((tx - cam.x) * (tx - cam.x) + (tz - cam.z) * (tz - cam.z));
                if (dist > MAX_DIST[q] || t.strength <= 0.01F) {
                    continue;
                }
                // A tornado stands out against the rain as a dark column: let it show through the fog as a hazy silhouette.
                RenderSystem.setShaderFogStart(fogStart);
                RenderSystem.setShaderFogEnd(Math.max(fogEnd, Math.min((float) dist + 160F, fogEnd * 3F + 60F)));
                float time = t.age + pt;
                drawCloud(t, tx, tz, cam, time, pt, q);
                drawFunnel(t, tx, tz, cam, time, pt, q);
                lastDrawn++;
                if (q >= 2 && t.rating >= 4 && t.contact > 0.8F && t.strength > 0.6F) {
                    drawSubVortices(t, tx, tz, cam, time, pt, q);
                }
            }
        } finally {
            flush();
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            light.turnOffLightLayer();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void drawFunnel(ClientTornadoes.T t, double tx, double tz, Vec3 cam, float time, float pt, int q) {
        int n = SEGMENTS[q];
        int m = SLICES[q];
        int shells = SHELLS[q];
        float height = t.height();
        float gap = t.rating == 0 ? 0.3F : t.rating == 1 ? 0.12F : 0F;
        // While the funnel is still coming down, only the part below the cloud is condensed.
        float bottom = Math.max((1F - t.contact) * 0.85F, gap * t.contact);
        float spin = t.spin(pt);
        float rise = time * 0.02F * (1F + t.rating * 0.2F);
        begin(FUNNEL);
        for (int s = 0; s < shells; s++) {
            float rs = SHELL_RADIUS[s];
            float speed = SHELL_SPEED[s];
            float shade = SHELL_SHADE[s];
            float baseAlpha = SHELL_ALPHA[s] * (0.3F + 0.7F * t.strength);
            float vRepeat = height / 24F;
            for (int j = 0; j < m; j++) {
                float f0 = bottom + (1F - bottom) * j / m;
                float f1 = bottom + (1F - bottom) * (j + 1) / m;
                float r0 = t.radiusAt(f0) * rs * tip(f0, bottom);
                float r1 = t.radiusAt(f1) * rs * tip(f1, bottom);
                float cx0 = (float) (tx + t.offsetX(f0, time) - cam.x);
                float cz0 = (float) (tz + t.offsetZ(f0, time) - cam.z);
                float cx1 = (float) (tx + t.offsetX(f1, time) - cam.x);
                float cz1 = (float) (tz + t.offsetZ(f1, time) - cam.z);
                float y0 = (float) (t.groundY + f0 * height - cam.y);
                float y1 = (float) (t.groundY + f1 * height - cam.y);
                float a0 = baseAlpha * fade(f0, bottom);
                float a1 = baseAlpha * fade(f1, bottom);
                float[] c0 = color(f0, t, shade);
                float[] c1 = color(f1, t, shade);
                float v0 = f0 * vRepeat - rise * speed;
                float v1 = f1 * vRepeat - rise * speed;
                for (int i = 0; i < n; i++) {
                    float ang0 = (float) (i * Math.PI * 2 / n);
                    float ang1 = (float) ((i + 1) * Math.PI * 2 / n);
                    float u0 = i * 3F / n + spin * speed * 3F;
                    float u1 = (i + 1) * 3F / n + spin * speed * 3F;
                    float cos0 = Mth.cos(ang0), sin0 = Mth.sin(ang0), cos1 = Mth.cos(ang1), sin1 = Mth.sin(ang1);
                    vertex(cx0 + cos0 * r0, y0, cz0 + sin0 * r0, u0, v0, c0, a0);
                    vertex(cx0 + cos1 * r0, y0, cz0 + sin1 * r0, u1, v0, c0, a0);
                    vertex(cx1 + cos1 * r1, y1, cz1 + sin1 * r1, u1, v1, c1, a1);
                    vertex(cx1 + cos0 * r1, y1, cz1 + sin0 * r1, u0, v1, c1, a1);
                }
            }
        }
        flush();
    }

    /** Narrows the funnel to a point while it is still aloft. */
    private static float tip(float f, float bottom) {
        if (bottom <= 0.001F) {
            return 1F;
        }
        float t = Mth.clamp((f - bottom) / 0.15F, 0F, 1F);
        return 0.15F + 0.85F * t * t * (3F - 2F * t);
    }

    private static float fade(float f, float bottom) {
        float top = f > 0.86F ? (1F - f) / 0.14F : 1F;
        float low = bottom > 0.001F ? Mth.clamp((f - bottom) / 0.05F, 0F, 1F) : 1F;
        return Mth.clamp(top, 0F, 1F) * low;
    }

    /** Condensation grey, browner near the ground where it is full of dust and debris. */
    private static float[] color(float f, ClientTornadoes.T t, float shade) {
        float g = (0.4F + 0.14F * f) * shade;
        float dust = t.contact * Mth.clamp(1F - f / (0.18F + t.rating * 0.03F), 0F, 1F);
        float r = Mth.lerp(dust, g, 0.47F * shade);
        float gg = Mth.lerp(dust, g * 1.01F, 0.39F * shade);
        float b = Mth.lerp(dust, g * 1.05F, 0.3F * shade);
        return new float[]{r, gg, b};
    }

    private static void drawCloud(ClientTornadoes.T t, double tx, double tz, Vec3 cam, float time, float pt, int q) {
        int rings = CLOUD_RINGS[q];
        int segs = SEGMENTS[q];
        int layers = CLOUD_LAYERS[q];
        float rc = Math.max(2F, t.coreRadius());
        float spin = t.spin(pt);
        float cxw = (float) (tx + t.offsetX(1F, time));
        float czw = (float) (tz + t.offsetZ(1F, time));
        begin(CLOUD);
        for (int l = 0; l < layers; l++) {
            // The rotating wall cloud, with a lower, tighter and faster ring under it.
            float radius = (rc * 6F + 34F) * (1F - l * 0.28F);
            float y = (float) (t.cloudBase() - 2F - l * 5F - cam.y);
            float rot = spin * (0.9F + l * 0.6F) * (float) (Math.PI * 2) * 0.12F;
            float shade = 0.36F - l * 0.05F;
            float alpha = (0.82F - l * 0.15F) * (0.35F + 0.65F * t.strength);
            for (int k = 0; k < rings; k++) {
                float ra = radius * k / rings;
                float rb = radius * (k + 1) / rings;
                float aa = alpha * (1F - (ra / radius) * (ra / radius));
                float ab = alpha * (1F - (rb / radius) * (rb / radius));
                for (int i = 0; i < segs; i++) {
                    float a0 = (float) (i * Math.PI * 2 / segs);
                    float a1 = (float) ((i + 1) * Math.PI * 2 / segs);
                    cloudVertex(cxw, czw, cam, ra, a0, y, rot, shade, aa);
                    cloudVertex(cxw, czw, cam, ra, a1, y, rot, shade, aa);
                    cloudVertex(cxw, czw, cam, rb, a1, y, rot, shade, ab);
                    cloudVertex(cxw, czw, cam, rb, a0, y, rot, shade, ab);
                }
            }
        }
        flush();
    }

    private static void cloudVertex(float cx, float cz, Vec3 cam, float r, float a, float y, float rot, float shade, float alpha) {
        float x = cx + Mth.cos(a) * r;
        float z = cz + Mth.sin(a) * r;
        // Texture coordinates rotate with the cloud (counter-clockwise from above).
        float u = Mth.cos(a + rot) * r / 48F;
        float v = Mth.sin(a + rot) * r / 48F;
        buffer.addVertex((float) (x - cam.x), y, (float) (z - cam.z)).setUv(u, v).setColor(shade, shade, shade * 1.05F, alpha).setLight(LIGHT);
    }

    private static void drawSubVortices(ClientTornadoes.T t, double tx, double tz, Vec3 cam, float time, float pt, int q) {
        int count = q >= 3 ? 5 : 3;
        float rc = t.coreRadius();
        float spin = t.spin(pt);
        float height = t.height() * 0.22F;
        int n = 10;
        int m = 6;
        begin(FUNNEL);
        for (int k = 0; k < count; k++) {
            float orbit = (float) (-spin * Math.PI * 2 * 1.3F + k * Math.PI * 2 / count);
            float ox = (float) (tx + Mth.cos(orbit) * rc * 0.75F - cam.x);
            float oz = (float) (tz + Mth.sin(orbit) * rc * 0.75F - cam.z);
            float rs = rc * 0.13F;
            for (int j = 0; j < m; j++) {
                float f0 = (float) j / m;
                float f1 = (float) (j + 1) / m;
                float r0 = rs * (0.6F + f0 * 0.9F);
                float r1 = rs * (0.6F + f1 * 0.9F);
                float y0 = (float) (t.groundY + f0 * height - cam.y);
                float y1 = (float) (t.groundY + f1 * height - cam.y);
                float a0 = 0.5F * (1F - f0);
                float a1 = 0.5F * (1F - f1);
                float[] c = {0.4F, 0.34F, 0.27F};
                for (int i = 0; i < n; i++) {
                    float ang0 = (float) (i * Math.PI * 2 / n);
                    float ang1 = (float) ((i + 1) * Math.PI * 2 / n);
                    float u0 = (float) i / n + spin * 6F;
                    float u1 = (float) (i + 1) / n + spin * 6F;
                    vertex(ox + Mth.cos(ang0) * r0, y0, oz + Mth.sin(ang0) * r0, u0, f0 - time * 0.05F, c, a0);
                    vertex(ox + Mth.cos(ang1) * r0, y0, oz + Mth.sin(ang1) * r0, u1, f0 - time * 0.05F, c, a0);
                    vertex(ox + Mth.cos(ang1) * r1, y1, oz + Mth.sin(ang1) * r1, u1, f1 - time * 0.05F, c, a1);
                    vertex(ox + Mth.cos(ang0) * r1, y1, oz + Mth.sin(ang0) * r1, u0, f1 - time * 0.05F, c, a1);
                }
            }
        }
        flush();
    }

    private static void vertex(float x, float y, float z, float u, float v, float[] c, float a) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(c[0], c[1], c[2], a).setLight(LIGHT);
    }

    private static void begin(ResourceLocation texture) {
        flush();
        RenderSystem.setShaderTexture(0, texture);
        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
    }

    private static void flush() {
        if (buffer != null) {
            MeshData mesh = buffer.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            buffer = null;
        }
    }

    /** How much tornado dust surrounds a point (0..1): the debris cloud near the base. */
    public static float dustAt(double x, double y, double z) {
        float best = 0F;
        for (ClientTornadoes.T t : ClientTornadoes.all()) {
            float rc = Math.max(2F, t.coreRadius());
            double d = Math.sqrt((x - t.x) * (x - t.x) + (z - t.z) * (z - t.z));
            double above = y - t.groundY;
            if (above > 40 + t.rating * 10) {
                continue;
            }
            float f = (float) Mth.clamp(1.0 - (d - rc) / (rc * 3 + 15), 0.0, 1.0) * t.contact * (0.4F + 0.6F * t.strength)
                    * (0.5F + Tornado.RADIUS[t.rating] / 54F);
            best = Math.max(best, f);
        }
        return Math.min(1F, best);
    }
}
