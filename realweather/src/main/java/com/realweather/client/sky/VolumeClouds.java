package com.realweather.client.sky;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.RealWeather;
import com.realweather.client.ClientWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * ULTRA 3D clouds: real volumetric clouds, ray-marched through a three-dimensional cloud layer in a shader
 * (drawn at half resolution and laid over the scene). Fair weather gives towering cumulus with flat bases and
 * sunlit, billowing tops and dark undersides; rain thickens them into a grey deck. If the shaders can't be compiled
 * on this machine the ordinary ULTRA clouds are used instead.
 */
public final class VolumeClouds {
    private static ShaderInstance march, composite, glow;
    private static TextureTarget target;
    private static boolean broken;
    /** Set by the sky pass when this frame should have volumetric clouds. */
    static boolean wanted;
    private static float coverage = 0.5F, darkness, base = 165F, top = 300F, farFade = 2600F;
    private static final float[] FLASH_POS = new float[16], FLASH_POWER = new float[4], FLASH_COLOR = new float[12];

    private VolumeClouds() {}

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), RealWeather.id("volumetric_clouds"), DefaultVertexFormat.POSITION),
                    s -> march = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), RealWeather.id("cloud_composite"), DefaultVertexFormat.POSITION),
                    s -> composite = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), RealWeather.id("sky_glow"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    s -> glow = s);
        } catch (Exception | LinkageError e) {
            march = null;
            composite = null;
            RealWeather.LOG.warn("ULTRA 3D clouds unavailable (shaders did not load); using the ULTRA clouds instead", e);
        }
    }

    /** Sets the shader for soft sky glows and thin sheets (no alpha cut-off), and smooth filtering on the texture. */
    static void useGlow(net.minecraft.resources.ResourceLocation texture) {
        if (glow != null) {
            RenderSystem.setShader(() -> glow);
        } else {
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        }
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
        RenderSystem.setShaderTexture(0, texture);
    }

    /** The soft glow shader on a texture, leaving the texture's own filtering as it is (for vanilla's moon). */
    static void useGlowKeepFilter(net.minecraft.resources.ResourceLocation texture) {
        if (glow != null) {
            RenderSystem.setShader(() -> glow);
        } else {
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        }
        RenderSystem.setShaderTexture(0, texture);
    }

    public static boolean available() {
        return march != null && composite != null && !broken;
    }

    /** Weather for this frame's clouds: how much of the sky they cover, how grey they are, their visibility. */
    static void prepare(ClientLevel level, float cover, float vis) {
        long dayIndex = Math.floorDiv(level.getDayTime(), 24000L);
        float fair = 0.44F + 0.16F * (CloudField.hash(dayIndex, 11) * 0.5F + 0.5F);
        coverage = Mth.lerp(Mth.clamp(cover * 1.25F, 0F, 1F), fair, 0.97F);
        darkness = Math.max(Mth.clamp((cover - 0.2F) / 0.6F, 0F, 1F), CloudTypes.nimbostratus * 0.9F);
        // The day's cumulus amount: a stratocumulus or stratus day has fewer separate cells.
        coverage *= 0.55F + 0.45F * Mth.clamp(CloudTypes.cumulus + CloudTypes.stratocumulus * 0.6F, 0F, 1F);
        base = Mth.lerp(darkness, 168F, 148F);
        top = Mth.lerp(darkness, 330F, 265F);
        farFade = Math.max(400F, vis * 1.6F);
        wanted = true;
    }

    /** Draws the clouds over the scene (after the terrain and particles, before the storm clouds). */
    static void render(RenderLevelStageEvent event) {
        wanted = false;
        if (!available()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            draw(event, mc);
        } catch (RuntimeException e) {
            broken = true;
            RealWeather.LOG.error("ULTRA 3D clouds disabled after an error", e);
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void draw(RenderLevelStageEvent event, Minecraft mc) {
        var main = mc.getMainRenderTarget();
        // LOW_3D and MEDIUM_3D march at a third of the resolution, HIGH_3D and ULTRA_3D at half.
        int q = com.realweather.ClientConfig.quality();
        int div = q <= 1 ? 3 : 2;
        int w = Math.max(1, main.width / div), h = Math.max(1, main.height / div);
        if (target == null) {
            target = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        } else if (target.width != w || target.height != h) {
            target.resize(w, h, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f inv = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ClientLevel level = mc.level;
        Vec3 sky = level == null ? new Vec3(0.5, 0.6, 0.8) : level.getSkyColor(cam, pt);
        float[] fog = RenderSystem.getShaderFogColor();
        float day = SkyLight.day;
        // The low sun still lights the clouds, in its warm colour (orange and pink undersides at sunset).
        float sunK = 0.3F + 0.7F * day;

        march.safeGetUniform("InvViewProj").set(inv);
        march.safeGetUniform("CameraPos").set((float) cam.x, (float) cam.y, (float) cam.z);
        march.safeGetUniform("Drift").set((float) -CloudField.driftX, (float) -CloudField.driftZ);
        float sy = Math.max(SkyLight.sunY, -0.2F);
        float len = Mth.sqrt(SkyLight.sunX * SkyLight.sunX + sy * sy + SkyLight.sunZ * SkyLight.sunZ) + 1.0E-4F;
        march.safeGetUniform("SunDir").set(SkyLight.sunX / len, sy / len, SkyLight.sunZ / len);
        // Near sunrise and sunset the low sun lights the clouds orange and pink from below.
        float warm = SkyLight.sunY > -0.12F ? Mth.clamp(1F - Math.abs(SkyLight.sunY) * 3.5F, 0F, 1F) * 0.8F : 0F;
        float sr = Mth.lerp(warm, SkyLight.r * sunK * 1.05F, 1.45F), sg = Mth.lerp(warm, SkyLight.g * sunK, 0.78F),
                sb = Mth.lerp(warm, SkyLight.b * sunK * 0.95F, 0.5F);
        march.safeGetUniform("SunColor").set(sr, sg, sb);
        float amb = (0.25F + 0.75F * Mth.clamp(SkyLight.r, 0F, 1F)) * (1F - 0.85F * ClientEclipse.dark);
        march.safeGetUniform("SkyColor").set((float) (sky.x * 0.45 + fog[0] * 0.55) * amb, (float) (sky.y * 0.45 + fog[1] * 0.55) * amb,
                (float) (sky.z * 0.45 + fog[2] * 0.55) * amb);
        march.safeGetUniform("HazeColor").set(fog[0], fog[1], fog[2]);
        march.safeGetUniform("Coverage").set(coverage);
        march.safeGetUniform("Darkness").set(darkness * (0.6F + 0.4F * ClientWeather.thunder));
        march.safeGetUniform("CloudBase").set(base);
        march.safeGetUniform("CloudTop").set(top);
        march.safeGetUniform("CloudTime").set((level == null ? 0 : level.getGameTime() % 240000L) + pt);
        march.safeGetUniform("FarFade").set(farFade);
        int[] steps = {20, 28, 34, 42};
        march.safeGetUniform("Steps").set((float) Math.round(steps[q] + steps[q] * 0.35F * darkness));
        march.safeGetUniform("LightSteps").set(q == 0 ? 1F : q == 3 ? 3F : 2F);
        march.safeGetUniform("Detail").set(q == 0 ? 0F : 1F);
        // Today's cloud types (varied again from place to place in the shader).
        march.safeGetUniform("TypesLow").set(CloudTypes.cumulus, CloudTypes.stratocumulus, CloudTypes.stratus, CloudTypes.cumulonimbus);
        march.safeGetUniform("TypesHigh").set(CloudTypes.altocumulus, CloudTypes.altostratus, CloudTypes.cirrus, CloudTypes.cirrocumulus);
        march.safeGetUniform("TypesVeil").set(CloudTypes.cirrostratus, 0F);
        march.safeGetUniform("DayOffset").set(CloudTypes.offsetX, CloudTypes.offsetZ);
        // Lightning inside the clouds.
        CloudFlashes.strongest(FLASH_POS, FLASH_POWER, FLASH_COLOR);
        for (int i = 0; i < 4; i++) {
            march.safeGetUniform("Flash" + i).set(FLASH_POS[i * 4], FLASH_POS[i * 4 + 1], FLASH_POS[i * 4 + 2], Math.max(1F, FLASH_POS[i * 4 + 3]));
            // Each flash lights the cloud in the colour of its kind of lightning.
            march.safeGetUniform("FlashColor" + i).set(FLASH_COLOR[i * 3], FLASH_COLOR[i * 3 + 1], FLASH_COLOR[i * 3 + 2]);
        }
        march.safeGetUniform("FlashPower").set(FLASH_POWER[0], FLASH_POWER[1], FLASH_POWER[2], FLASH_POWER[3]);
        // Rain storms (not the ones with a tornado: those keep their own look), nearest first.
        java.util.List<com.realweather.client.ClientStorms.Cell> cells = new java.util.ArrayList<>();
        for (com.realweather.client.ClientStorms.Cell c : com.realweather.client.ClientStorms.cells()) {
            if (c.cstrength() > 0.02F && !StormClouds.hostsTornado(c)) {
                cells.add(c);
            }
        }
        cells.sort(java.util.Comparator.comparingDouble(c -> (c.x - cam.x) * (c.x - cam.x) + (c.z - cam.z) * (c.z - cam.z)));
        for (int i = 0; i < 8; i++) {
            if (i < cells.size()) {
                com.realweather.client.ClientStorms.Cell c = cells.get(i);
                float s = Mth.clamp(c.cstrength() * (0.55F + 0.45F * c.cintensity()) * 1.2F, 0F, 1F);
                march.safeGetUniform("Storm" + i).set((float) (c.x + c.vx * pt - cam.x), (float) (c.z + c.vz * pt - cam.z), Math.max(40F, c.radius), s);
            } else {
                march.safeGetUniform("Storm" + i).set(0F, 0F, 1F, 0F);
            }
        }
        march.safeGetUniform("StormBase").set(level == null ? 150F : StormClouds.deckY(level) - 4F);

        // 1: the clouds, at half resolution, stopped by the scene's depth.
        target.setClearColor(0F, 0F, 0F, 0F);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> march);
        RenderSystem.setShaderTexture(0, main.getDepthTextureId());
        quad();

        // 2: laid over the scene.
        main.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(() -> composite);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        quad();
    }

    private static void quad() {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.addVertex(-1F, -1F, 0F);
        b.addVertex(1F, -1F, 0F);
        b.addVertex(1F, 1F, 0F);
        b.addVertex(-1F, 1F, 0F);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
