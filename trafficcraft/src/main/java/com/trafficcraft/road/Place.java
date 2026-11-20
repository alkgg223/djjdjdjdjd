package com.trafficcraft.road;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** A named location on the map (supermarket, bank, restaurant ...), placed with the location tool. */
public final class Place {
    public final int id;
    public double x, y, z;
    public String type, name;

    public Place(int id, double x, double y, double z, String type, String name) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.name = name;
    }

    public Vec3 pos() {
        return new Vec3(x, y, z);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putDouble("z", z);
        t.putString("type", type);
        t.putString("name", name);
        return t;
    }

    public static Place load(CompoundTag t) {
        return new Place(t.getInt("id"), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getString("type"), t.getString("name"));
    }
}
