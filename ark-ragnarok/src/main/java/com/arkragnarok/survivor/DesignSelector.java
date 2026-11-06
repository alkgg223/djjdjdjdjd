package com.arkragnarok.survivor;

import com.arkragnarok.survivor.world.SurvivorWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Chooses where a survivor settles and which blueprint it builds there, like a person would: it looks at the places
 * it has seen (flat ground, water, trees, caves, metal, predators, rival bases), reads the land of each candidate
 * (how uneven it is, the biome and climate, the altitude, whether anything built by someone else is in the way) and
 * then weighs every design against the site, its own temperament, its civilisation stage and its dinosaurs.
 */
final class DesignSelector {
    private DesignSelector() {}

    record Choice(BlockPos anchor, int design, int rotation, double score) {}

    private static final int R = 36, STEP = 2, N = R / STEP * 2 + 1;

    /** what a candidate site is like */
    private static final class Site {
        BlockPos anchor;
        final int[] h = new int[N * N];            // surface height per sampled column (anchor-relative), Integer.MIN_VALUE unknown
        final boolean[] blocked = new boolean[N * N];
        int water, trees, caves, metal, rock, threats, rivals;
        boolean desert, forest, mountain, coast, cold;
    }

    @Nullable
    static Choice choose(Survivor s, SurvivorWorld.Tribe t, SurvivorWorld world) {
        ServerLevel level = (ServerLevel) s.level();
        Personality p = t.personality();
        Random rnd = new Random(t.id.getMostSignificantBits() ^ level.getGameTime() / 24000);
        List<BlockPos> cands = new ArrayList<>();
        cands.add(s.blockPosition());
        List<Memory.Fact> flats = new ArrayList<>(s.memory.all(Memory.Kind.FLAT));
        flats.sort((a, b) -> Double.compare(a.pos().distToCenterSqr(s.position()), b.pos().distToCenterSqr(s.position())));
        for (Memory.Fact f : flats) {
            if (cands.size() >= 7) break;
            if (f.pos().distToCenterSqr(s.position()) < 70 * 70) cands.add(f.pos());
        }
        for (int i = 0; i < 4; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 12 + rnd.nextDouble() * 30;
            cands.add(s.blockPosition().offset((int) (Math.cos(a) * r), 0, (int) (Math.sin(a) * r)));
        }
        Choice best = null;
        for (BlockPos c : cands) {
            Site site = read(s, level, world, t, c);
            if (site == null) continue;
            for (BaseDesign d : BaseDesign.all()) {
                if (t.manualDesign && d.id != t.design) continue;
                for (int rot = 0; rot < 4; rot++) {
                    double sc = score(s, t, p, site, d, rot, rnd);
                    if (sc == Double.NEGATIVE_INFINITY) continue;
                    if (best == null || sc > best.score()) best = new Choice(site.anchor, d.id, rot, sc);
                }
            }
        }
        return best;
    }

    @Nullable
    private static Site read(Survivor s, ServerLevel level, SurvivorWorld world, SurvivorWorld.Tribe t, BlockPos c) {
        if (!level.isLoaded(c)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int g = 0; g < 12; g++) {
            BlockState under = level.getBlockState(new BlockPos(c.getX(), y - 1, c.getZ()));
            if (!under.is(net.minecraft.tags.BlockTags.LOGS) && !under.is(net.minecraft.tags.BlockTags.LEAVES)) break;
            y--;
        }
        BlockPos anchor = new BlockPos(c.getX(), y, c.getZ());
        if (!level.getFluidState(anchor).isEmpty() || !level.getFluidState(anchor.below()).isEmpty()) return null;
        // not inside or next to another tribe's base
        for (SurvivorWorld.Tribe o : world.all()) {
            if (o == t || o.base == null) continue;
            if (o.base.distSqr(anchor) < 50 * 50) return null;
        }
        Site st = new Site();
        st.anchor = anchor;
        int unknown = 0;
        for (int i = 0; i < N; i++) {
            for (int k = 0; k < N; k++) {
                int x = c.getX() - R + i * STEP, z = c.getZ() - R + k * STEP;
                BlockPos col = new BlockPos(x, y, z);
                int idx = i * N + k;
                if (!level.isLoaded(col)) {
                    st.h[idx] = Integer.MIN_VALUE;
                    unknown++;
                    continue;
                }
                int hy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int g = 0; g < 12; g++) {                       // tree trunks are cut, they are not the ground
                    BlockState under = level.getBlockState(new BlockPos(x, hy - 1, z));
                    if (!under.is(net.minecraft.tags.BlockTags.LOGS) && !under.is(net.minecraft.tags.BlockTags.LEAVES)) break;
                    hy--;
                }
                st.h[idx] = hy - y;
                for (int dy = -1; dy <= 3 && !st.blocked[idx]; dy++) {
                    BlockState bs = level.getBlockState(new BlockPos(x, y + dy, z));
                    if (!bs.isAir() && !Builder.natural(bs) && bs.getFluidState().isEmpty()) st.blocked[idx] = true;
                }
                BlockState top = level.getBlockState(new BlockPos(x, hy - 1, z));
                if (!top.getFluidState().isEmpty()) st.water++;
            }
        }
        if (unknown > N * N / 2) return null;
        Vec3 cv = Vec3.atCenterOf(anchor);
        for (Memory.Fact f : s.memory.all(Memory.Kind.WATER)) if (f.pos().distToCenterSqr(cv) < 30 * 30) st.water += 2;
        for (Memory.Fact f : s.memory.all(Memory.Kind.TREE)) if (f.pos().distToCenterSqr(cv) < 40 * 40) st.trees++;
        for (Memory.Fact f : s.memory.all(Memory.Kind.CAVE)) if (f.pos().distToCenterSqr(cv) < 45 * 45) st.caves++;
        for (Memory.Fact f : s.memory.all(Memory.Kind.NODE)) {
            if (f.pos().distToCenterSqr(cv) > 70 * 70) continue;
            if (f.detail().equals("metal") || f.detail().equals("crystal") || f.detail().equals("obsidian")) st.metal++;
            else st.rock++;
        }
        for (Memory.Fact f : s.memory.all(Memory.Kind.THREAT)) if (f.pos().distToCenterSqr(cv) < 60 * 60) st.threats++;
        for (Memory.Fact f : s.memory.all(Memory.Kind.BASE)) if (f.pos().distToCenterSqr(cv) < 120 * 120) st.rivals++;
        for (SurvivorWorld.Tribe o : world.all()) if (o != t && o.base != null && o.base.distSqr(anchor) < 150 * 150) st.rivals += 3;
        Holder<Biome> b = level.getBiome(anchor);
        st.desert = b.is(net.neoforged.neoforge.common.Tags.Biomes.IS_DESERT) || b.is(BiomeTags.IS_BADLANDS)
                || b.is(net.neoforged.neoforge.common.Tags.Biomes.IS_SANDY);
        st.forest = b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA) || b.is(BiomeTags.IS_JUNGLE) || st.trees >= 8;
        st.mountain = b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_HILL) || y > level.getSeaLevel() + 32;
        st.coast = b.is(BiomeTags.IS_BEACH) || b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_RIVER) || st.water > 12;
        st.cold = b.value().coldEnoughToSnow(anchor);
        return st;
    }

    private static double score(Survivor s, SurvivorWorld.Tribe t, Personality p, Site site, BaseDesign d, int rot, Random rnd) {
        int[] b = d.bounds(rot);
        boolean digs = d.archetype == BaseDesign.Archetype.UNDERGROUND || d.archetype == BaseDesign.Archetype.CAVE;
        // land: how much digging and filling the footprint needs, and nothing of anybody else in the way
        double uneven = 0;
        int samples = 0;
        for (int x = b[0]; x <= b[1]; x += STEP) {
            for (int z = b[2]; z <= b[3]; z += STEP) {
                int i = Math.floorDiv(x + R, STEP), k = Math.floorDiv(z + R, STEP);
                if (i < 0 || k < 0 || i >= N || k >= N) return Double.NEGATIVE_INFINITY;
                int idx = i * N + k;
                if (site.h[idx] == Integer.MIN_VALUE) return Double.NEGATIVE_INFINITY;
                if (site.blocked[idx]) return Double.NEGATIVE_INFINITY;
                int dh = site.h[idx];
                uneven += dh > 0 ? Math.min(dh, 8) * (digs ? 0.3 : 1.0) : Math.min(-dh, 6) * 1.4;
                samples++;
            }
        }
        if (samples == 0) return Double.NEGATIVE_INFINITY;
        double perCol = uneven / samples;
        if (perCol > (digs ? 4.0 : 1.6)) return Double.NEGATIVE_INFINITY;
        double sc = -perCol * 22 - Math.sqrt(site.anchor.distSqr(s.blockPosition())) * 0.15;
        // the archetype's preferred site
        BaseDesign.Archetype a = d.archetype;
        sc += switch (a.site) {
            case "mountain" -> site.mountain ? 26 : -18;
            case "forest" -> site.forest ? 24 : -14;
            case "coast" -> site.coast ? 26 : -22;
            case "desert" -> site.desert ? 30 : -24;
            case "cave" -> site.caves > 0 || site.mountain ? 18 : -16;
            case "metal" -> site.metal > 0 ? 14 + 4 * Math.min(4, site.metal) : -14;
            case "flat" -> perCol < 0.6 ? 8 : 0;
            default -> 2;
        };
        if (site.desert && (a == BaseDesign.Archetype.DESERT || a == BaseDesign.Archetype.UNDERGROUND)) sc += 8;   // shade
        if (site.cold && (a == BaseDesign.Archetype.UNDERGROUND || a == BaseDesign.Archetype.MOUNTAIN || a == BaseDesign.Archetype.STONE_FORT)) sc += 6;
        if (site.trees > 4) sc += 4;
        if (site.water > 0) sc += 5;
        if (site.rock > 0) sc += 3;
        // its temperament
        int pieces = d.pieceCount();
        double size = pieces / 60.0;
        sc += (p.ambition() - 0.45) * size * 14;
        sc -= (1 - p.ambition()) * size * 4;
        if (d.walled()) sc += (p.caution() - 0.4) * 30 + site.threats * 3 * p.caution() + site.rivals * 2.5;
        sc += d.towerCount() * (p.caution() * 3 + p.aggression() * 2 + site.rivals * 0.8);
        switch (a) {
            case MILITARY, GIANT_DEFENSE -> sc += (p.aggression() - 0.45) * 32 + site.threats * 2;
            case CASTLE -> sc += (p.ambition() + p.aggression() - 0.9) * 18;
            case STONE_FORT, UNDERGROUND -> sc += (p.caution() - 0.45) * 26;
            case MINING, INDUSTRIAL -> sc += (p.greed() + p.craft() - 0.9) * 20;
            case TRADE, FOREST_VILLAGE -> sc += (p.leadership() - 0.45) * 26;
            case RESEARCH, ADVANCED -> sc += (p.intelligence() - 0.45) * 26 + (t.stage - 1) * 3;
            case TAMING, BREEDING -> sc += (p.courage() - 0.45) * 22 + Math.min(4, t.tames.size()) * 8;
            case FARM -> sc += (1 - p.aggression()) * 10 + (site.water > 0 ? 6 : 0);
            default -> sc += 0;
        }
        sc += Math.min(4, t.tames.size()) * d.penCells() * 1.5;
        if (site.threats > 2 && !d.walled()) sc -= site.threats * 3 * p.caution();
        // door towards water or trees when it knows where they are, otherwise anywhere
        sc += rot == preferredRotation(s, site) ? 3 : 0;
        // nobody plans exactly alike
        Random r = new Random(t.id.getLeastSignificantBits() * 31 + d.id * 977L + rot);
        sc += r.nextDouble() * 16;
        return sc;
    }

    private static int preferredRotation(Survivor s, Site site) {
        Memory.Fact w = s.memory.nearest(Memory.Kind.WATER, Vec3.atCenterOf(site.anchor), 60);
        if (w == null) w = s.memory.nearest(Memory.Kind.TREE, Vec3.atCenterOf(site.anchor), 60);
        if (w == null) return 0;
        int dx = w.pos().getX() - site.anchor.getX(), dz = w.pos().getZ() - site.anchor.getZ();
        // rotation r turns the north entrance to: 0 north, 1 east, 2 south, 3 west
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 1 : 3;
        return dz > 0 ? 2 : 0;
    }
}
