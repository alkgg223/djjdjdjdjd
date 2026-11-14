package com.vehicleworks.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/** Orientation of a wing plate: which axis is the plate normal (lift direction). */
public enum WingOrientation implements StringRepresentable {
    HORIZONTAL("horizontal", Direction.Axis.Y),
    VERTICAL_X("vertical_x", Direction.Axis.X),
    VERTICAL_Z("vertical_z", Direction.Axis.Z);

    private final String name;
    public final Direction.Axis normal;

    WingOrientation(String name, Direction.Axis normal) {
        this.name = name;
        this.normal = normal;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static WingOrientation forNormal(Direction.Axis axis) {
        return switch (axis) {
            case X -> VERTICAL_X;
            case Z -> VERTICAL_Z;
            default -> HORIZONTAL;
        };
    }

    public WingOrientation next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
