package com.realweather.space;

/**
 * The blocks other worlds are made of, in the order of the block atlas the ray-traced planets are built from (8 x 8
 * tiles; tile 7 is the sea, drawn by the ray tracing only). The same list registers the real blocks, makes the
 * planets' ground in their dimension, and names the tiles each world uses.
 */
public final class Tiles {
    public static final int REGOLITH = 0, BASALT = 1, SAND = 2, ICE = 3, FROST = 4, MAGMA = 5, CLOUD = 6, WATER = 7, MOSS = 8, CRYSTAL = 9,
            SULFUR = 10, IRONSTONE = 11, STORM = 12, OBSIDIAN = 13, HOTROCK = 14, ASH = 15, MARS_DUST = 16, MARS_ROCK = 17, HIGHLAND = 18,
            MARE = 19, MERCURY_ROCK = 20, VENUS_ROCK = 21, TITAN_ICE = 22, METHANE_SNOW = 23, NITROGEN_ICE = 24, THOLIN = 25, EUROPA_ICE = 26,
            JUPITER_CLOUD = 27, SATURN_CLOUD = 28, URANUS_CLOUD = 29, NEPTUNE_CLOUD = 30, LAVA_CRUST = 31, DIAMOND_ROCK = 32, PURPLE_GRASS = 33,
            RED_GRASS = 34, SEABED = 35, SALT = 36, PERMAFROST = 37, IRON = 38, SANDSTONE = 39, GRANITE = 40, HEMATITE = 41, SLUSH = 42,
            RED_SPOT = 43, DIRT = 44, DEEP_ROCK = 45, IMPACT_GLASS = 46, DRY_ICE = 47;

    /** Material of a block: how it sounds and breaks. */
    public static final int M_DUST = 0, M_ROCK = 1, M_ICE = 2, M_SNOW = 3, M_CLOUD = 4, M_PLANT = 5, M_CRYSTAL = 6, M_METAL = 7, M_GLASS = 8,
            M_MUD = 9;

    /** One tile: its block's name, material, hardness and light, or null for the sea. */
    public record Tile(String name, int material, float hardness, int light) {}

    public static final Tile[] ALL = {
            new Tile("planet_regolith", M_DUST, 0.8F, 0),
            new Tile("planet_basalt", M_ROCK, 2.0F, 0),
            new Tile("planet_sand", M_DUST, 0.6F, 0),
            new Tile("planet_ice", M_ICE, 0.6F, 0),
            new Tile("planet_frost", M_SNOW, 0.3F, 0),
            new Tile("planet_magma", M_ROCK, 1.5F, 13),
            new Tile("planet_cloud", M_CLOUD, 0.3F, 0),
            null,
            new Tile("planet_moss", M_PLANT, 0.6F, 0),
            new Tile("planet_crystal", M_CRYSTAL, 1.5F, 7),
            new Tile("planet_sulfur", M_ROCK, 1.0F, 0),
            new Tile("planet_ironstone", M_ROCK, 1.8F, 0),
            new Tile("planet_storm_cloud", M_CLOUD, 0.3F, 0),
            new Tile("planet_obsidian", M_ROCK, 6.0F, 0),
            new Tile("planet_hot_rock", M_ROCK, 1.8F, 9),
            new Tile("planet_ash", M_DUST, 0.5F, 0),
            new Tile("mars_dust", M_DUST, 0.5F, 0),
            new Tile("mars_rock", M_ROCK, 1.8F, 0),
            new Tile("lunar_highland", M_ROCK, 1.2F, 0),
            new Tile("lunar_mare", M_ROCK, 1.5F, 0),
            new Tile("mercury_rock", M_ROCK, 1.6F, 0),
            new Tile("venus_rock", M_ROCK, 1.8F, 0),
            new Tile("titan_ice", M_ICE, 0.8F, 0),
            new Tile("methane_snow", M_SNOW, 0.3F, 0),
            new Tile("nitrogen_ice", M_ICE, 0.6F, 0),
            new Tile("tholin_crust", M_DUST, 0.9F, 0),
            new Tile("europa_ice", M_ICE, 0.7F, 0),
            new Tile("jupiter_cloud", M_CLOUD, 0.3F, 0),
            new Tile("saturn_cloud", M_CLOUD, 0.3F, 0),
            new Tile("uranus_cloud", M_CLOUD, 0.3F, 0),
            new Tile("neptune_cloud", M_CLOUD, 0.3F, 0),
            new Tile("lava_crust", M_ROCK, 1.6F, 10),
            new Tile("diamond_rock", M_CRYSTAL, 3.0F, 3),
            new Tile("purple_alien_grass", M_PLANT, 0.6F, 0),
            new Tile("red_alien_grass", M_PLANT, 0.6F, 0),
            new Tile("seabed_sand", M_DUST, 0.6F, 0),
            new Tile("salt_flat", M_CRYSTAL, 0.6F, 0),
            new Tile("permafrost", M_MUD, 0.9F, 0),
            new Tile("iron_meteorite", M_METAL, 5.0F, 0),
            new Tile("alien_sandstone", M_ROCK, 0.8F, 0),
            new Tile("alien_granite", M_ROCK, 1.5F, 0),
            new Tile("hematite_rock", M_ROCK, 1.5F, 0),
            new Tile("cryo_slush", M_MUD, 0.5F, 0),
            new Tile("red_spot_cloud", M_CLOUD, 0.3F, 0),
            new Tile("alien_dirt", M_MUD, 0.5F, 0),
            new Tile("deep_planet_rock", M_ROCK, 3.0F, 0),
            new Tile("impact_glass", M_GLASS, 0.6F, 0),
            new Tile("dry_ice", M_ICE, 0.6F, 0),
    };

    private Tiles() {}
}
