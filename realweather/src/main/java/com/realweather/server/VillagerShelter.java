package com.realweather.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Optional;

/** Villagers run home (to the beds, the same way they hide from raids) when a storm hits the village. */
public final class VillagerShelter {
    private static final String TAG = "realweather_shelter";

    private VillagerShelter() {}

    static void tick(ServerLevel level, WeatherData d) {
        boolean anyStorm = WeatherServer.thunder > 0.25F || WeatherServer.rain >= 0.75F && level.isRaining()
                || WeatherServer.blizzard > 0.3F || WeatherServer.sand > 0.3F;
        List<? extends Villager> villagers = level.getEntities(EntityType.VILLAGER, v -> v.isAlive());
        for (Villager v : villagers) {
            CompoundTag data = v.getPersistentData();
            boolean sheltering = data.getBoolean(TAG);
            boolean danger = d.villagers && anyStorm && dangerAt(level, v.blockPosition());
            Brain<Villager> brain = v.getBrain();
            Activity scheduled = brain.getSchedule().getActivityAt((int) (level.getDayTime() % 24000L));
            if (danger && scheduled != Activity.REST && !v.isSleeping()) {
                if (brain.isActive(Activity.PANIC) || brain.isActive(Activity.RAID) || brain.isActive(Activity.PRE_RAID)) {
                    continue;
                }
                if (!brain.isActive(Activity.HIDE)) {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    brain.eraseMemory(MemoryModuleType.PATH);
                    brain.setActiveActivityIfPossible(Activity.HIDE);
                    v.getNavigation().stop();
                }
                data.putBoolean(TAG, true);
                // No bed to run to: look for any roof nearby.
                if (level.canSeeSky(v.blockPosition()) && !brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                    BlockPos shelter = findShelter(level, v, brain);
                    if (shelter != null) {
                        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(shelter, 0.65F, 1));
                    }
                }
            } else if (sheltering) {
                data.remove(TAG);
                if (brain.isActive(Activity.HIDE) && !brain.hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME)) {
                    brain.eraseMemory(MemoryModuleType.HIDING_PLACE);
                    brain.updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
                    if (brain.isActive(Activity.HIDE)) {
                        brain.setActiveActivityIfPossible(Activity.IDLE);
                    }
                }
            }
        }
    }

    private static boolean dangerAt(ServerLevel level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        Biome.Precipitation p = biome.getPrecipitationAt(pos);
        if (WeatherServer.isDryBiome(biome)) {
            return WeatherServer.sand > 0.3F;
        }
        if (!level.isRaining()) {
            return false;
        }
        if (p == Biome.Precipitation.SNOW) {
            return WeatherServer.blizzard > 0.3F || WeatherServer.snow >= 0.85F;
        }
        return p == Biome.Precipitation.RAIN && (WeatherServer.thunder > 0.25F || WeatherServer.rain >= 0.75F);
    }

    private static BlockPos findShelter(ServerLevel level, Villager v, Brain<Villager> brain) {
        Optional<GlobalPos> home = brain.getMemory(MemoryModuleType.HOME);
        if (home.isPresent() && home.get().dimension() == level.dimension() && home.get().pos().closerThan(v.blockPosition(), 64)) {
            return home.get().pos();
        }
        BlockPos origin = v.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < 48; i++) {
            BlockPos c = origin.offset(v.getRandom().nextInt(33) - 16, v.getRandom().nextInt(7) - 3, v.getRandom().nextInt(33) - 16);
            if (!level.isLoaded(c) || level.canSeeSky(c)) {
                continue;
            }
            if (!level.getBlockState(c).getCollisionShape(level, c).isEmpty() || !level.getBlockState(c.above()).getCollisionShape(level, c.above()).isEmpty()) {
                continue;
            }
            if (!level.getBlockState(c.below()).isFaceSturdy(level, c.below(), Direction.UP)) {
                continue;
            }
            double dist = c.distSqr(origin);
            if (dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }
        return best;
    }
}
