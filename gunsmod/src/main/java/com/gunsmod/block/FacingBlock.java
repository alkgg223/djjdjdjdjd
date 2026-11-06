package com.gunsmod.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Horizontal block whose model faces north by default; shapes are given for north and rotated. */
public class FacingBlock extends HorizontalDirectionalBlock {
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public FacingBlock(Properties props) {
        this(props, Shapes.block());
    }

    public FacingBlock(Properties props, VoxelShape north) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, rotate(north, d));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(FacingBlock::new);
    }

    /** rotate a shape authored for NORTH to face d */
    public static VoxelShape rotate(VoxelShape shape, Direction d) {
        VoxelShape[] out = {Shapes.empty()};
        int times = (d.get2DDataValue() - Direction.NORTH.get2DDataValue() + 4) % 4;
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double ax0 = x0, az0 = z0, ax1 = x1, az1 = z1;
            for (int i = 0; i < times; i++) {
                double nx0 = 1 - az1, nz0 = ax0, nx1 = 1 - az0, nz1 = ax1;
                ax0 = nx0;
                az0 = nz0;
                ax1 = nx1;
                az1 = nz1;
            }
            out[0] = Shapes.or(out[0], Shapes.box(Math.min(ax0, ax1), y0, Math.min(az0, az1), Math.max(ax0, ax1), y1, Math.max(az0, az1)));
        });
        return out[0].optimize();
    }

    public static VoxelShape box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Block.box(x0, y0, z0, x1, y1, z1);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes.get(state.getValue(FACING));
    }
}
