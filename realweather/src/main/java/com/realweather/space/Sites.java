package com.realweather.space;

import java.util.List;

/**
 * Places out in deep space (in the planets dimension, west of the worlds): one beside each star, pulsar, black hole,
 * nebula and comet you can travel to, and one for flying between the planets of the solar system. There is no ground
 * there, only a small invisible platform to stand on; what you see round you is drawn by the client.
 */
public final class Sites {
    /** Flying between the worlds of the solar system. */
    public static final String FLIGHT = "flight";
    /** Every place out in space, by its catalogue id (new ones only ever go on the end). */
    public static final List<String> IDS = List.of(
            "sun", "alpha_centauri_a", "alpha_centauri_b", "rigel_kentaurus", "proxima_centauri", "sirius", "betelgeuse", "rigel", "vega",
            "polaris", "altair", "deneb", "antares", "aldebaran", "arcturus", "spica", "canopus", "capella", "regulus", "fomalhaut",
            "barnards_star", "wolf_359", "tau_ceti", "epsilon_eridani", "uy_scuti", "vy_canis_majoris", "stephenson_2_18", "eta_carinae",
            "ton618", "crab_pulsar", "vela_pulsar", "psr_b1919_21", "psr_j1748_2446ad", "psr_j0437_4715", "sgr_1806_20", "gw150914",
            "orion_nebula", "horsehead_nebula", "flame_nebula", "running_man_nebula", "crab_nebula", "eagle_nebula", "lagoon_nebula",
            "trifid_nebula", "carina_nebula", "ring_nebula", "helix_nebula", "dumbbell_nebula", "cats_eye_nebula", "eskimo_nebula",
            "rosette_nebula", "north_america_nebula", "veil_nebula", "tarantula_nebula", "omega_nebula", "cone_nebula", "bubble_nebula",
            "california_nebula", "heart_nebula", "soul_nebula", "witch_head_nebula", "sn1987a", "halley", "hale_bopp", "neowise");
    /** Height of the platform's top. */
    public static final int FLOOR = 100;

    private Sites() {}

    /** Which stretch of the dimension a block column is in (negative ones are out in space). */
    public static int region(double x) {
        return (int) Math.floorDiv((long) Math.floor(x) + Worlds.REGION / 2, Worlds.REGION);
    }

    public static boolean isSpace(double x) {
        return region(x) < 0;
    }

    /** The place out in space at a block column: FLIGHT, a site's id, or null on a world. */
    public static String at(double x) {
        int r = region(x);
        if (r >= 0) {
            return null;
        }
        int i = -r - 2;
        return i >= 0 && i < IDS.size() ? IDS.get(i) : FLIGHT;
    }

    /** X of the middle of a place's stretch. */
    public static double centre(String id) {
        int i = IDS.indexOf(id);
        return (i < 0 ? -1 : -(i + 2)) * (double) Worlds.REGION;
    }
}
