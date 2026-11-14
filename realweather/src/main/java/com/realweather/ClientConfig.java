package com.realweather;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Visual and audio settings, editable from the Mods screen (Config button). */
public final class ClientConfig {
    /** The *_3D levels draw real volumetric (ray-marched) clouds instead of the cloud sprites, at that level's detail. */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA, ULTRA_3D, LOW_3D, MEDIUM_3D, HIGH_3D }

    /** Tornado graphics have their own setting, independent of the rest of the weather. */
    public enum TornadoQuality { LOW, MEDIUM, HIGH, ULTRA }

    /** Ray-traced space graphics (planets of blocks, gas giants, stars, nebulae, the black hole): AUTO follows the frame rate. */
    public enum SpaceQuality { AUTO, LOW, MEDIUM, HIGH, ULTRA, OFF, ULTRA_KILLER }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<Quality> QUALITY;
    public static final ModConfigSpec.BooleanValue CUSTOM_PRECIPITATION;
    public static final ModConfigSpec.BooleanValue RAIN_DROPS_3D;
    public static final ModConfigSpec.BooleanValue SPLASHES;
    public static final ModConfigSpec.BooleanValue PUDDLES;
    public static final ModConfigSpec.BooleanValue REALISTIC_WATER;
    public static final ModConfigSpec.BooleanValue SOUNDS;
    public static final ModConfigSpec.BooleanValue FOG;
    public static final ModConfigSpec.BooleanValue LIGHTNING_FLASH;
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ModConfigSpec.BooleanValue REALISTIC_LIGHTNING;
    public static final ModConfigSpec.EnumValue<TornadoQuality> TORNADO_QUALITY;
    public static final ModConfigSpec.BooleanValue CLOUDS;
    public static final ModConfigSpec.BooleanValue MAX_OPTIMIZE;
    public static final ModConfigSpec.EnumValue<SpaceQuality> SPACE_QUALITY;
    public static final ModConfigSpec.IntValue EVENT_RATE;
    public static final ModConfigSpec.DoubleValue EVENT_LENGTH;
    public static final ModConfigSpec.DoubleValue EVENT_INTENSITY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        QUALITY = b.comment("Weather detail: LOW, MEDIUM, HIGH, ULTRA, or LOW_3D, MEDIUM_3D, HIGH_3D, ULTRA_3D for real volumetric 3D clouds. Lower settings use fewer particles and clouds; LOW is best for phones and weak PCs. The 3D levels need a good graphics card (LOW_3D is the lightest).")
                .translation("realweather.config.quality").defineEnum("quality", Quality.MEDIUM);
        MAX_OPTIMIZE = b.comment("Max optimize: clouds, storms, tornado parts, rain sheets and puddles that are entirely off the screen are not built or drawn. Everything you can see (clouds, tornadoes, rain, lighting, sounds) stays exactly the same; only the work for what you can't see is saved.")
                .translation("realweather.config.max_optimize").define("maxOptimize", true);
        CLOUDS = b.comment("Realistic 3D clouds: fair-weather cumulus, overcast decks and storm clouds (replaces the vanilla clouds).")
                .translation("realweather.config.clouds").define("realisticClouds", true);
        CUSTOM_PRECIPITATION = b.comment("Draw the realistic rain, snow and sandstorm instead of the vanilla weather.")
                .translation("realweather.config.custom_precipitation").define("customPrecipitation", true);
        RAIN_DROPS_3D = b.comment("Individual falling drops close to the camera.").translation("realweather.config.rain_drops_3d")
                .define("rainDrops3d", true);
        SPLASHES = b.comment("Splashes on the ground and ripples on water.").translation("realweather.config.splashes")
                .define("splashes", true);
        PUDDLES = b.comment("Puddles that form during rain and dry afterwards.").translation("realweather.config.puddles")
                .define("puddles", true);
        REALISTIC_WATER = b.comment("Waves, reflections and sun glints on water.").translation("realweather.config.realistic_water")
                .define("realisticWater", true);
        SOUNDS = b.comment("Realistic rain, wind, storm and thunder sounds.").translation("realweather.config.sounds")
                .define("sounds", true);
        FOG = b.comment("Lower visibility in heavy rain, blizzards and sandstorms.").translation("realweather.config.fog")
                .define("fog", true);
        LIGHTNING_FLASH = b.comment("Bright flash on screen when lightning strikes.").translation("realweather.config.lightning_flash")
                .define("lightningFlash", true);
        CAMERA_SHAKE = b.comment("Slight camera shake from very close thunder.").translation("realweather.config.camera_shake")
                .define("cameraShake", true);
        REALISTIC_LIGHTNING = b.comment("Branching, glowing lightning bolts that light up the clouds.").translation("realweather.config.realistic_lightning")
                .define("realisticLightning", true);
        b.comment("Tornado graphics (separate from the rain and snow settings).").translation("realweather.config.tornado").push("tornado");
        TORNADO_QUALITY = b.comment("LOW, MEDIUM, HIGH or ULTRA.").translation("realweather.config.tornado_quality")
                .defineEnum("tornadoGraphics", TornadoQuality.MEDIUM);
        b.pop();
        SPACE_QUALITY = b.comment("Space graphics (telescope, planet map, travel): AUTO (sharpness follows the frame rate; safe on phones), LOW, MEDIUM, HIGH, ULTRA, ULTRA_KILLER (this mod's own most detailed mode: full resolution, far more ray steps, bloom, denser star clusters; for strong GPUs), or OFF (no ray tracing at all, for very weak devices).")
                .translation("realweather.config.space_quality").defineEnum("spaceGraphics", SpaceQuality.AUTO);
        b.comment("Cosmic events out in space (simulations, not predictions).").translation("realweather.config.events").push("cosmicEvents");
        EVENT_RATE = b.comment("How many cosmic events there are (per game day).").translation("realweather.config.event_rate")
                .defineInRange("eventsPerDay", 8, 1, 24);
        EVENT_LENGTH = b.comment("How long each lasts (1 is normal; more to have longer to watch).").translation("realweather.config.event_length")
                .defineInRange("eventLength", 1.0, 0.25, 4.0);
        EVENT_INTENSITY = b.comment("How bright and violent they look.").translation("realweather.config.event_intensity")
                .defineInRange("eventIntensity", 1.0, 0.5, 2.0);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}

    public static float qualityScale() {
        return switch (QUALITY.get()) {
            case LOW -> 0.35F;
            case MEDIUM -> 0.6F;
            case HIGH -> 0.85F;
            case ULTRA, ULTRA_3D -> 1.0F;
            case LOW_3D -> 0.35F;
            case MEDIUM_3D -> 0.6F;
            case HIGH_3D -> 0.85F;
        };
    }

    /** Weather quality, 0 = LOW .. 3 = ULTRA (ULTRA_3D counts as ULTRA). */
    public static int quality() {
        return switch (QUALITY.get()) {
            case LOW, LOW_3D -> 0;
            case MEDIUM, MEDIUM_3D -> 1;
            case HIGH, HIGH_3D -> 2;
            case ULTRA, ULTRA_3D -> 3;
        };
    }

    /** Volumetric 3D clouds (ULTRA_3D). */
    public static boolean clouds3d() {
        return QUALITY.get().name().endsWith("_3D");
    }

    /** Skip building and drawing what is wholly off the screen (the picture stays exactly the same). */
    public static boolean maxOptimize() {
        return MAX_OPTIMIZE.get();
    }

    /** 0 = LOW .. 3 = ULTRA. */
    public static int tornadoQuality() {
        return TORNADO_QUALITY.get().ordinal();
    }
}
