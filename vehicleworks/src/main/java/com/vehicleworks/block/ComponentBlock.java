package com.vehicleworks.block;

import com.mojang.serialization.MapCodec;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.network.ServerActions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Base class of all engineering component blocks. */
public class ComponentBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<WingOrientation> PLANE = EnumProperty.create("plane", WingOrientation.class);

    private static final VoxelShape SEAT = Shapes.or(Block.box(1, 0, 1, 15, 8, 15), Block.box(1, 8, 12, 15, 18, 15));
    private static final VoxelShape WING_Y = Block.box(0, 6.5, 0, 16, 9.5, 16);
    private static final VoxelShape WING_X = Block.box(6.5, 0, 0, 9.5, 16, 16);
    private static final VoxelShape WING_Z = Block.box(0, 0, 6.5, 16, 16, 9.5);
    private static final VoxelShape SMALL = Block.box(2, 2, 2, 14, 14, 14);

    public final ComponentType type;

    public ComponentBlock(ComponentType type, Properties props) {
        super(props);
        this.type = type;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComponentBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (type.shape) {
            case SEAT -> SEAT;
            case WING -> switch (state.hasProperty(PLANE) ? state.getValue(PLANE) : WingOrientation.HORIZONTAL) {
                case VERTICAL_X -> WING_X;
                case VERTICAL_Z -> WING_Z;
                default -> WING_Y;
            };
            case DYNAMIC -> SMALL;
            default -> Shapes.block();
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            if (ServerActions.useComponentInWorld(sp, pos, state, this)) return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && ServerActions.useItemOnComponentInWorld(sp, pos, state, this, stack, hand)) {
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("vehicleworks.tooltip." + type.id).withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("vehicleworks.tooltip.mass", String.format("%.0f", type.massKg)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    // ------------------------------------------------------------------ orientation variants

    /** Component with a 6-way facing (engines, propellers, weapons). Faces where the player looks. */
    public static class Directional extends ComponentBlock {
        public Directional(ComponentType type, Properties props) {
            super(type, props);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            Direction d = ctx.getNearestLookingDirection();
            if (type == ComponentType.MAIN_ROTOR) d = ctx.getClickedFace();
            if (ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()) d = d.getOpposite();
            return defaultBlockState().setValue(FACING, d);
        }

        @Override
        protected BlockState rotate(BlockState state, Rotation rot) {
            return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
        }

        @Override
        protected BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }
    }

    /** Component with a horizontal facing (seats, core, wheels). Faces the player's look direction. */
    public static class Horizontal extends ComponentBlock {
        public Horizontal(ComponentType type, Properties props) {
            super(type, props);
            registerDefaultState(stateDefinition.any().setValue(HORIZONTAL_FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(HORIZONTAL_FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(HORIZONTAL_FACING, ctx.getHorizontalDirection());
        }

        @Override
        protected BlockState rotate(BlockState state, Rotation rot) {
            return state.setValue(HORIZONTAL_FACING, rot.rotate(state.getValue(HORIZONTAL_FACING)));
        }

        @Override
        protected BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(HORIZONTAL_FACING)));
        }
    }

    /** Wing plate: orientation chosen so the plate contains the span direction and the player's forward direction. */
    public static class Wing extends ComponentBlock {
        public Wing(ComponentType type, Properties props) {
            super(type, props);
            registerDefaultState(stateDefinition.any().setValue(PLANE, WingOrientation.HORIZONTAL));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(PLANE);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            Direction.Axis span = ctx.getClickedFace().getAxis();
            Direction.Axis fwd = ctx.getHorizontalDirection().getAxis();
            Direction.Axis normal;
            if (span == Direction.Axis.Y) {
                normal = fwd == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            } else if (span == fwd) {
                normal = Direction.Axis.Y;
            } else {
                normal = Direction.Axis.Y;
            }
            if (ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()) {
                normal = span == Direction.Axis.Y ? Direction.Axis.Y : (span == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            }
            return defaultBlockState().setValue(PLANE, WingOrientation.forNormal(normal));
        }

        @Override
        protected BlockState rotate(BlockState state, Rotation rot) {
            WingOrientation o = state.getValue(PLANE);
            if (o == WingOrientation.HORIZONTAL || rot == Rotation.NONE || rot == Rotation.CLOCKWISE_180) return state;
            return state.setValue(PLANE, o == WingOrientation.VERTICAL_X ? WingOrientation.VERTICAL_Z : WingOrientation.VERTICAL_X);
        }
    }
}
