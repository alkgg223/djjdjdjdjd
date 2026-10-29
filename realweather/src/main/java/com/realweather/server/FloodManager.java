package com.realweather.server;

import com.realweather.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.List;

/**
 * Heavy rain raises the water: low ground next to rivers, lakes and swamps floods first, then basins
 * fill up layer by layer. When the rain eases off, the water drains away, newest layers first.
 */
public final class FloodManager {
    private static final int MAX_BLOCKS = 24000;
    private static final int RADIUS = 44;

    private FloodManager() {}

    static boolean growing(ServerLevel level, WeatherData d) {
        return d.flood && level.isRaining() && WeatherServer.rain >= 0.55F;
    }

    static void tick(ServerLevel level, WeatherData d, List<ServerPlayer> players, RandomSource rng) {
        if (growing(level, d)) {
            float k = (WeatherServer.rain - 0.55F) / 0.45F;
            int attempts = 2 + (int) (k * 10);
            int count = Math.min(players.size(), 8);
            for (int p = 0; p < count && d.floodList.size() < MAX_BLOCKS; p++) {
                ServerPlayer player = players.get(rng.nextInt(players.size()));
                for (int i = 0; i < attempts; i++) {
                    tryFlood(level, d, player, rng, k);
                }
            }
        } else if (!d.floodList.isEmpty() && (!level.isRaining() || WeatherServer.rain < 0.45F || !d.flood) && level.getGameTime() % 2 == 0) {
            // The water soaks away over a minute or two, top layers first.
            int budget = 1 + d.floodList.size() / 1000;
            drain(level, d, budget);
        }
    }

    /** Removes up to `budget` flood blocks, newest first. Blocks in unloaded chunks are kept for later. */
    public static int drain(ServerLevel level, WeatherData d, int budget) {
        int removed = 0;
        int checked = 0;
        int i = d.floodList.size() - 1;
        while (i >= 0 && removed < budget && checked < budget * 4) {
            long packed = d.floodList.getLong(i);
            BlockPos pos = BlockPos.of(packed);
            checked++;
            if (level.isLoaded(pos)) {
                if (level.getFluidState(pos).getType() == ModContent.FLOOD.get()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                d.floodList.removeLong(i);
                d.floodSet.remove(packed);
                removed++;
            }
            i--;
        }
        if (removed > 0) {
            d.setDirty();
        }
        return removed;
    }

    static void onRandomTick(ServerLevel level, BlockPos pos, RandomSource random) {
        WeatherData d = WeatherData.get(level.getServer().overworld());
        boolean keep = level.dimension() == Level.OVERWORLD && growing(level, d) && level.isRainingAt(pos.above());
        if (!keep && random.nextInt(3) != 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            d.removeFlood(pos.asLong());
        }
    }

    private static boolean isFlood(FluidState f) {
        return f.getType() == ModContent.FLOOD.get();
    }

    private static boolean isNaturalWaterSource(FluidState f) {
        return f.is(FluidTags.WATER) && f.isSource() && !isFlood(f);
    }

    private static void tryFlood(ServerLevel level, WeatherData d, ServerPlayer player, RandomSource rng, float k) {
        int x = Mth.floor(player.getX()) + rng.nextInt(RADIUS * 2 + 1) - RADIUS;
        int z = Mth.floor(player.getZ()) + rng.nextInt(RADIUS * 2 + 1) - RADIUS;
        if (!level.isLoaded(new BlockPos(x, 0, z))) {
            return;
        }
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (h <= level.getMinBuildHeight() + 2 || h >= level.getMaxBuildHeight() - 2) {
            return;
        }
        BlockPos pos = new BlockPos(x, h, z);
        if (!level.isRainingAt(pos)) {
            return;
        }
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_BEACH)) {
            return;
        }
        BlockState at = level.getBlockState(pos);
        if (!at.isAir() && !(at.canBeReplaced() && at.getFluidState().isEmpty() && !at.is(Blocks.SNOW))) {
            return;
        }
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        FluidState groundFluid = ground.getFluidState();
        boolean onWater = false;
        if (isFlood(groundFluid)) {
            int depth = 1;
            while (depth < 8 && isFlood(level.getFluidState(below.below(depth)))) {
                depth++;
            }
            if (depth >= d.floodDepth) {
                return;
            }
        } else if (isNaturalWaterSource(groundFluid)) {
            onWater = true;
        } else {
            if (!groundFluid.isEmpty() || !ground.isFaceSturdy(level, below, Direction.UP)) {
                return;
            }
            if (ground.is(Blocks.FARMLAND) || ground.is(BlockTags.LEAVES) || ground.is(BlockTags.ICE) || ground.is(BlockTags.SAND) && rng.nextInt(3) != 0) {
                return;
            }
        }

        boolean spills = false;
        boolean nextToWater = false;
        boolean nextToShore = false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int nx = x + dir.getStepX();
            int nz = z + dir.getStepZ();
            int nh = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz);
            BlockPos same = new BlockPos(nx, h, nz);
            FluidState sameFluid = level.getFluidState(same);
            FluidState lowFluid = level.getFluidState(same.below());
            if (isFlood(sameFluid) || isNaturalWaterSource(sameFluid)) {
                nextToWater = true;
            } else if (isNaturalWaterSource(lowFluid) || isFlood(lowFluid)) {
                // Water surface level with our ground: we are on the bank (or on the water next to another water column).
                nextToWater |= !onWater;
            } else if (nh < h) {
                // The neighbour is lower dry land: water placed here would hang in the air over a slope.
                spills = true;
            } else if (nh == h && onWater) {
                nextToShore = true;
            }
            if (spills) {
                return;
            }
        }

        int higher = 0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int nx = x + (int) Math.round(Math.cos(a) * 3);
            int nz = z + (int) Math.round(Math.sin(a) * 3);
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) > h) {
                higher++;
            }
        }
        boolean basin = higher >= 6;
        boolean swamp = biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.HAS_CLOSER_WATER_FOG);

        float chance;
        if (onWater) {
            chance = nextToShore || nextToWater ? 0.5F : 0F;
        } else if (nextToWater) {
            chance = 0.55F;
        } else if (basin) {
            chance = 0.35F;
        } else {
            chance = 0F;
        }
        if (swamp) {
            chance *= 1.5F;
        }
        chance *= 0.4F + 0.6F * k;
        if (chance <= 0F || rng.nextFloat() >= chance) {
            return;
        }
        level.setBlock(pos, ModContent.FLOOD.get().defaultFluidState().createLegacyBlock(), 3);
        d.addFlood(pos.asLong());
    }
}
