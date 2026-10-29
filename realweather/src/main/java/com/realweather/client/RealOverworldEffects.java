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
        float f = LightningFx.worldLight;
        if (f > 0.002F && ClientWeather.active(level)) {
            float s = Math.min(1F, skyLight * 1.2F) * f;
            colors.add(0.82F * s, 0.86F * s, 1.0F * s);
        }
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
