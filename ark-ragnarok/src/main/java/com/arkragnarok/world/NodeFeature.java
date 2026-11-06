package com.arkragnarok.world;

import com.arkragnarok.entity.ResourceNode;
import com.arkragnarok.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** scatters resource nodes: boulders and metal rocks on the surface (more metal in the mountains), and metal,
 *  crystal, obsidian and rock on cave floors */
public class NodeFeature extends Feature<NoneFeatureConfiguration> {
    private final boolean cave;

    public NodeFeature(boolean cave) {
        super(NoneFeatureConfiguration.CODEC);
        this.cave = cave;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos.MutableBlockPos m = ctx.origin().mutable();
        if (cave) {
            for (int i = 0; i < 4 && !level.getBlockState(m).isAir(); i++) m.move(Direction.UP);
            for (int i = 0; i < 14 && level.getBlockState(m.below()).isAir(); i++) m.move(Direction.DOWN);
            if (!level.getBlockState(m).isAir() || !level.getBlockState(m.above()).isAir() || !level.getBlockState(m.above(2)).isAir()) return false;
        }
        if (!level.getFluidState(m).isEmpty() || !level.getBlockState(m.below()).isFaceSturdy(level, m.below(), Direction.UP)) return false;
        int y = m.getY();
        NodeKind kind;
        float k = r.nextFloat();
        if (cave) {
            kind = y < 0 ? (k < 0.38f ? NodeKind.METAL : k < 0.58f ? NodeKind.OBSIDIAN : k < 0.8f ? NodeKind.CRYSTAL : NodeKind.ROCK)
                    : (k < 0.42f ? NodeKind.METAL : k < 0.58f ? NodeKind.CRYSTAL : k < 0.63f ? NodeKind.OBSIDIAN : NodeKind.ROCK);
        } else {
            var biome = level.getBiome(m);
            boolean mountain = y > 105 || biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_BADLANDS);
            boolean cold = biome.value().coldEnoughToSnow(m);
            if (mountain) kind = k < 0.4f ? NodeKind.METAL : k < 0.47f && cold ? NodeKind.CRYSTAL : NodeKind.ROCK;
            else kind = k < 0.12f ? NodeKind.METAL : k < 0.135f ? NodeKind.OBSIDIAN : NodeKind.ROCK;
        }
        float s = r.nextFloat();
        int size = s < 0.42f ? 0 : s < 0.82f ? 1 : 2;
        ResourceNode node = ModEntities.RESOURCE_NODE.get().create(level.getLevel());
        if (node == null) return false;
        float yaw = r.nextFloat() * 360f;
        node.moveTo(m.getX() + 0.5, y - 0.15, m.getZ() + 0.5, yaw, 0f);
        node.setup(kind, size, yaw);
        level.addFreshEntity(node);
        return true;
    }
}
