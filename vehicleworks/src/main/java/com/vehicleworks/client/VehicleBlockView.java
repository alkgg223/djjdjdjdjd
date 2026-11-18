package com.vehicleworks.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;

/**
 * Immutable, thread-safe view of a vehicle's blocks in local coordinates, used to tessellate
 * the vehicle mesh off the render thread (face culling and ambient occlusion between
 * vehicle blocks work exactly like in world chunks).
 */
public final class VehicleBlockView implements BlockAndTintGetter {
    private final Long2ObjectOpenHashMap<BlockState> blocks;
    private final int sky, block;
    private final Biome biome;

    public VehicleBlockView(Long2ObjectOpenHashMap<BlockState> blocks, int sky, int block, Biome biome) {
        this.blocks = blocks;
        this.sky = sky;
        this.block = block;
        this.biome = biome;
    }

    @Override
    public float getShade(Direction d, boolean shade) {
        if (!shade) return 1.0f;
        return switch (d) {
            case DOWN -> 0.5f;
            case UP -> 1.0f;
            case NORTH, SOUTH -> 0.8f;
            default -> 0.6f;
        };
    }

    @Override
    public LevelLightEngine getLightEngine() {
        throw new UnsupportedOperationException("vehicle view has no light engine");
    }

    /** interior light sources (block key → light level), falling off by one level per block */
    public it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap lamps;

    private int lampLight(BlockPos pos) {
        if (lamps == null || lamps.isEmpty()) return 0;
        int best = 0;
        for (var e : lamps.long2IntEntrySet()) {
            long k = e.getLongKey();
            int d = Math.abs(BlockPos.getX(k) - pos.getX()) + Math.abs(BlockPos.getY(k) - pos.getY()) + Math.abs(BlockPos.getZ(k) - pos.getZ());
            best = Math.max(best, e.getIntValue() - d);
        }
        return best;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return layer == LightLayer.SKY ? sky : Math.max(block, lampLight(pos));
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return Math.max(sky - darkening, Math.max(block, lampLight(pos)));
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return sky >= 15;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return resolver.getColor(biome, pos.getX(), pos.getZ());
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState s = blocks.get(pos.asLong());
        return s != null ? s : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return 4096;
    }

    @Override
    public int getMinBuildHeight() {
        return -2048;
    }
}
