package com.trafficcraft.vehicle;

/** forward: +1 accelerate, -1 brake / reverse. left: +1 steer left. */
public record VehicleInput(float forward, float left, boolean handbrake) {
    public static final VehicleInput NONE = new VehicleInput(0, 0, false);
    public static final VehicleInput PARK = new VehicleInput(0, 0, true);
}
