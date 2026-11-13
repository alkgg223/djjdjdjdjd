package com.realweather.space;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.realweather.device.PlanetBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Makes the ground of the worlds in the planets dimension, column by column, from {@link PlanetTerrain}. */
public class PlanetChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PlanetChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, PlanetChunkGenerator::new));
    private static final int MIN_Y = -64, HEIGHT = 384;

    public PlanetChunkGenerator(BiomeSource biomes) {
        super(biomes);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    private static BlockState block(int tile) {
        return PlanetBlocks.state(tile);
    }

    private static BlockState at(Worlds.World w, int x, int z, int y, int h, int top) {
        if (y <= MIN_Y + 1) {
            return Blocks.BEDROCK.defaultBlockState();
        }
        if (y <= h) {
            return block(PlanetTerrain.tileAt(w, top, h - y));
        }
        if (w.ocean() && y <= PlanetTerrain.SEA_LEVEL - 1) {
            return Blocks.WATER.defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = bx + dx, z = bz + dz;
                Worlds.World w = Worlds.at(x);
                int h = PlanetTerrain.height(w, x, z);
                int top = PlanetTerrain.surfaceTile(w, x, z, h);
                int upTo = Math.max(h, w.ocean() ? PlanetTerrain.SEA_LEVEL - 1 : h);
                for (int y = MIN_Y; y <= upTo; y++) {
                    BlockState s = at(w, x, z, y, h, top);
                    if (!s.isAir()) {
                        chunk.setBlockState(pos.set(x, y, z), s, false);
                    }
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        Worlds.World w = Worlds.at(x);
        int h = PlanetTerrain.height(w, x, z);
        if (w.ocean() && (type == Heightmap.Types.WORLD_SURFACE_WG || type == Heightmap.Types.WORLD_SURFACE || type == Heightmap.Types.MOTION_BLOCKING)) {
            h = Math.max(h, PlanetTerrain.SEA_LEVEL - 1);
        }
        return h + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        Worlds.World w = Worlds.at(x);
        int h = PlanetTerrain.height(w, x, z);
        int top = PlanetTerrain.surfaceTile(w, x, z, h);
        BlockState[] col = new BlockState[HEIGHT];
        for (int i = 0; i < HEIGHT; i++) {
            col[i] = at(w, x, z, MIN_Y + i, h, top);
        }
        return new NoiseColumn(MIN_Y, col);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk,
                             GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return PlanetTerrain.SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Real Weather world: " + Worlds.at(pos.getX()).id());
    }
}
