package com.trafficcraft.vehicle;

import com.trafficcraft.registry.TCRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Dynamic headlight illumination: refreshes invisible light blocks in front of player-driven vehicles at night. */
public final class HeadlightLights {
    private static final Map<Level, Map<BlockPos, Long>> FRESH = new WeakHashMap<>();

    public static void update(VehicleEntity v) {
        Level level = v.level();
        if (!v.has(VehicleEntity.F_LOW)) return;
        if (level.getSkyDarken() < 4 && level.getMaxLocalRawBrightness(v.blockPosition()) > 9) return;
        Vec3 f = v.forward();
        float len = v.spec().length / 2;
        int[] dists = v.has(VehicleEntity.F_HIGH) ? new int[]{3, 7, 12, 18} : new int[]{3, 7, 11};
        for (int d : dists) {
            Vec3 p = v.position().add(f.scale(len + d)).add(0, 0.6, 0);
            BlockPos bp = BlockPos.containing(p);
            for (int dy = 0; dy < 3; dy++) {
                BlockPos q = bp.above(dy);
                BlockState st = level.getBlockState(q);
                if (st.is(TCRegistry.HEADLIGHT_GLOW.get())) {
                    mark(level, q);
                    break;
                }
                if (st.isAir() && level.getFluidState(q).isEmpty()) {
                    level.setBlock(q, TCRegistry.HEADLIGHT_GLOW.get().defaultBlockState(), 3);
                    level.scheduleTick(q, TCRegistry.HEADLIGHT_GLOW.get(), 12);
                    mark(level, q);
                    break;
                }
            }
        }
    }

    private static void mark(Level level, BlockPos p) {
        FRESH.computeIfAbsent(level, k -> new HashMap<>()).put(p.immutable(), level.getGameTime());
    }

    public static boolean isFresh(Level level, BlockPos p) {
        Map<BlockPos, Long> m = FRESH.get(level);
        if (m == null) return false;
        Long t = m.get(p);
        if (t == null || level.getGameTime() - t > 10) {
            m.remove(p);
            return false;
        }
        return true;
    }

    public static void removeBlock(Level level, BlockPos p) {
        if (level.getBlockState(p).is(TCRegistry.HEADLIGHT_GLOW.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    }
}
