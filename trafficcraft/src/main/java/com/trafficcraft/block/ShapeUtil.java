package com.trafficcraft.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ShapeUtil {
    public static double[][] boxes(JsonArray a) {
        if (a == null) return new double[][]{{0, 0, 0, 16, 16, 16}};
        double[][] out = new double[a.size()][];
        int i = 0;
        for (JsonElement e : a) {
            JsonArray b = e.getAsJsonArray();
            out[i++] = new double[]{b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(), b.get(3).getAsDouble(), b.get(4).getAsDouble(),
                    b.get(5).getAsDouble()};
        }
        return out;
    }

    /** Box list (in 1/16 units, modelled facing north) rotated to the given horizontal facing. */
    public static VoxelShape shape(double[][] boxes, Direction facing) {
        VoxelShape s = Shapes.empty();
        for (double[] b : boxes) {
            double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
            double[] r = switch (facing) {
                case EAST -> new double[]{16 - z1, x0, 16 - z0, x1};
                case SOUTH -> new double[]{16 - x1, 16 - z1, 16 - x0, 16 - z0};
                case WEST -> new double[]{z0, 16 - x1, z1, 16 - x0};
                default -> new double[]{x0, z0, x1, z1};
            };
            s = Shapes.or(s, Block.box(clamp(r[0]), clamp(b[1]), clamp(r[1]), clamp(r[2]), clamp(b[4]), clamp(r[3])));
        }
        return s;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(16, v));
    }
}
