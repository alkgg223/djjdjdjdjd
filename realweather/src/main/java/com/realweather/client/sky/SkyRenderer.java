package com.realweather.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.realweather.ClientConfig;
import com.realweather.RealWeather;
import com.realweather.client.ClientStorms;
import com.realweather.client.ClientWeather;
import com.realweather.client.tornado.TornadoRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

/**
 * Draws the sky sprites: fair-weather clouds, storm clouds and tornadoes. They are gathered once per frame right
 * after the sky; everything beyond the loaded terrain is drawn then (so hills cover it), and the rest is drawn
 * after the particles, depth-tested against the world, before the rain.
 */
public final class SkyRenderer {
    private static boolean failed;
    private static boolean pending;
    private static float splitDist;
    private static final float[] FOG = new float[3];

    private SkyRenderer() {}

    public static boolean failed() {
        return failed;
    }

    public static void tick() {
        CloudField.tick();
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (failed) {
            return;
        }
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_SKY && stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        try {
            if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
                pending = false;
                if (gather(event)) {
                    pending = true;
                    draw(event, true);
                }
            } else if (pending) {
                pending = false;
                draw(event, false);
            }
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            RealWeather.LOG.error("Realistic clouds disabled after an error", e);
        }
    }

    private static boolean gather(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!ClientWeather.active(level)) {
            return false;
        }
        Camera camera = event.getCamera();
        if (camera.getFluidInCamera() != FogType.NONE) {
            return false;
        }
        boolean clouds = ClientConfig.CLOUDS.get() && mc.options.getCloudsType() != CloudStatus.OFF;
        boolean storms = ClientConfig.CLOUDS.get() && !ClientStorms.cells().isEmpty();
        boolean tornadoes = !com.realweather.client.tornado.ClientTornadoes.isEmpty();
        if (!clouds && !storms && !tornadoes) {
            return false;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = camera.getPosition();
        int q = ClientConfig.quality();
        SkyLight.update(level, pt, camera);

        float terrain = mc.options.getEffectiveRenderDistance() * 16F;
        float limit = mc.gameRenderer.getDepthFar() * 0.85F;
        splitDist = terrain * 0.92F;
        float[] fog = RenderSystem.getShaderFogColor();
        FOG[0] = fog[0];
        FOG[1] = fog[1];
        FOG[2] = fog[2];
        float shelter = 0.35F + 0.65F * ClientWeather.exposure;
        float rain = Mth.clamp(ClientWeather.camRain * shelter, 0F, 1F);
        float vis = Mth.lerp(rain * 0.85F, 2600F, 260F);
        vis = Math.min(vis, Mth.lerp(ClientWeather.camSnow * shelter, vis, 300F));
        vis = Math.min(vis, Mth.lerp(ClientWeather.camBlizzard * shelter, vis, 30F));
        vis = Math.min(vis, Mth.lerp(ClientWeather.camSand * shelter, vis, 26F));
        vis = Math.min(vis, Mth.lerp(TornadoRenderer.dustAt(cam.x, cam.y, cam.z), vis, 20F));
        float fadeEnd = Math.max(CloudField.radius(q), 900F) * 1.05F;
        SkyBillboards.begin(camera, limit, FOG, vis, fadeEnd);
        if (clouds) {
            float rainLevel = level.getRainLevel(pt);
            float cover = ClientStorms.synced ? rainLevel * Mth.clamp(ClientStorms.stratiform + 0.3F, 0F, 1F) : rainLevel;
            CloudField.collect(level, cam, pt, q, cover);
        }
        if (storms) {
            StormClouds.collect(level, cam, pt, q);
        }
        if (tornadoes) {
            TornadoRenderer.collect(level, cam, pt, ClientConfig.tornadoQuality(), SkyLight.r, SkyLight.g, SkyLight.b, SkyLight.sunSide);
        } else {
            TornadoRenderer.lastDrawn = 0;
        }
        return SkyBillboards.count() > 0;
    }

    private static void draw(RenderLevelStageEvent event, boolean far) {
        Minecraft mc = Minecraft.getInstance();
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
        // Haze is applied per sprite (by real distance), so switch the shader fog off.
        RenderSystem.setShaderFogStart(1.0E7F);
        RenderSystem.setShaderFogEnd(1.1E7F);
        try {
            SkyBillboards.render(event.getCamera(), far, splitDist);
        } finally {
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
}
