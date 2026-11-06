package com.arkragnarok.building;

import com.arkragnarok.registry.ModBuilding;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of a 3x3 structure piece.  The block at the master part carries the sculpted 3D model (baked into the
 * chunk, see the blockstate), the others are invisible and only give the collision of the real shape: a full block
 * for foundations, a thin plank / stone panel for walls, a thin top slab for ceilings, the door leaf in a doorframe.
 * Breaking any block breaks the whole piece and gives the piece back.
 *
 * Layout: flat pieces part = (dx + 1) + 3 (dz + 1); walls part = (c + 1) + 3 r with c along facing.getClockWise()
 * and r the row from the bottom.  A wall's FACING is its outer side; walls stand just outside the foundation edge with
 * the panel on their inner face, so ceilings (3x3 at the walls' top row) fit between them and act as the next floor.
 */
public class PieceBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<PieceType> PIECE = EnumProperty.create("piece", PieceType.class);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    public static final EnumProperty<DoorState> DOOR = EnumProperty.create("door", DoorState.class);

    private static final VoxelShape CEIL = Block.box(0, 12, 0, 16, 16, 16);
    private static final VoxelShape[] PANEL = new VoxelShape[4];   // index = facing 2D data value
    private static final VoxelShape[] LEAF = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int i = d.get2DDataValue();
            PANEL[i] = switch (d) {          // the panel lies on the inner side (opposite the facing)
                case NORTH -> Block.box(0, 0, 12, 16, 16, 16);
                case SOUTH -> Block.box(0, 0, 0, 16, 16, 4);
                case EAST -> Block.box(0, 0, 0, 4, 16, 16);
                default -> Block.box(12, 0, 0, 16, 16, 16);
            };
            LEAF[i] = switch (d) {
                case NORTH -> Block.box(0, 0, 12.5, 16, 16, 15.5);
                case SOUTH -> Block.box(0, 0, 0.5, 16, 16, 3.5);
                case EAST -> Block.box(0.5, 0, 0, 3.5, 16, 16);
                default -> Block.box(12.5, 0, 0, 15.5, 16, 16);
            };
        }
    }

    public final Tier tier;
    private static boolean removing;

    public PieceBlock(Tier tier, Properties props) {
        super(props);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PIECE, PieceType.WALL).setValue(PART, 1)
                .setValue(DOOR, DoorState.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PIECE, PART, DOOR);
    }

    // ---------------------------------------------------------------------------------------------- layout
    public static boolean isOpening(BlockState s) {
        return s.getValue(PIECE) == PieceType.DOORFRAME && (s.getValue(PART) == 1 || s.getValue(PART) == 4);
    }

    public static BlockPos master(BlockPos pos, BlockState s) {
        int part = s.getValue(PART);
        if (s.getValue(PIECE).flat()) return pos.offset(-(part % 3 - 1), 0, -(part / 3 - 1));
        Direction right = s.getValue(FACING).getClockWise();
        return pos.relative(right, -(part % 3 - 1)).below(part / 3);
    }

    /** every block position of a piece and its part index */
    public static List<BlockPos> parts(BlockPos master, PieceType type, Direction facing, List<Integer> partOut) {
        List<BlockPos> out = new ArrayList<>();
        for (int part = 0; part < 9; part++) {
            BlockPos p;
            if (type.flat()) p = master.offset(part % 3 - 1, 0, part / 3 - 1);
            else p = master.relative(facing.getClockWise(), part % 3 - 1).above(part / 3);
            out.add(p);
            if (partOut != null) partOut.add(part);
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------- shapes
    private VoxelShape shape(BlockState s, boolean collision) {
        PieceType t = s.getValue(PIECE);
        int f = s.getValue(FACING).get2DDataValue();
        return switch (t) {
            case FOUNDATION -> Shapes.block();
            case CEILING -> CEIL;
            case WALL -> PANEL[f];
            case DOORFRAME -> {
                if (!isOpening(s)) yield PANEL[f];
                DoorState d = s.getValue(DOOR);
                if (d == DoorState.NONE) yield collision ? Shapes.empty() : Block.box(6, 0, 6, 10, 1, 10);
                if (d == DoorState.OPEN && collision) yield Shapes.empty();
                yield LEAF[f];
            }
        };
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(s, false);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(s, true);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState s, BlockGetter level, BlockPos pos) {
        return s.getValue(PIECE) != PieceType.FOUNDATION;
    }

    // ---------------------------------------------------------------------------------------------- doors
    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!isOpening(s) || s.getValue(DOOR) == DoorState.NONE) return InteractionResult.PASS;
        if (!level.isClientSide) toggleDoor(level, master(pos, s));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public void toggleDoor(Level level, BlockPos master) {
        BlockState ms = level.getBlockState(master);
        if (!(ms.getBlock() instanceof PieceBlock) || ms.getValue(PIECE) != PieceType.DOORFRAME) return;
        boolean open = ms.getValue(DOOR) != DoorState.OPEN;
        setDoor(level, master, ms, open ? DoorState.OPEN : DoorState.CLOSED);
        level.playSound(null, master, open ? tier.open : tier.close, SoundSource.BLOCKS, 1.0f, 0.9f + level.random.nextFloat() * 0.1f);
    }

    public static void setDoor(Level level, BlockPos master, BlockState ms, DoorState d) {
        level.setBlock(master, ms.setValue(DOOR, d), 3);
        BlockPos up = master.above();
        BlockState us = level.getBlockState(up);
        if (us.getBlock() instanceof PieceBlock && us.getValue(PIECE) == PieceType.DOORFRAME) level.setBlock(up, us.setValue(DOOR, d), 3);
    }

    // ---------------------------------------------------------------------------------------------- breaking
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState s, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            BlockPos m = master(pos, s);
            BlockState ms = level.getBlockState(m);
            ItemStack drop = ModBuilding.pieceItem(tier, s.getValue(PIECE));
            if (!drop.isEmpty()) Block.popResource(level, m, drop);
            if (ms.getBlock() instanceof PieceBlock && isOpening(ms) && ms.getValue(DOOR) != DoorState.NONE) {
                PieceBlockEntity be = level.getBlockEntity(m) instanceof PieceBlockEntity p ? p : null;
                Tier dt = be != null && be.doorTier() != null ? be.doorTier() : tier;
                Block.popResource(level, m, ModBuilding.doorItem(dt));
            }
        }
        return super.playerWillDestroy(level, pos, s, player);
    }

    @Override
    protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!removing && !newState.is(this) && !level.isClientSide) {
            removing = true;
            try {
                BlockPos m = master(pos, s);
                for (BlockPos p : parts(m, s.getValue(PIECE), s.getValue(FACING), null)) {
                    if (!p.equals(pos) && level.getBlockState(p).is(this)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            } finally {
                removing = false;
            }
        }
        super.onRemove(s, level, pos, newState, moved);
    }

    // ---------------------------------------------------------------------------------------------- door animation
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState s) {
        return s.getValue(PIECE) == PieceType.DOORFRAME && s.getValue(PART) == 1 ? new PieceBlockEntity(pos, s) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        if (!level.isClientSide || type != ModBuilding.PIECE_BE.get()) return null;
        return (lvl, pos, st, be) -> ((PieceBlockEntity) be).clientTick(st);
    }
}
