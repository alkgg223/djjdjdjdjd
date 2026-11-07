package com.arkragnarok;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings (config/arkragnarok-common.toml, also changed in game with /ark spawning and /ark density):
 * whether wild dinosaurs spawn by themselves, how many may live in each 3x3-chunk area, the highest wild level and
 * how rare high levels are.
 */
public final class ArkConfig {
    private ArkConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SPAWNING;
    public static final ModConfigSpec.IntValue DENSITY;
    public static final ModConfigSpec.IntValue INTERVAL;
    public static final ModConfigSpec.IntValue MAX_LEVEL;
    public static final ModConfigSpec.DoubleValue HIGH_LEVEL_CHANCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("spawning");
        SPAWNING = b.comment("Wild dinosaurs spawn by themselves around players").define("enabled", true);
        DENSITY = b.comment("How many wild dinosaurs may live in one 3x3-chunk area (48 x 48 blocks); 0 = none")
                .defineInRange("per3x3Chunks", 4, 0, 40);
        INTERVAL = b.comment("Ticks between spawn attempts near each player (20 ticks = 1 second)").defineInRange("intervalTicks", 80, 10, 2400);
        b.pop();
        b.push("levels");
        MAX_LEVEL = b.comment("Highest level a wild dinosaur can have").defineInRange("maxWildLevel", 500, 1, 500);
        HIGH_LEVEL_CHANCE = b.comment("Chance that a wild dinosaur is above level 150 (the higher, the rarer: most of these are 150-300)")
                .defineInRange("highLevelChance", 0.15, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }
}
