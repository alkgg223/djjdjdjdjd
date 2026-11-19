package com.trafficcraft.road;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * Parking spaces generated from a PARKING node: an access aisle starts at the node and runs along the row direction
 * (perpendicular to the road that connects the node unless 'rowYaw' is set); spaces line one or both sides of it.
 */
public final class ParkingLayout {
    public static final int NORMAL = 0, DISABLED = 1, RESERVED = 2;

    public record Space(int index, Vec3 center, Vec3 aisle, Vec3 facing, double length, double width, int type) {
        public float yaw() {
            return (float) Math.toDegrees(Math.atan2(-facing.x, facing.z));
        }
    }

    public static Vec3 rowDir(RoadNetwork net, RoadNode n) {
        if (n.cfg.contains("rowYaw")) {
            double r = Math.toRadians(n.cfg.getFloat("rowYaw"));
            return new Vec3(-Math.sin(r), 0, Math.cos(r));
        }
        for (RoadEdge e : net.roadEdgesOf(n.id)) {
            Vec3 in = TrafficLights.arrivalDir(net, e, n.id);
            if (in.lengthSqr() > 0) return in;
        }
        return new Vec3(0, 0, 1);
    }

    public static List<Space> spaces(RoadNetwork net, RoadNode n) {
        List<Space> out = new ArrayList<>();
        CompoundTag c = n.cfg;
        int count = c.contains("spaces") ? Math.max(1, Math.min(60, c.getInt("spaces"))) : 6;
        String layout = c.contains("layout") ? c.getString("layout") : "perpendicular";
        String side = c.contains("side") ? c.getString("side") : "both";
        int disabled = c.getInt("disabled"), reserved = c.getInt("reserved");
        Vec3 row = rowDir(net, n);
        Vec3 left = new Vec3(row.z, 0, -row.x);
        boolean parallel = layout.equals("parallel"), angled = layout.equals("angled");
        double width = parallel ? 2.5 : 2.6, length = parallel ? 6.0 : 5.0;
        double spacing = parallel ? 6.5 : angled ? 3.1 : width;
        double aisle = 1.8;
        int perSide = side.equals("both") ? (count + 1) / 2 : count;
        int idx = 0;
        for (int sd = 0; sd < 2 && idx < count; sd++) {
            if (sd == 0 && side.equals("left")) continue;
            if (sd == 1 && side.equals("right")) continue;
            Vec3 sideDir = sd == 0 ? left.scale(-1) : left; // right side first
            for (int i = 0; i < perSide && idx < count; i++) {
                Vec3 a = n.pos().add(row.scale(2.5 + (i + 0.5) * spacing));
                Vec3 face = sideDir;
                if (angled) face = sideDir.scale(0.82).add(row.scale(0.57)).normalize();
                Vec3 center = parallel ? a.add(sideDir.scale(aisle + width / 2)) : a.add(face.scale(aisle + length / 2));
                Vec3 f = parallel ? row : face;
                int type = idx < disabled ? DISABLED : idx < disabled + reserved ? RESERVED : NORMAL;
                out.add(new Space(idx, center, a, f, length, width, type));
                idx++;
            }
        }
        return out;
    }
}
