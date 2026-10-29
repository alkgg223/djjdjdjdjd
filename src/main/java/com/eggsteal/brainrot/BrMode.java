package com.eggsteal.brainrot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.FlatLevelSource;

/**
 * Which game a world plays. The "Brainrot Heist" world type is a flat void world with the meadow biome
 * (the Egg Steal world type uses plains), so the mode is read from the world itself.
 */
public final class BrMode {
    private static MinecraftServer cachedFor;
    private static boolean cached;

    public static boolean on(MinecraftServer s) {
        if (s == null) return false;
        if (cachedFor != s) {
            cachedFor = s;
            ServerLevel sl = s.overworld();
            cached = sl.getChunkSource().getGenerator() instanceof FlatLevelSource && sl.getBiome(new BlockPos(0, 64, 0)).is(Biomes.MEADOW);
        }
        return cached;
    }

    public static void reset() { cachedFor = null; }
}
