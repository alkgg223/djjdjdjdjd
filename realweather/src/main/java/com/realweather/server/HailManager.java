package com.realweather.server;

import com.realweather.ModContent;
import com.realweather.RealWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Hail under the storm cores: stones pile up on the ground, pack into hail-snow, sting whoever stands outside, then melt. */
public final class HailManager {
    public static final ResourceKey<DamageType> HAIL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RealWeather.id("hail"));

    private HailManager() {}

    static void tick(ServerLevel level, WeatherData d, List<ServerPlayer> players, RandomSource rng) {
        if (!level.isRaining() || d.storms.isEmpty() || players.isEmpty() || !d.hail) {
            return;
        }
        long t = level.getGameTime();
        int count = Math.min(players.size(), 8);
        for (int p = 0; p < count; p++) {
            ServerPlayer player = players.get(rng.nextInt(players.size()));
            float h = StormManager.hailAt(d, player.getX(), player.getZ());
            if (h < 0.05F) {
                continue;
            }
            if (t % 2 == 0) {
                int attempts = 1 + (int) (h * 7F);
                for (int i = 0; i < attempts; i++) {
                    int x = Mth.floor(player.getX()) + rng.nextInt(81) - 40;
                    int z = Mth.floor(player.getZ()) + rng.nextInt(81) - 40;
                    float here = StormManager.hailAt(d, x, z);
                    if (rng.nextFloat() < here) {
                        accumulate(level, new BlockPos(x, 0, z), here, rng);
                    }
                }
            }
            if (t % 40 == 0 && h > 0.45F) {
                sting(level, player, h, rng);
            }
        }
    }

    private static void accumulate(ServerLevel level, BlockPos column, float hail, RandomSource rng) {
        if (!level.isLoaded(column)) {
            return;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        Biome biome = level.getBiome(top).value();
        if (!biome.hasPrecipitation() || WeatherServer.isDryBiome(biome)) {
            return;
        }
        CoverBlock pellets = ModContent.HAIL_COVER.get();
        CoverBlock snow = ModContent.HAIL_SNOW.get();
        BlockState at = level.getBlockState(top);
        BlockState below = level.getBlockState(top.below());
        if (at.is(pellets)) {
            int l = at.getValue(CoverBlock.LAYERS);
            if (l < 3) {
                level.setBlockAndUpdate(top, at.setValue(CoverBlock.LAYERS, l + 1));
            } else if (rng.nextFloat() < 0.35F) {
                // The stones keep piling up and pack into icy hail-snow.
                level.setBlockAndUpdate(top, snow.defaultBlockState());
            }
            return;
        }
        BlockPos snowPos = at.is(snow) ? top : below.is(snow) ? top.below() : null;
        if (snowPos != null) {
            BlockState s = level.getBlockState(snowPos);
            int l = s.getValue(CoverBlock.LAYERS);
            int max = Mth.clamp(1 + Math.round(hail * 5F), 1, 6);
            if (l < max && l <= minNeighbour(level, snowPos, snow) + 1) {
                level.setBlockAndUpdate(snowPos, s.setValue(CoverBlock.LAYERS, l + 1));
            }
            return;
        }
        if (at.isAir() && pellets.defaultBlockState().canSurvive(level, top)) {
            level.setBlockAndUpdate(top, pellets.defaultBlockState());
        }
    }

    private static int minNeighbour(ServerLevel level, BlockPos pos, CoverBlock snow) {
        int min = 8;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState s = level.getBlockState(pos.relative(dir));
            min = Math.min(min, s.is(snow) ? s.getValue(CoverBlock.LAYERS) : s.isAir() ? 0 : 8);
        }
        return min;
    }

    /** Big hailstones hurt anyone caught outside; a helmet helps. */
    private static void sting(ServerLevel level, ServerPlayer near, float hail, RandomSource rng) {
        AABB box = near.getBoundingBox().inflate(40, 12, 40);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                continue;
            }
            if (!level.canSeeSky(BlockPos.containing(e.getX(), e.getEyeY(), e.getZ()))) {
                continue;
            }
            float h = StormManager.hailAt(WeatherData.get(level), e.getX(), e.getZ());
            if (h < 0.45F || rng.nextFloat() > h) {
                continue;
            }
            boolean helmet = !e.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
            if (helmet && rng.nextBoolean()) {
                continue;
            }
            e.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(HAIL_DAMAGE)), h > 0.8F ? 2F : 1F);
        }
    }

    /** Random tick of a hail layer: it melts once the hail stops, unless the weather is freezing. */
    static void melt(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        WeatherData d = WeatherData.get(level.getServer().overworld());
        if (StormManager.hailAt(d, pos.getX(), pos.getZ()) > 0.05F && level.isRaining()) {
            return;
        }
        Biome biome = level.getBiome(pos).value();
        if (biome.coldEnoughToSnow(pos)) {
            return;
        }
        CoverBlock block = (CoverBlock) state.getBlock();
        if (random.nextFloat() > (block.pellets ? 0.6F : 0.3F)) {
            return;
        }
        int l = state.getValue(CoverBlock.LAYERS);
        if (l <= 1) {
            level.removeBlock(pos, false);
        } else {
            level.setBlockAndUpdate(pos, state.setValue(CoverBlock.LAYERS, l - 1));
        }
    }
}
