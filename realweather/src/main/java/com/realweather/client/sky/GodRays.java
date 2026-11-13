package com.realweather.client.sky;

import com.mojang.blaze3d.pipeline.RenderTarget;
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
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Volumetric god rays: when the sun is up, shafts of sunlight fan out from it through the gaps in the clouds, the
 * treetops and the hills (gathered on the screen from the finished picture and its depth, then added on top).
 */
public final class GodRays {
    private static ShaderInstance shader;
    private static TextureTarget copy;
    private static boolean broken;

    private GodRays() {}

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), RealWeather.id("god_rays"), DefaultVertexFormat.POSITION_TEX),
                    s -> shader = s);
        } catch (Exception | LinkageError e) {
            shader = null;
            RealWeather.LOG.warn("God rays unavailable (shader did not load)", e);
        }
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (broken || shader == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!ClientWeather.active(level) || event.getCamera().getFluidInCamera() != FogType.NONE
                || com.realweather.client.space.Telescope.active() || event.getCamera().getPosition().y > 600) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double th = level.getTimeOfDay(pt) * Math.PI * 2;
        float sunY = (float) Math.cos(th);
        float day = Mth.clamp((sunY + 0.05F) / 0.3F, 0F, 1F);
        float strength = day * (1F - 0.75F * level.getRainLevel(pt)) * (1F - ClientEclipse.dark) * 0.55F;
        if (strength < 0.02F) {
            return;
        }
        // Where the sun is on the screen.
        Vector4f v = new Vector4f((float) -Math.sin(th) * 100F, (float) Math.cos(th) * 100F, 0F, 1F);
        event.getModelViewMatrix().transform(v);
        event.getProjectionMatrix().transform(v);
        if (v.w <= 0) {
            return;
        }
        float sx = (v.x / v.w) * 0.5F + 0.5F, sy = (v.y / v.w) * 0.5F + 0.5F;
        float off = Math.max(Math.max(-sx, sx - 1F), Math.max(-sy, sy - 1F));
        strength *= 1F - Mth.clamp(off / 0.5F, 0F, 1F);
        if (strength < 0.02F) {
            return;
        }
        try {
            draw(mc, sx, sy, strength, sunY);
        } catch (RuntimeException e) {
            broken = true;
            RealWeather.LOG.error("God rays disabled after an error", e);
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
    }

    private static void draw(Minecraft mc, float sx, float sy, float strength, float sunY) {
        RenderTarget main = mc.getMainRenderTarget();
        if (copy == null) {
            copy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (copy.width != main.width || copy.height != main.height) {
            copy.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        // A copy of the picture and its depth to read from.
        copy.copyDepthFrom(main);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
        float warm = Mth.clamp(1F - sunY * 2.2F, 0F, 1F);
        shader.safeGetUniform("SunPos").set(sx, sy);
        shader.safeGetUniform("Strength").set(strength);
        shader.safeGetUniform("SunColor").set(1F, Mth.lerp(warm, 0.93F, 0.66F), Mth.lerp(warm, 0.78F, 0.38F));
        shader.safeGetUniform("Aspect").set((float) main.width / Math.max(1, main.height));
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.setShaderTexture(1, copy.getDepthTextureId());
        RenderSystem.setShader(() -> shader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(-1F, -1F, 0F).setUv(0F, 0F);
        b.addVertex(1F, -1F, 0F).setUv(1F, 0F);
        b.addVertex(1F, 1F, 0F).setUv(1F, 1F);
        b.addVertex(-1F, 1F, 0F).setUv(0F, 1F);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
