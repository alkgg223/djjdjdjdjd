package com.realweather.space;

import com.mojang.serialization.MapCodec;
import com.realweather.RealWeather;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The planets dimension's ground maker. */
public final class SpaceRegistry {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR,
            RealWeather.MODID);

    static {
        GENERATORS.register("planet", () -> PlanetChunkGenerator.CODEC);
    }

    private SpaceRegistry() {}

    public static void register(IEventBus bus) {
        GENERATORS.register(bus);
    }
}
