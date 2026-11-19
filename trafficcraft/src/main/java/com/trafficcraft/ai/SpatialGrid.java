package com.trafficcraft.ai;

import com.trafficcraft.vehicle.VehicleEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Uniform 16-block grid of vehicles and road users, rebuilt every tick for O(1) neighbour queries. */
public final class SpatialGrid {
    private final Map<Long, List<Entity>> cells = new HashMap<>();

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    public void clear() {
        cells.clear();
    }

    public void add(Entity e) {
        cells.computeIfAbsent(key((int) Math.floor(e.getX() / 16), (int) Math.floor(e.getZ() / 16)), k -> new ArrayList<>()).add(e);
    }

    public void near(double x, double z, double r, List<Entity> out) {
        int x0 = (int) Math.floor((x - r) / 16), x1 = (int) Math.floor((x + r) / 16);
        int z0 = (int) Math.floor((z - r) / 16), z1 = (int) Math.floor((z + r) / 16);
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) {
            List<Entity> l = cells.get(key(cx, cz));
            if (l != null) out.addAll(l);
        }
    }

    public static boolean isRoadUser(Entity e) {
        return e instanceof VehicleEntity || (e instanceof LivingEntity && !e.isPassenger());
    }
}
