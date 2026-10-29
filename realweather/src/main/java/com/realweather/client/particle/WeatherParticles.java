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
    }
}
