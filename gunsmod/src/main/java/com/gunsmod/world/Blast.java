package com.gunsmod.world;

import com.gunsmod.net.Net;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Explosions with the client-side blast effects, scorched craters and pressure waves. */
public final class Blast {
    private Blast() {}

    /** effect styles understood by the client (ClientFx.blast) */
    public static final int FX_SMALL = 0, FX_MISSILE = 1, FX_FIRE = 2, FX_HEAVY = 3, FX_TITAN = 4, FX_ERUPTION = 5;

    /** a vanilla explosion plus its visual blast */
    public static void explode(Level level, @Nullable Entity source, Vec3 at, float power, boolean fire, Level.ExplosionInteraction mode, int fx, float fxSize) {
        level.explode(source, at.x, at.y, at.z, power, fire, mode);
        fx(level, at, fx, fxSize);
    }

    /** send the blast effect to everyone who can see or hear it */
    public static void fx(Level level, Vec3 at, int style, float size) {
        if (level instanceof ServerLevel sl) {
            double range = Math.min(512, 96 + size * 80);
            PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, range, new Net.ExplosionFx(at, style, size));
        }
    }

    /** blacken and burn the ground around a crater */
    public static void scorch(Level level, Vec3 at, float radius, boolean fire, boolean molten) {
        RandomSource r = level.random;
        int R = Mth.ceil(radius);
        int cx = Mth.floor(at.x), cz = Mth.floor(at.z);
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) / radius;
                if (d > 1 || r.nextFloat() > 1.05 - d * 0.7) continue;
                int x = cx + dx, z = cz + dz;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                if (Math.abs(top - at.y) > radius) continue;
                BlockPos p = new BlockPos(x, top, z);
                BlockState st = level.getBlockState(p);
                if (st.is(BlockTags.LEAVES) || st.is(BlockTags.FLOWERS) || st.is(BlockTags.REPLACEABLE)) {
                    level.removeBlock(p, false);
                    p = p.below();
                    st = level.getBlockState(p);
                }
                BlockState burnt = burnt(st, d, molten, r);
                if (burnt != null) level.setBlock(p, burnt, Block.UPDATE_ALL);
                BlockPos above = p.above();
                if (fire && r.nextFloat() < 0.35f * (1 - d) + 0.05f && level.getBlockState(above).isAir()
                        && BaseFireBlock.canBePlacedAt(level, above, Direction.UP)) {
                    level.setBlockAndUpdate(above, BaseFireBlock.getState(level, above));
                }
            }
        }
    }

    @Nullable
    private static BlockState burnt(BlockState st, double d, boolean molten, RandomSource r) {
        boolean soil = st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.PODZOL) || st.is(Blocks.ROOTED_DIRT)
                || st.is(Blocks.DIRT_PATH) || st.is(Blocks.COARSE_DIRT) || st.is(Blocks.MUD);
        boolean rock = st.is(Blocks.STONE) || st.is(Blocks.COBBLESTONE) || st.is(Blocks.ANDESITE) || st.is(Blocks.GRAVEL);
        if (soil) {
            if (molten && d < 0.25 && r.nextFloat() < 0.3f) return Blocks.MAGMA_BLOCK.defaultBlockState();
            if (d < 0.45 && r.nextFloat() < 0.45f) return Blocks.BLACKSTONE.defaultBlockState();
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (rock && d < 0.6 && r.nextFloat() < 0.5f) return molten && d < 0.2 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        if (st.is(Blocks.SAND) && d < 0.5 && r.nextFloat() < 0.4f) return Blocks.GLASS.defaultBlockState();
        return null;
    }

    /** pressure wave: throws, hurts and (near the centre) ignites everything around, walls do not stop it */
    public static void shockwave(Level level, Vec3 at, double radius, float maxDamage, int igniteSeconds, @Nullable Entity source, @Nullable Entity owner) {
        AABB box = new AABB(at, at).inflate(radius);
        for (Entity e : level.getEntities(source, box, e -> e.isAlive() && !e.isSpectator())) {
            Vec3 d = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(at);
            double dist = d.length();
            if (dist > radius) continue;
            double f = 1 - dist / radius;
            Vec3 push = dist > 0.01 ? d.scale(1 / dist) : new Vec3(0, 1, 0);
            if (e instanceof Player p && p.getAbilities().instabuild) continue;
            e.push(push.x * f * 2.6, 0.25 + f * 0.9, push.z * f * 2.6);
            e.hurtMarked = true;
            if (e instanceof LivingEntity le) {
                le.hurt(level.damageSources().explosion(source, owner), (float) (maxDamage * f * f));
                if (igniteSeconds > 0 && f > 0.5) le.igniteForSeconds(igniteSeconds);
            }
        }
    }

    /** scatter fires over the surface around a point */
    public static void firestorm(Level level, Vec3 at, float radius, int count) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Mth.TWO_PI, rr = Math.sqrt(r.nextDouble()) * radius;
            int x = Mth.floor(at.x + Math.cos(a) * rr), z = Mth.floor(at.z + Math.sin(a) * rr);
            BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            if (level.getBlockState(p).isAir() && BaseFireBlock.canBePlacedAt(level, p, Direction.UP)) {
                level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
            }
        }
    }
}
