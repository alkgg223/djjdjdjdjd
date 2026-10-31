package com.realweather.device;

import net.minecraft.core.BlockPos;

/** Calls from common block code into the client (only ever made on the client side). */
final class DeviceClientHooks {
    private DeviceClientHooks() {}

    static void openRadar(BlockPos pos) {
        com.realweather.client.radar.RadarClient.openRadar(pos);
    }

    static void openMonitor(BlockPos pos, int mode) {
        com.realweather.client.radar.RadarClient.openMonitor(pos, mode);
    }

    static void radarLoaded(BlockPos pos, boolean loaded) {
        com.realweather.client.radar.RadarClient.radarLoaded(pos, loaded);
    }

    static void monitorRemoved(BlockPos pos) {
        com.realweather.client.radar.RadarClient.monitorRemoved(pos);
    }
}
