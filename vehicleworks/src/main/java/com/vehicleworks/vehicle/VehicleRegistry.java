package com.vehicleworks.vehicle;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Tracks loaded vehicles per level (both logical sides) for fast spatial queries. */
public final class VehicleRegistry {
    private static final Map<Level, List<VehicleEntity>> BY_LEVEL = new WeakHashMap<>();

    private VehicleRegistry() {}

    public static synchronized void add(VehicleEntity v) {
        List<VehicleEntity> l = BY_LEVEL.computeIfAbsent(v.level(), k -> new ArrayList<>());
        if (!l.contains(v)) l.add(v);
    }

    public static synchronized void remove(VehicleEntity v) {
        List<VehicleEntity> l = BY_LEVEL.get(v.level());
        if (l != null) l.remove(v);
    }

    public static synchronized List<VehicleEntity> all(Level level) {
        List<VehicleEntity> l = BY_LEVEL.get(level);
        return l == null ? Collections.emptyList() : new ArrayList<>(l);
    }

    /** Vehicles whose world bounds intersect the box. Cheap: bounds test only. */
    public static synchronized List<VehicleEntity> near(Level level, AABB box) {
        List<VehicleEntity> l = BY_LEVEL.get(level);
        if (l == null || l.isEmpty()) return Collections.emptyList();
        List<VehicleEntity> out = null;
        for (VehicleEntity v : l) {
            if (v.isRemoved()) continue;
            if (v.getBoundingBox().intersects(box)) {
                if (out == null) out = new ArrayList<>(2);
                out.add(v);
            }
        }
        return out == null ? Collections.emptyList() : out;
    }

    public static synchronized VehicleEntity byId(Level level, int id) {
        List<VehicleEntity> l = BY_LEVEL.get(level);
        if (l == null) return null;
        for (VehicleEntity v : l) if (v.getId() == id) return v;
        return null;
    }
}
