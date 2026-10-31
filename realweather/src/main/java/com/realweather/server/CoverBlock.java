package com.realweather.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A layer left on the ground by a hailstorm: either loose hailstones (up to three thin layers, you walk
 * through them) or the packed, icy hail-snow they turn into when the hail keeps coming (up to eight layers,
 * like snow). Both melt away after the storm unless it is cold.
 */
public class CoverBlock extends Block {
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    private static final VoxelShape[] SNOW_SHAPES = new VoxelShape[9];
    private static final VoxelShape[] HAIL_SHAPES = new VoxelShape[9];

    static {
        for (int i = 0; i <= 8; i++) {
            SNOW_SHAPES[i] = Block.box(0, 0, 0, 16, i * 2, 16);
            HAIL_SHAPES[i] = Block.box(0, 0, 0, 16, Math.max(0.5, Math.min(3, i)), 16);
        }
    }

    /** Loose hailstones (true) or packed hail-snow (false). */
    public final boolean pellets;

    public CoverBlock(Properties properties, boolean pellets) {
        super(properties);
        this.pellets = pellets;
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
    }

    public int maxLayers() {
        return pellets ? 3 : 8;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return pellets ? HAIL_SHAPES[state.getValue(LAYERS)] : SNOW_SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (pellets) {
            return Shapes.empty();
        }
        return SNOW_SHAPES[state.getValue(LAYERS) - 1];
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getShape(state, level, pos, CollisionContext.empty());
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.LAND && (pellets || state.getValue(LAYERS) < 5);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.ICE) || below.is(Blocks.PACKED_ICE) || below.is(Blocks.BARRIER)) {
            return false;
        }
        if (below.getBlock() instanceof CoverBlock c && !c.pellets && below.getValue(LAYERS) == 8) {
            return true;
        }
        return Block.isFaceFull(below.getCollisionShape(level, pos.below()), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return pellets || state.getValue(LAYERS) == 1;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        HailManager.melt(level, pos, state, random);
    }
}
