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
