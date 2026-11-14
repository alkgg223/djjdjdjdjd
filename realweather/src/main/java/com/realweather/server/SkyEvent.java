package com.realweather.server;

/**
 * Something happening in the sky for a while: a lunar eclipse, a meteor shower, a comet, the aurora, a satellite
 * pass, a supernova... (solar eclipses have their own manager). Everything about how it looks is worked out on the
 * client from the type, the start time and the seed, so only these are sent.
 */
public final class SkyEvent {
    public static final int LUNAR_ECLIPSE = 0, SUPERMOON = 1, METEOR_SHOWER = 2, COMET = 3, FIREBALLS = 4, CONJUNCTION = 5, ALIGNMENT = 6,
            TRANSIT = 7, OCCULTATION = 8, AURORA = 9, ISS = 10, STARLINK = 11, SUPERNOVA = 12, RECURRENT_NOVA = 13, SATELLITE_TRANSIT = 14,
            DEBRIS_REENTRY = 15, SATELLITE_FLARE = 16, AURORA_STORM = 17, SPORADIC_METEORS = 18, STAR_OCCULTATION = 19,
            TRIPLE_CONJUNCTION = 20, WANDERING_COMET = 21, CORONAL_MASS_EJECTION = 22, UPPER_LIGHTNING = 23, FAINT_COMET = 24,
            DUST_GLOW = 25, STAR_FLARES = 26, RAINBOW = 27;
    public static final String[] NAMES = {"lunar_eclipse", "supermoon", "meteor_shower", "comet", "fireballs", "conjunction", "alignment",
            "transit", "occultation", "aurora", "iss", "starlink", "supernova", "recurrent_nova", "satellite_transit", "debris_reentry",
            "satellite_flare", "aurora_storm", "sporadic_meteors", "star_occultation", "triple_conjunction", "wandering_comet",
            "coronal_mass_ejection", "upper_lightning", "faint_comet", "dust_glow", "star_flares", "rainbow"};
    /** How long each lasts, ticks. */
    public static final int[] DURATION = {9000, 12000, 10000, 72000, 7200, 72000, 72000, 6000, 3600, 12000, 1800, 1600, 120000, 72000, 3600,
            900, 1200, 9000, 10000, 4800, 72000, 6000, 6000, 6000, 72000, 12000, 24000, 6000};
    /** 0: seen at night; 1: by day (at the sun); 2: either. */
    public static final int[] WHEN = {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 2, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    /** One night (or day) in how many, on average, when natural sky events are on (0 = never by itself). */
    public static final int[] CHANCE = {30, 14, 10, 45, 25, 30, 70, 60, 30, 12, 3, 7, 250, 90, 20, 40, 6, 45, 4, 15, 80, 60, 30, 0, 25, 5, 40, 0};

    public final int type;
    public final long start;
    public final int duration;
    public final long seed;

    public SkyEvent(int type, long start, int duration, long seed) {
        this.type = type;
        this.start = start;
        this.duration = duration;
        this.seed = seed;
    }

    public boolean active(double gameTime) {
        return gameTime >= start && gameTime <= start + duration;
    }

    /** 0..1 through the event. */
    public double progress(double gameTime) {
        return Math.max(0.0, Math.min(1.0, (gameTime - start) / duration));
    }

    public static int byName(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
