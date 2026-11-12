package com.realweather.client.particle;

import com.realweather.ModContent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public final class WeatherParticles {
    private WeatherParticles() {}

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModContent.SPLASH.get(), SplashParticle.Provider::new);
        event.registerSpriteSet(ModContent.RIPPLE.get(), RippleParticle.Provider::new);
        event.registerSpriteSet(ModContent.RAIN_DROP.get(), StreakParticle.DropProvider::new);
        event.registerSpriteSet(ModContent.BLIZZARD_FLAKE.get(), StreakParticle.BlizzardProvider::new);
        event.registerSpriteSet(ModContent.SAND_GRAIN.get(), StreakParticle.GrainProvider::new);
        event.registerSpriteSet(ModContent.SNOW_FLAKE.get(), FlakeParticle.Provider::new);
        event.registerSpriteSet(ModContent.DUST.get(), DustParticle.Provider::new);
        event.registerSpriteSet(ModContent.TORNADO_DUST.get(), com.realweather.client.tornado.TornadoDustParticle.Provider::new);
        event.registerSpriteSet(ModContent.HAILSTONE.get(), com.realweather.client.hail.HailParticle.Provider::new);
        event.registerSpriteSet(ModContent.TORNADO_CLOUD.get(), com.realweather.client.tornado.TornadoCloudParticle.Provider::new);
        event.registerSpriteSet(ModContent.SPLASH_DROP.get(), DropletParticle.Provider::new);
        event.registerSpriteSet(ModContent.RAIN_MIST.get(), RainMistParticle.Provider::new);
        event.registerSpriteSet(ModContent.TSUNAMI_SPRAY.get(), com.realweather.client.tsunami.TsunamiSprayParticle.Provider::new);
    }
}
