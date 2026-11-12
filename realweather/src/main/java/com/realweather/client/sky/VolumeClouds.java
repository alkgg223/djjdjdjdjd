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
    private static ShaderInstance march, composite;
    private static TextureTarget target;
    private static boolean broken;
    /** Set by the sky pass when this frame should have volumetric clouds. */
    static boolean wanted;
    private static float coverage = 0.5F, darkness, base = 165F, top = 300F, farFade = 2600F;

    private VolumeClouds() {}

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), RealWeather.id("volumetric_clouds"), DefaultVertexFormat.POSITION),
                    s -> march = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), RealWeather.id("cloud_composite"), DefaultVertexFormat.POSITION),
                    s -> composite = s);
        } catch (Exception | LinkageError e) {
            march = null;
            composite = null;
            RealWeather.LOG.warn("ULTRA 3D clouds unavailable (shaders did not load); using the ULTRA clouds instead", e);
        }
    }

    public static boolean available() {
        return march != null && composite != null && !broken;
    }

    /** Weather for this frame's clouds: how much of the sky they cover, how grey they are, their visibility. */
    static void prepare(ClientLevel level, float cover, float vis) {
        long dayIndex = Math.floorDiv(level.getDayTime(), 24000L);
        float fair = 0.44F + 0.16F * (CloudField.hash(dayIndex, 11) * 0.5F + 0.5F);
        coverage = Mth.lerp(Mth.clamp(cover * 1.25F, 0F, 1F), fair, 0.97F);
        darkness = Mth.clamp((cover - 0.2F) / 0.6F, 0F, 1F);
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
        int w = Math.max(1, main.width / 2), h = Math.max(1, main.height / 2);
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
        float amb = 0.25F + 0.75F * Mth.clamp(SkyLight.r, 0F, 1F);
        march.safeGetUniform("SkyColor").set((float) (sky.x * 0.45 + fog[0] * 0.55) * amb, (float) (sky.y * 0.45 + fog[1] * 0.55) * amb,
                (float) (sky.z * 0.45 + fog[2] * 0.55) * amb);
        march.safeGetUniform("HazeColor").set(fog[0], fog[1], fog[2]);
        march.safeGetUniform("Coverage").set(coverage);
        march.safeGetUniform("Darkness").set(darkness * (0.6F + 0.4F * ClientWeather.thunder));
        march.safeGetUniform("CloudBase").set(base);
        march.safeGetUniform("CloudTop").set(top);
        march.safeGetUniform("CloudTime").set((level == null ? 0 : level.getGameTime() % 240000L) + pt);
        march.safeGetUniform("FarFade").set(farFade);
        march.safeGetUniform("Steps").set((float) Math.round(40 + 16 * darkness));

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
