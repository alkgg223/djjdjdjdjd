package com.eggsteal.brainrot;

import com.eggsteal.EggSteal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base skins of Brainrot Heist. Skin 0 is the free classic look (concrete in the base colour); skins 1-25 are bought in
 * the shop. Every skin is a set of five matching custom blocks: the wall frame, the window glass, the floor, the carpet
 * (walkway, podium tops and the ring inside the walls) and the lamp on the walls; many of them give off particles.
 */
public class BrSkin {
    public static final String[] PARTS = {"frame", "glass", "floor", "carpet", "lamp"};
    public static final int FRAME = 0, GLASS = 1, FLOOR = 2, CARPET = 3, LAMP = 4;

    // particles of the skin blocks (see BrSkinBlock)
    public static final int NONE = 0, FLAME = 1, SOUL_FIRE = 2, END_ROD = 3, SPARK = 4, HEART = 5, NOTE = 6, VILLAGER = 7, CHERRY = 8,
            SNOW = 9, HONEY = 10, LAVA = 11, SPORE = 12, MYCELIUM = 13, ENCHANT = 14, PORTAL = 15, REVERSE_PORTAL = 16, WITCH = 17,
            GLOW = 18, GOLD_SPARK = 19, WHITE_SPARK = 20, CYAN_SPARK = 21, SOUL = 22, SMOKE = 23, CLOUD = 24, BUBBLES = 25,
            DRAGON = 26, TOTEM = 27, RAINBOW_DUST = 28, GOLD_DUST = 29, TOXIC_DUST = 30, CRIMSON = 31, FIREWORK = 32, DRIP_LAVA = 33,
            SLIME = 34, PUMPKIN = 35;

    public final int index;
    public final String id, name, color, look;
    public final double price;
    /** Particle of each part (FRAME..LAMP), and the light level of each part. */
    public final int[] fx, light;

    private BrSkin(int index, String id, String name, String color, double price, String look, int[] fx, int[] light) {
        this.index = index; this.id = id; this.name = name; this.color = color; this.price = price; this.look = look; this.fx = fx; this.light = light;
    }

    static int[] fx(int frame, int glass, int floor, int carpet, int lamp) { return new int[]{frame, glass, floor, carpet, lamp}; }
    static int[] light(int frame, int carpet) { return new int[]{frame, 0, 0, carpet, 15}; }

    public static final BrSkin[] ALL = {
        new BrSkin(0, "classic", "Classic", "§f", 0, "Concrete and glass in your base colour", fx(0, 0, 0, 0, 0), light(0, 0)),
        new BrSkin(1, "wood", "Wooden Cabin", "§6", 10_000, "Log walls, lattice windows, plank floor, lanterns", fx(0, 0, 0, 0, FLAME), light(0, 0)),
        new BrSkin(2, "candy", "Candy Pop", "§d", 18_000, "Candy-cane walls, gumdrop lamps that float hearts", fx(0, 0, 0, 0, HEART), light(0, 0)),
        new BrSkin(3, "desert", "Desert Temple", "§e", 32_000, "Carved sandstone, golden lattice, fire bowls", fx(0, 0, 0, 0, FLAME), light(0, 0)),
        new BrSkin(4, "ocean", "Ocean Reef", "§3", 55_000, "Prismarine walls, wavy glass, glowing pearls", fx(0, 0, 0, BUBBLES, GLOW), light(0, 0)),
        new BrSkin(5, "jungle", "Jungle Ruins", "§2", 95_000, "Mossy ruins with vines, leafy glass, firefly lamps", fx(0, 0, 0, 0, SPORE), light(0, 0)),
        new BrSkin(6, "cherry", "Cherry Blossom", "§d", 160_000, "Pink wood, blossom windows, petals falling everywhere", fx(CHERRY, 0, 0, 0, CHERRY), light(0, 0)),
        new BrSkin(7, "pumpkin", "Pumpkin Night", "§6", 280_000, "Spooky bricks with jack-o'-lanterns that smoke", fx(0, 0, 0, 0, PUMPKIN), light(0, 0)),
        new BrSkin(8, "honey", "Honey Hive", "§e", 480_000, "Honeycomb walls and lamps dripping honey", fx(0, 0, 0, 0, HONEY), light(0, 0)),
        new BrSkin(9, "frost", "Frost Palace", "§b", 820_000, "Ice bricks, frosted windows, snow falling from the walls", fx(SNOW, 0, 0, 0, SNOW), light(0, 0)),
        new BrSkin(10, "mushroom", "Mushroom Grove", "§c", 1_400_000, "Spotted mushroom walls and glowing shroom lamps", fx(0, 0, 0, MYCELIUM, CRIMSON), light(0, 0)),
        new BrSkin(11, "copper", "Steam Works", "§6", 2_400_000, "Riveted copper, gear windows, steaming lamps", fx(0, 0, 0, 0, SMOKE), light(0, 0)),
        new BrSkin(12, "emerald", "Lucky Emerald", "§a", 4_200_000, "Emerald and gold, clover windows, lucky sparkles", fx(0, 0, 0, VILLAGER, VILLAGER), light(0, 0)),
        new BrSkin(13, "royal", "Royal Velvet", "§4", 7_200_000, "Red velvet and gold, marble floor, golden chandeliers", fx(0, 0, 0, 0, GOLD_SPARK), light(0, 0)),
        new BrSkin(14, "haunted", "Haunted Manor", "§8", 12_000_000, "Rotten planks, cobweb glass, soul fire lanterns", fx(0, 0, 0, 0, SOUL_FIRE), light(0, 0)),
        new BrSkin(15, "toxic", "Toxic Lab", "§a", 21_000_000, "Hazard stripes, glowing slime carpet, toxic fumes", fx(0, 0, 0, SLIME, TOXIC_DUST), light(0, 9)),
        new BrSkin(16, "lava", "Lava Forge", "§c", 37_000_000, "Basalt with flowing lava cracks, burning lamps", fx(0, 0, 0, DRIP_LAVA, LAVA), light(7, 12)),
        new BrSkin(17, "neon", "Neon City", "§b", 64_000_000, "Black panels with pulsing neon, sparking tubes", fx(0, 0, 0, 0, SPARK), light(6, 10)),
        new BrSkin(18, "disco", "Disco Party", "§d", 110_000_000, "Mirror walls, a flashing dance floor, music notes", fx(0, 0, NOTE, 0, NOTE), light(0, 10)),
        new BrSkin(19, "matrix", "Hacker Matrix", "§a", 190_000_000, "Falling green code, circuit floor, magic glyphs", fx(ENCHANT, 0, 0, 0, ENCHANT), light(5, 8)),
        new BrSkin(20, "amethyst", "Amethyst Cave", "§5", 330_000_000, "Crystal walls and floating crystal lamps", fx(WITCH, 0, 0, 0, REVERSE_PORTAL), light(4, 0)),
        new BrSkin(21, "heaven", "Heaven Gate", "§f", 570_000_000, "White marble, cloud floor, golden halos of light", fx(0, 0, CLOUD, 0, END_ROD), light(0, 8)),
        new BrSkin(22, "dragon", "Dragon Lair", "§5", 1_000_000_000, "Dragon scales, obsidian floor, dragon breath", fx(0, 0, 0, PORTAL, DRAGON), light(0, 6)),
        new BrSkin(23, "galaxy", "Galaxy", "§9", 1_700_000_000, "Twinkling stars and nebulas on every block", fx(END_ROD, 0, 0, PORTAL, FIREWORK), light(6, 8)),
        new BrSkin(24, "rainbow", "Rainbow Dream", "§c", 3_000_000_000.0, "Every block shifts through the rainbow", fx(RAINBOW_DUST, 0, RAINBOW_DUST, 0, NOTE), light(8, 10)),
        new BrSkin(25, "golden", "Golden God", "§6", 5_000_000_000.0, "Shining gold and diamonds with sparkles everywhere", fx(GOLD_SPARK, 0, 0, WHITE_SPARK, TOTEM), light(6, 10)),
    };

    public static String fxName(int fx) {
        return switch (fx) {
            case FLAME -> "flames"; case SOUL_FIRE -> "soul fire"; case END_ROD -> "rising lights"; case SPARK -> "electric sparks";
            case HEART -> "hearts"; case NOTE -> "music notes"; case VILLAGER -> "lucky sparkles"; case CHERRY -> "falling petals";
            case SNOW -> "snowflakes"; case HONEY -> "honey drips"; case LAVA -> "fire and lava"; case SPORE -> "floating spores";
            case MYCELIUM -> "mushroom spores"; case ENCHANT -> "magic glyphs"; case PORTAL -> "portal swirls"; case REVERSE_PORTAL -> "crystal motes";
            case WITCH -> "magic sparkles"; case GLOW -> "glowing motes"; case GOLD_SPARK -> "golden sparks"; case WHITE_SPARK -> "white sparkles";
            case CYAN_SPARK -> "cyan sparks"; case SOUL -> "souls"; case SMOKE -> "steam"; case CLOUD -> "clouds"; case BUBBLES -> "bubbles";
            case DRAGON -> "dragon breath"; case TOTEM -> "confetti"; case RAINBOW_DUST -> "rainbow dust"; case GOLD_DUST -> "gold dust";
            case TOXIC_DUST -> "toxic fumes"; case CRIMSON -> "red spores"; case FIREWORK -> "twinkles"; case DRIP_LAVA -> "lava bubbles";
            case SLIME -> "slime"; case PUMPKIN -> "spooky flames";
            default -> "";
        };
    }

    public static BrSkin of(BrData.Stats s) { return s == null || s.skin <= 0 || s.skin >= ALL.length ? ALL[0] : ALL[s.skin]; }

    public static boolean owns(BrData.Stats s, int k) { return k == 0 || ((s.skins >> k) & 1L) != 0; }

    /** The custom block of one part (skins 1..25 only). */
    public Block block(int part) { return EggSteal.BR_SKIN_BLOCKS.get((index - 1) * PARTS.length + part).get(); }

    // ------------------------------------------------------------------ what a base is built from (base index i gives the classic colour)

    public BlockState frame(int i) { return (index == 0 ? BrMap.COLORS[i] : block(FRAME)).defaultBlockState(); }
    public BlockState glass(int i) { return (index == 0 ? BrMap.GLASS[i] : block(GLASS)).defaultBlockState(); }
    public BlockState carpet(int i) { return (index == 0 ? BrMap.COLORS[i] : block(CARPET)).defaultBlockState(); }
    public BlockState lamp() { return (index == 0 ? Blocks.SEA_LANTERN : block(LAMP)).defaultBlockState(); }
    public BlockState podiumRing() { return (index == 0 ? Blocks.SMOOTH_QUARTZ : block(FRAME)).defaultBlockState(); }
    public BlockState floor(int x, int z) {
        if (index > 0) return block(FLOOR).defaultBlockState();
        return (((x + z) & 1) == 0 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState();
    }
}
