package com.trafficcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TCConfig {
    public static final ModConfigSpec SPEC;
    // vehicles
    public static final ModConfigSpec.DoubleValue STEP_HEIGHT;
    public static final ModConfigSpec.DoubleValue FUEL_CONSUMPTION;
    public static final ModConfigSpec.BooleanValue VEHICLE_DAMAGE_ENTITIES;
    public static final ModConfigSpec.BooleanValue DYNAMIC_HEADLIGHTS;
    public static final ModConfigSpec.DoubleValue SEATED_SCALE;
    public static final ModConfigSpec.BooleanValue REQUIRE_FUEL;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.DoubleValue ENGINE_VOLUME;
    public static final ModConfigSpec.IntValue RENDER_DISTANCE;
    public static final ModConfigSpec.IntValue INTERIOR_DISTANCE;
    public static final ModConfigSpec.BooleanValue HEADLIGHT_BEAMS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("vehicles");
        STEP_HEIGHT = b.comment("How high (blocks) a vehicle can drive up, e.g. curbs and slabs").defineInRange("stepHeight", 1.05, 0.0, 1.6);
        FUEL_CONSUMPTION = b.comment("Fuel consumption multiplier (0 = no consumption)").defineInRange("fuelConsumption", 1.0, 0.0, 20.0);
        REQUIRE_FUEL = b.comment("Vehicles stop when their tank is empty").define("requireFuel", true);
        VEHICLE_DAMAGE_ENTITIES = b.comment("Moving vehicles push and damage mobs/players they hit").define("damageEntities", true);
        DYNAMIC_HEADLIGHTS = b.comment("Headlights light up the world in front of player-driven vehicles at night (places temporary light blocks)")
                .define("dynamicHeadlights", true);
        SEATED_SCALE = b.comment("Player scale while seated in a vehicle (lets the player fit realistically in the cabin)")
                .defineInRange("seatedScale", 0.7, 0.3, 1.0);
        b.pop();
        b.push("client");
        SHOW_HUD = b.comment("Show the driving HUD (speed, gear, fuel, indicators)").define("showHud", true);
        ENGINE_VOLUME = b.comment("Engine sound volume").defineInRange("engineVolume", 1.0, 0.0, 2.0);
        RENDER_DISTANCE = b.comment("Max distance (blocks) at which vehicles are drawn").defineInRange("renderDistance", 160, 32, 512);
        INTERIOR_DISTANCE = b.comment("Distance (blocks) within which the detailed model and interior are drawn").defineInRange("detailDistance", 24, 4, 96);
        HEADLIGHT_BEAMS = b.comment("Draw visible headlight beams and light glows").define("headlightBeams", true);
        b.pop();
        SPEC = b.build();
    }
}
