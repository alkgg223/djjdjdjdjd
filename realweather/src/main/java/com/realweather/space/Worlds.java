package com.realweather.space;

import com.realweather.RealWeather;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

import static com.realweather.space.Tiles.*;

/**
 * The worlds you can travel to. They all live in one dimension, each in its own stretch of it along X (the planet's
 * map wrapped round it, its poles towards north and south), so the server can make any of them without a dimension
 * each. What their ground is made of, their seas, glow and air are given here once, for the dimension's terrain and
 * for the ray-traced views.
 */
public final class Worlds {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, RealWeather.id("planets"));
    /** Width of each world's stretch of the dimension (blocks), and the circumference its map is wrapped round. */
    public static final int REGION = 65536;
    public static final double RADIUS = 4096, CIRCUMFERENCE = Math.PI * 2 * RADIUS;

    /**
     * One world: its id (as in the catalogue), map, whether it is a gas giant or has seas, its blocks (low, middle and
     * high ground, just under the top, deep down, and its snow), how much it glows, its air (strength and colour) and
     * how rugged it is.
     */
    public record World(String id, String map, boolean gas, boolean ocean, int low, int mid, int high, int sub, int deep, int snow, float glow,
                        float air, float ar, float ag, float ab, float relief, String parent) {
        public boolean airless() {
            return air <= 0.01F;
        }
    }

    public static final List<World> ALL = new ArrayList<>();

    private Worlds() {}

    private static void rock(String id, String map, int low, int mid, int high, int sub, int snow, float air, float ar, float ag, float ab, float relief,
                             String parent) {
        ALL.add(new World(id, map, false, false, low, mid, high, sub, DEEP_ROCK, snow, 0F, air, ar, ag, ab, relief, parent));
    }

    private static void sea(String id, int low, int mid, int high, int sub, float ar, float ag, float ab) {
        ALL.add(new World(id, id, false, true, low, mid, high, sub, DEEP_ROCK, FROST, 0F, 1F, ar, ag, ab, 1F, null));
    }

    private static void lava(String id, int low, int mid, int high, float glow) {
        ALL.add(new World(id, id, false, false, low, mid, high, BASALT, OBSIDIAN, ASH, glow, 0.15F, 1F, 0.5F, 0.3F, 1.1F, null));
    }

    private static void gas(String id, String map, int low, int mid, int high, float glow, float ar, float ag, float ab) {
        ALL.add(new World(id, map, true, false, low, mid, high, STORM, CLOUD, high, glow, 1F, ar, ag, ab, 0.25F, null));
    }

    static {
        // The solar system.
        rock("mercury", "mercury", MERCURY_ROCK, REGOLITH, HIGHLAND, BASALT, FROST, 0F, 0, 0, 0, 1.1F, null);
        rock("venus", "venus", VENUS_ROCK, BASALT, SULFUR, BASALT, SULFUR, 1F, 1F, 0.85F, 0.55F, 0.9F, null);
        rock("moon", "moon", MARE, REGOLITH, HIGHLAND, BASALT, FROST, 0F, 0, 0, 0, 1.0F, "earth");
        rock("mars", "mars", MARS_DUST, MARS_ROCK, IRONSTONE, MARS_ROCK, DRY_ICE, 0.45F, 1F, 0.72F, 0.5F, 1.3F, null);
        rock("ceres", "ceres", REGOLITH, BASALT, SALT, BASALT, ICE, 0F, 0, 0, 0, 0.8F, null);
        gas("jupiter", "jupiter", JUPITER_CLOUD, STORM, RED_SPOT, 0F, 0.9F, 0.8F, 0.65F);
        rock("io", "venus", SULFUR, LAVA_CRUST, ASH, BASALT, SULFUR, 0F, 0, 0, 0, 1.0F, "jupiter");
        rock("europa", "lhs1140b", EUROPA_ICE, ICE, FROST, ICE, FROST, 0F, 0, 0, 0, 0.6F, "jupiter");
        rock("ganymede", "mercury", REGOLITH, EUROPA_ICE, ICE, ICE, FROST, 0F, 0, 0, 0, 0.9F, "jupiter");
        rock("callisto", "moon", REGOLITH, BASALT, FROST, ICE, FROST, 0F, 0, 0, 0, 0.9F, "jupiter");
        gas("saturn", "saturn", SATURN_CLOUD, CLOUD, STORM, 0F, 0.95F, 0.88F, 0.65F);
        rock("titan", "venus", THOLIN, TITAN_ICE, METHANE_SNOW, TITAN_ICE, METHANE_SNOW, 1F, 1F, 0.65F, 0.3F, 0.7F, "saturn");
        gas("uranus", "uranus", URANUS_CLOUD, URANUS_CLOUD, CLOUD, 0F, 0.6F, 0.9F, 0.95F);
        gas("neptune", "neptune", NEPTUNE_CLOUD, NEPTUNE_CLOUD, CLOUD, 0F, 0.4F, 0.55F, 1F);
        rock("pluto", "pluto", THOLIN, NITROGEN_ICE, METHANE_SNOW, NITROGEN_ICE, METHANE_SNOW, 0.15F, 0.6F, 0.75F, 1F, 1.0F, null);
        rock("charon", "charon", REGOLITH, ICE, THOLIN, ICE, FROST, 0F, 0, 0, 0, 0.9F, "pluto");
        rock("haumea", "haumea", ICE, CRYSTAL, FROST, ICE, FROST, 0F, 0, 0, 0, 0.7F, null);
        rock("hiiaka", "eris", ICE, FROST, FROST, ICE, FROST, 0F, 0, 0, 0, 0.6F, "haumea");
        rock("namaka", "eris", ICE, FROST, NITROGEN_ICE, ICE, FROST, 0F, 0, 0, 0, 0.6F, "haumea");
        rock("makemake", "makemake", THOLIN, METHANE_SNOW, NITROGEN_ICE, NITROGEN_ICE, METHANE_SNOW, 0F, 0, 0, 0, 0.8F, null);
        rock("eris", "eris", NITROGEN_ICE, METHANE_SNOW, FROST, NITROGEN_ICE, METHANE_SNOW, 0F, 0, 0, 0, 0.7F, null);
        rock("dysnomia", "charon", REGOLITH, BASALT, ICE, BASALT, FROST, 0F, 0, 0, 0, 0.9F, "eris");
        // Other stars' worlds.
        sea("proxima_b", SAND, RED_GRASS, ICE, DIRT, 0.6F, 0.75F, 1F);
        gas("peg51b", "peg51b", RED_SPOT, STORM, CLOUD, 0.12F, 1F, 0.8F, 0.6F);
        lava("cnc55e", LAVA_CRUST, OBSIDIAN, DIAMOND_ROCK, 0.45F);
        gas("hd189733b", "hd189733b", NEPTUNE_CLOUD, URANUS_CLOUD, CLOUD, 0F, 0.4F, 0.6F, 1F);
        gas("hd209458b", "hd209458b", URANUS_CLOUD, CLOUD, NEPTUNE_CLOUD, 0F, 0.6F, 0.75F, 1F);
        gas("wasp12b", "wasp12b", RED_SPOT, STORM, JUPITER_CLOUD, 0.5F, 1F, 0.5F, 0.3F);
        gas("wasp76b", "wasp76b", RED_SPOT, STORM, SATURN_CLOUD, 0.35F, 1F, 0.6F, 0.4F);
        gas("wasp17b", "wasp17b", CLOUD, SATURN_CLOUD, URANUS_CLOUD, 0F, 0.85F, 0.85F, 0.9F);
        gas("kelt9b", "kelt9b", RED_SPOT, JUPITER_CLOUD, STORM, 0.6F, 1F, 0.85F, 0.7F);
        gas("kepler16b", "kepler16b", SATURN_CLOUD, STORM, CLOUD, 0F, 0.85F, 0.8F, 0.7F);
        sea("kepler22b", SAND, MOSS, GRANITE, DIRT, 0.5F, 0.75F, 1F);
        sea("kepler186f", SAND, PURPLE_GRASS, GRANITE, DIRT, 0.7F, 0.7F, 0.9F);
        sea("kepler452b", SANDSTONE, MOSS, GRANITE, DIRT, 0.55F, 0.7F, 1F);
        lava("kepler10b", MAGMA, LAVA_CRUST, OBSIDIAN, 0.4F);
        lava("kepler78b", LAVA_CRUST, HOTROCK, OBSIDIAN, 0.45F);
        sea("kepler1649c", SAND, RED_GRASS, PERMAFROST, DIRT, 0.7F, 0.75F, 0.95F);
        sea("kepler442b", SAND, MOSS, GRANITE, DIRT, 0.55F, 0.72F, 1F);
        sea("kepler62f", SAND, MOSS, ICE, DIRT, 0.6F, 0.8F, 1F);
        ALL.add(new World("trappist1b", "trappist1b", false, false, HOTROCK, BASALT, OBSIDIAN, BASALT, OBSIDIAN, ASH, 0.25F, 0F, 0, 0, 0, 1F, null));
        rock("trappist1c", "trappist1c", BASALT, REGOLITH, ASH, BASALT, FROST, 0.4F, 0.6F, 0.75F, 1F, 1F, null);
        sea("trappist1d", SAND, RED_GRASS, ICE, DIRT, 0.6F, 0.75F, 1F);
        sea("trappist1e", SAND, MOSS, PERMAFROST, DIRT, 0.6F, 0.75F, 1F);
        rock("trappist1f", "trappist1f", SLUSH, ICE, FROST, ICE, FROST, 0.6F, 0.6F, 0.75F, 1F, 0.8F, null);
        rock("trappist1g", "trappist1g", ICE, FROST, NITROGEN_ICE, ICE, FROST, 0.5F, 0.6F, 0.75F, 1F, 0.8F, null);
        rock("trappist1h", "trappist1h", FROST, METHANE_SNOW, NITROGEN_ICE, ICE, METHANE_SNOW, 0.3F, 0.6F, 0.75F, 1F, 0.7F, null);
        gas("gj1214b", "gj1214b", URANUS_CLOUD, CLOUD, URANUS_CLOUD, 0F, 0.85F, 0.9F, 0.95F);
        gas("gj436b", "gj436b", NEPTUNE_CLOUD, URANUS_CLOUD, CLOUD, 0F, 0.6F, 0.8F, 1F);
        ALL.add(new World("lhs1140b", "lhs1140b", false, true, ICE, SLUSH, FROST, ICE, DEEP_ROCK, FROST, 0F, 0.8F, 0.7F, 0.85F, 1F, 0.8F, null));
        gas("k2_18b", "k2_18b", URANUS_CLOUD, NEPTUNE_CLOUD, CLOUD, 0F, 0.4F, 0.8F, 1F);
        sea("toi700e", SAND, MOSS, PERMAFROST, DIRT, 0.7F, 0.8F, 1F);
        sea("toi700d", SAND, MOSS, GRANITE, DIRT, 0.5F, 0.7F, 1F);
        gas("betapic_b", "betapic_b", JUPITER_CLOUD, STORM, RED_SPOT, 0F, 0.9F, 0.75F, 0.6F);
        gas("hr8799e", "hr8799e", RED_SPOT, STORM, JUPITER_CLOUD, 0.22F, 1F, 0.6F, 0.5F);
        gas("hr8799d", "hr8799d", RED_SPOT, JUPITER_CLOUD, STORM, 0.2F, 1F, 0.7F, 0.4F);
        gas("hr8799c", "hr8799c", STORM, RED_SPOT, CLOUD, 0.18F, 1F, 0.6F, 0.7F);
        gas("hr8799b", "hr8799b", RED_SPOT, STORM, JUPITER_CLOUD, 0.2F, 1F, 0.5F, 0.4F);
        gas("psr_b1620_26b", "psr_b", SATURN_CLOUD, CLOUD, STORM, 0F, 0.8F, 0.85F, 0.95F);
    }

    public static World byId(String id) {
        for (World w : ALL) {
            if (w.id.equals(id)) {
                return w;
            }
        }
        return null;
    }

    public static int index(World w) {
        return ALL.indexOf(w);
    }

    /** The world whose stretch of the dimension a block column (x) is in (null out in space, west of them all). */
    public static World at(double x) {
        int i = (int) Math.floorDiv((long) Math.floor(x) + REGION / 2, REGION);
        if (i < 0) {
            return null;
        }
        return ALL.get(Math.floorMod(i, ALL.size()));
    }

    /** X of the middle of a world's stretch. */
    public static double centre(World w) {
        return (double) index(w) * REGION;
    }

    /** Where on the world's map (u across, v down from north, 0..1) a block column is. */
    public static double[] mapUV(double x, double z) {
        double lx = x - Math.floor((x + REGION / 2.0) / REGION) * REGION;
        double u = lx / CIRCUMFERENCE;
        u -= Math.floor(u);
        double v = 0.5 + z / CIRCUMFERENCE;
        // Past a pole the map is mirrored (as if walking on over it).
        v = Math.abs(v);
        v = v % 2;
        if (v > 1) {
            v = 2 - v;
        }
        return new double[]{u, v};
    }

    /** The block column at a place on the map (longitude, latitude in radians) of a world. */
    public static double[] column(World w, double lon, double lat) {
        double u = lon / (Math.PI * 2);
        u -= Math.floor(u);
        if (u > 0.5) {
            u -= 1;
        }
        double v = 0.5 - lat / Math.PI;
        return new double[]{centre(w) + u * CIRCUMFERENCE, (v - 0.5) * CIRCUMFERENCE};
    }
}
