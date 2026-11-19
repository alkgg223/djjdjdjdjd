package com.trafficcraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Static block with a custom (non-rotating) shape. */
public class ShapeBlock extends Block {
    protected final VoxelShape shape;
    protected final boolean noCollide;

    public ShapeBlock(Properties p, double[][] boxes, boolean noCollide) {
        super(p);
        this.shape = ShapeUtil.shape(boxes, Direction.NORTH);
        this.noCollide = noCollide;
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return noCollide ? Shapes.empty() : shape;
    }
}
