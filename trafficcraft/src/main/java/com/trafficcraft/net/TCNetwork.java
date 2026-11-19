package com.trafficcraft.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class TCNetwork {
    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");
        r.playToServer(VehicleControlPayload.TYPE, VehicleControlPayload.CODEC, VehicleControlPayload::handle);
        r.playToServer(VehicleStatePayload.TYPE, VehicleStatePayload.CODEC, VehicleStatePayload::handle);
    }
}
