package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;

/** Decorative furniture (hotel rooms, lobbies, homes). Counts toward a business' ambience. */
public class DecorBlock extends HorizontalDirectionalBlock {
    private final EnumMap<Direction, VoxelShape> shape;
    private final boolean flat;

    public DecorBlock(Properties p, double[]... boxes) {
        super(p);
        this.shape = Shapes.of(boxes);
        double maxY = 0;
        for (double[] b : boxes) maxY = Math.max(maxY, b[4]);
        this.flat = maxY <= 1.01;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return shape.get(s.getValue(FACING)); }

    /** Rugs can be walked over; everything else is walked around. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) { return flat; }
}
