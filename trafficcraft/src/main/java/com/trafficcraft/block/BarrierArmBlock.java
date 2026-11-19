package com.trafficcraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible collision of a lowered barrier arm (the arm itself is drawn by the gate's renderer). */
public class BarrierArmBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 13, 0, 16, 17, 16);
    private static final VoxelShape FULL = Block.box(0, 0, 0, 16, 17, 16);

    public BarrierArmBlock(Properties p) {
        super(p);
    }

    @Override
    protected RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FULL;
    }
}
