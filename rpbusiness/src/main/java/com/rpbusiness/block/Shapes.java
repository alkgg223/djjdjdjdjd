package com.rpbusiness.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;

/** Builds per-facing shapes from boxes described for a NORTH-facing block (pixel units). */
public final class Shapes {
    private Shapes() {}

    public static EnumMap<Direction, VoxelShape> of(double[]... boxes) {
        EnumMap<Direction, VoxelShape> m = new EnumMap<>(Direction.class);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape s = net.minecraft.world.phys.shapes.Shapes.empty();
            for (double[] b : boxes) s = net.minecraft.world.phys.shapes.Shapes.or(s, rot(b, d));
            m.put(d, s.optimize());
        }
        return m;
    }

    private static VoxelShape rot(double[] b, Direction d) {
        double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
        return switch (d) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    public static double[] bx(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new double[]{x1, y1, z1, x2, y2, z2};
    }
}
