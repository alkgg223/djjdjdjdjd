package com.trafficcraft.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible, non-solid light source that removes itself when no headlight refreshes it. */
public class HeadlightGlowBlock extends Block {
    public HeadlightGlowBlock(Properties p) {
        super(p);
    }

    @Override
    protected RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    protected void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
        if (HeadlightLights.isFresh(level, pos)) level.scheduleTick(pos, this, 12);
        else HeadlightLights.removeBlock(level, pos);
    }
}
