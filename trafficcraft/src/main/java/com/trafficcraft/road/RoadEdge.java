package com.trafficcraft.road;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** A road (or pedestrian path) between two nodes. Lanes are ordered left-to-right looking from A to B. */
public final class RoadEdge {
    public static final int R_NO_TRUCKS = 1, R_BUS_ONLY = 2, R_NO_ENTRY_AB = 4, R_NO_ENTRY_BA = 8;

    public final int id;
    public int a, b;
    public boolean ped;
    public float width = 5f;
    public float speed = 50f; // km/h
    public final List<Lane> lanes = new ArrayList<>();
    public Vec3 ctrl; // optional curve control point
    public boolean overtake = true;
    public int restrict;
    public String name = "";
    public transient EdgeGeom geom;

    public RoadEdge(int id, int a, int b) {
        this.id = id;
        this.a = a;
        this.b = b;
    }

    public int other(int node) {
        return node == a ? b : a;
    }

    public float laneWidth() {
        return lanes.isEmpty() ? width : width / lanes.size();
    }

    /** Lateral offset (positive = left looking A->B) of the centre of lane i. */
    public float laneOffset(int i) {
        float lw = laneWidth();
        return width / 2 - (i + 0.5f) * lw;
    }

    public boolean twoWay() {
        boolean f = false, r = false;
        for (Lane l : lanes) {
            if (l.dir() > 0) f = true;
            else r = true;
        }
        return f && r;
    }

    public boolean allows(int fromNode) {
        int dir = fromNode == a ? 1 : -1;
        for (Lane l : lanes) if (l.dir() == dir) return true;
        return ped;
    }

    /** Lanes usable when travelling away from fromNode, ordered from the right-most (kerb side) to the left-most. */
    public List<Integer> lanesFrom(int fromNode) {
        int dir = fromNode == a ? 1 : -1;
        List<Integer> out = new ArrayList<>();
        if (dir > 0) {
            for (int i = lanes.size() - 1; i >= 0; i--) if (lanes.get(i).dir() == dir) out.add(i);
        } else {
            for (int i = 0; i < lanes.size(); i++) if (lanes.get(i).dir() == dir) out.add(i);
        }
        return out;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putInt("a", a);
        t.putInt("b", b);
        t.putBoolean("ped", ped);
        t.putFloat("w", width);
        t.putFloat("spd", speed);
        int[] ld = new int[lanes.size()];
        for (int i = 0; i < ld.length; i++) ld[i] = (lanes.get(i).dir() > 0 ? 1 : 0) | lanes.get(i).type() << 1;
        t.putIntArray("lanes", ld);
        if (ctrl != null) {
            t.putDouble("cx", ctrl.x);
            t.putDouble("cy", ctrl.y);
            t.putDouble("cz", ctrl.z);
        }
        t.putBoolean("ot", overtake);
        t.putInt("rs", restrict);
        t.putString("name", name);
        return t;
    }

    public static RoadEdge load(CompoundTag t) {
        RoadEdge e = new RoadEdge(t.getInt("id"), t.getInt("a"), t.getInt("b"));
        e.ped = t.getBoolean("ped");
        e.width = t.getFloat("w");
        e.speed = t.getFloat("spd");
        for (int v : t.getIntArray("lanes")) e.lanes.add(new Lane((v & 1) == 1 ? 1 : -1, v >> 1));
        if (t.contains("cx")) e.ctrl = new Vec3(t.getDouble("cx"), t.getDouble("cy"), t.getDouble("cz"));
        e.overtake = t.getBoolean("ot");
        e.restrict = t.getInt("rs");
        e.name = t.getString("name");
        return e;
    }

    public static List<Lane> defaultLanes(int count, boolean oneWay) {
        List<Lane> l = new ArrayList<>();
        if (oneWay) {
            for (int i = 0; i < count; i++) l.add(new Lane(1, Lane.NORMAL));
        } else {
            int back = count / 2, fwd = count - back;
            if (count == 1) {
                back = 0;
                fwd = 1;
            }
            for (int i = 0; i < back; i++) l.add(new Lane(-1, Lane.NORMAL));
            for (int i = 0; i < fwd; i++) l.add(new Lane(1, Lane.NORMAL));
        }
        return l;
    }
}
