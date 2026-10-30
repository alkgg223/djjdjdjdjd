package com.gunsmod.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Real moving light for muzzle flashes, explosions, flares, missile motors and weapon lights. It places invisible
 * light blocks in the client's copy of the world only (the server and the save never see them) and restores the air
 * afterwards. A small budget of lights and block changes per tick keeps it cheap on phones.
 */
public final class ClientLights {
    private ClientLights() {}

    private static final int MAX_LIGHTS = 8, CHANGES_PER_TICK = 4;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Source(Vec3 at, int level, long until) {}

    private static final Map<Object, Source> SOURCES = new HashMap<>();
    /** light blocks we placed: position -> (our state, what was there before) */
    private static final Map<BlockPos, BlockState[]> PLACED = new HashMap<>();
    @Nullable private static ClientLevel world;
    private static long now;

    /** a light that lasts {@code ticks}; the same key replaces the previous light (a burst of shots keeps one light) */
    public static void light(Object key, Vec3 at, int level, int ticks) {
        SOURCES.put(key, new Source(at, Math.max(1, Math.min(15, level)), now + ticks));
    }

    /** a short flash with its own key */
    public static void flash(Vec3 at, int level, int ticks) {
        light(new Object(), at, level, ticks);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != world) {
            // new world (or none): the old light blocks went with the old client world
            PLACED.clear();
            SOURCES.clear();
            world = level;
        }
        if (level == null) return;
        now++;
        SOURCES.values().removeIf(s -> s.until < now);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        // wanted lights: the strongest sources near the camera, merged per block
        List<Source> list = new ArrayList<>(SOURCES.values());
        list.sort((a, b) -> Double.compare(a.at.distanceToSqr(cam), b.at.distanceToSqr(cam)));
        Map<BlockPos, Integer> want = new HashMap<>();
        for (Source s : list) {
            if (want.size() >= MAX_LIGHTS || s.at.distanceToSqr(cam) > 96 * 96) break;
            BlockPos p = spot(level, BlockPos.containing(s.at));
            if (p != null) want.merge(p, s.level, Math::max);
        }
        int budget = CHANGES_PER_TICK;
        Iterator<Map.Entry<BlockPos, BlockState[]>> it = PLACED.entrySet().iterator();
        while (it.hasNext() && budget > 0) {
            Map.Entry<BlockPos, BlockState[]> e = it.next();
            Integer w = want.get(e.getKey());
            if (w != null && e.getValue()[0].getValue(LightBlock.LEVEL).intValue() == w.intValue()) continue;
            restore(level, e.getKey(), e.getValue());
            it.remove();
            budget--;
        }
        for (Map.Entry<BlockPos, Integer> e : want.entrySet()) {
            if (budget <= 0) break;
            if (PLACED.containsKey(e.getKey())) continue;
            place(level, e.getKey(), e.getValue());
            budget--;
        }
    }

    /** an air (or still water) block at or right next to the point */
    @Nullable
    private static BlockPos spot(ClientLevel level, BlockPos p) {
        if (free(level, p)) return p;
        for (Direction d : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
            BlockPos q = p.relative(d);
            if (free(level, q)) return q;
        }
        return null;
    }

    private static boolean free(ClientLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockState st = level.getBlockState(p);
        if (PLACED.containsKey(p)) return true;
        return st.isAir() || st.is(Blocks.WATER) && level.getFluidState(p).isSource();
    }

    private static void place(ClientLevel level, BlockPos p, int lvl) {
        BlockState old = level.getBlockState(p);
        boolean water = old.is(Blocks.WATER);
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lvl).setValue(BlockStateProperties.WATERLOGGED, water);
        level.setBlock(p, light, FLAGS);
        PLACED.put(p.immutable(), new BlockState[]{light, old});
    }

    private static void restore(ClientLevel level, BlockPos p, BlockState[] s) {
        // only if it is still our light: the server may have put a real block there meanwhile
        if (level.isLoaded(p) && level.getBlockState(p) == s[0]) level.setBlock(p, s[1], FLAGS);
    }
}
