package com.trafficcraft.block;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.npc.PedestrianEntity;
import com.trafficcraft.npc.Workplaces;
import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.Props;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadNetwork;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.registry.TCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Easy border checkpoint: placing a barrier gate creates a yellow BORDER node in the lane under its arm (or joins the node of a
 * gate already standing nearby, e.g. the one for the other direction) and posts a police officer beside it. Connect the road on
 * each side to that node with the road planner and the checkpoint works: cars stop, the officer inspects them, the barrier lifts.
 * Breaking the gate turns the node back into a plain road node (connected roads are kept) and dismisses the officer.
 */
@EventBusSubscriber(modid = TrafficCraft.MODID)
public final class GateEvents {
    static boolean isGate(net.minecraft.world.level.block.state.BlockState s) {
        return TCBlocks.GATE_BLOCKS.stream().anyMatch(id -> s.is(TCBlocks.get(id)));
    }

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof Player p) || !isGate(e.getPlacedBlock())) return;
        BlockPos gp = e.getPos();
        level.getServer().execute(() -> setup(level, gp, p));       // after the block entity exists
    }

    public static RoadNode setup(ServerLevel level, BlockPos gp, Player p) {
        if (!(level.getBlockEntity(gp) instanceof BarrierGateBE g)) return null;
        RoadNetwork net = RoadData.get(level);
        Direction arm = g.armDir();
        Vec3 lane = Vec3.atBottomCenterOf(gp).add(arm.getStepX() * (g.length / 2.0 + 0.5), 0, arm.getStepZ() * (g.length / 2.0 + 0.5));
        RoadNode n = null;
        for (RoadNode q : net.nodesOfType(NodeType.BORDER)) if (q.pos().distanceTo(lane) < 7) n = q;
        boolean fresh = n == null;
        if (fresh) {
            n = net.addNode(lane.x, lane.y, lane.z, NodeType.BORDER);
            Props.defaults(NodeType.BORDER, n.cfg);
            n.cfg.putBoolean("fromGate", true);
            net.changed();
        }
        Workplaces.ensureWorker(level, gp, "officer");
        if (p != null) p.displayClientMessage(Component.translatable(fresh ? "msg.trafficcraft.gate_node" : "msg.trafficcraft.gate_joined"), false);
        return n;
    }

    @SubscribeEvent
    public static void broken(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !isGate(e.getState())) return;
        BlockPos gp = e.getPos();
        for (PedestrianEntity ped : level.getEntitiesOfClass(PedestrianEntity.class, new AABB(gp).inflate(24), q -> q.kind().equals("officer") && gp.equals(q.workplace)))
            ped.discard();
        RoadNetwork net = RoadData.get(level);
        for (RoadNode n : net.nodesOfType(NodeType.BORDER)) {
            if (!n.cfg.getBoolean("fromGate") || n.pos().distanceTo(Vec3.atCenterOf(gp)) > 10) continue;
            boolean otherGate = false;
            BlockPos c = BlockPos.containing(n.pos());
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-9, -2, -9), c.offset(9, 2, 9)))
                if (!q.equals(gp) && level.getBlockEntity(q) instanceof BarrierGateBE) otherGate = true;
            if (otherGate) continue;
            if (net.edgesOf(n.id).isEmpty()) net.removeNode(n.id);
            else {
                n.type = NodeType.ROAD;
                net.changed();
            }
        }
    }
}
