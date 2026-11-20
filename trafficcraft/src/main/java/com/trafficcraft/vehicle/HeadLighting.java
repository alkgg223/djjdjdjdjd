package com.trafficcraft.vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Real road illumination for headlights: invisible vanilla light blocks placed in the air just above the road ahead of a car whose
 * low / high beams are on at night. Low beam lights a short pool, high beam reaches further and brighter. The blocks follow the car,
 * are removed as soon as the lights go off or the car is removed / unloaded, and their positions are saved with the car so nothing
 * is left behind after a restart.
 */
public final class HeadLighting {
    private static final Map<UUID, List<BlockPos>> PLACED = new HashMap<>();
    private static final float[] LOW_D = {3.5f, 6.5f, 9.5f};
    private static final int[] LOW_L = {13, 12, 9};
    private static final float[] HIGH_D = {4f, 8f, 12f, 16f, 20f};
    private static final int[] HIGH_L = {15, 15, 13, 11, 9};

    private HeadLighting() {}

    static boolean dark(ServerLevel lvl) {
        return lvl.getSkyDarken() >= 4 || lvl.isThundering();
    }

    public static void tick(VehicleEntity v) {
        if (v.tickCount % 4 != 0 || !(v.level() instanceof ServerLevel lvl)) return;
        boolean low = v.has(VehicleEntity.F_LOW);
        boolean high = v.has(VehicleEntity.F_HIGH);
        List<BlockPos> want = new ArrayList<>();
        List<Integer> lev = new ArrayList<>();
        if ((low || high) && dark(lvl) && lvl.getNearestPlayer(v, 56) != null) {
            float[] ds = high ? HIGH_D : LOW_D;
            int[] ls = high ? HIGH_L : LOW_L;
            VehicleSpec s = v.spec();
            for (int i = 0; i < ds.length; i++) {
                Vec3 p = v.position().add(VehicleEntity.toWorldOffset(0, 0.6, s.length / 2 + ds[i], v.getYRot()));
                BlockPos bp = BlockPos.containing(p);
                BlockPos pick = null;
                for (int dy : new int[]{0, 1, -1, 2}) {
                    BlockPos c = bp.above(dy);
                    if (!lvl.isLoaded(c)) break;
                    BlockState st = lvl.getBlockState(c);
                    if ((st.isAir() || st.is(Blocks.LIGHT)) && !lvl.getBlockState(c.below()).isAir()) {
                        pick = c;
                        break;
                    }
                }
                if (pick != null && !want.contains(pick)) {
                    want.add(pick);
                    lev.add(ls[i]);
                }
            }
        }
        List<BlockPos> old = PLACED.getOrDefault(v.getUUID(), List.of());
        for (BlockPos bp : old) if (!want.contains(bp)) erase(lvl, bp);
        for (int i = 0; i < want.size(); i++) {
            BlockPos bp = want.get(i);
            BlockState st = lvl.getBlockState(bp);
            BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lev.get(i));
            if (st.isAir() || (st.is(Blocks.LIGHT) && old.contains(bp) && st.getValue(LightBlock.LEVEL) != lev.get(i)))
                lvl.setBlock(bp, light, Block.UPDATE_CLIENTS);
        }
        if (want.isEmpty()) PLACED.remove(v.getUUID());
        else PLACED.put(v.getUUID(), want);
    }

    private static void erase(ServerLevel lvl, BlockPos bp) {
        if (lvl.isLoaded(bp) && lvl.getBlockState(bp).is(Blocks.LIGHT)) lvl.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    public static void clear(VehicleEntity v) {
        if (!(v.level() instanceof ServerLevel lvl)) return;
        List<BlockPos> old = PLACED.remove(v.getUUID());
        if (old != null) for (BlockPos bp : old) erase(lvl, bp);
    }

    public static void save(VehicleEntity v, CompoundTag t) {
        List<BlockPos> l = PLACED.get(v.getUUID());
        if (l == null || l.isEmpty()) return;
        long[] a = new long[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i).asLong();
        t.putLongArray("HeadLights", a);
    }

    public static void load(VehicleEntity v, CompoundTag t) {
        if (!t.contains("HeadLights")) return;
        List<BlockPos> l = new ArrayList<>();
        for (long x : t.getLongArray("HeadLights")) l.add(BlockPos.of(x));
        PLACED.put(v.getUUID(), l);          // cleaned up / re-used on the next tick
    }
}
