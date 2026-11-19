package com.vehicleworks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Aircraft skin blocks: light aluminium panels for building fuselages that look like an aircraft instead of
 * wool and planks. Shaped kinds (rounded corner, bevels, 45° slope) have a facing (the side that faces out
 * of the aircraft) and a half (top = roof edge, bottom = belly edge).
 */
public class SkinBlock extends Block {
    public enum Kind {
        PANEL(15, false), STRIPE(15, false), BELLY(15, false), WINDOW(17, false), FLOOR(30, false), COCKPIT_GLASS(20, false),
        CURVE(12, true), BEVEL_WALL(14, true), BEVEL_ROOF(14, true), SLOPE(9, true);

        public final double massKg;
        public final boolean shaped;

        Kind(double massKg, boolean shaped) {
            this.massKg = massKg;
            this.shaped = shaped;
        }
    }

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;

    public final Kind kind;
    private final Map<Direction, VoxelShape[]> shapes = new EnumMap<>(Direction.class);

    public SkinBlock(Kind kind, BlockBehaviour.Properties p) {
        super(p);
        this.kind = kind;
        if (kind.shaped) {
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.TOP));
            for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, new VoxelShape[]{build(d, false), build(d, true)});
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        // the property set must not depend on the kind field (not set yet here): every skin block has both
        b.add(FACING, HALF);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (kind == Kind.WINDOW || kind == Kind.COCKPIT_GLASS) {
            // see-through along the direction the player looks (the window faces in and out of the fuselage)
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
        }
        if (!kind.shaped) return defaultBlockState();
        Direction face = ctx.getClickedFace();
        boolean top = face == Direction.DOWN || (face != Direction.UP && ctx.getClickLocation().y - ctx.getClickedPos().getY() > 0.5);
        // the shaped side faces away from the player (the outside of the fuselage when building from inside)
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection()).setValue(HALF, top ? Half.TOP : Half.BOTTOM);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (!kind.shaped) return Shapes.block();
        return shapes.get(s.getValue(FACING))[s.getValue(HALF) == Half.BOTTOM ? 1 : 0];
    }

    @Override
    protected BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState s) {
        return kind.shaped;
    }

    /** collision / outline boxes in the NORTH frame (outside = north = -z), pixels, rotated to the facing */
    private VoxelShape build(Direction d, boolean bottom) {
        double[][] boxes = switch (kind) {
            // quarter round: material inside a radius-16 circle about the inner edge (south, bottom for a roof corner)
            case CURVE -> new double[][]{{0, 6, 2, 16, 11, 16}, {0, 11, 5, 16, 14, 16}, {0, 14, 9, 16, 16, 16}, {0, 0, 0, 16, 6, 16}};
            case BEVEL_WALL -> new double[][]{{0, 0, 0, 16, 10, 16}, {0, 10, 2, 16, 16, 16}};
            case BEVEL_ROOF -> new double[][]{{0, 0, 0, 16, 13, 16}, {0, 13, 4, 16, 16, 16}};
            case SLOPE -> new double[][]{{0, 0, 0, 16, 4, 16}, {0, 4, 4, 16, 8, 16}, {0, 8, 8, 16, 12, 16}, {0, 12, 12, 16, 16, 16}};
            default -> new double[][]{{0, 0, 0, 16, 16, 16}};
        };
        VoxelShape out = Shapes.empty();
        for (double[] b : boxes) {
            double y0 = b[1], y1 = b[4];
            if (bottom) { y0 = 16 - b[4]; y1 = 16 - b[1]; }
            double[] r = rot(d, b[0], b[2], b[3], b[5]);
            out = Shapes.or(out, Block.box(r[0], y0, r[1], r[2], y1, r[3]));
        }
        return out;
    }

    /** rotate an x/z rectangle (pixels) from the NORTH frame to facing d */
    private static double[] rot(Direction d, double x0, double z0, double x1, double z1) {
        return switch (d) {
            case SOUTH -> new double[]{16 - x1, 16 - z1, 16 - x0, 16 - z0};
            case EAST -> new double[]{16 - z1, x0, 16 - z0, x1};
            case WEST -> new double[]{z0, 16 - x1, z1, 16 - x0};
            default -> new double[]{x0, z0, x1, z1};
        };
    }
}
