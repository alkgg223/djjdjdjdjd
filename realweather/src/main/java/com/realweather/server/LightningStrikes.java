package com.realweather.server;

import com.realweather.LightningKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * What each kind of lightning does where it lands: orange is the weakest, then green, white and purple. Stronger bolts
 * hit harder; white ones start a little more fire round the strike, and purple ones scorch everything close by and set
 * several fires.
 */
public final class LightningStrikes {
    private record Pending(ServerLevel level, Vec3 pos, int kind, net.minecraft.world.entity.Entity cause) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    /** Bolts this mod made visual-only (flashes without a strike): they set no fire. */
    private static final java.util.Set<java.util.UUID> VISUAL = new java.util.HashSet<>();

    private LightningStrikes() {}

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof LightningBolt bolt)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        int kind = LightningKind.of(bolt.getUUID());
        bolt.setDamage(LightningKind.DAMAGE[kind]);
        boolean visual = VISUAL.remove(bolt.getUUID());
        if (VISUAL.size() > 256) {
            VISUAL.clear();
        }
        if (!visual && kind >= LightningKind.WHITE) {
            // The world is changed on the next tick, not while the bolt is being added.
            PENDING.add(new Pending(level, bolt.position(), kind, bolt));
        }
    }

    /** Called every server tick. */
    static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Pending> now = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending p : now) {
            effects(p);
        }
    }

    private static void effects(Pending p) {
        ServerLevel level = p.level;
        RandomSource rng = level.random;
        boolean purple = p.kind == LightningKind.PURPLE;
        if (level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            int fires = purple ? 5 : 2;
            int reach = purple ? 3 : 2;
            BlockPos base = BlockPos.containing(p.pos);
            for (int i = 0; i < fires; i++) {
                BlockPos at = base.offset(rng.nextInt(reach * 2 + 1) - reach, rng.nextInt(3) - 1, rng.nextInt(reach * 2 + 1) - reach);
                if (!level.isLoaded(at)) {
                    continue;
                }
                BlockState fire = BaseFireBlock.getState(level, at);
                if (level.getBlockState(at).isAir() && fire.canSurvive(level, at)) {
                    level.setBlockAndUpdate(at, fire);
                }
            }
        }
        if (purple) {
            // The blast of a very powerful stroke burns everything near it, not only what it struck.
            AABB box = new AABB(p.pos, p.pos).inflate(4.5, 3, 4.5);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
                double d = e.position().distanceTo(p.pos);
                if (d < 4.5) {
                    e.hurt(level.damageSources().lightningBolt(), (float) (6.0 * (1.0 - d / 5.0)) + 1F);
                    e.igniteForSeconds(4F);
                }
            }
        }
    }

    /** Makes a bolt visual-only or not (call after {@link #setKind}, which changes its UUID). */
    public static void setVisual(LightningBolt bolt, boolean visual) {
        bolt.setVisualOnly(visual);
        if (visual) {
            VISUAL.add(bolt.getUUID());
        }
    }

    /** A bolt of the given kind (natural storms pick the kind from how strong the storm is). */
    public static void setKind(LightningBolt bolt, int kind, RandomSource rng) {
        bolt.setUUID(LightningKind.withKind(bolt.getUUID(), kind, rng));
    }

    /** A real bolt of the given kind at a point on the ground (for the command). */
    public static boolean strike(ServerLevel level, double x, double z, int kind) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return false;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, 0, z));
        bolt.moveTo(Vec3.atBottomCenterOf(top));
        setKind(bolt, kind, level.random);
        level.addFreshEntity(bolt);
        return true;
    }
}
