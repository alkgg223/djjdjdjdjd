package com.realweather.space;

import java.util.HashMap;
import java.util.Map;

import static com.realweather.space.Geology.*;
import static com.realweather.space.Tiles.*;

/**
 * Where each world's materials belong: the ground of its plains, lowlands and highlands, its steep slopes and canyon
 * walls, crater floors and the debris thrown round them, volcanic regions, dune fields, poles, scattered deposits, what
 * lies just under the surface, the layers below and the deep rock; and how much its land is cut by canyons, raised in
 * volcanoes or rippled into dunes. Gas giants get their cloud decks instead (which you fall through).
 */
public final class GeologyProfiles {
    /** One world's geology. Tiles are indices into {@link Tiles#ALL} (-1: none). */
    public static final class Profile {
        public int plains = -1, lowland = -1, highland = -1, peak = -1, slope = -1, craterFloor = -1, ejecta = -1, volcanic = -1, polar = -1, dune = -1,
                deposit = -1, deposit2 = -1, sub = -1, deep = -1, ore = -1, beach = -1, wet = -1, seabed = -1;
        public int[] strata = {};
        /** Cloud decks of a gas giant: pairs of tile and height. */
        public int[] clouds = {};
        public double canyons, volcanoes, dunes, craters = 1, ice;
        /** Two faces (Iapetus): the leading half dark, the trailing half bright. */
        public boolean twoFaced;

        Profile ground(int lowland, int plains, int highland) {
            this.lowland = lowland;
            this.plains = plains;
            this.highland = highland;
            return this;
        }

        Profile peak(int t) {
            peak = t;
            return this;
        }

        Profile slope(int t) {
            slope = t;
            return this;
        }

        Profile craters(int floor, int rim) {
            craterFloor = floor;
            ejecta = rim;
            return this;
        }

        Profile craterScale(double s) {
            craters = s;
            return this;
        }

        Profile volcanic(int t, double cones) {
            volcanic = t;
            volcanoes = cones;
            return this;
        }

        Profile polar(int t) {
            polar = t;
            return this;
        }

        Profile dunes(int t, double amount) {
            dune = t;
            dunes = amount;
            return this;
        }

        Profile deposits(int a, int b) {
            deposit = a;
            deposit2 = b;
            return this;
        }

        Profile under(int sub, int deep, int ore, int... strata) {
            this.sub = sub;
            this.deep = deep;
            this.ore = ore;
            this.strata = strata;
            return this;
        }

        Profile canyons(double c) {
            canyons = c;
            return this;
        }

        Profile icePlains(double c) {
            ice = c;
            return this;
        }

        Profile coast(int beach, int wet, int seabed) {
            this.beach = beach;
            this.wet = wet;
            this.seabed = seabed;
            return this;
        }

        Profile clouds(int... pairs) {
            clouds = pairs;
            return this;
        }

        Profile twoFaced() {
            twoFaced = true;
            return this;
        }
    }

    private static final Map<String, Profile> BY_WORLD = new HashMap<>();

    private GeologyProfiles() {}

    private static Profile p(String... ids) {
        Profile p = new Profile();
        for (String id : ids) {
            BY_WORLD.put(id, p);
        }
        return p;
    }

    public static Profile of(String world) {
        return BY_WORLD.get(world);
    }

    static {
        p("mars").ground(MARS_FINE_DUST, MARS_REGOLITH, MARS_ANCIENT_ROCK).peak(MARS_VOLCANIC_ROCK).slope(MARS_CANYON_ROCK)
                .craters(MARS_DUNES, MARS_CRATER_ROCK).volcanic(MARS_VOLCANIC_ROCK, 1.0).polar(MARS_POLAR_ICE).dunes(MARS_DUNES, 0.7)
                .deposits(MARS_HEMATITE, MARS_SULFATE)
                .under(MARS_IRON_OXIDE_SOIL, MARS_DARK_BASALT, MARS_HEMATITE, MARS_SEDIMENT_LAYERS, MARS_SANDSTONE, MARS_CANYON_ROCK, MARS_SULFATE,
                        MARS_SEDIMENT_LAYERS, MARS_DARK_BASALT)
                .canyons(1.0);
        p("moon").ground(MOON_MARIA_BASALT, MOON_REGOLITH, MOON_HIGHLANDS).peak(MOON_ANORTHOSITE).slope(MOON_ANORTHOSITE)
                .craters(MOON_IMPACT_MELT, MOON_CRATER_DEBRIS).polar(MOON_POLAR_ICE).deposits(MOON_IMPACT_GLASS, MOON_DUST)
                .under(MOON_DUST, MOON_MARIA_BASALT, MOON_IMPACT_GLASS, MOON_BRECCIA, MOON_REGOLITH, MOON_ANORTHOSITE, MOON_IMPACT_MELT);
        p("mercury").ground(MERCURY_VOLCANIC_PLAINS, MERCURY_REGOLITH, MERCURY_SILICATE_ROCK).slope(MERCURY_DARK_ROCK)
                .craters(MERCURY_ANCIENT_IMPACT_ROCK, MERCURY_CRATER_MATERIAL).polar(MERCURY_POLAR_ICE).deposits(MERCURY_BRIGHT_DEPOSITS, -1)
                .under(MERCURY_REGOLITH, MERCURY_DARK_ROCK, MERCURY_BRIGHT_DEPOSITS, MERCURY_SILICATE_ROCK, MERCURY_DARK_ROCK, MERCURY_ANCIENT_IMPACT_ROCK)
                .craterScale(1.2);
        p("venus").ground(VENUS_LAVA_PLAINS, VENUS_BASALT, VENUS_TESSERA).peak(VENUS_HIGHLAND_ROCK).slope(VENUS_FRACTURED_ROCK)
                .craters(VENUS_IMPACT_MATERIAL, VENUS_IMPACT_MATERIAL).volcanic(VENUS_VOLCANIC_ROCK, 1.2).deposits(VENUS_VOLCANIC_DEPOSITS, -1)
                .under(VENUS_VOLCANIC_DEPOSITS, VENUS_BASALT, VENUS_VOLCANIC_ROCK, VENUS_BASALT, VENUS_VOLCANIC_ROCK, VENUS_FRACTURED_ROCK)
                .canyons(0.4);
        p("io").ground(IO_ORANGE_SULFUR, IO_SULFUR, IO_SILICATE_ROCK).peak(IO_SILICATE_ROCK).slope(IO_SILICATE_ROCK)
                .volcanic(IO_BLACK_LAVA, 1.6).polar(IO_SO2_FROST).deposits(IO_YELLOW_SULFUR, IO_SO2_FROST)
                .under(IO_SULFUR, IO_SILICATE_ROCK, IO_YELLOW_SULFUR, IO_SILICATE_ROCK, IO_ORANGE_SULFUR, IO_SULFUR, IO_BLACK_LAVA);
        p("europa").ground(EUROPA_CHAOS, EUROPA_WATER_ICE, EUROPA_ICE_RIDGES).slope(EUROPA_FRACTURED_ICE).craters(EUROPA_WATER_ICE, EUROPA_FRACTURED_ICE)
                .deposits(EUROPA_RED_DEPOSITS, -1).under(EUROPA_FRACTURED_ICE, EUROPA_OCEAN_ICE, EUROPA_RED_DEPOSITS, EUROPA_WATER_ICE,
                        EUROPA_FRACTURED_ICE)
                .craterScale(0.3).icePlains(0.7);
        p("ganymede").ground(GANYMEDE_DARK_TERRAIN, GANYMEDE_GROOVED_ICE, GANYMEDE_BRIGHT_ICE).slope(GANYMEDE_ANCIENT_ROCK)
                .craters(GANYMEDE_CRATER_ICE, GANYMEDE_CRATER_ICE).polar(GANYMEDE_BRIGHT_ICE)
                .under(GANYMEDE_DARK_TERRAIN, GANYMEDE_ANCIENT_ROCK, -1, GANYMEDE_ANCIENT_ROCK, GANYMEDE_BRIGHT_ICE);
        p("callisto").ground(CALLISTO_DARK_ICE, CALLISTO_ICE_ROCK, CALLISTO_CRATER_MATERIAL).slope(CALLISTO_ICE_ROCK)
                .craters(CALLISTO_CRATER_MATERIAL, CALLISTO_IMPACT_DEBRIS).under(CALLISTO_DARK_ICE, CALLISTO_ICE_ROCK, -1, CALLISTO_ICE_ROCK,
                        CALLISTO_CRATER_MATERIAL)
                .craterScale(1.4);
        p("titan").ground(TITAN_LAKE_BED, TITAN_ORGANIC_SAND, TITAN_ICE_BEDROCK).slope(TITAN_ICE_BEDROCK).polar(TITAN_LAKE_BED)
                .dunes(TITAN_DUNE_MATERIAL, 1.0).deposits(TITAN_ORGANIC_DEPOSITS, TITAN_HYDROCARBON_ICE)
                .under(TITAN_ORGANIC_DEPOSITS, TITAN_ICE_BEDROCK, TITAN_HYDROCARBON_ICE, TITAN_HYDROCARBON_ICE, TITAN_ICE_BEDROCK)
                .canyons(0.3);
        p("enceladus").ground(ENCELADUS_FRESH_ICE, ENCELADUS_BRIGHT_ICE, ENCELADUS_FRACTURED_ICE).slope(ENCELADUS_FRACTURED_ICE)
                .craters(ENCELADUS_BRIGHT_ICE, ENCELADUS_FRACTURED_ICE).volcanic(ENCELADUS_CRYOVOLCANIC, 0.4).polar(ENCELADUS_CRYOVOLCANIC)
                .under(ENCELADUS_FRESH_ICE, ENCELADUS_FRACTURED_ICE, -1, ENCELADUS_BRIGHT_ICE, ENCELADUS_FRACTURED_ICE).icePlains(0.5);
        p("rhea").ground(RHEA_ICE, RHEA_ICE, DIONE_ICE).slope(DIONE_ICE).craters(MIMAS_CRATER_MATERIAL, RHEA_ICE)
                .under(RHEA_ICE, CALLISTO_ICE_ROCK, -1, RHEA_ICE, DIONE_ICE).craterScale(1.3);
        p("dione").ground(DIONE_ICE, RHEA_ICE, DIONE_ICE).slope(DIONE_ICE).craters(RHEA_ICE, DIONE_ICE)
                .under(DIONE_ICE, CALLISTO_ICE_ROCK, -1, DIONE_ICE, RHEA_ICE);
        p("iapetus").ground(IAPETUS_DARK_MATERIAL, IAPETUS_BRIGHT_ICE, IAPETUS_BRIGHT_ICE).slope(IAPETUS_BRIGHT_ICE)
                .craters(IAPETUS_DARK_MATERIAL, IAPETUS_BRIGHT_ICE).under(IAPETUS_DARK_MATERIAL, IAPETUS_BRIGHT_ICE, -1, IAPETUS_BRIGHT_ICE).twoFaced();
        p("mimas").ground(MIMAS_CRATER_MATERIAL, MIMAS_CRATER_MATERIAL, RHEA_ICE).craters(MIMAS_CRATER_MATERIAL, RHEA_ICE)
                .under(MIMAS_CRATER_MATERIAL, RHEA_ICE, -1, RHEA_ICE).craterScale(2.0);
        p("hyperion").ground(HYPERION_POROUS_ICE, HYPERION_POROUS_ICE, HYPERION_POROUS_ICE).craters(HYPERION_POROUS_ICE, HYPERION_POROUS_ICE)
                .under(HYPERION_POROUS_ICE, HYPERION_POROUS_ICE, -1).craterScale(2.4);
        p("triton").ground(TRITON_WATER_ICE, TRITON_NITROGEN_ICE, TRITON_METHANE_FROST).slope(TRITON_WATER_ICE).polar(TRITON_NITROGEN_ICE)
                .volcanic(TRITON_CRYOVOLCANIC, 0.3).deposits(NEPTUNE_MOON_DEPOSITS, TRITON_CRYOVOLCANIC)
                .under(TRITON_METHANE_FROST, TRITON_WATER_ICE, -1, TRITON_NITROGEN_ICE, TRITON_WATER_ICE).craterScale(0.4);
        p("miranda").ground(MIRANDA_ANCIENT_ROCK, MIRANDA_FRACTURED_ICE, MIRANDA_ANCIENT_ROCK).slope(MIRANDA_FRACTURED_ICE)
                .craters(MIRANDA_ANCIENT_ROCK, MIRANDA_FRACTURED_ICE).under(MIRANDA_FRACTURED_ICE, MIRANDA_ANCIENT_ROCK, -1, MIRANDA_ANCIENT_ROCK)
                .canyons(1.2);
        p("ariel").ground(ARIEL_ICE, ARIEL_ICE, OBERON_ICE_ROCK).slope(ARIEL_ICE).craters(ARIEL_ICE, ARIEL_ICE)
                .under(ARIEL_ICE, OBERON_ICE_ROCK, -1, ARIEL_ICE).canyons(0.6);
        p("umbriel", "dysnomia").ground(UMBRIEL_DARK_ICE, UMBRIEL_DARK_ICE, OBERON_ICE_ROCK).craters(UMBRIEL_DARK_ICE, ARIEL_ICE)
                .under(UMBRIEL_DARK_ICE, OBERON_ICE_ROCK, -1, UMBRIEL_DARK_ICE).craterScale(1.3);
        p("oberon").ground(OBERON_ICE_ROCK, OBERON_ICE_ROCK, ARIEL_ICE).slope(OBERON_ICE_ROCK).craters(UMBRIEL_DARK_ICE, ARIEL_ICE)
                .under(OBERON_ICE_ROCK, OBERON_ICE_ROCK, -1, OBERON_ICE_ROCK, UMBRIEL_DARK_ICE);
        p("pluto").ground(PLUTO_DARK_ORGANICS, PLUTO_NITROGEN_ICE, PLUTO_METHANE_ICE).peak(PLUTO_ICE_BEDROCK).slope(PLUTO_ICE_BEDROCK)
                .craters(PLUTO_CRATER_ROCK, PLUTO_CRATER_ROCK).polar(PLUTO_METHANE_ICE).deposits(PLUTO_DARK_ORGANICS, -1)
                .under(PLUTO_METHANE_ICE, PLUTO_ICE_BEDROCK, -1, PLUTO_ICE_BEDROCK, PLUTO_METHANE_ICE).icePlains(0.6).craterScale(0.6);
        p("charon").ground(CHARON_WATER_ICE, CHARON_WATER_ICE, PLUTO_ICE_BEDROCK).slope(PLUTO_ICE_BEDROCK).craters(CHARON_WATER_ICE, CHARON_WATER_ICE)
                .polar(PLUTO_DARK_ORGANICS).under(CHARON_WATER_ICE, PLUTO_ICE_BEDROCK, -1, CHARON_WATER_ICE).canyons(0.8);
        p("ceres").ground(CERES_DARK_REGOLITH, CERES_DARK_REGOLITH, CERES_CARBONATE).slope(CERES_CARBONATE).craters(CERES_SALT_DEPOSITS, CERES_DARK_REGOLITH)
                .deposits(CERES_SALT_DEPOSITS, CERES_CARBONATE).under(CERES_DARK_REGOLITH, CERES_CARBONATE, CERES_SALT_DEPOSITS, CERES_CARBONATE);
        p("haumea", "hiiaka", "namaka").ground(HAUMEA_WATER_ICE, HAUMEA_WATER_ICE, HAUMEA_WATER_ICE).craters(HAUMEA_WATER_ICE, HAUMEA_WATER_ICE)
                .under(HAUMEA_WATER_ICE, HAUMEA_WATER_ICE, -1);
        p("makemake").ground(PLUTO_DARK_ORGANICS, MAKEMAKE_METHANE_ICE, MAKEMAKE_METHANE_ICE).craters(MAKEMAKE_METHANE_ICE, MAKEMAKE_METHANE_ICE)
                .under(MAKEMAKE_METHANE_ICE, PLUTO_ICE_BEDROCK, -1, MAKEMAKE_METHANE_ICE);
        p("eris").ground(ERIS_METHANE_ICE, ERIS_METHANE_ICE, ERIS_METHANE_ICE).craters(ERIS_METHANE_ICE, ERIS_METHANE_ICE)
                .under(ERIS_METHANE_ICE, PLUTO_ICE_BEDROCK, -1, ERIS_METHANE_ICE).icePlains(0.5);
        // Small bodies.
        p("psyche").ground(ASTEROID_REGOLITH, ASTEROID_METALLIC_IRON, ASTEROID_NICKEL_IRON).slope(ASTEROID_NICKEL_IRON)
                .craters(ASTEROID_IMPACT_MATERIAL, ASTEROID_REGOLITH).deposits(ASTEROID_SILICATE_ROCK, -1)
                .under(ASTEROID_REGOLITH, ASTEROID_NICKEL_IRON, -1, ASTEROID_METALLIC_IRON, ASTEROID_NICKEL_IRON).craterScale(1.6);
        p("bennu").ground(ASTEROID_REGOLITH, ASTEROID_CARBONACEOUS_ROCK, ASTEROID_CARBONACEOUS_ROCK).slope(ASTEROID_SILICATE_ROCK)
                .craters(ASTEROID_IMPACT_MATERIAL, ASTEROID_CARBONACEOUS_ROCK).deposits(ASTEROID_SILICATE_ROCK, ASTEROID_REGOLITH)
                .under(ASTEROID_REGOLITH, ASTEROID_CARBONACEOUS_ROCK, -1, ASTEROID_CARBONACEOUS_ROCK).craterScale(1.8);
        p("comet_67p").ground(COMET_DUST, COMET_POROUS_ICE, COMET_ROCKY_CORE).slope(COMET_ROCKY_CORE).craters(COMET_ORGANIC_MATERIAL, COMET_DUST)
                .deposits(COMET_CO2_ICE, COMET_CO_ICE).under(COMET_ORGANIC_MATERIAL, COMET_ROCKY_CORE, COMET_CO_ICE, COMET_POROUS_ICE, COMET_ROCKY_CORE)
                .canyons(0.8).craterScale(0.8);
        // Gas and ice giants: cloud decks, top to bottom.
        p("jupiter").clouds(JUPITER_AMMONIA_CLOUD, 150, JUPITER_NH4SH_CLOUD, 100, JUPITER_WATER_CLOUD, 45, DEEP_ATMOSPHERE_CLOUD, -20);
        p("saturn").clouds(SATURN_HAZE, 175, SATURN_AMMONIA_CLOUD, 130, JUPITER_WATER_CLOUD, 40, DEEP_ATMOSPHERE_CLOUD, -20);
        p("uranus").clouds(SATURN_HAZE, 170, URANUS_METHANE_CLOUD, 130, DEEP_ATMOSPHERE_CLOUD, 0);
        p("neptune").clouds(URANUS_METHANE_CLOUD, 170, NEPTUNE_METHANE_CLOUD, 130, DEEP_ATMOSPHERE_CLOUD, 0);
        p("peg51b", "wasp12b", "wasp76b", "kelt9b", "hr8799b", "hr8799c", "hr8799d", "hr8799e")
                .clouds(DEEP_ATMOSPHERE_CLOUD, 140, DEEP_ATMOSPHERE_CLOUD, 60, EXO_SUPERCRITICAL_FLUID, -30);
        p("hd189733b", "hd209458b", "wasp17b", "kepler16b", "betapic_b", "psr_b1620_26b")
                .clouds(SATURN_HAZE, 160, JUPITER_AMMONIA_CLOUD, 110, DEEP_ATMOSPHERE_CLOUD, 20);
        p("gj1214b", "gj436b", "k2_18b").clouds(URANUS_METHANE_CLOUD, 150, SATURN_HAZE, 100, EXO_SUPERCRITICAL_FLUID, -30);
        // Worlds of other stars.
        p("kepler22b", "kepler452b", "kepler442b", "kepler62f", "trappist1e", "toi700e", "toi700d")
                .ground(EARTH_FERTILE_SOIL, EARTH_GRASSLAND_SOIL, EARTH_MOUNTAIN_ROCK).peak(EARTH_SNOW).slope(EARTH_GRANITE)
                .volcanic(EARTH_VOLCANIC_ROCK, 0.3).polar(EARTH_COMPRESSED_ICE).dunes(EARTH_DESERT_SAND, 0.4).deposits(EARTH_CLAY, EARTH_DRY_EARTH)
                .coast(EARTH_BEACH_SAND, EARTH_WET_SAND, EARTH_OCEAN_FLOOR)
                .under(EARTH_ROCKY_SOIL, EARTH_BASALT, EARTH_MARBLE, EARTH_LIMESTONE, EARTH_SANDSTONE, EARTH_SLATE, EARTH_LIMESTONE, EARTH_GRANITE)
                .canyons(0.5);
        p("proxima_b", "kepler1649c", "trappist1d").ground(EARTH_DRY_EARTH, RED_GRASS, EARTH_MOUNTAIN_ROCK).peak(EARTH_SNOW).slope(EARTH_SANDSTONE)
                .polar(EARTH_COMPRESSED_ICE).dunes(EARTH_RED_DESERT_SAND, 0.8).deposits(EARTH_CLAY, EXO_MINERAL_DEPOSITS)
                .coast(EARTH_RED_DESERT_SAND, EARTH_WET_SAND, EARTH_OCEAN_FLOOR)
                .under(EARTH_RED_DESERT_SAND, EARTH_BASALT, EXO_MINERAL_DEPOSITS, EARTH_SANDSTONE, EARTH_LIMESTONE, EARTH_SANDSTONE).canyons(0.8);
        p("kepler186f").ground(EARTH_FERTILE_SOIL, PURPLE_GRASS, EARTH_SLATE).slope(EARTH_SLATE).polar(EARTH_SNOW).dunes(EARTH_DESERT_SAND, 0.3)
                .deposits(EXO_CRYSTALLINE_ROCK, EARTH_CLAY).coast(EARTH_BEACH_SAND, EARTH_WET_SAND, EARTH_OCEAN_FLOOR)
                .under(EARTH_ROCKY_SOIL, EARTH_BASALT, EXO_CRYSTALLINE_ROCK, EARTH_SLATE, EARTH_MARBLE, EARTH_LIMESTONE);
        p("cnc55e").ground(MAGMA, EXO_LAVA_CRUST, EXO_GRAPHITE).peak(EXO_DIAMOND_CARBON).slope(EXO_VOLCANIC_GLASS).volcanic(EXO_LAVA_CRUST, 1.2)
                .deposits(EXO_DIAMOND_CARBON, EXO_GRAPHITE).under(EXO_GRAPHITE, EXO_DIAMOND_CARBON, EXO_DIAMOND_CARBON, EXO_GRAPHITE, EXO_SUPER_EARTH_ROCK);
        p("kepler10b", "kepler78b").ground(MAGMA, EXO_LAVA_CRUST, EXO_VOLCANIC_GLASS).slope(EXO_VOLCANIC_GLASS).volcanic(EXO_LAVA_CRUST, 1.0)
                .deposits(EXO_MINERAL_DEPOSITS, -1).under(EXO_VOLCANIC_GLASS, EXO_IRON_CORE, EXO_MINERAL_DEPOSITS, EXO_SUPER_EARTH_ROCK, EXO_IRON_CORE);
        p("trappist1b").ground(EXO_LAVA_CRUST, VENUS_BASALT, EXO_VOLCANIC_GLASS).slope(EXO_VOLCANIC_GLASS).volcanic(EXO_LAVA_CRUST, 0.8)
                .under(VENUS_VOLCANIC_DEPOSITS, EXO_SUPER_EARTH_ROCK, -1, VENUS_BASALT, EXO_SUPER_EARTH_ROCK);
        p("trappist1c").ground(EXO_SUPER_EARTH_ROCK, VENUS_BASALT, EXO_SUPER_EARTH_ROCK).slope(VENUS_FRACTURED_ROCK)
                .craters(VENUS_IMPACT_MATERIAL, MERCURY_CRATER_MATERIAL).deposits(EXO_MINERAL_DEPOSITS, -1)
                .under(VENUS_VOLCANIC_DEPOSITS, EXO_SUPER_EARTH_ROCK, EXO_MINERAL_DEPOSITS, VENUS_BASALT, EXO_SUPER_EARTH_ROCK);
        p("trappist1f", "trappist1g", "trappist1h", "lhs1140b").ground(EUROPA_OCEAN_ICE, EARTH_COMPRESSED_ICE, EXO_HIGH_PRESSURE_ICE)
                .slope(EXO_HIGH_PRESSURE_ICE).polar(EARTH_SNOW).deposits(EXO_CRYSTALLINE_ROCK, -1)
                .coast(EARTH_COMPRESSED_ICE, EUROPA_OCEAN_ICE, EXO_HIGH_PRESSURE_ICE)
                .under(EARTH_COMPRESSED_ICE, EXO_HIGH_PRESSURE_ICE, EXO_CRYSTALLINE_ROCK, EARTH_COMPRESSED_ICE, EXO_HIGH_PRESSURE_ICE).icePlains(0.6);
    }
}
