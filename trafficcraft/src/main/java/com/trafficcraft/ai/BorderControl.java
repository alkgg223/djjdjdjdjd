package com.trafficcraft.ai;

import com.trafficcraft.TCConfig;
import com.trafficcraft.block.BarrierGateBE;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Border checkpoints: a vehicle arriving at a BORDER node stops at the line, waits for an officer (NPC or player)
 * to inspect it for the configured time, then the nearest barrier rises, the car passes and the barrier lowers.
 */
public final class BorderControl {
    public static final class Case {
        public final int vehicle;
        public final int node;
        public int ticks, waited;
        public boolean approved, denied, officerPresent, playerOfficer;
        public long approvedAt;

        Case(int v, int n) {
            vehicle = v;
            node = n;
        }
    }

    public final Map<Integer, Case> cases = new HashMap<>();

    /** True when the vehicle may continue past the border node. */
    public boolean cleared(ServerLevel level, RoadNode n, VehicleEntity v, double dist) {
        Case c = cases.get(v.getId());
        if (c != null && c.node == n.id && c.approved) {
            openGate(level, n, 70);
            return level.getGameTime() - c.approvedAt > 12;
        }
        if (dist > 1.5 || Math.abs(v.speed) > 0.3f) return false;
        if (c == null || c.node != n.id) {
            c = new Case(v.getId(), n.id);
            cases.put(v.getId(), c);
        }
        int need = 20 * (n.cfg.contains("inspection") ? n.cfg.getInt("inspection") : TCConfig.BORDER_INSPECTION.get());
        boolean officersRequired = !n.cfg.contains("officers") || n.cfg.getInt("officers") > 0;
        boolean playerDuty = !n.cfg.getString("player_officer").isEmpty();
        c.waited++;
        if (playerDuty && !c.denied) {
            // a player is on duty: the vehicle waits for the stamp (auto-approve after 90 s so traffic never deadlocks)
            if (c.waited > 20 * 90) approve(level, n, c);
            return false;
        }
        if (!officersRequired || c.officerPresent || c.playerOfficer) c.ticks++;
        // fallback when the checkpoint has no officer NPC nearby
        if (!c.officerPresent && officersRequired && c.waited > 60 && !com.trafficcraft.npc.Workplaces.officerNear(level, n)) c.officerPresent = true;
        if (!c.officerPresent && officersRequired && c.waited > 20 * 30) c.officerPresent = true;
        if (c.denied) {
            if (c.ticks > need * 2) {
                c.denied = false;
                approve(level, n, c);
            }
            return false;
        }
        if (c.ticks >= need && !c.approved) {
            int deny = n.cfg.contains("denyChance") ? n.cfg.getInt("denyChance") : (int) (TCConfig.BORDER_DENY_CHANCE.get() * 100);
            if (!c.playerOfficer && v.getRandom().nextInt(100) < deny) {
                c.denied = true;
                TrafficStats.inc("border_secondary");
                v.setFlag(VehicleEntity.F_HAZARD, true);
            } else approve(level, n, c);
        }
        return false;
    }

    public void approve(ServerLevel level, RoadNode n, Case c) {
        TrafficStats.inc("border_approved");
        TrafficStats.inc("border_approved@" + n.id);
        c.approved = true;
        c.approvedAt = level.getGameTime();
        if (level.getEntity(c.vehicle) instanceof VehicleEntity v) v.setFlag(VehicleEntity.F_HAZARD, false);
        openGate(level, n, 80);
    }

    public static void openGate(ServerLevel level, RoadNode n, int hold) {
        BlockPos c = BlockPos.containing(n.pos());
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-9, -2, -9), c.offset(9, 2, 9))) {
            if (level.getBlockEntity(q) instanceof BarrierGateBE g && !g.manual) g.setOpen(true, hold);
        }
    }

    /**
     * Player drivers: stopping at a checkpoint starts an inspection. A passport is required when the node asks for it;
     * when a player officer is on duty they decide with the stamp, otherwise the NPC officer clears the car.
     */
    public void playerTick(ServerLevel level, com.trafficcraft.road.RoadNetwork rn) {
        for (RoadNode n : rn.nodesOfType(com.trafficcraft.road.NodeType.BORDER)) {
            Vec3 c = n.pos();
            for (VehicleEntity v : level.getEntitiesOfClass(VehicleEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(9, 3, 9))) {
                if (!(v.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player p)) continue;
                Case k = cases.get(v.getId());
                if (k != null && k.node == n.id && k.approved) {
                    if (level.getGameTime() - k.approvedAt > 20 * 20) cases.remove(v.getId());
                    continue;
                }
                if (v.position().distanceTo(c) > 7 || Math.abs(v.speed) > 0.4f) continue;
                if (k == null || k.node != n.id) {
                    k = new Case(v.getId(), n.id);
                    cases.put(v.getId(), k);
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.trafficcraft.border_stop"), true);
                }
                k.waited += 5;
                boolean needPass = !n.cfg.contains("passport") || n.cfg.getBoolean("passport");
                if (needPass && !com.trafficcraft.npc.Workplaces.hasPassport(p)) {
                    if (k.waited % 60 == 0) p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.trafficcraft.border_passport"), true);
                    continue;
                }
                if (!n.cfg.getString("player_officer").isEmpty() && !n.cfg.getString("player_officer").equals(p.getUUID().toString())) {
                    if (k.waited % 80 == 0) p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.trafficcraft.border_wait_officer"), true);
                    continue;
                }
                int need = 20 * Math.max(2, (n.cfg.contains("inspection") ? n.cfg.getInt("inspection") : TCConfig.BORDER_INSPECTION.get()) / 2);
                if (k.waited >= need) {
                    approve(level, n, k);
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.trafficcraft.border_ok"), true);
                }
            }
        }
    }

    public void forget(int vehicle) {
        cases.remove(vehicle);
    }
}
