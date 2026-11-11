package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;

import static com.rpbusiness.block.Shapes.bx;

public class StorageBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final int FULL = 0, CRATE = 1, PACKAGE = 2, FREEZER = 3;
    @SuppressWarnings("unchecked")
    private static final EnumMap<Direction, VoxelShape>[] SHAPES = new EnumMap[]{
            Shapes.of(bx(0, 0, 0, 16, 16, 16)), Shapes.of(bx(1, 0, 1, 15, 14, 15)),
            Shapes.of(bx(3, 0, 3, 13, 8, 13)), Shapes.of(bx(0, 0, 1, 16, 14, 15))};
    public final int size, shape;

    public StorageBlock(Properties p, int size, int shape) {
        super(p);
        this.size = size;
        this.shape = shape;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public boolean animated() { return false; }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPES[shape].get(s.getValue(FACING)); }

    @Override
    protected RenderShape getRenderShape(BlockState s) { return animated() ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return animated() ? new AnimatedStorageBE(pos, state) : new StorageBE(pos, state);
    }

    protected BlockPos mainPos(BlockState state, BlockPos pos) { return pos; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(mainPos(state, pos)) instanceof StorageBE be) player.openMenu(be);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof StorageBE be) {
            Containers.dropContents(level, pos, be);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !animated()) return null;
        return (l, p, s, be) -> { if (be instanceof AnimatedStorageBE a) a.serverTick(); };
    }

    /** NPCs must walk around furniture and equipment, never through it. */
    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) { return false; }
}
