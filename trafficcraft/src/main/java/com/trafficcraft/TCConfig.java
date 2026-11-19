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
    // traffic
    public static final ModConfigSpec.IntValue MAX_AI_VEHICLES;
    public static final ModConfigSpec.IntValue MAX_PEDESTRIANS;
    public static final ModConfigSpec.DoubleValue TRAFFIC_DENSITY;
    public static final ModConfigSpec.DoubleValue PED_DENSITY;
    public static final ModConfigSpec.IntValue DEFAULT_SPAWN_INTERVAL;
    public static final ModConfigSpec.IntValue DEFAULT_PED_INTERVAL;
    public static final ModConfigSpec.IntValue SIM_DISTANCE;
    public static final ModConfigSpec.IntValue DESPAWN_DISTANCE;
    public static final ModConfigSpec.DoubleValue AGGRESSION;
    public static final ModConfigSpec.BooleanValue HORNS;
    public static final ModConfigSpec.IntValue HORN_PATIENCE;
    public static final ModConfigSpec.DoubleValue OVERTAKE_CHANCE;
    public static final ModConfigSpec.DoubleValue BREAKDOWN_CHANCE;
    public static final ModConfigSpec.BooleanValue AI_FUEL;
    public static final ModConfigSpec.DoubleValue PARK_CHANCE;
    public static final ModConfigSpec.DoubleValue LIGHT_GREEN;
    public static final ModConfigSpec.IntValue BORDER_INSPECTION;
    public static final ModConfigSpec.DoubleValue BORDER_DENY_CHANCE;
    public static final ModConfigSpec.IntValue DEFAULT_ROAD_WIDTH;
    public static final ModConfigSpec.IntValue DEFAULT_SPEED;
    public static final ModConfigSpec.BooleanValue STREET_LAMPS_AUTO;
    public static final ModConfigSpec.IntValue AI_TICK_NEAR;
    public static final ModConfigSpec.IntValue AI_TICK_FAR;

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
        b.push("traffic");
        MAX_AI_VEHICLES = b.comment("Maximum AI vehicles per dimension").defineInRange("maxAiVehicles", 60, 0, 600);
        MAX_PEDESTRIANS = b.comment("Maximum pedestrians per dimension").defineInRange("maxPedestrians", 60, 0, 600);
        TRAFFIC_DENSITY = b.comment("Global traffic density multiplier").defineInRange("trafficDensity", 1.0, 0.0, 5.0);
        PED_DENSITY = b.comment("Global pedestrian density multiplier").defineInRange("pedestrianDensity", 1.0, 0.0, 5.0);
        DEFAULT_SPAWN_INTERVAL = b.comment("Default vehicle spawn interval of new spawn nodes (seconds)").defineInRange("vehicleSpawnInterval", 8, 1, 600);
        DEFAULT_PED_INTERVAL = b.comment("Default pedestrian spawn interval (seconds)").defineInRange("pedestrianSpawnInterval", 10, 1, 600);
        SIM_DISTANCE = b.comment("AI is simulated within this distance (blocks) of a player").defineInRange("simulationDistance", 128, 32, 512);
        DESPAWN_DISTANCE = b.comment("AI vehicles/pedestrians further than this from every player are removed").defineInRange("despawnDistance", 160, 48, 640);
        AGGRESSION = b.comment("AI driving aggression 0 (very patient) .. 1 (very aggressive)").defineInRange("aggression", 0.4, 0.0, 1.0);
        HORNS = b.comment("AI drivers honk when blocked").define("horns", true);
        HORN_PATIENCE = b.comment("Seconds a normal driver waits behind a blocking vehicle before honking").defineInRange("hornPatience", 8, 1, 120);
        OVERTAKE_CHANCE = b.comment("Chance that a driver overtakes a slow vehicle when allowed").defineInRange("overtakeChance", 0.5, 0.0, 1.0);
        BREAKDOWN_CHANCE = b.comment("Chance per vehicle per minute of a breakdown (hazard lights, stops for a while)").defineInRange("breakdownChance", 0.003, 0.0, 1.0);
        AI_FUEL = b.comment("AI vehicles use fuel and visit gas stations").define("aiFuel", true);
        PARK_CHANCE = b.comment("Chance an AI vehicle passing a parking node parks there").defineInRange("parkChance", 0.3, 0.0, 1.0);
        LIGHT_GREEN = b.comment("Default green time of traffic lights (seconds)").defineInRange("lightGreenSeconds", 14.0, 3.0, 180.0);
        BORDER_INSPECTION = b.comment("Default border inspection time (seconds)").defineInRange("borderInspectionSeconds", 6, 1, 120);
        BORDER_DENY_CHANCE = b.comment("Chance an AI vehicle is sent to secondary inspection").defineInRange("borderDenyChance", 0.1, 0.0, 1.0);
        DEFAULT_ROAD_WIDTH = b.comment("Default road width (blocks) for the Road Planner").defineInRange("defaultRoadWidth", 5, 2, 30);
        DEFAULT_SPEED = b.comment("Default speed limit (km/h) for new roads").defineInRange("defaultSpeedLimit", 50, 10, 200);
        STREET_LAMPS_AUTO = b.comment("Street lamps switch on automatically at night").define("streetLampsAuto", true);
        AI_TICK_NEAR = b.comment("AI update interval (ticks) near players (performance)").defineInRange("aiTickNear", 1, 1, 10);
        AI_TICK_FAR = b.comment("AI update interval (ticks) for distant vehicles (performance)").defineInRange("aiTickFar", 4, 1, 20);
        b.pop();
        SPEC = b.build();
    }
}
