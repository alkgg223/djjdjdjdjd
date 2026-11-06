package com.arkragnarok.building;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * ARK-like snapping: foundations line up with neighbouring foundations; walls and doorframes stand on the foundation
 * edge nearest to where you aim (or on top of another wall); ceilings sit on the walls' top row, between the walls.
 */
public final class Placement {
    private Placement() {}

    public record Plan(BlockPos master, Direction facing) {}

    @Nullable
    private static BlockState piece(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof PieceBlock ? s : null;
    }

    @Nullable
    public static Plan plan(Level level, PieceType type, BlockPos clicked, Direction face, Vec3 hit, Direction look) {
        BlockState cs = piece(level, clicked);
        if (type == PieceType.FOUNDATION) {
            if (cs != null && cs.getValue(PieceBlock.PIECE) == PieceType.FOUNDATION && face.getAxis().isHorizontal()) {
                return new Plan(PieceBlock.master(clicked, cs).relative(face, 3), look);
            }
            BlockPos base = clicked.relative(face);
            return new Plan(face == Direction.UP ? base.relative(look) : base, look);
        }
        if (type == PieceType.CEILING) {
            if (cs != null) {
                PieceType ct = cs.getValue(PieceBlock.PIECE);
                BlockPos m = PieceBlock.master(clicked, cs);
                if (ct == PieceType.WALL || ct == PieceType.DOORFRAME) {
                    Direction f = cs.getValue(PieceBlock.FACING);
                    return new Plan(m.relative(f.getOpposite(), 2).above(2), look);
                }
                if (ct == PieceType.CEILING && face.getAxis().isHorizontal()) return new Plan(m.relative(face, 3), look);
            }
            return new Plan(clicked.relative(face), look);
        }
        // walls and doorframes
        if (cs != null) {
            PieceType ct = cs.getValue(PieceBlock.PIECE);
            BlockPos m = PieceBlock.master(clicked, cs);
            if ((ct == PieceType.FOUNDATION || ct == PieceType.CEILING) && face == Direction.UP) {
                double dx = hit.x - (m.getX() + 0.5), dz = hit.z - (m.getZ() + 0.5);
                Direction f = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                return new Plan(m.relative(f, 2).above(ct == PieceType.FOUNDATION ? 1 : 1), f);
            }
            if ((ct == PieceType.WALL || ct == PieceType.DOORFRAME) && face == Direction.UP) {
                return new Plan(m.above(3), cs.getValue(PieceBlock.FACING));
            }
        }
        return new Plan(clicked.relative(face), look);
    }

    /** places the piece if all its blocks are free; returns false otherwise */
    public static boolean place(Level level, PieceBlock block, PieceType type, Plan plan) {
        List<Integer> partIdx = new ArrayList<>();
        List<BlockPos> ps = PieceBlock.parts(plan.master(), type, plan.facing(), partIdx);
        for (BlockPos p : ps) {
            if (!level.getBlockState(p).canBeReplaced() || p.getY() >= level.getMaxBuildHeight() || p.getY() < level.getMinBuildHeight()) return false;
        }
        for (int i = 0; i < ps.size(); i++) {
            BlockState s = block.defaultBlockState().setValue(PieceBlock.PIECE, type).setValue(PieceBlock.PART, partIdx.get(i))
                    .setValue(PieceBlock.FACING, plan.facing()).setValue(PieceBlock.DOOR, DoorState.NONE);
            level.setBlock(ps.get(i), s, 3);
        }
        return true;
    }

    /** hangs a door in the doorframe that contains this block */
    public static boolean installDoor(Level level, BlockPos clicked, Tier doorTier) {
        BlockState cs = piece(level, clicked);
        if (cs == null || cs.getValue(PieceBlock.PIECE) != PieceType.DOORFRAME) return false;
        BlockPos m = PieceBlock.master(clicked, cs);
        BlockState ms = level.getBlockState(m);
        if (!(ms.getBlock() instanceof PieceBlock) || ms.getValue(PieceBlock.DOOR) != DoorState.NONE) return false;
        PieceBlock.setDoor(level, m, ms, DoorState.CLOSED);
        if (level.getBlockEntity(m) instanceof PieceBlockEntity be) be.setDoorTier(doorTier);
        return true;
    }
}
