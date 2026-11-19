package com.trafficcraft.ai;

import com.trafficcraft.block.BEBlock;
import com.trafficcraft.block.FacingBlock;
import com.trafficcraft.block.GenericBE;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.station.FuelStations;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** AI refuelling at GAS_STATION nodes: pick a free pump nearby, stop beside it, get served, pay the station. */
public final class FuelService {
    private final Map<Integer, BlockPos> pumpOf = new HashMap<>();
    private final Map<BlockPos, Integer> busy = new HashMap<>();
    private final Map<Integer, Float> startFuel = new HashMap<>();

    public BlockPos claimPump(ServerLevel level, RoadNode n, VehicleEntity v) {
        BlockPos c = BlockPos.containing(n.pos());
        BlockPos best = null;
        double bd = 1e9;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-20, -3, -20), c.offset(20, 3, 20))) {
            if (!(level.getBlockEntity(q) instanceof GenericBE be) || !(level.getBlockState(q).getBlock() instanceof BEBlock b) || !b.kind.equals("fuel_pump")) continue;
            boolean electricPump = level.getBlockState(q).getBlock().builtInRegistryHolder().key().location().getPath().equals("ev_charger");
            if (electricPump != v.spec().electric) continue;
            BlockPos im = q.immutable();
            Integer owner = busy.get(im);
            if (owner != null && level.getEntity(owner) instanceof VehicleEntity) continue;
            if (be.data.hasUUID("holder")) continue;
            double d = im.distSqr(c);
            if (d < bd) {
                bd = d;
                best = im;
            }
        }
        if (best != null) {
            busy.put(best, v.getId());
            pumpOf.put(v.getId(), best);
        }
        return best;
    }

    /** Where a car stops to be refuelled: beside the pump, on its side opposite the display. */
    public Vec3 stopPoint(ServerLevel level, BlockPos pump) {
        Direction f = level.getBlockState(pump).getValue(FacingBlock.FACING);
        Direction side = f.getClockWise();
        return Vec3.atBottomCenterOf(pump).add(side.getStepX() * 2.1, 0, side.getStepZ() * 2.1);
    }

    /** Direction cars drive when pulling up to the pump (toward the pump's front). */
    public Vec3 pumpDir(ServerLevel level, BlockPos pump) {
        Direction f = level.getBlockState(pump).getValue(FacingBlock.FACING);
        return new Vec3(f.getStepX(), 0, f.getStepZ());
    }

    public void startService(ServerLevel level, VehicleEntity v) {
        BlockPos p = pumpOf.get(v.getId());
        startFuel.put(v.getId(), v.getFuel());
        if (p != null && level.getBlockEntity(p) instanceof GenericBE be) {
            be.data.putInt("serviceVehicle", v.getId());
            be.data.putFloat("litres", 0);
            be.sync();
        }
    }

    public boolean serviceDone(ServerLevel level, VehicleEntity v) {
        return v.getFuel() >= v.spec().fuelCapacity - 0.6f;
    }

    public void finishService(ServerLevel level, VehicleEntity v, RoadNode station) {
        BlockPos p = pumpOf.remove(v.getId());
        if (p != null) {
            busy.remove(p);
            if (level.getBlockEntity(p) instanceof GenericBE be) {
                float litres = v.getFuel() - startFuel.getOrDefault(v.getId(), v.getFuel());
                float price = station != null && station.cfg.contains("price") ? station.cfg.getFloat("price") : FuelStations.price(be.data);
                be.data.putInt("serviceVehicle", 0);
                be.data.putFloat("lastLitres", litres);
                be.data.putInt("till", be.data.getInt("till") + Math.max(1, Math.round(litres * price)));
                be.sync();
            }
        }
        startFuel.remove(v.getId());
    }

    public BlockPos pumpFor(int vehicle) {
        return pumpOf.get(vehicle);
    }

    public void forget(int vehicle) {
        BlockPos p = pumpOf.remove(vehicle);
        if (p != null) busy.remove(p);
        startFuel.remove(vehicle);
    }
}
