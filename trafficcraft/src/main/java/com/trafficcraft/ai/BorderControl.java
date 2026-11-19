package com.trafficcraft.ai;

import com.trafficcraft.TCConfig;
import com.trafficcraft.block.BarrierGateBE;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Border checkpoints: a vehicle arriving at a BORDER node stops at the line, waits for an officer (NPC or player)
 * to inspect it for the configured time, then the nearest barrier rises, the car passes and the barrier lowers.
 */
public final class BorderControl {
    public static final class Case {
        public final int vehicle;
        public final int node;
        public int ticks;
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
        if (!officersRequired || c.officerPresent || c.playerOfficer) c.ticks++;
        else if (level.getGameTime() % 20 == 0) c.ticks += 0; // waiting for an officer to arrive
        if (!c.officerPresent && !c.playerOfficer && officersRequired && c.ticks == 0 && level.getGameTime() % 100 == 0) c.officerPresent = true; // fallback
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

    public void forget(int vehicle) {
        cases.remove(vehicle);
    }
}
