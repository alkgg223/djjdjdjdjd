package com.arkragnarok.building;

import net.minecraft.util.StringRepresentable;

/** structure pieces; foundation and ceiling are 3x3 flat, wall and doorframe are 3 wide x 3 tall */
public enum PieceType implements StringRepresentable {
    FOUNDATION, WALL, DOORFRAME, CEILING;

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }

    public boolean flat() {
        return this == FOUNDATION || this == CEILING;
    }

    /** part index of the block that carries the model (centre of a flat piece, bottom middle of a wall) */
    public int masterPart() {
        return flat() ? 4 : 1;
    }
}
