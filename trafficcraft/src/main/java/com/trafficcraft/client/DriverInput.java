package com.trafficcraft.client;

import com.trafficcraft.net.VehicleStatePayload;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleInput;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Reads WASD/space from the local player while driving and sends the state packet. */
public final class DriverInput {
    public static VehicleInput read(VehicleEntity v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getVehicle() != v || mc.screen != null && !(mc.screen instanceof com.trafficcraft.client.gui.VehicleScreen)) {
            return VehicleInput.NONE;
        }
        var in = mc.player.input;
        return new VehicleInput(in.forwardImpulse, in.leftImpulse, in.jumping);
    }

    public static void sendState(VehicleEntity v) {
        int bits = v.flags() & VehicleEntity.CLIENT_BITS;
        PacketDistributor.sendToServer(new VehicleStatePayload(v.getId(), v.speed, v.steer, v.rpm, v.throttle, v.gear, bits));
    }
}
