package com.realweather;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Visual and audio settings, editable from the Mods screen (Config button). */
public final class ClientConfig {
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Tornado graphics have their own setting, independent of the rest of the weather. */
    public enum TornadoQuality { LOW, MEDIUM, HIGH, ULTRA }

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

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        QUALITY = b.comment("Weather detail: LOW, MEDIUM, HIGH or ULTRA. Lower settings use fewer particles and clouds; LOW is best for phones and weak PCs.")
                .translation("realweather.config.quality").defineEnum("quality", Quality.MEDIUM);
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
        SPEC = b.build();
    }

    private ClientConfig() {}

    public static float qualityScale() {
        return switch (QUALITY.get()) {
            case LOW -> 0.35F;
            case MEDIUM -> 0.6F;
            case HIGH -> 0.85F;
            case ULTRA -> 1.0F;
        };
    }

    /** Weather quality, 0 = LOW .. 3 = ULTRA. */
    public static int quality() {
        return QUALITY.get().ordinal();
    }

    /** 0 = LOW .. 3 = ULTRA. */
    public static int tornadoQuality() {
        return TORNADO_QUALITY.get().ordinal();
    }
}
