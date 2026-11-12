package com.realweather.server;

import com.realweather.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Deep snow. Heavy snowfall and blizzards pile snow up to three blocks deep (vanilla only ever lays one thin layer):
 * each full block of snow turns into a snow block and the next layers start on top of it. The cover settles the way
 * real snow does: it evens out, slides off steps and steep spots onto the ground below, drifts against walls, and
 * once the storm is over it melts back in the sun where the weather isn't cold. The same goes for the hail-snow a
 * hailstorm leaves.
 */
public final class SnowPile {
    /** Deepest pile, in layers (eight to a block). */
    public static final int MAX_DEPTH = 24;

    private SnowPile() {}

    static void tick(ServerLevel level, WeatherData d, List<ServerPlayer> players, RandomSource rng) {
        if (players.isEmpty()) {
            return;
        }
        boolean snowing = level.isRaining() && WeatherServer.snow >= 0.35F;
        int count = Math.min(players.size(), 8);
        if (d.snowPile && snowing) {
            int maxDepth = Mth.clamp(1 + Math.round(WeatherServer.snow * 6F) + Math.round(WeatherServer.blizzard * 17F), 1, MAX_DEPTH);
            int attempts = 1 + (int) (WeatherServer.snow * 6F) + (int) (WeatherServer.blizzard * 14F);
            for (int p = 0; p < count; p++) {
                ServerPlayer player = players.get(rng.nextInt(players.size()));
                for (int i = 0; i < attempts; i++) {
                    int x = Mth.floor(player.getX()) + rng.nextInt(97) - 48;
                    int z = Mth.floor(player.getZ()) + rng.nextInt(97) - 48;
                    pile(level, new BlockPos(x, 0, z), maxDepth);
                }
            }
        }
        // Settling, sliding and (after the storm) melting, a few columns a tick around each player.
        if (level.getGameTime() % 2 == 0) {
            boolean warmDay = !snowing && level.isDay();
            for (int p = 0; p < count; p++) {
                ServerPlayer player = players.get(rng.nextInt(players.size()));
                for (int i = 0; i < 6; i++) {
                    int x = Mth.floor(player.getX()) + rng.nextInt(65) - 32;
                    int z = Mth.floor(player.getZ()) + rng.nextInt(65) - 32;
                    settle(level, new BlockPos(x, 0, z), warmDay, rng);
                }
            }
        }
    }

    private static void pile(ServerLevel level, BlockPos column, int maxDepth) {
        if (!level.isLoaded(column)) {
            return;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        if (Precip.at(level.getBiome(top), top, WeatherServer.force) != Precip.SNOW || level.getBrightness(LightLayer.BLOCK, top) >= 10) {
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
            int depth = depth(level, snowPos);
            // Keep the cover even: a spot only grows when its neighbours are about as deep.
            if (depth >= maxDepth || depth > lowestNeighbourDepth(level, snowPos) + 1) {
                return;
            }
            if (layers < 7) {
                level.setBlockAndUpdate(snowPos, state.setValue(SnowLayerBlock.LAYERS, layers + 1));
            } else if (level.getBlockState(snowPos.above()).isAir()) {
                // A full block: it packs into a snow block and the next layer starts on top.
                level.setBlockAndUpdate(snowPos, Blocks.SNOW_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(snowPos.above(), Blocks.SNOW.defaultBlockState());
            }
        } else if (level.getBlockState(top).isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, top)) {
            level.setBlockAndUpdate(top, Blocks.SNOW.defaultBlockState());
        }
    }

    // ------------------------------------------------------------------------------------ depth
    /** Layers in a snow (or hail-snow) block: 1..8, 8 for a full snow block, 0 for anything else. */
    public static int layers(BlockState s) {
        if (s.is(Blocks.SNOW)) {
            return s.getValue(SnowLayerBlock.LAYERS);
        }
        if (s.is(Blocks.SNOW_BLOCK)) {
            return 8;
        }
        if (s.getBlock() instanceof CoverBlock c && !c.pellets) {
            return s.getValue(CoverBlock.LAYERS);
        }
        return 0;
    }

    /** Total depth of the pile whose top layer is at `top`, in layers (the full blocks under it count eight each). */
    public static int depth(ServerLevel level, BlockPos top) {
        int d = layers(level.getBlockState(top));
        BlockPos.MutableBlockPos m = top.mutable();
        for (int i = 0; i < 3; i++) {
            m.move(Direction.DOWN);
            BlockState s = level.getBlockState(m);
            if (layers(s) == 8) {
                d += 8;
            } else {
                break;
            }
        }
        return d;
    }

    /** Depth of the shallowest neighbour (walls and solid ground count as deep: snow banks up against them). */
    static int lowestNeighbourDepth(ServerLevel level, BlockPos top) {
        int base = top.getY() - (depth(level, top) - layers(level.getBlockState(top))) / 8;
        int lowest = MAX_DEPTH;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            lowest = Math.min(lowest, neighbourDepth(level, top.relative(dir).atY(base)));
        }
        return lowest;
    }

    /** Snow lying in the neighbouring column at the same ground level, in layers (a wall counts as full). */
    private static int neighbourDepth(ServerLevel level, BlockPos ground) {
        BlockPos.MutableBlockPos m = ground.mutable();
        int d = 0;
        for (int i = 0; i < 4; i++) {
            BlockState s = level.getBlockState(m);
            int l = layers(s);
            if (l > 0) {
                d += l;
                if (l < 8) {
                    return d;
                }
                m.move(Direction.UP);
                continue;
            }
            if (s.isAir()) {
                // A drop (a step down, the edge of a roof) doesn't hold the pile back; level ground does.
                return level.getBlockState(m.below()).isAir() ? MAX_DEPTH : d;
            }
            return MAX_DEPTH;
        }
        return d;
    }

    // ------------------------------------------------------------------------------------ settling
    /**
     * One column of the snow cover settles: a pile standing well above a neighbour (a step, the edge of a roof, a slope)
     * sheds a layer onto it; in the sun after the storm a layer melts away.
     */
    private static void settle(ServerLevel level, BlockPos column, boolean warmDay, RandomSource rng) {
        if (!level.isLoaded(column)) {
            return;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        BlockPos pos = layers(level.getBlockState(top)) > 0 ? top : top.below();
        BlockState s = level.getBlockState(pos);
        int l = layers(s);
        if (l == 0) {
            return;
        }
        boolean hail = s.getBlock() instanceof CoverBlock;
        if (warmDay && !hail && (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) && onGround(level, pos)) && !level.getBiome(pos).value().coldEnoughToSnow(pos)
                && level.canSeeSky(pos.above()) && rng.nextFloat() < 0.25F) {
            melt(level, pos, s);
            return;
        }
        if (l < 3 && !s.is(Blocks.SNOW_BLOCK)) {
            return;
        }
        // Slide: onto a neighbour whose snow surface lies at least two layers lower.
        float surface = pos.getY() + (s.is(Blocks.SNOW_BLOCK) ? 1F : l / 8F);
        Direction best = null;
        float bestDrop = 2.5F / 8F;
        BlockPos bestPos = null;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(dir);
            for (int dy = 0; dy >= -3; dy--) {
                BlockPos at = n.offset(0, dy, 0);
                BlockState ns = level.getBlockState(at);
                float ns0;
                BlockPos target;
                if (ns.isAir()) {
                    BlockState below = level.getBlockState(at.below());
                    if (below.isAir()) {
                        continue;
                    }
                    if (layers(below) > 0 && layers(below) < 8) {
                        ns0 = at.getY() - 1 + layers(below) / 8F;
                        target = at.below();
                    } else if (Blocks.SNOW.defaultBlockState().canSurvive(level, at)) {
                        ns0 = at.getY();
                        target = at;
                    } else {
                        break;
                    }
                } else if (layers(ns) > 0 && layers(ns) < 8 && !(ns.getBlock() instanceof CoverBlock) == !hail) {
                    ns0 = at.getY() + layers(ns) / 8F;
                    target = at;
                } else {
                    break;
                }
                float drop = surface - ns0;
                if (drop > bestDrop) {
                    bestDrop = drop;
                    best = dir;
                    bestPos = target;
                }
                break;
            }
        }
        if (best == null || rng.nextFloat() > 0.6F) {
            return;
        }
        // Take a layer off the top here...
        melt(level, pos, s);
        // ...and add it to the lower spot.
        BlockState t = level.getBlockState(bestPos);
        if (t.isAir()) {
            level.setBlockAndUpdate(bestPos, hail ? ModContent.HAIL_SNOW.get().defaultBlockState() : Blocks.SNOW.defaultBlockState());
        } else if (t.is(Blocks.SNOW)) {
            level.setBlockAndUpdate(bestPos, t.setValue(SnowLayerBlock.LAYERS, Math.min(8, t.getValue(SnowLayerBlock.LAYERS) + 1)));
        } else if (t.getBlock() instanceof CoverBlock) {
            level.setBlockAndUpdate(bestPos, t.setValue(CoverBlock.LAYERS, Math.min(8, t.getValue(CoverBlock.LAYERS) + 1)));
        }
    }

    /** Whether a snow block is part of a pile lying on the land (not something built of snow blocks). */
    private static boolean onGround(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos m = pos.mutable();
        for (int i = 0; i < 3; i++) {
            m.move(Direction.DOWN);
            BlockState b = level.getBlockState(m);
            if (b.is(Blocks.SNOW_BLOCK)) {
                continue;
            }
            return QuakeManager.isTerrain(b) && !b.isAir();
        }
        return false;
    }

    /** One layer less: a snow block opens back into eight layers first. */
    private static void melt(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.is(Blocks.SNOW_BLOCK)) {
            level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7));
            return;
        }
        int l = layers(s);
        if (l <= 1) {
            level.removeBlock(pos, false);
        } else if (s.is(Blocks.SNOW)) {
            level.setBlockAndUpdate(pos, s.setValue(SnowLayerBlock.LAYERS, l - 1));
        } else if (s.getBlock() instanceof CoverBlock) {
            level.setBlockAndUpdate(pos, s.setValue(CoverBlock.LAYERS, l - 1));
        }
    }

    /**
     * Deep snow drags at the legs: anyone wading through it slows down the deeper it is, and packs it down as they go.
     */
    public static void onEntityTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.LivingEntity e) || e.isSpectator() || e.isPassenger() || !e.onGround()
                || e instanceof com.realweather.vehicle.StormVehicle) {
            return;
        }
        if (e instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().flying) {
            return;
        }
        BlockPos feet = e.blockPosition();
        BlockState s = e.level().getBlockState(feet);
        int l = layers(s);
        if (l < 2 || l >= 8) {
            return;
        }
        float slow = Math.min(0.4F, (l - 1) * 0.06F);
        net.minecraft.world.phys.Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * (1F - slow), v.y, v.z * (1F - slow));
        if (e.level() instanceof ServerLevel level && e.tickCount % 4 == 0 && v.horizontalDistanceSqr() > 0.0009) {
            trample(level, feet, e.getRandom());
        }
    }

    /**
     * Someone walking through deep snow packs it down under their feet (a trail forms where many go), called by the
     * movement handler.
     */
    public static void trample(ServerLevel level, BlockPos feet, RandomSource rng) {
        BlockState s = level.getBlockState(feet);
        int l = layers(s);
        if (l >= 3 && l < 8 && rng.nextFloat() < 0.22F) {
            melt(level, feet, s);
        }
    }
}
