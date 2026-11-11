package com.rpbusiness.core;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CUSTOMERS_ENABLED;
    public static final ModConfigSpec.IntValue MAX_CUSTOMERS;
    public static final ModConfigSpec.IntValue SPAWN_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue SPAWN_DISTANCE;
    public static final ModConfigSpec.IntValue ACTIVE_RANGE;
    public static final ModConfigSpec.IntValue STARTING_CASH;
    public static final ModConfigSpec.IntValue MAX_BUSINESS_SIZE;
    public static final ModConfigSpec.DoubleValue PATIENCE_MULTIPLIER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("customers");
        CUSTOMERS_ENABLED = b.comment("Whether NPC customers spawn near your open businesses").define("enabled", true);
        MAX_CUSTOMERS = b.comment("Maximum number of NPC customers alive at the same time").defineInRange("maxCustomers", 10, 0, 60);
        SPAWN_INTERVAL_SECONDS = b.comment("Seconds between customer spawn attempts (lower = busier)").defineInRange("spawnIntervalSeconds", 25, 3, 600);
        SPAWN_DISTANCE = b.comment("How far from the business entrance customers appear").defineInRange("spawnDistance", 22, 8, 64);
        ACTIVE_RANGE = b.comment("Businesses farther than this from the player do not receive customers (blocks)").defineInRange("activeRange", 128, 32, 512);
        PATIENCE_MULTIPLIER = b.comment("Multiplier for how long customers are willing to wait").defineInRange("patienceMultiplier", 1.0, 0.2, 10.0);
        b.pop();
        b.push("economy");
        STARTING_CASH = b.comment("Cash given once to a player the first time they join a world").defineInRange("startingCash", 500, 0, 1000000);
        b.pop();
        b.push("business");
        MAX_BUSINESS_SIZE = b.comment("Maximum width/length of a business area in blocks").defineInRange("maxSize", 64, 8, 128);
        b.pop();
        SPEC = b.build();
    }

    private Config() {}
}
