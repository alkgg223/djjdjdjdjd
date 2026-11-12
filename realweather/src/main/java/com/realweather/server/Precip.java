package com.realweather.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * What falls from the sky at a place: rain, snow or blowing sand. Normally the biome decides; a command can force
 * one kind everywhere (snow on the plains, a sandstorm in the forest, rain in the desert) for as long as it lasts.
 */
public final class Precip {
    public static final int NONE = 0, RAIN = 1, SNOW = 2, SAND = 3;
    /** The kind forced on the client (synced from the server), 0 = the biomes decide. */
    public static int clientForce;

    private Precip() {}

    public static int force(Level level) {
        return level.isClientSide() ? clientForce : WeatherServer.force;
    }

    public static int at(Level level, Biome biome, BlockPos pos) {
        return at(biome, pos, force(level));
    }

    public static int at(Biome biome, BlockPos pos, int force) {
        if (force == RAIN || force == SNOW || force == SAND) {
            return force;
        }
        if (WeatherServer.isDryBiome(biome)) {
            return SAND;
        }
        if (!biome.hasPrecipitation()) {
            return NONE;
        }
        Biome.Precipitation p = biome.getPrecipitationAt(pos);
        return p == Biome.Precipitation.RAIN ? RAIN : p == Biome.Precipitation.SNOW ? SNOW : NONE;
    }

    public static Biome.Precipitation vanilla(int kind) {
        return kind == RAIN ? Biome.Precipitation.RAIN : kind == SNOW ? Biome.Precipitation.SNOW : Biome.Precipitation.NONE;
    }
}
