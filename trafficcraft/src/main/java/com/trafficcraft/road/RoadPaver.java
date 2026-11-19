package com.trafficcraft.road;

import com.trafficcraft.registry.TCBlocks;
import java.util.HashSet;
import java.util.Set;
import com.trafficcraft.road.RoadNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Lays asphalt (and optional sidewalks) along an edge's smooth geometry and fills junction areas. */
public final class RoadPaver {
    public static final int BOTH = 0, WALKS = 1, ROAD = 2;

    public static void paveEdge(ServerLevel level, RoadNetwork net, RoadEdge e, CompoundTag tool) {
        paveEdge(level, net, e, tool, WALKS);
        paveEdge(level, net, e, tool, ROAD);
    }

    /** Lays the sidewalks (WALKS) or the carriageway (ROAD) of an edge. Roads always win over sidewalks. */
    public static void paveEdge(ServerLevel level, RoadNetwork net, RoadEdge e, CompoundTag tool, int what) {
        EdgeGeom g = net.geom(e);
        if (g == null) return;
        boolean clear = !tool.contains("clear") || tool.getBoolean("clear");
        int sidewalk = e.ped ? 0 : (tool.contains("sidewalk") ? tool.getInt("sidewalk") : 0);
        Block surface = TCBlocks.get(e.ped ? "sidewalk" : "asphalt");
        Block walk = TCBlocks.get("sidewalk_slab");
        Block base = TCBlocks.get("concrete_block");
        Set<BlockPos> done = new HashSet<>();
        double half = e.width / 2 + (e.ped ? 0 : 0.35);
        RoadNode na = net.nodes.get(e.a), nb = net.nodes.get(e.b);
        double exA = net.isJunction(e.a) ? net.junctionRadius(e.a) + sidewalk + 1.5 : 0, exB = net.isJunction(e.b) ? net.junctionRadius(e.b) + sidewalk + 1.5 : 0;
        for (double s = 0; s <= g.length + 0.01; s += 0.35) {
            for (double o = -half - sidewalk; o <= half + sidewalk + 0.01; o += 0.35) {
                boolean isWalk = Math.abs(o) > half;
                if (isWalk != (what == WALKS)) continue;
                Vec3 p = g.point(s, o);
                if (isWalk && (p.distanceTo(na.pos()) < exA || p.distanceTo(nb.pos()) < exB)) continue;
                BlockPos bp = BlockPos.containing(p.x, p.y - 0.5, p.z);
                if (!done.add(bp)) continue;
                if (isWalk) {
                    if (level.getBlockState(bp).is(surface)) continue; // never put a kerb on a road
                    level.setBlock(bp, base.defaultBlockState(), 2);
                    BlockState above = level.getBlockState(bp.above());
                    if (RoadEditing.replaceable(above) || clear) level.setBlock(bp.above(), walk.defaultBlockState(), 2);
                } else {
                    level.setBlock(bp, surface.defaultBlockState(), 2);
                    for (int dy = 1; dy <= 3; dy++) {
                        BlockPos up = bp.above(dy);
                        BlockState st = level.getBlockState(up);
                        boolean kerb = st.is(walk);
                        if (!st.isAir() && (kerb || clear && !TCBlocks.isInfrastructure(st))) level.setBlock(up, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        if (what == ROAD) for (int id : new int[]{e.a, e.b}) if (net.isJunction(id) && !e.ped) paveJunction(level, net, id);
    }

    public static void paveJunction(ServerLevel level, RoadNetwork net, int nodeId) {
        RoadNode n = net.nodes.get(nodeId);
        if (n == null) return;
        float r = net.junctionRadius(nodeId) + 0.5f;
        Block surface = TCBlocks.get("asphalt");
        for (int dx = (int) -r - 1; dx <= r + 1; dx++) for (int dz = (int) -r - 1; dz <= r + 1; dz++) {
            if (dx * dx + dz * dz > r * r) continue;
            BlockPos bp = BlockPos.containing(n.x + dx, n.y - 0.5, n.z + dz);
            level.setBlock(bp, surface.defaultBlockState(), 2);
            for (int dy = 1; dy <= 3; dy++) {
                BlockState st = level.getBlockState(bp.above(dy));
                if (!st.isAir() && (!TCBlocks.isInfrastructure(st) || st.is(TCBlocks.get("sidewalk_slab")))) level.setBlock(bp.above(dy), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }
}
