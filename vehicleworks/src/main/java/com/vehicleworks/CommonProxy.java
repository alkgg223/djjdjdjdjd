package com.vehicleworks;

import com.vehicleworks.network.Payloads;
import com.vehicleworks.vehicle.VehicleEntity;

/** Side-specific hooks; the client replaces this with a client implementation. */
public class CommonProxy {
    public void initClientState(VehicleEntity v) {}
    public void clientTick(VehicleEntity v) {}
    public void disposeClientState(VehicleEntity v) {}
    public void onState(Payloads.State p) {}
    public void onTelemetry(Payloads.Telemetry p) {}
    public void onBlocks(Payloads.Blocks p) {}
    public void onSeats(Payloads.Seats p) {}

    public void onCabin(Payloads.Cabin p) {}
    public void onOpenScreen(Payloads.OpenScreen p) {}
    public void onInfo(Payloads.Info p) {}
}
