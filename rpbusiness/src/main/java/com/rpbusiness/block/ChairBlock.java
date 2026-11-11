package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
import com.rpbusiness.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;

import static com.rpbusiness.block.Shapes.bx;

/** A chair: customers (and the player) really sit on it. Facing = the direction the sitter looks. */
public class ChairBlock extends HorizontalDirectionalBlock {
    private static final EnumMap<Direction, VoxelShape> CHAIR = Shapes.of(bx(2, 0, 2, 14, 9, 14), bx(2, 9, 12, 14, 20, 14));
    private static final EnumMap<Direction, VoxelShape> STOOL = Shapes.of(bx(3, 0, 3, 13, 12, 13));
    private static final EnumMap<Direction, VoxelShape> SOFA = Shapes.of(bx(0, 0, 1, 16, 8, 15), bx(0, 8, 11, 16, 16, 15), bx(0, 8, 1, 2, 12, 11), bx(14, 8, 1, 16, 12, 11));
    public static final int CHAIR_KIND = 0, STOOL_KIND = 1, SOFA_KIND = 2;
    public final boolean stool;
    public final int kind;

    public ChairBlock(Properties p, int kind) {
        super(p);
        this.kind = kind;
        this.stool = kind == STOOL_KIND;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public double seatHeight() { return kind == STOOL_KIND ? 0.75 : kind == SOFA_KIND ? 0.5 : 0.56; }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return (kind == STOOL_KIND ? STOOL : kind == SOFA_KIND ? SOFA : CHAIR).get(s.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() || player.isPassenger()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (SeatEntity.occupied(level, pos) || !com.rpbusiness.server.BusinessManager.isSeat(level, pos)) return InteractionResult.CONSUME;
            SeatEntity.sit(level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** NPCs must walk around furniture and equipment, never through it. */
    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) { return false; }
}
