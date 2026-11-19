package com.trafficcraft.client.render;

import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.ParkingLayout;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.world.phys.Vec3;

/** Painted bay lines for parking nodes (white normal, blue disabled with symbol, yellow reserved). */
final class ParkingMarkings {
    static void build(RoadNetwork net, BiConsumer<Vec3, List<float[]>> add) {
        for (RoadNode n : net.nodes.values()) {
            if (n.type != NodeType.PARKING) continue;
            List<float[]> quads = new ArrayList<>();
            for (ParkingLayout.Space s : ParkingLayout.spaces(net, n)) {
                int col = s.type() == ParkingLayout.DISABLED ? 0xFF3A70D0 : s.type() == ParkingLayout.RESERVED ? 0xFFF0C020 : 0xFFEDEDE6;
                Vec3 f = s.facing(), l = new Vec3(f.z, 0, -f.x);
                double hl = s.length() / 2, hw = s.width() / 2;
                double y = n.y + 0.02;
                // side lines + back line (front stays open toward the aisle)
                bar(quads, s.center().add(l.scale(hw)), f, hl, 0.06, y, col);
                bar(quads, s.center().add(l.scale(-hw)), f, hl, 0.06, y, col);
                bar(quads, s.center().add(f.scale(hl)), l, hw, 0.06, y, col);
                if (s.type() == ParkingLayout.DISABLED) {
                    bar(quads, s.center(), f, 0.6, 0.6, y + 0.001, 0xFF3A70D0);
                    bar(quads, s.center(), f, 0.35, 0.35, y + 0.002, 0xFFEDEDE6);
                }
                if (s.type() == ParkingLayout.RESERVED) {
                    bar(quads, s.center().add(f.scale(-hl + 0.6)), l, hw - 0.2, 0.08, y, col);
                }
            }
            add.accept(n.pos(), quads);
        }
    }

    /** Rectangle centred at c, half-length hl along dir d, half-width hw across. Packed as 12 coords + colour. */
    static void bar(List<float[]> out, Vec3 c, Vec3 d, double hl, double hw, double y, int col) {
        Vec3 l = new Vec3(d.z, 0, -d.x);
        Vec3[] p = {c.add(d.scale(hl)).add(l.scale(hw)), c.add(d.scale(-hl)).add(l.scale(hw)), c.add(d.scale(-hl)).add(l.scale(-hw)), c.add(d.scale(hl)).add(l.scale(-hw))};
        float[] q = new float[13];
        for (int k = 0; k < 4; k++) {
            q[k * 3] = (float) p[k].x;
            q[k * 3 + 1] = (float) y;
            q[k * 3 + 2] = (float) p[k].z;
        }
        q[12] = Float.intBitsToFloat(col);
        out.add(q);
    }
}
