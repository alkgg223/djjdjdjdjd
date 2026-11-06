package com.arkragnarok.building;

import net.minecraft.util.StringRepresentable;

public enum DoorState implements StringRepresentable {
    NONE, CLOSED, OPEN;

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
