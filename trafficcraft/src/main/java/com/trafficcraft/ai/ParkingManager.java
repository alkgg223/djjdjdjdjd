package com.trafficcraft.ai;

import com.trafficcraft.road.ParkingLayout;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** Tracks which parking spaces are claimed or occupied. */
public final class ParkingManager {
    private final Map<Integer, Map<Integer, Integer>> claims = new HashMap<>(); // node -> space -> vehicle

    public int claim(RoadNetwork net, RoadNode n, VehicleEntity v) {
        Map<Integer, Integer> m = claims.computeIfAbsent(n.id, k -> new HashMap<>());
        List<ParkingLayout.Space> spaces = ParkingLayout.spaces(net, n);
        for (ParkingLayout.Space sp : spaces) {
            if (sp.type() == ParkingLayout.RESERVED) continue;
            if (sp.type() == ParkingLayout.DISABLED && v.getRandom().nextFloat() > 0.08f) continue;
            if (m.containsKey(sp.index())) continue;
            // a player's car standing in the bay also blocks it
            AABB box = new AABB(sp.center().x - 1.2, sp.center().y, sp.center().z - 1.2, sp.center().x + 1.2, sp.center().y + 2, sp.center().z + 1.2);
            boolean occupied = false;
            for (Entity e : v.level().getEntities(v, box)) if (e instanceof VehicleEntity) occupied = true;
            if (occupied) continue;
            m.put(sp.index(), v.getId());
            return sp.index();
        }
        return -1;
    }

    public void release(int node, int vehicle) {
        Map<Integer, Integer> m = claims.get(node);
        if (m != null) m.values().removeIf(id -> vehicle < 0 || id == vehicle);
    }

    public void releaseVehicle(int vehicle) {
        for (Map<Integer, Integer> m : claims.values()) m.values().removeIf(id -> id == vehicle);
    }

    public int occupied(int node) {
        Map<Integer, Integer> m = claims.get(node);
        return m == null ? 0 : m.size();
    }
}
