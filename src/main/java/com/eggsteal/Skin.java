package com.eggsteal;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Cosmetic base skins (index 0 = the free classic look; 1..20 are bought in the shop, cheapest is $1M). */
public class Skin {
    public final String name;
    public final Block wall, trim, light;
    public final int style;
    public final double price;

    static final Block[] RAINBOW = {Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.PURPLE_CONCRETE};

    private Skin(int id, String name, Block wall, Block trim, Block light, int style) {
        this.name = name; this.wall = wall; this.trim = trim; this.light = light; this.style = style;
        this.price = id == 0 ? 0 : 1_000_000 * (1 + 0.6 * (id - 1));
    }

    static Block c(int i) { return EggSteal.SKIN_BLOCKS.get(i).get(); }

    public static final Skin[] ALL;
    static {
        Block neon = c(0), crystal = c(1), candy = c(2), golden = c(3), rune = c(4), frost = c(5), slime = c(6), lava = c(7);
        int i = 0;
        ALL = new Skin[]{
            new Skin(i++, "Classic", Blocks.STONE_BRICKS, null, null, 0),
            new Skin(i++, "Neon Pink", Blocks.DEEPSLATE_BRICKS, neon, neon, 0),
            new Skin(i++, "Cyber Blue", Blocks.POLISHED_BLACKSTONE_BRICKS, crystal, crystal, 3),
            new Skin(i++, "Candy Land", candy, Blocks.WHITE_CONCRETE, candy, 1),
            new Skin(i++, "Golden Palace", Blocks.QUARTZ_BRICKS, golden, golden, 2),
            new Skin(i++, "Frozen Fort", frost, Blocks.SNOW_BLOCK, Blocks.SEA_LANTERN, 1),
            new Skin(i++, "Lava Keep", Blocks.NETHER_BRICKS, lava, lava, 2),
            new Skin(i++, "Slime Jungle", slime, Blocks.MOSSY_STONE_BRICKS, Blocks.GLOWSTONE, 0),
            new Skin(i++, "Rune Temple", rune, Blocks.OBSIDIAN, crystal, 3),
            new Skin(i++, "Ocean Reef", Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN, 0),
            new Skin(i++, "Desert Palace", Blocks.CUT_SANDSTONE, golden, Blocks.GLOWSTONE, 1),
            new Skin(i++, "Haunted Manor", Blocks.DEEPSLATE_TILES, rune, Blocks.SHROOMLIGHT, 2),
            new Skin(i++, "Royal Red", Blocks.BRICKS, golden, Blocks.GLOWSTONE, 2),
            new Skin(i++, "Sky Castle", Blocks.WHITE_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.SEA_LANTERN, 1),
            new Skin(i++, "Sunset", Blocks.ORANGE_TERRACOTTA, Blocks.RED_CONCRETE, Blocks.GLOWSTONE, 3),
            new Skin(i++, "Emerald Hall", Blocks.GREEN_CONCRETE, Blocks.EMERALD_BLOCK, Blocks.SEA_LANTERN, 0),
            new Skin(i++, "Amethyst Cave", Blocks.AMETHYST_BLOCK, crystal, rune, 2),
            new Skin(i++, "Diamond Vault", Blocks.QUARTZ_BRICKS, Blocks.DIAMOND_BLOCK, Blocks.SEA_LANTERN, 3),
            new Skin(i++, "Space Station", Blocks.WHITE_CONCRETE, crystal, crystal, 3),
            new Skin(i++, "Gingerbread", Blocks.BROWN_CONCRETE, candy, Blocks.GLOWSTONE, 1),
            new Skin(i++, "Rainbow", Blocks.PURPLE_CONCRETE, neon, neon, 4),
        };
    }

    public Block wallAt(int x, int y, int z) {
        if (style == 4) return RAINBOW[Math.floorMod(x + z + y / 2, RAINBOW.length)];
        return wall;
    }
}
