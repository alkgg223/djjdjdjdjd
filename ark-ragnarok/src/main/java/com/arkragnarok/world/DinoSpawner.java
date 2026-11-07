package com.arkragnarok.world;

import com.arkragnarok.ArkConfig;
import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.registry.ModEntities;
import com.arkragnarok.species.Diet;
import com.arkragnarok.species.Species;
import com.arkragnarok.species.SpeciesList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wild dinosaurs come by themselves: every few seconds, around every player, a spot 32-90 blocks away on dry
 * natural ground is picked; if its 3x3-chunk area has fewer wild dinosaurs than the density setting, one creature
 * (or a small pack) arrives - plant eaters are common, big predators rare.  Wild ones far from every player despawn
 * again, so the world does not fill up.  Settings: {@link ArkConfig}.
 */
@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DinoSpawner {
    private DinoSpawner() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (!ArkConfig.SPAWNING.get() || ArkConfig.DENSITY.get() <= 0) return;
        ServerLevel level = e.getServer().overworld();
        if (level.getGameTime() % ArkConfig.INTERVAL.get() != 0 || !level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            trySpawn(level, p, level.random);
        }
    }

    private static void trySpawn(ServerLevel level, ServerPlayer p, RandomSource r) {
        double a = r.nextDouble() * Math.PI * 2, d = 32 + r.nextDouble() * 58;
        int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
        BlockPos col = new BlockPos(x, 0, z);
        if (!level.isLoaded(col) || !level.isPositionEntityTicking(col)) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        BlockState ground = level.getBlockState(pos.below());
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.below()).isEmpty()) return;
        if (!(ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(BlockTags.SNOW))) return;
        for (ServerPlayer o : level.players()) if (o.distanceToSqr(x, y, z) < 24 * 24) return;
        // the 3x3-chunk area around the spot
        int cx = (x >> 4) << 4, cz = (z >> 4) << 4;
        AABB area = new AABB(cx - 16, y - 48, cz - 16, cx + 32, y + 48, cz + 32);
        List<ArkCreature> wild = level.getEntitiesOfClass(ArkCreature.class, area, c -> !c.isTamed());
        if (wild.size() >= ArkConfig.DENSITY.get()) return;
        Species s = pick(r);
        if (s == null) return;
        int group = s.pack ? 2 + r.nextInt(2) : 1;
        group = Math.min(group, ArkConfig.DENSITY.get() - wild.size());
        for (int i = 0; i < group; i++) {
            BlockPos at = i == 0 ? pos : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.offset(r.nextInt(7) - 3, 0, r.nextInt(7) - 3));
            if (!level.noCollision(ModEntities.type(s.id).getSpawnAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5))) continue;
            ModEntities.type(s.id).spawn(level, at, MobSpawnType.NATURAL);
        }
    }

    /** plant eaters and small animals are common, big meat eaters rare */
    private static Species pick(RandomSource r) {
        List<Species> all = SpeciesList.all();
        double total = 0;
        double[] w = new double[all.size()];
        for (int i = 0; i < all.size(); i++) {
            Species s = all.get(i);
            double k = s.diet == Diet.HERBIVORE ? 3.0 : 1.0;
            if (s.big()) k *= s.diet == Diet.HERBIVORE ? 0.6 : 0.3;
            w[i] = k;
            total += k;
        }
        double q = r.nextDouble() * total;
        for (int i = 0; i < all.size(); i++) {
            q -= w[i];
            if (q <= 0) return all.get(i);
        }
        return all.isEmpty() ? null : all.get(0);
    }

    /** a wild level: mostly 1-150 (low levels most common), rarer 150-300, very rare up to the maximum */
    public static int wildLevel(RandomSource r) {
        int max = ArkConfig.MAX_LEVEL.get();
        double high = ArkConfig.HIGH_LEVEL_CHANCE.get();
        int lvl;
        double q = r.nextDouble();
        if (q >= high) lvl = 1 + (int) Math.floor(Math.pow(r.nextDouble(), 1.6) * 149.0);
        else if (q >= high * 0.2) lvl = 150 + (int) (r.nextDouble() * 150);                    // 150-300
        else if (q >= high * 0.02) lvl = 300 + (int) (r.nextDouble() * 150);                   // 300-450
        else lvl = 450 + (int) (r.nextDouble() * 51);                                          // 450-500
        return Math.max(1, Math.min(max, lvl));
    }
}
