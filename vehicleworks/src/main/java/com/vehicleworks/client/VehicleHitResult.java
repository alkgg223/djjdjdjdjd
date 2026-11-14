package com.vehicleworks.client;

import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleRaycast;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Vector3d;

/** Crosshair target: a specific block of a vehicle. */
public final class VehicleHitResult extends EntityHitResult {
    private final VehicleRaycast.Hit hit;

    public VehicleHitResult(VehicleRaycast.Hit hit) {
        super(hit.vehicle(), hit.location());
        this.hit = hit;
    }

    public VehicleEntity vehicle() {
        return hit.vehicle();
    }

    public long block() {
        return hit.block();
    }

    public Direction face() {
        return hit.face();
    }

    public Vector3d localHit() {
        return hit.localHit();
    }
}
