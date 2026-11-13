package com.realweather.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Network {
    private Network() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // Optional so vanilla clients (and servers without the mod) can still connect.
        event.registrar("1").optional()
                .playToClient(WeatherSyncPayload.TYPE, WeatherSyncPayload.CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.realweather.client.ClientWeather.onSync(payload)))
                .playToClient(StormSyncPayload.TYPE, StormSyncPayload.CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.realweather.client.ClientStorms.onSync(payload)))
                .playToClient(TornadoSyncPayload.TYPE, TornadoSyncPayload.CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.realweather.client.tornado.ClientTornadoes.onSync(payload)))
                .playToClient(TsunamiSyncPayload.TYPE, TsunamiSyncPayload.CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.realweather.client.tsunami.ClientTsunamis.onSync(payload)))
                .playToClient(QuakeSyncPayload.TYPE, QuakeSyncPayload.CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.realweather.client.quake.ClientQuakes.onSync(payload)))
                .playToClient(EclipseSyncPayload.TYPE, EclipseSyncPayload.CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.realweather.client.sky.ClientEclipse.onSync(payload)))
                .playToClient(SkyEventsPayload.TYPE, SkyEventsPayload.CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.realweather.client.sky.ClientSkyEvents.onSync(payload)))
                .playToServer(VehicleInputPayload.TYPE, VehicleInputPayload.CODEC, VehicleInputPayload::handle);
    }
}
