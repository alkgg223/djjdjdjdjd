package com.realweather.client;

import com.realweather.ClientConfig;
import com.realweather.RealWeather;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;

/** Vanilla overworld sky, with the rain/snow rendering and rain ticking replaced by the realistic versions. */
public class RealOverworldEffects extends DimensionSpecialEffects.OverworldEffects {
    private static boolean failed;

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
        if (failed || !ClientConfig.CUSTOM_PRECIPITATION.get()) {
            return false;
        }
        try {
            return PrecipitationRenderer.render(level, ticks, partialTick, lightTexture, camX, camY, camZ);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            RealWeather.LOG.error("Realistic precipitation disabled after an error, using vanilla weather", e);
            return false;
        }
    }

    /** The lightning flash lights up everything under the open sky with a cold blue-white light. */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight,
                                     int pixelX, int pixelY, org.joml.Vector3f colors) {
        // A solar eclipse dims the daylight (torches and lamps keep their light).
        float ecl = com.realweather.client.sky.ClientEclipse.dark;
        if (ecl > 0.002F) {
            float k = 1F - 0.88F * ecl * (pixelY / 15F);
            float bl = net.minecraft.client.renderer.LightTexture.getBrightness(level.dimensionType(), pixelX) * 1.5F;
            float bg = bl * ((bl * 0.6F + 0.4F) * 0.6F + 0.4F), bb = bl * (bl * bl * 0.6F + 0.4F);
            colors.set(Math.max(colors.x * k, Math.min(1F, bl) * 0.95F), Math.max(colors.y * k, Math.min(1F, bg) * 0.95F),
                    Math.max(colors.z * k, Math.min(1F, bb) * 0.95F));
        }
        float f = LightningFx.worldLight;
        if (f > 0.002F && ClientWeather.active(level)) {
            float s = Math.min(1F, skyLight * 1.2F) * f;
            colors.add(0.82F * s, 0.86F * s, 1.0F * s);
        }
    }

    /** The realistic clouds replace the vanilla cloud layer. */
    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                double camX, double camY, double camZ, org.joml.Matrix4f modelViewMatrix, org.joml.Matrix4f projectionMatrix) {
        return ClientConfig.CLOUDS.get() && !com.realweather.client.sky.SkyRenderer.failed();
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        if (failed || !ClientConfig.CUSTOM_PRECIPITATION.get()) {
            return false;
        }
        try {
            return WeatherTicker.tick(level, ticks, camera);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            RealWeather.LOG.error("Realistic weather particles disabled after an error", e);
            return false;
        }
    }
}
