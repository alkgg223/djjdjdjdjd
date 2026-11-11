package com.rpbusiness.block;

import com.mojang.serialization.MapCodec;
import com.rpbusiness.server.BusinessManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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

/** Facing block with a custom shape; functional kinds open business interfaces. */
public class ShapedBlock extends HorizontalDirectionalBlock {
    public enum Kind {
        COUNTER(Shapes.of(bx(0, 0, 0, 16, 16, 16))),
        LAMP(Shapes.of(bx(4, 4, 4, 12, 11, 12), bx(7.5, 11, 7.5, 8.5, 16, 8.5))),
        MENU_BOARD(Shapes.of(bx(1, 0, 6, 15, 16, 10))),
        PLANT(Shapes.of(bx(4, 0, 4, 12, 15, 12))),
        REGISTER(Shapes.of(bx(2, 0, 3, 14, 9, 14))),
        TERMINAL(Shapes.of(bx(3, 0, 4, 13, 16, 12))),
        COMPUTER(Shapes.of(bx(1, 0, 4, 15, 12, 14))),
        SUPPLIER(Shapes.of(bx(3, 0, 4, 13, 16, 12))),
        BANK(Shapes.of(bx(0, 0, 0, 16, 16, 16))),
        BANK_COMPUTER(Shapes.of(bx(1, 0, 4, 15, 12, 14))),
        ATM(Shapes.of(bx(1, 0, 3, 15, 16, 14))),
        DELIVERY(Shapes.of(bx(1, 0, 4, 15, 12, 14)));

        final EnumMap<Direction, VoxelShape> shapes;
        Kind(EnumMap<Direction, VoxelShape> s) { shapes = s; }
    }

    public final Kind kind;

    public ShapedBlock(Properties p, Kind kind) {
        super(p);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return MapCodec.unit(this); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return kind.shapes.get(s.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        switch (kind) {
            case REGISTER, TERMINAL, COMPUTER, SUPPLIER, MENU_BOARD, BANK, BANK_COMPUTER, ATM, DELIVERY -> {
                if (!level.isClientSide && player instanceof ServerPlayer sp) BusinessManager.useFunctionalBlock(sp, pos, kind);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            default -> { return InteractionResult.PASS; }
        }
    }

    /** NPCs must walk around furniture and equipment, never through it. */
    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) { return false; }
}
