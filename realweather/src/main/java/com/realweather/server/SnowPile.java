package com.realweather.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/** Heavy snowfall builds up deeper snow (vanilla only ever places a single layer). */
public final class SnowPile {
    private SnowPile() {}

    static void tick(ServerLevel level, WeatherData d, List<ServerPlayer> players, RandomSource rng) {
        if (!d.snowPile || !level.isRaining() || WeatherServer.snow < 0.35F || level.getGameTime() % 2 != 0) {
            return;
        }
        int maxLayers = Mth.clamp(1 + Math.round(WeatherServer.snow * 3F) + Math.round(WeatherServer.blizzard * 3F), 1, 7);
        int attempts = 1 + (int) (WeatherServer.snow * 3F) + (int) (WeatherServer.blizzard * 4F);
        int count = Math.min(players.size(), 8);
        for (int p = 0; p < count; p++) {
            ServerPlayer player = players.get(rng.nextInt(players.size()));
            for (int i = 0; i < attempts; i++) {
                int x = Mth.floor(player.getX()) + rng.nextInt(97) - 48;
                int z = Mth.floor(player.getZ()) + rng.nextInt(97) - 48;
                pile(level, new BlockPos(x, 0, z), maxLayers);
            }
        }
    }

    private static void pile(ServerLevel level, BlockPos column, int maxLayers) {
        if (!level.isLoaded(column)) {
            return;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        Biome biome = level.getBiome(top).value();
        if (biome.getPrecipitationAt(top) != Biome.Precipitation.SNOW || level.getBrightness(LightLayer.BLOCK, top) >= 10) {
            return;
        }
        BlockPos snowPos = null;
        if (level.getBlockState(top).is(Blocks.SNOW)) {
            snowPos = top;
        } else if (level.getBlockState(top.below()).is(Blocks.SNOW)) {
            snowPos = top.below();
        }
        if (snowPos != null) {
            BlockState state = level.getBlockState(snowPos);
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers >= maxLayers) {
                return;
            }
            // Keep the cover even: a spot only grows when its neighbours are about as deep.
            int lowest = 8;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                lowest = Math.min(lowest, layersAt(level, snowPos.relative(dir)));
            }
            if (layers <= lowest + 1) {
                level.setBlockAndUpdate(snowPos, state.setValue(SnowLayerBlock.LAYERS, layers + 1));
            }
        } else if (level.getBlockState(top).isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, top)) {
            level.setBlockAndUpdate(top, Blocks.SNOW.defaultBlockState());
        }
    }

    private static int layersAt(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.is(Blocks.SNOW)) {
            return s.getValue(SnowLayerBlock.LAYERS);
        }
        if (s.isAir()) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(Blocks.SNOW) ? 0 : below.isFaceSturdy(level, pos.below(), Direction.UP) ? 0 : 8;
        }
        // Walls, fences and solid blocks count as already full.
        return 8;
    }
}
