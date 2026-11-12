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
    public static final int CHAIR_KIND = 0, STOOL_KIND = 1, SOFA_KIND = 2, BED_KIND = 3, ARMCHAIR_KIND = 4, OFFICE_KIND = 5, TOILET_KIND = 6, BATH_KIND = 7;
    /** Seats can get dirty (spills, crumbs) and must be cleaned with a Mop or by a cleaner. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty DIRTY = net.minecraft.world.level.block.state.properties.BooleanProperty.create("dirty");
    private static final EnumMap<Direction, VoxelShape> ARMCHAIR = Shapes.of(bx(2, 0, 2, 14, 8, 14), bx(2, 8, 11, 14, 16, 14), bx(2, 8, 2, 4, 12, 11), bx(12, 8, 2, 14, 12, 11));
    private static final EnumMap<Direction, VoxelShape> OFFICE = Shapes.of(bx(3, 0, 3, 13, 9, 13), bx(3, 9, 11, 13, 18, 13));
    private static final EnumMap<Direction, VoxelShape> TOILET = Shapes.of(bx(4, 0, 2, 12, 8, 12), bx(3, 8, 11, 13, 16, 15));
    private static final EnumMap<Direction, VoxelShape> BATH = Shapes.of(bx(0, 0, 0, 16, 9, 16));
    private static final EnumMap<Direction, VoxelShape> BED = Shapes.of(bx(0, 0, 0, 16, 9, 16));
    public final boolean stool;
    public final int kind;

    public ChairBlock(Properties p, int kind) {
        super(p);
        this.kind = kind;
        this.stool = kind == STOOL_KIND;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DIRTY, false));
    }

    public double seatHeight() {
        return switch (kind) {
            case STOOL_KIND -> 0.75; case SOFA_KIND, ARMCHAIR_KIND -> 0.5; case BED_KIND -> 0.6; case TOILET_KIND -> 0.5; case BATH_KIND -> 0.25;
            default -> 0.56;
        };
    }

    /** How many people can use it at once (double beds: 2). */
    public int capacity() { return 1; }

    public static boolean isDirty(BlockState s) { return s.hasProperty(DIRTY) && s.getValue(DIRTY); }

    public static void setDirty(Level level, BlockPos pos, boolean dirty) {
        BlockState s = level.getBlockState(pos);
        if (s.hasProperty(DIRTY) && s.getValue(DIRTY) != dirty) {
            level.setBlock(pos, s.setValue(DIRTY, dirty), 3);
            if (!dirty) level.playSound(null, pos, com.rpbusiness.core.ModSounds.CLEAN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f);
        }
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                                    net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (stack.is(com.rpbusiness.core.ModItems.MOP.get()) && isDirty(state)) {
            if (!level.isClientSide) {
                setDirty(level, pos, false);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.rpbusiness.room_cleaned"), true);
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, DIRTY); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return (switch (kind) {
            case STOOL_KIND -> STOOL; case SOFA_KIND -> SOFA; case BED_KIND -> BED; case ARMCHAIR_KIND -> ARMCHAIR; case OFFICE_KIND -> OFFICE;
            case TOILET_KIND -> TOILET; case BATH_KIND -> BATH; default -> CHAIR;
        }).get(s.getValue(FACING));
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
