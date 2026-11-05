package com.gunsmod;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings, in config/gunsmod-common.toml (gameplay: the server's copy decides) and config/gunsmod-client.toml (looks).
 * Every getter falls back to the default while the file is not loaded yet, so nothing breaks during start-up.
 */
public final class Config {
    private Config() {}

    public static final ModConfigSpec COMMON;
    public static final ModConfigSpec CLIENT;

    private static final ModConfigSpec.DoubleValue AI_ACCURACY, AI_REACTION, WEAPON_DAMAGE, VEHICLE_DURABILITY, AIRCRAFT_SPEED, MISSILE_SPEED,
            MISSILE_AGILITY, RADAR_RANGE, EXPLOSION_POWER, SOLDIER_SPAWN, VEHICLE_SPAWN, BALLISTIC_DROP;
    private static final ModConfigSpec.BooleanValue FRIENDLY_FIRE, BLOCK_DESTRUCTION, BALLISTICS, RADAR_NETWORK, BOT_DRONES;
    private static final ModConfigSpec.IntValue AI_THINK_DISTANCE, SQUAD_RADIUS;
    private static final ModConfigSpec.DoubleValue PARTICLES;
    private static final ModConfigSpec.IntValue LOD_DISTANCE;
    private static final ModConfigSpec.BooleanValue COCKPIT_LOCK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("ai");
        AI_ACCURACY = b.comment("Soldier aim: 1 = normal, 2 = twice as accurate, 0.5 = half").defineInRange("accuracy", 1.0, 0.1, 4.0);
        AI_REACTION = b.comment("Soldier reaction speed: 1 = normal, higher reacts faster").defineInRange("reaction", 1.0, 0.25, 4.0);
        AI_THINK_DISTANCE = b.comment("Soldiers further than this from every player think less often (blocks)").defineInRange("thinkDistance", 128, 32, 1024);
        SQUAD_RADIUS = b.comment("How far squad orders reach (blocks)").defineInRange("squadRadius", 48, 8, 256);
        BOT_DRONES = b.comment("Some soldiers carry FPV drones and Shaheds").define("droneOperators", true);
        b.pop();
        b.push("combat");
        WEAPON_DAMAGE = b.comment("Bullet damage multiplier").defineInRange("weaponDamage", 1.0, 0.1, 10.0);
        VEHICLE_DURABILITY = b.comment("Vehicle durability multiplier (2 = takes half the damage)").defineInRange("vehicleDurability", 1.0, 0.1, 10.0);
        FRIENDLY_FIRE = b.comment("Bullets hit team mates").define("friendlyFire", false);
        BALLISTICS = b.comment("Bullets fly with travel time and drop, go through thin blocks and ricochet; off = instant hits").define("ballistics", true);
        BALLISTIC_DROP = b.comment("Bullet drop multiplier").defineInRange("bulletDrop", 1.0, 0.0, 4.0);
        b.pop();
        b.push("explosions");
        EXPLOSION_POWER = b.comment("Explosion power multiplier").defineInRange("power", 1.0, 0.1, 4.0);
        BLOCK_DESTRUCTION = b.comment("Explosions break blocks (off keeps the terrain intact)").define("blockDestruction", false);
        b.pop();
        b.push("air");
        AIRCRAFT_SPEED = b.comment("Aircraft top speed multiplier").defineInRange("aircraftSpeed", 1.0, 0.5, 2.0);
        MISSILE_SPEED = b.comment("Guided missile speed multiplier").defineInRange("missileSpeed", 1.0, 0.5, 2.0);
        MISSILE_AGILITY = b.comment("Guided missile turn rate multiplier").defineInRange("missileAgility", 1.0, 0.25, 3.0);
        RADAR_RANGE = b.comment("Radar range multiplier").defineInRange("radarRange", 1.0, 0.25, 4.0);
        RADAR_NETWORK = b.comment("Radars of a team share their tracks with every launcher of the team in range").define("radarNetwork", true);
        b.pop();
        b.push("spawning");
        SOLDIER_SPAWN = b.comment("Soldier spawner speed multiplier").defineInRange("soldierRate", 1.0, 0.1, 10.0);
        VEHICLE_SPAWN = b.comment("Vehicle spawner speed multiplier").defineInRange("vehicleRate", 1.0, 0.1, 10.0);
        b.pop();
        COMMON = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        c.push("graphics");
        PARTICLES = c.comment("Particle density multiplier for explosions, smoke and muzzle fire").defineInRange("particles", 1.0, 0.1, 2.0);
        LOD_DISTANCE = c.comment("Vehicles further than this are drawn with their simple model (blocks)").defineInRange("lodDistance", 160, 32, 1024);
        c.pop();
        c.push("controls");
        COCKPIT_LOCK = c.comment("Jets start in the locked cockpit view").define("cockpitLock", true);
        c.pop();
        CLIENT = c.build();
    }

    private static double d(ModConfigSpec.DoubleValue v, double def) {
        try {
            return v.get();
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static boolean b(ModConfigSpec.BooleanValue v, boolean def) {
        try {
            return v.get();
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static int i(ModConfigSpec.IntValue v, int def) {
        try {
            return v.get();
        } catch (RuntimeException e) {
            return def;
        }
    }

    public static float aiAccuracy() { return (float) d(AI_ACCURACY, 1.0); }
    public static float aiReaction() { return (float) d(AI_REACTION, 1.0); }
    public static int aiThinkDistance() { return i(AI_THINK_DISTANCE, 128); }
    public static int squadRadius() { return i(SQUAD_RADIUS, 48); }
    public static boolean botDrones() { return b(BOT_DRONES, true); }
    public static float weaponDamage() { return (float) d(WEAPON_DAMAGE, 1.0); }
    public static float vehicleDurability() { return (float) d(VEHICLE_DURABILITY, 1.0); }
    public static boolean friendlyFire() { return b(FRIENDLY_FIRE, false); }
    public static boolean ballistics() { return b(BALLISTICS, true); }
    public static double bulletDrop() { return d(BALLISTIC_DROP, 1.0); }
    public static float explosionPower() { return (float) d(EXPLOSION_POWER, 1.0); }
    public static boolean blockDestruction() { return b(BLOCK_DESTRUCTION, false); }
    public static double aircraftSpeed() { return d(AIRCRAFT_SPEED, 1.0); }
    public static double missileSpeed() { return d(MISSILE_SPEED, 1.0); }
    public static double missileAgility() { return d(MISSILE_AGILITY, 1.0); }
    public static double radarRange() { return d(RADAR_RANGE, 1.0); }
    public static boolean radarNetwork() { return b(RADAR_NETWORK, true); }
    public static double soldierSpawn() { return d(SOLDIER_SPAWN, 1.0); }
    public static double vehicleSpawn() { return d(VEHICLE_SPAWN, 1.0); }
    public static float particles() { return (float) d(PARTICLES, 1.0); }
    public static int lodDistance() { return i(LOD_DISTANCE, 160); }
    public static boolean cockpitLock() { return b(COCKPIT_LOCK, true); }
}
