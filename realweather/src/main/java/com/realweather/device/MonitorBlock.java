package com.realweather.device;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Radar console: its screen shows the live picture of the nearest weather radar. Use it to open the full radar
 * display; sneak-use to switch the product on the screen (reflectivity, velocity, ZDR, CC).
 */
public class MonitorBlock extends BaseEntityBlock {
    public static final MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 3);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 2, 15, 9, 15), Block.box(1, 9, 8, 15, 16, 13));

    public MonitorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MODE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> rotateShape(2);
            case WEST -> rotateShape(3);
            case EAST -> rotateShape(1);
            default -> SHAPE;
        };
    }

    private static VoxelShape rotateShape(int quarter) {
        VoxelShape[] out = {SHAPE};
        for (int i = 0; i < quarter; i++) {
            VoxelShape[] next = {Shapes.empty()};
            out[0].forAllBoxes((x0, y0, z0, x1, y1, z1) -> next[0] = Shapes.or(next[0], Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1)));
            out[0] = next[0];
        }
        return out[0];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonitorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                int mode = (state.getValue(MODE) + 1) % 4;
                level.setBlockAndUpdate(pos, state.setValue(MODE, mode));
                player.displayClientMessage(Component.translatable("realweather.radar.mode." + mode), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            DeviceClientHooks.openMonitor(pos, state.getValue(MODE));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
