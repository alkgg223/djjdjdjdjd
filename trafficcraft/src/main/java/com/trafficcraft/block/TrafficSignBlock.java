package com.trafficcraft.block;

import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadEdge;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A traffic sign. Placing it next to a road applies its rule to the road network (speed limit, stop, give way, no entry). */
public class TrafficSignBlock extends FacingBlock {
    public final String sign;

    public TrafficSignBlock(Properties p, double[][] boxes, String sign) {
        super(p, boxes, false);
        this.sign = sign;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState s, net.minecraft.world.entity.LivingEntity by, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, s, by, stack);
        if (level instanceof ServerLevel sl) {
            if (sign.equals("one_way") && by != null) oneWay(sl, pos, by.getLookAngle());
            else apply(sl, pos);
        }
    }

    /** One-way sign: the nearest road becomes one-way in the direction the player looked when placing the sign. */
    void oneWay(ServerLevel level, BlockPos pos, Vec3 look) {
        RoadNetwork net = RoadData.get(level);
        Vec3 p = Vec3.atCenterOf(pos);
        RoadEdge e = net.nearestEdge(p, 6, false);
        if (e == null) return;
        var g = net.geom(e);
        Vec3 t = g.tangent(g.project(p, null));
        int dir = t.x * look.x + t.z * look.z >= 0 ? 1 : -1;
        java.util.List<com.trafficcraft.road.Lane> nl = new java.util.ArrayList<>();
        for (var l : e.lanes) nl.add(new com.trafficcraft.road.Lane(dir, l.type()));
        e.lanes.clear();
        e.lanes.addAll(nl);
        net.changed();
    }

    void apply(ServerLevel level, BlockPos pos) {
        RoadNetwork net = RoadData.get(level);
        Vec3 p = Vec3.atCenterOf(pos);
        RoadEdge e = net.nearestEdge(p, 6, false);
        if (e == null) return;
        if (sign.startsWith("speed_")) {
            e.speed = Integer.parseInt(sign.substring(6));
            net.changed();
            return;
        }
        RoadNode na = net.nodes.get(e.a), nb = net.nodes.get(e.b);
        RoadNode n = na.pos().distanceToSqr(p) < nb.pos().distanceToSqr(p) ? na : nb;
        switch (sign) {
            case "stop" -> addEdge(n, "stopEdges", e.id);
            case "give_way" -> addEdge(n, "yieldEdges", e.id);
            case "no_entry" -> e.restrict |= n.id == e.a ? RoadEdge.R_NO_ENTRY_AB : RoadEdge.R_NO_ENTRY_BA;
            case "crossing", "pedestrians" -> n.flags |= RoadNode.F_CROSSWALK;
            default -> {
                return;
            }
        }
        net.changed();
    }

    static void addEdge(RoadNode n, String key, int edge) {
        int[] a = n.cfg.getIntArray(key);
        for (int v : a) if (v == edge) return;
        int[] b = java.util.Arrays.copyOf(a, a.length + 1);
        b[a.length] = edge;
        n.cfg.put(key, new IntArrayTag(b));
    }
}
