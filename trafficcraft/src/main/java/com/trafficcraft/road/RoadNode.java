package com.trafficcraft.road;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public final class RoadNode {
    public static final int F_STOP = 1, F_YIELD = 2, F_LIGHTS = 4, F_CROSSWALK = 8, F_RING = 16, F_PRIORITY = 32;

    public final int id;
    public double x, y, z;
    public NodeType type;
    public int flags;
    public CompoundTag cfg = new CompoundTag();

    public RoadNode(int id, double x, double y, double z, NodeType type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
    }

    public Vec3 pos() {
        return new Vec3(x, y, z);
    }

    public boolean has(int f) {
        return (flags & f) != 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putDouble("z", z);
        t.putString("type", type.name());
        t.putInt("flags", flags);
        t.put("cfg", cfg.copy());
        return t;
    }

    public static RoadNode load(CompoundTag t) {
        RoadNode n = new RoadNode(t.getInt("id"), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), NodeType.byName(t.getString("type")));
        n.flags = t.getInt("flags");
        n.cfg = t.getCompound("cfg");
        return n;
    }
}
