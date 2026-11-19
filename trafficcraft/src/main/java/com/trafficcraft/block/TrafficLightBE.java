package com.trafficcraft.block;

import com.trafficcraft.registry.TCBlocks;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadEdge;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.road.TrafficLights;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Traffic / pedestrian signal head linked automatically to the nearest signalised junction and to the approach it faces. */
public class TrafficLightBE extends SyncedBE implements BEBlock.TickingBE {
    public int node = -1, edge = -1;
    public boolean ped;
    private int check;

    public TrafficLightBE(BlockPos p, BlockState s) {
        super(TCBlocks.TRAFFIC_LIGHT_BE.get(), p, s);
        ped = s.getBlock() instanceof BEBlock b && b.kind.equals("ped_signal");
    }

    @Override
    protected void write(CompoundTag t) {
        t.putInt("node", node);
        t.putInt("edge", edge);
    }

    @Override
    protected void read(CompoundTag t) {
        node = t.getInt("node");
        edge = t.getInt("edge");
    }

    @Override
    public void serverTick() {
        if (++check % 40 != 1) return;
        RoadNetwork net = RoadData.get((ServerLevel) level);
        RoadNode n = net.nodes.get(node);
        RoadEdge e = net.edges.get(edge);
        if (n != null && e != null && n.has(RoadNode.F_LIGHTS) && (e.a == n.id || e.b == n.id)) return;
        link(net);
    }

    void link(RoadNetwork net) {
        Vec3 p = Vec3.atCenterOf(worldPosition);
        RoadNode best = null;
        double bd = 30 * 30;
        for (RoadNode n : net.nodes.values()) {
            if (!n.has(RoadNode.F_LIGHTS)) continue;
            double d = n.pos().distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        int nn = -1, ne = -1;
        if (best != null) {
            Direction f = getBlockState().getValue(FacingBlock.FACING);
            Vec3 face = new Vec3(f.getStepX(), 0, f.getStepZ());
            double score = 1e9;
            for (RoadEdge e : TrafficLights.approaches(net, best.id)) {
                Vec3 arr = TrafficLights.arrivalDir(net, e, best.id);
                double facing = arr.x * face.x + arr.z * face.z; // -1 when the head faces oncoming traffic
                var g = net.geom(e);
                Vec3 end = e.b == best.id ? g.point(g.length - g.trimB, 0) : g.point(g.trimA, 0);
                double s = facing * 20 + end.distanceTo(p);
                if (ped) s = end.distanceTo(p);
                if (s < score) {
                    score = s;
                    ne = e.id;
                }
            }
            nn = best.id;
        }
        if (nn != node || ne != edge) {
            node = nn;
            edge = ne;
            sync();
        }
    }
}
