package com.realweather.server;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The ground a tsunami runs over, smoothed over 8-block cells so the wave climbs hills and drops into valleys
 * without jumping block by block. Trees, plants and standing water are looked through to the land underneath; out
 * at sea (or where the land lies below it) the sea surface is the floor. The same on server and client.
 */
public final class TsunamiGround {
    private static final int CELL = 8;
    private static final Long2FloatOpenHashMap SERVER = new Long2FloatOpenHashMap(), CLIENT = new Long2FloatOpenHashMap();
    private static long serverStamp = Long.MIN_VALUE, clientStamp = Long.MIN_VALUE;

    private TsunamiGround() {}

    /** Floor under the wave at a point: the land, or the sea surface `sea` where the land is lower. */
    public static float at(Level level, double x, double z, float sea) {
        double fx = x / CELL, fz = z / CELL;
        int ix = Mth.floor(fx), iz = Mth.floor(fz);
        float tx = (float) (fx - ix), tz = (float) (fz - iz);
        float a = cell(level, ix, iz, sea), b = cell(level, ix + 1, iz, sea);
        float c = cell(level, ix, iz + 1, sea), d = cell(level, ix + 1, iz + 1, sea);
        return Mth.lerp(tz, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d));
    }

    private static float cell(Level level, int ix, int iz, float sea) {
        boolean client = level.isClientSide();
        Long2FloatOpenHashMap map = client ? CLIENT : SERVER;
        long now = level.getGameTime();
        // The land changes slowly (and the wave itself tears some of it down): look again every ten seconds.
        if (client ? Math.abs(now - clientStamp) > 200 : Math.abs(now - serverStamp) > 200) {
            map.clear();
            if (client) {
                clientStamp = now;
            } else {
                serverStamp = now;
            }
        }
        long key = ((long) ix << 32) ^ (iz & 0xFFFFFFFFL);
        float v = map.getOrDefault(key, Float.NaN);
        if (Float.isNaN(v)) {
            int x = ix * CELL, z = iz * CELL;
            if (!level.hasChunk(x >> 4, z >> 4)) {
                // Not loaded: ride on the sea there, and look again once it is.
                return sea;
            }
            v = Math.max(sea, top(level, x, z));
            if (map.size() > 60000) {
                map.clear();
            }
            map.put(key, v);
        }
        return Math.max(sea, v);
    }

    /** Height of the land itself at a column (the first free block above it), looking through trees, plants and water. */
    public static int top(Level level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int min = level.getMinBuildHeight();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 40 && y > min; i++) {
            BlockState s = level.getBlockState(m.set(x, y - 1, z));
            if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || !s.getFluidState().isEmpty()
                    || (s.canBeReplaced() && s.getFluidState().isEmpty())) {
                y--;
            } else if (level.getBlockState(m.set(x, y - 2, z)).isAir() && level.getBlockState(m.set(x, y - 3, z)).isAir()) {
                // A roof, a bridge or an overhang with open space under it: the ground is further down.
                y -= 3;
            } else {
                break;
            }
        }
        return y;
    }
}
