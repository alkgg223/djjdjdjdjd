package com.vehicleworks;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server (gameplay/physics) and client (rendering/HUD) configuration. */
public final class Config {
    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        SERVER = new Server(b);
        SERVER_SPEC = b.build();
        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        CLIENT = new Client(c);
        CLIENT_SPEC = c.build();
    }

    public static final class Server {
        public final ModConfigSpec.IntValue maxBlocks;
        public final ModConfigSpec.IntValue maxExtent;
        public final ModConfigSpec.IntValue substeps;
        public final ModConfigSpec.IntValue maxSubsteps;
        public final ModConfigSpec.DoubleValue gravity;
        public final ModConfigSpec.DoubleValue altitudeScale;
        public final ModConfigSpec.DoubleValue blockMassScale;
        public final ModConfigSpec.DoubleValue gasLiftScale;
        public final ModConfigSpec.DoubleValue windSpeed;
        public final ModConfigSpec.DoubleValue turbulence;
        public final ModConfigSpec.DoubleValue waveAmplitude;
        public final ModConfigSpec.BooleanValue crashDamage;
        public final ModConfigSpec.DoubleValue crashDamageScale;
        public final ModConfigSpec.DoubleValue crashSpeedThreshold;
        public final ModConfigSpec.BooleanValue weaponsBreakBlocks;
        public final ModConfigSpec.DoubleValue weaponDamageScale;
        public final ModConfigSpec.DoubleValue maxSpeed;
        public final ModConfigSpec.DoubleValue fuelConsumptionScale;
        public final ModConfigSpec.BooleanValue requireFuel;
        public final ModConfigSpec.DoubleValue crushDepth;
        public final ModConfigSpec.DoubleValue structuralStrength;
        public final ModConfigSpec.BooleanValue weaponsEnabled;
        public final ModConfigSpec.IntValue syncRangeBlocks;

        Server(ModConfigSpec.Builder b) {
            b.push("construction");
            maxBlocks = b.comment("Maximum number of blocks in one vehicle").defineInRange("maxBlocks", 16384, 1, 200000);
            maxExtent = b.comment("Maximum size of a vehicle along any axis (blocks)").defineInRange("maxExtent", 192, 4, 1024);
            b.pop();
            b.push("physics");
            substeps = b.comment("Base physics substeps per server tick (fixed timestep = 1/20/substeps s)").defineInRange("substeps", 4, 1, 16);
            maxSubsteps = b.comment("Maximum adaptive substeps for fast vehicles").defineInRange("maxSubsteps", 24, 1, 64);
            gravity = b.defineInRange("gravity", 9.81, 0.0, 50.0);
            altitudeScale = b.comment("Metres of atmosphere altitude per block above sea level (air gets thinner with height)").defineInRange("altitudeScale", 8.0, 0.0, 200.0);
            blockMassScale = b.comment("Multiplier on block masses").defineInRange("blockMassScale", 1.0, 0.05, 20.0);
            gasLiftScale = b.comment("World-scale factor for balloon / gas cell lift (Minecraft balloons are tiny compared to real ones)").defineInRange("gasLiftScale", 6.0, 0.1, 100.0);
            maxSpeed = b.comment("Velocity cap (m/s) for stability").defineInRange("maxSpeed", 220.0, 10.0, 1000.0);
            b.pop();
            b.push("environment");
            windSpeed = b.comment("Mean wind speed (m/s) at sea level in clear weather").defineInRange("windSpeed", 3.0, 0.0, 60.0);
            turbulence = b.comment("Turbulence intensity multiplier").defineInRange("turbulence", 1.0, 0.0, 10.0);
            waveAmplitude = b.comment("Ocean wave amplitude (m) in clear weather; doubled in storms").defineInRange("waveAmplitude", 0.25, 0.0, 4.0);
            crushDepth = b.comment("Submarine hull crush depth (m below surface) for standard hulls").defineInRange("crushDepth", 90.0, 5.0, 2000.0);
            b.pop();
            b.push("damage");
            crashDamage = b.define("crashDamage", true);
            crashDamageScale = b.defineInRange("crashDamageScale", 1.0, 0.0, 20.0);
            crashSpeedThreshold = b.comment("Impact speed (m/s) below which collisions do no damage").defineInRange("crashSpeedThreshold", 9.0, 0.0, 200.0);
            structuralStrength = b.comment("Multiplier on aerodynamic load limits of wings").defineInRange("structuralStrength", 1.0, 0.1, 100.0);
            b.pop();
            b.push("weapons");
            weaponsEnabled = b.define("weaponsEnabled", true);
            weaponsBreakBlocks = b.comment("Whether explosive weapons break terrain blocks").define("weaponsBreakBlocks", false);
            weaponDamageScale = b.defineInRange("weaponDamageScale", 1.0, 0.0, 100.0);
            b.pop();
            b.push("fuel");
            requireFuel = b.define("requireFuel", true);
            fuelConsumptionScale = b.comment("Multiplier on fuel use (1 = realistic)").defineInRange("fuelConsumptionScale", 0.5, 0.0, 100.0);
            b.pop();
            b.push("network");
            syncRangeBlocks = b.comment("Distance within which full-rate state sync is sent").defineInRange("syncRangeBlocks", 160, 16, 1024);
            b.pop();
        }
    }

    public static final class Client {
        public final ModConfigSpec.IntValue lodDistance;
        public final ModConfigSpec.IntValue farLodDistance;
        public final ModConfigSpec.BooleanValue renderBlockEntities;
        public final ModConfigSpec.BooleanValue cameraRoll;
        public final ModConfigSpec.DoubleValue hudScale;
        public final ModConfigSpec.BooleanValue mouseAimDefault;
        public final ModConfigSpec.DoubleValue engineVolume;

        Client(ModConfigSpec.Builder b) {
            lodDistance = b.comment("Beyond this distance animated parts and block entities are skipped").defineInRange("lodDistance", 128, 16, 1024);
            farLodDistance = b.comment("Beyond this distance vehicles render as a simplified silhouette mesh").defineInRange("farLodDistance", 320, 32, 4096);
            renderBlockEntities = b.define("renderBlockEntities", true);
            cameraRoll = b.comment("Roll the first-person camera with the aircraft").define("cameraRoll", true);
            hudScale = b.defineInRange("hudScale", 1.0, 0.5, 2.0);
            mouseAimDefault = b.define("mouseAimDefault", false);
            engineVolume = b.defineInRange("engineVolume", 1.0, 0.0, 2.0);
        }
    }

    private Config() {}
}
