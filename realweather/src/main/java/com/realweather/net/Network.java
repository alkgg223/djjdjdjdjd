package com.realweather.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Network {
    private Network() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // Optional so vanilla clients (and servers without the mod) can still connect.
        event.registrar("1").optional().playToClient(WeatherSyncPayload.TYPE, WeatherSyncPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> com.realweather.client.ClientWeather.onSync(payload)));
    }
}
