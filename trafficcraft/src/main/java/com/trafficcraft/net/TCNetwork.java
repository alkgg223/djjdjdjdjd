package com.trafficcraft.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class TCNetwork {
    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");
        r.playToServer(VehicleControlPayload.TYPE, VehicleControlPayload.CODEC, VehicleControlPayload::handle);
        r.playToServer(VehicleStatePayload.TYPE, VehicleStatePayload.CODEC, VehicleStatePayload::handle);
        r.playToClient(RoadSyncPayload.TYPE, RoadSyncPayload.CODEC, RoadSyncPayload::handle);
        r.playToServer(RoadEditPayload.TYPE, RoadEditPayload.CODEC, RoadEditPayload::handle);
        r.playToClient(OpenEditorPayload.TYPE, OpenEditorPayload.CODEC, OpenEditorPayload::handle);
    }
}
