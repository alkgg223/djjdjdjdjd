package com.arkragnarok.survivor;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.registry.ModEntities;
import com.arkragnarok.survivor.world.SurvivorWorld;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Brings fallen survivors back: a few seconds after dying a survivor wakes up in its own base (next to its bed, or
 * in its shelter), with the same name and look, and then goes back to where it died to collect its belongings.
 * Without a base it comes back near where it last lived.  It waits until its base is loaded.
 */
@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SurvivorRespawner {
    private static final Logger LOG = LogUtils.getLogger();

    private SurvivorRespawner() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        ServerLevel level = e.getServer().overworld();
        if (level.getGameTime() % 20 != 0) return;
        SurvivorWorld w = SurvivorWorld.get(level);
        for (SurvivorWorld.Tribe t : List.copyOf(w.all())) {
            if (t.respawnAt <= 0 || level.getGameTime() < t.respawnAt) continue;
            if (level.getEntity(t.id) != null) continue;                     // the body is still there
            BlockPos at = spawnPoint(level, t);
            if (at == null || !level.isLoaded(at) || !level.isPositionEntityTicking(at)) continue;
            Survivor sv = ModEntities.SURVIVOR.get().create(level);
            if (sv == null) continue;
            sv.setUUID(t.id);
            sv.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
            sv.restore(t);
            if (level.addFreshEntity(sv)) {
                t.respawnAt = 0;
                w.setDirty();
                LOG.info("Survivor {} ({}) wakes up again at {} and goes back for its things at {}", t.person, t.name, at, t.deathPos);
            }
        }
    }

    /** next to its bed, else in its shelter, else near where it lived */
    private static BlockPos spawnPoint(ServerLevel level, SurvivorWorld.Tribe t) {
        if (t.base != null) {
            List<BaseDesign.Step> plan = Builder.plan(t);
            for (int i = 0; i < plan.size(); i++) {
                BaseDesign.Step st = plan.get(i);
                if (st.kind() != BaseDesign.Kind.FURNITURE || !st.furniture().equals("bed") || t.built[i] == 0 || t.built[i] == Builder.SKIPPED) continue;
                BlockPos bed = Builder.at(t, st);
                if (level.isLoaded(bed) && level.getBlockState(bed).getBlock() instanceof BedBlock) {
                    BlockPos shelter = t.base.offset(BaseDesign.rotate(Builder.design(t).shelterOffset(), t.rotation));
                    if (level.isEmptyBlock(shelter) && level.isEmptyBlock(shelter.above())) return shelter;
                    return bed.above();
                }
            }
            BlockPos shelter = t.base.offset(BaseDesign.rotate(Builder.design(t).shelterOffset(), t.rotation));
            if (level.isLoaded(shelter) && level.isEmptyBlock(shelter) && level.isEmptyBlock(shelter.above())) return shelter;
            if (level.isLoaded(t.base)) return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, t.base);
            return t.base;
        }
        BlockPos near = t.deathPos != null ? t.deathPos.offset(level.random.nextInt(13) - 6, 0, level.random.nextInt(13) - 6) : t.lastPos;
        if (!level.isLoaded(near)) return near;
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near);
    }
}
