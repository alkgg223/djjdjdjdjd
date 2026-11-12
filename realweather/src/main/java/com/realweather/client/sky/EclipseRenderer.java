package com.realweather.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.realweather.RealWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * The solar eclipse in the sky: the day darkens towards a deep twilight as the moon covers the sun, the stars come out,
 * the horizon glows all round like a sunset; the sun shrinks to a crescent; just before and after totality the last
 * bead of sunlight flares into a 'diamond ring'; and in totality the black disc of the moon hangs in the pearly corona.
 * It also works out where the sun is on the screen, for the view through eclipse glasses.
 */
public final class EclipseRenderer {
    private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation DISC = RealWeather.id("textures/environment/disc.png");
    private static final ResourceLocation CORONA = RealWeather.id("textures/environment/corona.png");
    private static final ResourceLocation FLARE = RealWeather.id("textures/environment/flare.png");
    /** Sun disc radius as vanilla draws it (the middle half of a 60-wide quad at 100 blocks). */
    static final float SUN_R = 15F;
    private static float[] stars;
    private static boolean failed;
    /** Where the sun and the moon are on the screen this frame (GUI pixels), and their radius; NaN when off screen. */
    public static float screenSunX = Float.NaN, screenSunY, screenR, screenMoonX, screenMoonY;

    private EclipseRenderer() {}

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (failed || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ClientEclipse.update(level, pt);
        screenSunX = Float.NaN;
        if (level == null) {
            return;
        }
        // Where the sun is on the screen (for the view through eclipse glasses), eclipse or not.
        project(event, level, pt);
        if (ClientEclipse.current == null || ClientEclipse.coverage <= 0F || ClientEclipse.sunUp <= 0F) {
            return;
        }
        try {
            render(event, mc, level, pt);
        } catch (RuntimeException e) {
            failed = true;
            RealWeather.LOG.error("Eclipse rendering disabled after an error", e);
        }
    }

    private static void render(RenderLevelStageEvent event, Minecraft mc, ClientLevel level, float pt) {
        float dark = ClientEclipse.dark, cov = ClientEclipse.coverage, up = ClientEclipse.sunUp;
        float rain = level.getRainLevel(pt);
        // Thick cloud hides the sun itself; the darkening comes through all the same.
        float clear = 1F - rain * 0.9F;
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        try {
            Vec3 sky = level.getSkyColor(event.getCamera().getPosition(), pt);
            float nr = 0.012F, ng = 0.02F, nb = 0.055F;
            // 1: the whole sky dims towards a deep blue twilight.
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            box(b, 60F, nr, ng, nb, dark * 0.93F);
            // A sunset glow all round the horizon in totality (sunlight from beyond the shadow).
            float glow = Mth.clamp((cov - 0.96F) / 0.04F, 0F, 1F) * 0.6F * up;
            if (glow > 0.01F) {
                ring(b, 59F, glow);
            }
            draw(b);

            Matrix4f sunFrame = new Matrix4f(event.getModelViewMatrix())
                    .rotate(Axis.YP.rotationDegrees(-90F)).rotate(Axis.XP.rotationDegrees(level.getTimeOfDay(pt) * 360F));
            mv.set(sunFrame);
            RenderSystem.applyModelViewMatrix();
            // 2: cover the vanilla sun (it can't be hidden behind the moon) with the colour of the darkened sky.
            float cr = (float) Mth.lerp(dark * 0.93F, sky.x, nr), cg = (float) Mth.lerp(dark * 0.93F, sky.y, ng), cb = (float) Mth.lerp(dark * 0.93F, sky.z, nb);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, DISC);
            b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            quadTex(b, 0F, 100F, 0F, 33F, cr, cg, cb, 1F);
            draw(b);

            // 3: the moon, as depth only: the sun, its glow, the corona and the stars behind it are hidden there.
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, DISC);
            LightTexture light = mc.gameRenderer.lightTexture();
            light.turnOnLightLayer();
            b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            float k = 0.9F;
            quadParticle(b, ClientEclipse.moonX * SUN_R * k, 100F * k, ClientEclipse.moonZ * SUN_R * k, SUN_R * com.realweather.server.Eclipse.MOON * k,
                    1F, 1F, 1F, 1F);
            draw(b);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(false);

            // 4: stars, the remaining sun, the corona (all additive, behind the moon).
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            float starA = Mth.clamp((dark - 0.4F) / 0.5F, 0F, 1F) * up * clear;
            if (starA > 0.01F) {
                mv.set(event.getModelViewMatrix());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                stars(b, starA);
                draw(b);
                mv.set(sunFrame);
                RenderSystem.applyModelViewMatrix();
            }
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, SUN);
            b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float sunA = clear * (cov > 0.9995F ? 0F : 1F);
            quadTex(b, 0F, 100F, 0F, 30F, 1F, 0.98F, 0.93F, sunA);
            draw(b);
            if (ClientEclipse.corona > 0.01F) {
                RenderSystem.setShaderTexture(0, CORONA);
                b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                float cs = SUN_R * com.realweather.server.Eclipse.MOON / 0.2F;
                quadTex(b, ClientEclipse.moonX * SUN_R * 0.3F, 100F, ClientEclipse.moonZ * SUN_R * 0.3F, cs, 0.95F, 0.97F, 1F,
                        ClientEclipse.corona * clear);
                draw(b);
            }
            // 5: the diamond ring: the last bead of sun at the edge, flaring over the moon.
            if (ClientEclipse.diamond > 0.01F) {
                RenderSystem.disableDepthTest();
                float mx = ClientEclipse.moonX, mz = ClientEclipse.moonZ;
                float len = Mth.sqrt(mx * mx + mz * mz);
                float dx = len > 1.0E-3F ? -mx / len : 1F, dz = len > 1.0E-3F ? -mz / len : 0F;
                RenderSystem.setShaderTexture(0, FLARE);
                b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                float fa = ClientEclipse.diamond * clear;
                quadTex(b, dx * SUN_R, 100F, dz * SUN_R, 58F, 1F, 1F, 0.97F, fa);
                quadTex(b, dx * SUN_R, 100F, dz * SUN_R, 20F, 1F, 1F, 1F, fa);
                draw(b);
            }
            light.turnOffLightLayer();
        } finally {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** Screen position of the sun and the moon (for the glasses). */
    private static void project(RenderLevelStageEvent event, ClientLevel level, float pt) {
        float a = level.getTimeOfDay(pt) * Mth.TWO_PI;
        float sx = -Mth.sin(a), sy = Mth.cos(a);
        // Axes of the sun's disc: x -> world +z, z -> world (-cos a, -sin a, 0).
        float[] sun = toScreen(event, sx * 100F, sy * 100F, 0F);
        if (sun == null) {
            return;
        }
        float[] edge = toScreen(event, sx * 100F, sy * 100F, SUN_R);
        boolean eclipse = ClientEclipse.current != null && ClientEclipse.coverage > 0F;
        float mx = eclipse ? ClientEclipse.moonX * SUN_R : 0F, mz = eclipse ? ClientEclipse.moonZ * SUN_R : 0F;
        float[] moon = toScreen(event, sx * 100F - Mth.cos(a) * mz, sy * 100F - Mth.sin(a) * mz, mx);
        if (edge == null || moon == null) {
            return;
        }
        screenSunX = sun[0];
        screenSunY = sun[1];
        screenR = (float) Math.hypot(edge[0] - sun[0], edge[1] - sun[1]);
        screenMoonX = moon[0];
        screenMoonY = moon[1];
    }

    private static float[] toScreen(RenderLevelStageEvent event, float x, float y, float z) {
        Vector4f v = new Vector4f(x, y, z, 1F);
        event.getModelViewMatrix().transform(v);
        event.getProjectionMatrix().transform(v);
        if (v.w <= 0.01F) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        return new float[]{(v.x / v.w * 0.5F + 0.5F) * w, (1F - (v.y / v.w * 0.5F + 0.5F)) * h};
    }

    // ------------------------------------------------------------------------------------ geometry
    private static void draw(BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /** A box round the camera (the whole sky). */
    private static void box(BufferBuilder b, float s, float r, float g, float bl, float a) {
        float[][] f = {
                {-s, -s, -s, s, -s, -s, s, s, -s, -s, s, -s}, {-s, -s, s, -s, s, s, s, s, s, s, -s, s},
                {-s, -s, -s, -s, s, -s, -s, s, s, -s, -s, s}, {s, -s, -s, s, -s, s, s, s, s, s, s, -s},
                {-s, s, -s, s, s, -s, s, s, s, -s, s, s}, {-s, -s, -s, -s, -s, s, s, -s, s, s, -s, -s}};
        for (float[] q : f) {
            for (int i = 0; i < 4; i++) {
                b.addVertex(q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).setColor(r, g, bl, a);
            }
        }
    }

    /** A band round the horizon, orange at the bottom fading out upwards. */
    private static void ring(BufferBuilder b, float rad, float a) {
        int n = 48;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float x0 = Mth.cos(a0) * rad, z0 = Mth.sin(a0) * rad, x1 = Mth.cos(a1) * rad, z1 = Mth.sin(a1) * rad;
            b.addVertex(x0, -6F, z0).setColor(0.95F, 0.52F, 0.28F, a);
            b.addVertex(x1, -6F, z1).setColor(0.95F, 0.52F, 0.28F, a);
            b.addVertex(x1, 14F, z1).setColor(0.55F, 0.35F, 0.45F, 0F);
            b.addVertex(x0, 14F, z0).setColor(0.55F, 0.35F, 0.45F, 0F);
        }
    }

    private static void quadTex(BufferBuilder b, float x, float y, float z, float s, float r, float g, float bl, float a) {
        b.addVertex(x - s, y, z - s).setUv(0F, 0F).setColor(r, g, bl, a);
        b.addVertex(x + s, y, z - s).setUv(1F, 0F).setColor(r, g, bl, a);
        b.addVertex(x + s, y, z + s).setUv(1F, 1F).setColor(r, g, bl, a);
        b.addVertex(x - s, y, z + s).setUv(0F, 1F).setColor(r, g, bl, a);
    }

    private static void quadParticle(BufferBuilder b, float x, float y, float z, float s, float r, float g, float bl, float a) {
        int full = LightTexture.FULL_BRIGHT;
        b.addVertex(x - s, y, z - s).setUv(0F, 0F).setColor(r, g, bl, a).setLight(full);
        b.addVertex(x + s, y, z - s).setUv(1F, 0F).setColor(r, g, bl, a).setLight(full);
        b.addVertex(x + s, y, z + s).setUv(1F, 1F).setColor(r, g, bl, a).setLight(full);
        b.addVertex(x - s, y, z + s).setUv(0F, 1F).setColor(r, g, bl, a).setLight(full);
    }

    /** Stars: small points all over the sky (a fixed pattern), brighter ones fewer. */
    private static void stars(BufferBuilder b, float a) {
        if (stars == null) {
            RandomSource r = RandomSource.create(10842L);
            stars = new float[1600 * 4];
            for (int i = 0; i < 1600; i++) {
                float x = r.nextFloat() * 2F - 1F, y = r.nextFloat() * 2F - 1F, z = r.nextFloat() * 2F - 1F;
                float len = Mth.sqrt(x * x + y * y + z * z);
                if (len < 0.05F || len > 1F) {
                    i--;
                    continue;
                }
                stars[i * 4] = x / len;
                stars[i * 4 + 1] = y / len;
                stars[i * 4 + 2] = z / len;
                float m = r.nextFloat();
                stars[i * 4 + 3] = m * m * m;
            }
        }
        for (int i = 0; i < 1600; i++) {
            float x = stars[i * 4] * 95F, y = stars[i * 4 + 1] * 95F, z = stars[i * 4 + 2] * 95F, m = stars[i * 4 + 3];
            if (y < -10F) {
                continue;
            }
            float s = 0.12F + 0.22F * m;
            // Two vectors across the line of sight.
            float ux, uy, uz;
            if (Math.abs(stars[i * 4 + 1]) < 0.9F) {
                ux = -stars[i * 4 + 2];
                uy = 0F;
                uz = stars[i * 4];
            } else {
                ux = 1F;
                uy = 0F;
                uz = 0F;
            }
            float ul = Mth.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= ul;
            uz /= ul;
            float vx = stars[i * 4 + 1] * uz - stars[i * 4 + 2] * uy, vy = stars[i * 4 + 2] * ux - stars[i * 4] * uz, vz = stars[i * 4] * uy - stars[i * 4 + 1] * ux;
            float al = a * (0.35F + 0.65F * m);
            b.addVertex(x - ux * s - vx * s, y - uy * s - vy * s, z - uz * s - vz * s).setColor(1F, 1F, 1F, al);
            b.addVertex(x + ux * s - vx * s, y + uy * s - vy * s, z + uz * s - vz * s).setColor(1F, 1F, 1F, al);
            b.addVertex(x + ux * s + vx * s, y + uy * s + vy * s, z + uz * s + vz * s).setColor(1F, 1F, 1F, al);
            b.addVertex(x - ux * s + vx * s, y - uy * s + vy * s, z - uz * s + vz * s).setColor(1F, 1F, 1F, al);
        }
    }
}
