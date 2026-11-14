package com.vehicleworks.client;

import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleRaycast;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Replaces the vanilla crosshair target with a vehicle block when one is closer. */
public final class ClientPick {
    private ClientPick() {}

    public static void refine(float partial) {
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        if (cam == null || mc.level == null || mc.player == null) return;
        if (mc.player.getVehicle() instanceof VehicleEntity) return; // seated: mouse buttons are fire controls
        Vec3 eye = cam.getEyePosition(partial);
        Vec3 look = cam.getViewVector(partial);
        double reach = mc.player.blockInteractionRange();
        VehicleRaycast.Hit h = VehicleRaycast.raycast(mc.level, eye, eye.add(look.scale(reach)), null);
        if (h == null) return;
        HitResult cur = mc.hitResult;
        double curDist = cur == null || cur.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : cur.getLocation().distanceTo(eye);
        if (h.distance() <= curDist + 1e-3) {
            mc.hitResult = new VehicleHitResult(h);
            mc.crosshairPickEntity = null;
        }
    }
}
