package com.gunsmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Sandbags (kind 0), low sandbags (1) and concrete jersey barriers (2). */
public class CoverBlock extends FacingBlock {
    private final int kind;

    public CoverBlock(Properties props, int kind) {
        super(props, shape(kind));
        this.kind = kind;
    }

    private static VoxelShape shape(int kind) {
        return switch (kind) {
            case 1 -> box(0, 0, 1.5, 16, 8, 14.5);
            case 2 -> Shapes.or(box(0, 0, 1, 16, 3, 15), box(0, 3, 3, 16, 16, 13));
            default -> box(0, 0, 1.5, 16, 16, 14.5);
        };
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        int k = kind;
        return simpleCodec(p -> new CoverBlock(p, k));
    }
}
